package harderbeds.events;

import harderbeds.Harderbeds;
import harderbeds.config.ModConfig;
import harderbeds.util.CheckBedLocation;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class CancelVillageBedDrop implements ModInitializer {

    @Override
    public void onInitialize() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) ->
                onBlockBreakAfter(level, player, pos, state, blockEntity));
    }

    private void onBlockBreakAfter(Level world, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (!ModConfig.getSettings().shouldPreventBedDropInVillages()) {
            return;
        }
        if (world == null || player == null || pos == null || state == null) {
            return;
        }
        if (!(state.getBlock() instanceof BedBlock)) {
            return;
        }
        if (world.isClientSide() || !(world instanceof ServerLevel)) {
            return;
        }
        try {
            if (CheckBedLocation.isWithinVillageStructure(world, pos)) {
                removeDropsAroundPosition(world, pos, player);
            }
        } catch (Exception e) {
            if (Harderbeds.debug) System.err.println("Error in CancelVillageBedDrop event handler: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void removeDropsAroundPosition(Level world, BlockPos pos, Player player) {
        if (world == null || pos == null || world.isClientSide()) {
            return;
        }
        try {
            AABB searchBox = new AABB(pos).inflate(2.0);
            world.getEntitiesOfClass(ItemEntity.class, searchBox, entity ->
                    entity != null && entity.getItem() != null && entity.getItem().getItem() instanceof BedItem
            ).forEach(itemEntity -> {
                itemEntity.discard();
                player.sendSystemMessage(Component.translatable("Cheap villager junk. Nothing worth keeping."));
                if (Harderbeds.debug) System.out.println("Removed bed drop in village!");
            });
        } catch (Exception e) {
            if (Harderbeds.debug) System.err.println("Error removing drops: " + e.getMessage());
        }
    }
}