package fr.cobblepulse.claims.test;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import fr.cobblepulse.claims.ClaimsConfig;
import fr.cobblepulse.claims.CobblePulseClaims;
import fr.cobblepulse.claims.border.WorldBorders;
import fr.cobblepulse.claims.claim.Access;
import fr.cobblepulse.claims.claim.ClaimManager;
import fr.cobblepulse.claims.claim.ClaimOps;
import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.claim.Relation;
import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.data.ClaimType;
import fr.cobblepulse.claims.data.CommandRule;
import fr.cobblepulse.claims.data.FlagState;
import fr.cobblepulse.claims.data.PlayerClaimData;
import fr.cobblepulse.claims.data.TrustLevel;
import fr.cobblepulse.claims.economy.BlockEconomy;
import fr.cobblepulse.claims.flag.Flag;
import fr.cobblepulse.claims.flag.Flags;
import fr.cobblepulse.claims.integration.ClanBridge;
import fr.cobblepulse.claims.integration.CobblemonHooks;
import fr.cobblepulse.claims.net.Actions;
import fr.cobblepulse.claims.net.Views;
import fr.cobblepulse.claims.perm.Perms;
import fr.cobblepulse.claims.protect.CommandGuard;
import fr.cobblepulse.claims.protect.Interactions;
import fr.cobblepulse.claims.storage.Store;
import fr.cobblepulse.claims.tools.FakeVisuals;
import fr.cobblepulse.claims.tools.InspectorService;
import fr.cobblepulse.claims.tools.ModItems;
import fr.cobblepulse.claims.tools.WandService;
import fr.cobblepulse.claims.zone.ZoneEffects;
import fr.cobblepulse.claims.zone.ZoneTracker;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Auto-tests serveur (CI, -Dcobblepulse_claims.selftest=true puis /claimadmin selftest). Phase 1 : referentiel, economie,
 * balise (creation, refus, redimensionnement, parcelles), confiance, flags et verrous, protections reelles (casse, coffres,
 * explosions, feu, liquides, pistons, ramassage, lacher, commandes, PvP), Cobblemon, claims admin et flags de zone, WorldBorders,
 * Clans, Ranch, index spatial. Phase 2 (apres redemarrage) : persistance.
 */
public final class SelfTest {
    public static final boolean ENABLED = Boolean.getBoolean("cobblepulse_claims.selftest");
    private static final int PHASE = Integer.getInteger("cobblepulse_claims.selftest.phase", 1);
    private static final String TAG = "[CobblePulse-Claims][selftest] ";

    private record Task(int at, Runnable run) {}

    private static final List<Task> TASKS = new ArrayList<>();
    private static int pass, fail, tick, cursor;
    private static boolean running;
    private static MinecraftServer server;
    private static ServerLevel level;
    private static FakePlayer alice, bob, carol, staff;
    private static Claim home;
    private static final int X0 = 5000, Z0 = 5000, Y = 200;

    private SelfTest() {}

    public static void install() {
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (!running) return;
            tick++;
            List<Task> due = new ArrayList<>();
            TASKS.removeIf(t -> {
                if (t.at() <= tick) {
                    due.add(t);
                    return true;
                }
                return false;
            });
            for (Task t : due) {
                try {
                    t.run().run();
                } catch (Throwable e) {
                    check("exception_t" + tick, false, e.toString());
                    CobblePulseClaims.LOG.error(TAG + "erreur", e);
                }
            }
            if (TASKS.isEmpty()) {
                running = false;
                CobblePulseClaims.LOG.info(TAG + "SELFTEST SUMMARY (phase {}): {} pass, {} fail", PHASE, pass, fail);
                CobblePulseClaims.LOG.info(TAG + "SELFTEST DONE");
            }
        });
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) pass++;
        else fail++;
        CobblePulseClaims.LOG.info(TAG + "{} {}{}", ok ? "PASS" : "FAIL", name, detail == null || detail.isEmpty() ? "" : " - " + detail);
    }

    /** Planifie une etape « gap » ticks apres la precedente (les clics de balise sont espaces de plus de 150 ms). */
    private static void step(int gap, Runnable r) {
        cursor += gap;
        TASKS.add(new Task(cursor, r));
    }

    private static FakePlayer fake(String name) {
        UUID id = UUID.nameUUIDFromBytes(("cpclaims-selftest-" + name).getBytes(StandardCharsets.UTF_8));
        FakePlayer p = FakePlayer.get(level, new GameProfile(id, name));
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    private static void at(FakePlayer p, double x, double z) {
        p.setPos(x + 0.5, Y + 1, z + 0.5);
        p.setOnGround(true);
    }

    private static void platform(int x1, int z1, int x2, int z2) {
        for (int cx = x1 >> 4; cx <= x2 >> 4; cx++) for (int cz = z1 >> 4; cz <= z2 >> 4; cz++) level.getChunk(cx, cz);
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                level.setBlock(new BlockPos(x, Y, z), Blocks.STONE.defaultBlockState(), 2);
                for (int y = Y + 1; y <= Y + 4; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    private static void click(FakePlayer p, int x, int z) {
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CLAIM_WAND));
        BlockHitResult hit = new BlockHitResult(new Vec3(x + 0.5, Y + 1, z + 0.5), Direction.UP, new BlockPos(x, Y, z), false);
        ModItems.CLAIM_WAND.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, hit));
    }

    private static boolean can(FakePlayer p, Flag f, int x, int z) {
        return Protection.check(p, level, x, z, f, false);
    }

    private static boolean useBlock(FakePlayer p, BlockPos pos) {
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return UseBlockCallback.EVENT.invoker().interact(p, level, InteractionHand.MAIN_HAND, hit) != InteractionResult.FAIL;
    }

    private static Path expectFile() {
        return Store.root().resolve("selftest-expect.json");
    }

    public static void start(MinecraftServer s) {
        if (running) return;
        server = s;
        level = s.overworld();
        TASKS.clear();
        pass = fail = tick = cursor = 0;
        running = true;
        alice = fake("Alice");
        bob = fake("Bob");
        carol = fake("Carol");
        staff = fake("Staffy");
        for (String n : Perms.allNodes()) Perms.override(staff.getUUID(), n, true);
        CobblePulseClaims.LOG.info(TAG + "début des tests, phase {}", PHASE);
        if (PHASE >= 2) phase2();
        else phase1();
    }

    // ================================================================== phase 1

    private static void phase1() {
        step(1, SelfTest::registry);
        step(1, SelfTest::economy);
        step(1, () -> {
            level.setWeatherParameters(12000, 0, false, false);
            platform(X0 - 30, Z0 - 10, X0 + 60, Z0 + 40);
            for (FakePlayer p : List.of(alice, bob, carol, staff)) {
                BlockEconomy.forgetForTests(p.getUUID());
                BlockEconomy.onJoin(p);
            }
            for (Claim c : new ArrayList<>(ClaimManager.all())) if (c.dimension.equals("minecraft:overworld") && c.overlaps(X0 - 40, Z0 - 20, X0 + 70, Z0 + 50))
                ClaimManager.delete(c);
            PlayerClaimData a = BlockEconomy.get(alice.getUUID());
            a.accrued = 2000;
            a.bonus = 0;
            at(alice, X0, Z0);
            check("depart_500_blocs", BlockEconomy.get(bob.getUUID()).total() == 500, "bob=" + BlockEconomy.get(bob.getUUID()).total());
        });
        // creation a la balise
        step(5, () -> click(alice, X0, Z0));
        step(5, () -> {
            WandService.Selection s = WandService.selection(alice.getUUID());
            check("balise_coin_a", s != null && s.a.equals(new BlockPos(X0, Y, Z0)), String.valueOf(s == null ? null : s.a));
            click(alice, X0 + 19, Z0 + 19);
        });
        step(5, () -> {
            home = ClaimManager.at("minecraft:overworld", X0 + 5, Z0 + 5);
            check("balise_creation", home != null && alice.getUUID().equals(home.owner) && home.area() == 400,
                    home == null ? "aucun claim" : home.area() + " blocs");
            check("solde_debite", BlockEconomy.available(alice.getUUID()) == 1600, "dispo=" + BlockEconomy.available(alice.getUUID()));
            check("hors_claim_nature", ClaimManager.at("minecraft:overworld", X0 - 1, Z0) == null, "");
            // refus : chevauchement, taille minimale, solde, nombre max, bordure
            ClaimManager.Result r1 = ClaimManager.create(bob, "minecraft:overworld", X0 + 15, Z0 + 15, X0 + 25, Z0 + 25, false);
            check("refus_chevauchement", !r1.ok() && r1.message().contains("chevauche"), r1.message());
            ClaimManager.Result r2 = ClaimManager.create(bob, "minecraft:overworld", X0 + 30, Z0, X0 + 32, Z0 + 2, false);
            check("refus_taille_min", !r2.ok(), r2.message());
            ClaimManager.Result r3 = ClaimManager.create(bob, "minecraft:overworld", X0 + 30, Z0, X0 + 59, Z0 + 29, false);
            check("refus_solde", !r3.ok() && r3.message().contains("Solde"), r3.message());
            ClaimManager.Result r4 = ClaimManager.create(bob, "minecraft:overworld", 9990, 0, 10010, 20, false);
            check("refus_bordure", !r4.ok() && r4.message().contains("bordure"), r4.message());
            // limite de territoires par joueur (config.json), puis remplacement par LuckPerms (s'il est autorise)
            ClaimsConfig.Claims cc = ClaimsConfig.get().claims;
            int oldMax = cc.max_claims_per_player;
            boolean oldLp = cc.luckperms_max_claims_override;
            cc.max_claims_per_player = 1;
            ClaimManager.Result r5 = ClaimManager.create(bob, "minecraft:overworld", X0 + 30, Z0, X0 + 39, Z0 + 9, false);
            ClaimManager.Result r6 = ClaimManager.create(bob, "minecraft:overworld", X0 + 45, Z0, X0 + 54, Z0 + 9, false);
            check("limite_claims_config", r5.ok() && !r6.ok() && r6.message().contains("maximal"), r5.message() + " / " + r6.message());
            Perms.override(bob.getUUID(), Perms.LIMIT_CLAIMS + ".3", true);
            check("limite_claims_luckperms", ClaimManager.maxClaims(bob) == 3, "" + ClaimManager.maxClaims(bob));
            cc.luckperms_max_claims_override = false;
            check("limite_claims_config_seule", ClaimManager.maxClaims(bob) == 1, "" + ClaimManager.maxClaims(bob));
            cc.max_claims_per_player = 0;
            check("limite_claims_illimitee", ClaimManager.maxClaims(bob) == Integer.MAX_VALUE, "");
            cc.max_claims_per_player = oldMax;
            cc.luckperms_max_claims_override = oldLp;
            check("limite_claims_defaut_10", ClaimsConfig.get().claims.max_claims_per_player == 10, "");
            if (r5.ok()) ClaimManager.delete(r5.claim());
            Perms.clearOverrides(bob.getUUID());
        });
        // redimensionnement par un coin
        step(5, () -> click(alice, X0 + 19, Z0 + 19));
        step(5, () -> {
            WandService.Selection s = WandService.selection(alice.getUUID());
            check("redimension_mode", s != null && s.resizing == home.id && s.fixedX == X0 && s.fixedZ == Z0, s == null ? "null" : s.resizing + "");
            click(alice, X0 + 24, Z0 + 19);
        });
        step(5, () -> {
            check("redimension_applique", home.maxX == X0 + 24 && home.area() == 500 && ClaimManager.at("minecraft:overworld", X0 + 22, Z0 + 3) == home,
                    home.minX + ".." + home.maxX + " " + home.area());
            check("redimension_solde", BlockEconomy.available(alice.getUUID()) == 1500, "" + BlockEconomy.available(alice.getUUID()));
        });
        // parcelle (deux coins dans son claim)
        step(5, () -> click(alice, X0 + 2, Z0 + 2));
        step(5, () -> click(alice, X0 + 6, Z0 + 6));
        step(5, () -> {
            Claim sub = ClaimManager.at("minecraft:overworld", X0 + 4, Z0 + 4);
            check("parcelle_creee", sub != null && sub.isSub() && sub.parent == home.id && home.children.contains(sub), sub == null ? "null" : "parent=" + sub.parent);
            check("parcelle_gratuite", BlockEconomy.available(alice.getUUID()) == 1500, "" + BlockEconomy.available(alice.getUUID()));
            ClaimManager.Result r = ClaimManager.createSub(alice, home, X0 + 5, Z0 + 5, X0 + 9, Z0 + 9);
            check("parcelle_chevauchement_refuse", !r.ok(), r.message());
            ClaimManager.Result out = ClaimManager.createSub(alice, home, X0 + 20, Z0, X0 + 30, Z0 + 5);
            check("parcelle_hors_claim_refusee", !out.ok(), out.message());
        });
        step(2, SelfTest::trustAndFlags);
        step(2, SelfTest::realProtections);
        step(2, SelfTest::environment);
        step(2, SelfTest::cobblemon);
        step(2, SelfTest::adminZones);
        step(2, SelfTest::borders);
        step(2, SelfTest::integrations);
        step(2, SelfTest::actions);
        step(2, SelfTest::spatialIndex);
        step(5, SelfTest::preparePersistence);
        step(40, () -> {
            Store.flushNow();
            check("ecriture_differee", Store.pendingCount() == 0 && Files.exists(Store.root().resolve("claims").resolve(home.id + ".json")), "écritures=" + Store.writes());
        });
    }

    private static void registry() {
        check("flags_referentiel", Flags.all().size() == 84, "83 du cahier des charges + pension ; n=" + Flags.all().size());
        int[] cats = new int[6];
        for (Flag f : Flags.all()) cats[f.category().ordinal()]++;
        check("flags_categories", cats[0] == 12 && cats[1] == 14 && cats[2] == 11 && cats[3] == 16 && cats[4] == 19 && cats[5] == 12, java.util.Arrays.toString(cats));
        check("flag_block_break", Flags.BLOCK_BREAK.defVisitor() == FlagState.DENY && Flags.BLOCK_BREAK.defMember() == FlagState.ALLOW && Flags.editable(Flags.BLOCK_BREAK), "");
        check("flag_admin_verrouille", !Flags.editable(Flags.WILD_SPAWN) && !Flags.editable(Flags.FLY) && !Flags.editable(Flags.WITHER_DRAGON), "");
        check("flag_chorus", Flags.CHORUS.defVisitor() == FlagState.DENY && Flags.CHORUS.defMember() == FlagState.ALLOW, "");
        check("flag_pension_ranch", Flags.RANCH.defVisitor() == FlagState.DENY && Flags.RANCH.defMember() == FlagState.ALLOW
                && Flags.RANCH.memberLevel() == TrustLevel.CONTAINER, "");
        check("flags_json", Files.exists(ClaimsConfig.dir().resolve("flags.json")) && Files.exists(ClaimsConfig.dir().resolve("config.json"))
                && Files.exists(ClaimsConfig.dir().resolve("worldborders.json")), "");
        check("objets_registre", BuiltInRegistries.ITEM.getKey(ModItems.CLAIM_WAND).toString().equals("cobblepulse:claim_wand")
                && BuiltInRegistries.ITEM.getKey(ModItems.CLAIM_INSPECTOR).toString().equals("cobblepulse:claim_inspector"), "");
        check("recettes", server.getRecipeManager().byKey(ResourceLocation.parse("cobblepulse_claims:claim_wand")).isPresent()
                && server.getRecipeManager().byKey(ResourceLocation.parse("cobblepulse_claims:claim_inspector")).isPresent(), "");
        WorldBorders.Border ow = WorldBorders.get("minecraft:overworld"), ne = WorldBorders.get("minecraft:the_nether"), en = WorldBorders.get("minecraft:the_end");
        check("bordures_par_defaut", ow != null && ow.circle() && ow.radius == 10000 && ne != null && !ne.circle() && ne.sizeX == 4000
                && en != null && en.circle() && en.centerX == 100 && en.centerZ == -50 && en.radius == 5000, "");
        var d = server.getCommands().getDispatcher();
        boolean cmds = true;
        for (String c : List.of("claim", "claims", "trust", "trustall", "untrust", "untrustall", "trustlist", "claimadmin", "abandonclaim"))
            cmds &= d.getRoot().getChild(c) != null;
        check("commandes_enregistrees", cmds, "");
        // les mixins s'appliquent au chargement de leurs classes cibles (y compris les classes internes privees)
        List<String> bad = new ArrayList<>();
        List<Class<?>> targets = new ArrayList<>(List.of(net.minecraft.world.entity.ai.goal.BreakDoorGoal.class,
                net.minecraft.world.entity.ai.behavior.HarvestFarmland.class, net.minecraft.world.entity.monster.Ravager.class,
                net.minecraft.world.entity.boss.wither.WitherBoss.class, net.minecraft.world.entity.boss.enderdragon.EnderDragon.class,
                net.minecraft.world.entity.projectile.SmallFireball.class, net.minecraft.world.entity.projectile.ThrownEnderpearl.class,
                net.minecraft.world.item.ChorusFruitItem.class, net.minecraft.world.entity.projectile.FishingHook.class,
                net.minecraft.world.item.enchantment.effects.ReplaceDisk.class, net.minecraft.world.level.block.entity.BrushableBlockEntity.class,
                com.cobblemon.mod.common.battles.BattleRegistry.class, com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity.class));
        int inner = 0;
        for (Class<?> outer : List.of(net.minecraft.world.entity.monster.EnderMan.class, net.minecraft.world.entity.monster.Silverfish.class,
                net.minecraft.world.entity.animal.Rabbit.class)) {
            try {
                for (Class<?> in : outer.getDeclaredClasses()) {
                    targets.add(in);
                    inner++;
                }
            } catch (Throwable t) {
                bad.add(outer.getSimpleName() + " : " + t);
            }
        }
        for (Class<?> c : targets) {
            try {
                Class.forName(c.getName(), true, c.getClassLoader());
                c.getDeclaredMethods();
            } catch (Throwable t) {
                bad.add(c.getName() + " : " + t);
            }
        }
        check("mixins_charges", bad.isEmpty() && inner >= 4, inner + " classes internes " + String.join(" | ", bad));
    }

    private static void economy() {
        FakePlayer e = fake("Eco");
        BlockEconomy.forgetForTests(e.getUUID());
        BlockEconomy.onJoin(e);
        PlayerClaimData d = BlockEconomy.get(e.getUUID());
        check("eco_depart", d.accrued == 500 && d.total() == 500, "" + d.total());
        long min = 60_000L;
        int g1 = BlockEconomy.accrue(e, 29 * min);
        check("eco_29_min_rien", g1 == 0 && d.progressMs == 29 * min, "gagne=" + g1);
        int g2 = BlockEconomy.accrue(e, min);
        check("eco_palier_150", g2 == 150 && d.accrued == 650 && d.progressMs == 0, "gagne=" + g2 + " accrued=" + d.accrued);
        d.accrued = 4950;
        int g3 = BlockEconomy.accrue(e, 30 * min);
        check("eco_plafond_5000", g3 == 50 && d.accrued == 5000, "gagne=" + g3);
        int g4 = BlockEconomy.accrue(e, 90 * min);
        check("eco_plafond_bloque", g4 == 0 && d.accrued == 5000 && d.progressMs == 0 && BlockEconomy.untilNext(e) == -1, "gagne=" + g4);
        BlockEconomy.addBonus(d, 1000);
        check("eco_bonus_hors_plafond", d.total() == 6000 && BlockEconomy.available(e.getUUID()) == 6000, "" + d.total());
        BlockEconomy.accrue(e, 30 * min);
        check("eco_bonus_conserve", d.bonus == 1000, "" + d.bonus);
        Perms.override(e.getUUID(), Perms.LIMIT_BLOCKS + ".10000", true);
        Perms.override(e.getUUID(), Perms.LIMIT_BLOCKS + ".8000", true);
        Perms.override(e.getUUID(), Perms.LIMIT_PER_INTERVAL + ".300", true);
        check("eco_grade_plafond", BlockEconomy.cap(e) == 10000, "" + BlockEconomy.cap(e));
        int g5 = BlockEconomy.accrue(e, 30 * min);
        check("eco_grade_palier_300", g5 == 300 && d.accrued == 5300, "gagne=" + g5);
        Perms.override(e.getUUID(), Perms.LIMIT_CLAIMS + ".unlimited", true);
        check("eco_claims_illimites", ClaimManager.maxClaims(e) == Integer.MAX_VALUE, "");
        BlockEconomy.setLastActiveForTests(e, System.currentTimeMillis() - 10 * min);
        check("eco_afk", BlockEconomy.isAfk(e) && BlockEconomy.accrue(e, 30 * min) == 0, "");
        BlockEconomy.setLastActiveForTests(e, System.currentTimeMillis());
        Perms.override(e.getUUID(), Perms.ACCRUE, false);
        check("eco_sans_noeud_accrue", BlockEconomy.accrue(e, 30 * min) == 0, "");
        BlockEconomy.setTotal(d, 7000);
        check("eco_definir_total", d.total() == 7000, "" + d.total());
        Perms.clearOverrides(e.getUUID());
        BlockEconomy.forgetForTests(e.getUUID());
    }

    // ------------------------------------------------------------------ confiance et flags

    private static void trustAndFlags() {
        int bx = X0 + 10, bz = Z0 + 10;
        at(alice, X0 + 12, Z0 + 12);
        check("visiteur_casse_refusee", !can(bob, Flags.BLOCK_BREAK, bx, bz), "");
        check("proprietaire_casse", can(alice, Flags.BLOCK_BREAK, bx, bz), "");
        check("visiteur_entre", can(bob, Flags.ENTER, bx, bz) && can(bob, Flags.ENDER_CHEST, bx, bz), "");
        check("visiteur_porte_refusee", !can(bob, Flags.DOOR, bx, bz) && !can(bob, Flags.CHEST, bx, bz), "");
        // /trust par commande, depuis le claim
        int r = execAs(alice, "trust Bob access");
        check("cmd_trust_access", r == 1 && home.trusted.get(bob.getUUID().toString()) == TrustLevel.ACCESS, String.valueOf(home.trusted));
        check("access_porte_ok_coffre_non", can(bob, Flags.DOOR, bx, bz) && can(bob, Flags.HEALER, bx, bz) && !can(bob, Flags.CHEST, bx, bz)
                && !can(bob, Flags.BLOCK_BREAK, bx, bz), "");
        execAs(alice, "trust Bob container");
        check("container_coffre_ok", can(bob, Flags.CHEST, bx, bz) && can(bob, Flags.RANCH, bx, bz) && !can(bob, Flags.BLOCK_PLACE, bx, bz), "");
        execAs(alice, "trust Bob build");
        check("build_construction_ok", can(bob, Flags.BLOCK_PLACE, bx, bz) && can(bob, Flags.CAPTURE, bx, bz) && !Access.canManage(bob, home), "");
        check("build_lave_refusee", !can(bob, Flags.LAVA, bx, bz) && can(alice, Flags.LAVA, bx, bz), "membre: DENY, proprio: bypass");
        execAs(alice, "untrust Bob");
        check("cmd_untrust", !home.trusted.containsKey(bob.getUUID().toString()) && !can(bob, Flags.DOOR, bx, bz), "");
        execAs(alice, "trust Carol manager");
        check("co_maire", Access.canManage(carol, home), "");
        ClaimOps.Res noMgr = ClaimOps.trust(carol, home, bob.getUUID(), "Bob", TrustLevel.MANAGER);
        ClaimOps.Res okBuild = ClaimOps.trust(carol, home, bob.getUUID(), "Bob", TrustLevel.BUILD);
        check("co_maire_limites", !noMgr.ok() && okBuild.ok(), noMgr.message());
        ClaimOps.untrust(alice, home, bob.getUUID(), "Bob");
        execAs(alice, "trustlist");
        // public
        execAs(alice, "trust public access");
        check("trust_public", can(bob, Flags.DOOR, bx, bz) && !can(bob, Flags.CHEST, bx, bz), String.valueOf(home.trusted.keySet()));
        execAs(alice, "untrust public");
        check("untrust_public", !can(bob, Flags.DOOR, bx, bz), "");
        // surcharges de la matrice
        ClaimOps.Res f1 = ClaimOps.setFlag(alice, home, Flags.BLOCK_BREAK, false, FlagState.ALLOW);
        check("flag_visiteur_allow", f1.ok() && can(bob, Flags.BLOCK_BREAK, bx, bz), f1.message());
        Flags.setEditable(Flags.BLOCK_BREAK, false);
        check("flag_verrou_ignore_surcharge", !can(bob, Flags.BLOCK_BREAK, bx, bz), "");
        ClaimOps.Res f2 = ClaimOps.setFlag(alice, home, Flags.BLOCK_BREAK, false, FlagState.DENY);
        check("flag_verrou_refus_joueur", !f2.ok() && f2.message().contains("verrouill"), f2.message());
        Flags.setEditable(Flags.BLOCK_BREAK, true);
        check("flag_deverrou", can(bob, Flags.BLOCK_BREAK, bx, bz), "");
        ClaimOps.setFlag(alice, home, Flags.BLOCK_BREAK, false, FlagState.DEFAULT);
        check("flag_defaut", !can(bob, Flags.BLOCK_BREAK, bx, bz), "");
        ClaimOps.Res f3 = ClaimOps.setFlag(alice, home, Flags.FLY, false, FlagState.ALLOW);
        ClaimOps.Res f4 = ClaimOps.setFlag(staff, home, Flags.FLY, false, FlagState.ALLOW);
        check("flag_admin_staff_seulement", !f3.ok() && f4.ok() && home.staffFlags.contains(Flags.FLY.id()) && Access.zone(home, Flags.FLY), f3.message());
        ClaimOps.setFlag(staff, home, Flags.FLY, false, FlagState.DEFAULT);
        check("flag_admin_retire", !Access.zone(home, Flags.FLY) && !home.staffFlags.contains(Flags.FLY.id()), "");
        // heritage des parcelles
        Claim sub = home.children.get(0);
        ClaimOps.trust(alice, home, bob.getUUID(), "Bob", TrustLevel.BUILD);
        check("parcelle_heritage", can(bob, Flags.BLOCK_PLACE, X0 + 4, Z0 + 4), "");
        ClaimOps.setInherit(alice, sub, false);
        check("parcelle_sans_heritage", !can(bob, Flags.BLOCK_PLACE, X0 + 4, Z0 + 4) && can(bob, Flags.BLOCK_PLACE, X0 + 12, Z0 + 12), "");
        ClaimOps.trust(alice, sub, carol.getUUID(), "Carol", TrustLevel.BUILD);
        check("parcelle_membre_propre", can(carol, Flags.BLOCK_PLACE, X0 + 4, Z0 + 4), "");
        ClaimOps.setInherit(alice, sub, true);
        ClaimOps.untrust(alice, home, bob.getUUID(), "Bob");
        // commandes
        ClaimOps.Res c1 = ClaimOps.setCommands(alice, home, "BLACKLIST", List.of("/sethome", "tpa"));
        at(bob, X0 + 12, Z0 + 12);
        check("cmd_liste_noire", c1.ok() && !CommandGuard.allow(bob, "sethome maison") && !CommandGuard.allow(bob, "tpa Alice")
                && CommandGuard.allow(bob, "tpaccept") && CommandGuard.allow(bob, "spawn"), c1.message());
        ClaimOps.setCommands(alice, home, "WHITELIST", List.of("spawn"));
        check("cmd_liste_blanche", CommandGuard.allow(bob, "spawn") && !CommandGuard.allow(bob, "back") && CommandGuard.allow(bob, "claim info"), "");
        ClaimOps.setCommands(alice, home, "DENY", List.of());
        check("cmd_tout_bloquer", !CommandGuard.allow(bob, "spawn") && CommandGuard.allow(bob, "trust Carol") && CommandGuard.allow(bob, "claimadmin"), "");
        check("cmd_proprio_exempte", CommandGuard.allow(alice, "back"), "");
        ClaimOps.setCommands(alice, home, "DEFAULT", List.of());
        check("cmd_defaut", CommandGuard.allow(bob, "back"), "");
        check("cmd_prefixe", CommandRule.matches("tpa", "minecraft:tpa Bob") && !CommandRule.matches("tpa", "tpaccept"), "");
        // PvP
        InteractionResult pvp = AttackEntityCallback.EVENT.invoker().interact(alice, level, InteractionHand.MAIN_HAND, bob, null);
        check("pvp_interdit", pvp == InteractionResult.FAIL, pvp.toString());
        check("pvp_degats_bloques", !net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.invoker()
                .allowDamage(bob, level.damageSources().playerAttack(alice), 2f), "");
    }

    private static int execAs(ServerPlayer p, String cmd) {
        try {
            return server.getCommands().getDispatcher().execute(cmd, p.createCommandSourceStack());
        } catch (Exception e) {
            check("commande_" + cmd.split(" ")[0], false, e.toString());
            return 0;
        }
    }

    // ------------------------------------------------------------------ protections reelles

    private static void realProtections() {
        BlockPos stone = new BlockPos(X0 + 15, Y, Z0 + 15);
        at(bob, X0 + 15, Z0 + 14);
        boolean broke = bob.gameMode.destroyBlock(stone);
        check("casse_reelle_refusee", !broke && level.getBlockState(stone).is(Blocks.STONE), "");
        boolean ev = PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, bob, stone, level.getBlockState(stone), null);
        check("evenement_casse", !ev, "");
        at(alice, X0 + 15, Z0 + 14);
        alice.setGameMode(GameType.CREATIVE);
        boolean aliceBroke = alice.gameMode.destroyBlock(stone);
        alice.setGameMode(GameType.SURVIVAL);
        check("casse_reelle_proprio", aliceBroke && level.getBlockState(stone).isAir(), "");
        level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
        // coffre
        BlockPos chest = new BlockPos(X0 + 16, Y + 1, Z0 + 16);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        check("coffre_visiteur_refuse", !useBlock(bob, chest), "");
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(chest), Direction.UP, chest, false);
        bob.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        InteractionResult denied = bob.gameMode.useItemOn(bob, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
        check("coffre_reel_refuse", denied == InteractionResult.FAIL, denied.toString());
        ClaimOps.trust(alice, home, bob.getUUID(), "Bob", TrustLevel.CONTAINER);
        check("coffre_membre_ok", useBlock(bob, chest), "");
        InteractionResult allowed = bob.gameMode.useItemOn(bob, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
        check("coffre_reel_autorise", allowed != InteractionResult.FAIL && allowed != InteractionResult.PASS, allowed.toString());
        bob.closeContainer();
        ClaimOps.untrust(alice, home, bob.getUUID(), "Bob");
        // pose de bloc reelle
        bob.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIRT, 4));
        BlockPos above = new BlockPos(X0 + 14, Y, Z0 + 14);
        bob.gameMode.useItemOn(bob, level, bob.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(above).add(0, 0.5, 0), Direction.UP, above, false));
        check("pose_reelle_refusee", level.getBlockState(above.above()).isAir() && bob.getMainHandItem().getCount() == 4, "");
        // ramassage et lacher
        ClaimOps.setFlag(alice, home, Flags.PICKUP, false, FlagState.DENY);
        ItemEntity item = new ItemEntity(level, bob.getX(), bob.getY(), bob.getZ(), new ItemStack(Items.DIAMOND));
        item.setNoPickUpDelay();
        level.addFreshEntity(item);
        bob.getInventory().clearContent();
        item.playerTouch(bob);
        check("ramassage_refuse", item.isAlive() && !bob.getInventory().contains(new ItemStack(Items.DIAMOND)), "");
        ClaimOps.setFlag(alice, home, Flags.PICKUP, false, FlagState.DEFAULT);
        item.playerTouch(bob);
        check("ramassage_autorise", bob.getInventory().contains(new ItemStack(Items.DIAMOND)), "");
        ClaimOps.setFlag(alice, home, Flags.DROP, false, FlagState.DENY);
        ItemStack dia = bob.getInventory().removeItem(bob.getInventory().findSlotMatchingItem(new ItemStack(Items.DIAMOND)), 1);
        ItemEntity dropped = bob.drop(dia, false, true);
        check("lacher_refuse_objet_rendu", dropped == null && bob.getInventory().contains(new ItemStack(Items.DIAMOND)), "");
        ClaimOps.setFlag(alice, home, Flags.DROP, false, FlagState.DEFAULT);
        bob.getInventory().clearContent();
        // pension de CobblePulse-Ranch (demande de Spacer)
        var ranch = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.fromNamespaceAndPath("cobblepulse_ranch", "ranch_block"));
        if (ranch.isPresent()) {
            BlockPos rp = new BlockPos(X0 + 18, Y + 1, Z0 + 18);
            level.setBlock(rp, ranch.get().defaultBlockState(), 3);
            check("pension_identifiee", Interactions.forBlock(level.getBlockState(rp)) == Flags.RANCH, "");
            check("pension_visiteur_refuse", !useBlock(bob, rp), "");
            check("pension_proprio_ok", useBlock(alice, rp), "");
            ClaimOps.trust(alice, home, bob.getUUID(), "Bob", TrustLevel.BUILD);
            check("pension_apres_trust", useBlock(bob, rp), "");
            ClaimOps.untrust(alice, home, bob.getUUID(), "Bob");
            bob.closeContainer();
            alice.closeContainer();
            level.removeBlock(rp, false);
        } else check("pension_ranch_absent", false, "cobblepulse_ranch non chargé");
        // entree interdite
        ClaimOps.setFlag(alice, home, Flags.ENTER, false, FlagState.DENY);
        ZoneTracker.forget(bob.getUUID());
        at(bob, X0 - 5, Z0 + 5);
        ZoneTracker.update(bob);
        at(bob, X0 + 3, Z0 + 5);
        ZoneTracker.update(bob);
        check("entree_refusee_repousse", bob.getX() < X0, "x=" + bob.getX());
        ClaimOps.setFlag(alice, home, Flags.ENTER, false, FlagState.DEFAULT);
        // titres d'entree
        ClaimOps.rename(alice, home, "Bourg-Palette");
        ZoneTracker.forget(bob.getUUID());
        at(bob, X0 - 5, Z0 + 5);
        ZoneTracker.update(bob);
        at(bob, X0 + 12, Z0 + 12);
        ZoneTracker.update(bob);
        String t = ZoneTracker.LAST_TITLE.get(bob.getUUID());
        check("titre_ville", t != null && t.startsWith("Bourg-Palette|Propriétaire : Alice | Clan : "), t);
        at(bob, X0 - 5, Z0 + 5);
        ZoneTracker.update(bob);
        check("zone_sauvage", "BAR:Zone sauvage".equals(ZoneTracker.LAST_TITLE.get(bob.getUUID())), ZoneTracker.LAST_TITLE.get(bob.getUUID()));
        String[] noName = ZoneTracker.entryTexts(namelessCopy(home));
        check("titre_sans_nom", noName[0].equals("Territoire de Alice") && noName[1].startsWith("Clan : "), noName[0] + "|" + noName[1]);
        // lunette
        List<Component> sum = InspectorService.summary(bob, home, new BlockPos(X0 + 5, Y, Z0 + 5));
        String all = String.join("\n", sum.stream().map(Component::getString).toList());
        check("lunette_resume", all.contains("Bourg-Palette") && all.contains("Alice") && all.contains("Superficie") && all.contains("500 blocs") && all.contains("Ville"), all);
        for (int cx = (home.minX - 8) >> 4; cx <= (home.maxX + 8) >> 4; cx++) for (int cz = (home.minZ - 8) >> 4; cz <= (home.maxZ + 8) >> 4; cz++) level.getChunk(cx, cz);
        at(bob, X0 + 5, Z0 + 5);
        InspectorService.inspect(bob, new BlockPos(X0 + 5, Y, Z0 + 5));
        check("reperes_blocs_fantomes", FakeVisuals.shownCount(bob.getUUID()) > 30, "" + FakeVisuals.shownCount(bob.getUUID()));
    }

    private static Claim namelessCopy(Claim c) {
        Claim n = new Claim();
        n.id = c.id;
        n.owner = c.owner;
        n.ownerName = c.ownerName;
        n.type = c.type;
        return n;
    }

    // ------------------------------------------------------------------ environnement

    private static void environment() {
        // explosion dans le claim : aucun bloc detruit ; hors claim : cratere
        int inside = countStone(X0 + 8, Z0 + 14, 3);
        level.explode(null, X0 + 8.5, Y + 1, Z0 + 14.5, 4f, Level.ExplosionInteraction.TNT);
        check("explosion_claim_protegee", countStone(X0 + 8, Z0 + 14, 3) == inside, inside + " -> " + countStone(X0 + 8, Z0 + 14, 3));
        int outside = countStone(X0 - 20, Z0 + 20, 3);
        level.explode(null, X0 - 19.5, Y + 1, Z0 + 20.5, 4f, Level.ExplosionInteraction.TNT);
        check("explosion_nature_detruit", countStone(X0 - 20, Z0 + 20, 3) < outside, outside + " -> " + countStone(X0 - 20, Z0 + 20, 3));
        Pig pig = EntityType.PIG.create(level);
        pig.moveTo(X0 + 10.5, Y + 1, Z0 + 10.5);
        level.addFreshEntity(pig);
        float hp = pig.getHealth();
        pig.hurt(level.damageSources().explosion(null, null), 5f);
        check("explosion_entites_protegees", pig.getHealth() == hp, hp + " -> " + pig.getHealth());
        check("explosion_flag_allow", Protection.allowsZone(level, new BlockPos(X0 - 5, Y, Z0), Flags.EXPLOSION_BLOCK), "nature");
        pig.discard();
        // feu : planches entourees de feu dans le claim
        BlockPos plank = new BlockPos(X0 + 20, Y + 1, Z0 + 15);
        level.setBlock(plank, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        BlockPos fire = plank.east();
        level.setBlock(fire, Blocks.FIRE.defaultBlockState(), 3);
        level.setBlock(plank.west(), Blocks.OAK_PLANKS.defaultBlockState(), 3);
        for (int i = 0; i < 400; i++) {
            BlockState fs = level.getBlockState(fire);
            if (!fs.is(Blocks.FIRE)) level.setBlock(fire, Blocks.FIRE.defaultBlockState(), 3);
            level.getBlockState(fire).tick(level, fire, level.getRandom());
        }
        check("feu_sans_propagation", level.getBlockState(plank).is(Blocks.OAK_PLANKS) && level.getBlockState(plank.west()).is(Blocks.OAK_PLANKS)
                && !level.getBlockState(plank.above()).is(Blocks.FIRE), level.getBlockState(plank).toString());
        level.removeBlock(fire, false);
        // liquide exterieur qui coule vers le claim
        BlockPos src = new BlockPos(home.minX - 2, Y + 1, Z0 + 10);
        level.setBlock(src, Blocks.WATER.defaultBlockState(), 3);
        for (int i = 0; i < 6; i++) {
            for (int dx = -3; dx <= 3; dx++) {
                BlockPos q = src.offset(dx, 0, 0);
                if (!level.getFluidState(q).isEmpty()) level.getFluidState(q).tick(level, q);
            }
        }
        check("liquide_exterieur_bloque", level.getFluidState(new BlockPos(home.minX, Y + 1, Z0 + 10)).isEmpty()
                && !level.getFluidState(new BlockPos(home.minX - 1, Y + 1, Z0 + 10)).isEmpty(), "");
        for (int dx = -4; dx <= 3; dx++) level.setBlock(src.offset(dx, 0, 0), Blocks.AIR.defaultBlockState(), 3);
        // piston hors du claim qui pousse vers le claim
        BlockPos piston = new BlockPos(home.minX - 2, Y + 1, Z0 + 12);
        level.setBlock(piston.east(), Blocks.COBBLESTONE.defaultBlockState(), 3);
        boolean into = new PistonStructureResolver(level, piston, Direction.EAST, true).resolve();
        BlockPos pin = new BlockPos(X0 + 10, Y + 1, Z0 + 12);
        level.setBlock(pin.east(), Blocks.COBBLESTONE.defaultBlockState(), 3);
        boolean within = new PistonStructureResolver(level, pin, Direction.EAST, true).resolve();
        check("piston_frontiere", !into && within, "vers claim=" + into + " interne=" + within);
        level.removeBlock(piston.east(), false);
        level.removeBlock(pin.east(), false);
        // grief des mobs et flags de zone
        check("grief_mobs_bloque", !Protection.allowsZone(level, new BlockPos(X0 + 3, Y, Z0 + 3), Flags.MOB_GRIEF)
                && Protection.allowsZone(level, new BlockPos(X0 - 3, Y, Z0 - 3), Flags.MOB_GRIEF), "");
        check("fonte_feuilles_par_defaut", Protection.allowsZone(level, new BlockPos(X0 + 3, Y, Z0 + 3), Flags.MELT)
                && Protection.allowsZone(level, new BlockPos(X0 + 3, Y, Z0 + 3), Flags.LEAF_DECAY), "");
        check("spawn_par_defaut", Protection.allowsZone(level, new BlockPos(X0 + 3, Y, Z0 + 3), Flags.HOSTILE_SPAWN)
                && Protection.allowsZone(level, new BlockPos(X0 + 3, Y, Z0 + 3), Flags.WILD_SPAWN), "");
    }

    private static int countStone(int cx, int cz, int r) {
        int n = 0;
        for (int x = cx - r; x <= cx + r; x++) for (int z = cz - r; z <= cz + r; z++) if (level.getBlockState(new BlockPos(x, Y, z)).is(Blocks.STONE)) n++;
        return n;
    }

    // ------------------------------------------------------------------ Cobblemon

    private static void cobblemon() {
        PokemonEntity wild = PokemonProperties.Companion.parse("pikachu level=10").createEntity(level);
        wild.moveTo(X0 + 9.5, Y + 1, Z0 + 9.5);
        level.addFreshEntity(wild);
        check("capture_visiteur_refusee", !CobblemonHooks.captureAllowed(bob, wild), "");
        check("capture_proprio_ok", CobblemonHooks.captureAllowed(alice, wild), "");
        ClaimOps.trust(alice, home, bob.getUUID(), "Bob", TrustLevel.BUILD);
        check("capture_membre_ok", CobblemonHooks.captureAllowed(bob, wild), "");
        wild.getPokemon().setShiny(true);
        check("capture_shiny_membre_refusee", !CobblemonHooks.captureAllowed(bob, wild) && CobblemonHooks.captureAllowed(alice, wild), "");
        wild.getPokemon().setShiny(false);
        ClaimOps.untrust(alice, home, bob.getUUID(), "Bob");
        InteractionResult hurt = AttackEntityCallback.EVENT.invoker().interact(bob, level, InteractionHand.MAIN_HAND, wild, null);
        InteractionResult hurtOwner = AttackEntityCallback.EVENT.invoker().interact(alice, level, InteractionHand.MAIN_HAND, wild, null);
        check("blesser_sauvage", hurt == InteractionResult.FAIL && hurtOwner != InteractionResult.FAIL, hurt + "/" + hurtOwner);
        check("soigneur_pc_visiteur_ok", can(bob, Flags.HEALER, X0 + 9, Z0 + 9) && can(bob, Flags.PC, X0 + 9, Z0 + 9), "");
        check("fossiles_visiteur_refuse", !can(bob, Flags.FOSSIL, X0 + 9, Z0 + 9), "");
        check("combat_sauvage_visiteur_ok", can(bob, Flags.BATTLE_WILD, X0 + 9, Z0 + 9) && can(bob, Flags.SEND_OUT, X0 + 9, Z0 + 9), "");
        check("machines_identifiees", Interactions.forBlock(BuiltInRegistries.BLOCK.get(ResourceLocation.parse("cobblemon:healing_machine")).defaultBlockState()) == Flags.HEALER
                && Interactions.forBlock(BuiltInRegistries.BLOCK.get(ResourceLocation.parse("cobblemon:pasture")).defaultBlockState()) == Flags.PASTURE_VIEW, "");
        wild.discard();
    }

    // ------------------------------------------------------------------ claims admin et flags de zone

    private static void adminZones() {
        long before = BlockEconomy.available(alice.getUUID());
        ClaimOps.Res noPerm = ClaimOps.convertToAdmin(alice, home);
        ClaimOps.Res conv = ClaimOps.convertToAdmin(staff, home);
        check("conversion_admin", !noPerm.ok() && conv.ok() && home.type == ClaimType.ADMIN && home.owner == null && alice.getUUID().equals(home.formerOwner)
                && BlockEconomy.available(alice.getUUID()) == before + 500, conv.message());
        check("titre_admin", ZoneTracker.entryTexts(home)[1].equals("Zone Administrative"), ZoneTracker.entryTexts(home)[1]);
        check("relation_admin", Relation.of(bob.getUUID(), home) == Relation.ADMIN, "");
        ClaimOps.Res back = ClaimOps.convertToPlayer(staff, home, null, null);
        check("conversion_retour", back.ok() && alice.getUUID().equals(home.owner) && home.type == ClaimType.PLAYER, back.message());
        // claim admin a la balise (mode admin du staff) : gratuit
        platform(X0 + 30, Z0 + 20, X0 + 60, Z0 + 40);
        WandService.toggleAdminMode(staff);
        long staffBefore = BlockEconomy.available(staff.getUUID());
        TASKS.add(new Task(tick + 1, () -> click(staff, X0 + 35, Z0 + 25)));
        TASKS.add(new Task(tick + 6, () -> click(staff, X0 + 55, Z0 + 38)));
        TASKS.add(new Task(tick + 11, () -> {
            Claim spawn = ClaimManager.at("minecraft:overworld", X0 + 40, Z0 + 30);
            check("claim_admin_balise", spawn != null && spawn.isAdmin() && BlockEconomy.available(staff.getUUID()) == staffBefore, spawn == null ? "null" : spawn.type + "");
            if (spawn == null) return;
            WandService.toggleAdminMode(staff);
            for (Flag f : List.of(Flags.INVULNERABLE, Flags.KEEP_INVENTORY, Flags.FLY, Flags.WEATHER, Flags.TIME, Flags.HEAL_ON_ENTRY))
                ClaimOps.setFlag(staff, spawn, f, false, FlagState.ALLOW);
            ClaimOps.setFlag(staff, spawn, Flags.HUNGER, false, FlagState.DENY);
            ClaimOps.setSetting(staff, spawn, "weather", "RAIN");
            ClaimOps.setSetting(staff, spawn, "time", "18000");
            ZoneTracker.forget(bob.getUUID());
            at(bob, X0 + 45, Z0 + 30);
            bob.setGameMode(GameType.SURVIVAL);
            ZoneTracker.update(bob);
            check("zone_invulnerable", ZoneEffects.invulnerable(bob) && !net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.invoker()
                    .allowDamage(bob, level.damageSources().generic(), 4f), "");
            check("zone_faim", ZoneEffects.hungerBlocked(bob), "");
            check("zone_inventaire", ZoneEffects.keepInventory(bob), "");
            check("zone_vol", bob.getAbilities().mayfly, "");
            check("zone_meteo_heure", "RAIN".equals(ZoneEffects.sentWeather(bob.getUUID())) && Long.valueOf(18000).equals(ZoneEffects.sentTime(bob.getUUID())),
                    ZoneEffects.sentWeather(bob.getUUID()) + " " + ZoneEffects.sentTime(bob.getUUID()));
            check("zone_spawn_verrouille_joueur", !ClaimOps.setFlag(alice, home, Flags.WILD_SPAWN, false, FlagState.DENY).ok(), "");
            at(bob, X0 + 28, Z0 + 30);
            ZoneTracker.update(bob);
            check("zone_sortie", !bob.getAbilities().mayfly && ZoneEffects.sentWeather(bob.getUUID()) == null && ZoneEffects.sentTime(bob.getUUID()) == null
                    && !ZoneEffects.invulnerable(bob), "");
            check("zone_chute_amortie", ZoneEffects.inFallGrace(bob) || bob.onGround(), "");
            ClaimManager.delete(spawn);
        }));
        cursor += 14;
    }

    // ------------------------------------------------------------------ WorldBorders

    private static void borders() {
        check("bordure_rect_inside", WorldBorders.rectInside("minecraft:overworld", -100, -100, 100, 100)
                && !WorldBorders.rectInside("minecraft:overworld", 9990, 0, 10010, 10), "");
        check("bordure_nether_rectangle", WorldBorders.inside("minecraft:the_nether", 1999, 1999) && !WorldBorders.inside("minecraft:the_nether", 2001, 0), "");
        check("bordure_end_centre", WorldBorders.inside("minecraft:the_end", 5000, -50) && !WorldBorders.inside("minecraft:the_end", -5000, 0), "");
        check("bordure_abords", WorldBorders.griefBlocked("minecraft:overworld", 9999, 0) && !WorldBorders.griefBlocked("minecraft:overworld", 9000, 0), "");
        FakePlayer t = fake("Voyageur");
        t.setPos(9000.5, 120, 0.5);
        t.teleportTo(level, 10050.5, 120, 0.5, 0f, 0f);
        check("teleport_hors_limite_annulee", Math.abs(t.getX() - 9000.5) < 0.01, "x=" + t.getX());
        t.teleportTo(level, 9100.5, 120, 0.5, 0f, 0f);
        check("teleport_dans_limite_ok", Math.abs(t.getX() - 9100.5) < 0.01, "x=" + t.getX());
        level.getChunk(10005 >> 4, 0);
        level.getChunk(9998 >> 4, 0);
        t.setPos(10005.5, 120, 0.5);
        WorldBorders.pushBack(level, WorldBorders.get("minecraft:overworld"), t);
        check("repoussement_doux", WorldBorders.inset(WorldBorders.get("minecraft:overworld"), t.getX(), t.getZ()) > 0 && t.getHealth() > 0, "x=" + t.getX());
        // autres dimensions (mods) ajoutees automatiquement
        check("dimensions_enregistrees", WorldBorders.all().size() >= 3, "" + WorldBorders.all().keySet());
    }

    // ------------------------------------------------------------------ Clans, vues, actions

    private static void integrations() {
        if (!FabricLoader.getInstance().isModLoaded("cobblepulse")) {
            check("clans_absents", false, "mod Clans non chargé");
            return;
        }
        try {
            Class<?> mgr = Class.forName("fr.cobblepulse.clan.ClanManager");
            Class<?> data = Class.forName("fr.cobblepulse.data.ClanData");
            Object clan = mgr.getMethod("clanOf", UUID.class).invoke(null, alice.getUUID());
            if (clan == null) clan = mgr.getMethod("create", ServerPlayer.class, String.class, String.class).invoke(null, alice, "Testeurs", "TST");
            if (mgr.getMethod("clanOf", UUID.class).invoke(null, carol.getUUID()) == null) {
                var add = mgr.getDeclaredMethod("addMember", data, UUID.class, String.class);
                add.setAccessible(true);
                add.invoke(null, clan, carol.getUUID(), "Carol");
            }
        } catch (Throwable t) {
            check("clans_creation", false, t.toString());
        }
        ClanBridge.invalidate();
        check("clans_tag", "TST".equals(ClanBridge.tag(alice.getUUID())), String.valueOf(ClanBridge.tag(alice.getUUID())));
        check("clans_meme_clan", ClanBridge.sameClan(alice.getUUID(), carol.getUUID()) && !ClanBridge.sameClan(alice.getUUID(), bob.getUUID()), "");
        String[] tx = ZoneTracker.entryTexts(home);
        check("titre_avec_clan", tx[0].equals("Bourg-Palette") && tx[1].equals("Propriétaire : Alice | Clan : [TST]"), tx[0] + "|" + tx[1]);
        ClaimOps.untrust(alice, home, carol.getUUID(), "Carol");
        check("relations_couleurs", Relation.of(alice.getUUID(), home) == Relation.OWN && Relation.of(carol.getUUID(), home) == Relation.CLAN
                && Relation.of(bob.getUUID(), home) == Relation.OTHER, Relation.of(carol.getUUID(), home).name());
        ClaimOps.trust(alice, home, carol.getUUID(), "Carol", TrustLevel.MANAGER);
        check("relation_membre_cyan", Relation.of(carol.getUUID(), home) == Relation.TRUSTED, "");
        JsonObject v = Views.summary(bob, home);
        check("vue_resume_carte", v.get("clan").getAsString().equals("TST") && v.get("rel").getAsString().equals("OTHER") && v.get("owner").getAsString().equals("Alice"), v.toString());
    }

    private static void actions() {
        JsonObject o = new JsonObject();
        o.addProperty("id", home.id);
        o.addProperty("flag", "block.break");
        o.addProperty("column", "visitor");
        o.addProperty("state", "ALLOW");
        Actions.handle(bob, "set_flag", o);
        check("action_refusee_non_proprio", !home.visitorFlags.containsKey("block.break"), String.valueOf(home.visitorFlags));
        Actions.handle(bob, "convert_admin", o);
        check("action_conversion_refusee", home.type == ClaimType.PLAYER, "");
        JsonObject eco = new JsonObject();
        eco.addProperty("interval_minutes", 1);
        Actions.handle(alice, "admin_economy", eco);
        check("action_admin_refusee", ClaimsConfig.get().economy.interval_minutes == 30, "" + ClaimsConfig.get().economy.interval_minutes);
        JsonObject border = new JsonObject();
        border.addProperty("dim", "minecraft:overworld");
        border.addProperty("radius", 50);
        Actions.handle(alice, "admin_border", border);
        check("action_bordure_refusee", WorldBorders.get("minecraft:overworld").radius == 10000, "");
        Actions.handle(carol, "set_flag", o);
        check("action_co_maire_ok", home.visitorFlags.get("block.break") == FlagState.ALLOW, "");
        o.addProperty("state", "DEFAULT");
        Actions.handle(carol, "set_flag", o);
        JsonObject name = new JsonObject();
        name.addProperty("id", home.id);
        name.addProperty("name", "§cBourg§r-Palette");
        Actions.handle(alice, "set_name", name);
        check("action_nom_nettoye", !home.name.contains("§"), home.name);
        ClaimOps.rename(alice, home, "Bourg-Palette");
        JsonObject view = Views.manager(alice, home);
        check("vue_gestion", view.getAsJsonArray("flags").size() == Flags.all().size() && view.get("role").getAsString().equals("OWNER")
                && view.getAsJsonArray("subs").size() == 1 && view.getAsJsonObject("perms").get("convert").getAsBoolean() == false, "");
        JsonObject staffView = Views.manager(staff, home);
        check("vue_staff_conversion", staffView.getAsJsonObject("perms").get("convert").getAsBoolean(), "");
        JsonObject dash = Views.dashboard(alice);
        check("vue_tableau_de_bord", dash.getAsJsonArray("claims").size() == 1 && dash.get("available").getAsLong() == BlockEconomy.available(alice.getUUID()), "");
        JsonObject info = Views.info(bob, home);
        check("vue_fiche", !info.get("canManage").getAsBoolean() && info.getAsJsonArray("rules").size() > 10, "");
        JsonObject adm = Views.admin(staff, "flags");
        check("vue_admin", adm.getAsJsonArray("flags").size() == Flags.all().size() && adm.getAsJsonArray("borders").size() >= 3, "");
    }

    // ------------------------------------------------------------------ index spatial

    private static void spatialIndex() {
        List<Claim> made = new ArrayList<>();
        long t0 = System.nanoTime();
        for (int i = 0; i < 50; i++) {
            for (int j = 0; j < 50; j++) {
                int x = -6000 + i * 24, z = -6000 + j * 24;
                ClaimManager.Result r = ClaimManager.create(staff, "minecraft:overworld", x, z, x + 15, z + 15, true);
                if (r.ok()) made.add(r.claim());
            }
        }
        long createMs = (System.nanoTime() - t0) / 1_000_000;
        check("index_2500_claims", made.size() == 2500, made.size() + " en " + createMs + " ms");
        Random rnd = new Random(42);
        boolean ok = true;
        for (int k = 0; k < 2000; k++) {
            int x = -6050 + rnd.nextInt(1300), z = -6050 + rnd.nextInt(1300);
            Claim fast = ClaimManager.topAt("minecraft:overworld", x, z);
            Claim slow = null;
            for (Claim c : made) if (c.contains(x, z)) slow = c;
            if (fast != slow) {
                ok = false;
                break;
            }
        }
        check("index_exact", ok, "");
        long t1 = System.nanoTime();
        int hits = 0;
        for (int k = 0; k < 1_000_000; k++) {
            if (ClaimManager.at("minecraft:overworld", -6050 + (k * 37) % 1300, -6050 + (k * 91) % 1300) != null) hits++;
        }
        long lookMs = (System.nanoTime() - t1) / 1_000_000;
        check("index_1M_lookups_rapides", lookMs < 2000, lookMs + " ms, " + hits + " touches");
        ClaimManager.clearForTests(made);
        check("index_nettoye", ClaimManager.topAt("minecraft:overworld", -5995, -5995) == null, "");
    }

    // ------------------------------------------------------------------ persistance

    private static void preparePersistence() {
        ClaimOps.trust(alice, home, bob.getUUID(), "Bob", TrustLevel.BUILD);
        ClaimOps.setFlag(alice, home, Flags.CHEST, false, FlagState.ALLOW);
        ClaimOps.setCommands(alice, home, "BLACKLIST", List.of("sethome"));
        PlayerClaimData a = BlockEconomy.get(alice.getUUID());
        a.progressMs = 12 * 60_000L;
        a.bonus = 77;
        Store.savePlayer(a);
        JsonObject e = new JsonObject();
        e.addProperty("home", home.id);
        e.addProperty("sub", home.children.get(0).id);
        e.addProperty("total", a.total());
        e.addProperty("used", ClaimManager.usedBlocks(alice.getUUID()));
        try {
            Files.writeString(expectFile(), e.toString(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            check("persistance_attendu", false, ex.toString());
        }
    }

    // ================================================================== phase 2

    private static void phase2() {
        step(1, () -> {
            JsonObject e;
            try {
                e = JsonParser.parseString(Files.readString(expectFile(), StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception ex) {
                check("persistance_fichier", false, ex.toString());
                return;
            }
            Claim h = ClaimManager.get(e.get("home").getAsInt());
            check("persistance_claim", h != null && "Bourg-Palette".equals(h.name) && alice.getUUID().equals(h.owner) && h.area() == 500, h == null ? "null" : h.name);
            if (h == null) return;
            check("persistance_index", ClaimManager.at("minecraft:overworld", X0 + 22, Z0 + 3) == h, "");
            Claim s = ClaimManager.get(e.get("sub").getAsInt());
            check("persistance_parcelle", s != null && s.parent == h.id && h.children.contains(s) && ClaimManager.at("minecraft:overworld", X0 + 4, Z0 + 4) == s, "");
            check("persistance_membres", h.trusted.get(bob.getUUID().toString()) == TrustLevel.BUILD && h.trusted.get(carol.getUUID().toString()) == TrustLevel.MANAGER, "");
            check("persistance_flags", h.visitorFlags.get(Flags.CHEST.id()) == FlagState.ALLOW && Access.allows(bob, h, Flags.BLOCK_BREAK), "");
            check("persistance_commandes", "BLACKLIST".equals(h.commands.mode) && h.commands.list.contains("sethome"), "");
            PlayerClaimData a = BlockEconomy.get(alice.getUUID());
            check("persistance_solde", a != null && a.total() == e.get("total").getAsInt() && a.bonus == 77, a == null ? "null" : a.total() + "");
            check("persistance_minuteur", a != null && a.progressMs == 12 * 60_000L, a == null ? "" : "" + a.progressMs);
            check("persistance_utilises", ClaimManager.usedBlocks(alice.getUUID()) == e.get("used").getAsLong(), "");
            check("persistance_config", Flags.editable(Flags.BLOCK_BREAK) && ClaimsConfig.get().economy.interval_minutes == 30, "");
            check("persistance_clan", !ClanBridge.available() || "TST".equals(ClanBridge.tag(alice.getUUID())), String.valueOf(ClanBridge.tag(alice.getUUID())));
            home = h;
            at(bob, X0 + 15, Z0 + 14);
            BlockPos stone = new BlockPos(X0 + 15, Y, Z0 + 15);
            level.getChunk(stone);
            if (!level.getBlockState(stone).is(Blocks.STONE)) level.setBlock(stone, Blocks.STONE.defaultBlockState(), 3);
            bob.setGameMode(GameType.CREATIVE);
            check("persistance_membre_casse", bob.gameMode.destroyBlock(stone), "");
            bob.setGameMode(GameType.SURVIVAL);
            FakePlayer dave = fake("Dave");
            check("persistance_visiteur_bloque", !Protection.check(dave, level, X0 + 15, Z0 + 15, Flags.BLOCK_BREAK, false), "");
        });
    }
}
