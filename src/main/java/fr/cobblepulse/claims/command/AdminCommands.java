package fr.cobblepulse.claims.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.cobblepulse.claims.ClaimsConfig;
import fr.cobblepulse.claims.CobblePulseClaims;
import fr.cobblepulse.claims.Msg;
import fr.cobblepulse.claims.border.WorldBorders;
import fr.cobblepulse.claims.claim.ClaimManager;
import fr.cobblepulse.claims.claim.ClaimOps;
import fr.cobblepulse.claims.claim.Names;
import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.data.FlagState;
import fr.cobblepulse.claims.data.PlayerClaimData;
import fr.cobblepulse.claims.economy.BlockEconomy;
import fr.cobblepulse.claims.flag.Flag;
import fr.cobblepulse.claims.flag.Flags;
import fr.cobblepulse.claims.net.ClaimsNet;
import fr.cobblepulse.claims.perm.Perms;
import fr.cobblepulse.claims.test.SelfTest;
import fr.cobblepulse.claims.tools.InspectorService;
import fr.cobblepulse.claims.tools.WandService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** /claimadmin : interface staff, contournement, conversion, suppression, blocs, bordures, flags globaux, rechargement. */
public final class AdminCommands {
    private static final SuggestionProvider<CommandSourceStack> FLAGS = (c, b) -> {
        List<String> ids = new ArrayList<>();
        for (Flag f : Flags.all()) ids.add(f.id());
        return SharedSuggestionProvider.suggest(ids, b);
    };
    private static final SuggestionProvider<CommandSourceStack> DIMS = (c, b) -> SharedSuggestionProvider.suggest(WorldBorders.all().keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> STATES = (c, b) -> SharedSuggestionProvider.suggest(List.of("allow", "deny", "default"), b);
    private static final SuggestionProvider<CommandSourceStack> COLUMNS = (c, b) -> SharedSuggestionProvider.suggest(List.of("visiteur", "membre"), b);

    private AdminCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(literal("claimadmin").requires(s -> Perms.has(s, Perms.INSPECT_ALL) || Perms.has(s, Perms.ADMIN_CONFIG)
                        || Perms.has(s, Perms.ADMIN_WORLDBORDER) || Perms.has(s, Perms.ADMIN_GIVE_BLOCKS) || Perms.has(s, Perms.BYPASS))
                .executes(c -> hub(c))
                .then(literal("hub").executes(AdminCommands::hub))
                .then(literal("bypass").requires(s -> Perms.has(s, Perms.BYPASS))
                        .executes(c -> bypass(c, null))
                        .then(literal("on").executes(c -> bypass(c, true)))
                        .then(literal("off").executes(c -> bypass(c, false))))
                .then(literal("creation").requires(s -> Perms.has(s, Perms.CONVERT_ADMIN))
                        .then(literal("admin").executes(c -> creation(c, true)))
                        .then(literal("joueur").executes(c -> creation(c, false))))
                .then(literal("convertir").requires(s -> Perms.has(s, Perms.CONVERT_ADMIN))
                        .then(literal("admin").executes(AdminCommands::toAdmin))
                        .then(literal("joueur").executes(c -> toPlayer(c, null))
                                .then(argument("joueur", StringArgumentType.word()).suggests(ClaimCommands.PLAYERS)
                                        .executes(c -> toPlayer(c, StringArgumentType.getString(c, "joueur"))))))
                .then(literal("supprimer").requires(s -> Perms.has(s, Perms.DELETE_OTHERS))
                        .executes(c -> delete(c, 0))
                        .then(argument("id", IntegerArgumentType.integer(1)).executes(c -> delete(c, IntegerArgumentType.getInteger(c, "id")))))
                .then(literal("info").requires(s -> Perms.has(s, Perms.INSPECT_ALL))
                        .then(argument("id", IntegerArgumentType.integer(1)).executes(AdminCommands::info)))
                .then(literal("tp").requires(s -> Perms.has(s, Perms.INSPECT_ALL))
                        .then(argument("id", IntegerArgumentType.integer(1)).executes(AdminCommands::tp)))
                .then(literal("blocs").requires(s -> Perms.has(s, Perms.ADMIN_GIVE_BLOCKS))
                        .then(argument("joueur", StringArgumentType.word()).suggests(ClaimCommands.PLAYERS)
                                .executes(c -> blocks(c, null, 0))
                                .then(literal("ajouter").then(argument("n", IntegerArgumentType.integer(0)).executes(c -> blocks(c, "add", ClaimCommands.intArg(c, "n")))))
                                .then(literal("retirer").then(argument("n", IntegerArgumentType.integer(0)).executes(c -> blocks(c, "remove", ClaimCommands.intArg(c, "n")))))
                                .then(literal("definir").then(argument("n", IntegerArgumentType.integer(0)).executes(c -> blocks(c, "set", ClaimCommands.intArg(c, "n")))))))
                .then(literal("bordure").requires(s -> Perms.has(s, Perms.ADMIN_WORLDBORDER))
                        .executes(AdminCommands::borders)
                        .then(argument("dimension", net.minecraft.commands.arguments.ResourceLocationArgument.id()).suggests(DIMS)
                                .executes(AdminCommands::borders)
                                .then(literal("activer").executes(c -> border(c, b -> b.enabled = true)))
                                .then(literal("desactiver").executes(c -> border(c, b -> b.enabled = false)))
                                .then(literal("centre").then(argument("x", DoubleArgumentType.doubleArg()).then(argument("z", DoubleArgumentType.doubleArg())
                                        .executes(c -> border(c, b -> {
                                            b.centerX = DoubleArgumentType.getDouble(c, "x");
                                            b.centerZ = DoubleArgumentType.getDouble(c, "z");
                                        })))))
                                .then(literal("cercle").then(argument("rayon", DoubleArgumentType.doubleArg(8)).executes(c -> border(c, b -> {
                                    b.shape = "CIRCLE";
                                    b.radius = DoubleArgumentType.getDouble(c, "rayon");
                                }))))
                                .then(literal("rectangle").then(argument("x", DoubleArgumentType.doubleArg(16)).then(argument("z", DoubleArgumentType.doubleArg(16))
                                        .executes(c -> border(c, b -> {
                                            b.shape = "RECTANGLE";
                                            b.sizeX = DoubleArgumentType.getDouble(c, "x");
                                            b.sizeZ = DoubleArgumentType.getDouble(c, "z");
                                        })))))))
                .then(literal("flag").requires(s -> Perms.has(s, Perms.INSPECT_ALL) || Perms.has(s, Perms.ADMIN_CONFIG))
                        .then(argument("flag", StringArgumentType.string()).suggests(FLAGS)
                                .then(argument("colonne", StringArgumentType.word()).suggests(COLUMNS)
                                        .then(argument("etat", StringArgumentType.word()).suggests(STATES).executes(AdminCommands::flag)))))
                .then(literal("defaut").requires(s -> Perms.has(s, Perms.ADMIN_CONFIG))
                        .then(argument("flag", StringArgumentType.string()).suggests(FLAGS)
                                .then(argument("colonne", StringArgumentType.word()).suggests(COLUMNS)
                                        .then(argument("etat", StringArgumentType.word()).suggests(STATES).executes(AdminCommands::globalDefault)))))
                .then(literal("verrou").requires(s -> Perms.has(s, Perms.ADMIN_CONFIG))
                        .then(argument("flag", StringArgumentType.string()).suggests(FLAGS)
                                .then(literal("oui").executes(c -> lock(c, true)))
                                .then(literal("non").executes(c -> lock(c, false)))))
                .then(literal("reglage").requires(s -> Perms.has(s, Perms.ADMIN_CONFIG) || Perms.has(s, Perms.INSPECT_ALL))
                        .then(argument("cle", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("weather", "time", "level_cap"), b))
                                .then(argument("valeur", StringArgumentType.word()).executes(AdminCommands::setting))))
                .then(literal("reload").requires(s -> Perms.has(s, Perms.ADMIN_CONFIG)).executes(AdminCommands::reload))
                .then(literal("selftest").requires(s -> SelfTest.ENABLED && s.hasPermission(2)).executes(c -> {
                    SelfTest.start(c.getSource().getServer());
                    return 1;
                })));
    }

    private static int hub(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = ClaimCommands.player(c);
        ClaimsNet.openAdmin(p, "claims");
        return 1;
    }

    private static int bypass(CommandContext<CommandSourceStack> c, Boolean on) throws CommandSyntaxException {
        ServerPlayer p = ClaimCommands.player(c);
        boolean v = Perms.setBypass(p, on == null ? !Perms.bypass(p) : on);
        Msg.ok(p, "Contournement des protections : " + (v ? "activé" : "désactivé") + ".");
        return 1;
    }

    private static int creation(CommandContext<CommandSourceStack> c, boolean admin) throws CommandSyntaxException {
        ServerPlayer p = ClaimCommands.player(c);
        if (WandService.adminMode(p.getUUID()) != admin) WandService.toggleAdminMode(p);
        Msg.ok(p, admin ? "La Balise crée maintenant des claims admin." : "La Balise crée de nouveau des claims joueur.");
        return 1;
    }

    private static Claim here(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = ClaimCommands.player(c);
        Claim cl = ClaimCommands.here(p);
        if (cl == null) Msg.err(p, "Aucun territoire ici.");
        return cl;
    }

    private static int toAdmin(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Claim cl = here(c);
        return cl == null ? 0 : ClaimCommands.report(ClaimCommands.player(c), ClaimOps.convertToAdmin(ClaimCommands.player(c), cl));
    }

    private static int toPlayer(CommandContext<CommandSourceStack> c, String who) throws CommandSyntaxException {
        ServerPlayer p = ClaimCommands.player(c);
        Claim cl = here(c);
        if (cl == null) return 0;
        Names.Resolved r = who == null ? null : Names.resolve(p.server, who);
        if (who != null && r == null) {
            Msg.err(p, "Joueur inconnu : " + who);
            return 0;
        }
        return ClaimCommands.report(p, ClaimOps.convertToPlayer(p, cl, r == null ? null : r.uuid(), r == null ? null : r.name()));
    }

    private static int delete(CommandContext<CommandSourceStack> c, int id) throws CommandSyntaxException {
        ServerPlayer p = ClaimCommands.player(c);
        Claim cl = id > 0 ? ClaimManager.get(id) : ClaimCommands.here(p);
        if (cl == null) {
            Msg.err(p, "Territoire introuvable.");
            return 0;
        }
        return ClaimCommands.report(p, ClaimOps.delete(p, cl));
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = ClaimCommands.player(c);
        Claim cl = ClaimManager.get(IntegerArgumentType.getInteger(c, "id"));
        if (cl == null) {
            Msg.err(p, "Territoire introuvable.");
            return 0;
        }
        for (Component line : InspectorService.summary(p, cl, new net.minecraft.core.BlockPos((cl.minX + cl.maxX) / 2, 0, (cl.minZ + cl.maxZ) / 2)))
            p.sendSystemMessage(line);
        Msg.info(p, cl.dimension + " [" + cl.minX + ", " + cl.minZ + "] → [" + cl.maxX + ", " + cl.maxZ + "], " + cl.trusted.size() + " membre(s), surcharges : "
                + cl.visitorFlags + " / " + cl.memberFlags + ", commandes " + cl.commands.mode + " " + cl.commands.list);
        return 1;
    }

    private static int tp(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = ClaimCommands.player(c);
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("id", IntegerArgumentType.getInteger(c, "id"));
        fr.cobblepulse.claims.net.Actions.handle(p, "tp", o);
        return 1;
    }

    private static int blocks(CommandContext<CommandSourceStack> c, String op, int n) {
        CommandSourceStack src = c.getSource();
        String who = StringArgumentType.getString(c, "joueur");
        Names.Resolved r = Names.resolve(src.getServer(), who);
        if (r == null) {
            Msg.err(src, "Joueur inconnu : " + who);
            return 0;
        }
        PlayerClaimData d = BlockEconomy.getOrCreate(r.uuid(), r.name());
        if ("add".equals(op)) BlockEconomy.addBonus(d, n);
        else if ("remove".equals(op)) BlockEconomy.addBonus(d, -n);
        else if ("set".equals(op)) BlockEconomy.setTotal(d, n);
        Msg.ok(src, r.name() + " : " + d.total() + " blocs au total (passif " + d.accrued + ", staff " + (d.bonus >= 0 ? "+" : "") + d.bonus + "), "
                + BlockEconomy.available(r.uuid()) + " disponibles, " + ClaimManager.ownedBy(r.uuid()).size() + " territoire(s).");
        return 1;
    }

    private static int borders(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        String only = null;
        try {
            only = net.minecraft.commands.arguments.ResourceLocationArgument.getId(c, "dimension").toString();
        } catch (IllegalArgumentException ignored) {
        }
        for (var e : WorldBorders.all().entrySet()) {
            if (only != null && !only.equals(e.getKey())) continue;
            Msg.info(src, e.getKey() + " : " + (e.getValue().enabled ? WorldBorders.describe(e.getValue()) : "désactivée"));
        }
        Msg.info(src, "Marge anti-grief aux abords : " + WorldBorders.edgeBuffer() + " bloc(s).");
        return 1;
    }

    private interface BorderEdit {
        void apply(WorldBorders.Border b);
    }

    private static int border(CommandContext<CommandSourceStack> c, BorderEdit edit) {
        String dim = net.minecraft.commands.arguments.ResourceLocationArgument.getId(c, "dimension").toString();
        WorldBorders.Border b = WorldBorders.get(dim);
        if (b == null) {
            Msg.err(c.getSource(), "Dimension inconnue : " + dim);
            return 0;
        }
        b = b.copy();
        edit.apply(b);
        WorldBorders.set(dim, b);
        Msg.ok(c.getSource(), dim + " : " + (b.enabled ? WorldBorders.describe(b) : "désactivée"));
        return 1;
    }

    private static int flag(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = ClaimCommands.player(c);
        Claim cl = here(c);
        if (cl == null) return 0;
        Flag f = Flags.byId(StringArgumentType.getString(c, "flag"));
        FlagState st = FlagState.parse(StringArgumentType.getString(c, "etat"));
        boolean member = StringArgumentType.getString(c, "colonne").toLowerCase().startsWith("m");
        return ClaimCommands.report(p, ClaimOps.setFlag(p, cl, f, member, st));
    }

    private static int globalDefault(CommandContext<CommandSourceStack> c) {
        Flag f = Flags.byId(StringArgumentType.getString(c, "flag"));
        FlagState st = FlagState.parse(StringArgumentType.getString(c, "etat"));
        if (f == null || st == null) {
            Msg.err(c.getSource(), "Permission ou état inconnu.");
            return 0;
        }
        boolean member = StringArgumentType.getString(c, "colonne").toLowerCase().startsWith("m");
        Flags.setGlobal(f, member, st);
        Msg.ok(c.getSource(), f.id() + " : défaut " + (member ? "membres" : "visiteurs") + " = " + Flags.globalValue(f, member));
        return 1;
    }

    private static int lock(CommandContext<CommandSourceStack> c, boolean locked) {
        Flag f = Flags.byId(StringArgumentType.getString(c, "flag"));
        if (f == null) {
            Msg.err(c.getSource(), "Permission inconnue.");
            return 0;
        }
        Flags.setEditable(f, !locked);
        Msg.ok(c.getSource(), f.id() + (locked ? " : verrouillée par le serveur." : " : modifiable par les joueurs."));
        return 1;
    }

    private static int setting(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = ClaimCommands.player(c);
        Claim cl = here(c);
        if (cl == null) return 0;
        return ClaimCommands.report(p, ClaimOps.setSetting(p, cl, StringArgumentType.getString(c, "cle"), StringArgumentType.getString(c, "valeur")));
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        ClaimsConfig.load();
        Flags.load();
        WorldBorders.load();
        WorldBorders.registerLevels(c.getSource().getServer());
        for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) {
            ClaimsNet.sendBorder(p);
            ClaimsNet.sendFullSync(p);
        }
        CobblePulseClaims.LOG.info("[CobblePulse-Claims] Configuration rechargée");
        Msg.ok(c.getSource(), "Configuration, permissions globales et WorldBorders rechargées.");
        return 1;
    }
}
