package fr.cobblepulse.claims.flag;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.cobblepulse.claims.ClaimsConfig;
import fr.cobblepulse.claims.CobblePulseClaims;
import fr.cobblepulse.claims.data.FlagState;
import fr.cobblepulse.claims.data.TrustLevel;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static fr.cobblepulse.claims.data.FlagState.ALLOW;
import static fr.cobblepulse.claims.data.FlagState.DENY;
import static fr.cobblepulse.claims.data.TrustLevel.ACCESS;
import static fr.cobblepulse.claims.data.TrustLevel.BUILD;
import static fr.cobblepulse.claims.data.TrustLevel.CONTAINER;
import static fr.cobblepulse.claims.flag.Flag.Category.A;
import static fr.cobblepulse.claims.flag.Flag.Category.B;
import static fr.cobblepulse.claims.flag.Flag.Category.C;
import static fr.cobblepulse.claims.flag.Flag.Category.D;
import static fr.cobblepulse.claims.flag.Flag.Category.E;
import static fr.cobblepulse.claims.flag.Flag.Category.F;

/**
 * Referentiel complet des permissions de claim (section 8 du cahier des charges) et etat global choisi par les
 * administrateurs (config/cobblepulse/claims/flags.json : valeur par defaut Visiteur / Membre et statut Modifiable).
 */
public final class Flags {
    private static final Map<String, Flag> BY_ID = new LinkedHashMap<>();
    private static final List<Flag> ALL = new ArrayList<>();

    // A — Construction & Blocs
    public static final Flag BLOCK_BREAK = p("block.break", A, DENY, ALLOW, BUILD, "Casser des blocs", "Casser des blocs standard dans le claim");
    public static final Flag BLOCK_PLACE = p("block.place", A, DENY, ALLOW, BUILD, "Poser des blocs", "Poser des blocs standard dans le claim");
    public static final Flag BLOCK_TRAMPLE = p("block.trample", A, DENY, DENY, BUILD, "Piétiner les cultures", "Piétiner les terres labourées et cultures");
    public static final Flag CROP_HARVEST = p("block.crop_harvest", A, DENY, ALLOW, BUILD, "Récolter les cultures", "Récolter et replanter les cultures (blé, carottes, etc.)");
    public static final Flag BONEMEAL = p("block.bonemeal", A, DENY, ALLOW, BUILD, "Poudre d'os", "Utiliser de la poudre d'os sur les plantes et cultures");
    public static final Flag FROST_WALKER = p("block.frost_walker", A, DENY, ALLOW, BUILD, "Semelles givrantes", "Geler l'eau avec l'enchantement Semelles givrantes");
    public static final Flag BRUSH = p("block.brush_archaeology", A, DENY, ALLOW, BUILD, "Archéologie (pinceau)", "Utiliser un pinceau sur le sable/gravier suspect");
    public static final Flag WATER = p("fluid.water_place", A, DENY, ALLOW, BUILD, "Seau d'eau", "Placer ou récupérer de l'eau avec un seau");
    public static final Flag LAVA = p("fluid.lava_place", A, DENY, DENY, BUILD, "Seau de lave", "Placer ou récupérer de la lave avec un seau");
    public static final Flag POWDER_SNOW = p("fluid.powder_snow", A, DENY, ALLOW, BUILD, "Neige poudreuse", "Placer ou récupérer de la neige poudreuse");
    public static final Flag FLOW_IN = z("fluid.flow_in", A, DENY, true, "Liquides entrants", "Autoriser les liquides extérieurs à couler dans le claim");
    public static final Flag PISTON = z("piston.cross_border", A, DENY, true, "Pistons transfrontaliers", "Autoriser les pistons à pousser/tirer des blocs à travers la frontière");
    // B — Conteneurs & Interactions
    public static final Flag CHEST = p("container.chest_barrel", B, DENY, ALLOW, CONTAINER, "Coffres et tonneaux", "Ouvrir coffres, tonneaux et coffres piégés");
    public static final Flag SHULKER = p("container.shulker", B, DENY, ALLOW, CONTAINER, "Boîtes de Shulker", "Ouvrir les boîtes de Shulker posées");
    public static final Flag FURNACE = p("container.furnace_smelt", B, DENY, ALLOW, CONTAINER, "Fours et alambics", "Utiliser fours, hauts fourneaux, fumoirs et alambics");
    public static final Flag HOPPER = p("container.hopper_dropper", B, DENY, ALLOW, CONTAINER, "Entonnoirs et distributeurs", "Ouvrir entonnoirs, distributeurs et droppers");
    public static final Flag ENDER_CHEST = p("container.ender_chest", B, ALLOW, ALLOW, ACCESS, "Coffre de l'Ender", "Ouvrir un Coffre de l'Ender (Ender Chest)");
    public static final Flag DOOR = p("interact.door_gate", B, DENY, ALLOW, ACCESS, "Portes et portillons", "Ouvrir/fermer portes, trappes et portillons");
    public static final Flag BUTTON = p("interact.button_lever", B, DENY, ALLOW, ACCESS, "Boutons et leviers", "Activer boutons, leviers et plaques de pression");
    public static final Flag NOTE_JUKEBOX = p("interact.note_jukebox", B, DENY, ALLOW, CONTAINER, "Blocs musicaux et jukebox", "Utiliser les blocs musicaux et jukebox (disques)");
    public static final Flag BED = p("interact.bed_respawn", B, DENY, ALLOW, ACCESS, "Lits et ancres", "Dormir dans un lit ou charger une ancre de réapparition");
    public static final Flag ANVIL = p("interact.anvil_enchant", B, ALLOW, ALLOW, ACCESS, "Enclumes et établis", "Utiliser enclumes, tables d'enchantement, meules, métiers");
    public static final Flag BEACON = p("interact.beacon", B, DENY, DENY, BUILD, "Balise (Beacon)", "Modifier les effets d'une balise (Beacon)");
    public static final Flag SIGN = p("interact.sign_lectern", B, DENY, ALLOW, BUILD, "Pancartes et pupitres", "Modifier les pancartes ou prendre un livre sur un pupitre");
    public static final Flag FRAME = p("interact.item_frame_armorstand", B, DENY, ALLOW, BUILD, "Cadres et porte-armures", "Tourner/retirer items des cadres et porte-armures");
    public static final Flag REDSTONE = p("redstone.edit", B, DENY, ALLOW, BUILD, "Réglages redstone", "Modifier le délai des répéteurs, comparateurs et capteurs");
    // C — Entites & Objets
    public static final Flag HURT_PASSIVE = p("entity.hurt_passive", C, DENY, ALLOW, BUILD, "Animaux passifs", "Attaquer/tuer les animaux passifs (vaches, moutons, abeilles)");
    public static final Flag HURT_HOSTILE = p("entity.hurt_hostile", C, ALLOW, ALLOW, ACCESS, "Monstres hostiles", "Attaquer/tuer les monstres hostiles vanilla");
    public static final Flag PVP = pNoBypass("entity.hurt_player", C, DENY, DENY, BUILD, "PvP", "PvP physique direct entre joueurs dans le claim");
    public static final Flag BREED = p("entity.breed_feed", C, DENY, ALLOW, BUILD, "Nourrir et élever", "Nourrir, accoupler ou tondre/traire les animaux");
    public static final Flag LEASH = p("entity.leash_name", C, DENY, ALLOW, BUILD, "Laisse et étiquette", "Attacher une laisse ou utiliser une étiquette (Name Tag)");
    public static final Flag TRADE = p("entity.villager_trade", C, ALLOW, ALLOW, ACCESS, "Commerce villageois", "Échanger avec les villageois et marchands ambulants");
    public static final Flag VEHICLE_PLACE = p("vehicle.place_break", C, DENY, ALLOW, BUILD, "Poser/casser véhicules", "Poser ou casser bateaux et wagonnets");
    public static final Flag VEHICLE_RIDE = p("vehicle.ride", C, ALLOW, ALLOW, ACCESS, "Monter en véhicule", "Monter dans un bateau, wagonnet ou sur un cheval/cochon");
    public static final Flag PICKUP = p("item.pickup", C, ALLOW, ALLOW, ACCESS, "Ramasser des objets", "Ramasser des objets au sol dans le claim");
    public static final Flag DROP = p("item.drop", C, ALLOW, ALLOW, ACCESS, "Jeter des objets", "Jeter des objets au sol dans le claim");
    public static final Flag XP = p("item.xp_pickup", C, ALLOW, ALLOW, ACCESS, "Orbes d'expérience", "Absorber les orbes d'expérience au sol");
    // D — Environnement & Deplacement
    public static final Flag EXPLOSION_BLOCK = z("env.explosion_block", D, DENY, true, "Explosions (blocs)", "Dégâts aux blocs causés par les explosions (TNT, Creepers, lits)");
    public static final Flag EXPLOSION_ENTITY = z("env.explosion_entity", D, DENY, true, "Explosions (entités)", "Dégâts aux entités et Pokémon causés par les explosions");
    public static final Flag FIRE_IGNITE = p("env.fire_ignite", D, DENY, DENY, BUILD, "Allumer un feu", "Allumer un feu (briquet, boule de feu)");
    public static final Flag FIRE_SPREAD = z("env.fire_spread", D, DENY, true, "Propagation du feu", "Propagation naturelle du feu sur les blocs");
    public static final Flag MELT = z("env.ice_snow_melt", D, ALLOW, true, "Fonte glace/neige", "Fonte naturelle de la glace et de la neige");
    public static final Flag LEAF_DECAY = z("env.leaf_decay", D, ALLOW, true, "Chute des feuilles", "Décomposition naturelle des feuilles d'arbres");
    public static final Flag ENTER = p("move.enter_claim", D, ALLOW, ALLOW, ACCESS, "Entrer dans le claim", "Autoriser les joueurs à entrer physiquement dans le claim");
    public static final Flag ENDERPEARL = p("move.enderpearl", D, ALLOW, ALLOW, ACCESS, "Perles de l'Ender", "Utiliser des perles de l'Ender pour entrer/se déplacer");
    public static final Flag CHORUS = p("move.chorus_fruit", D, DENY, ALLOW, ACCESS, "Fruit de chorus", "Téléportation via fruit de chorus dans le claim");
    public static final Flag COMMANDS = p("command.execute", D, ALLOW, ALLOW, ACCESS, "Exécuter des commandes",
            "Exécuter des commandes chat dans le claim (blocage total ou filtrage par liste dans l'écran)");
    public static final Flag ELYTRA = p("move.elytra_flight", D, ALLOW, ALLOW, ACCESS, "Vol en élytres", "Voler en Élytres au-dessus/dans le claim");
    public static final Flag MOB_GRIEF = z("env.mob_grief_blocks", D, DENY, true, "Grief des mobs",
            "Autoriser les mobs à casser, ramasser ou poser des blocs (Endermen, Creepers, Ghasts, Ravagers, Silverfish)");
    public static final Flag MOB_TRAMPLE = z("env.mob_crop_trample", D, DENY, true, "Cultures et mobs",
            "Autoriser les mobs (animaux, monstres, villageois) à piétiner les cultures et récolter les champs");
    public static final Flag MOB_DOOR = z("env.mob_door_break", D, DENY, true, "Portes cassées par les mobs", "Autoriser les zombies et vindicateurs à casser les portes dans le claim");
    public static final Flag WITHER_DRAGON = z("env.wither_dragon_grief", D, DENY, false, "Wither et Ender Dragon",
            "Autoriser la destruction de blocs causée par le Wither ou l'Ender Dragon dans le claim");
    public static final Flag PROJECTILE_GRIEF = z("env.projectile_block_grief", D, DENY, true, "Projectiles de mobs",
            "Autoriser les projectiles de mobs (boules de feu de Blaze/Ghast, crânes de Wither) à enflammer ou détruire des blocs");
    // E — Cobblemon
    public static final Flag CAPTURE = p("cobblemon.capture_wild", E, DENY, ALLOW, BUILD, "Capturer", "Lancer des Poké Balls pour capturer des Pokémon sauvages");
    public static final Flag CAPTURE_RARE = p("cobblemon.capture_shiny_legendary", E, DENY, DENY, BUILD, "Capturer Shiny/Légendaires",
            "Capturer spécifiquement les Pokémon Shiny, Légendaires ou Mythiques");
    public static final Flag BATTLE_WILD = p("cobblemon.battle_wild", E, ALLOW, ALLOW, BUILD, "Combat sauvage", "Engager un combat contre un Pokémon sauvage");
    public static final Flag BATTLE_PVP = pNoBypass("cobblemon.battle_pvp", E, ALLOW, ALLOW, BUILD, "Combat de dresseurs", "Défier un autre joueur en combat Pokémon");
    public static final Flag HURT_WILD = p("cobblemon.hurt_wild", E, DENY, ALLOW, BUILD, "Blesser un sauvage", "Infliger des dégâts physiques (épée, arc, feu) aux Pokémon sauvages");
    public static final Flag HURT_OWNED = p("cobblemon.hurt_owned", E, DENY, DENY, BUILD, "Blesser un Pokémon de joueur", "Infliger des dégâts physiques aux Pokémon appartenant à un joueur");
    public static final Flag KNOCKBACK = p("cobblemon.projectile_knockback", E, DENY, DENY, BUILD, "Repousser les Pokémon",
            "Repousser les Pokémon avec des projectiles (flèches, boules de neige, canne à pêche)");
    public static final Flag HEALER = p("cobblemon.machine_healer", E, ALLOW, ALLOW, ACCESS, "Soigneur", "Utiliser les Healing Machines (soigneurs Pokémon)");
    public static final Flag PC = p("cobblemon.machine_pc", E, ALLOW, ALLOW, ACCESS, "PC Cobblemon", "Utiliser les PC Cobblemon");
    public static final Flag FOSSIL = p("cobblemon.machine_fossil", E, DENY, ALLOW, CONTAINER, "Machines à fossiles", "Utiliser l'Analyseur de Fossiles et le Réservoir de Restauration");
    public static final Flag PASTURE_VIEW = p("cobblemon.pasture_view", E, ALLOW, ALLOW, ACCESS, "Voir les Pâturages", "Consulter l'interface des Pasture Blocks (Pâturages)");
    public static final Flag PASTURE_MANAGE = p("cobblemon.pasture_manage", E, DENY, DENY, BUILD, "Gérer les Pâturages",
            "Ajouter/retirer des Pokémon et récupérer les objets des Pâturages");
    public static final Flag APRICORN = p("cobblemon.apricorn_harvest", E, DENY, ALLOW, BUILD, "Noigrumes et baies", "Récolter les Noigrumes (Apricorns) et baies sur les arbres");
    public static final Flag BERRY_PLANT = p("cobblemon.berry_plant", E, DENY, ALLOW, BUILD, "Planter baies/Noigrumes", "Planter des graines de baies et pousses de Noigrumes");
    public static final Flag NPC = p("cobblemon.npc_interact", E, ALLOW, ALLOW, ACCESS, "PNJ Cobblemon", "Interagir avec les PNJ Cobblemon (dresseurs, infirmières, marchands)");
    public static final Flag SEND_OUT = p("cobblemon.send_out_pokemon", E, ALLOW, ALLOW, ACCESS, "Sortir son Pokémon", "Sortir son Pokémon de sa Poké Ball dans la zone");
    public static final Flag RIDE = p("cobblemon.ride", E, ALLOW, ALLOW, ACCESS, "Monture Pokémon", "Chevaucher/utiliser une monture Pokémon dans la zone");
    public static final Flag POKE_LOOT = p("cobblemon.poke_loot", E, DENY, ALLOW, CONTAINER, "Poké Loots", "Ouvrir les Poké Loots (coffres Poké Ball) présents dans le claim");
    public static final Flag RANCH = p("cobblemon.ranch_daycare", E, DENY, ALLOW, CONTAINER, "Bloc de Pension",
            "Ouvrir un Bloc de Pension de CobblePulse-Ranch (propriétaire du claim et joueurs ajoutés par /trust)");
    // F — Admin & Villes (verrouilles)
    public static final Flag WILD_SPAWN = z("admin.wild_spawn", F, ALLOW, false, "Spawn Pokémon sauvages", "Autoriser le spawn naturel des Pokémon sauvages dans le claim");
    public static final Flag HOSTILE_SPAWN = z("admin.hostile_spawn", F, ALLOW, false, "Spawn monstres", "Autoriser le spawn naturel des monstres hostiles vanilla");
    public static final Flag PASSIVE_SPAWN = z("admin.passive_spawn", F, ALLOW, false, "Spawn animaux", "Autoriser le spawn naturel des animaux passifs vanilla");
    public static final Flag HEAL_ON_ENTRY = z("admin.heal_on_entry", F, DENY, false, "Soin à l'entrée", "Soigner automatiquement l'équipe Pokémon à l'entrée de la zone");
    public static final Flag LEVEL_CAP = z("admin.level_cap", F, DENY, false, "Plafond de niveau", "Appliquer un plafond de niveau Pokémon en combat (Arènes)");
    public static final Flag PVP_ARENA = z("admin.pvp_arena", F, DENY, false, "Arène compétitive", "Activer les règles de combat d'arène compétitive");
    public static final Flag KEEP_INVENTORY = z("admin.keep_inventory", F, DENY, false, "Garder l'inventaire", "Conserver l'inventaire et l'XP en cas de mort dans le claim");
    public static final Flag INVULNERABLE = z("admin.invulnerable_player", F, DENY, false, "Joueurs invulnérables", "Rendre les joueurs invulnérables aux dégâts dans la zone (Spawn)");
    public static final Flag HUNGER = z("admin.hunger_drain", F, ALLOW, false, "Perte de faim", "Activer ou désactiver la perte de faim dans la zone");
    public static final Flag WEATHER = z("admin.weather_lock", F, DENY, false, "Météo figée", "Forcer une météo locale dans la zone (Clair / Pluie / Orage)");
    public static final Flag TIME = z("admin.time_lock", F, DENY, false, "Heure figée", "Figer l'heure visuelle dans la zone (Jour / Nuit)");
    public static final Flag FLY = z("admin.fly_zone", F, DENY, false, "Zone de vol", "Autoriser le vol créatif/survie dans les limites du claim");

    /** Etat global courant (modifiable par les administrateurs). */
    private static final Map<String, Global> GLOBAL = new HashMap<>();

    public static final class Global {
        public FlagState visitor;
        public FlagState member;
        public boolean editable;

        Global(FlagState visitor, FlagState member, boolean editable) {
            this.visitor = visitor;
            this.member = member;
            this.editable = editable;
        }
    }

    private Flags() {}

    private static Flag reg(Flag f) {
        BY_ID.put(f.id(), f);
        ALL.add(f);
        return f;
    }

    private static Flag p(String id, Flag.Category c, FlagState v, FlagState m, TrustLevel lvl, String label, String desc) {
        return reg(new Flag(id, c, Flag.Kind.PLAYER, v, m, true, lvl, true, label, desc));
    }

    private static Flag pNoBypass(String id, Flag.Category c, FlagState v, FlagState m, TrustLevel lvl, String label, String desc) {
        return reg(new Flag(id, c, Flag.Kind.PLAYER, v, m, true, lvl, false, label, desc));
    }

    private static Flag z(String id, Flag.Category c, FlagState v, boolean editable, String label, String desc) {
        return reg(new Flag(id, c, Flag.Kind.ZONE, v, v, editable, TrustLevel.NONE, false, label, desc));
    }

    public static List<Flag> all() {
        return Collections.unmodifiableList(ALL);
    }

    public static Flag byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    public static Global global(Flag f) {
        return GLOBAL.computeIfAbsent(f.id(), k -> new Global(f.defVisitor(), f.defMember(), f.defEditable()));
    }

    public static FlagState globalValue(Flag f, boolean member) {
        Global g = global(f);
        return f.isZone() || !member ? g.visitor : g.member;
    }

    public static boolean editable(Flag f) {
        return global(f).editable;
    }

    private static Path file() {
        return ClaimsConfig.dir().resolve("flags.json");
    }

    public static void load() {
        GLOBAL.clear();
        Path f = file();
        try {
            if (Files.exists(f)) {
                JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Flag fl : ALL) {
                    if (!o.has(fl.id()) || !o.get(fl.id()).isJsonObject()) continue;
                    JsonObject e = o.getAsJsonObject(fl.id());
                    FlagState v = e.has("visitor") ? FlagState.parse(e.get("visitor").getAsString()) : null;
                    FlagState m = e.has("member") ? FlagState.parse(e.get("member").getAsString()) : null;
                    boolean ed = e.has("editable") ? e.get("editable").getAsBoolean() : fl.defEditable();
                    if (v == null || v == FlagState.DEFAULT) v = fl.defVisitor();
                    if (m == null || m == FlagState.DEFAULT) m = fl.defMember();
                    if (fl.isZone()) m = v;
                    GLOBAL.put(fl.id(), new Global(v, m, ed));
                }
            }
        } catch (Exception e) {
            CobblePulseClaims.LOG.error("[CobblePulse-Claims] flags.json illisible, valeurs du cahier des charges utilisees : {}", e.toString());
        }
        save();
    }

    public static void save() {
        JsonObject o = new JsonObject();
        for (Flag fl : ALL) {
            Global g = global(fl);
            JsonObject e = new JsonObject();
            e.addProperty("category", fl.category().name());
            e.addProperty("visitor", g.visitor.name());
            if (!fl.isZone()) e.addProperty("member", g.member.name());
            e.addProperty("editable", g.editable);
            e.addProperty("description", fl.description());
            o.add(fl.id(), e);
        }
        ClaimsConfig.writeAtomic(file(), ClaimsConfig.GSON.toJson(o));
    }

    public static void setGlobal(Flag f, boolean member, FlagState s) {
        if (s == null || s == FlagState.DEFAULT) s = member ? f.defMember() : f.defVisitor();
        Global g = global(f);
        if (f.isZone()) {
            g.visitor = s;
            g.member = s;
        } else if (member) g.member = s;
        else g.visitor = s;
        save();
    }

    public static void setEditable(Flag f, boolean editable) {
        global(f).editable = editable;
        save();
    }
}
