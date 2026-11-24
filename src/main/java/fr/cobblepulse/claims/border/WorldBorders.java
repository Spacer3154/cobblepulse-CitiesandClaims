package fr.cobblepulse.claims.border;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.cobblepulse.claims.ClaimsConfig;
import fr.cobblepulse.claims.CobblePulseClaims;
import fr.cobblepulse.claims.Msg;
import fr.cobblepulse.claims.perm.Perms;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * WorldBorders independantes par dimension (cle de registre du monde) : centre (X, Z), forme circulaire (rayon) ou
 * rectangulaire (largeur X, longueur Z). Empeche les claims au-dela, repousse en douceur les joueurs, leurs montures et
 * leurs Pokemon, annule les teleportations hors limite et bloque le grief aux abords. Independantes de la bordure vanilla.
 */
public final class WorldBorders {
    public static final class Border {
        public boolean enabled = true;
        public double centerX, centerZ;
        /** CIRCLE ou RECTANGLE. */
        public String shape = "CIRCLE";
        public double radius = 10000;
        public double sizeX = 4000, sizeZ = 4000;

        public boolean circle() {
            return !"RECTANGLE".equalsIgnoreCase(shape);
        }

        public Border copy() {
            Border b = new Border();
            b.enabled = enabled;
            b.centerX = centerX;
            b.centerZ = centerZ;
            b.shape = shape;
            b.radius = radius;
            b.sizeX = sizeX;
            b.sizeZ = sizeZ;
            return b;
        }
    }

    private static final Map<String, Border> BORDERS = new LinkedHashMap<>();
    /** Marge (blocs) a l'interieur de la bordure ou le grief reste bloque (« aux abords »). */
    private static int edgeBuffer = 2;
    public static final java.util.List<Consumer<String>> CHANGED = new CopyOnWriteArrayList<>();

    private WorldBorders() {}

    private static Path file() {
        return ClaimsConfig.dir().resolve("worldborders.json");
    }

    public static void load() {
        BORDERS.clear();
        Path f = file();
        boolean exists = Files.exists(f);
        try {
            if (exists) {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                if (o.has("edge_buffer")) edgeBuffer = Math.max(0, o.get("edge_buffer").getAsInt());
                JsonObject dims = o.has("dimensions") ? o.getAsJsonObject("dimensions") : new JsonObject();
                for (String k : dims.keySet()) {
                    Border b = ClaimsConfig.GSON.fromJson(dims.get(k), Border.class);
                    if (b != null) BORDERS.put(k, sanitize(b));
                }
            }
        } catch (Exception e) {
            CobblePulseClaims.LOG.error("[CobblePulse-Claims] worldborders.json illisible : {}", e.toString());
        }
        if (!exists) {
            // valeurs du cahier des charges
            Border ow = new Border();
            ow.shape = "CIRCLE";
            ow.radius = 10000;
            BORDERS.put("minecraft:overworld", ow);
            Border nether = new Border();
            nether.shape = "RECTANGLE";
            nether.sizeX = 4000;
            nether.sizeZ = 4000;
            BORDERS.put("minecraft:the_nether", nether);
            Border end = new Border();
            end.centerX = 100;
            end.centerZ = -50;
            end.shape = "CIRCLE";
            end.radius = 5000;
            BORDERS.put("minecraft:the_end", end);
        }
        save();
    }

    private static Border sanitize(Border b) {
        b.shape = b.circle() ? "CIRCLE" : "RECTANGLE";
        b.radius = Math.max(8, b.radius);
        b.sizeX = Math.max(16, b.sizeX);
        b.sizeZ = Math.max(16, b.sizeZ);
        return b;
    }

    /** Dimensions chargees (vanilla et mods) : ajoutees, desactivees par defaut si inconnues. */
    public static void registerLevels(MinecraftServer server) {
        boolean dirty = false;
        for (ServerLevel l : server.getAllLevels()) {
            String k = l.dimension().location().toString();
            if (!BORDERS.containsKey(k)) {
                Border b = new Border();
                b.enabled = false;
                b.radius = 10000;
                BORDERS.put(k, b);
                dirty = true;
            }
        }
        if (dirty) save();
    }

    public static void save() {
        JsonObject o = new JsonObject();
        o.addProperty("edge_buffer", edgeBuffer);
        JsonObject dims = new JsonObject();
        for (Map.Entry<String, Border> e : BORDERS.entrySet()) dims.add(e.getKey(), ClaimsConfig.GSON.toJsonTree(e.getValue()));
        o.add("dimensions", dims);
        ClaimsConfig.writeAtomic(file(), ClaimsConfig.GSON.toJson(o));
    }

    public static Map<String, Border> all() {
        return BORDERS;
    }

    public static Border get(String dim) {
        return BORDERS.get(dim);
    }

    public static Border active(String dim) {
        Border b = BORDERS.get(dim);
        return b != null && b.enabled ? b : null;
    }

    public static int edgeBuffer() {
        return edgeBuffer;
    }

    public static void set(String dim, Border b) {
        BORDERS.put(dim, sanitize(b));
        save();
        for (Consumer<String> l : CHANGED) l.accept(dim);
    }

    public static void setEdgeBuffer(int v) {
        edgeBuffer = Math.max(0, v);
        save();
    }

    /** Distance signee au bord : positive a l'interieur. */
    public static double inset(Border b, double x, double z) {
        if (b.circle()) return b.radius - Math.hypot(x - b.centerX, z - b.centerZ);
        double dx = b.sizeX / 2 - Math.abs(x - b.centerX);
        double dz = b.sizeZ / 2 - Math.abs(z - b.centerZ);
        return Math.min(dx, dz);
    }

    public static boolean inside(String dim, double x, double z) {
        Border b = active(dim);
        return b == null || inset(b, x, z) >= 0;
    }

    /** Rectangle de blocs [x1..x2] x [z1..z2] entierement dans la bordure. */
    public static boolean rectInside(String dim, int x1, int z1, int x2, int z2) {
        Border b = active(dim);
        if (b == null) return true;
        return inset(b, x1, z1) >= 0 && inset(b, x2 + 1, z1) >= 0 && inset(b, x1, z2 + 1) >= 0 && inset(b, x2 + 1, z2 + 1) >= 0;
    }

    /** Grief bloque au-dela de la bordure et a moins de edge_buffer blocs de celle-ci. */
    public static boolean griefBlocked(String dim, int x, int z) {
        Border b = active(dim);
        return b != null && inset(b, x + 0.5, z + 0.5) < edgeBuffer;
    }

    /** Point le plus proche a l'interieur, a « margin » blocs du bord. */
    public static Vec3 clampInside(Border b, double x, double z, double margin) {
        if (b.circle()) {
            double dx = x - b.centerX, dz = z - b.centerZ;
            double d = Math.hypot(dx, dz);
            double r = Math.max(0, b.radius - margin);
            if (d <= r || d == 0) return new Vec3(x, 0, z);
            return new Vec3(b.centerX + dx / d * r, 0, b.centerZ + dz / d * r);
        }
        double hx = Math.max(0, b.sizeX / 2 - margin), hz = Math.max(0, b.sizeZ / 2 - margin);
        return new Vec3(Math.max(b.centerX - hx, Math.min(b.centerX + hx, x)), 0, Math.max(b.centerZ - hz, Math.min(b.centerZ + hz, z)));
    }

    /** Teleportation vers (x, z) de cette dimension autorisee ? (refus avec message). */
    public static boolean allowTeleport(ServerPlayer p, ServerLevel target, double x, double z) {
        if (p == null || target == null || Perms.bypass(p)) return true;
        if (inside(target.dimension().location().toString(), x, z)) return true;
        Msg.deny(p, "border_tp", "Téléportation annulée : destination au-delà de la bordure du monde.");
        return false;
    }

    /** Repoussement doux, appele toutes les 5 ticks. */
    public static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            Border b = active(level.dimension().location().toString());
            if (b == null) continue;
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator() || Perms.bypass(p)) continue;
                if (inset(b, p.getX(), p.getZ()) >= 0) continue;
                Entity mover = p.getRootVehicle();
                pushBack(level, b, mover);
                p.displayClientMessage(Component.literal("⚠ Limite du monde atteinte").withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    /** Pokemon et entites des joueurs (montures) hors limite : ramenes a l'interieur. Appele chaque seconde. */
    public static void tickEntities(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            Border b = active(level.dimension().location().toString());
            if (b == null) continue;
            for (Entity e : level.getAllEntities()) {
                if (e instanceof ServerPlayer || !e.isAlive()) continue;
                if (!(e instanceof com.cobblemon.mod.common.entity.pokemon.PokemonEntity pe)) continue;
                if (pe.getOwner() == null) continue;
                if (inset(b, e.getX(), e.getZ()) < 0 && e.getVehicle() == null) pushBack(level, b, e);
            }
        }
    }

    public static void pushBack(ServerLevel level, Border b, Entity e) {
        Vec3 in = clampInside(b, e.getX(), e.getZ(), 1.5);
        double y = e.getY();
        BlockPos at = BlockPos.containing(in.x, y, in.z);
        if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty() || !level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()) {
            y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        }
        Vec3 inward = new Vec3(in.x - e.getX(), 0, in.z - e.getZ());
        Vec3 push = inward.lengthSqr() > 1e-6 ? inward.normalize().scale(0.25) : Vec3.ZERO;
        if (e instanceof ServerPlayer sp) {
            sp.connection.teleport(in.x, y, in.z, sp.getYRot(), sp.getXRot());
        } else {
            e.teleportTo(in.x, y, in.z);
        }
        e.setDeltaMovement(push.x, Math.max(0, e.getDeltaMovement().y), push.z);
        e.hurtMarked = true;
        e.fallDistance = 0;
        for (Entity passenger : e.getIndirectPassengers()) passenger.fallDistance = 0;
    }

    public static String describe(Border b) {
        if (b == null) return "désactivée";
        String c = String.format(Locale.ROOT, "centre (%.0f, %.0f)", b.centerX, b.centerZ);
        return b.circle() ? c + ", cercle de rayon " + (long) b.radius : c + ", rectangle " + (long) b.sizeX + " × " + (long) b.sizeZ;
    }
}
