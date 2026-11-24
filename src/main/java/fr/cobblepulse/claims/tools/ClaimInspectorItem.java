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
 * Lunette Topographique (cobblepulse:claim_inspector). Tenue en main (principale ou secondaire) : bordures des claims
 * voisins et de la WorldBorder dessinees en 3D par le client. Clic droit (bloc ou air) : resume de la zone visee.
 */
public class ClaimInspectorItem extends Item {
    public ClaimInspectorItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (ctx.getPlayer() instanceof ServerPlayer p) InspectorService.inspect(p, ctx.getClickedPos());
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.success(st);
        if (player instanceof ServerPlayer p) InspectorService.inspectLook(p);
        return InteractionResultHolder.consume(st);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("En main : bordures des claims en 3D").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Vert/cyan : vous · Bleu : votre clan").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Orange : autres joueurs · Violet : admin").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Clic droit : inspecter la zone visée").withStyle(ChatFormatting.DARK_AQUA));
    }
}
