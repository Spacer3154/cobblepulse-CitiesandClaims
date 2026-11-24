package fr.cobblepulse.claims.tools;

import fr.cobblepulse.claims.Msg;
import fr.cobblepulse.claims.claim.Access;
import fr.cobblepulse.claims.claim.ClaimManager;
import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.economy.BlockEconomy;
import fr.cobblepulse.claims.net.ClaimsNet;
import fr.cobblepulse.claims.perm.Perms;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Selection a la Balise : coin A, coin B (creation ou parcelle), redimensionnement par un coin existant. */
public final class WandService {
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();
    /** Staff en mode « creation de claims admin ». */
    private static final Set<UUID> ADMIN_MODE = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> LAST_CLICK = new HashMap<>();

    public static final class Selection {
        public String dim;
        public BlockPos a;
        /** Redimensionnement : claim vise et coin fixe (oppose au coin saisi). */
        public int resizing;
        public int fixedX, fixedZ;
    }

    private WandService() {}

    public static Selection selection(UUID id) {
        return SELECTIONS.get(id);
    }

    public static boolean toggleAdminMode(ServerPlayer p) {
        if (ADMIN_MODE.remove(p.getUUID())) return false;
        ADMIN_MODE.add(p.getUUID());
        return true;
    }

    public static boolean adminMode(UUID id) {
        return ADMIN_MODE.contains(id);
    }

    public static void clear(ServerPlayer p) {
        SELECTIONS.remove(p.getUUID());
        ClaimsNet.sendSelection(p, null, 0);
    }

    public static void forget(UUID id) {
        SELECTIONS.remove(id);
        LAST_CLICK.remove(id);
    }

    /** Anti double-clic (main principale + secondaire, ou clic repete dans le meme tick). */
    private static boolean debounce(ServerPlayer p) {
        long now = System.currentTimeMillis();
        Long last = LAST_CLICK.get(p.getUUID());
        LAST_CLICK.put(p.getUUID(), now);
        return last != null && now - last < 150;
    }

    public static void clickAir(ServerPlayer p, boolean shift) {
        if (debounce(p)) return;
        if (shift) {
            openAt(p);
            return;
        }
        if (SELECTIONS.remove(p.getUUID()) != null) {
            ClaimsNet.sendSelection(p, null, 0);
            Msg.info(p, "Sélection annulée.");
        } else {
            Msg.info(p, "Visez un bloc : clic droit pour poser le coin A du territoire.");
        }
    }

    private static void openAt(ServerPlayer p) {
        Claim c = ClaimManager.at(p.level(), p.blockPosition());
        if (c == null) {
            ClaimsNet.openDashboard(p);
            return;
        }
        if (Access.canManage(p, c)) ClaimsNet.openManager(p, c);
        else ClaimsNet.openInfo(p, c);
    }

    public static void click(ServerPlayer p, BlockPos pos, boolean shift) {
        if (debounce(p)) return;
        if (!Perms.has(p, Perms.USE)) {
            Msg.err(p, "Vous n'avez pas accès aux outils de claim.");
            return;
        }
        String dim = ClaimManager.dim(p.level());
        if (shift) {
            Claim c = ClaimManager.at(dim, pos.getX(), pos.getZ());
            if (c == null) ClaimsNet.openDashboard(p);
            else if (Access.canManage(p, c)) ClaimsNet.openManager(p, c);
            else ClaimsNet.openInfo(p, c);
            return;
        }
        Selection s = SELECTIONS.get(p.getUUID());
        if (s != null && !dim.equals(s.dim)) {
            SELECTIONS.remove(p.getUUID());
            s = null;
        }
        // 1) redimensionnement en cours : nouveau coin
        if (s != null && s.resizing != 0) {
            Claim c = ClaimManager.get(s.resizing);
            SELECTIONS.remove(p.getUUID());
            if (c == null || !Access.canManage(p, c)) {
                Msg.err(p, "Ce territoire n'existe plus ou ne vous appartient pas.");
                ClaimsNet.sendSelection(p, null, 0);
                return;
            }
            ClaimManager.Result r = ClaimManager.resize(p, c, s.fixedX, s.fixedZ, pos.getX(), pos.getZ());
            ClaimsNet.sendSelection(p, null, 0);
            report(p, r);
            return;
        }
        // 2) clic sur un coin d'un claim gere : debut du redimensionnement
        if (s == null) {
            Claim at = ClaimManager.at(dim, pos.getX(), pos.getZ());
            Claim corner = null;
            if (at != null && at.isCorner(pos.getX(), pos.getZ())) corner = at;
            else if (at != null && at.isSub() && ClaimManager.top(at).isCorner(pos.getX(), pos.getZ())) corner = ClaimManager.top(at);
            else if (at != null && !at.isSub()) {
                for (Claim sub : at.children) if (sub.isCorner(pos.getX(), pos.getZ())) corner = sub;
            }
            if (corner != null && Access.canManage(p, corner)) {
                s = new Selection();
                s.dim = dim;
                s.resizing = corner.id;
                s.fixedX = pos.getX() == corner.minX ? corner.maxX : corner.minX;
                s.fixedZ = pos.getZ() == corner.minZ ? corner.maxZ : corner.minZ;
                s.a = pos;
                SELECTIONS.put(p.getUUID(), s);
                ClaimsNet.sendSelection(p, s, corner.id);
                Msg.info(p, "Redimensionnement de « " + corner.displayName() + " » : clic droit sur le nouvel emplacement de ce coin.");
                return;
            }
        }
        // 3) coin A
        if (s == null) {
            s = new Selection();
            s.dim = dim;
            s.a = pos.immutable();
            SELECTIONS.put(p.getUUID(), s);
            ClaimsNet.sendSelection(p, s, 0);
            FakeVisuals.showCorner(p, pos);
            Msg.info(p, "Coin A posé en (" + pos.getX() + ", " + pos.getZ() + "). Clic droit sur le coin opposé pour valider.");
            return;
        }
        // 4) coin B : creation (parcelle si les deux coins sont dans un claim gere)
        SELECTIONS.remove(p.getUUID());
        ClaimsNet.sendSelection(p, null, 0);
        BlockPos a = s.a;
        Claim inA = ClaimManager.topAt(dim, a.getX(), a.getZ());
        Claim inB = ClaimManager.topAt(dim, pos.getX(), pos.getZ());
        ClaimManager.Result r;
        if (inA != null && inA == inB && Access.canManage(p, inA)) {
            r = ClaimManager.createSub(p, inA, a.getX(), a.getZ(), pos.getX(), pos.getZ());
        } else if (ADMIN_MODE.contains(p.getUUID()) && Perms.has(p, Perms.CONVERT_ADMIN)) {
            r = ClaimManager.create(p, dim, a.getX(), a.getZ(), pos.getX(), pos.getZ(), true);
        } else {
            r = ClaimManager.create(p, dim, a.getX(), a.getZ(), pos.getX(), pos.getZ(), false);
        }
        report(p, r);
    }

    private static void report(ServerPlayer p, ClaimManager.Result r) {
        if (!r.ok()) {
            Msg.err(p, r.message());
            return;
        }
        Msg.ok(p, r.message());
        Claim c = r.claim();
        if (!c.isSub() && !c.isAdmin()) Msg.info(p, "Solde restant : " + BlockEconomy.available(p.getUUID()) + " blocs. Maj + clic droit dans le territoire pour le gérer.");
        ClaimsNet.sendHighlight(p, c);
        FakeVisuals.showClaim(p, c);
    }
}
