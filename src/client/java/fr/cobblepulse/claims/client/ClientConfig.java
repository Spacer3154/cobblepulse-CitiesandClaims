package fr.cobblepulse.claims.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Options client (config/cobblepulse/claims-client.json), modifiables depuis l'ecran d'options du mod. */
public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ClientConfig instance = new ClientConfig();

    /** Territoires sur JourneyMap / Xaero's Minimap / Xaero's World Map. */
    public boolean map_overlays = true;
    /** Trace de la WorldBorder sur les cartes. */
    public boolean map_worldborder = true;
    /** Noms des villes sur les cartes. */
    public boolean map_labels = true;
    /** Bordures 3D toujours visibles (sinon seulement avec la Balise ou la Lunette en main). */
    public boolean always_render_3d = false;
    /** WorldBorder translucide en 3D a l'approche. */
    public boolean render_worldborder = true;
    /** Distance de rendu 3D des territoires (blocs). */
    public int render_radius = 128;

    public static ClientConfig get() {
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("cobblepulse").resolve("claims-client.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                ClientConfig c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), ClientConfig.class);
                if (c != null) instance = c;
            }
        } catch (Exception ignored) {
        }
        instance.render_radius = Math.max(32, Math.min(512, instance.render_radius));
        save();
    }

    public static void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            Files.writeString(f, GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }
}
