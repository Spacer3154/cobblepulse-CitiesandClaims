package fr.cobblepulse.claims.mixin;

import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flag;
import fr.cobblepulse.claims.protect.Interactions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** block.place (et plantations Cobblemon, neige poudreuse) a la position exacte de pose. */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {
    @Shadow
    public abstract Block getBlock();

    @Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;", at = @At("HEAD"), cancellable = true)
    private void cpclaims$place(BlockPlaceContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        if (ctx.getLevel().isClientSide() || !(ctx.getPlayer() instanceof ServerPlayer p)) return;
        Block block = getBlock();
        Flag f = Interactions.forPlace(block);
        BlockPos pos = ctx.getClickedPos();
        boolean ok = Protection.check(p, ctx.getLevel(), pos, f);
        if (ok && (block instanceof DoorBlock || block instanceof DoublePlantBlock)) ok = Protection.check(p, ctx.getLevel(), pos.above(), f);
        if (ok && block instanceof BedBlock) ok = Protection.check(p, ctx.getLevel(), pos.relative(ctx.getHorizontalDirection()), f);
        if (!ok) {
            p.connection.send(new ClientboundBlockUpdatePacket(ctx.getLevel(), pos));
            p.connection.send(new ClientboundBlockUpdatePacket(ctx.getLevel(), pos.above()));
            p.containerMenu.sendAllDataToRemote();
            cir.setReturnValue(InteractionResult.FAIL);
        }
    }
}
