package fr.cobblepulse.claims.protect;

import com.cobblemon.mod.common.block.ApricornBlock;
import com.cobblemon.mod.common.block.ApricornSaplingBlock;
import com.cobblemon.mod.common.block.BerryBlock;
import com.cobblemon.mod.common.block.DisplayCaseBlock;
import com.cobblemon.mod.common.block.FossilAnalyzerBlock;
import com.cobblemon.mod.common.block.HealingMachineBlock;
import com.cobblemon.mod.common.block.MonitorBlock;
import com.cobblemon.mod.common.block.PCBlock;
import com.cobblemon.mod.common.block.PastureBlock;
import com.cobblemon.mod.common.block.RestorationTankBlock;
import com.cobblemon.mod.common.block.campfirepot.CampfirePotBlock;
import com.cobblemon.mod.common.block.chest.GildedChestBlock;
import com.cobblemon.mod.common.block.tmmachine.TMMachineBlock;
import fr.cobblepulse.claims.flag.Flag;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.EnderEyeItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Correspondance bloc / objet -> permission (flag) pour les clics droits. */
public final class Interactions {
    private static final ResourceLocation RANCH_BLOCK = ResourceLocation.fromNamespaceAndPath("cobblepulse_ranch", "ranch_block");

    private Interactions() {}

    /** Flag de l'interaction avec le bloc lui-meme (clic droit sans s'accroupir), null si le bloc n'a pas d'interaction protegee. */
    public static Flag forBlock(BlockState state) {
        Block b = state.getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
        if (RANCH_BLOCK.equals(id)) return Flags.RANCH;
        // Cobblemon
        if (b instanceof HealingMachineBlock) return Flags.HEALER;
        if (b instanceof PCBlock) return Flags.PC;
        if (b instanceof FossilAnalyzerBlock || b instanceof RestorationTankBlock || b instanceof MonitorBlock) return Flags.FOSSIL;
        if (b instanceof PastureBlock) return Flags.PASTURE_VIEW;
        if (b instanceof ApricornBlock || b instanceof BerryBlock) return Flags.APRICORN;
        if (b instanceof GildedChestBlock) return Flags.POKE_LOOT;
        if (b instanceof TMMachineBlock) return Flags.ANVIL;
        if (b instanceof CampfirePotBlock) return Flags.FURNACE;
        if (b instanceof DisplayCaseBlock) return Flags.FRAME;
        if (b instanceof com.cobblemon.mod.common.block.LecternBlock) return Flags.SIGN;
        // Vanilla
        if (b instanceof EnderChestBlock) return Flags.ENDER_CHEST;
        if (b instanceof ShulkerBoxBlock) return Flags.SHULKER;
        if (b instanceof ChestBlock || b instanceof BarrelBlock || b instanceof ChiseledBookShelfBlock || b instanceof DecoratedPotBlock) return Flags.CHEST;
        if (b instanceof AbstractFurnaceBlock || b instanceof BrewingStandBlock || b instanceof CampfireBlock) return Flags.FURNACE;
        if (b instanceof HopperBlock || b instanceof DispenserBlock || b instanceof CrafterBlock) return Flags.HOPPER;
        if (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock) return Flags.DOOR;
        if (b instanceof ButtonBlock || b instanceof LeverBlock) return Flags.BUTTON;
        if (b instanceof NoteBlock || b instanceof JukeboxBlock) return Flags.NOTE_JUKEBOX;
        if (b instanceof BedBlock || b instanceof RespawnAnchorBlock) return Flags.BED;
        if (b instanceof AnvilBlock || b instanceof EnchantingTableBlock || b instanceof GrindstoneBlock || b instanceof StonecutterBlock
                || b instanceof LoomBlock || b instanceof CartographyTableBlock || b instanceof SmithingTableBlock || b instanceof CraftingTableBlock)
            return Flags.ANVIL;
        if (b instanceof BeaconBlock) return Flags.BEACON;
        if (b instanceof SignBlock || b instanceof LecternBlock) return Flags.SIGN;
        if (b instanceof RepeaterBlock || b instanceof ComparatorBlock || b instanceof DaylightDetectorBlock || b instanceof RedStoneWireBlock)
            return Flags.REDSTONE;
        if (b instanceof SweetBerryBushBlock || b instanceof CaveVinesBlock || b instanceof CaveVinesPlantBlock) return Flags.CROP_HARVEST;
        if (b instanceof ComposterBlock || b instanceof CakeBlock || b instanceof CandleBlock || b instanceof CandleCakeBlock
                || b instanceof FlowerPotBlock || b instanceof AbstractCauldronBlock || b instanceof BeehiveBlock || b instanceof VaultBlock)
            return Flags.BLOCK_PLACE;
        if (b instanceof DragonEggBlock) return Flags.BLOCK_BREAK;
        return null;
    }

    /** Flag de l'objet tenu utilise sur un bloc (si le bloc n'a pas d'interaction propre). relative = sur la face visee. */
    public record ItemUse(Flag flag, boolean relative) {}

    public static ItemUse forItemOnBlock(Item item) {
        if (item instanceof BoneMealItem) return new ItemUse(Flags.BONEMEAL, false);
        if (item instanceof FlintAndSteelItem || item instanceof FireChargeItem) return new ItemUse(Flags.FIRE_IGNITE, true);
        if (item instanceof BrushItem) return new ItemUse(Flags.BRUSH, false);
        if (item instanceof HoeItem || item instanceof ShovelItem || item instanceof AxeItem || item instanceof HoneycombItem
                || item instanceof ShearsItem || item instanceof EnderEyeItem) return new ItemUse(Flags.BLOCK_BREAK, false);
        if (item instanceof MinecartItem) return new ItemUse(Flags.VEHICLE_PLACE, false);
        if (item instanceof LeadItem) return new ItemUse(Flags.LEASH, false);
        if (item instanceof SpawnEggItem || item instanceof ArmorStandItem || item instanceof HangingEntityItem || item instanceof EndCrystalItem)
            return new ItemUse(Flags.BLOCK_PLACE, true);
        return null;
    }

    /** Casser ce bloc : recolte (cultures, Noigrumes, baies) ou cassage standard. */
    public static Flag forBreak(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof ApricornBlock || b instanceof BerryBlock) return Flags.APRICORN;
        if (state.is(BlockTags.CROPS) || b instanceof CocoaBlock || b instanceof NetherWartBlock || b instanceof SweetBerryBushBlock
                || b instanceof StemBlock || b instanceof PitcherCropBlock) return Flags.CROP_HARVEST;
        return Flags.BLOCK_BREAK;
    }

    /** Pose d'un bloc : plantation Cobblemon, neige poudreuse ou pose standard. */
    public static Flag forPlace(Block placed) {
        if (placed instanceof BerryBlock || placed instanceof ApricornSaplingBlock || placed instanceof ApricornBlock) return Flags.BERRY_PLANT;
        if (placed instanceof PowderSnowBlock) return Flags.POWDER_SNOW;
        return Flags.BLOCK_PLACE;
    }
}
