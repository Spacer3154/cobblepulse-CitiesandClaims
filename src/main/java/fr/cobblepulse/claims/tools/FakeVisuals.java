package fr.cobblepulse.claims.tools;

import fr.cobblepulse.claims.ClaimsConfig;
import fr.cobblepulse.claims.claim.ClaimManager;
import fr.cobblepulse.claims.claim.Relation;
import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.net.ClaimsNet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reperes pour les joueurs sans le mod client : blocs fantomes (visibles par le seul joueur, jamais poses dans le monde)
 * aux coins et le long des bords, couleur selon la relation. Effaces apres visual_seconds.
 */
public final class FakeVisuals {
    private record Shown(String dim, List<BlockPos> positions, long until) {}

    private static final Map<UUID, Shown> SHOWN = new HashMap<>();

    private FakeVisuals() {}

    private static boolean vanilla(ServerPlayer p) {
        return !ClaimsNet.hasClient(p);
    }

    public static void showCorner(ServerPlayer p, BlockPos pos) {
        if (!vanilla(p)) return;
        revert(p);
        List<BlockPos> list = new ArrayList<>();
        list.add(pos.immutable());
        p.connection.send(new ClientboundBlockUpdatePacket(pos, Blocks.GLOWSTONE.defaultBlockState()));
        SHOWN.put(p.getUUID(), new Shown(ClaimManager.dim(p.level()), list, System.currentTimeMillis() + ClaimsConfig.get().claims.visual_seconds * 1000L));
    }

    public static void showClaim(ServerPlayer p, Claim c) {
        if (!vanilla(p)) return;
        showClaims(p, List.of(c));
    }

    public static void showClaims(ServerPlayer p, List<Claim> claims) {
        if (!vanilla(p)) return;
        revert(p);
        ServerLevel level = p.serverLevel();
        List<BlockPos> list = new ArrayList<>();
        for (Claim c : claims) {
            if (!c.dimension.equals(ClaimManager.dim(level))) continue;
            BlockState edge = switch (Relation.of(p.getUUID(), c)) {
                case OWN -> Blocks.LIME_CONCRETE.defaultBlockState();
                case TRUSTED -> Blocks.CYAN_CONCRETE.defaultBlockState();
                case CLAN -> Blocks.BLUE_CONCRETE.defaultBlockState();
                case OTHER -> Blocks.ORANGE_CONCRETE.defaultBlockState();
                case ADMIN -> Blocks.PURPLE_CONCRETE.defaultBlockState();
            };
            if (c.isSub()) edge = Blocks.WHITE_CONCRETE.defaultBlockState();
            BlockState corner = c.isSub() ? Blocks.IRON_BLOCK.defaultBlockState() : Blocks.GLOWSTONE.defaultBlockState();
            int step = Math.max(2, (int) Math.ceil((c.width() + c.length()) * 2 / 160.0));
            for (int x = c.minX; x <= c.maxX; x++) {
                boolean cornerX = x == c.minX || x == c.maxX;
                if (!cornerX && (x - c.minX) % step != 0) continue;
                put(p, level, list, x, c.minZ, cornerX ? corner : edge);
                put(p, level, list, x, c.maxZ, cornerX ? corner : edge);
            }
            for (int z = c.minZ + 1; z < c.maxZ; z++) {
                if ((z - c.minZ) % step != 0) continue;
                put(p, level, list, c.minX, z, edge);
                put(p, level, list, c.maxX, z, edge);
            }
            if (list.size() > 2000) break;
        }
        SHOWN.put(p.getUUID(), new Shown(ClaimManager.dim(level), list, System.currentTimeMillis() + ClaimsConfig.get().claims.visual_seconds * 1000L));
    }

    private static void put(ServerPlayer p, ServerLevel level, List<BlockPos> list, int x, int z, BlockState state) {
        if (!level.hasChunk(x >> 4, z >> 4)) return;
        if (Math.abs(x - p.getX()) > 160 || Math.abs(z - p.getZ()) > 160) return;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        BlockPos pos = new BlockPos(x, y, z);
        list.add(pos);
        p.connection.send(new ClientboundBlockUpdatePacket(pos, state));
    }

    public static void revert(ServerPlayer p) {
        Shown s = SHOWN.remove(p.getUUID());
        if (s == null || !s.dim().equals(ClaimManager.dim(p.level()))) return;
        for (BlockPos pos : s.positions()) p.connection.send(new ClientboundBlockUpdatePacket(p.level(), pos));
    }

    public static void tick(MinecraftServer server) {
        if (SHOWN.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(SHOWN.keySet())) {
            Shown s = SHOWN.get(id);
            if (s.until() > now) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) revert(p);
            else SHOWN.remove(id);
        }
    }

    public static int shownCount(UUID id) {
        Shown s = SHOWN.get(id);
        return s == null ? 0 : s.positions().size();
    }

    public static void forget(UUID id) {
        SHOWN.remove(id);
    }
}
