package fr.cobblepulse.claims.client.map;

import fr.cobblepulse.claims.CobblePulseClaims;
import fr.cobblepulse.claims.client.ClientState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Decouverte de la carte : un territoire n'est surligne sur JourneyMap / Xaero que sur les chunks que le joueur a deja
 * charges (meme regle que l'exploration des cartes). Un bit par chunk, par region de 32 x 32 chunks, conserve par
 * serveur et par dimension dans {@code cobblepulse_claims/discovery/}.
 */
public final class Discovery {
    private static final int MAGIC = 0x43504431; // "CPD1"
    private static final Map<Long, long[]> REGIONS = new ConcurrentHashMap<>();
    /** Regle envoyee par le serveur (config.json : sync.map_discovery_required). */
    public static volatile boolean required = true;
    /** Incremente quand un chunk nouvellement decouvert touche un territoire : les cartes sont redessinees. */
    public static volatile int mapVersion;
    private static volatile String loadedKey;
    private static boolean dirty;
    private static long lastSave;
    private static int ticks;

    private Discovery() {}

    static long regionKey(int rx, int rz) {
        return (long) rx << 32 | rz & 0xFFFFFFFFL;
    }

    private static int bit(int cx, int cz) {
        return (cz & 31) << 5 | cx & 31;
    }

    public static boolean isDiscovered(int cx, int cz) {
        long[] bits = REGIONS.get(regionKey(cx >> 5, cz >> 5));
        if (bits == null) return false;
        int i = bit(cx, cz);
        return (bits[i >> 6] >>> (i & 63) & 1L) != 0;
    }

    /** Le surlignage d'un territoire est-il permis sur ce chunk ? */
    public static boolean visible(int cx, int cz) {
        return !required || isDiscovered(cx, cz);
    }

    /** Vrai si au moins un chunk du rectangle (blocs inclus) est visible. */
    public static boolean anyVisible(int x1, int z1, int x2, int z2) {
        if (!required) return true;
        for (int cx = x1 >> 4; cx <= x2 >> 4; cx++) {
            for (int cz = z1 >> 4; cz <= z2 >> 4; cz++) if (isDiscovered(cx, cz)) return true;
        }
        return false;
    }

    /** Empreinte des chunks decouverts d'une region Xaero (512 x 512 blocs) : la carte repeint la region si elle change. */
    public static int regionHash(int rx, int rz) {
        if (!required) return 1;
        long[] bits = REGIONS.get(regionKey(rx, rz));
        return bits == null ? 0 : Arrays.hashCode(bits);
    }

    /** Marque un chunk ; renvoie vrai s'il n'etait pas encore decouvert. */
    public static boolean mark(int cx, int cz) {
        long[] bits = REGIONS.computeIfAbsent(regionKey(cx >> 5, cz >> 5), k -> new long[16]);
        int i = bit(cx, cz);
        long m = 1L << (i & 63);
        if ((bits[i >> 6] & m) != 0) return false;
        bits[i >> 6] |= m;
        dirty = true;
        return true;
    }

    public static int count() {
        int n = 0;
        for (long[] bits : REGIONS.values()) for (long l : bits) n += Long.bitCount(l);
        return n;
    }

    // ------------------------------------------------------------------ exploration

    /** Fin de tick client : chunks charges autour du joueur = decouverts (deux fois par seconde). */
    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        String key = storageKey(mc);
        if (!key.equals(loadedKey)) {
            save();
            load(key);
        }
        if (++ticks % 10 != 0) return;
        ClientChunkCache src = mc.level.getChunkSource();
        int r = Math.max(2, mc.options.getEffectiveRenderDistance()) + 1;
        int pcx = mc.player.chunkPosition().x, pcz = mc.player.chunkPosition().z;
        boolean touched = false;
        for (int cx = pcx - r; cx <= pcx + r; cx++) {
            for (int cz = pcz - r; cz <= pcz + r; cz++) {
                if (src.hasChunk(cx, cz) && mark(cx, cz) && touchesClaim(cx, cz)) touched = true;
            }
        }
        if (touched) mapVersion++;
        if (dirty && System.currentTimeMillis() - lastSave > 60_000) save();
    }

    private static boolean touchesClaim(int cx, int cz) {
        int x1 = cx << 4, z1 = cz << 4;
        for (ClientState.CClaim c : ClientState.CLAIMS.values()) {
            if (c.minX <= x1 + 15 && c.maxX >= x1 && c.minZ <= z1 + 15 && c.maxZ >= z1) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ persistance

    static String storageKey(Minecraft mc) {
        String server;
        if (mc.getSingleplayerServer() != null) server = "solo_" + mc.getSingleplayerServer().getWorldData().getLevelName();
        else if (mc.getCurrentServer() != null) server = "serveur_" + mc.getCurrentServer().ip;
        else server = "inconnu";
        return safe(server) + "/" + safe(mc.level.dimension().location().toString());
    }

    private static String safe(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static Path file(String key) {
        return FabricLoader.getInstance().getGameDir().resolve("cobblepulse_claims").resolve("discovery").resolve(key + ".dat");
    }

    static synchronized void load(String key) {
        REGIONS.clear();
        loadedKey = key;
        dirty = false;
        lastSave = System.currentTimeMillis();
        Path f = file(key);
        if (Files.isRegularFile(f)) {
            try (DataInputStream in = new DataInputStream(new GZIPInputStream(new BufferedInputStream(Files.newInputStream(f))))) {
                if (in.readInt() != MAGIC) throw new IOException("format inconnu");
                int n = in.readInt();
                for (int i = 0; i < n; i++) {
                    long k = in.readLong();
                    long[] bits = new long[16];
                    for (int j = 0; j < 16; j++) bits[j] = in.readLong();
                    REGIONS.put(k, bits);
                }
            } catch (IOException e) {
                CobblePulseClaims.LOG.warn("[CobblePulse-Claims] Découverte de carte illisible ({}) : {}", f, e.toString());
            }
        }
        mapVersion++;
    }

    public static synchronized void save() {
        String key = loadedKey;
        if (key == null || !dirty) return;
        Path f = file(key);
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp))))) {
                Map<Long, long[]> copy = Map.copyOf(REGIONS);
                out.writeInt(MAGIC);
                out.writeInt(copy.size());
                for (Map.Entry<Long, long[]> e : copy.entrySet()) {
                    out.writeLong(e.getKey());
                    for (long l : e.getValue()) out.writeLong(l);
                }
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            dirty = false;
            lastSave = System.currentTimeMillis();
        } catch (IOException e) {
            CobblePulseClaims.LOG.warn("[CobblePulse-Claims] Découverte de carte non sauvegardée : {}", e.toString());
        }
    }

    /** Deconnexion : sauvegarde puis oubli (la prochaine session recharge le fichier du serveur rejoint). */
    public static void disconnect() {
        save();
        REGIONS.clear();
        loadedKey = null;
        required = true;
    }

    /** Test : sauvegarde forcee puis rechargement depuis le disque. */
    public static void reloadForTest() {
        String key = loadedKey;
        dirty = true;
        save();
        if (key != null) load(key);
    }
}
