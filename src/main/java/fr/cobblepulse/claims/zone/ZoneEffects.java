package fr.cobblepulse.claims.zone;

import com.cobblemon.mod.common.Cobblemon;
import fr.cobblepulse.claims.ClaimsConfig;
import fr.cobblepulse.claims.claim.ClaimManager;
import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Flags d'administration appliques aux joueurs : invulnerabilite, faim, inventaire conserve, vol, soin a l'entree,
 * meteo et heure figees (envoyees au seul joueur present dans la zone, la meteo et l'heure du monde restent intactes).
 */
public final class ZoneEffects {
    /** Joueurs morts dans une zone admin.keep_inventory (inventaire et XP restaures a la reapparition). */
    public static final Set<UUID> KEEP = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> FALL_GRACE = new HashMap<>();
    private static final Map<UUID, Long> LAST_HEAL = new HashMap<>();
    /** Joueurs dont la meteo / l'heure sont figees par leur zone (valeur envoyee). */
    private static final Map<UUID, String> WEATHER = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> TIME = new ConcurrentHashMap<>();

    private ZoneEffects() {}

    public static boolean invulnerable(ServerPlayer p) {
        return Protection.active(p.level(), p.getX(), p.getZ(), Flags.INVULNERABLE);
    }

    public static boolean hungerBlocked(Player p) {
        return p instanceof ServerPlayer sp && !Protection.allowsZone(sp.level(), sp.blockPosition(), Flags.HUNGER);
    }

    public static boolean keepInventory(Player p) {
        return p instanceof ServerPlayer sp && Protection.active(sp.level(), sp.getX(), sp.getZ(), Flags.KEEP_INVENTORY);
    }

    public static void setFallGrace(ServerPlayer p) {
        FALL_GRACE.put(p.getUUID(), System.currentTimeMillis() + ClaimsConfig.get().admin_zones.fly_fall_grace_seconds * 1000L);
    }

    public static boolean inFallGrace(ServerPlayer p) {
        Long until = FALL_GRACE.get(p.getUUID());
        if (until == null) return false;
        if (System.currentTimeMillis() > until || p.onGround()) {
            if (System.currentTimeMillis() > until) FALL_GRACE.remove(p.getUUID());
            return System.currentTimeMillis() <= until;
        }
        return true;
    }

    /** Soin de l'equipe Cobblemon a l'entree (delai anti-abus). Renvoie true si soigne. */
    public static boolean healOnEntry(ServerPlayer p, Claim c) {
        long now = System.currentTimeMillis();
        Long last = LAST_HEAL.get(p.getUUID());
        if (last != null && now - last < ClaimsConfig.get().admin_zones.heal_cooldown_seconds * 1000L) return false;
        LAST_HEAL.put(p.getUUID(), now);
        try {
            Cobblemon.INSTANCE.getStorage().getParty(p).heal();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ meteo / heure

    /** Meteo voulue par la zone du joueur (CLEAR / RAIN / THUNDER) ou null. */
    public static String zoneWeather(ServerPlayer p) {
        if (!Protection.active(p.level(), p.getX(), p.getZ(), Flags.WEATHER)) return null;
        Claim c = settingsSource(ClaimManager.at(p.level(), p.blockPosition()), Flags.WEATHER);
        return c == null ? null : c.settings.weather;
    }

    /** Reglages du claim qui active le flag : la parcelle si elle le surcharge, sinon le claim principal. */
    public static Claim settingsSource(Claim c, fr.cobblepulse.claims.flag.Flag f) {
        if (c == null) return null;
        if (c.isSub() && fr.cobblepulse.claims.claim.Access.override(c, f, false) == null) return ClaimManager.top(c);
        return c;
    }

    public static Long zoneTime(ServerPlayer p) {
        if (!Protection.active(p.level(), p.getX(), p.getZ(), Flags.TIME)) return null;
        Claim c = settingsSource(ClaimManager.at(p.level(), p.blockPosition()), Flags.TIME);
        return c == null ? null : c.settings.time;
    }

    public static void updateWeather(ServerPlayer p) {
        String want = zoneWeather(p);
        String had = WEATHER.get(p.getUUID());
        if (want == null) {
            if (had != null) {
                WEATHER.remove(p.getUUID());
                sendWorldWeather(p);
            }
            return;
        }
        if (want.equals(had)) return;
        WEATHER.put(p.getUUID(), want);
        boolean rain = !"CLEAR".equalsIgnoreCase(want);
        boolean thunder = "THUNDER".equalsIgnoreCase(want);
        p.connection.send(new ClientboundGameEventPacket(rain ? ClientboundGameEventPacket.START_RAINING : ClientboundGameEventPacket.STOP_RAINING, 0));
        p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rain ? 1f : 0f));
        p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, thunder ? 1f : 0f));
    }

    private static void sendWorldWeather(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        p.connection.send(new ClientboundGameEventPacket(l.isRaining() ? ClientboundGameEventPacket.START_RAINING : ClientboundGameEventPacket.STOP_RAINING, 0));
        p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, l.getRainLevel(1f)));
        p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, l.getThunderLevel(1f)));
    }

    public static void updateTime(ServerPlayer p) {
        Long want = zoneTime(p);
        Long had = TIME.get(p.getUUID());
        if (want == null) {
            if (had != null) {
                TIME.remove(p.getUUID());
                ServerLevel l = p.serverLevel();
                p.connection.send(new ClientboundSetTimePacket(l.getGameTime(), l.getDayTime(), l.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)));
            }
            return;
        }
        if (want.equals(had)) return;
        TIME.put(p.getUUID(), want);
        p.connection.send(new ClientboundSetTimePacket(p.serverLevel().getGameTime(), want, false));
    }

    public static boolean weatherLocked(ServerPlayer p) {
        return WEATHER.containsKey(p.getUUID());
    }

    /** Diffusion de la meteo du monde : les joueurs a meteo figee sont exclus (mixin ServerLevel#advanceWeatherCycle). */
    public static void broadcastWeather(PlayerList list, Packet<?> packet, ResourceKey<Level> dim) {
        for (ServerPlayer p : list.getPlayers()) {
            if (dim != null && p.level().dimension() != dim) continue;
            if (WEATHER.containsKey(p.getUUID())) continue;
            p.connection.send(packet);
        }
    }

    /** Diffusion de l'heure : les joueurs a heure figee recoivent leur heure (mixin MinecraftServer#synchronizeTime). */
    public static void broadcastTime(PlayerList list, Packet<?> packet, ResourceKey<Level> dim) {
        for (ServerPlayer p : list.getPlayers()) {
            if (dim != null && p.level().dimension() != dim) continue;
            Long t = TIME.get(p.getUUID());
            if (t != null && packet instanceof ClientboundSetTimePacket st) p.connection.send(new ClientboundSetTimePacket(st.getGameTime(), t, false));
            else p.connection.send(packet);
        }
    }

    public static String sentWeather(UUID id) {
        return WEATHER.get(id);
    }

    public static Long sentTime(UUID id) {
        return TIME.get(id);
    }

    public static void forget(UUID id) {
        WEATHER.remove(id);
        TIME.remove(id);
        FALL_GRACE.remove(id);
        KEEP.remove(id);
    }
}
