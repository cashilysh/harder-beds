package harderbeds.events;

import harderbeds.Harderbeds;
import harderbeds.config.ModConfig;
import harderbeds.util.CheckBedLocation;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
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
        // Register using the After interface properly
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) ->
                onBlockBreakAfter(level, player, pos, state, blockEntity));
    }

    private void onBlockBreakAfter(Level world, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (!ModConfig.getSettings().shouldPreventBedDropInVillages()){
            return;
        }

        // Safety checks: ensure parameters are valid
        if (world == null || player == null || pos == null || state == null) {
            return;
        }

        // Safety check: ensure we're dealing with a bed
        if (!(state.getBlock() instanceof BedBlock)) {
            return;
        }

        // Only process on server side
        if (world.isClientSide() || !(world instanceof ServerLevel)) {
            return;
        }

        try {
            // Check if this bed was within a village structure
            if (CheckBedLocation.isWithinVillageStructure(world, pos)) {
                // Remove any bed drops that spawned
                removeDropsAroundPosition(world, pos);
            }
        } catch (Exception e) {
            if(Harderbeds.debug) System.err.println("Error in CancelVillageBedDrop event handler: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void removeDropsAroundPosition(Level world, BlockPos pos) {
        if (world == null || pos == null || world.isClientSide()) {
            return;
        }

        try {
            // Create a box around the broken block position to search for items
            AABB searchBox = new AABB(pos).inflate(2.0); // 2 block radius

            // Find all item entities in the area
            world.getEntitiesOfClass(ItemEntity.class, searchBox, entity -> {
                if (entity == null || entity.getItem() == null) {
                    return false;
                }
                // Check if the item is a bed item
                return entity.getItem().getItem() instanceof BedItem;
            }).forEach(itemEntity -> {
                // Remove the bed drop
                itemEntity.discard();
                if(Harderbeds.debug) System.out.println("Removed bed drop in village!");
            });

        } catch (Exception e) {
            if(Harderbeds.debug) System.err.println("Error removing drops: " + e.getMessage());
        }
    }
}