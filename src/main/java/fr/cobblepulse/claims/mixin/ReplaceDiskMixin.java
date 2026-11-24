package fr.cobblepulse.claims.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.effects.ReplaceDisk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Semelles givrantes (effet replace_disk) : block.frost_walker pour les joueurs. */
@Mixin(ReplaceDisk.class)
public abstract class ReplaceDiskMixin {
    @WrapOperation(method = "apply", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean cpclaims$frost(ServerLevel level, BlockPos pos, BlockState state, Operation<Boolean> original, @Local(argsOnly = true) Entity entity) {
        if (entity instanceof ServerPlayer p && !Protection.check(p, level, pos.getX(), pos.getZ(), Flags.FROST_WALKER, false)) return false;
        return original.call(level, pos, state);
    }
}
