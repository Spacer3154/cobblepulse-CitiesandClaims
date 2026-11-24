package fr.cobblepulse.claims.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.cobblepulse.claims.ClaimsConfig;
import fr.cobblepulse.claims.Msg;
import fr.cobblepulse.claims.claim.Access;
import fr.cobblepulse.claims.claim.ClaimManager;
import fr.cobblepulse.claims.claim.ClaimOps;
import fr.cobblepulse.claims.claim.Names;
import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.data.PlayerClaimData;
import fr.cobblepulse.claims.data.TrustLevel;
import fr.cobblepulse.claims.economy.BlockEconomy;
import fr.cobblepulse.claims.net.ClaimsNet;
import fr.cobblepulse.claims.perm.Perms;
import fr.cobblepulse.claims.tools.InspectorService;
import fr.cobblepulse.claims.tools.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** /claim, /trust, /trustall, /untrust, /untrustall, /trustlist, /abandonclaim. */
public final class ClaimCommands {
    private static final Map<UUID, Long> ABANDON_CONFIRM = new HashMap<>();
    static final SuggestionProvider<CommandSourceStack> PLAYERS = (ctx, b) -> SharedSuggestionProvider.suggest(
            ctx.getSource().getServer().getPlayerNames(), b);
    static final SuggestionProvider<CommandSourceStack> PLAYERS_PUBLIC = (ctx, b) -> {
        b.suggest("public");
        return SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), b);
    };
    static final SuggestionProvider<CommandSourceStack> LEVELS = (ctx, b) -> SharedSuggestionProvider.suggest(
            List.of("access", "container", "build", "manager", "acces", "conteneurs", "membre", "comaire"), b);

    private ClaimCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var claim = literal("claim").requires(s -> Perms.has(s, Perms.USE))
                .executes(ClaimCommands::help)
                .then(literal("info").executes(ClaimCommands::info))
                .then(literal("liste").executes(c -> list(c, null))
                        .then(argument("joueur", StringArgumentType.word()).suggests(PLAYERS).requires(s -> Perms.has(s, Perms.INSPECT_ALL))
                                .executes(c -> list(c, StringArgumentType.getString(c, "joueur")))))
                .then(literal("nom").executes(c -> rename(c, ""))
                        .then(argument("nom", StringArgumentType.greedyString()).executes(c -> rename(c, StringArgumentType.getString(c, "nom")))))
                .then(literal("abandonner").executes(c -> abandon(c, false))
                        .then(literal("confirmer").executes(c -> abandon(c, true))))
                .then(literal("blocs").executes(ClaimCommands::blocks))
                .then(literal("outils").executes(ClaimCommands::tools))
                .then(literal("menu").executes(ClaimCommands::menu))
                .then(literal("herite").then(argument("valeur", BoolArgumentType.bool()).executes(ClaimCommands::inherit)));
        var root = d.register(claim);
        d.register(literal("claims").requires(s -> Perms.has(s, Perms.USE)).executes(ClaimCommands::help).redirect(root));
        d.register(literal("abandonclaim").requires(s -> Perms.has(s, Perms.USE)).executes(c -> abandon(c, false))
                .then(literal("confirmer").executes(c -> abandon(c, true))));

        d.register(literal("trust").requires(s -> Perms.has(s, Perms.TRUST))
                .then(argument("joueur", StringArgumentType.word()).suggests(PLAYERS_PUBLIC)
                        .executes(c -> trust(c, "build", false))
                        .then(argument("niveau", StringArgumentType.word()).suggests(LEVELS)
                                .executes(c -> trust(c, StringArgumentType.getString(c, "niveau"), false)))));
        d.register(literal("trustall").requires(s -> Perms.has(s, Perms.TRUST))
                .then(argument("joueur", StringArgumentType.word()).suggests(PLAYERS_PUBLIC)
                        .executes(c -> trust(c, "build", true))
                        .then(argument("niveau", StringArgumentType.word()).suggests(LEVELS)
                                .executes(c -> trust(c, StringArgumentType.getString(c, "niveau"), true)))));
        d.register(literal("untrust").requires(s -> Perms.has(s, Perms.TRUST))
                .then(argument("joueur", StringArgumentType.word()).suggests(PLAYERS_PUBLIC).executes(c -> untrust(c, false))));
        d.register(literal("untrustall").requires(s -> Perms.has(s, Perms.TRUST))
                .then(argument("joueur", StringArgumentType.word()).suggests(PLAYERS_PUBLIC).executes(c -> untrust(c, true))));
        d.register(literal("trustlist").requires(s -> Perms.has(s, Perms.USE)).executes(ClaimCommands::trustlist));
    }

    static ServerPlayer player(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return c.getSource().getPlayerOrException();
    }

    static Claim here(ServerPlayer p) {
        return ClaimManager.at(p.level(), p.blockPosition());
    }

    private static int help(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        p.sendSystemMessage(Msg.prefix().append(Component.literal("Cités & Territoires").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        String[][] lines = {
                {"/claim outils", "Balise de Territoire et Lunette Topographique"},
                {"/claim info", "le territoire où vous êtes"},
                {"/claim liste", "vos territoires"},
                {"/claim blocs", "solde de blocs et prochain palier"},
                {"/claim nom <nom>", "nommer la ville (ou la parcelle)"},
                {"/trust <joueur> [access|container|build|manager]", "ajouter un membre"},
                {"/untrust <joueur>", "retirer un membre"},
                {"/trustlist", "membres du territoire"},
                {"/claim abandonner", "supprimer le territoire où vous êtes"},
                {"/claim menu", "ouvrir l'écran (ou la touche K)"}};
        for (String[] l : lines) {
            p.sendSystemMessage(Component.literal("  " + l[0]).withStyle(s -> s.withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, l[0].split(" <")[0].split(" \\[")[0])))
                    .append(Component.literal(" — " + l[1]).withStyle(ChatFormatting.GRAY)));
        }
        return 1;
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        for (Component line : InspectorService.summary(p, here(p), p.blockPosition())) p.sendSystemMessage(line);
        Claim cl = here(p);
        if (cl != null) Msg.info(p, "Votre rôle ici : " + Access.trust(cl, p.getUUID()).label + ".");
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c, String who) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        UUID target = p.getUUID();
        String name = p.getGameProfile().getName();
        if (who != null) {
            Names.Resolved r = Names.resolve(p.server, who);
            if (r == null) {
                Msg.err(p, "Joueur inconnu : " + who);
                return 0;
            }
            target = r.uuid();
            name = r.name();
        }
        List<Claim> owned = ClaimManager.ownedBy(target);
        Msg.info(p, name + " : " + owned.size() + " territoire(s), " + ClaimManager.usedBlocks(target) + " blocs utilisés, "
                + BlockEconomy.available(target) + " disponibles.");
        for (Claim cl : owned) {
            MutableComponent line = Component.literal("  n°" + cl.id + " " + cl.displayName()).withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(" · " + cl.dimension.replace("minecraft:", "") + " (" + (cl.minX + cl.maxX) / 2 + ", " + (cl.minZ + cl.maxZ) / 2
                            + ") · " + cl.area() + " blocs" + (cl.children.isEmpty() ? "" : " · " + cl.children.size() + " parcelle(s)")).withStyle(ChatFormatting.GRAY));
            p.sendSystemMessage(line);
        }
        return owned.size();
    }

    private static int rename(CommandContext<CommandSourceStack> c, String name) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Claim cl = here(p);
        if (cl == null) {
            Msg.err(p, "Placez-vous dans votre territoire.");
            return 0;
        }
        return report(p, ClaimOps.rename(p, cl, name));
    }

    static int report(ServerPlayer p, ClaimOps.Res r) {
        if (r.ok()) Msg.ok(p, r.message());
        else Msg.err(p, r.message());
        return r.ok() ? 1 : 0;
    }

    private static int abandon(CommandContext<CommandSourceStack> c, boolean confirm) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Claim cl = here(p);
        if (cl == null) {
            Msg.err(p, "Placez-vous dans le territoire à abandonner.");
            return 0;
        }
        Long asked = ABANDON_CONFIRM.get(p.getUUID());
        if (!confirm && (asked == null || System.currentTimeMillis() - asked > 30_000)) {
            ABANDON_CONFIRM.put(p.getUUID(), System.currentTimeMillis());
            p.sendSystemMessage(Msg.prefix().append(Component.literal("Abandonner « " + cl.displayName() + " »" + (cl.children.isEmpty() ? "" : " et ses parcelles")
                    + " ? ").withStyle(ChatFormatting.YELLOW)).append(Component.literal("[Confirmer]").withStyle(s -> s.withColor(ChatFormatting.RED).withBold(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/claim abandonner confirmer"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Suppression définitive"))))));
            return 1;
        }
        ABANDON_CONFIRM.remove(p.getUUID());
        return report(p, ClaimOps.delete(p, cl));
    }

    private static int blocks(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        PlayerClaimData d = BlockEconomy.get(p.getUUID());
        long used = ClaimManager.usedBlocks(p.getUUID());
        Msg.info(p, "Blocs : " + BlockEconomy.available(p.getUUID()) + " disponibles / " + (d == null ? 0 : d.total()) + " au total (" + used + " utilisés).");
        long next = BlockEconomy.untilNext(p);
        if (next < 0) Msg.info(p, "Plafond du cumul passif atteint (" + BlockEconomy.cap(p) + " blocs).");
        else Msg.info(p, "Prochain palier (+" + BlockEconomy.perInterval(p) + ") dans " + fmt(next) + (BlockEconomy.isAfk(p) ? " (en pause : inactif)" : "") + ".");
        int max = ClaimManager.maxClaims(p);
        Msg.info(p, "Territoires : " + ClaimManager.ownedBy(p.getUUID()).size() + " / " + (max == Integer.MAX_VALUE ? "illimité" : max) + ".");
        return 1;
    }

    public static String fmt(long ms) {
        long s = ms / 1000;
        long m = s / 60;
        return m > 0 ? m + " min " + (s % 60) + " s" : s + " s";
    }

    private static int tools(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        int given = 0;
        if (!p.getInventory().contains(st -> st.is(ModItems.CLAIM_WAND))) {
            p.getInventory().placeItemBackInInventory(new ItemStack(ModItems.CLAIM_WAND));
            given++;
        }
        if (!p.getInventory().contains(st -> st.is(ModItems.CLAIM_INSPECTOR))) {
            p.getInventory().placeItemBackInInventory(new ItemStack(ModItems.CLAIM_INSPECTOR));
            given++;
        }
        if (given == 0) Msg.info(p, "Vous avez déjà la Balise et la Lunette.");
        else Msg.ok(p, "Outils de territoire remis.");
        return 1;
    }

    private static int menu(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Claim cl = here(p);
        if (cl == null) ClaimsNet.openDashboard(p);
        else if (Access.canManage(p, cl)) ClaimsNet.openManager(p, cl);
        else ClaimsNet.openInfo(p, cl);
        return 1;
    }

    private static int inherit(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Claim cl = here(p);
        if (cl == null) {
            Msg.err(p, "Placez-vous dans une parcelle.");
            return 0;
        }
        return report(p, ClaimOps.setInherit(p, cl, BoolArgumentType.getBool(c, "valeur")));
    }

    private static int trust(CommandContext<CommandSourceStack> c, String levelArg, boolean all) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        String who = StringArgumentType.getString(c, "joueur");
        TrustLevel lvl = TrustLevel.parse(levelArg);
        if (lvl == null || lvl == TrustLevel.NONE || lvl == TrustLevel.OWNER) {
            Msg.err(p, "Niveau inconnu « " + levelArg + " » : access, container, build ou manager.");
            return 0;
        }
        boolean pub = who.equalsIgnoreCase("public") || who.equals("*");
        UUID target = null;
        String name = "Public";
        if (pub) {
            if (lvl == TrustLevel.MANAGER) lvl = TrustLevel.BUILD;
        } else {
            Names.Resolved r = Names.resolve(p.server, who);
            if (r == null) {
                Msg.err(p, "Joueur inconnu : " + who);
                return 0;
            }
            target = r.uuid();
            name = r.name();
        }
        if (all) {
            List<Claim> owned = ClaimManager.ownedBy(p.getUUID());
            if (owned.isEmpty()) {
                Msg.err(p, "Vous n'avez aucun territoire.");
                return 0;
            }
            int n = 0;
            for (Claim cl : owned) if (ClaimOps.trust(p, cl, target, name, lvl).ok()) n++;
            Msg.ok(p, name + " : " + lvl.label + " sur " + n + " territoire(s).");
            return n;
        }
        Claim cl = here(p);
        if (cl == null) {
            Msg.err(p, "Placez-vous dans votre territoire (ou utilisez /trustall).");
            return 0;
        }
        return report(p, ClaimOps.trust(p, cl, target, name, lvl));
    }

    private static int untrust(CommandContext<CommandSourceStack> c, boolean all) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        String who = StringArgumentType.getString(c, "joueur");
        boolean pub = who.equalsIgnoreCase("public") || who.equals("*");
        UUID target = null;
        String name = "Public";
        if (!pub) {
            Names.Resolved r = Names.resolve(p.server, who);
            if (r == null) {
                Msg.err(p, "Joueur inconnu : " + who);
                return 0;
            }
            target = r.uuid();
            name = r.name();
        }
        if (all) {
            int n = 0;
            for (Claim cl : ClaimManager.ownedBy(p.getUUID())) {
                if (ClaimOps.untrust(p, cl, target, name).ok()) n++;
                for (Claim sub : cl.children) if (ClaimOps.untrust(p, sub, target, name).ok()) n++;
            }
            Msg.ok(p, name + " retiré de " + n + " territoire(s) ou parcelle(s).");
            return n;
        }
        Claim cl = here(p);
        if (cl == null) {
            Msg.err(p, "Placez-vous dans votre territoire (ou utilisez /untrustall).");
            return 0;
        }
        return report(p, ClaimOps.untrust(p, cl, target, name));
    }

    private static int trustlist(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Claim cl = here(p);
        if (cl == null) {
            Msg.err(p, "Placez-vous dans un territoire.");
            return 0;
        }
        if (Access.canManage(p, cl) && ClaimsNet.hasClient(p) && ClaimsConfig.get() != null) {
            var view = fr.cobblepulse.claims.net.Views.manager(p, cl);
            view.addProperty("tab", "members");
            ClaimsNet.open(p, "manager", view);
        }
        Claim top = ClaimManager.top(cl);
        Msg.info(p, "Membres de « " + cl.displayName() + " » (propriétaire : " + (top.isAdmin() ? "Administration" : top.ownerName) + ") :");
        if (cl.trusted.isEmpty()) p.sendSystemMessage(Component.literal("  aucun membre").withStyle(ChatFormatting.DARK_GRAY));
        for (Map.Entry<String, TrustLevel> e : cl.trusted.entrySet()) {
            p.sendSystemMessage(Component.literal("  " + cl.trustedNames.getOrDefault(e.getKey(), e.getKey())).withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(" — " + e.getValue().label + " (" + e.getValue().id() + ")").withStyle(ChatFormatting.GRAY)));
        }
        if (cl.isSub() && cl.inherit && !top.trusted.isEmpty())
            p.sendSystemMessage(Component.literal("  + membres hérités du claim principal : " + top.trusted.size()).withStyle(ChatFormatting.DARK_GRAY));
        return 1;
    }

    static int intArg(CommandContext<CommandSourceStack> c, String n) {
        return IntegerArgumentType.getInteger(c, n);
    }
}
