package fr.cobblepulse.claims.net;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.cobblepulse.claims.CobblePulseClaims;
import fr.cobblepulse.claims.Msg;
import fr.cobblepulse.claims.border.WorldBorders;
import fr.cobblepulse.claims.claim.Access;
import fr.cobblepulse.claims.claim.ClaimManager;
import fr.cobblepulse.claims.data.Claim;
import fr.cobblepulse.claims.economy.BlockEconomy;
import fr.cobblepulse.claims.perm.Perms;
import fr.cobblepulse.claims.tools.WandService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reseau serveur : synchronisation des territoires et des WorldBorders vers les clients equipes du mod, ouverture des
 * ecrans, reception des actions (toutes revalidees cote serveur, voir {@link Actions}).
 */
public final class ClaimsNet {
    public static final Gson GSON = new Gson();
    /** Empreinte de la derniere synchronisation complete envoyee (detection des changements de clan). */
    private static final Map<UUID, Integer> LAST_SYNC_HASH = new HashMap<>();

    private ClaimsNet() {}

    public static void registerTypes() {
        PayloadTypeRegistry.playC2S().register(Payloads.ContextKeyPayload.TYPE, Payloads.ContextKeyPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(Payloads.ClaimActionPayload.TYPE, Payloads.ClaimActionPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(Payloads.OpenScreenPayload.TYPE, Payloads.OpenScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(Payloads.ClaimsSyncPayload.TYPE, Payloads.ClaimsSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(Payloads.BorderSyncPayload.TYPE, Payloads.BorderSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(Payloads.VisualPayload.TYPE, Payloads.VisualPayload.CODEC);
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(Payloads.ContextKeyPayload.TYPE, (payload, ctx) -> {
            ServerPlayer p = ctx.player();
            ctx.server().execute(() -> contextKey(p, payload.shift()));
        });
        ServerPlayNetworking.registerGlobalReceiver(Payloads.ClaimActionPayload.TYPE, (payload, ctx) -> {
            ServerPlayer p = ctx.player();
            ctx.server().execute(() -> {
                JsonObject o;
                try {
                    o = payload.json() == null || payload.json().isBlank() ? new JsonObject() : JsonParser.parseString(payload.json()).getAsJsonObject();
                } catch (Exception e) {
                    o = new JsonObject();
                }
                try {
                    BlockEconomy.markActive(p);
                    Actions.handle(p, payload.action(), o);
                } catch (Throwable t) {
                    CobblePulseClaims.LOG.error("[CobblePulse-Claims] Action {} de {} en erreur", payload.action(), p.getGameProfile().getName(), t);
                    Msg.err(p, "Action impossible (erreur serveur).");
                }
            });
        });
        ClaimManager.CHANGED.add(ClaimsNet::broadcastUpsert);
        ClaimManager.REMOVED.add(ClaimsNet::broadcastRemove);
        WorldBorders.CHANGED.add(dim -> {
            MinecraftServer s = CobblePulseClaims.server();
            if (s == null) return;
            for (ServerPlayer p : s.getPlayerList().getPlayers()) if (ClaimManager.dim(p.level()).equals(dim)) sendBorder(p);
        });
    }

    public static boolean hasClient(ServerPlayer p) {
        try {
            return p.connection != null && ServerPlayNetworking.canSend(p, Payloads.OpenScreenPayload.TYPE);
        } catch (Throwable t) {
            return false;
        }
    }

    // ------------------------------------------------------------------ touche contextuelle

    static void contextKey(ServerPlayer p, boolean shift) {
        BlockEconomy.markActive(p);
        if (!Perms.has(p, Perms.USE)) {
            Msg.err(p, "Vous n'avez pas accès aux territoires.");
            return;
        }
        if (shift) {
            if (Perms.staff(p) || Perms.has(p, Perms.ADMIN_WORLDBORDER) || Perms.has(p, Perms.ADMIN_GIVE_BLOCKS)) openAdmin(p, "claims");
            else Msg.err(p, "Interface d'administration réservée au staff.");
            return;
        }
        Claim c = ClaimManager.at(p.level(), p.blockPosition());
        if (c == null) openDashboard(p);
        else if (Access.canManage(p, c)) openManager(p, c);
        else openInfo(p, c);
    }

    // ------------------------------------------------------------------ ecrans

    public static void open(ServerPlayer p, String screen, JsonObject data) {
        if (!hasClient(p)) return;
        ServerPlayNetworking.send(p, new Payloads.OpenScreenPayload(screen, GSON.toJson(data)));
    }

    public static void openManager(ServerPlayer p, Claim c) {
        if (!hasClient(p)) {
            for (var line : fr.cobblepulse.claims.tools.InspectorService.summary(p, c, p.blockPosition())) p.sendSystemMessage(line);
            Msg.info(p, "Installez le modpack CobblePulse pour l'écran de gestion, ou utilisez /trust, /claim nom.");
            return;
        }
        open(p, "manager", Views.manager(p, c));
    }

    public static void openInfo(ServerPlayer p, Claim c) {
        if (!hasClient(p)) {
            for (var line : fr.cobblepulse.claims.tools.InspectorService.summary(p, c, p.blockPosition())) p.sendSystemMessage(line);
            return;
        }
        open(p, "info", Views.info(p, c));
    }

    public static void openDashboard(ServerPlayer p) {
        if (!hasClient(p)) {
            Msg.info(p, "Solde : " + BlockEconomy.available(p.getUUID()) + " blocs disponibles. /claim blocs pour le détail.");
            return;
        }
        open(p, "dashboard", Views.dashboard(p));
    }

    public static void openAdmin(ServerPlayer p, String tab) {
        if (!hasClient(p)) {
            Msg.info(p, "Interface d'administration : client CobblePulse requis. Commandes : /claimadmin.");
            return;
        }
        open(p, "admin", Views.admin(p, tab));
    }

    /** Resultat d'une action, affiche dans l'ecran ouvert. */
    public static void result(ServerPlayer p, boolean ok, String message) {
        if (!hasClient(p)) {
            if (ok) Msg.ok(p, message);
            else Msg.err(p, message);
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("ok", ok);
        o.addProperty("message", message);
        ServerPlayNetworking.send(p, new Payloads.OpenScreenPayload("result", GSON.toJson(o)));
    }

    // ------------------------------------------------------------------ synchronisation

    public static void sendFullSync(ServerPlayer p) {
        if (!hasClient(p)) return;
        String dim = ClaimManager.dim(p.level());
        JsonObject o = new JsonObject();
        o.addProperty("dim", dim);
        o.addProperty("full", true);
        JsonArray arr = new JsonArray();
        for (Claim c : ClaimManager.all()) if (c.dimension.equals(dim)) arr.add(Views.summary(p, c));
        o.add("upsert", arr);
        o.addProperty("self", p.getUUID().toString());
        o.addProperty("staff", Perms.staff(p));
        o.addProperty("discovery", fr.cobblepulse.claims.ClaimsConfig.get().sync.map_discovery_required);
        String json = GSON.toJson(o);
        LAST_SYNC_HASH.put(p.getUUID(), (dim + arr + fr.cobblepulse.claims.ClaimsConfig.get().sync.map_discovery_required).hashCode());
        ServerPlayNetworking.send(p, new Payloads.ClaimsSyncPayload(json));
    }

    /** Toutes les 30 s : renvoi complet si la vue du joueur a change (clans, confiance publique, noms). */
    public static void refreshIfChanged(ServerPlayer p) {
        if (!hasClient(p)) return;
        String dim = ClaimManager.dim(p.level());
        JsonArray arr = new JsonArray();
        for (Claim c : ClaimManager.all()) if (c.dimension.equals(dim)) arr.add(Views.summary(p, c));
        Integer last = LAST_SYNC_HASH.get(p.getUUID());
        if (last == null || last != (dim + arr + fr.cobblepulse.claims.ClaimsConfig.get().sync.map_discovery_required).hashCode()) sendFullSync(p);
    }

    private static void broadcastUpsert(Claim c) {
        MinecraftServer s = CobblePulseClaims.server();
        if (s == null) return;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (!hasClient(p) || !ClaimManager.dim(p.level()).equals(c.dimension)) continue;
            JsonObject o = new JsonObject();
            o.addProperty("dim", c.dimension);
            JsonArray arr = new JsonArray();
            arr.add(Views.summary(p, c));
            // les parcelles changent de relation avec le claim principal (membres herites)
            if (!c.isSub()) for (Claim sub : c.children) arr.add(Views.summary(p, sub));
            o.add("upsert", arr);
            ServerPlayNetworking.send(p, new Payloads.ClaimsSyncPayload(GSON.toJson(o)));
            LAST_SYNC_HASH.remove(p.getUUID());
        }
    }

    private static void broadcastRemove(Claim c) {
        MinecraftServer s = CobblePulseClaims.server();
        if (s == null) return;
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (!hasClient(p) || !ClaimManager.dim(p.level()).equals(c.dimension)) continue;
            JsonObject o = new JsonObject();
            o.addProperty("dim", c.dimension);
            JsonArray rm = new JsonArray();
            rm.add(c.id);
            o.add("remove", rm);
            ServerPlayNetworking.send(p, new Payloads.ClaimsSyncPayload(GSON.toJson(o)));
            LAST_SYNC_HASH.remove(p.getUUID());
        }
    }

    public static void sendBorder(ServerPlayer p) {
        if (!hasClient(p)) return;
        String dim = ClaimManager.dim(p.level());
        WorldBorders.Border b = WorldBorders.get(dim);
        JsonObject o = new JsonObject();
        o.addProperty("dim", dim);
        o.addProperty("enabled", b != null && b.enabled);
        if (b != null) {
            o.addProperty("cx", b.centerX);
            o.addProperty("cz", b.centerZ);
            o.addProperty("shape", b.circle() ? "CIRCLE" : "RECTANGLE");
            o.addProperty("radius", b.radius);
            o.addProperty("sx", b.sizeX);
            o.addProperty("sz", b.sizeZ);
        }
        ServerPlayNetworking.send(p, new Payloads.BorderSyncPayload(GSON.toJson(o)));
    }

    /** Coin A pose / redimensionnement en cours (s == null : effacer). */
    public static void sendSelection(ServerPlayer p, WandService.Selection s, int resizing) {
        if (!hasClient(p)) return;
        JsonObject o = new JsonObject();
        o.addProperty("kind", s == null ? "clear" : resizing != 0 ? "resize" : "corner");
        if (s != null) {
            o.addProperty("x", s.a.getX());
            o.addProperty("y", s.a.getY());
            o.addProperty("z", s.a.getZ());
            o.addProperty("claim", resizing);
            if (resizing != 0) {
                o.addProperty("fx", s.fixedX);
                o.addProperty("fz", s.fixedZ);
            }
        }
        ServerPlayNetworking.send(p, new Payloads.VisualPayload(GSON.toJson(o)));
    }

    public static void sendHighlight(ServerPlayer p, Claim c) {
        if (!hasClient(p)) return;
        JsonObject o = new JsonObject();
        o.addProperty("kind", "highlight");
        o.addProperty("claim", c.id);
        o.addProperty("seconds", fr.cobblepulse.claims.ClaimsConfig.get().claims.visual_seconds);
        ServerPlayNetworking.send(p, new Payloads.VisualPayload(GSON.toJson(o)));
    }

    public static void forget(UUID id) {
        LAST_SYNC_HASH.remove(id);
    }
}
