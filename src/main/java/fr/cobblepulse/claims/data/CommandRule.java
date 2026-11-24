package fr.cobblepulse.claims.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Gestionnaire granulaire des commandes d'un claim (sous-panneau de command.execute). Mode FLAG : la matrice decide
 * seule ; BLACKLIST : les prefixes listes sont bloques ; WHITELIST : seuls les prefixes listes sont autorises.
 * Les modes ALLOW / DENY du sous-panneau sont ecrits directement dans la matrice (command.execute).
 */
public class CommandRule {
    public String mode = "FLAG";
    public List<String> list = new ArrayList<>();

    public static String normalize(String cmd) {
        String s = cmd == null ? "" : cmd.trim().toLowerCase(Locale.ROOT);
        while (s.startsWith("/")) s = s.substring(1);
        return s.replaceAll("\\s+", " ");
    }

    /** "tpa" couvre "tpa" et "tpa Bob", pas "tpaccept". Un namespace (minecraft:tp) est ignore. */
    public static boolean matches(String entry, String command) {
        String e = normalize(entry);
        String c = normalize(command);
        if (e.isEmpty()) return false;
        int colon = c.indexOf(':');
        int space = c.indexOf(' ');
        if (colon > 0 && (space < 0 || colon < space)) c = c.substring(colon + 1);
        return c.equals(e) || c.startsWith(e + " ");
    }

    public boolean listed(String command) {
        for (String e : list) if (matches(e, command)) return true;
        return false;
    }

    public CommandRule copy() {
        CommandRule r = new CommandRule();
        r.mode = mode;
        r.list = new ArrayList<>(list);
        return r;
    }
}
