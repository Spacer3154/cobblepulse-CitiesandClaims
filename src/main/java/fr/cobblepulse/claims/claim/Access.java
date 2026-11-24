package fr.cobblepulse.claims.claim;

import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.data.ClaimType;
import fr.cobblepulse.claims.data.FlagState;
import fr.cobblepulse.claims.data.TrustLevel;
import fr.cobblepulse.claims.flag.Flag;
import fr.cobblepulse.claims.flag.Flags;
import fr.cobblepulse.claims.perm.Perms;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Evaluation des droits : niveau de confiance d'un joueur dans un claim, valeur effective d'un flag (surcharge de la
 * sous-zone, puis du claim principal si heritage, puis valeur globale), et decision finale pour un joueur.
 */
public final class Access {
    private Access() {}

    public static TrustLevel trust(Claim c, UUID player) {
        if (c == null || player == null) return TrustLevel.NONE;
        Claim top = ClaimManager.top(c);
        if (top.type == ClaimType.PLAYER && player.equals(top.owner)) return TrustLevel.OWNER;
        TrustLevel lvl = level(c, player);
        if (c != top) {
            TrustLevel parent = level(top, player);
            // les Co-Maires du claim principal gerent toujours ses parcelles
            if (parent == TrustLevel.MANAGER || c.inherit) lvl = TrustLevel.max(lvl, parent);
        }
        return lvl;
    }

    private static TrustLevel level(Claim c, UUID player) {
        TrustLevel own = c.trusted.get(player.toString());
        TrustLevel pub = c.trusted.get("*");
        // le niveau public ne donne jamais la gestion du claim
        if (pub == TrustLevel.MANAGER) pub = TrustLevel.BUILD;
        return TrustLevel.max(own, pub);
    }

    /** Valeur effective d'un flag pour la colonne visiteurs (member=false) ou membres. Jamais DEFAULT. */
    public static FlagState effective(Claim c, Flag f, boolean member) {
        if (c != null) {
            FlagState s = override(c, f, member);
            if (s == null && c.isSub() && c.inherit) s = override(ClaimManager.top(c), f, member);
            if (s != null) return s;
        }
        return Flags.globalValue(f, member);
    }

    /** Surcharge locale d'un claim, ignoree si le flag est verrouille et n'a pas ete pose par le staff (claims joueur). */
    public static FlagState override(Claim c, Flag f, boolean member) {
        FlagState s = (f.isZone() || !member ? c.visitorFlags : c.memberFlags).get(f.id());
        if (s == null || s == FlagState.DEFAULT) return null;
        if (c.type == ClaimType.PLAYER && !Flags.editable(f) && !c.staffFlags.contains(f.id())) return null;
        return s;
    }

    /** Flag de zone (environnement, flags admin) au point donne : hors claim, valeur globale. */
    public static boolean zone(Claim c, Flag f) {
        return effective(c, f, false) == FlagState.ALLOW;
    }

    /** Hors claim : toujours autorise (sauf flags de zone, voir {@link #zone}). */
    public static boolean allows(ServerPlayer p, Claim c, Flag f) {
        if (c == null) return true;
        if (Perms.bypass(p)) return true;
        TrustLevel lvl = trust(c, p.getUUID());
        if (lvl == TrustLevel.OWNER && f.ownerBypass()) return true;
        boolean member = lvl.atLeast(f.memberLevel()) && lvl != TrustLevel.NONE;
        return effective(c, f, member) == FlagState.ALLOW;
    }

    /** Peut ouvrir l'ecran de gestion et modifier ce claim (proprietaire, Co-Maire, ou staff). */
    public static boolean canManage(ServerPlayer p, Claim c) {
        if (c == null || p == null) return false;
        if (Perms.staff(p)) return true;
        TrustLevel lvl = trust(c, p.getUUID());
        return lvl.atLeast(TrustLevel.MANAGER);
    }

    /** Peut modifier ce flag dans ce claim. */
    public static boolean canEditFlag(ServerPlayer p, Claim c, Flag f) {
        if (Perms.has(p, Perms.ADMIN_CONFIG) || (c.isAdmin() && Perms.staff(p))) return true;
        if (!canManage(p, c)) return false;
        return Flags.editable(f) && c.type == ClaimType.PLAYER;
    }
}
