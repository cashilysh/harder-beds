package harderbeds.mixin;

import harderbeds.config.ModConfig;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.spawner.PhantomSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PhantomSpawner.class)
public class PhantomSpawnerMixin {

    // Your toggleable variable


    @Inject(method = "spawn", at = @At("HEAD"), cancellable = true)
    private void checkPhantomSpawning(ServerWorld world, boolean spawnMonsters, boolean spawnAnimals, CallbackInfo ci) {
        if (ModConfig.getSettings().isPhantomSpawningDisabled()) {
            ci.cancel(); // Cancel the method execution and return early
        }
    }
}