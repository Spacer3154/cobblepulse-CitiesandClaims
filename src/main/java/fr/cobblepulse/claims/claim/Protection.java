package fr.cobblepulse.claims.claim;

import fr.cobblepulse.claims.Msg;
import fr.cobblepulse.claims.border.WorldBorders;
import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.flag.Flag;
import fr.cobblepulse.claims.flag.Flags;
import fr.cobblepulse.claims.perm.Perms;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Set;

/** Point d'entree des verifications : action d'un joueur a une position, ou comportement d'une zone. */
public final class Protection {
    /** Actions qui modifient le terrain : refusees aussi au-dela (et aux abords) de la bordure de la dimension. */
    private static final Set<Flag> TERRAIN = Set.of(Flags.BLOCK_BREAK, Flags.BLOCK_PLACE, Flags.BLOCK_TRAMPLE, Flags.BONEMEAL, Flags.FROST_WALKER,
            Flags.BRUSH, Flags.WATER, Flags.LAVA, Flags.POWDER_SNOW, Flags.FIRE_IGNITE, Flags.VEHICLE_PLACE, Flags.CROP_HARVEST);

    private Protection() {}

    public static boolean check(Player player, Level level, BlockPos pos, Flag f) {
        return check(player, level, pos.getX(), pos.getZ(), f, true);
    }

    public static boolean check(Player player, Level level, int x, int z, Flag f, boolean message) {
        if (!(player instanceof ServerPlayer p) || level.isClientSide()) return true;
        if (TERRAIN.contains(f) && !Perms.bypass(p) && WorldBorders.griefBlocked(ClaimManager.dim(level), x, z)) {
            if (message) Msg.deny(p, "border", "Action impossible aux abords ou au-delà de la bordure du monde.");
            return false;
        }
        Claim c = ClaimManager.at(ClaimManager.dim(level), x, z);
        if (Access.allows(p, c, f)) return true;
        if (message) Msg.deny(p, f.id(), "Interdit ici : " + f.label() + " (" + c.displayName() + ")");
        return false;
    }

    /** Comportement autorise dans la zone ? Hors claim : oui (la nature reste libre). */
    public static boolean allowsZone(Level level, BlockPos pos, Flag f) {
        return allowsZone(level, pos.getX(), pos.getZ(), f);
    }

    public static boolean allowsZone(Level level, int x, int z, Flag f) {
        if (level == null || level.isClientSide()) return true;
        Claim c = ClaimManager.at(ClaimManager.dim(level), x, z);
        return c == null || Access.zone(c, f);
    }

    /** Flag d'administration actif dans la zone (soin, vol, meteo...) ? Hors claim : non. */
    public static boolean active(Level level, double x, double z, Flag f) {
        if (level == null || level.isClientSide()) return false;
        Claim c = ClaimManager.at(ClaimManager.dim(level), (int) Math.floor(x), (int) Math.floor(z));
        return c != null && Access.zone(c, f);
    }

    /** Deux positions dans des « territoires » differents (claim A, claim B ou nature) ? */
    public static boolean crosses(Level level, BlockPos from, BlockPos to) {
        String d = ClaimManager.dim(level);
        Claim a = ClaimManager.topAt(d, from.getX(), from.getZ());
        Claim b = ClaimManager.topAt(d, to.getX(), to.getZ());
        return a != b;
    }
}
