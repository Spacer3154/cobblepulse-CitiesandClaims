package fr.cobblepulse.claims;

import fr.cobblepulse.claims.border.WorldBorders;
import fr.cobblepulse.claims.claim.ClaimManager;
import fr.cobblepulse.claims.command.AdminCommands;
import fr.cobblepulse.claims.command.ClaimCommands;
import fr.cobblepulse.claims.data.PlayerClaimData;
import fr.cobblepulse.claims.economy.BlockEconomy;
import fr.cobblepulse.claims.flag.Flags;
import fr.cobblepulse.claims.integration.ClanBridge;
import fr.cobblepulse.claims.integration.CobblemonHooks;
import fr.cobblepulse.claims.net.ClaimsNet;
import fr.cobblepulse.claims.perm.Perms;
import fr.cobblepulse.claims.protect.ProtectionEvents;
import fr.cobblepulse.claims.storage.Store;
import fr.cobblepulse.claims.test.SelfTest;
import fr.cobblepulse.claims.tools.FakeVisuals;
import fr.cobblepulse.claims.tools.ModItems;
import fr.cobblepulse.claims.tools.WandService;
import fr.cobblepulse.claims.zone.ZoneEffects;
import fr.cobblepulse.claims.zone.ZoneTracker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CobblePulse Cities & Claims : claims a la balise (GriefPrevention), permissions fines (Flan), villes et parcelles,
 * economie de blocs passive, WorldBorders par dimension, integrations Cobblemon, Clans CobblePulse et cartes.
 */
public final class CobblePulseClaims implements ModInitializer {
    public static final String MOD_ID = "cobblepulse_claims";
    public static final Logger LOG = LoggerFactory.getLogger("CobblePulse-Claims");
    private static MinecraftServer server;
    private static long ticks;

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public static MinecraftServer server() {
        return server;
    }

    @Override
    public void onInitialize() {
        ClaimsConfig.load();
        Flags.load();
        WorldBorders.load();
        ModItems.register();
        ClaimsNet.registerTypes();
        ClaimsNet.registerServer();
        ProtectionEvents.register();
        CobblemonHooks.register();
        CommandRegistrationCallback.EVENT.register((d, registry, env) -> {
            ClaimCommands.register(d);
            AdminCommands.register(d);
        });

        ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            Store.open(s);
            ClaimManager.load();
            BlockEconomy.load();
            WorldBorders.registerLevels(s);
            ClanBridge.available();
            LOG.info("[CobblePulse-Claims] Prêt : {} permissions de claim, {} WorldBorders, {} joueurs connus", Flags.all().size(),
                    WorldBorders.all().size(), BlockEconomy.all().size());
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            for (ServerPlayer p : s.getPlayerList().getPlayers()) BlockEconomy.onLeave(p);
            Store.close();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
            ServerPlayer p = handler.getPlayer();
            boolean fresh = BlockEconomy.onJoin(p);
            PlayerClaimData d = BlockEconomy.get(p.getUUID());
            if (fresh) LOG.info("[CobblePulse-Claims] Nouveau joueur {} : {} blocs de départ", p.getGameProfile().getName(), d.accrued);
            if (d != null && !d.toolsGiven && ClaimsConfig.get().claims.give_tools_on_first_join && Perms.has(p, Perms.USE)) {
                d.toolsGiven = true;
                p.getInventory().placeItemBackInInventory(new ItemStack(ModItems.CLAIM_WAND));
                p.getInventory().placeItemBackInInventory(new ItemStack(ModItems.CLAIM_INSPECTOR));
                Store.savePlayer(d);
                Msg.info(p, "Bienvenue ! Voici la Balise de Territoire et la Lunette Topographique. /claim pour l'aide.");
            }
            s.execute(() -> {
                ClaimsNet.sendBorder(p);
                ClaimsNet.sendFullSync(p);
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> {
            ServerPlayer p = handler.getPlayer();
            BlockEconomy.onLeave(p);
            WandService.forget(p.getUUID());
            ZoneTracker.forget(p.getUUID());
            ZoneEffects.forget(p.getUUID());
            FakeVisuals.forget(p.getUUID());
            ClaimsNet.forget(p.getUUID());
            Msg.forget(p.getUUID());
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((p, from, to) -> {
            ClaimsNet.sendBorder(p);
            ClaimsNet.sendFullSync(p);
            WandService.forget(p.getUUID());
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldP, newP, alive) -> {
            ClaimsNet.sendBorder(newP);
            ClaimsNet.sendFullSync(newP);
        });
        ServerPlayerEvents.COPY_FROM.register((oldP, newP, alive) -> {
            if (alive || !ZoneEffects.KEEP.remove(oldP.getUUID())) return;
            if (oldP.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;
            newP.getInventory().replaceWith(oldP.getInventory());
            newP.experienceLevel = oldP.experienceLevel;
            newP.totalExperience = oldP.totalExperience;
            newP.experienceProgress = oldP.experienceProgress;
            newP.setScore(oldP.getScore());
        });

        ServerTickEvents.END_SERVER_TICK.register(s -> {
            ticks++;
            if (ticks % 4 == 0) ZoneTracker.tick(s);
            if (ticks % 5 == 0) WorldBorders.tick(s);
            if (ticks % 20 == 0) {
                BlockEconomy.tickSecond(s);
                WorldBorders.tickEntities(s);
                FakeVisuals.tick(s);
            }
            if (ticks % 600 == 0) {
                ClanBridge.invalidate();
                for (ServerPlayer p : s.getPlayerList().getPlayers()) ClaimsNet.refreshIfChanged(p);
            }
        });

        if (SelfTest.ENABLED) SelfTest.install();
        LOG.info("[CobblePulse-Claims] Cités & Territoires initialisé ({} permissions de claim)", Flags.all().size());
    }
}
