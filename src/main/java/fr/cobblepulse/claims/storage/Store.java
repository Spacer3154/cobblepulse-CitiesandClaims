package fr.cobblepulse.claims.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.cobblepulse.claims.ClaimsConfig;
import fr.cobblepulse.claims.CobblePulseClaims;
import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.data.PlayerClaimData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stockage JSON avec ecriture differee (write-behind) : les objets sont serialises sur le fil du serveur au moment du
 * changement (instantane coherent), puis ecrits hors du fil principal par un unique fil d'E/S, de facon atomique (fichier
 * temporaire puis remplacement). Un fichier par claim et par joueur : seule la donnee modifiee est reecrite.
 * Dossier : world/cobblepulse_claims/.
 */
public final class Store {
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();
    private static final Object LOCK = new Object();
    /** chemin -> contenu a ecrire (null = suppression). */
    private static final Map<Path, String> PENDING = new LinkedHashMap<>();
    private static final AtomicBoolean SCHEDULED = new AtomicBoolean();
    private static ScheduledExecutorService io;
    private static Path root;
    private static long writes;

    private Store() {}

    public static void open(MinecraftServer server) {
        root = server.getWorldPath(LevelResource.ROOT).resolve("cobblepulse_claims");
        try {
            Files.createDirectories(root.resolve("claims"));
            Files.createDirectories(root.resolve("players"));
        } catch (Exception e) {
            CobblePulseClaims.LOG.error("[CobblePulse-Claims] Dossier de données inaccessible : {}", e.toString());
        }
        if (io == null || io.isShutdown()) {
            io = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "CobblePulse-Claims-IO");
                t.setDaemon(true);
                return t;
            });
        }
    }

    public static Path root() {
        return root;
    }

    public static List<Claim> loadClaims() {
        List<Claim> out = new ArrayList<>();
        if (root == null) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root.resolve("claims"), "*.json")) {
            for (Path p : ds) {
                try {
                    Claim c = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), Claim.class);
                    if (c != null && c.id > 0) {
                        c.normalize();
                        out.add(c);
                    }
                } catch (Exception e) {
                    CobblePulseClaims.LOG.error("[CobblePulse-Claims] Claim illisible {} : {}", p.getFileName(), e.toString());
                }
            }
        } catch (Exception e) {
            CobblePulseClaims.LOG.error("[CobblePulse-Claims] Lecture des claims impossible : {}", e.toString());
        }
        return out;
    }

    public static List<PlayerClaimData> loadPlayers() {
        List<PlayerClaimData> out = new ArrayList<>();
        if (root == null) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(root.resolve("players"), "*.json")) {
            for (Path p : ds) {
                try {
                    PlayerClaimData d = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), PlayerClaimData.class);
                    if (d != null && d.uuid != null) out.add(d);
                } catch (Exception e) {
                    CobblePulseClaims.LOG.error("[CobblePulse-Claims] Joueur illisible {} : {}", p.getFileName(), e.toString());
                }
            }
        } catch (Exception e) {
            CobblePulseClaims.LOG.error("[CobblePulse-Claims] Lecture des joueurs impossible : {}", e.toString());
        }
        return out;
    }

    public static Meta loadMeta() {
        try {
            Path p = root.resolve("meta.json");
            if (Files.exists(p)) {
                Meta m = GSON.fromJson(Files.readString(p, StandardCharsets.UTF_8), Meta.class);
                if (m != null) return m;
            }
        } catch (Exception e) {
            CobblePulseClaims.LOG.error("[CobblePulse-Claims] meta.json illisible : {}", e.toString());
        }
        return new Meta();
    }

    public static final class Meta {
        public int nextId = 1;
    }

    public static void saveMeta(Meta m) {
        queue(root.resolve("meta.json"), GSON.toJson(m));
    }

    public static void saveClaim(Claim c) {
        if (root == null) return;
        queue(root.resolve("claims").resolve(c.id + ".json"), GSON.toJson(c));
    }

    public static void deleteClaim(int id) {
        if (root == null) return;
        queue(root.resolve("claims").resolve(id + ".json"), null);
    }

    public static void savePlayer(PlayerClaimData d) {
        if (root == null || d == null || d.uuid == null) return;
        queue(root.resolve("players").resolve(d.uuid + ".json"), GSON.toJson(d));
    }

    private static void queue(Path p, String content) {
        synchronized (LOCK) {
            PENDING.put(p, content);
        }
        if (io != null && !io.isShutdown() && SCHEDULED.compareAndSet(false, true)) {
            io.schedule(Store::flushPending, Math.max(50, ClaimsConfig.get().storage.flush_delay_ms), TimeUnit.MILLISECONDS);
        }
    }

    private static void flushPending() {
        SCHEDULED.set(false);
        Map<Path, String> batch;
        synchronized (LOCK) {
            batch = new LinkedHashMap<>(PENDING);
            PENDING.clear();
        }
        for (Map.Entry<Path, String> e : batch.entrySet()) write(e.getKey(), e.getValue());
    }

    private static void write(Path p, String content) {
        try {
            if (content == null) {
                Files.deleteIfExists(p);
                return;
            }
            Files.createDirectories(p.getParent());
            Path tmp = p.resolveSibling(p.getFileName() + ".tmp");
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomic) {
                Files.move(tmp, p, StandardCopyOption.REPLACE_EXISTING);
            }
            writes++;
        } catch (Exception e) {
            CobblePulseClaims.LOG.error("[CobblePulse-Claims] Écriture impossible de {} : {}", p, e.toString());
        }
    }

    /** Ecrit immediatement tout ce qui est en attente (arret du serveur, tests). */
    public static void flushNow() {
        if (io == null) {
            flushPending();
            return;
        }
        try {
            io.submit(Store::flushPending).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            flushPending();
        }
    }

    public static void close() {
        flushNow();
        if (io != null) io.shutdown();
        io = null;
    }

    public static int pendingCount() {
        synchronized (LOCK) {
            return PENDING.size();
        }
    }

    public static long writes() {
        return writes;
    }

    public static boolean playerFileExists(UUID id) {
        return root != null && Files.exists(root.resolve("players").resolve(id + ".json"));
    }
}
