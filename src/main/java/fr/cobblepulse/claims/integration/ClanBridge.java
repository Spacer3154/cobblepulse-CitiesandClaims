package fr.cobblepulse.claims.integration;

import fr.cobblepulse.claims.CobblePulseClaims;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lecture du clan d'un joueur dans le mod Clans de CobblePulse (id « cobblepulse »), sans dependance de compilation :
 * fr.cobblepulse.clan.ClanManager.clanOf(UUID) -> ClanData (id, name, tag). Mis en cache 10 secondes.
 */
public final class ClanBridge {
    public record ClanInfo(String id, String name, String tag) {}

    private static final ClanInfo NONE = new ClanInfo(null, null, null);
    private static final Map<UUID, ClanInfo> CACHE = new HashMap<>();
    private static final Map<UUID, Long> CACHE_TIME = new HashMap<>();
    private static Method clanOf;
    private static Field fId, fName, fTag;
    private static boolean resolved, available;

    private ClanBridge() {}

    private static synchronized boolean resolve() {
        if (resolved) return available;
        resolved = true;
        if (!FabricLoader.getInstance().isModLoaded("cobblepulse")) return false;
        try {
            Class<?> mgr = Class.forName("fr.cobblepulse.clan.ClanManager");
            Class<?> data = Class.forName("fr.cobblepulse.data.ClanData");
            clanOf = mgr.getMethod("clanOf", UUID.class);
            fId = data.getField("id");
            fName = data.getField("name");
            fTag = data.getField("tag");
            available = true;
            CobblePulseClaims.LOG.info("[CobblePulse-Claims] Clans CobblePulse détectés : tags de clan et territoires de clan actifs");
        } catch (Throwable t) {
            CobblePulseClaims.LOG.warn("[CobblePulse-Claims] Mod Clans présent mais API introuvable : {}", t.toString());
        }
        return available;
    }

    public static boolean available() {
        return resolve();
    }

    /** Clan du joueur, ou null. */
    public static ClanInfo clanOf(UUID player) {
        if (player == null || !resolve()) return null;
        long now = System.currentTimeMillis();
        Long t = CACHE_TIME.get(player);
        if (t != null && now - t < 10_000) {
            ClanInfo c = CACHE.get(player);
            return c == NONE ? null : c;
        }
        ClanInfo info = NONE;
        try {
            Object d = clanOf.invoke(null, player);
            if (d != null) info = new ClanInfo(String.valueOf(fId.get(d)), String.valueOf(fName.get(d)), String.valueOf(fTag.get(d)));
        } catch (Throwable e) {
            info = NONE;
        }
        CACHE.put(player, info);
        CACHE_TIME.put(player, now);
        return info == NONE ? null : info;
    }

    public static String tag(UUID player) {
        ClanInfo c = clanOf(player);
        return c == null ? null : c.tag();
    }

    public static boolean sameClan(UUID a, UUID b) {
        if (a == null || b == null) return false;
        ClanInfo ca = clanOf(a), cb = clanOf(b);
        return ca != null && cb != null && ca.id() != null && ca.id().equals(cb.id());
    }

    public static void invalidate() {
        CACHE.clear();
        CACHE_TIME.clear();
    }
}
