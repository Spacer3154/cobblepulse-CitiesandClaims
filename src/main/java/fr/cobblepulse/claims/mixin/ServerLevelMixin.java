package fr.cobblepulse.claims.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.cobblepulse.claims.zone.ZoneEffects;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** admin.weather_lock : la meteo du monde n'est pas envoyee aux joueurs dont la zone fige la meteo. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @WrapOperation(method = "advanceWeatherCycle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/resources/ResourceKey;)V"))
    private void cpclaims$weatherDim(PlayerList list, Packet<?> packet, ResourceKey<Level> dim, Operation<Void> original) {
        ZoneEffects.broadcastWeather(list, packet, dim);
    }

    @WrapOperation(method = "advanceWeatherCycle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;)V"))
    private void cpclaims$weatherAll(PlayerList list, Packet<?> packet, Operation<Void> original) {
        ZoneEffects.broadcastWeather(list, packet, null);
    }
}
