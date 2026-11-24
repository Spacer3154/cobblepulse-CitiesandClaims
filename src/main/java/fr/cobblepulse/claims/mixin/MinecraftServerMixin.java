package fr.cobblepulse.claims.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.cobblepulse.claims.zone.ZoneEffects;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** admin.time_lock : l'heure envoyee chaque seconde est remplacee par l'heure figee de la zone du joueur. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @WrapOperation(method = "synchronizeTime", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/resources/ResourceKey;)V"))
    private void cpclaims$time(PlayerList list, Packet<?> packet, ResourceKey<Level> dim, Operation<Void> original) {
        ZoneEffects.broadcastTime(list, packet, dim);
    }
}
