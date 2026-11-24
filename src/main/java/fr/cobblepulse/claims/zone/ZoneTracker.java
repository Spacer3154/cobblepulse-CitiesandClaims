package fr.cobblepulse.claims.zone;

import fr.cobblepulse.claims.ClaimsConfig;
import fr.cobblepulse.claims.Msg;
import fr.cobblepulse.claims.claim.Access;
import fr.cobblepulse.claims.claim.ClaimManager;
import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.flag.Flags;
import fr.cobblepulse.claims.integration.ClanBridge;
import fr.cobblepulse.claims.perm.Perms;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Suivi des joueurs (toutes les 4 ticks) : titre / sous-titre a l'entree d'une ville ou d'un territoire, « Zone sauvage »
 * en barre d'action a la sortie, refus d'entree (move.enter_claim), elytres, vol de zone, soin a l'entree, meteo et
 * heure figees.
 */
public final class ZoneTracker {
    private static final Map<UUID, Track> TRACKS = new HashMap<>();
    /** Derniers textes envoyes (tests). */
    public static final Map<UUID, String> LAST_TITLE = new HashMap<>();

    private static final class Track {
        int top = -1, sub = -1;
        String dim;
        Vec3 safe;
        boolean flyGranted;
    }

    private ZoneTracker() {}

    public static void tick(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            try {
                update(p);
            } catch (Throwable t) {
                fr.cobblepulse.claims.CobblePulseClaims.LOG.error("[CobblePulse-Claims] Suivi de zone en erreur pour {}", p.getGameProfile().getName(), t);
            }
        }
    }

    public static void update(ServerPlayer p) {
        Track t = TRACKS.computeIfAbsent(p.getUUID(), k -> new Track());
        String dim = ClaimManager.dim(p.level());
        Claim c = ClaimManager.at(dim, p.getBlockX(), p.getBlockZ());
        Claim top = ClaimManager.top(c);
        int topId = top == null ? 0 : top.id;
        int subId = c != null && c.isSub() ? c.id : 0;
        boolean dimChanged = !dim.equals(t.dim);
        if (dimChanged) {
            t.dim = dim;
            t.safe = null;
        }
        if (topId != t.top || dimChanged) {
            if (top != null && !Perms.bypass(p) && !p.isSpectator() && !Access.allows(p, top, Flags.ENTER)) {
                if (t.safe != null && !dimChanged) {
                    p.connection.teleport(t.safe.x, t.safe.y, t.safe.z, p.getYRot(), p.getXRot());
                    if (p.getVehicle() != null) p.stopRiding();
                }
                Msg.deny(p, "enter", "Entrée interdite : " + top.displayName());
                if (t.safe != null && !dimChanged) return;
            }
            int previous = t.top;
            t.top = topId;
            if (previous != -1 || top != null) announce(p, top, previous > 0);
            if (top != null && Protection.active(p.level(), p.getX(), p.getZ(), Flags.HEAL_ON_ENTRY)) {
                if (ZoneEffects.healOnEntry(p, top)) Msg.ok(p, "Votre équipe Pokémon a été soignée.");
            }
        }
        if (subId != t.sub) {
            int prevSub = t.sub;
            t.sub = subId;
            if (subId != 0 && ClaimsConfig.get().notifications.subclaim_actionbar && prevSub != -1) {
                p.displayClientMessage(Component.literal("Parcelle : ").withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(c.displayName()).withStyle(ChatFormatting.AQUA)), true);
            } else if (subId != 0 && prevSub == -1 && top != null && ClaimsConfig.get().notifications.subclaim_actionbar) {
                p.displayClientMessage(Component.literal("Parcelle : " + c.displayName()).withStyle(ChatFormatting.AQUA), true);
            }
        }
        if (top == null || Access.allows(p, top, Flags.ENTER) || Perms.bypass(p)) {
            if (p.onGround() || p.isInWater() || p.isPassenger() || p.getAbilities().flying) t.safe = p.position();
        }
        // elytres
        if (p.isFallFlying() && c != null && !Access.allows(p, c, Flags.ELYTRA)) {
            p.stopFallFlying();
            Msg.deny(p, "elytra", "Vol en élytres interdit ici.");
        }
        // vol de zone
        boolean fly = c != null && Protection.active(p.level(), p.getX(), p.getZ(), Flags.FLY);
        boolean survival = p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL || p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE;
        if (fly && survival && !p.getAbilities().mayfly) {
            p.getAbilities().mayfly = true;
            p.onUpdateAbilities();
            t.flyGranted = true;
            Msg.info(p, "Vol autorisé dans cette zone.");
        } else if (!fly && t.flyGranted) {
            t.flyGranted = false;
            if (survival) {
                boolean wasFlying = p.getAbilities().flying;
                p.getAbilities().mayfly = false;
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
                if (wasFlying || !p.onGround()) ZoneEffects.setFallGrace(p);
                Msg.info(p, "Vous quittez la zone de vol.");
            }
        }
        ZoneEffects.updateWeather(p);
        ZoneEffects.updateTime(p);
    }

    /** Titre / sous-titre / barre d'action du cahier des charges. */
    public static void announce(ServerPlayer p, Claim top, boolean fromClaim) {
        ClaimsConfig.Notifications n = ClaimsConfig.get().notifications;
        if (!n.enabled) return;
        if (top == null) {
            if (fromClaim) {
                p.displayClientMessage(Component.literal(n.wilderness).withStyle(ChatFormatting.GREEN), true);
                LAST_TITLE.put(p.getUUID(), "BAR:" + n.wilderness);
            }
            return;
        }
        String[] tx = entryTexts(top);
        p.connection.send(new ClientboundSetTitlesAnimationPacket(n.fade_in_ticks, n.stay_ticks, n.fade_out_ticks));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(tx[1]).withStyle(ChatFormatting.GRAY)));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(tx[0]).withStyle(top.isAdmin() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD)));
        LAST_TITLE.put(p.getUUID(), tx[0] + "|" + tx[1]);
    }

    /** [titre, sous-titre] pour un claim principal. */
    public static String[] entryTexts(Claim top) {
        boolean named = top.name != null && !top.name.isBlank();
        String tag = top.isAdmin() ? null : ClanBridge.tag(top.owner);
        String clan = tag == null ? "Sans clan" : "[" + tag + "]";
        if (named) {
            String sub = top.isAdmin() ? "Zone Administrative" : "Propriétaire : " + top.ownerName + " | Clan : " + clan;
            return new String[]{top.name, sub};
        }
        if (top.isAdmin()) return new String[]{"Zone Administrative", "Administration du serveur"};
        return new String[]{"Territoire de " + top.ownerName, "Clan : " + clan};
    }

    public static void forget(UUID id) {
        TRACKS.remove(id);
        LAST_TITLE.remove(id);
    }

    /** Force la prochaine mise a jour a reannoncer la zone (changement de nom, conversion). */
    public static void reset(UUID id) {
        Track t = TRACKS.get(id);
        if (t != null) t.top = -2;
    }
}
