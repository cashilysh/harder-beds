package harderbeds.util;

import harderbeds.Harderbeds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

public class CheckBedLocation {

    public static boolean isWithinVillageStructure(Level world, BlockPos pos) {
        if (world == null || pos == null || world.isClientSide()) return false;

        if(Harderbeds.debug) System.out.println("Checking if Block is in village structure......");

        try {
            ServerLevel serverWorld = (ServerLevel) world;
            if (serverWorld.structureManager() == null) return false;

            var structureReference = serverWorld.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE);

            if (structureReference != null && structureReference.isValid()) {
                if(Harderbeds.debug) System.out.println("Bed is in village structure!");
                return true;
            } else {
                if(Harderbeds.debug) System.out.println("Bed is NOT village structure!");
                return false;
            }

        } catch (Exception e) {
            if(Harderbeds.debug) System.out.println("Primary structure check failed, trying fallback: " + e.getMessage());
            return isWithinVillageStructureFallback(world, pos);
        }
    }

    public static boolean isWithinVillageStructureFallback(Level world, BlockPos pos) {
        if (world == null || pos == null || world.isClientSide()) return false;

        try {
            ServerLevel serverWorld = (ServerLevel) world;
            if (serverWorld.structureManager() == null) return false;

            var structureAccessor = serverWorld.structureManager();
            int radius = 1;
            var currentChunk = serverWorld.getChunk(pos);
            if (currentChunk == null) return false;

            var chunkPos = currentChunk.getPos();

            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    try {
                        // FIX: Use x() and z() getters instead of .x and .z
                        var checkChunkPos = new ChunkPos(chunkPos.x() + x, chunkPos.z() + z);

                        // FIX: Use x() and z() getters
                        if (!serverWorld.hasChunk(checkChunkPos.x(), checkChunkPos.z())) {
                            continue;
                        }

                        // FIX: Use x() and z() getters
                        var chunk = serverWorld.getChunk(checkChunkPos.x(), checkChunkPos.z());
                        if (chunk == null) continue;

                        var structureReferences = structureAccessor.getAllStructuresAt(chunk.getPos().getWorldPosition());
                        if (structureReferences == null) continue;

                        for (var entry : structureReferences.entrySet()) {
                            var structure = entry.getKey();
                            var structureSet = entry.getValue();

                            if (structure != null && structureSet != null && !structureSet.isEmpty()) {
                                if (isVillageStructure(serverWorld, structure)) {
                                    return true;
                                }
                            }
                        }
                    } catch (Exception chunkException) {
                        continue;
                    }
                }
            }

            return false;

        } catch (Exception e) {
            if(Harderbeds.debug)  System.out.println("Fallback structure check failed: " + e.getMessage());
            return false;
        }
    }

    public static boolean isVillageStructure(ServerLevel world, Structure structure) {
        if (world == null || structure == null) return false;

        try {
            var registryManager = world.registryAccess();
            if (registryManager == null) return false;

            var registry = registryManager.lookupOrThrow(Registries.STRUCTURE);
            if (registry == null) return false;

            var identifier = registry.getKey(structure);
            if (identifier == null) return false;

            String path = identifier.getPath();
            if (path == null) return false;

            return path.equals("village_plains") ||
                    path.equals("village_desert") ||
                    path.equals("village_savanna") ||
                    path.equals("village_snowy") ||
                    path.equals("village_taiga") ||
                    path.contains("village");

        } catch (Exception e) {
            if(Harderbeds.debug)  System.err.println("Error checking village structure: " + e.getMessage());
            return false;
        }
    }
}