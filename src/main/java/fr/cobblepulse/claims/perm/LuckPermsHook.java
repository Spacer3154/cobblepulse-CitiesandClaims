package fr.cobblepulse.claims.perm;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.util.Tristate;

import java.util.Map;
import java.util.UUID;

/** Seule classe qui touche l'API LuckPerms (chargee uniquement si le mod est present). */
final class LuckPermsHook {
    private LuckPermsHook() {}

    /** true / false si le noeud est defini (heritage et jokers compris), null sinon ou si l'utilisateur n'est pas charge. */
    static Boolean check(UUID uuid, String node) {
        User u = LuckPermsProvider.get().getUserManager().getUser(uuid);
        if (u == null) return null;
        Tristate t = u.getCachedData().getPermissionData().checkPermission(node);
        return t == Tristate.UNDEFINED ? null : t.asBoolean();
    }

    /** Plus grande valeur n parmi les noeuds « prefix.n » accordes (prefix.unlimited = Integer.MAX_VALUE), null si aucun. */
    static Integer numeric(UUID uuid, String prefix) {
        User u = LuckPermsProvider.get().getUserManager().getUser(uuid);
        if (u == null) return null;
        Map<String, Boolean> map = u.getCachedData().getPermissionData().getPermissionMap();
        return Perms.bestNumeric(map, prefix);
    }
}
