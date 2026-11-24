package fr.cobblepulse.claims.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Etat recu du serveur : territoires de la dimension courante, WorldBorder, selection de la balise, mises en evidence. */
public final class ClientState {
    public static final Map<Integer, CClaim> CLAIMS = new ConcurrentHashMap<>();
    public static final Map<Integer, Long> HIGHLIGHTS = new ConcurrentHashMap<>();
    public static volatile String dim = "";
    public static volatile UUID self;
    public static volatile boolean staff;
    public static volatile CBorder border;
    public static volatile Selection selection;
    /** Claim dessine en 3D a la demande (bouton de l'ecran de gestion), 0 sinon. */
    public static volatile int forced3d;
    /** Incremente a chaque changement (rafraichissement des cartes). */
    public static volatile int version;
    public static volatile boolean connectedToMod;

    public static final class CClaim {
        public int id, parent;
        public String name, owner, clan, type, rel, dim;
        public boolean named;
        public int minX, minZ, maxX, maxZ;
        public long area;

        public boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        public boolean isSub() {
            return parent != 0;
        }

        public boolean isAdmin() {
            return "ADMIN".equals(type);
        }

        /** Couleur RGB selon la relation (vert/cyan : vous, bleu : clan, orange : tiers, violet : admin). */
        public int rgb() {
            return switch (rel == null ? "" : rel) {
                case "OWN" -> 0x3BE36B;
                case "TRUSTED" -> 0x34D8E8;
                case "CLAN" -> 0x3D7BFF;
                case "ADMIN" -> 0xB45CFF;
                default -> 0xFF8C2E;
            };
        }

        public String relationLabel() {
            return switch (rel == null ? "" : rel) {
                case "OWN" -> "Votre territoire";
                case "TRUSTED" -> "Vous êtes membre";
                case "CLAN" -> "Territoire de votre clan";
                case "ADMIN" -> "Zone administrative";
                default -> "Territoire d'un tiers";
            };
        }

        public List<String> tooltip() {
            List<String> l = new ArrayList<>();
            l.add(name);
            if (!isAdmin()) l.add("Propriétaire : " + owner);
            l.add(isAdmin() ? "Zone Administrative" : "Clan : " + (clan == null ? "Sans clan" : "[" + clan + "]"));
            if (isSub()) l.add("Parcelle");
            return l;
        }
    }

    public static final class CBorder {
        public boolean enabled;
        public double cx, cz, radius, sx, sz;
        public boolean circle;
        public String dim;

        public double inset(double x, double z) {
            if (circle) return radius - Math.hypot(x - cx, z - cz);
            return Math.min(sx / 2 - Math.abs(x - cx), sz / 2 - Math.abs(z - cz));
        }
    }

    public static final class Selection {
        public String kind;
        public int x, y, z, claim, fx, fz;
    }

    private ClientState() {}

    public static void reset() {
        CLAIMS.clear();
        HIGHLIGHTS.clear();
        border = null;
        selection = null;
        forced3d = 0;
        connectedToMod = false;
        version++;
    }

    static int i(JsonObject o, String k) {
        return o.has(k) ? o.get(k).getAsInt() : 0;
    }

    static String s(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }

    public static CClaim parse(JsonObject o) {
        CClaim c = new CClaim();
        c.id = i(o, "id");
        c.parent = i(o, "parent");
        c.name = s(o, "name");
        c.named = o.has("named") && o.get("named").getAsBoolean();
        c.owner = s(o, "owner");
        c.clan = s(o, "clan");
        c.type = s(o, "type");
        c.rel = s(o, "rel");
        c.dim = s(o, "dim");
        c.minX = i(o, "minX");
        c.minZ = i(o, "minZ");
        c.maxX = i(o, "maxX");
        c.maxZ = i(o, "maxZ");
        c.area = o.has("area") ? o.get("area").getAsLong() : 0;
        return c;
    }

    public static void applySync(JsonObject o) {
        connectedToMod = true;
        String d = s(o, "dim");
        if (o.has("full") && o.get("full").getAsBoolean()) {
            CLAIMS.clear();
            dim = d;
            if (o.has("self")) self = UUID.fromString(o.get("self").getAsString());
            if (o.has("staff")) staff = o.get("staff").getAsBoolean();
            fr.cobblepulse.claims.client.map.Discovery.required = !o.has("discovery") || o.get("discovery").getAsBoolean();
        } else if (d != null && !d.equals(dim)) {
            return;
        }
        if (o.has("upsert")) {
            for (JsonElement e : o.getAsJsonArray("upsert")) {
                CClaim c = parse(e.getAsJsonObject());
                CLAIMS.put(c.id, c);
            }
        }
        if (o.has("remove")) {
            JsonArray rm = o.getAsJsonArray("remove");
            for (JsonElement e : rm) {
                int id = e.getAsInt();
                CLAIMS.remove(id);
                CLAIMS.values().removeIf(c -> c.parent == id);
            }
        }
        version++;
    }

    public static void applyBorder(JsonObject o) {
        CBorder b = new CBorder();
        b.dim = s(o, "dim");
        b.enabled = o.has("enabled") && o.get("enabled").getAsBoolean();
        if (b.enabled) {
            b.cx = o.get("cx").getAsDouble();
            b.cz = o.get("cz").getAsDouble();
            b.circle = !"RECTANGLE".equals(s(o, "shape"));
            b.radius = o.get("radius").getAsDouble();
            b.sx = o.get("sx").getAsDouble();
            b.sz = o.get("sz").getAsDouble();
        }
        border = b;
        version++;
    }

    public static void applyVisual(JsonObject o) {
        String kind = s(o, "kind");
        if ("clear".equals(kind)) {
            selection = null;
        } else if ("highlight".equals(kind)) {
            HIGHLIGHTS.put(i(o, "claim"), System.currentTimeMillis() + Math.max(5, i(o, "seconds")) * 1000L);
        } else {
            Selection s = new Selection();
            s.kind = kind;
            s.x = i(o, "x");
            s.y = i(o, "y");
            s.z = i(o, "z");
            s.claim = i(o, "claim");
            s.fx = i(o, "fx");
            s.fz = i(o, "fz");
            selection = s;
        }
    }

    /** Claim le plus specifique en (x, z). */
    public static CClaim at(int x, int z) {
        CClaim best = null;
        for (CClaim c : CLAIMS.values()) {
            if (!c.contains(x, z)) continue;
            if (best == null || (c.isSub() && !best.isSub())) best = c;
        }
        return best;
    }

    public static CClaim get(int id) {
        return CLAIMS.get(id);
    }
}
