package fr.cobblepulse.claims.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.cobblepulse.claims.ClaimsConfig;
import fr.cobblepulse.claims.border.WorldBorders;
import fr.cobblepulse.claims.claim.Access;
import fr.cobblepulse.claims.claim.ClaimManager;
import fr.cobblepulse.claims.claim.Relation;
import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.data.FlagState;
import fr.cobblepulse.claims.data.PlayerClaimData;
import fr.cobblepulse.claims.data.TrustLevel;
import fr.cobblepulse.claims.economy.BlockEconomy;
import fr.cobblepulse.claims.flag.Flag;
import fr.cobblepulse.claims.flag.Flags;
import fr.cobblepulse.claims.integration.ClanBridge;
import fr.cobblepulse.claims.perm.Perms;
import fr.cobblepulse.claims.tools.WandService;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Donnees envoyees aux ecrans natifs du client (JSON). */
public final class Views {
    private Views() {}

    public static JsonObject summary(ServerPlayer viewer, Claim c) {
        JsonObject o = new JsonObject();
        Claim top = ClaimManager.top(c);
        o.addProperty("id", c.id);
        o.addProperty("parent", c.parent);
        o.addProperty("name", c.displayName());
        o.addProperty("named", c.name != null && !c.name.isBlank());
        o.addProperty("owner", top.isAdmin() ? "Administration" : top.ownerName);
        String tag = top.isAdmin() ? null : ClanBridge.tag(top.owner);
        if (tag != null) o.addProperty("clan", tag);
        o.addProperty("type", top.type.name());
        o.addProperty("rel", Relation.of(viewer == null ? null : viewer.getUUID(), c).name());
        o.addProperty("dim", c.dimension);
        o.addProperty("minX", c.minX);
        o.addProperty("minZ", c.minZ);
        o.addProperty("maxX", c.maxX);
        o.addProperty("maxZ", c.maxZ);
        o.addProperty("area", c.area());
        return o;
    }

    private static JsonObject rect(Claim c) {
        JsonObject o = new JsonObject();
        o.addProperty("id", c.id);
        o.addProperty("name", c.displayName());
        o.addProperty("minX", c.minX);
        o.addProperty("minZ", c.minZ);
        o.addProperty("maxX", c.maxX);
        o.addProperty("maxZ", c.maxZ);
        return o;
    }

    public static JsonArray flags(ServerPlayer p, Claim c) {
        JsonArray arr = new JsonArray();
        for (Flag f : Flags.all()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", f.id());
            o.addProperty("cat", f.category().name());
            o.addProperty("catLabel", f.category().label);
            o.addProperty("zone", f.isZone());
            o.addProperty("label", f.label());
            o.addProperty("desc", f.description());
            o.addProperty("level", f.memberLevel().label);
            FlagState v = c == null ? null : (f.isZone() ? c.visitorFlags : c.visitorFlags).get(f.id());
            FlagState m = c == null || f.isZone() ? null : c.memberFlags.get(f.id());
            o.addProperty("v", v == null ? "DEFAULT" : v.name());
            o.addProperty("m", m == null ? "DEFAULT" : m.name());
            // valeur que donne DEFAULT ici (parcelle -> claim principal -> valeur globale)
            Claim parent = c != null && c.isSub() && c.inherit ? ClaimManager.top(c) : null;
            o.addProperty("dv", Access.effective(parent, f, false).name());
            o.addProperty("dm", Access.effective(parent, f, true).name());
            o.addProperty("gv", Flags.global(f).visitor.name());
            o.addProperty("gm", Flags.global(f).member.name());
            o.addProperty("locked", !Flags.editable(f));
            o.addProperty("editable", c == null ? Perms.has(p, Perms.ADMIN_CONFIG) : Access.canEditFlag(p, c, f));
            if (c != null && c.staffFlags.contains(f.id())) o.addProperty("staff", true);
            arr.add(o);
        }
        return arr;
    }

    public static JsonObject manager(ServerPlayer p, Claim c) {
        JsonObject o = new JsonObject();
        Claim top = ClaimManager.top(c);
        JsonObject cl = summary(p, c);
        cl.addProperty("rawName", c.name == null ? "" : c.name);
        cl.addProperty("inherit", c.inherit);
        cl.addProperty("created", c.created);
        cl.addProperty("parentName", c.isSub() ? top.displayName() : "");
        cl.addProperty("formerOwner", top.formerOwnerName == null ? "" : top.formerOwnerName);
        o.add("claim", cl);
        TrustLevel lvl = Access.trust(c, p.getUUID());
        boolean staff = Perms.staff(p);
        o.addProperty("role", lvl == TrustLevel.OWNER ? "OWNER" : lvl == TrustLevel.MANAGER ? "MANAGER" : staff ? "STAFF" : lvl.name());
        JsonObject perms = new JsonObject();
        perms.addProperty("rename", Perms.has(p, Perms.CITY_NAME) || staff);
        perms.addProperty("convert", !top.isAdmin() && Perms.has(p, Perms.CONVERT_ADMIN));
        perms.addProperty("toPlayer", top.isAdmin() && Perms.has(p, Perms.CONVERT_ADMIN));
        perms.addProperty("delete", lvl == TrustLevel.OWNER || (c.isSub() && lvl.atLeast(TrustLevel.MANAGER)) || Perms.has(p, Perms.DELETE_OTHERS));
        perms.addProperty("trust", Perms.has(p, Perms.TRUST) || staff);
        perms.addProperty("grantManager", lvl == TrustLevel.OWNER || staff);
        perms.addProperty("settings", Perms.has(p, Perms.ADMIN_CONFIG) || (top.isAdmin() && staff));
        perms.addProperty("subclaim", Perms.has(p, Perms.SUBCLAIM) || staff);
        perms.addProperty("tp", Perms.has(p, Perms.INSPECT_ALL));
        o.add("perms", perms);
        o.add("flags", flags(p, c));
        JsonObject cmd = new JsonObject();
        cmd.addProperty("mode", c.commands.mode);
        JsonArray list = new JsonArray();
        for (String s : c.commands.list) list.add(s);
        cmd.add("list", list);
        cmd.addProperty("v", stateOr(c.visitorFlags.get(Flags.COMMANDS.id())));
        cmd.addProperty("m", stateOr(c.memberFlags.get(Flags.COMMANDS.id())));
        o.add("commands", cmd);
        JsonArray members = new JsonArray();
        for (Map.Entry<String, TrustLevel> e : c.trusted.entrySet()) {
            JsonObject m = new JsonObject();
            m.addProperty("uuid", e.getKey());
            m.addProperty("name", c.trustedNames.getOrDefault(e.getKey(), e.getKey()));
            m.addProperty("level", e.getValue().name());
            m.addProperty("label", e.getValue().label);
            members.add(m);
        }
        o.add("members", members);
        if (c.isSub() && c.inherit) {
            JsonArray inherited = new JsonArray();
            for (Map.Entry<String, TrustLevel> e : top.trusted.entrySet()) {
                JsonObject m = new JsonObject();
                m.addProperty("name", top.trustedNames.getOrDefault(e.getKey(), e.getKey()));
                m.addProperty("label", e.getValue().label);
                inherited.add(m);
            }
            o.add("inheritedMembers", inherited);
        }
        JsonArray subs = new JsonArray();
        for (Claim s : top.children) {
            JsonObject so = rect(s);
            so.addProperty("area", s.area());
            so.addProperty("members", s.trusted.size());
            so.addProperty("inherit", s.inherit);
            subs.add(so);
        }
        o.add("subs", subs);
        o.addProperty("topId", top.id);
        JsonObject settings = new JsonObject();
        settings.addProperty("weather", c.settings.weather);
        settings.addProperty("time", c.settings.time);
        settings.addProperty("level_cap", c.settings.level_cap);
        o.add("settings", settings);
        // visualiseur 2D : le claim, ses parcelles, les voisins proches et la position du joueur
        JsonObject map = new JsonObject();
        map.add("top", rect(top));
        JsonArray near = new JsonArray();
        int margin = Math.max(16, Math.max(top.width(), top.length()) / 2);
        for (Claim n : ClaimManager.overlapping(top.dimension, top.minX - margin, top.minZ - margin, top.maxX + margin, top.maxZ + margin)) {
            if (n == top) continue;
            JsonObject no = rect(n);
            no.addProperty("rel", Relation.of(p.getUUID(), n).name());
            near.add(no);
        }
        map.add("near", near);
        map.addProperty("px", p.getX());
        map.addProperty("pz", p.getZ());
        map.addProperty("sameDim", ClaimManager.dim(p.level()).equals(c.dimension));
        o.add("map", map);
        if (top.owner != null) {
            o.addProperty("ownerAvailable", BlockEconomy.available(top.owner));
        }
        return o;
    }

    private static String stateOr(FlagState s) {
        return s == null ? "DEFAULT" : s.name();
    }

    /** Fiche synthetique en lecture seule (claim d'un tiers). */
    public static JsonObject info(ServerPlayer p, Claim c) {
        JsonObject o = new JsonObject();
        Claim top = ClaimManager.top(c);
        o.add("claim", summary(p, c));
        o.addProperty("topName", top.displayName());
        o.addProperty("role", Access.trust(c, p.getUUID()).label);
        o.addProperty("children", top.children.size());
        JsonArray rules = new JsonArray();
        Flag[] shown = {Flags.ENTER, Flags.BLOCK_BREAK, Flags.BLOCK_PLACE, Flags.CHEST, Flags.DOOR, Flags.BUTTON, Flags.CAPTURE, Flags.BATTLE_WILD,
                Flags.BATTLE_PVP, Flags.PVP, Flags.HEALER, Flags.PC, Flags.COMMANDS, Flags.ENDERPEARL, Flags.PICKUP, Flags.RANCH};
        for (Flag f : shown) {
            JsonObject r = new JsonObject();
            r.addProperty("label", f.label());
            r.addProperty("you", Access.allows(p, c, f));
            r.addProperty("public", Access.effective(c, f, false) == FlagState.ALLOW);
            rules.add(r);
        }
        o.add("rules", rules);
        o.addProperty("canManage", Access.canManage(p, c));
        return o;
    }

    /** Tableau de bord global du joueur (dans la nature). */
    public static JsonObject dashboard(ServerPlayer p) {
        JsonObject o = new JsonObject();
        PlayerClaimData d = BlockEconomy.get(p.getUUID());
        long used = ClaimManager.usedBlocks(p.getUUID());
        o.addProperty("available", BlockEconomy.available(p.getUUID()));
        o.addProperty("total", d == null ? 0 : d.total());
        o.addProperty("accrued", d == null ? 0 : d.accrued);
        o.addProperty("bonus", d == null ? 0 : d.bonus);
        o.addProperty("used", used);
        o.addProperty("cap", BlockEconomy.cap(p));
        o.addProperty("perInterval", BlockEconomy.perInterval(p));
        o.addProperty("intervalMin", ClaimsConfig.get().economy.interval_minutes);
        o.addProperty("untilNext", BlockEconomy.untilNext(p));
        o.addProperty("afk", BlockEconomy.isAfk(p));
        o.addProperty("accrue", Perms.has(p, Perms.ACCRUE));
        int max = ClaimManager.maxClaims(p);
        o.addProperty("maxClaims", max == Integer.MAX_VALUE ? -1 : max);
        JsonArray claims = new JsonArray();
        for (Claim c : ClaimManager.ownedBy(p.getUUID())) {
            JsonObject co = summary(p, c);
            co.addProperty("subs", c.children.size());
            co.addProperty("members", c.trusted.size());
            claims.add(co);
        }
        o.add("claims", claims);
        JsonArray trusted = new JsonArray();
        String me = p.getUUID().toString();
        for (Claim c : ClaimManager.all()) {
            if (c.isSub() || !c.trusted.containsKey(me)) continue;
            JsonObject co = summary(p, c);
            co.addProperty("level", c.trusted.get(me).label);
            trusted.add(co);
        }
        o.add("trusted", trusted);
        o.addProperty("staff", Perms.staff(p) || Perms.has(p, Perms.ADMIN_WORLDBORDER) || Perms.has(p, Perms.ADMIN_GIVE_BLOCKS));
        o.addProperty("dim", ClaimManager.dim(p.level()));
        o.addProperty("px", p.getX());
        o.addProperty("pz", p.getZ());
        return o;
    }

    /** Interface d'administration globale (AdminClaimsHubScreen / AdminPermissionsConfigScreen). */
    public static JsonObject admin(ServerPlayer p, String tab) {
        JsonObject o = new JsonObject();
        o.addProperty("tab", tab == null ? "claims" : tab);
        JsonObject perms = new JsonObject();
        perms.addProperty("config", Perms.has(p, Perms.ADMIN_CONFIG));
        perms.addProperty("worldborder", Perms.has(p, Perms.ADMIN_WORLDBORDER));
        perms.addProperty("give", Perms.has(p, Perms.ADMIN_GIVE_BLOCKS));
        perms.addProperty("delete", Perms.has(p, Perms.DELETE_OTHERS));
        perms.addProperty("convert", Perms.has(p, Perms.CONVERT_ADMIN));
        perms.addProperty("inspect", Perms.has(p, Perms.INSPECT_ALL));
        perms.addProperty("bypass", Perms.has(p, Perms.BYPASS));
        o.add("perms", perms);
        o.addProperty("bypassOn", Perms.bypass(p));
        o.addProperty("adminMode", WandService.adminMode(p.getUUID()));
        JsonArray claims = new JsonArray();
        List<Claim> all = new ArrayList<>(ClaimManager.topClaims(null));
        all.sort((a, b) -> Integer.compare(b.id, a.id));
        int n = 0;
        for (Claim c : all) {
            if (n++ >= 3000) break;
            JsonObject co = summary(p, c);
            co.addProperty("subs", c.children.size());
            co.addProperty("members", c.trusted.size());
            claims.add(co);
        }
        o.add("claims", claims);
        o.addProperty("claimCount", all.size());
        o.add("flags", flags(p, null));
        ClaimsConfig.Economy e = ClaimsConfig.get().economy;
        JsonObject eco = new JsonObject();
        eco.addProperty("interval_minutes", e.interval_minutes);
        eco.addProperty("blocks_per_interval", e.blocks_per_interval);
        eco.addProperty("max_accrued_blocks", e.max_accrued_blocks);
        eco.addProperty("initial_blocks", e.initial_blocks);
        eco.addProperty("afk_minutes", e.afk_minutes);
        o.add("economy", eco);
        // limite de territoires : reglage du fichier uniquement, affichee en lecture seule
        o.addProperty("maxClaims", ClaimsConfig.get().claims.max_claims_per_player);
        o.addProperty("maxClaimsLp", ClaimsConfig.get().claims.luckperms_max_claims_override);
        JsonArray borders = new JsonArray();
        for (Map.Entry<String, WorldBorders.Border> be : WorldBorders.all().entrySet()) {
            WorldBorders.Border b = be.getValue();
            JsonObject bo = new JsonObject();
            bo.addProperty("dim", be.getKey());
            bo.addProperty("enabled", b.enabled);
            bo.addProperty("cx", b.centerX);
            bo.addProperty("cz", b.centerZ);
            bo.addProperty("shape", b.circle() ? "CIRCLE" : "RECTANGLE");
            bo.addProperty("radius", b.radius);
            bo.addProperty("sx", b.sizeX);
            bo.addProperty("sz", b.sizeZ);
            borders.add(bo);
        }
        o.add("borders", borders);
        o.addProperty("edgeBuffer", WorldBorders.edgeBuffer());
        o.addProperty("dim", ClaimManager.dim(p.level()));
        o.addProperty("px", p.getBlockX());
        o.addProperty("pz", p.getBlockZ());
        return o;
    }
}
