package fr.cobblepulse.claims.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.cobblepulse.claims.CobblePulseClaims;
import fr.cobblepulse.claims.client.map.DiscoveredShape;
import fr.cobblepulse.claims.client.map.Discovery;
import fr.cobblepulse.claims.client.map.JourneyMapClaimsPlugin;
import fr.cobblepulse.claims.client.map.MapBridge;
import fr.cobblepulse.claims.client.map.MapStyle;
import fr.cobblepulse.claims.client.map.XaeroMinimapSupport;
import fr.cobblepulse.claims.client.map.XaeroWorldMapSupport;
import fr.cobblepulse.claims.client.render.ClaimWorldRenderer;
import fr.cobblepulse.claims.client.screen.AdminClaimsHubScreen;
import fr.cobblepulse.claims.client.screen.AdminPermissionsConfigScreen;
import fr.cobblepulse.claims.client.screen.ClaimInfoScreen;
import fr.cobblepulse.claims.client.screen.ClaimManagerScreen;
import fr.cobblepulse.claims.client.screen.ClientOptionsScreen;
import fr.cobblepulse.claims.client.screen.DashboardScreen;
import fr.cobblepulse.claims.net.Payloads;
import fr.cobblepulse.claims.tools.ModItems;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Test de bout en bout (CI, -Dcobblepulse_claims.livetest=true) : vrai client connecte au serveur de test, avec
 * JourneyMap, Xaero's Minimap et Xaero's World Map. Balise (coin A, creation, parcelle), titre d'entree, touche
 * contextuelle et tous les ecrans, matrice et sous-panneau des commandes, membres, rendu 3D, Lunette, tableau de bord
 * (noeuds LuckPerms numeriques), administration, WorldBorder (rendu, teleportation annulee), cartes, fiche d'un tiers.
 */
final class LiveTest {
    private static final String TAG = "[CobblePulse-Claims][livetest] ";
    private record Step(int delay, Consumer<Minecraft> run) {}

    private static final List<Step> STEPS = new ArrayList<>();
    private static int ticks, start = -1, pass, fail, index, nextAt;
    private static final int X = 2000, Y = 150, Z = 2000;
    private static int claimId, subId, waited;
    private static Vec3 before;
    private static int reach;

    private LiveTest() {}

    static void install() {
        ClientTickEvents.END_CLIENT_TICK.register(LiveTest::tick);
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) pass++;
        else fail++;
        CobblePulseClaims.LOG.info(TAG + "{} {}{}", ok ? "PASS" : "FAIL", name, detail == null || detail.isEmpty() ? "" : " - " + detail);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> CobblePulseClaims.LOG.info(TAG + "capture {}", name));
    }

    private static void cmd(Minecraft mc, String c) {
        mc.player.connection.sendCommand(c);
    }

    private static void select(Minecraft mc, Item item) {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (item == null ? inv.getItem(i).isEmpty() : inv.getItem(i).is(item)) {
                inv.selected = i;
                return;
            }
        }
        if (item != null) {
            for (int i = 9; i < 36; i++) {
                if (inv.getItem(i).is(item)) {
                    mc.gameMode.handlePickItem(i);
                    return;
                }
            }
        }
    }

    /** Se place a portee du bloc vise (portee d'interaction ~5 blocs). */
    private static void near(Minecraft mc, int x, int z) {
        cmd(mc, "tp @s " + x + ".5 " + (Y + 1) + " " + (z - 2) + ".5 0 60");
    }

    private static void useOn(Minecraft mc, int x, int z) {
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(new Vec3(x + 0.5, Y + 1.0, z + 0.5), Direction.UP, new BlockPos(x, Y, z), false));
    }

    private static void step(Consumer<Minecraft> r) {
        STEPS.add(new Step(40, r));
    }

    private static void step(int delay, Consumer<Minecraft> r) {
        STEPS.add(new Step(delay, r));
    }

    /** Territoire factice cote client (decouverte de la carte). */
    private static void fake(Minecraft mc, int id, String name, int x1, int z1, int x2, int z2) {
        ClientState.CClaim c = new ClientState.CClaim();
        c.id = id;
        c.name = name;
        c.named = true;
        c.owner = "Explorateur";
        c.type = "PLAYER";
        c.rel = "OTHER";
        c.dim = ClientState.dim;
        c.minX = x1;
        c.minZ = z1;
        c.maxX = x2;
        c.maxZ = z2;
        c.area = (long) (x2 - x1 + 1) * (z2 - z1 + 1);
        ClientState.CLAIMS.put(id, c);
    }

    private static ClientState.CClaim own() {
        for (ClientState.CClaim c : ClientState.CLAIMS.values()) if (!c.isSub() && "OWN".equals(c.rel)) return c;
        return null;
    }

    private static KeyMapping findKey(String... parts) {
        for (KeyMapping k : Minecraft.getInstance().options.keyMappings) {
            String n = k.getName().toLowerCase();
            boolean all = true;
            for (String p : parts) all &= n.contains(p);
            if (all) return k;
        }
        return null;
    }

    static {
        step(mc -> {
            cmd(mc, "gamemode creative");
            cmd(mc, "claimadmin bypass off");
            cmd(mc, "forceload add " + (X - 16) + " " + (Z - 16) + " " + (X + 48) + " " + (Z + 40));
        });
        step(mc -> cmd(mc, "tp @s " + (X + 10) + " " + (Y + 6) + " " + (Z - 10) + " 0 35"));
        // le serveur peut etre occupe a generer les chunks : on attend d'etre arrive
        STEPS.add(new Step(20, mc -> {
            if (mc.player.distanceToSqr(X + 10.5, Y + 6, Z - 9.5) > 9 && waited++ < 30) index--;
        }));
        step(mc -> {
            cmd(mc, "fill " + (X - 10) + " " + Y + " " + (Z - 15) + " " + (X + 40) + " " + Y + " " + (Z + 30) + " minecraft:grass_block");
            cmd(mc, "fill " + (X - 10) + " " + (Y + 1) + " " + (Z - 15) + " " + (X + 40) + " " + (Y + 8) + " " + (Z + 30) + " minecraft:air");
            cmd(mc, "claim outils");
        });
        step(mc -> {
            boolean wand = mc.player.getInventory().contains(st -> st.is(ModItems.CLAIM_WAND));
            boolean insp = mc.player.getInventory().contains(st -> st.is(ModItems.CLAIM_INSPECTOR));
            check("outils_recus", wand && insp, "");
            check("sync_connecte", ClientState.connectedToMod && ClientState.border != null && ClientState.border.enabled, "dim=" + ClientState.dim);
            select(mc, ModItems.CLAIM_WAND);
            near(mc, X, Z);
        });
        step(15, mc -> useOn(mc, X, Z));
        step(mc -> {
            check("coin_a_repere", ClientState.selection != null && ClientState.selection.x == X && ClientState.selection.z == Z, "");
            shot(mc, "live_01_coin_a");
            near(mc, X + 20, Z + 15);
        });
        step(15, mc -> useOn(mc, X + 20, Z + 15));
        step(mc -> {
            ClientState.CClaim c = own();
            check("claim_cree_et_synchronise", c != null && c.area == 336 && c.minX == X && c.maxZ == Z + 15, c == null ? "aucun" : c.area + "");
            if (c != null) claimId = c.id;
            check("rendu_3d_actif", ClaimWorldRenderer.holdingTool(mc.player) && ClaimWorldRenderer.frames > 0, "frames=" + ClaimWorldRenderer.frames);
            shot(mc, "live_02_claim_3d");
            cmd(mc, "tp @s " + (X + 10) + " " + (Y + 1) + " " + (Z - 6) + " 0 10");
        });
        step(20, mc -> cmd(mc, "tp @s " + (X + 10) + " " + (Y + 1) + " " + (Z + 4) + " 0 10"));
        step(24, mc -> shot(mc, "live_03_titre_entree"));
        step(mc -> ClaimsClient.pressContextKey(mc));
        step(mc -> {
            ClaimManagerScreen s = mc.screen instanceof ClaimManagerScreen m ? m : null;
            check("touche_ouvre_gestion", s != null && s.claimId() == claimId && "OWNER".equals(s.data().get("role").getAsString()), String.valueOf(mc.screen));
            shot(mc, "live_04_gestion_general");
            if (s != null) s.testTypeName("Bourg-Palette");
        });
        step(mc -> {
            ClaimManagerScreen s = mc.screen instanceof ClaimManagerScreen m ? m : null;
            ClientState.CClaim c = ClientState.get(claimId);
            check("nom_de_ville_par_ecran", s != null && "Bourg-Palette".equals(s.data().getAsJsonObject("claim").get("name").getAsString())
                    && c != null && "Bourg-Palette".equals(c.name), s == null ? "" : String.valueOf(s.lastResult()));
            shot(mc, "live_05_gestion_nommee");
            if (s != null) s.selectTab("perms");
        });
        step(mc -> {
            shot(mc, "live_06_permissions");
            if (mc.screen instanceof ClaimManagerScreen s) s.testSetSearch("coffre");
        });
        step(20, mc -> {
            shot(mc, "live_07_recherche");
            JsonObject a = new JsonObject();
            a.addProperty("id", claimId);
            a.addProperty("flag", "block.break");
            a.addProperty("column", "visitor");
            a.addProperty("state", "ALLOW");
            ClaimsClient.action("set_flag", a);
        });
        step(mc -> {
            ClaimManagerScreen s = mc.screen instanceof ClaimManagerScreen m ? m : null;
            String v = null;
            if (s != null) for (JsonElement e : s.data().getAsJsonArray("flags")) {
                JsonObject f = e.getAsJsonObject();
                if (f.get("id").getAsString().equals("block.break")) v = f.get("v").getAsString();
            }
            check("matrice_flag_modifie", "ALLOW".equals(v), String.valueOf(v));
            if (s != null) {
                s.testSetSearch("");
                s.testOpenCommands();
            }
        });
        step(mc -> {
            check("sous_panneau_commandes", mc.screen instanceof ClaimManagerScreen s && s.commandsOpen(), "");
            shot(mc, "live_08_commandes");
            JsonObject a = new JsonObject();
            a.addProperty("id", claimId);
            a.addProperty("mode", "BLACKLIST");
            JsonArray l = new JsonArray();
            l.add("sethome");
            l.add("tpa");
            a.add("list", l);
            ClaimsClient.action("set_commands", a);
        });
        step(mc -> {
            ClaimManagerScreen s = mc.screen instanceof ClaimManagerScreen m ? m : null;
            check("commandes_liste_noire", s != null && "BLACKLIST".equals(s.data().getAsJsonObject("commands").get("mode").getAsString())
                    && s.data().getAsJsonObject("commands").getAsJsonArray("list").size() == 2, "");
            if (s != null) s.selectTab("members");
            JsonObject a = new JsonObject();
            a.addProperty("id", claimId);
            a.addProperty("name", "public");
            a.addProperty("level", "ACCESS");
            ClaimsClient.action("trust", a);
        });
        step(mc -> {
            ClaimManagerScreen s = mc.screen instanceof ClaimManagerScreen m ? m : null;
            boolean pub = false;
            if (s != null) for (JsonElement e : s.data().getAsJsonArray("members")) pub |= e.getAsJsonObject().get("uuid").getAsString().equals("*");
            check("membres_trust_public", pub, "");
            shot(mc, "live_09_membres");
            mc.setScreen(null);
            select(mc, ModItems.CLAIM_WAND);
            near(mc, X + 2, Z + 2);
        });
        step(15, mc -> useOn(mc, X + 2, Z + 2));
        step(mc -> near(mc, X + 6, Z + 6));
        step(15, mc -> useOn(mc, X + 6, Z + 6));
        step(15, mc -> cmd(mc, "tp @s " + (X + 10) + " " + (Y + 1) + " " + (Z + 10) + " 0 10"));
        step(mc -> {
            ClientState.CClaim sub = null;
            for (ClientState.CClaim c : ClientState.CLAIMS.values()) if (c.parent == claimId) sub = c;
            check("parcelle_creee", sub != null && sub.minX == X + 2 && sub.maxZ == Z + 6, "");
            if (sub != null) subId = sub.id;
            ClaimsClient.pressContextKey(mc);
        });
        step(mc -> {
            if (mc.screen instanceof ClaimManagerScreen s) s.selectTab("subs");
        });
        step(mc -> {
            check("onglet_parcelles", mc.screen instanceof ClaimManagerScreen s && s.data().getAsJsonArray("subs").size() == 1, "");
            shot(mc, "live_10_parcelles");
            ClientState.forced3d = claimId;
            mc.setScreen(null);
            select(mc, null);
            cmd(mc, "tp @s " + (X + 10) + " " + (Y + 5) + " " + (Z - 8) + " 0 30");
        });
        step(mc -> {
            check("rendu_3d_force_sans_outil", !ClaimWorldRenderer.holdingTool(mc.player) && ClientState.forced3d == claimId, "");
            shot(mc, "live_11_rendu_3d_bouton");
            ClientState.forced3d = 0;
            select(mc, ModItems.CLAIM_INSPECTOR);
        });
        step(mc -> near(mc, X + 3, Z + 3));
        step(15, mc -> useOn(mc, X + 3, Z + 3));
        step(mc -> shot(mc, "live_12_lunette"));
        step(mc -> {
            select(mc, null);
            cmd(mc, "tp @s " + (X - 6) + " " + (Y + 1) + " " + (Z + 25) + " 90 10");
        });
        step(mc -> ClaimsClient.pressContextKey(mc));
        step(mc -> {
            DashboardScreen d = mc.screen instanceof DashboardScreen s ? s : null;
            check("tableau_de_bord_nature", d != null && d.data().getAsJsonArray("claims").size() == 1, String.valueOf(mc.screen));
            check("luckperms_plafond_12000", d != null && d.data().get("cap").getAsInt() == 12000, d == null ? "" : d.data().get("cap").getAsString());
            check("luckperms_palier_300", d != null && d.data().get("perInterval").getAsInt() == 300, d == null ? "" : d.data().get("perInterval").getAsString());
            check("compte_a_rebours", d != null && d.remaining() > 0 && d.remaining() <= 30 * 60_000L, d == null ? "" : "" + d.remaining());
            shot(mc, "live_13_tableau_de_bord");
            mc.setScreen(new ClientOptionsScreen(mc.screen));
        });
        step(mc -> {
            shot(mc, "live_14_options_client");
            mc.setScreen(null);
            ClientPlayNetworking.send(new Payloads.ContextKeyPayload(true));
        });
        step(mc -> {
            check("maj_touche_admin", mc.screen instanceof AdminClaimsHubScreen, String.valueOf(mc.screen));
            shot(mc, "live_15_admin_territoires");
            if (mc.screen instanceof AdminClaimsHubScreen h) h.selectTab("flags");
        });
        step(mc -> {
            check("admin_permissions_globales", mc.screen instanceof AdminPermissionsConfigScreen a && "flags".equals(a.currentTab()), String.valueOf(mc.screen));
            shot(mc, "live_16_admin_permissions");
            if (mc.screen instanceof AdminPermissionsConfigScreen a) a.selectTab("economy");
        });
        step(mc -> {
            shot(mc, "live_17_admin_economie");
            if (mc.screen instanceof AdminPermissionsConfigScreen a) a.selectTab("borders");
        });
        step(mc -> {
            shot(mc, "live_18_admin_bordures");
            mc.setScreen(null);
            cmd(mc, "claimadmin bordure minecraft:overworld cercle 2880");
        });
        step(mc -> {
            check("bordure_synchronisee", ClientState.border != null && ClientState.border.radius == 2880, ClientState.border == null ? "" : "" + ClientState.border.radius);
            cmd(mc, "tp @s " + (X + 30) + " " + (Y + 1) + " " + (Z + 25) + " -45 5");
        });
        step(mc -> {
            shot(mc, "live_19_worldborder_3d");
            before = mc.player.position();
            cmd(mc, "tp @s 2100 151 2100");
        });
        step(mc -> {
            check("teleport_hors_bordure_annulee", mc.player.position().distanceTo(before) < 1.0, "pos=" + mc.player.position());
            cmd(mc, "claimadmin bordure minecraft:overworld cercle 10000");
            cmd(mc, "tp @s " + (X + 10) + " " + (Y + 30) + " " + (Z + 8) + " 0 90");
        });
        // decouverte de la carte : un territoire n'est surligne que sur les chunks deja explores
        step(mc -> {
            int pcx = mc.player.chunkPosition().x, pcz = mc.player.chunkPosition().z;
            check("decouverte_chunk_joueur", Discovery.isDiscovered(pcx, pcz), "chunks decouverts=" + Discovery.count());
            check("decouverte_regle_serveur", Discovery.required, "config sync.map_discovery_required");
            java.util.Random rnd = new java.util.Random(7);
            int bad = 0;
            for (int t = 0; t < 300; t++) {
                int x1 = rnd.nextInt(200) - 100, z1 = rnd.nextInt(200) - 100, w = 1 + rnd.nextInt(120), h = 1 + rnd.nextInt(120);
                long seed = rnd.nextLong();
                double pr = rnd.nextDouble();
                DiscoveredShape.ChunkTest ct = (cx, cz) -> new java.util.Random(seed * 31 + cx * 7919L + cz * 104729L).nextDouble() < pr;
                double area = 0;
                for (DiscoveredShape.Piece pc : DiscoveredShape.visible(x1, z1, x1 + w - 1, z1 + h - 1, ct)) area += pc.area();
                if (Math.abs(area - DiscoveredShape.bruteArea(x1, z1, x1 + w - 1, z1 + h - 1, ct)) > 1e-6) bad++;
            }
            List<DiscoveredShape.Piece> ring = DiscoveredShape.visible(0, 0, 47, 47, (cx, cz) -> !(cx == 1 && cz == 1));
            check("decouverte_formes_carte", bad == 0 && ring.size() == 1 && ring.get(0).holes().size() == 1,
                    "erreurs=" + bad + " anneau=" + ring.size() + "/" + (ring.isEmpty() ? -1 : ring.get(0).holes().size()));
            reach = (mc.options.getEffectiveRenderDistance() + 3) * 16;
            fake(mc, -4241, "Terres lointaines", X + 40, Z - 40, X + reach + 400, Z + 60);
            fake(mc, -4242, "Terres inexplorées", X + 6000, Z + 6000, X + 6100, Z + 6100);
            ClientState.version++;
        });
        step(40, mc -> {
            int farX = X + reach + 300;
            check("decouverte_masque_inexplore", !Discovery.isDiscovered(farX >> 4, Z >> 4) && !MapStyle.chunkHighlit(farX >> 4, Z >> 4)
                    && MapStyle.claimAt(ClientState.dim, farX, Z) == null, "x=" + farX);
            int[] px = new int[256];
            check("decouverte_visible_explore", MapStyle.chunkHighlit((X + 48) >> 4, Z >> 4)
                    && MapStyle.chunkPixels((X + 48) >> 4, Z >> 4, px, (rgb, a) -> rgb << 8 | a)
                    && MapStyle.claimAt(ClientState.dim, X + 48, Z) != null, "");
            check("decouverte_journeymap", !MapBridge.JOURNEYMAP || (JourneyMapClaimsPlugin.hidden >= 1 && JourneyMapClaimsPlugin.partial >= 1),
                    "masques=" + JourneyMapClaimsPlugin.hidden + " partiels=" + JourneyMapClaimsPlugin.partial + " overlays=" + JourneyMapClaimsPlugin.shown);
            Discovery.mark((X + 6050) >> 4, (Z + 6050) >> 4);
            check("decouverte_revele", MapStyle.claimAt(ClientState.dim, X + 6050, Z + 6050) != null
                    && MapStyle.claimAt(ClientState.dim, X + 6090, Z + 6090) == null, "");
            int n = Discovery.count();
            Discovery.reloadForTest();
            check("decouverte_sauvegarde", n > 0 && Discovery.count() == n && Discovery.isDiscovered((X + 6050) >> 4, (Z + 6050) >> 4), n + " -> " + Discovery.count());
            ClientState.version++;
        });
        // cartes
        step(mc -> {
            check("journeymap_overlays", !MapBridge.JOURNEYMAP || (JourneyMapClaimsPlugin.ready() && JourneyMapClaimsPlugin.shown >= 3),
                    "pret=" + JourneyMapClaimsPlugin.ready() + " overlays=" + JourneyMapClaimsPlugin.shown);
            check("xaero_surligneur", !MapBridge.XAERO_WORLDMAP || XaeroWorldMapSupport.registered > 0, "wm=" + XaeroWorldMapSupport.registered + " mm=" + XaeroMinimapSupport.registered);
            StringBuilder keys = new StringBuilder();
            for (KeyMapping k : mc.options.keyMappings) {
                String n = k.getName().toLowerCase();
                if (n.contains("xaero") || n.contains("journeymap")) keys.append(k.getName()).append(' ');
            }
            CobblePulseClaims.LOG.info(TAG + "touches cartes : {}", keys);
            shot(mc, "live_20_minimaps");
            KeyMapping wm = findKey("xaero", "open", "map");
            if (wm == null) wm = findKey("xaero_world_map");
            if (wm != null) KeyMapping.click(KeyBindingHelper.getBoundKeyOf(wm));
            else check("xaero_touche_carte", false, keys.toString());
        });
        step(80, mc -> {
            CobblePulseClaims.LOG.info(TAG + "écran carte Xaero : {}", mc.screen);
            shot(mc, "live_21_xaero_worldmap");
        });
        step(mc -> {
            mc.setScreen(null);
            KeyMapping jm = findKey("key.journeymap.map_toggle_alt");
            if (jm == null) jm = findKey("key.journeymap.map_toggle");
            if (jm != null) KeyMapping.click(KeyBindingHelper.getBoundKeyOf(jm));
        });
        step(60, mc -> {
            // premiere ouverture : JourneyMap affiche d'abord son ecran « A propos »
            if (mc.screen != null && !mc.screen.getClass().getName().toLowerCase().contains("fullscreen")) {
                CobblePulseClaims.LOG.info(TAG + "écran JourneyMap intermédiaire : {}", mc.screen);
                mc.setScreen(null);
                KeyMapping jm = findKey("key.journeymap.map_toggle_alt");
                if (jm != null) KeyMapping.click(KeyBindingHelper.getBoundKeyOf(jm));
            }
        });
        step(80, mc -> {
            CobblePulseClaims.LOG.info(TAG + "écran JourneyMap : {}", mc.screen);
            check("journeymap_plein_ecran", mc.screen != null, String.valueOf(mc.screen));
            shot(mc, "live_22_journeymap");
        });
        step(mc -> {
            ClientState.CLAIMS.remove(-4241);
            ClientState.CLAIMS.remove(-4242);
            ClientState.version++;
        });
        // fiche d'un tiers : le claim devient admin et le joueur n'est plus operateur
        step(mc -> {
            mc.setScreen(null);
            cmd(mc, "tp @s " + (X + 12) + " " + (Y + 1) + " " + (Z + 10) + " 0 10");
        });
        step(mc -> cmd(mc, "claimadmin convertir admin"));
        step(mc -> {
            ClientState.CClaim c = ClientState.get(claimId);
            check("conversion_admin_synchronisee", c != null && c.isAdmin() && "ADMIN".equals(c.rel), c == null ? "" : c.rel);
            cmd(mc, "deop " + mc.player.getGameProfile().getName());
        });
        step(mc -> ClaimsClient.pressContextKey(mc));
        step(mc -> {
            check("fiche_lecture_seule", mc.screen instanceof ClaimInfoScreen, String.valueOf(mc.screen));
            shot(mc, "live_23_fiche_info");
            mc.setScreen(null);
        });
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        ticks++;
        if (start < 0) {
            start = ticks;
            nextAt = ticks + 220;
            CobblePulseClaims.LOG.info(TAG + "connecté, début dans 11 s");
        }
        if (ticks < nextAt) return;
        try {
            if (index < STEPS.size()) {
                Step s = STEPS.get(index++);
                s.run().accept(mc);
                nextAt = ticks + (index < STEPS.size() ? STEPS.get(index).delay() : 40);
            } else if (index == STEPS.size()) {
                index++;
                CobblePulseClaims.LOG.info(TAG + "LIVETEST SUMMARY: {} pass, {} fail", pass, fail);
                mc.stop();
            }
        } catch (Throwable e) {
            check("exception_etape_" + index, false, e.toString());
            CobblePulseClaims.LOG.error(TAG + "erreur", e);
            nextAt = ticks + 40;
        }
    }
}
