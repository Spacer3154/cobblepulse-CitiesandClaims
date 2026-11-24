package fr.cobblepulse.claims.tools;

import fr.cobblepulse.claims.ClaimsConfig;
import fr.cobblepulse.claims.claim.ClaimManager;
import fr.cobblepulse.claims.claim.Relation;
import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.integration.ClanBridge;
import fr.cobblepulse.claims.perm.Perms;
import fr.cobblepulse.claims.Msg;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

/** Inspection ciblee de la Lunette : resume de la zone visee dans le chat. */
public final class InspectorService {
    private InspectorService() {}

    public static void inspectLook(ServerPlayer p) {
        HitResult hr = p.pick(ClaimsConfig.get().claims.inspector_range, 0f, false);
        BlockPos pos = BlockPos.containing(hr.getLocation());
        inspect(p, pos);
    }

    public static void inspect(ServerPlayer p, BlockPos pos) {
        if (!Perms.has(p, Perms.USE)) {
            Msg.err(p, "Vous n'avez pas accès aux outils de claim.");
            return;
        }
        String dim = ClaimManager.dim(p.level());
        Claim c = ClaimManager.at(dim, pos.getX(), pos.getZ());
        for (Component line : summary(p, c, pos)) p.sendSystemMessage(line);
        // reperes pour les clients sans le mod : claims proches en blocs fantomes
        List<Claim> near = new ArrayList<>(ClaimManager.near(dim, pos.getX(), pos.getZ(), 64));
        if (c != null && c.isSub() && !near.contains(c)) near.add(c);
        for (Claim t : new ArrayList<>(near)) near.addAll(t.children);
        FakeVisuals.showClaims(p, near);
    }

    public static List<Component> summary(ServerPlayer viewer, Claim c, BlockPos pos) {
        List<Component> out = new ArrayList<>();
        String coords = " (" + pos.getX() + ", " + pos.getZ() + ")";
        if (c == null) {
            out.add(Msg.prefix().append(Component.literal("Zone sauvage" + coords + " : aucun claim.").withStyle(ChatFormatting.GREEN)));
            return out;
        }
        Claim top = ClaimManager.top(c);
        Relation rel = Relation.of(viewer.getUUID(), c);
        String tag = top.isAdmin() ? null : ClanBridge.tag(top.owner);
        MutableComponent head = Msg.prefix().append(Component.literal(top.displayName())
                .withStyle(s -> s.withColor(TextColor.fromRgb(rel.rgb)).withBold(true))).append(Component.literal(coords).withStyle(ChatFormatting.DARK_GRAY));
        out.add(head);
        out.add(line("Propriétaire", top.isAdmin() ? "Administration" : top.ownerName));
        out.add(line("Clan", top.isAdmin() ? "—" : tag == null ? "Sans clan" : "[" + tag + "]"));
        out.add(line("Superficie", top.area() + " blocs (" + top.width() + " × " + top.length() + ")"));
        String type = top.isAdmin() ? "Claim d'administration" : (top.name != null && !top.name.isBlank() ? "Ville" : "Claim joueur");
        out.add(line("Typologie", type + " · n°" + top.id + (top.children.isEmpty() ? "" : " · " + top.children.size() + " parcelle(s)")));
        if (c.isSub()) out.add(line("Parcelle", c.displayName() + " (" + c.width() + " × " + c.length() + ")"));
        return out;
    }

    private static Component line(String k, String v) {
        return Component.literal("  " + k + " : ").withStyle(ChatFormatting.GRAY).append(Component.literal(v).withStyle(ChatFormatting.WHITE));
    }
}
