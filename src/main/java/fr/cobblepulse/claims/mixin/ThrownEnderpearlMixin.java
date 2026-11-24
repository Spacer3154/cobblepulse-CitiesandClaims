package fr.cobblepulse.claims.mixin;

import fr.cobblepulse.claims.border.WorldBorders;
import fr.cobblepulse.claims.claim.Protection;
import fr.cobblepulse.claims.flag.Flags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** move.enderpearl / move.enter_claim / WorldBorder : la teleportation est annulee et la perle rendue. */
@Mixin(ThrownEnderpearl.class)
public abstract class ThrownEnderpearlMixin {
    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void cpclaims$pearl(HitResult hit, CallbackInfo ci) {
        ThrownEnderpearl self = (ThrownEnderpearl) (Object) this;
        if (self.level().isClientSide() || !(self.getOwner() instanceof ServerPlayer p)) return;
        int x = (int) Math.floor(hit.getLocation().x), z = (int) Math.floor(hit.getLocation().z);
        boolean ok = Protection.check(p, self.level(), x, z, Flags.ENDERPEARL, true) && Protection.check(p, self.level(), x, z, Flags.ENTER, true)
                && WorldBorders.allowTeleport(p, (net.minecraft.server.level.ServerLevel) self.level(), hit.getLocation().x, hit.getLocation().z);
        if (ok) return;
        if (!p.isCreative()) p.getInventory().placeItemBackInInventory(new ItemStack(Items.ENDER_PEARL));
        self.discard();
        ci.cancel();
    }
}
