package fr.cobblepulse.claims.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import fr.cobblepulse.claims.CobblePulseClaims;
import fr.cobblepulse.claims.client.map.Discovery;
import fr.cobblepulse.claims.client.map.MapBridge;
import fr.cobblepulse.claims.client.render.ClaimWorldRenderer;
import fr.cobblepulse.claims.client.screen.AdminClaimsHubScreen;
import fr.cobblepulse.claims.client.screen.AdminPermissionsConfigScreen;
import fr.cobblepulse.claims.client.screen.BaseScreen;
import fr.cobblepulse.claims.client.screen.ClaimInfoScreen;
import fr.cobblepulse.claims.client.screen.ClaimManagerScreen;
import fr.cobblepulse.claims.client.screen.ClientOptionsScreen;
import fr.cobblepulse.claims.client.screen.DashboardScreen;
import fr.cobblepulse.claims.net.Payloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * Cote client : touche contextuelle (K par defaut, reassignable), ecrans natifs, rendu 3D des territoires et de la
 * WorldBorder, connecteurs JourneyMap / Xaero's Minimap / Xaero's World Map.
 */
public final class ClaimsClient implements ClientModInitializer {
    public static KeyMapping CONTEXT_KEY;
    private static int lastMapVersion = -1, lastDiscovery;
    private static long lastRefreshTick;

    @Override
    public void onInitializeClient() {
        ClientConfig.load();
        CONTEXT_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cobblepulse_claims.context", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_K, "key.categories.cobblepulse_claims"));

        ClientPlayNetworking.registerGlobalReceiver(Payloads.ClaimsSyncPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> {
            ClientState.applySync(json(p.json()));
        }));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.BorderSyncPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> ClientState.applyBorder(json(p.json()))));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.VisualPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> ClientState.applyVisual(json(p.json()))));
        ClientPlayNetworking.registerGlobalReceiver(Payloads.OpenScreenPayload.TYPE, (p, ctx) -> ctx.client().execute(() -> openScreen(p.screen(), json(p.json()))));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
            Discovery.disconnect();
            ClientState.reset();
            MapBridge.refresh();
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (CONTEXT_KEY.consumeClick()) pressContextKey(mc);
            Discovery.tick(mc);
            if (ClientState.version != lastMapVersion) {
                lastMapVersion = ClientState.version;
                lastDiscovery = Discovery.mapVersion;
                lastRefreshTick = mc.level == null ? 0 : mc.level.getGameTime();
                MapBridge.refresh();
            } else if (Discovery.mapVersion != lastDiscovery && mc.level != null && mc.level.getGameTime() - lastRefreshTick >= 40) {
                // nouveaux chunks decouverts sur un territoire : redessin limite a une fois toutes les 2 s
                lastDiscovery = Discovery.mapVersion;
                lastRefreshTick = mc.level.getGameTime();
                MapBridge.refresh();
            }
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ClaimWorldRenderer::render);
        ClientCommandRegistrationCallback.EVENT.register((d, reg) -> d.register(literal("claimsoptions").executes(c -> {
            Minecraft mc = Minecraft.getInstance();
            mc.tell(() -> mc.setScreen(new ClientOptionsScreen(null)));
            return 1;
        })));
        MapBridge.init();
        if (Boolean.getBoolean("cobblepulse_claims.livetest")) LiveTest.install();
        CobblePulseClaims.LOG.info("[CobblePulse-Claims] Client prêt (touche contextuelle : K)");
    }

    private static JsonObject json(String s) {
        try {
            return JsonParser.parseString(s).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    public static void pressContextKey(Minecraft mc) {
        if (mc.player == null) return;
        if (!ClientPlayNetworking.canSend(Payloads.ContextKeyPayload.TYPE)) {
            mc.player.displayClientMessage(Component.literal("Ce serveur n'a pas CobblePulse Cities & Claims.").withStyle(ChatFormatting.RED), true);
            return;
        }
        ClientPlayNetworking.send(new Payloads.ContextKeyPayload(Screen.hasShiftDown()));
    }

    public static void action(String action, JsonObject args) {
        if (!ClientPlayNetworking.canSend(Payloads.ClaimActionPayload.TYPE)) return;
        ClientPlayNetworking.send(new Payloads.ClaimActionPayload(action, args == null ? "{}" : args.toString()));
    }

    static void openScreen(String screen, JsonObject o) {
        Minecraft mc = Minecraft.getInstance();
        try {
            switch (screen) {
                case "manager" -> {
                    int id = o.getAsJsonObject("claim").get("id").getAsInt();
                    if (mc.screen instanceof ClaimManagerScreen s && s.claimId() == id) s.update(o);
                    else mc.setScreen(new ClaimManagerScreen(o));
                }
                case "info" -> mc.setScreen(new ClaimInfoScreen(o));
                case "dashboard" -> {
                    if (mc.screen instanceof DashboardScreen s) s.update(o);
                    else mc.setScreen(new DashboardScreen(o));
                }
                case "admin" -> {
                    String tab = o.has("tab") ? o.get("tab").getAsString() : "claims";
                    if (tab.equals("claims")) {
                        if (mc.screen instanceof AdminClaimsHubScreen s) s.update(o);
                        else mc.setScreen(new AdminClaimsHubScreen(o));
                    } else {
                        if (mc.screen instanceof AdminPermissionsConfigScreen s) s.update(o);
                        else mc.setScreen(new AdminPermissionsConfigScreen(o));
                    }
                }
                case "result" -> {
                    boolean ok = o.get("ok").getAsBoolean();
                    String msg = o.get("message").getAsString();
                    if (mc.screen instanceof BaseScreen b) b.showResult(ok, msg);
                    else if (mc.player != null)
                        mc.player.displayClientMessage(Component.literal(msg).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED), false);
                }
                default -> {
                }
            }
        } catch (Throwable t) {
            CobblePulseClaims.LOG.error("[CobblePulse-Claims] Écran {} illisible", screen, t);
        }
    }
}
