package fr.cobblepulse.claims.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** env.wither_dragon_grief : le Wither ne detruit pas les blocs d'un claim protege. */
@Mixin(WitherBoss.class)
public abstract class GriefWitherMixin {
    @WrapOperation(method = "customServerAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"))
    private boolean cpclaims$wither(Level level, BlockPos pos, boolean drop, Entity e, Operation<Boolean> original) {
        if (!Protection.allowsZone(level, pos, Flags.WITHER_DRAGON)) return false;
        return original.call(level, pos, drop, e);
    }
}
