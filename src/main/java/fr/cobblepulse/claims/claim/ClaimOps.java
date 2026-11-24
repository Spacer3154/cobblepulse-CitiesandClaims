package fr.cobblepulse.claims.claim;

import fr.cobblepulse.claims.ClaimsConfig;
import fr.cobblepulse.claims.CobblePulseClaims;
import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.data.CommandRule;
import fr.cobblepulse.claims.data.FlagState;
import fr.cobblepulse.claims.data.TrustLevel;
import fr.cobblepulse.claims.flag.Flag;
import fr.cobblepulse.claims.flag.Flags;
import fr.cobblepulse.claims.perm.Perms;
import fr.cobblepulse.claims.zone.ZoneTracker;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Operations sur un claim communes aux commandes et aux ecrans, avec le pipeline de validation du cahier des charges :
 * identite et role (proprietaire, membre habilite, staff), filtrage des flags modifiables, privileges staff.
 */
public final class ClaimOps {
    public record Res(boolean ok, String message) {
        static Res ok(String m) {
            return new Res(true, m);
        }

        static Res no(String m) {
            return new Res(false, m);
        }
    }

    private ClaimOps() {}

    public static Res rename(ServerPlayer p, Claim c, String name) {
        if (!Access.canManage(p, c)) return Res.no("Seuls le propriétaire et les Co-Maires peuvent renommer ce territoire.");
        if (!Perms.has(p, Perms.CITY_NAME) && !Perms.staff(p)) return Res.no("Vous n'avez pas le droit de nommer une ville.");
        String n = name == null ? "" : name.replace('§', ' ').replaceAll("\\s+", " ").trim();
        int max = ClaimsConfig.get().claims.max_city_name_length;
        if (n.length() > max) return Res.no("Nom trop long (" + max + " caractères maximum).");
        c.name = n.isEmpty() ? null : n;
        ClaimManager.changed(c);
        resetTitles();
        return Res.ok(c.name == null ? "Nom retiré." : (c.isSub() ? "Parcelle" : "Ville") + " renommée « " + c.name + " ».");
    }

    private static void resetTitles() {
        var s = CobblePulseClaims.server();
        if (s != null) for (ServerPlayer pl : s.getPlayerList().getPlayers()) ZoneTracker.reset(pl.getUUID());
    }

    public static Res trust(ServerPlayer p, Claim c, UUID target, String name, TrustLevel lvl) {
        if (!Access.canManage(p, c)) return Res.no("Seuls le propriétaire et les Co-Maires gèrent les membres.");
        if (!Perms.has(p, Perms.TRUST) && !Perms.staff(p)) return Res.no("Vous n'avez pas le droit d'utiliser /trust.");
        if (lvl == null || lvl == TrustLevel.OWNER) return Res.no("Niveau inconnu : access, container, build ou manager.");
        Claim top = ClaimManager.top(c);
        if (target != null && target.equals(top.owner)) return Res.no("Le propriétaire a déjà tous les droits.");
        TrustLevel mine = Access.trust(c, p.getUUID());
        if (lvl == TrustLevel.MANAGER && mine != TrustLevel.OWNER && !Perms.staff(p)) return Res.no("Seul le propriétaire peut nommer un Co-Maire.");
        if (target != null) {
            TrustLevel current = c.trusted.get(target.toString());
            if (current == TrustLevel.MANAGER && mine != TrustLevel.OWNER && !Perms.staff(p)) return Res.no("Seul le propriétaire peut modifier un Co-Maire.");
        }
        ClaimManager.setTrust(c, target, name, lvl);
        return Res.ok((target == null ? "Tout le monde" : name) + " : " + lvl.label + " sur « " + c.displayName() + " ».");
    }

    public static Res untrust(ServerPlayer p, Claim c, UUID target, String name) {
        if (!Access.canManage(p, c)) return Res.no("Seuls le propriétaire et les Co-Maires gèrent les membres.");
        if (!Perms.has(p, Perms.TRUST) && !Perms.staff(p)) return Res.no("Vous n'avez pas le droit d'utiliser /untrust.");
        String k = target == null ? "*" : target.toString();
        TrustLevel current = c.trusted.get(k);
        if (current == null) return Res.no((target == null ? "L'accès public" : name) + " n'a aucun accès ici.");
        if (current == TrustLevel.MANAGER && Access.trust(c, p.getUUID()) != TrustLevel.OWNER && !Perms.staff(p))
            return Res.no("Seul le propriétaire peut retirer un Co-Maire.");
        ClaimManager.setTrust(c, target, name, TrustLevel.NONE);
        return Res.ok((target == null ? "Accès public" : name) + " retiré de « " + c.displayName() + " ».");
    }

    /** column : visitor / member (ignore pour un flag de zone). */
    public static Res setFlag(ServerPlayer p, Claim c, Flag f, boolean member, FlagState state) {
        if (f == null || state == null) return Res.no("Permission inconnue.");
        if (!Access.canEditFlag(p, c, f)) {
            if (!Flags.editable(f)) return Res.no("🔒 « " + f.label() + " » est verrouillée par le serveur.");
            return Res.no("Vous ne pouvez pas modifier les permissions de ce territoire.");
        }
        boolean col = !f.isZone() && member;
        var map = col ? c.memberFlags : c.visitorFlags;
        if (state == FlagState.DEFAULT) map.remove(f.id());
        else map.put(f.id(), state);
        boolean staffSet = !Flags.editable(f) || !Access.canManage(p, c) || c.isAdmin();
        if (staffSet && Perms.staff(p) && state != FlagState.DEFAULT) c.staffFlags.add(f.id());
        if (!c.visitorFlags.containsKey(f.id()) && !c.memberFlags.containsKey(f.id())) c.staffFlags.remove(f.id());
        ClaimManager.changed(c);
        return Res.ok(f.label() + (f.isZone() ? "" : col ? " (membres)" : " (visiteurs)") + " : " + label(state));
    }

    public static String label(FlagState s) {
        return switch (s) {
            case ALLOW -> "Autorisé";
            case DENY -> "Interdit";
            case DEFAULT -> "Valeur système";
        };
    }

    /** Sous-panneau de command.execute : ALLOW, DENY, BLACKLIST ou WHITELIST (+ liste de prefixes). */
    public static Res setCommands(ServerPlayer p, Claim c, String mode, List<String> list) {
        Flag f = Flags.COMMANDS;
        if (!Access.canEditFlag(p, c, f)) return Res.no("Vous ne pouvez pas modifier les commandes de ce territoire.");
        String m = mode == null ? "" : mode.toUpperCase(Locale.ROOT);
        List<String> clean = new ArrayList<>();
        if (list != null) {
            for (String s : list) {
                String n = CommandRule.normalize(s);
                if (n.isEmpty() || n.length() > 64 || clean.contains(n)) continue;
                clean.add(n);
                if (clean.size() >= 64) break;
            }
        }
        switch (m) {
            case "ALLOW", "DENY" -> {
                FlagState st = FlagState.valueOf(m);
                c.visitorFlags.put(f.id(), st);
                c.memberFlags.put(f.id(), st);
                c.commands.mode = "FLAG";
                c.commands.list = clean;
            }
            case "BLACKLIST", "WHITELIST" -> {
                c.visitorFlags.put(f.id(), FlagState.ALLOW);
                c.memberFlags.put(f.id(), FlagState.ALLOW);
                c.commands.mode = m;
                c.commands.list = clean;
            }
            case "DEFAULT", "FLAG" -> {
                c.visitorFlags.remove(f.id());
                c.memberFlags.remove(f.id());
                c.commands.mode = "FLAG";
                c.commands.list = clean;
            }
            default -> {
                return Res.no("Mode inconnu.");
            }
        }
        ClaimManager.changed(c);
        return Res.ok(switch (c.commands.mode) {
            case "BLACKLIST" -> "Commandes : liste noire (" + clean.size() + " préfixe(s) bloqué(s)).";
            case "WHITELIST" -> "Commandes : liste blanche (" + clean.size() + " préfixe(s) autorisé(s)).";
            default -> "Commandes : " + (m.equals("DENY") ? "tout bloquer" : m.equals("ALLOW") ? "tout autoriser" : "valeur système") + ".";
        });
    }

    public static Res delete(ServerPlayer p, Claim c) {
        TrustLevel lvl = Access.trust(c, p.getUUID());
        boolean own = lvl == TrustLevel.OWNER || (c.isSub() && lvl.atLeast(TrustLevel.MANAGER));
        if (!own && !Perms.has(p, Perms.DELETE_OTHERS)) return Res.no("Vous ne pouvez pas supprimer ce territoire.");
        String n = c.displayName();
        ClaimManager.delete(c);
        return Res.ok((c.isSub() ? "Parcelle « " : "Territoire « ") + n + " » supprimé" + (c.isSub() || c.isAdmin() ? "." : " : " + c.area() + " blocs rendus."));
    }

    public static Res convertToAdmin(ServerPlayer p, Claim c) {
        if (!Perms.has(p, Perms.CONVERT_ADMIN)) return Res.no("Permission cobblepulse.staff.convert_admin requise.");
        Claim top = ClaimManager.top(c);
        if (top.isAdmin()) return Res.no("C'est déjà un claim admin.");
        ClaimManager.convertToAdmin(top);
        resetTitles();
        return Res.ok("« " + top.displayName() + " » est maintenant un claim admin (sans coût en blocs).");
    }

    public static Res convertToPlayer(ServerPlayer p, Claim c, UUID owner, String name) {
        if (!Perms.has(p, Perms.CONVERT_ADMIN)) return Res.no("Permission cobblepulse.staff.convert_admin requise.");
        Claim top = ClaimManager.top(c);
        if (!top.isAdmin()) return Res.no("Ce n'est pas un claim admin.");
        if (owner == null) {
            owner = top.formerOwner;
            name = top.formerOwnerName;
        }
        if (owner == null) return Res.no("Précisez le joueur qui recevra ce territoire.");
        ClaimManager.convertToPlayer(top, owner, name);
        resetTitles();
        return Res.ok("Territoire rendu à " + name + ".");
    }

    public static Res setInherit(ServerPlayer p, Claim c, boolean inherit) {
        if (!c.isSub()) return Res.no("Seules les parcelles héritent du claim principal.");
        if (!Access.canManage(p, c)) return Res.no("Réservé au propriétaire et aux Co-Maires.");
        c.inherit = inherit;
        ClaimManager.changed(c);
        return Res.ok(inherit ? "La parcelle hérite des membres et permissions du claim." : "La parcelle a ses propres membres et permissions.");
    }

    public static Res setSetting(ServerPlayer p, Claim c, String key, String value) {
        if (!Perms.has(p, Perms.ADMIN_CONFIG) && !(ClaimManager.top(c).isAdmin() && Perms.staff(p))) return Res.no("Réglage réservé aux administrateurs.");
        try {
            switch (key) {
                case "weather" -> {
                    String w = value.toUpperCase(Locale.ROOT);
                    if (!w.equals("CLEAR") && !w.equals("RAIN") && !w.equals("THUNDER")) return Res.no("Météo : CLEAR, RAIN ou THUNDER.");
                    c.settings.weather = w;
                }
                case "time" -> c.settings.time = Math.floorMod(Long.parseLong(value), 24000L);
                case "level_cap" -> c.settings.level_cap = Math.max(1, Math.min(1000, Integer.parseInt(value)));
                default -> {
                    return Res.no("Réglage inconnu.");
                }
            }
        } catch (NumberFormatException e) {
            return Res.no("Valeur invalide.");
        }
        ClaimManager.changed(c);
        return Res.ok("Réglage « " + key + " » : " + value);
    }
}
