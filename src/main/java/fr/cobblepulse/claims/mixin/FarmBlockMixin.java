package fr.cobblepulse.claims.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Pietinement des terres labourees : joueur -> block.trample, mob -> env.mob_crop_trample. */
@Mixin(FarmBlock.class)
public abstract class FarmBlockMixin {
    @WrapOperation(method = "fallOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/FarmBlock;turnToDirt(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V"))
    private void cpclaims$trample(Entity entity, BlockState state, Level level, BlockPos pos, Operation<Void> original) {
        if (!level.isClientSide()) {
            if (entity instanceof ServerPlayer p) {
                if (!Protection.check(p, level, pos.getX(), pos.getZ(), Flags.BLOCK_TRAMPLE, false)) return;
            } else if (!Protection.allowsZone(level, pos, Flags.MOB_TRAMPLE)) return;
        }
        original.call(entity, state, level, pos);
    }
}
