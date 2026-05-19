package harderbeds.mixin;

import harderbeds.config.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BedBlock.class)
public class BedBlockMixin {

    private static int badEffectDurationMinutes = 5;

    @Inject(method = "useWithoutItem", at = @At("HEAD"), cancellable = true)

    private void checkBedSafety(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {


        if (ModConfig.getSettings().shouldSimulateMobPathingOnSleep()) {

        if (!world.isClientSide() && world instanceof ServerLevel) {
            try {
                if (!harderbeds.util.BedSafetyChecker.isBedSafe(world, pos, player)) {

                    player.sendSystemMessage(Component.translatable("Monsters can reach the bed!"));
                    cir.setReturnValue(InteractionResult.SUCCESS);
                }
            } catch (Exception e) {
                // Log error if you have a logger
                e.printStackTrace();
            }
        }
    }
    }

    @Inject(method = "useWithoutItem", at = @At("TAIL"), cancellable = true)
    private void checkVillageBedSleep(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {

        if (ModConfig.getSettings().isVillageBedPenaltyEnabled()) {

            if ((cir.getReturnValue() == InteractionResult.SUCCESS_SERVER || cir.getReturnValue() == InteractionResult.SUCCESS) && player.isSleeping()) {
                if (!world.isClientSide() && world instanceof ServerLevel) {
                    try {
                        if (!harderbeds.util.BedSafetyChecker.isBedAllowed(world, pos)) {
                            player.sendSystemMessage(Component.translatable("Prepare for a night full of weird villager dreams...."));
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}