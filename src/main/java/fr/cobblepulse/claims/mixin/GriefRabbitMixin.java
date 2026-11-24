package fr.cobblepulse.claims.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** env.mob_crop_trample : les lapins ne pillent pas les carottes d'un claim protege. */
@Mixin(targets = "net.minecraft.world.entity.animal.Rabbit$RaidGardenGoal")
public abstract class GriefRabbitMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"))
    private boolean cpclaims$destroy(Level level, BlockPos pos, boolean drop, Entity e, Operation<Boolean> original) {
        if (!Protection.allowsZone(level, pos, Flags.MOB_TRAMPLE)) return false;
        return original.call(level, pos, drop, e);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean cpclaims$set(Level level, BlockPos pos, BlockState state, int flags, Operation<Boolean> original) {
        if (!Protection.allowsZone(level, pos, Flags.MOB_TRAMPLE)) return false;
        return original.call(level, pos, state, flags);
    }
}
