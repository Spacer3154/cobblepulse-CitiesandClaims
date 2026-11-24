package fr.cobblepulse.claims.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.cobblepulse.claims.client.ClientConfig;
import fr.cobblepulse.claims.client.ClientState;
import fr.cobblepulse.claims.tools.ModItems;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Rendu 3D passif : bordures des territoires proches (Balise ou Lunette en main, ou option permanente), mise en evidence
 * apres creation, repere lumineux du coin A, apercu du rectangle en cours, WorldBorder translucide a l'approche.
 */
public final class ClaimWorldRenderer {
    public static volatile long frames;

    private ClaimWorldRenderer() {}

    public static boolean holdingTool(LocalPlayer p) {
        return p.getMainHandItem().is(ModItems.CLAIM_WAND) || p.getOffhandItem().is(ModItems.CLAIM_WAND)
                || p.getMainHandItem().is(ModItems.CLAIM_INSPECTOR) || p.getOffhandItem().is(ModItems.CLAIM_INSPECTOR);
    }

    public static void render(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        ClientConfig cfg = ClientConfig.get();
        boolean tool = holdingTool(p);
        boolean all = tool || cfg.always_render_3d;
        long now = System.currentTimeMillis();
        ClientState.HIGHLIGHTS.values().removeIf(until -> until < now);
        boolean border = cfg.render_worldborder && ClientState.border != null && ClientState.border.enabled
                && ClientState.border.inset(p.getX(), p.getZ()) < 40;
        if (!all && ClientState.forced3d == 0 && ClientState.HIGHLIGHTS.isEmpty() && ClientState.selection == null && !border) return;

        PoseStack pose = ctx.matrixStack() != null ? ctx.matrixStack() : new PoseStack();
        MultiBufferSource consumers = ctx.consumers() != null ? ctx.consumers() : mc.renderBuffers().bufferSource();
        Vec3 cam = ctx.camera().getPosition();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose last = pose.last();
        VertexConsumer lines = consumers.getBuffer(RenderType.lines());
        double py = Math.floor(p.getY());
        int radius = cfg.render_radius;
        for (ClientState.CClaim c : ClientState.CLAIMS.values()) {
            boolean show = (all && near(c, p.getX(), p.getZ(), radius)) || ClientState.forced3d == c.id || ClientState.HIGHLIGHTS.containsKey(c.id)
                    || (ClientState.forced3d != 0 && c.parent == ClientState.forced3d);
            if (!show) continue;
            int rgb = c.isSub() ? 0xFFFFFF : c.rgb();
            int a = c.isSub() ? 0xB0 : 0xFF;
            if (ClientState.HIGHLIGHTS.containsKey(c.id)) a = (int) (160 + 95 * Math.abs(Math.sin(now / 250.0)));
            claimBox(lines, last, c, py, rgb, a, p);
        }
        // selection de la balise
        ClientState.Selection s = ClientState.selection;
        if (s != null) {
            int gold = 0xFFD24A;
            float ax = s.kind.equals("resize") ? s.fx : s.x, az = s.kind.equals("resize") ? s.fz : s.z;
            box(lines, last, s.x, s.y, s.z, s.x + 1, s.y + 1, s.z + 1, gold, 0xFF);
            line(lines, last, s.x + 0.5f, s.y + 1f, s.z + 0.5f, s.x + 0.5f, s.y + 48f, s.z + 0.5f, gold, 0xFF);
            HitResult hr = mc.hitResult;
            if (hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK) {
                BlockPos t = bh.getBlockPos();
                float x1 = Math.min(ax, t.getX()), x2 = Math.max(ax, t.getX()) + 1, z1 = Math.min(az, t.getZ()), z2 = Math.max(az, t.getZ()) + 1;
                float y = t.getY() + 1.02f;
                rect(lines, last, x1, z1, x2, z2, y, gold, 0xE0);
                rect(lines, last, x1, z1, x2, z2, y + 2, gold, 0x80);
                box(lines, last, t.getX(), t.getY(), t.getZ(), t.getX() + 1, t.getY() + 1, t.getZ() + 1, 0xFFFFFF, 0xC0);
            }
        }
        if (consumers instanceof MultiBufferSource.BufferSource bs) bs.endBatch(RenderType.lines());
        if (border) {
            VertexConsumer quads = consumers.getBuffer(RenderType.debugQuads());
            borderWall(quads, last, p.getX(), p.getZ(), py);
            if (consumers instanceof MultiBufferSource.BufferSource bs) bs.endBatch(RenderType.debugQuads());
        }
        pose.popPose();
        frames++;
    }

    private static boolean near(ClientState.CClaim c, double x, double z, int r) {
        double dx = Math.max(0, Math.max(c.minX - x, x - (c.maxX + 1)));
        double dz = Math.max(0, Math.max(c.minZ - z, z - (c.maxZ + 1)));
        return dx * dx + dz * dz <= (double) r * r;
    }

    private static void claimBox(VertexConsumer vc, PoseStack.Pose pose, ClientState.CClaim c, double py, int rgb, int a, LocalPlayer p) {
        float x1 = c.minX, z1 = c.minZ, x2 = c.maxX + 1, z2 = c.maxZ + 1;
        float low = (float) py - 1.98f, mid = (float) py + 0.02f, high = (float) py + 3.02f;
        rect(vc, pose, x1, z1, x2, z2, mid, rgb, a);
        rect(vc, pose, x1, z1, x2, z2, high, rgb, a / 2);
        rect(vc, pose, x1, z1, x2, z2, low, rgb, a / 3);
        float top = (float) py + 14, bottom = (float) py - 8;
        for (float[] corner : new float[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) {
            line(vc, pose, corner[0], bottom, corner[1], corner[0], top, corner[1], rgb, a);
        }
        // poteaux le long des bords proches du joueur
        int step = 8;
        double px = p.getX(), pz = p.getZ();
        for (float x = x1 + step; x < x2; x += step) {
            if (Math.abs(x - px) > 48) continue;
            if (Math.abs(z1 - pz) < 48) line(vc, pose, x, low, z1, x, high, z1, rgb, a / 2);
            if (Math.abs(z2 - pz) < 48) line(vc, pose, x, low, z2, x, high, z2, rgb, a / 2);
        }
        for (float z = z1 + step; z < z2; z += step) {
            if (Math.abs(z - pz) > 48) continue;
            if (Math.abs(x1 - px) < 48) line(vc, pose, x1, low, z, x1, high, z, rgb, a / 2);
            if (Math.abs(x2 - px) < 48) line(vc, pose, x2, low, z, x2, high, z, rgb, a / 2);
        }
    }

    static void rect(VertexConsumer vc, PoseStack.Pose pose, float x1, float z1, float x2, float z2, float y, int rgb, int a) {
        line(vc, pose, x1, y, z1, x2, y, z1, rgb, a);
        line(vc, pose, x2, y, z1, x2, y, z2, rgb, a);
        line(vc, pose, x2, y, z2, x1, y, z2, rgb, a);
        line(vc, pose, x1, y, z2, x1, y, z1, rgb, a);
    }

    static void box(VertexConsumer vc, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2, int rgb, int a) {
        rect(vc, pose, x1, z1, x2, z2, y1, rgb, a);
        rect(vc, pose, x1, z1, x2, z2, y2, rgb, a);
        line(vc, pose, x1, y1, z1, x1, y2, z1, rgb, a);
        line(vc, pose, x2, y1, z1, x2, y2, z1, rgb, a);
        line(vc, pose, x1, y1, z2, x1, y2, z2, rgb, a);
        line(vc, pose, x2, y1, z2, x2, y2, z2, rgb, a);
    }

    static void line(VertexConsumer vc, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2, int rgb, int a) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-4f) return;
        dx /= len;
        dy /= len;
        dz /= len;
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, dx, dy, dz);
        vc.addVertex(pose, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, dx, dy, dz);
    }

    /** Mur translucide de la WorldBorder sur ±40 blocs autour du point le plus proche. */
    private static void borderWall(VertexConsumer vc, PoseStack.Pose pose, double px, double pz, double py) {
        ClientState.CBorder b = ClientState.border;
        float y1 = (float) py - 16, y2 = (float) py + 24;
        int alpha = (int) Math.max(30, Math.min(110, 110 - b.inset(px, pz) * 2));
        int col = alpha << 24 | 0xFF4040;
        if (b.circle) {
            double ang = Math.atan2(pz - b.cz, px - b.cx);
            double span = Math.min(Math.PI, 48 / Math.max(1, b.radius));
            int n = 48;
            for (int i = 0; i < n; i++) {
                double a1 = ang - span + 2 * span * i / n, a2 = ang - span + 2 * span * (i + 1) / n;
                float x1 = (float) (b.cx + Math.cos(a1) * b.radius), z1 = (float) (b.cz + Math.sin(a1) * b.radius);
                float x2 = (float) (b.cx + Math.cos(a2) * b.radius), z2 = (float) (b.cz + Math.sin(a2) * b.radius);
                quad(vc, pose, x1, z1, x2, z2, y1, y2, col);
            }
        } else {
            float minX = (float) (b.cx - b.sx / 2), maxX = (float) (b.cx + b.sx / 2), minZ = (float) (b.cz - b.sz / 2), maxZ = (float) (b.cz + b.sz / 2);
            float ax = (float) Math.max(minX, px - 48), bx = (float) Math.min(maxX, px + 48);
            float az = (float) Math.max(minZ, pz - 48), bz = (float) Math.min(maxZ, pz + 48);
            if (Math.abs(pz - minZ) < 48 && ax < bx) quad(vc, pose, ax, minZ, bx, minZ, y1, y2, col);
            if (Math.abs(pz - maxZ) < 48 && ax < bx) quad(vc, pose, ax, maxZ, bx, maxZ, y1, y2, col);
            if (Math.abs(px - minX) < 48 && az < bz) quad(vc, pose, minX, az, minX, bz, y1, y2, col);
            if (Math.abs(px - maxX) < 48 && az < bz) quad(vc, pose, maxX, az, maxX, bz, y1, y2, col);
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, float x1, float z1, float x2, float z2, float y1, float y2, int argb) {
        vc.addVertex(pose, x1, y1, z1).setColor(argb);
        vc.addVertex(pose, x2, y1, z2).setColor(argb);
        vc.addVertex(pose, x2, y2, z2).setColor(argb);
        vc.addVertex(pose, x1, y2, z1).setColor(argb);
    }
}
