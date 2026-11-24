package fr.cobblepulse.claims.economy;

import fr.cobblepulse.claims.ClaimsConfig;
import fr.cobblepulse.claims.claim.ClaimManager;
import fr.cobblepulse.claims.data.PlayerClaimData;
import fr.cobblepulse.claims.perm.Perms;
import fr.cobblepulse.claims.storage.Store;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Attribution passive de blocs de claim : un palier (150 blocs par defaut) toutes les 30 minutes de temps de jeu
 * effectif (hors AFK). Le minuteur est sauvegarde en continu : une deconnexion ne remet pas les minutes a zero. Le
 * cumul passif s'arrete au plafond (5 000 par defaut, ajustable par grade) ; les credits du staff passent au-dela.
 */
public final class BlockEconomy {
    private static final Map<UUID, PlayerClaimData> DATA = new HashMap<>();
    private static final Map<UUID, Activity> ACTIVITY = new HashMap<>();
    private static int saveCounter;

    private static final class Activity {
        Vec3 pos;
        float yaw, pitch;
        long lastActive = System.currentTimeMillis();
    }

    private BlockEconomy() {}

    public static void load() {
        DATA.clear();
        for (PlayerClaimData d : Store.loadPlayers()) DATA.put(d.uuid, d);
    }

    public static PlayerClaimData get(UUID id) {
        return DATA.get(id);
    }

    public static Collection<PlayerClaimData> all() {
        return DATA.values();
    }

    public static PlayerClaimData byName(String name) {
        if (name == null) return null;
        String n = name.toLowerCase(Locale.ROOT);
        for (PlayerClaimData d : DATA.values()) if (d.name != null && d.name.toLowerCase(Locale.ROOT).equals(n)) return d;
        return null;
    }

    /** Premiere connexion : capital de depart. Renvoie true si le joueur est nouveau. */
    public static boolean onJoin(ServerPlayer p) {
        PlayerClaimData d = DATA.get(p.getUUID());
        boolean fresh = d == null;
        String name = p.getGameProfile().getName();
        if (fresh) {
            d = new PlayerClaimData();
            d.uuid = p.getUUID();
            d.name = name;
            d.firstJoin = System.currentTimeMillis();
            d.accrued = ClaimsConfig.get().economy.initial_blocks;
            DATA.put(d.uuid, d);
        } else if (!name.equals(d.name)) {
            d.name = name;
            ClaimManager.refreshNames(p.getUUID(), name);
        }
        d.lastSeen = System.currentTimeMillis();
        ACTIVITY.put(p.getUUID(), new Activity());
        Store.savePlayer(d);
        return fresh;
    }

    public static void onLeave(ServerPlayer p) {
        PlayerClaimData d = DATA.get(p.getUUID());
        if (d != null) {
            d.lastSeen = System.currentTimeMillis();
            Store.savePlayer(d);
        }
        ACTIVITY.remove(p.getUUID());
    }

    /** Toute action du joueur (clic, chat, commande) le sort de l'AFK. */
    public static void markActive(ServerPlayer p) {
        Activity a = ACTIVITY.get(p.getUUID());
        if (a != null) a.lastActive = System.currentTimeMillis();
    }

    public static boolean isAfk(ServerPlayer p) {
        int afk = ClaimsConfig.get().economy.afk_minutes;
        if (afk <= 0) return false;
        Activity a = ACTIVITY.get(p.getUUID());
        return a != null && System.currentTimeMillis() - a.lastActive > afk * 60_000L;
    }

    public static int cap(ServerPlayer p) {
        return Perms.numeric(p, Perms.LIMIT_BLOCKS, ClaimsConfig.get().economy.max_accrued_blocks);
    }

    public static int perInterval(ServerPlayer p) {
        return Perms.numeric(p, Perms.LIMIT_PER_INTERVAL, ClaimsConfig.get().economy.blocks_per_interval);
    }

    public static long intervalMs() {
        return ClaimsConfig.get().economy.interval_minutes * 60_000L;
    }

    /** Appele chaque seconde. */
    public static void tickSecond(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Activity a = ACTIVITY.computeIfAbsent(p.getUUID(), k -> new Activity());
            Vec3 pos = p.position();
            if (a.pos == null || pos.distanceToSqr(a.pos) > 0.01 || Math.abs(p.getYRot() - a.yaw) > 0.5f || Math.abs(p.getXRot() - a.pitch) > 0.5f) {
                if (a.pos != null) a.lastActive = System.currentTimeMillis();
                a.pos = pos;
                a.yaw = p.getYRot();
                a.pitch = p.getXRot();
            }
            accrue(p, 1000L);
        }
        if (++saveCounter >= 30) {
            saveCounter = 0;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                PlayerClaimData d = DATA.get(p.getUUID());
                if (d != null) Store.savePlayer(d);
            }
        }
    }

    /** Ajoute du temps de jeu effectif ; renvoie le nombre de blocs gagnes. */
    public static int accrue(ServerPlayer p, long ms) {
        PlayerClaimData d = DATA.get(p.getUUID());
        if (d == null || isAfk(p)) return 0;
        d.playedMs += ms;
        if (!Perms.has(p, Perms.ACCRUE)) return 0;
        int cap = cap(p);
        if (d.accrued >= cap) {
            d.progressMs = 0;
            return 0;
        }
        d.progressMs += ms;
        long interval = intervalMs();
        int gained = 0;
        while (d.progressMs >= interval && d.accrued < cap) {
            d.progressMs -= interval;
            int add = Math.min(perInterval(p), cap - d.accrued);
            d.accrued += add;
            gained += add;
        }
        if (d.accrued >= cap) d.progressMs = 0;
        if (gained > 0) {
            Store.savePlayer(d);
            fr.cobblepulse.claims.Msg.ok(p, "+" + gained + " blocs de claim (solde : " + available(p.getUUID()) + " disponibles).");
        }
        return gained;
    }

    public static long available(UUID id) {
        PlayerClaimData d = DATA.get(id);
        long total = d == null ? 0 : d.total();
        return total - ClaimManager.usedBlocks(id);
    }

    /** Millisecondes avant le prochain palier, -1 si le plafond est atteint ou si le joueur ne cumule pas. */
    public static long untilNext(ServerPlayer p) {
        PlayerClaimData d = DATA.get(p.getUUID());
        if (d == null || !Perms.has(p, Perms.ACCRUE) || d.accrued >= cap(p)) return -1;
        return Math.max(0, intervalMs() - d.progressMs);
    }

    /** Credit / debit du staff (hors plafond). */
    public static void addBonus(PlayerClaimData d, int amount) {
        d.bonus += amount;
        Store.savePlayer(d);
    }

    /** Fixe le total du joueur (passif + bonus) en ajustant le bonus. */
    public static void setTotal(PlayerClaimData d, int total) {
        d.bonus = total - d.accrued;
        Store.savePlayer(d);
    }

    /** Joueur inconnu (jamais connecte) : creation pour le staff. */
    public static PlayerClaimData getOrCreate(UUID id, String name) {
        return DATA.computeIfAbsent(id, k -> {
            PlayerClaimData d = new PlayerClaimData();
            d.uuid = id;
            d.name = name;
            d.firstJoin = System.currentTimeMillis();
            d.accrued = ClaimsConfig.get().economy.initial_blocks;
            return d;
        });
    }

    public static void forgetForTests(UUID id) {
        DATA.remove(id);
        ACTIVITY.remove(id);
    }

    public static void setLastActiveForTests(ServerPlayer p, long ms) {
        ACTIVITY.computeIfAbsent(p.getUUID(), k -> new Activity()).lastActive = ms;
    }
}
