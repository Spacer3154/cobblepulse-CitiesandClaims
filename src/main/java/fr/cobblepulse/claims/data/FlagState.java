package fr.cobblepulse.claims.data;

import java.util.Locale;

/** Selecteur 3 etats de la matrice des permissions. */
public enum FlagState {
    ALLOW, DENY, DEFAULT;

    public static FlagState parse(String s) {
        if (s == null) return null;
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "allow", "autoriser", "autorise", "oui", "true", "on" -> ALLOW;
            case "deny", "interdire", "interdit", "non", "false", "off" -> DENY;
            case "default", "defaut", "défaut", "systeme", "système" -> DEFAULT;
            default -> null;
        };
    }

    public boolean bool() {
        return this == ALLOW;
    }

    public static FlagState of(boolean b) {
        return b ? ALLOW : DENY;
    }
}
