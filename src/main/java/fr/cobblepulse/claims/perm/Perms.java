package fr.cobblepulse.claims.perm;

import fr.cobblepulse.claims.ClaimsConfig;
import fr.cobblepulse.claims.CobblePulseClaims;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Noeuds LuckPerms du mod (section 9 du cahier des charges). Noeuds joueur : accordes par defaut (un refus explicite
 * dans LuckPerms l'emporte). Noeuds staff / admin : definis dans LuckPerms, sinon niveau d'operateur (2 par defaut).
 */
public final class Perms {
    public static final String USE = "cobblepulse.claims.use";
    public static final String CREATE = "cobblepulse.claims.create";
    public static final String CITY_NAME = "cobblepulse.claims.city_name";
    public static final String SUBCLAIM = "cobblepulse.claims.subclaim";
    public static final String TRUST = "cobblepulse.claims.trust";
    public static final String ACCRUE = "cobblepulse.claims.accrue";
    public static final String LIMIT_CLAIMS = "cobblepulse.claims.limit.max_claims";
    public static final String LIMIT_BLOCKS = "cobblepulse.claims.limit.max_blocks";
    public static final String LIMIT_PER_INTERVAL = "cobblepulse.claims.limit.blocks_per_interval";
    public static final String BYPASS = "cobblepulse.staff.bypass";
    public static final String INSPECT_ALL = "cobblepulse.staff.inspect_all";
    public static final String CONVERT_ADMIN = "cobblepulse.staff.convert_admin";
    public static final String DELETE_OTHERS = "cobblepulse.staff.delete_others";
    public static final String ADMIN_CONFIG = "cobblepulse.admin.config";
    public static final String ADMIN_WORLDBORDER = "cobblepulse.admin.worldborder";
    public static final String ADMIN_GIVE_BLOCKS = "cobblepulse.admin.give_blocks";

    private static final boolean LUCKPERMS = FabricLoader.getInstance().isModLoaded("luckperms");
    private static final Set<String> PLAYER_NODES = Set.of(USE, CREATE, CITY_NAME, SUBCLAIM, TRUST, ACCRUE);
    /** Staff ayant desactive le contournement (/claimadmin bypass off). */
    private static final Set<UUID> BYPASS_OFF = ConcurrentHashMap.newKeySet();
    /** Tests : noeuds forces pour des joueurs factices. */
    private static final Map<UUID, Map<String, Boolean>> OVERRIDES = new ConcurrentHashMap<>();
    private static boolean warned;

    private Perms() {}

    public static boolean has(ServerPlayer p, String node) {
        if (p == null) return false;
        Map<String, Boolean> ov = OVERRIDES.get(p.getUUID());
        if (ov != null && ov.containsKey(node)) return ov.get(node);
        Boolean lp = luckperms(p.getUUID(), node);
        if (lp != null) return lp;
        if (PLAYER_NODES.contains(node)) return true;
        if (p instanceof FakePlayer) return false;
        return p.hasPermissions(ClaimsConfig.get().permissions.op_level_fallback);
    }

    public static boolean has(CommandSourceStack src, String node) {
        if (src.getEntity() instanceof ServerPlayer p) return has(p, node);
        return src.hasPermission(2);
    }

    /** Valeur numerique d'un noeud a suffixe (max_claims, max_blocks, blocks_per_interval), ou la valeur par defaut. */
    public static int numeric(ServerPlayer p, String prefix, int def) {
        if (p == null) return def;
        Map<String, Boolean> ov = OVERRIDES.get(p.getUUID());
        if (ov != null) {
            Integer v = bestNumeric(ov, prefix);
            if (v != null) return v;
        }
        if (LUCKPERMS) {
            try {
                Integer v = LuckPermsHook.numeric(p.getUUID(), prefix);
                if (v != null) return v;
            } catch (Throwable t) {
                warn(t);
            }
        }
        return def;
    }

    static Integer bestNumeric(Map<String, Boolean> map, String prefix) {
        Integer best = null;
        String pre = prefix.toLowerCase(Locale.ROOT) + ".";
        for (Map.Entry<String, Boolean> e : map.entrySet()) {
            if (!Boolean.TRUE.equals(e.getValue())) continue;
            String k = e.getKey().toLowerCase(Locale.ROOT);
            if (!k.startsWith(pre)) continue;
            String suf = k.substring(pre.length());
            int v;
            if (suf.equals("unlimited") || suf.equals("illimite") || suf.equals("*")) v = Integer.MAX_VALUE;
            else {
                try {
                    v = Integer.parseInt(suf);
                } catch (NumberFormatException ex) {
                    continue;
                }
            }
            if (best == null || v > best) best = v;
        }
        return best;
    }

    private static Boolean luckperms(UUID id, String node) {
        if (!LUCKPERMS) return null;
        try {
            return LuckPermsHook.check(id, node);
        } catch (Throwable t) {
            warn(t);
            return null;
        }
    }

    private static void warn(Throwable t) {
        if (!warned) {
            warned = true;
            CobblePulseClaims.LOG.warn("[CobblePulse-Claims] API LuckPerms indisponible, repli sur les valeurs par défaut : {}", t.toString());
        }
    }

    /** Contournement actif : noeud cobblepulse.staff.bypass et contournement non desactive par le joueur. */
    public static boolean bypass(ServerPlayer p) {
        return p != null && !BYPASS_OFF.contains(p.getUUID()) && has(p, BYPASS);
    }

    public static boolean setBypass(ServerPlayer p, boolean on) {
        if (on) BYPASS_OFF.remove(p.getUUID());
        else BYPASS_OFF.add(p.getUUID());
        return on;
    }

    public static boolean bypassEnabled(UUID id) {
        return !BYPASS_OFF.contains(id);
    }

    /** Staff pouvant tout inspecter et gerer n'importe quel claim depuis les ecrans. */
    public static boolean staff(ServerPlayer p) {
        return has(p, INSPECT_ALL) || has(p, ADMIN_CONFIG);
    }

    public static void override(UUID id, String node, Boolean value) {
        Map<String, Boolean> m = OVERRIDES.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
        if (value == null) m.remove(node);
        else m.put(node, value);
    }

    public static void clearOverrides(UUID id) {
        OVERRIDES.remove(id);
    }

    public static Set<String> allNodes() {
        return new HashSet<>(Set.of(USE, CREATE, CITY_NAME, SUBCLAIM, TRUST, ACCRUE, BYPASS, INSPECT_ALL, CONVERT_ADMIN, DELETE_OTHERS,
                ADMIN_CONFIG, ADMIN_WORLDBORDER, ADMIN_GIVE_BLOCKS));
    }
}
