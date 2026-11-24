package fr.cobblepulse.claims.protect;

import fr.cobblepulse.claims.ClaimsConfig;
import fr.cobblepulse.claims.Msg;
import fr.cobblepulse.claims.claim.Access;
import fr.cobblepulse.claims.claim.ClaimManager;
import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.data.CommandRule;
import fr.cobblepulse.claims.data.TrustLevel;
import fr.cobblepulse.claims.flag.Flags;
import fr.cobblepulse.claims.perm.Perms;
import net.minecraft.server.level.ServerPlayer;

/**
 * Filtrage des commandes dans un claim (mixin Commands#performCommand) : command.execute sur DENY bloque tout, sauf les
 * commandes du mod de claim ; liste noire / blanche de prefixes sinon. Proprietaire, Co-Maires et staff exemptes des listes.
 */
public final class CommandGuard {
    private CommandGuard() {}

    public static boolean allow(ServerPlayer p, String rawCommand) {
        if (p == null || rawCommand == null) return true;
        String cmd = CommandRule.normalize(rawCommand);
        if (cmd.isEmpty()) return true;
        String root = cmd.split(" ", 2)[0];
        int colon = root.indexOf(':');
        if (colon >= 0) root = root.substring(colon + 1);
        for (String a : ClaimsConfig.get().commands.always_allowed) if (CommandRule.normalize(a).equals(root)) return true;
        Claim c = ClaimManager.at(p.level(), p.blockPosition());
        if (c == null || Perms.bypass(p)) return true;
        if (!Access.allows(p, c, Flags.COMMANDS)) {
            Msg.err(p, "Les commandes sont bloquées dans « " + c.displayName() + " ».");
            return false;
        }
        TrustLevel lvl = Access.trust(c, p.getUUID());
        if (lvl.atLeast(TrustLevel.MANAGER)) return true;
        CommandRule rule = effectiveRule(c);
        if ("BLACKLIST".equals(rule.mode) && rule.listed(cmd)) {
            Msg.err(p, "La commande /" + root + " est bloquée dans « " + c.displayName() + " ».");
            return false;
        }
        if ("WHITELIST".equals(rule.mode) && !rule.listed(cmd)) {
            Msg.err(p, "Seules certaines commandes sont autorisées dans « " + c.displayName() + " ».");
            return false;
        }
        return true;
    }

    /** Une parcelle sans regle propre (mode FLAG, liste vide) suit celle du claim principal si elle herite. */
    public static CommandRule effectiveRule(Claim c) {
        if (c.isSub() && c.inherit && "FLAG".equals(c.commands.mode)) return ClaimManager.top(c).commands;
        return c.commands;
    }
}
