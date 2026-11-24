package fr.cobblepulse.claims.integration;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.battles.BattleFormat;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.battles.BattleSide;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import fr.cobblepulse.claims.Msg;
import fr.cobblepulse.claims.claim.ClaimManager;
import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.flag.Flags;
import fr.cobblepulse.claims.zone.ZoneEffects;
import kotlin.Unit;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Evenements Cobblemon : captures, combats (sauvages / dresseurs), envoi, monture, apparitions, arenes. */
public final class CobblemonHooks {
    private CobblemonHooks() {}

    public static void register() {
        CobblemonEvents.THROWN_POKEBALL_HIT.subscribe(Priority.HIGHEST, event -> {
            Entity owner = event.getPokeBall().getOwner();
            PokemonEntity target = event.getPokemon();
            if (owner instanceof ServerPlayer p && !captureAllowed(p, target)) event.cancel();
            return Unit.INSTANCE;
        });

        CobblemonEvents.BATTLE_STARTED_PRE.subscribe(Priority.HIGHEST, event -> {
            String why = battleRefusal(event.getBattle());
            if (why != null) {
                event.cancel();
                event.setReason(Component.literal(why).withStyle(ChatFormatting.RED));
                for (ServerPlayer p : event.getBattle().getPlayers()) Msg.deny(p, "battle", why);
            } else arenaHeal(event.getBattle());
            return Unit.INSTANCE;
        });

        CobblemonEvents.BATTLE_ENDED.subscribe(Priority.NORMAL, event -> {
            arenaHeal(event.getBattle());
            return Unit.INSTANCE;
        });

        CobblemonEvents.POKEMON_SENT_PRE.subscribe(Priority.HIGHEST, event -> {
            ServerPlayer p = event.getPokemon().getOwnerPlayer();
            if (p == null) return Unit.INSTANCE;
            // un Pokemon envoye pour un combat deja autorise n'est pas bloque
            if (BattleRegistry.getBattleByParticipatingPlayer(p) != null) return Unit.INSTANCE;
            int x = (int) Math.floor(event.getPosition().x), z = (int) Math.floor(event.getPosition().z);
            if (!Protection.check(p, event.getLevel(), x, z, Flags.SEND_OUT, true)) event.cancel();
            return Unit.INSTANCE;
        });

        CobblemonEvents.RIDE_EVENT_PRE.subscribe(Priority.HIGHEST, event -> {
            ServerPlayer p = event.getPlayer();
            PokemonEntity pe = event.getPokemon();
            if (!Protection.check(p, pe.level(), pe.blockPosition(), Flags.RIDE)) event.cancel();
            return Unit.INSTANCE;
        });

        CobblemonEvents.ENTITY_SPAWN.subscribe(Priority.HIGHEST, event -> {
            if (event.getEntity() instanceof PokemonEntity) {
                var pos = event.getSpawnablePosition();
                if (!Protection.allowsZone(pos.getWorld(), pos.getPosition(), Flags.WILD_SPAWN)) event.cancel();
            }
            return Unit.INSTANCE;
        });
    }

    /** Capture autorisee (flag de capture, et flag Shiny/Legendaires pour les Pokemon rares). */
    public static boolean captureAllowed(ServerPlayer p, PokemonEntity target) {
        Pokemon mon = target.getPokemon();
        if (!mon.isWild()) return true;
        if (!Protection.check(p, target.level(), target.blockPosition(), Flags.CAPTURE)) return false;
        boolean rare = mon.getShiny() || mon.isLegendary() || mon.isMythical() || mon.isUltraBeast();
        return !rare || Protection.check(p, target.level(), target.blockPosition(), Flags.CAPTURE_RARE);
    }

    /** Raison du refus d'un combat, ou null s'il est autorise. */
    public static String battleRefusal(PokemonBattle battle) {
        List<ServerPlayer> players = battle.getPlayers();
        if (battle.isPvP()) {
            for (ServerPlayer p : players) {
                if (Protection.active(p.level(), p.getX(), p.getZ(), Flags.PVP_ARENA)) continue;
                if (!Protection.check(p, p.level(), p.getBlockX(), p.getBlockZ(), Flags.BATTLE_PVP, false))
                    return "Les combats de dresseurs sont interdits ici (" + where(p) + ").";
            }
        } else if (battle.isPvW()) {
            for (ServerPlayer p : players) {
                if (!Protection.check(p, p.level(), p.getBlockX(), p.getBlockZ(), Flags.BATTLE_WILD, false))
                    return "Les combats contre les Pokémon sauvages sont interdits ici (" + where(p) + ").";
            }
        }
        return null;
    }

    private static String where(ServerPlayer p) {
        Claim c = ClaimManager.at(p.level(), p.blockPosition());
        return c == null ? "nature" : c.displayName();
    }

    /** Arene competitive : equipes soignees avant et apres le combat (aucune perte durable). */
    public static void arenaHeal(PokemonBattle battle) {
        for (ServerPlayer p : battle.getPlayers()) {
            if (Protection.active(p.level(), p.getX(), p.getZ(), Flags.PVP_ARENA)) {
                try {
                    Cobblemon.INSTANCE.getStorage().getParty(p).heal();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * Plafond de niveau d'arene (mixin BattleRegistry#startBattle) : si un joueur du combat est dans une zone
     * admin.level_cap, le format est copie avec le niveau impose (le format partage n'est jamais modifie).
     */
    public static BattleFormat levelCap(BattleFormat format, BattleSide side1, BattleSide side2) {
        int cap = -1;
        Set<UUID> seen = new HashSet<>();
        List<BattleActor> actors = new ArrayList<>();
        for (BattleActor a : side1.getActors()) actors.add(a);
        for (BattleActor a : side2.getActors()) actors.add(a);
        net.minecraft.server.MinecraftServer server = fr.cobblepulse.claims.CobblePulseClaims.server();
        if (server == null) return format;
        for (BattleActor a : actors) {
            for (UUID id : a.getPlayerUUIDs()) {
                if (!seen.add(id)) continue;
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null || !Protection.active(p.level(), p.getX(), p.getZ(), Flags.LEVEL_CAP)) continue;
                Claim c = ZoneEffects.settingsSource(ClaimManager.at(p.level(), p.blockPosition()), Flags.LEVEL_CAP);
                if (c != null) cap = Math.max(cap, c.settings.level_cap);
            }
        }
        if (cap <= 0) return format;
        return format.copy(format.getMod(), format.getBattleType(), new HashSet<>(format.getRuleSet()), format.getGen(), cap);
    }
}
