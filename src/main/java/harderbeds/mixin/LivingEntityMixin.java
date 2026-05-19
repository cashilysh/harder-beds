package harderbeds.mixin;

import harderbeds.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {

    private static int badEffectDurationMinutes = 5;


    @Inject(method = "stopSleeping", at = @At("HEAD"))
    private void onWakeUp(CallbackInfo ci) {


        if (ModConfig.getSettings().isVillageBedPenaltyEnabled()) {


            LivingEntity self = (LivingEntity) (Object) this;

            // Only apply to players
            if (!(self instanceof Player)) {
                return;
            }

            Player player = (Player) self;
            Level world = player.level();

            if (world.getOverworldClockTime() % 24000L < 1000L) {

                if (!world.isClientSide() && world instanceof ServerLevel) {

                    try {
                        // Get the sleeping position
                        BlockPos sleepingPos = player.getSleepingPos().orElse(null);
                        if (sleepingPos != null) {
                            if (!harderbeds.util.BedSafetyChecker.isBedAllowed(world, sleepingPos)) {
                                player.addEffect(new MobEffectInstance(MobEffects.HUNGER, badEffectDurationMinutes * 60 * 20, 0));
                                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, badEffectDurationMinutes * 60 * 20, 0));
                                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, badEffectDurationMinutes * 60 * 20, 0));
                                player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, badEffectDurationMinutes * 60 * 20, 0));

                                player.sendSystemMessage(Component.translatable("That was an uncomfortable night..."));
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