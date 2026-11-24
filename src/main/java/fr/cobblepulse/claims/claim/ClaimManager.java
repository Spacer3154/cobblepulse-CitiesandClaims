package fr.cobblepulse.claims.claim;

import fr.cobblepulse.claims.ClaimsConfig;
import fr.cobblepulse.claims.CobblePulseClaims;
import fr.cobblepulse.claims.border.WorldBorders;
import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.data.ClaimType;
import fr.cobblepulse.claims.data.TrustLevel;
import fr.cobblepulse.claims.economy.BlockEconomy;
import fr.cobblepulse.claims.perm.Perms;
import fr.cobblepulse.claims.storage.Store;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Creation, redimensionnement, sous-zones, conversion et suppression des claims ; requetes spatiales. */
public final class ClaimManager {
    private static final Map<Integer, Claim> ALL = new HashMap<>();
    private static final ClaimIndex INDEX = new ClaimIndex();
    private static Store.Meta meta = new Store.Meta();
    /** Ecouteurs : claim cree ou modifie / supprime (synchronisation client, cartes, suivi des zones). */
    public static final List<Consumer<Claim>> CHANGED = new CopyOnWriteArrayList<>();
    public static final List<Consumer<Claim>> REMOVED = new CopyOnWriteArrayList<>();

    public record Result(boolean ok, String message, Claim claim) {
        static Result fail(String m) {
            return new Result(false, m, null);
        }

        static Result ok(String m, Claim c) {
            return new Result(true, m, c);
        }
    }

    private ClaimManager() {}

    // ------------------------------------------------------------------ chargement

    public static void load() {
        ALL.clear();
        INDEX.clear();
        meta = Store.loadMeta();
        List<Claim> loaded = Store.loadClaims();
        for (Claim c : loaded) {
            ALL.put(c.id, c);
            meta.nextId = Math.max(meta.nextId, c.id + 1);
        }
        for (Claim c : loaded) {
            if (c.isSub()) {
                Claim p = ALL.get(c.parent);
                if (p == null || !p.dimension.equals(c.dimension)) {
                    CobblePulseClaims.LOG.warn("[CobblePulse-Claims] Sous-zone {} orpheline : convertie en claim principal", c.id);
                    c.parent = 0;
                    INDEX.add(c);
                    Store.saveClaim(c);
                } else p.children.add(c);
            } else INDEX.add(c);
        }
        CobblePulseClaims.LOG.info("[CobblePulse-Claims] {} claims chargés ({} entrées de chunk indexées)", ALL.size(), INDEX.chunkEntries());
    }

    // ------------------------------------------------------------------ requetes

    public static String dim(Level level) {
        return level.dimension().location().toString();
    }

    public static Claim get(int id) {
        return ALL.get(id);
    }

    public static Collection<Claim> all() {
        return ALL.values();
    }

    /** Claim le plus specifique (sous-zone si le point y est) en (x, z). */
    public static Claim at(String dim, int x, int z) {
        Claim top = INDEX.topAt(dim, x, z);
        if (top == null) return null;
        for (Claim s : top.children) if (s.contains(x, z)) return s;
        return top;
    }

    public static Claim at(Level level, BlockPos pos) {
        return at(dim(level), pos.getX(), pos.getZ());
    }

    public static Claim topAt(String dim, int x, int z) {
        return INDEX.topAt(dim, x, z);
    }

    public static Claim top(Claim c) {
        if (c == null || !c.isSub()) return c;
        Claim p = ALL.get(c.parent);
        return p == null ? c : p;
    }

    public static List<Claim> topClaims(String dim) {
        List<Claim> out = new ArrayList<>();
        for (Claim c : ALL.values()) if (!c.isSub() && (dim == null || c.dimension.equals(dim))) out.add(c);
        out.sort(Comparator.comparingInt(c -> c.id));
        return out;
    }

    public static List<Claim> ownedBy(UUID owner) {
        List<Claim> out = new ArrayList<>();
        for (Claim c : ALL.values()) if (!c.isSub() && c.type == ClaimType.PLAYER && owner.equals(c.owner)) out.add(c);
        out.sort(Comparator.comparingInt(c -> c.id));
        return out;
    }

    public static long usedBlocks(UUID owner) {
        long n = 0;
        for (Claim c : ownedBy(owner)) n += c.area();
        return n;
    }

    public static List<Claim> near(String dim, int x, int z, int radius) {
        List<Claim> out = new ArrayList<>();
        for (Claim c : INDEX.overlapping(dim, x - radius, z - radius, x + radius, z + radius)) out.add(c);
        return out;
    }

    public static Set<Claim> overlapping(String dim, int x1, int z1, int x2, int z2) {
        return INDEX.overlapping(dim, x1, z1, x2, z2);
    }

    /** Limite de territoires du joueur : config.json (max_claims_per_player), remplacee par LuckPerms si autorise. */
    public static int maxClaims(ServerPlayer p) {
        ClaimsConfig.Claims cfg = ClaimsConfig.get().claims;
        int base = cfg.max_claims_per_player <= 0 ? Integer.MAX_VALUE : cfg.max_claims_per_player;
        return cfg.luckperms_max_claims_override ? Perms.numeric(p, Perms.LIMIT_CLAIMS, base) : base;
    }

    // ------------------------------------------------------------------ creation

    private static Claim newClaim(String dim, int x1, int z1, int x2, int z2) {
        Claim c = new Claim();
        c.id = meta.nextId++;
        Store.saveMeta(meta);
        c.dimension = dim;
        c.minX = Math.min(x1, x2);
        c.maxX = Math.max(x1, x2);
        c.minZ = Math.min(z1, z2);
        c.maxZ = Math.max(z1, z2);
        c.created = System.currentTimeMillis();
        return c;
    }

    private static String overlapMessage(Collection<Claim> others) {
        Claim o = others.iterator().next();
        return "Cette zone chevauche « " + o.displayName() + " »" + (o.isAdmin() ? "" : " (" + o.ownerName + ")") + ".";
    }

    /** Claim principal (joueur ou admin). owner = null pour un claim admin. */
    public static Result create(ServerPlayer actor, String dim, int x1, int z1, int x2, int z2, boolean admin) {
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2), minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        ClaimsConfig.Claims cfg = ClaimsConfig.get().claims;
        long area = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
        if (!WorldBorders.rectInside(dim, minX, minZ, maxX, maxZ)) return Result.fail("La zone dépasse la bordure du monde de cette dimension.");
        Set<Claim> over = INDEX.overlapping(dim, minX, minZ, maxX, maxZ);
        if (!over.isEmpty()) return Result.fail(overlapMessage(over));
        if (!admin) {
            if (actor == null) return Result.fail("Joueur requis.");
            if (!Perms.has(actor, Perms.CREATE)) return Result.fail("Vous n'avez pas le droit de réserver un territoire.");
            int w = maxX - minX + 1, l = maxZ - minZ + 1;
            if (w < cfg.min_width || l < cfg.min_width) return Result.fail("Un claim doit mesurer au moins " + cfg.min_width + " × " + cfg.min_width + " blocs.");
            if (area < cfg.min_area) return Result.fail("Un claim doit couvrir au moins " + cfg.min_area + " blocs.");
            int max = maxClaims(actor);
            if (max != Integer.MAX_VALUE && ownedBy(actor.getUUID()).size() >= max)
                return Result.fail("Vous possédez déjà le nombre maximal de territoires (" + max + ").");
            long available = BlockEconomy.available(actor.getUUID());
            if (available < area) return Result.fail("Solde insuffisant : " + area + " blocs requis, " + available + " disponibles.");
        }
        Claim c = newClaim(dim, minX, minZ, maxX, maxZ);
        if (admin) {
            c.type = ClaimType.ADMIN;
            c.owner = null;
            c.ownerName = "Administration";
        } else {
            c.owner = actor.getUUID();
            c.ownerName = actor.getGameProfile().getName();
        }
        ALL.put(c.id, c);
        INDEX.add(c);
        changed(c);
        return Result.ok((admin ? "Claim admin" : "Territoire") + " n°" + c.id + " créé (" + c.width() + " × " + c.length() + " = " + area + " blocs).", c);
    }

    /** Sous-zone (parcelle) dans un claim principal. */
    public static Result createSub(ServerPlayer actor, Claim parent, int x1, int z1, int x2, int z2) {
        if (parent == null) return Result.fail("Aucun claim principal ici.");
        parent = top(parent);
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2), minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        if (actor != null && !Perms.has(actor, Perms.SUBCLAIM) && !Perms.staff(actor)) return Result.fail("Vous n'avez pas le droit de créer des parcelles.");
        if (!parent.containsRect(minX, minZ, maxX, maxZ)) return Result.fail("Une parcelle doit être entièrement dans le claim principal.");
        int mw = ClaimsConfig.get().claims.subclaim_min_width;
        if (maxX - minX + 1 < mw || maxZ - minZ + 1 < mw) return Result.fail("Une parcelle doit mesurer au moins " + mw + " × " + mw + " blocs.");
        if (minX == parent.minX && maxX == parent.maxX && minZ == parent.minZ && maxZ == parent.maxZ)
            return Result.fail("Une parcelle ne peut pas couvrir tout le claim.");
        for (Claim s : parent.children) {
            if (s.overlaps(minX, minZ, maxX, maxZ)) return Result.fail("Cette parcelle chevauche « " + s.displayName() + " ».");
        }
        Claim c = newClaim(parent.dimension, minX, minZ, maxX, maxZ);
        c.parent = parent.id;
        c.type = parent.type;
        c.owner = parent.owner;
        c.ownerName = parent.ownerName;
        ALL.put(c.id, c);
        parent.children.add(c);
        changed(c);
        return Result.ok("Parcelle n°" + c.id + " créée dans « " + parent.displayName() + " » (" + c.width() + " × " + c.length() + ").", c);
    }

    /** Redimensionnement (claim principal ou sous-zone). Le cout est porte par le proprietaire. */
    public static Result resize(ServerPlayer actor, Claim c, int x1, int z1, int x2, int z2) {
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2), minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        if (c.isSub()) {
            Claim parent = top(c);
            if (!parent.containsRect(minX, minZ, maxX, maxZ)) return Result.fail("La parcelle doit rester dans le claim principal.");
            int mw = ClaimsConfig.get().claims.subclaim_min_width;
            if (maxX - minX + 1 < mw || maxZ - minZ + 1 < mw) return Result.fail("Parcelle trop petite.");
            for (Claim s : parent.children) {
                if (s != c && s.overlaps(minX, minZ, maxX, maxZ)) return Result.fail("La parcelle chevaucherait « " + s.displayName() + " ».");
            }
            c.minX = minX;
            c.maxX = maxX;
            c.minZ = minZ;
            c.maxZ = maxZ;
            changed(c);
            return Result.ok("Parcelle redimensionnée (" + c.width() + " × " + c.length() + ").", c);
        }
        if (!WorldBorders.rectInside(c.dimension, minX, minZ, maxX, maxZ)) return Result.fail("La zone dépasserait la bordure du monde.");
        for (Claim o : INDEX.overlapping(c.dimension, minX, minZ, maxX, maxZ)) {
            if (o != c) return Result.fail(overlapMessage(List.of(o)));
        }
        for (Claim s : c.children) {
            if (!(s.minX >= minX && s.maxX <= maxX && s.minZ >= minZ && s.maxZ <= maxZ))
                return Result.fail("La parcelle « " + s.displayName() + " » sortirait du claim.");
        }
        long newArea = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
        boolean staffForce = actor != null && Perms.staff(actor) && (c.owner == null || !c.owner.equals(actor.getUUID()));
        if (c.type == ClaimType.PLAYER && !staffForce) {
            ClaimsConfig.Claims cfg = ClaimsConfig.get().claims;
            if (maxX - minX + 1 < cfg.min_width || maxZ - minZ + 1 < cfg.min_width || newArea < cfg.min_area)
                return Result.fail("Taille minimale : " + cfg.min_width + " × " + cfg.min_width + " (" + cfg.min_area + " blocs).");
            long available = BlockEconomy.available(c.owner) + c.area();
            if (available < newArea) return Result.fail("Solde insuffisant : " + newArea + " blocs requis, " + available + " disponibles.");
        }
        INDEX.remove(c);
        c.minX = minX;
        c.maxX = maxX;
        c.minZ = minZ;
        c.maxZ = maxZ;
        INDEX.add(c);
        changed(c);
        return Result.ok("Territoire redimensionné (" + c.width() + " × " + c.length() + " = " + newArea + " blocs).", c);
    }

    public static void delete(Claim c) {
        if (c == null) return;
        for (Claim s : new ArrayList<>(c.children)) delete(s);
        ALL.remove(c.id);
        if (c.isSub()) {
            Claim p = ALL.get(c.parent);
            if (p != null) p.children.remove(c);
        } else INDEX.remove(c);
        Store.deleteClaim(c.id);
        for (Consumer<Claim> l : REMOVED) safe(l, c);
    }

    public static void convertToAdmin(Claim c) {
        Claim t = top(c);
        if (t.type == ClaimType.ADMIN) return;
        t.formerOwner = t.owner;
        t.formerOwnerName = t.ownerName;
        t.type = ClaimType.ADMIN;
        t.owner = null;
        t.ownerName = "Administration";
        for (Claim s : t.children) {
            s.type = ClaimType.ADMIN;
            s.owner = null;
            s.ownerName = t.ownerName;
            changed(s);
        }
        changed(t);
    }

    public static void convertToPlayer(Claim c, UUID owner, String name) {
        Claim t = top(c);
        t.type = ClaimType.PLAYER;
        t.owner = owner;
        t.ownerName = name;
        t.formerOwner = null;
        t.formerOwnerName = null;
        for (Claim s : t.children) {
            s.type = ClaimType.PLAYER;
            s.owner = owner;
            s.ownerName = name;
            changed(s);
        }
        changed(t);
    }

    public static void setTrust(Claim c, UUID target, String name, TrustLevel lvl) {
        String k = target == null ? "*" : target.toString();
        if (lvl == null || lvl == TrustLevel.NONE) {
            c.trusted.remove(k);
            c.trustedNames.remove(k);
        } else {
            c.trusted.put(k, lvl);
            c.trustedNames.put(k, target == null ? "Public" : name);
        }
        changed(c);
    }

    /** Sauvegarde differee + notification des ecouteurs. */
    public static void changed(Claim c) {
        Store.saveClaim(c);
        for (Consumer<Claim> l : CHANGED) safe(l, c);
    }

    /** Met a jour le pseudo du proprietaire et des membres (changement de pseudo). */
    public static void refreshNames(UUID id, String name) {
        for (Claim c : ALL.values()) {
            boolean dirty = false;
            if (id.equals(c.owner) && !name.equals(c.ownerName)) {
                c.ownerName = name;
                dirty = true;
            }
            String k = id.toString();
            if (c.trustedNames.containsKey(k) && !name.equals(c.trustedNames.get(k))) {
                c.trustedNames.put(k, name);
                dirty = true;
            }
            if (dirty) changed(c);
        }
    }

    private static void safe(Consumer<Claim> l, Claim c) {
        try {
            l.accept(c);
        } catch (Throwable t) {
            CobblePulseClaims.LOG.error("[CobblePulse-Claims] Écouteur de claim en erreur", t);
        }
    }

    /** Tests : suppression de tout (monde de test uniquement). */
    public static void clearForTests(Collection<Claim> claims) {
        for (Claim c : new ArrayList<>(claims)) if (ALL.containsKey(c.id)) delete(c);
    }
}
