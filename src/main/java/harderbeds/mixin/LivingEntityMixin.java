package harderbeds.mixin;

import harderbeds.config.ModConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    private static int badEffectDurationMinutes = 5;


    @Inject(method = "wakeUp", at = @At("HEAD"))
    private void onWakeUp(CallbackInfo ci) {


        if (ModConfig.getSettings().isVillageBedPenaltyEnabled()) {


            LivingEntity self = (LivingEntity) (Object) this;

            // Only apply to players
            if (!(self instanceof PlayerEntity)) {
                return;
            }

            PlayerEntity player = (PlayerEntity) self;
            World world = player.getWorld();

            if (world.getTimeOfDay() % 24000L < 1000L) {

                if (!world.isClient && world instanceof ServerWorld) {

                    try {
                        // Get the sleeping position
                        BlockPos sleepingPos = player.getSleepingPosition().orElse(null);
                        if (sleepingPos != null) {
                            if (!harderbeds.util.BedSafetyChecker.isBedAllowed(world, sleepingPos)) {
                                player.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, badEffectDurationMinutes * 60 * 20, 0));
                                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, badEffectDurationMinutes * 60 * 20, 0));
                                player.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, badEffectDurationMinutes * 60 * 20, 0));
                                player.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, badEffectDurationMinutes * 60 * 20, 0));

                                player.sendMessage(Text.translatable("That was an uncomfortable night..."), true);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}