package fr.cobblepulse.claims.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.HarvestFarmland;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** env.mob_crop_trample : les villageois fermiers ne recoltent pas / ne replantent pas dans un claim protege. */
@Mixin(HarvestFarmland.class)
public abstract class GriefHarvestFarmlandMixin {
    @WrapOperation(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"))
    private boolean cpclaims$harvest(ServerLevel level, BlockPos pos, boolean drop, Entity e, Operation<Boolean> original) {
        if (!Protection.allowsZone(level, pos, Flags.MOB_TRAMPLE)) return false;
        return original.call(level, pos, drop, e);
    }

    @WrapOperation(method = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/Villager;J)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean cpclaims$plant(ServerLevel level, BlockPos pos, BlockState state, Operation<Boolean> original) {
        if (!Protection.allowsZone(level, pos, Flags.MOB_TRAMPLE)) return false;
        return original.call(level, pos, state);
    }
}
