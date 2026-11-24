package fr.cobblepulse.claims;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** config/cobblepulse/claims/config.json : economie de blocs, regles des claims, notifications, zones admin. */
public final class ClaimsConfig {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static ClaimsConfig instance = new ClaimsConfig();

    public Economy economy = new Economy();
    public Claims claims = new Claims();
    public Notifications notifications = new Notifications();
    public Commands commands = new Commands();
    public AdminZones admin_zones = new AdminZones();
    public Permissions permissions = new Permissions();
    public Sync sync = new Sync();
    public Storage storage = new Storage();

    public static final class Economy {
        /** Minutes de jeu effectif entre deux paliers. */
        public int interval_minutes = 30;
        /** Blocs ajoutes a chaque palier (surcharge : cobblepulse.claims.limit.blocks_per_interval.<n>). */
        public int blocks_per_interval = 150;
        /** Capital de depart a la premiere connexion. */
        public int initial_blocks = 500;
        /** Plafond du cumul passif (surcharge : cobblepulse.claims.limit.max_blocks.<n>). */
        public int max_accrued_blocks = 5000;
        /** Inactivite au-dela de laquelle le temps ne compte plus (0 = jamais AFK). */
        public int afk_minutes = 5;
    }

    public static final class Claims {
        public int min_width = 5;
        public int min_area = 25;
        /**
         * Nombre maximal de territoires (claims principaux) par joueur. Reglage du fichier uniquement (pas d'ecran).
         * 0 ou negatif = illimite. Les parcelles et les claims admin ne comptent pas.
         */
        public int max_claims_per_player = 10;
        /** Les noeuds LuckPerms cobblepulse.claims.limit.max_claims.<n> (ou .unlimited) remplacent-ils la limite ci-dessus ? */
        public boolean luckperms_max_claims_override = true;
        public int subclaim_min_width = 1;
        public int max_city_name_length = 32;
        public boolean give_tools_on_first_join = true;
        /** Duree d'affichage des reperes (blocs fantomes pour les joueurs sans le mod, rendu 3D sinon). */
        public int visual_seconds = 30;
        /** Distance max de la lunette (clic droit dans l'air). */
        public int inspector_range = 128;
    }

    public static final class Notifications {
        public boolean enabled = true;
        public int fade_in_ticks = 8;
        public int stay_ticks = 40;
        public int fade_out_ticks = 12;
        public String wilderness = "Zone sauvage";
        public boolean subclaim_actionbar = true;
    }

    public static final class Commands {
        /** Commandes toujours autorisees, meme dans un claim qui bloque tout (commandes du mod de claim). */
        public List<String> always_allowed = new ArrayList<>(List.of("claim", "claims", "trust", "untrust", "trustall", "untrustall",
                "trustlist", "claimadmin", "abandonclaim"));
    }

    public static final class AdminZones {
        public int heal_cooldown_seconds = 60;
        public int fly_fall_grace_seconds = 10;
        public int default_level_cap = 50;
        public long default_time = 6000;
        public String default_weather = "CLEAR";
    }

    public static final class Permissions {
        /** Sans LuckPerms (ou noeud non defini) : niveau d'operateur requis pour les noeuds staff/admin. */
        public int op_level_fallback = 2;
    }

    public static final class Sync {
        /** Distance (blocs) sous laquelle la lunette et la balise dessinent les claims en 3D. */
        public int render_radius = 160;
    }

    public static final class Storage {
        /** Delai d'ecriture differee (write-behind) en millisecondes. */
        public int flush_delay_ms = 2000;
    }

    public static ClaimsConfig get() {
        return instance;
    }

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("cobblepulse").resolve("claims");
    }

    public static void load() {
        Path f = dir().resolve("config.json");
        ClaimsConfig c = null;
        try {
            if (Files.exists(f)) c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), ClaimsConfig.class);
        } catch (Exception e) {
            CobblePulseClaims.LOG.error("[CobblePulse-Claims] config.json illisible, valeurs par defaut utilisees : {}", e.toString());
        }
        if (c == null) c = new ClaimsConfig();
        if (c.economy == null) c.economy = new Economy();
        if (c.claims == null) c.claims = new Claims();
        if (c.notifications == null) c.notifications = new Notifications();
        if (c.commands == null) c.commands = new Commands();
        if (c.admin_zones == null) c.admin_zones = new AdminZones();
        if (c.permissions == null) c.permissions = new Permissions();
        if (c.sync == null) c.sync = new Sync();
        if (c.storage == null) c.storage = new Storage();
        c.economy.interval_minutes = Math.max(1, c.economy.interval_minutes);
        c.economy.blocks_per_interval = Math.max(0, c.economy.blocks_per_interval);
        c.economy.max_accrued_blocks = Math.max(0, c.economy.max_accrued_blocks);
        c.claims.min_width = Math.max(1, c.claims.min_width);
        instance = c;
        save();
    }

    public static void save() {
        writeAtomic(dir().resolve("config.json"), GSON.toJson(instance));
    }

    public static void writeAtomic(Path f, String text) {
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            try {
                Files.writeString(f, text, StandardCharsets.UTF_8);
            } catch (Exception e2) {
                CobblePulseClaims.LOG.error("[CobblePulse-Claims] Ecriture impossible de {} : {}", f, e2.toString());
            }
        }
    }
}
