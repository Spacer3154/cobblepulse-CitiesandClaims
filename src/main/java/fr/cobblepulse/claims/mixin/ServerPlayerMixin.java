package fr.cobblepulse.claims.mixin;

import fr.cobblepulse.claims.border.WorldBorders;
import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/** item.drop (l'objet revient dans l'inventaire) et teleportations hors WorldBorder annulees. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Unique
    private static final ThreadLocal<Boolean> CPCLAIMS_GIVING_BACK = ThreadLocal.withInitial(() -> false);

    @Inject(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void cpclaims$drop(ItemStack stack, boolean dropAround, boolean includeThrower, CallbackInfoReturnable<ItemEntity> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (stack.isEmpty() || self.isDeadOrDying() || self.isRemoved() || CPCLAIMS_GIVING_BACK.get()) return;
        if (Protection.check(self, self.level(), self.getBlockX(), self.getBlockZ(), Flags.DROP, true)) return;
        CPCLAIMS_GIVING_BACK.set(true);
        try {
            self.getInventory().placeItemBackInInventory(stack.copy());
        } finally {
            CPCLAIMS_GIVING_BACK.set(false);
        }
        stack.setCount(0);
        self.containerMenu.sendAllDataToRemote();
        self.inventoryMenu.sendAllDataToRemote();
        cir.setReturnValue(null);
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void cpclaims$tp1(ServerLevel level, double x, double y, double z, float yaw, float pitch, CallbackInfo ci) {
        if (!WorldBorders.allowTeleport((ServerPlayer) (Object) this, level, x, z)) ci.cancel();
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("HEAD"), cancellable = true)
    private void cpclaims$tp2(ServerLevel level, double x, double y, double z, Set<RelativeMovement> rel, float yaw, float pitch, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        double tx = rel.contains(RelativeMovement.X) ? self.getX() + x : x;
        double tz = rel.contains(RelativeMovement.Z) ? self.getZ() + z : z;
        if (!WorldBorders.allowTeleport(self, level, tx, tz)) cir.setReturnValue(false);
    }

    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"), cancellable = true)
    private void cpclaims$tp3(double x, double y, double z, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!WorldBorders.allowTeleport(self, self.serverLevel(), x, z)) ci.cancel();
    }
}
