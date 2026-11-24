package fr.cobblepulse.claims.data;

import java.util.Locale;

/** Niveaux de confiance (inspires de GriefPrevention) et roles affiches dans l'ecran Membres & Roles. */
public enum TrustLevel {
    NONE("Visiteur"),
    ACCESS("Accès"),
    CONTAINER("Conteneurs"),
    BUILD("Membre"),
    MANAGER("Co-Maire"),
    OWNER("Maire");

    public final String label;

    TrustLevel(String label) {
        this.label = label;
    }

    public boolean atLeast(TrustLevel other) {
        return ordinal() >= other.ordinal();
    }

    public static TrustLevel max(TrustLevel a, TrustLevel b) {
        if (a == null) return b == null ? NONE : b;
        if (b == null) return a;
        return a.ordinal() >= b.ordinal() ? a : b;
    }

    /** access/acces, container/conteneurs, build/membre, manager/comaire (+ noms d'enum). */
    public static TrustLevel parse(String s) {
        if (s == null) return null;
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").trim();
        return switch (n) {
            case "access", "acces", "accès" -> ACCESS;
            case "container", "containers", "conteneur", "conteneurs" -> CONTAINER;
            case "build", "builder", "membre", "member", "constructeur" -> BUILD;
            case "manager", "comaire", "gestionnaire", "permission" -> MANAGER;
            case "none", "visiteur", "visitor", "aucun" -> NONE;
            default -> null;
        };
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
