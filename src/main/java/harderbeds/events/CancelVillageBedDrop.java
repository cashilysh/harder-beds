package harderbeds.events;


import harderbeds.Harderbeds;
import harderbeds.config.ModConfig;
import harderbeds.util.CheckBedLocation;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BedItem;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

public class CancelVillageBedDrop implements ModInitializer {

    @Override
    public void onInitialize() {
        // Change to AFTER event
        PlayerBlockBreakEvents.AFTER.register(this::onBlockBreakAfter);
    }

    private void onBlockBreakAfter(World world, PlayerEntity player, BlockPos pos, BlockState state, net.minecraft.block.entity.BlockEntity blockEntity) {

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
        if (world.isClient() || !(world instanceof ServerWorld)) {
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

    private void removeDropsAroundPosition(World world, BlockPos pos) {
        if (world == null || pos == null || world.isClient()) {
            return;
        }

        try {
            // Create a box around the broken block position to search for items
            Box searchBox = new Box(pos).expand(2.0); // 2 block radius

            // Find all item entities in the area
            world.getEntitiesByClass(ItemEntity.class, searchBox, entity -> {
                if (entity == null || entity.getStack() == null) {
                    return false;
                }
                // Check if the item is a bed item
                return entity.getStack().getItem() instanceof BedItem;
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