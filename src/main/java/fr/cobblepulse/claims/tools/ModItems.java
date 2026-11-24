package fr.cobblepulse.claims.tools;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/** Objets du registre « cobblepulse » : Balise de Territoire et Lunette Topographique. */
public final class ModItems {
    public static final String NAMESPACE = "cobblepulse";
    public static Item CLAIM_WAND;
    public static Item CLAIM_INSPECTOR;

    private ModItems() {}

    public static void register() {
        CLAIM_WAND = Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(NAMESPACE, "claim_wand"),
                new ClaimWandItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
        CLAIM_INSPECTOR = Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(NAMESPACE, "claim_inspector"),
                new ClaimInspectorItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(e -> {
            e.accept(CLAIM_WAND);
            e.accept(CLAIM_INSPECTOR);
        });
    }
}
