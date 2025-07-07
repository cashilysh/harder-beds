package harderbeds.util;

import harderbeds.Harderbeds;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.StructureTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.structure.Structure;



public class CheckBedLocation {
	
  // Keep all your existing structure checking methods unchanged
    public static boolean isWithinVillageStructure(World world, BlockPos pos) {
        if (world == null || pos == null || world.isClient()) return false;

        if(Harderbeds.debug) System.out.println("Checking if Block is in village structure......");

        try {
            ServerWorld serverWorld = (ServerWorld) world;
            if (serverWorld.getStructureAccessor() == null) return false;

            var structureReference = serverWorld.getStructureAccessor().getStructureContaining(pos, StructureTags.VILLAGE);

            if (structureReference != null && structureReference.hasChildren()) {
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

    public static boolean isWithinVillageStructureFallback(World world, BlockPos pos) {
        if (world == null || pos == null || world.isClient()) return false;

        try {
            ServerWorld serverWorld = (ServerWorld) world;
            if (serverWorld.getStructureAccessor() == null) return false;

            var structureAccessor = serverWorld.getStructureAccessor();
            int radius = 1;
            var currentChunk = serverWorld.getChunk(pos);
            if (currentChunk == null) return false;

            var chunkPos = currentChunk.getPos();

            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    try {
                        var checkChunkPos = new net.minecraft.util.math.ChunkPos(chunkPos.x + x, chunkPos.z + z);

                        if (!serverWorld.isChunkLoaded(checkChunkPos.x, checkChunkPos.z)) {
                            continue;
                        }

                        var chunk = serverWorld.getChunk(checkChunkPos.x, checkChunkPos.z);
                        if (chunk == null) continue;

                        var structureReferences = structureAccessor.getStructureReferences(chunk.getPos().getStartPos());
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

    public static boolean isVillageStructure(ServerWorld world, Structure structure) {
        if (world == null || structure == null) return false;

        try {
            var registryManager = world.getRegistryManager();
            if (registryManager == null) return false;

            var registry = registryManager.get(RegistryKeys.STRUCTURE);
            if (registry == null) return false;

            var identifier = registry.getId(structure);
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