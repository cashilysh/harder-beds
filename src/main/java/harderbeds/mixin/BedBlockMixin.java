package harderbeds.mixin;

import harderbeds.config.ModConfig;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BedBlock.class)
public class BedBlockMixin {

    private static int badEffectDurationMinutes = 5;

    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)

    private void checkBedSafety(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {


        if (ModConfig.getSettings().shouldSimulateMobPathingOnSleep()) {

        if (!world.isClient() && world instanceof ServerWorld) {
            try {
                if (!harderbeds.util.BedSafetyChecker.isBedSafe(world, pos, player)) {

                    player.sendMessage(Text.translatable("Monsters can reach the bed!"), true);
                    cir.setReturnValue(ActionResult.SUCCESS);
                }
            } catch (Exception e) {
                // Log error if you have a logger
                e.printStackTrace();
            }
        }
    }
    }

    @Inject(method = "onUse", at = @At("TAIL"), cancellable = true)
    private void checkVillageBedSleep(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {

        if (ModConfig.getSettings().isVillageBedPenaltyEnabled()) {

            if ((cir.getReturnValue() == ActionResult.SUCCESS_SERVER || cir.getReturnValue() == ActionResult.SUCCESS) && player.isSleeping()) {
                if (!world.isClient() && world instanceof ServerWorld) {
                    try {
                        if (!harderbeds.util.BedSafetyChecker.isBedAllowed(world, pos)) {
                            player.sendMessage(Text.translatable("Prepare for a night full of weird villager dreams...."), true);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}