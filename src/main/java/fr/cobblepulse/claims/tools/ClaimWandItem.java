package fr.cobblepulse.claims.tools;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Balise de Territoire (cobblepulse:claim_wand). Clic droit : coin A puis coin B (creation), clic droit sur un coin
 * existant : redimensionnement, Maj + clic droit dans un claim : ecran de gestion.
 */
public class ClaimWandItem extends Item {
    public ClaimWandItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (ctx.getPlayer() instanceof ServerPlayer p) WandService.click(p, ctx.getClickedPos(), p.isShiftKeyDown());
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.success(st);
        if (player instanceof ServerPlayer p) WandService.clickAir(p, p.isShiftKeyDown());
        return InteractionResultHolder.consume(st);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Clic droit : coin A, puis coin opposé B").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Clic droit sur un coin : redimensionner").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Dans votre claim : crée une parcelle").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Maj + clic droit : gérer le territoire").withStyle(ChatFormatting.DARK_AQUA));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
