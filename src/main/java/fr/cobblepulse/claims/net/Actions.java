package fr.cobblepulse.claims.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.cobblepulse.claims.ClaimsConfig;
import fr.cobblepulse.claims.CobblePulseClaims;
import fr.cobblepulse.claims.border.WorldBorders;
import fr.cobblepulse.claims.claim.Access;
import fr.cobblepulse.claims.claim.ClaimManager;
import fr.cobblepulse.claims.claim.ClaimOps;
import fr.cobblepulse.claims.claim.Names;
import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.data.FlagState;
import fr.cobblepulse.claims.data.PlayerClaimData;
import fr.cobblepulse.claims.data.TrustLevel;
import fr.cobblepulse.claims.economy.BlockEconomy;
import fr.cobblepulse.claims.flag.Flag;
import fr.cobblepulse.claims.flag.Flags;
import fr.cobblepulse.claims.perm.Perms;
import fr.cobblepulse.claims.tools.WandService;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Actions envoyees par les ecrans (ClaimActionPayload). Pipeline de validation cote serveur avant toute execution :
 * 1) identite et role (proprietaire, membre habilite ou staff LuckPerms), 2) flags modifiables seulement,
 * 3) privileges staff verifies explicitement (conversion, WorldBorder, economie...). Toute tentative refusee est
 * journalisee.
 */
public final class Actions {
    private Actions() {}

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static int num(JsonObject o, String k, int def) {
        try {
            return o.has(k) ? o.get(k).getAsInt() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static double dbl(JsonObject o, String k, double def) {
        try {
            return o.has(k) ? o.get(k).getAsDouble() : def;
        } catch (Exception e) {
            return def;
        }
    }

    private static void refuse(ServerPlayer p, String action, String why) {
        CobblePulseClaims.LOG.warn("[CobblePulse-Claims] Action refusée « {} » de {} : {}", action, p.getGameProfile().getName(), why);
        ClaimsNet.result(p, false, why);
    }

    /** Re-envoie l'ecran du claim apres modification. */
    private static void refresh(ServerPlayer p, Claim c, ClaimOps.Res r) {
        if (r != null) ClaimsNet.result(p, r.ok(), r.message());
        if (c != null && ClaimManager.get(c.id) != null && Access.canManage(p, c)) ClaimsNet.open(p, "manager", Views.manager(p, c));
    }

    public static void handle(ServerPlayer p, String action, JsonObject o) {
        if (!Perms.has(p, Perms.USE)) {
            refuse(p, action, "Accès aux territoires refusé.");
            return;
        }
        Claim c = o.has("id") ? ClaimManager.get(num(o, "id", 0)) : null;
        switch (action) {
            case "open_dashboard" -> ClaimsNet.openDashboard(p);
            case "open_manager" -> {
                if (c == null) refuse(p, action, "Territoire introuvable.");
                else if (Access.canManage(p, c)) ClaimsNet.openManager(p, c);
                else ClaimsNet.openInfo(p, c);
            }
            case "open_info" -> {
                if (c != null) ClaimsNet.openInfo(p, c);
            }
            case "open_admin" -> {
                if (!adminAny(p)) refuse(p, action, "Interface réservée au staff.");
                else ClaimsNet.openAdmin(p, str(o, "tab"));
            }
            case "set_name" -> {
                if (c == null) return;
                refresh(p, c, ClaimOps.rename(p, c, str(o, "name")));
            }
            case "set_flag" -> {
                if (c == null) return;
                Flag f = Flags.byId(str(o, "flag"));
                FlagState st = FlagState.parse(str(o, "state"));
                ClaimOps.Res r = ClaimOps.setFlag(p, c, f, "member".equals(str(o, "column")), st);
                if (!r.ok()) CobblePulseClaims.LOG.warn("[CobblePulse-Claims] Flag refusé pour {} : {}", p.getGameProfile().getName(), r.message());
                refresh(p, c, r);
            }
            case "set_commands" -> {
                if (c == null) return;
                List<String> list = new ArrayList<>();
                if (o.has("list") && o.get("list").isJsonArray()) for (JsonElement e : o.getAsJsonArray("list")) list.add(e.getAsString());
                refresh(p, c, ClaimOps.setCommands(p, c, str(o, "mode"), list));
            }
            case "trust" -> {
                if (c == null) return;
                String name = str(o, "name");
                TrustLevel lvl = TrustLevel.parse(str(o, "level"));
                if ("public".equalsIgnoreCase(name) || "*".equals(name)) {
                    refresh(p, c, ClaimOps.trust(p, c, null, "Public", lvl == TrustLevel.MANAGER ? TrustLevel.BUILD : lvl));
                    return;
                }
                Names.Resolved r = Names.resolve(p.server, name);
                if (r == null) {
                    refresh(p, c, new ClaimOps.Res(false, "Joueur inconnu : " + name));
                    return;
                }
                refresh(p, c, ClaimOps.trust(p, c, r.uuid(), r.name(), lvl));
            }
            case "untrust" -> {
                if (c == null) return;
                String id = str(o, "uuid");
                UUID target = null;
                if (id != null && !id.equals("*")) {
                    try {
                        target = UUID.fromString(id);
                    } catch (IllegalArgumentException e) {
                        return;
                    }
                }
                refresh(p, c, ClaimOps.untrust(p, c, target, target == null ? "Public" : c.trustedNames.getOrDefault(id, id)));
            }
            case "set_inherit" -> {
                if (c == null) return;
                refresh(p, c, ClaimOps.setInherit(p, c, o.has("value") && o.get("value").getAsBoolean()));
            }
            case "set_setting" -> {
                if (c == null) return;
                refresh(p, c, ClaimOps.setSetting(p, c, str(o, "key"), str(o, "value")));
            }
            case "delete" -> {
                if (c == null) return;
                Claim top = ClaimManager.top(c);
                ClaimOps.Res r = ClaimOps.delete(p, c);
                ClaimsNet.result(p, r.ok(), r.message());
                if (r.ok()) {
                    if (c.isSub() && ClaimManager.get(top.id) != null) ClaimsNet.open(p, "manager", Views.manager(p, top));
                    else if ("admin".equals(str(o, "from"))) ClaimsNet.openAdmin(p, "claims");
                    else ClaimsNet.openDashboard(p);
                } else CobblePulseClaims.LOG.warn("[CobblePulse-Claims] Suppression refusée pour {} (claim {})", p.getGameProfile().getName(), c.id);
            }
            case "convert_admin" -> {
                if (c == null) return;
                if (!Perms.has(p, Perms.CONVERT_ADMIN)) {
                    refuse(p, action, "Conversion refusée : nœud cobblepulse.staff.convert_admin absent.");
                    return;
                }
                refresh(p, c, ClaimOps.convertToAdmin(p, c));
            }
            case "convert_player" -> {
                if (c == null) return;
                if (!Perms.has(p, Perms.CONVERT_ADMIN)) {
                    refuse(p, action, "Conversion refusée : nœud cobblepulse.staff.convert_admin absent.");
                    return;
                }
                String name = str(o, "name");
                Names.Resolved r = name == null || name.isBlank() ? null : Names.resolve(p.server, name);
                if (name != null && !name.isBlank() && r == null) {
                    refresh(p, c, new ClaimOps.Res(false, "Joueur inconnu : " + name));
                    return;
                }
                refresh(p, c, ClaimOps.convertToPlayer(p, c, r == null ? null : r.uuid(), r == null ? null : r.name()));
            }
            case "highlight" -> {
                if (c != null) ClaimsNet.sendHighlight(p, c);
            }
            case "tp" -> {
                if (c == null) return;
                if (!Perms.has(p, Perms.INSPECT_ALL)) {
                    refuse(p, action, "Téléportation réservée au staff.");
                    return;
                }
                ServerLevel level = p.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        ResourceLocation.parse(c.dimension)));
                if (level == null) return;
                int x = (c.minX + c.maxX) / 2, z = (c.minZ + c.maxZ) / 2;
                level.getChunk(x >> 4, z >> 4);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                p.teleportTo(level, x + 0.5, y, z + 0.5, p.getYRot(), p.getXRot());
            }
            // ---------------------------------------------------------- administration
            case "admin_flag" -> {
                if (!Perms.has(p, Perms.ADMIN_CONFIG)) {
                    refuse(p, action, "Nœud cobblepulse.admin.config requis.");
                    return;
                }
                Flag f = Flags.byId(str(o, "flag"));
                FlagState st = FlagState.parse(str(o, "state"));
                if (f == null || st == null || st == FlagState.DEFAULT) return;
                Flags.setGlobal(f, "member".equals(str(o, "column")), st);
                ClaimsNet.result(p, true, f.label() + " : défaut " + ("member".equals(str(o, "column")) ? "membres" : "visiteurs") + " = " + ClaimOps.label(st));
                ClaimsNet.openAdmin(p, "flags");
            }
            case "admin_lock" -> {
                if (!Perms.has(p, Perms.ADMIN_CONFIG)) {
                    refuse(p, action, "Nœud cobblepulse.admin.config requis.");
                    return;
                }
                Flag f = Flags.byId(str(o, "flag"));
                if (f == null) return;
                boolean editable = o.has("editable") && o.get("editable").getAsBoolean();
                Flags.setEditable(f, editable);
                ClaimsNet.result(p, true, f.label() + (editable ? " : modifiable par les joueurs." : " : 🔒 verrouillée par le serveur."));
                ClaimsNet.openAdmin(p, "flags");
            }
            case "admin_economy" -> {
                if (!Perms.has(p, Perms.ADMIN_CONFIG)) {
                    refuse(p, action, "Nœud cobblepulse.admin.config requis.");
                    return;
                }
                ClaimsConfig.Economy e = ClaimsConfig.get().economy;
                e.interval_minutes = Math.max(1, num(o, "interval_minutes", e.interval_minutes));
                e.blocks_per_interval = Math.max(0, num(o, "blocks_per_interval", e.blocks_per_interval));
                e.max_accrued_blocks = Math.max(0, num(o, "max_accrued_blocks", e.max_accrued_blocks));
                e.initial_blocks = Math.max(0, num(o, "initial_blocks", e.initial_blocks));
                e.afk_minutes = Math.max(0, num(o, "afk_minutes", e.afk_minutes));
                ClaimsConfig.save();
                ClaimsNet.result(p, true, "Économie : " + e.blocks_per_interval + " blocs toutes les " + e.interval_minutes + " min, plafond " + e.max_accrued_blocks
                        + ", départ " + e.initial_blocks + ".");
                ClaimsNet.openAdmin(p, "economy");
            }
            case "admin_border" -> {
                if (!Perms.has(p, Perms.ADMIN_WORLDBORDER)) {
                    refuse(p, action, "Nœud cobblepulse.admin.worldborder requis.");
                    return;
                }
                String dim = str(o, "dim");
                if (dim == null || WorldBorders.get(dim) == null) return;
                WorldBorders.Border b = WorldBorders.get(dim).copy();
                b.enabled = o.has("enabled") ? o.get("enabled").getAsBoolean() : b.enabled;
                b.centerX = dbl(o, "cx", b.centerX);
                b.centerZ = dbl(o, "cz", b.centerZ);
                if (o.has("shape")) b.shape = "RECTANGLE".equalsIgnoreCase(str(o, "shape")) ? "RECTANGLE" : "CIRCLE";
                b.radius = dbl(o, "radius", b.radius);
                b.sizeX = dbl(o, "sx", b.sizeX);
                b.sizeZ = dbl(o, "sz", b.sizeZ);
                WorldBorders.set(dim, b);
                ClaimsNet.result(p, true, "Bordure de " + dim + " : " + (b.enabled ? WorldBorders.describe(b) : "désactivée") + ".");
                ClaimsNet.openAdmin(p, "borders");
            }
            case "admin_edge" -> {
                if (!Perms.has(p, Perms.ADMIN_WORLDBORDER)) {
                    refuse(p, action, "Nœud cobblepulse.admin.worldborder requis.");
                    return;
                }
                WorldBorders.setEdgeBuffer(num(o, "value", WorldBorders.edgeBuffer()));
                ClaimsNet.openAdmin(p, "borders");
            }
            case "admin_blocks" -> {
                if (!Perms.has(p, Perms.ADMIN_GIVE_BLOCKS)) {
                    refuse(p, action, "Nœud cobblepulse.admin.give_blocks requis.");
                    return;
                }
                Names.Resolved r = Names.resolve(p.server, str(o, "name"));
                if (r == null) {
                    ClaimsNet.result(p, false, "Joueur inconnu : " + str(o, "name"));
                    return;
                }
                PlayerClaimData d = BlockEconomy.getOrCreate(r.uuid(), r.name());
                int amount = num(o, "amount", 0);
                String op = String.valueOf(str(o, "op"));
                switch (op) {
                    case "add" -> BlockEconomy.addBonus(d, Math.abs(amount));
                    case "remove" -> BlockEconomy.addBonus(d, -Math.abs(amount));
                    case "set" -> BlockEconomy.setTotal(d, Math.max(0, amount));
                    default -> {
                        return;
                    }
                }
                ClaimsNet.result(p, true, r.name() + " : total " + d.total() + " blocs, " + BlockEconomy.available(r.uuid()) + " disponibles.");
                ClaimsNet.openAdmin(p, "economy");
            }
            case "admin_bypass" -> {
                if (!Perms.has(p, Perms.BYPASS)) {
                    refuse(p, action, "Nœud cobblepulse.staff.bypass requis.");
                    return;
                }
                boolean on = Perms.setBypass(p, !Perms.bypass(p));
                ClaimsNet.result(p, true, "Contournement des protections : " + (on ? "activé" : "désactivé") + ".");
                ClaimsNet.openAdmin(p, str(o, "tab"));
            }
            case "admin_mode" -> {
                if (!Perms.has(p, Perms.CONVERT_ADMIN)) {
                    refuse(p, action, "Nœud cobblepulse.staff.convert_admin requis.");
                    return;
                }
                boolean on = WandService.toggleAdminMode(p);
                ClaimsNet.result(p, true, on ? "La Balise crée maintenant des claims admin." : "La Balise crée de nouveau des claims joueur.");
                ClaimsNet.openAdmin(p, str(o, "tab"));
            }
            default -> CobblePulseClaims.LOG.warn("[CobblePulse-Claims] Action inconnue « {} » de {}", action, p.getGameProfile().getName());
        }
    }

    static boolean adminAny(ServerPlayer p) {
        return Perms.staff(p) || Perms.has(p, Perms.ADMIN_WORLDBORDER) || Perms.has(p, Perms.ADMIN_GIVE_BLOCKS) || Perms.has(p, Perms.CONVERT_ADMIN)
                || Perms.has(p, Perms.DELETE_OTHERS);
    }

    public static BlockPos center(Claim c) {
        return new BlockPos((c.minX + c.maxX) / 2, 64, (c.minZ + c.maxZ) / 2);
    }
}
