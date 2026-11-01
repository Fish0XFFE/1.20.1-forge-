package com.fish.cpuoptimizer.mixin;

import com.fish.cpuoptimizer.throttle.EntityThrottle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true, require = 0)
    private void cpuoptimizer$throttleDistantEntity(Entity entity, CallbackInfo ci) {
        if (EntityThrottle.shouldSkip(entity)) {
            ci.cancel();
        }
    }
}
