package fr.cobblepulse.claims.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Un territoire : claim joueur / ville, claim admin, ou sous-zone (parcelle) d'un claim principal. Toute la hauteur du
 * monde est couverte ; les bornes X/Z sont inclusives.
 */
public class Claim {
    public int id;
    /** Proprietaire (null pour un claim admin). */
    public UUID owner;
    public String ownerName = "";
    public String dimension = "minecraft:overworld";
    public int minX, minZ, maxX, maxZ;
    /** Nom de ville (claim principal) ou nom de parcelle (sous-zone). */
    public String name;
    public ClaimType type = ClaimType.PLAYER;
    /** Claim principal d'une sous-zone, 0 sinon. */
    public int parent;
    /** Une sous-zone herite des membres et des permissions du claim principal (sauf reglage contraire). */
    public boolean inherit = true;
    /** uuid -> niveau ; "*" = tout le monde (/trust public). */
    public Map<String, TrustLevel> trusted = new LinkedHashMap<>();
    public Map<String, String> trustedNames = new LinkedHashMap<>();
    /** Surcharges de la matrice : colonne Visiteurs (et valeur unique des flags de zone) et colonne Membres. */
    public Map<String, FlagState> visitorFlags = new LinkedHashMap<>();
    public Map<String, FlagState> memberFlags = new LinkedHashMap<>();
    /** Flags poses par le staff (un flag verrouille reste applique s'il a ete regle par le staff). */
    public Set<String> staffFlags = new HashSet<>();
    public CommandRule commands = new CommandRule();
    public ClaimSettings settings = new ClaimSettings();
    /** Ancien proprietaire d'un claim converti en claim admin (pour pouvoir le rendre). */
    public UUID formerOwner;
    public String formerOwnerName;
    public long created;

    /** Sous-zones (reconstruit au chargement, non sauvegarde). */
    public transient List<Claim> children = new ArrayList<>();

    public boolean isSub() {
        return parent != 0;
    }

    public boolean isAdmin() {
        return type == ClaimType.ADMIN;
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int length() {
        return maxZ - minZ + 1;
    }

    public long area() {
        return (long) width() * length();
    }

    public boolean contains(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean containsRect(int x1, int z1, int x2, int z2) {
        return x1 >= minX && x2 <= maxX && z1 >= minZ && z2 <= maxZ;
    }

    public boolean overlaps(int x1, int z1, int x2, int z2) {
        return x1 <= maxX && x2 >= minX && z1 <= maxZ && z2 >= minZ;
    }

    public boolean isCorner(int x, int z) {
        return (x == minX || x == maxX) && (z == minZ || z == maxZ);
    }

    public String displayName() {
        if (name != null && !name.isBlank()) return name;
        if (isSub()) return "Parcelle n°" + id;
        if (isAdmin()) return "Zone Administrative";
        return "Territoire de " + ownerName;
    }

    public void normalize() {
        if (trusted == null) trusted = new LinkedHashMap<>();
        if (trustedNames == null) trustedNames = new LinkedHashMap<>();
        if (visitorFlags == null) visitorFlags = new LinkedHashMap<>();
        if (memberFlags == null) memberFlags = new LinkedHashMap<>();
        if (staffFlags == null) staffFlags = new HashSet<>();
        if (commands == null) commands = new CommandRule();
        if (commands.list == null) commands.list = new ArrayList<>();
        if (settings == null) settings = new ClaimSettings();
        if (type == null) type = ClaimType.PLAYER;
        if (ownerName == null) ownerName = "";
        if (children == null) children = new ArrayList<>();
        trusted.values().removeIf(v -> v == null);
        visitorFlags.values().removeIf(v -> v == null || v == FlagState.DEFAULT);
        memberFlags.values().removeIf(v -> v == null || v == FlagState.DEFAULT);
        int x1 = Math.min(minX, maxX), x2 = Math.max(minX, maxX), z1 = Math.min(minZ, maxZ), z2 = Math.max(minZ, maxZ);
        minX = x1;
        maxX = x2;
        minZ = z1;
        maxZ = z2;
    }
}
