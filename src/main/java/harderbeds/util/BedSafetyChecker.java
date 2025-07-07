package harderbeds.util;

import harderbeds.Harderbeds;
import harderbeds.config.ModConfig;
import harderbeds.util.CheckBedLocation;

import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;



public class BedSafetyChecker {

    private static final int HORIZONTAL_SEARCH_RADIUS = 20; // x2
    private static final int VERTICAL_SEARCH_RADIUS = 10;    // x2
    private static final int INNER_EXCLUSION_RADIUS = 3;    // x2
    private static final int MIN_LIGHT_LEVEL = 8;


    public static boolean isBedAllowed(World world, BlockPos bedPos) {
        if (CheckBedLocation.isWithinVillageStructure(world, bedPos)){
            if(Harderbeds.debug) System.out.println("[BedSafety] Beds within villages cannot be used.");
            return false;
        } else {
            return true;
        }
    }

    public static boolean isBedSafe(World world, BlockPos bedPos, PlayerEntity player) {


        if (world.isClient || !(world instanceof ServerWorld serverWorld)) {
            return true;
        }
        if (serverWorld.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            return true;
        }


        RegistryEntry<Biome> biomeEntry = world.getBiome(bedPos);

        if (biomeEntry.matchesKey(BiomeKeys.MUSHROOM_FIELDS)) {
            if(Harderbeds.debug) System.out.println("[BedSafety] SAFE: Bed is in Mushroom Fields biome.");
            return true;
        }


        // Check for dangerous spawn locations (ones where mobs can reach the bed)
        BlockPos potentialSpawnPos = findSpawnLocationAndPathing(serverWorld, bedPos, player);

        if (potentialSpawnPos != null) {
            if(Harderbeds.debug) System.out.println("[BedSafety] UN-SAFE: Found a mob path to the player/bed from: " + potentialSpawnPos);
            return false;
        }
        if(Harderbeds.debug) System.out.println("[BedSafety] SAFE: No potential mob paths found to the bed.");
        return true;
    }


    private static BlockPos findSpawnLocationAndPathing(ServerWorld world, BlockPos bedPos, PlayerEntity player) {
        Random random = world.getRandom();

        // Calculate coordinate ranges
        int horizontalRange = (HORIZONTAL_SEARCH_RADIUS * 2) + 1; // e.g., 65 for radius 32
        int verticalRange = (VERTICAL_SEARCH_RADIUS * 2) + 1;     // e.g., 65 for radius 32

        // Create arrays for randomized X and Z coordinates
        int[] xCoords = new int[horizontalRange];
        int[] zCoords = new int[horizontalRange];

        // Fill coordinate arrays
        for (int i = 0; i < horizontalRange; i++) {
            xCoords[i] = bedPos.getX() - HORIZONTAL_SEARCH_RADIUS + i;
            zCoords[i] = bedPos.getZ() - HORIZONTAL_SEARCH_RADIUS + i;
        }

        // Search from top to bottom to prefer higher spawn locations
        int topY = bedPos.getY() + VERTICAL_SEARCH_RADIUS;
        int bottomY = bedPos.getY() - VERTICAL_SEARCH_RADIUS;

        for (int y = topY; y >= bottomY; y--) {
            // Skip inner exclusion zone vertically
            if (Math.abs(y - bedPos.getY()) <= INNER_EXCLUSION_RADIUS) {
                continue;
            }

            // Shuffle X and Z coordinates for this Y level to add randomness
            shuffleArray(xCoords, random);
            shuffleArray(zCoords, random);

            for (int x : xCoords) {
                // Skip inner exclusion zone horizontally (X axis)
                if (Math.abs(x - bedPos.getX()) <= INNER_EXCLUSION_RADIUS) {
                    continue;
                }

                for (int z : zCoords) {
                    // Skip inner exclusion zone horizontally (Z axis)
                    if (Math.abs(z - bedPos.getZ()) <= INNER_EXCLUSION_RADIUS) {
                        continue;
                    }

                    BlockPos.Mutable testPos = new BlockPos.Mutable(x, y, z);

                    // Skip if chunk not loaded for performance
                    if (!world.isChunkLoaded(testPos)) {
                        continue;
                    }

                    if (world.getBlockState(testPos).isAir()) {
                        continue;
                    }

                    // Check if current block is solid (potential spawn platform)
                    if (world.getBlockState(testPos).isSolidBlock(world, testPos)) {

                        BlockPos abovePos = testPos.up();
                        BlockPos twoAbovePos = testPos.up(2);

                        // Check if there are 2 air blocks above (allows mob spawning)
                        if (world.getBlockState(abovePos).isAir()) {

                            if (world.getBlockState(twoAbovePos).isAir()) {

                                // Check light level of the first air block above
                                if (world.getLightLevel(LightType.BLOCK, abovePos) < MIN_LIGHT_LEVEL) {
                                    if(Harderbeds.debug) System.out.println("[SpawnLoc] Found potential spawn location: " + testPos +
                                            " (distance from bed: X=" + Math.abs(x - bedPos.getX()) +
                                            ", Y=" + Math.abs(y - bedPos.getY()) +
                                            ", Z=" + Math.abs(z - bedPos.getZ()) + ")");

                                    // Check if mob can reach bed from this spawn location
                                    if (canMobReachBed(world, testPos.toImmutable(), bedPos, player)) {
                                        if(Harderbeds.debug) System.out.println("[BedSafety] UNSAFE: A mob can reach the bed from " + testPos);
                                        return testPos.toImmutable(); // Return the spawn location that can reach bed
                                    } else {
                                        if(Harderbeds.debug)  System.out.println("[SpawnLoc] Spawn location found but mob cannot reach bed, continuing search...");
                                        // Continue searching for other spawn locations
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }


    private static void shuffleArray(int[] array, Random random) {
        for (int i = array.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
    }

    // Unchanged method
    private static boolean canMobReachBed(ServerWorld world, BlockPos start, BlockPos target, PlayerEntity player) {
        MobEntity dummyMob = new ZombieEntity(EntityType.ZOMBIE, world);
        dummyMob.setSilent(true);
        dummyMob.setInvisible(true);
        dummyMob.getNavigation().setCanSwim(true);
        //dummyMob.getNavigation().setCanOpenDoors(false);

        dummyMob.setPos(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);

        // Initialize the mob properly in the world
        dummyMob.initialize(world, world.getLocalDifficulty(start), SpawnReason.COMMAND, null);

        //world.spawnEntity(dummyMob);

        dummyMob.setTarget(player);

            for (int i = 0; i < 2; i++) {
                dummyMob.tick();
            }

            Path path = dummyMob.getNavigation().findPathTo(player, 0);

            if (path == null) {
                if(Harderbeds.debug) System.out.println("[PathCheck] Path is null. Mob cannot reach.");
                return false;
            }

            if (path.getLength() < 2) {
                if(Harderbeds.debug) System.out.println("[PathCheck] Path is invalid. Lenght is 1");
                return false;
            }

            if (pathContainsBlockingDoor(path, world)) {
                if(Harderbeds.debug) System.out.println("[PathCheck] Path contains a closed door");
                return false;
            }

            if (path != null && path.reachesTarget()) {
                if (ModConfig.getSettings().isMobPathVisualizationEnabled()) {
                    spawnPathParticles(path, dummyMob);
                    if(Harderbeds.debug) System.out.println("[PathCheck] Path reaches target!");
                    return true;
                }
            }

        if (path != null) {

            PathNode endNode = path.getEnd();
            BlockPos pathEndPos = new BlockPos(endNode.x, endNode.y, endNode.z);

            double squaredDistance = target.getSquaredDistance(pathEndPos);

            if(squaredDistance <= 1) {

                if (ModConfig.getSettings().isMobPathVisualizationEnabled()) {
                    spawnPathParticles(path, dummyMob);
                }

                if(Harderbeds.debug) System.out.println("[PathCheck] Path is close enough to the target to reach!");
                return true;
            }

        }


        if (dummyMob != null) {
            dummyMob.discard();
            dummyMob = null;
        }
        if(Harderbeds.debug) System.out.println("Path reaches target: " + path.reachesTarget());
            return false;
    }


    private static boolean pathContainsBlockingDoor(Path path, World world) {
        for (int i = 0; i < path.getLength(); i++) {
            PathNode node = path.getNode(i);
            BlockPos pos = new BlockPos(node.x, node.y, node.z);
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock) {
                Boolean open = state.get(DoorBlock.OPEN);
                if (open == null || !open) {  // Door is closed (blocking)
                    return true;
                }
            }
        }
        return false;
    }


    private static void spawnPathParticles(Path path, LivingEntity entityworld){

        ServerWorld serverWorld = (ServerWorld) entityworld.getWorld();

        PathNode endNode = path.getEnd();
        BlockPos endPos = new BlockPos(
                (int) endNode.getPos().getX(),
                (int) endNode.getPos().getY(),
                (int) endNode.getPos().getZ());
        serverWorld.spawnParticles(
                ParticleTypes.FLAME,
                endPos.getX() + 0.5, endPos.getY() + 0.5, endPos.getZ() + 0.5,
                30, 0.2, 0.2, 0.2, 0.0
        );

        // Path nodes (blue) - soul fire flame particles
        int nodeCount = path.getLength();
        for (int i = 0; i < nodeCount; i++) {
            PathNode node = path.getNode(i);
            BlockPos nodePos = new BlockPos(
                    (int) node.getPos().getX(),
                    (int) node.getPos().getY(),
                    (int) node.getPos().getZ());
            serverWorld.spawnParticles(
                    ParticleTypes.SOUL_FIRE_FLAME,
                    nodePos.getX() + 0.5, nodePos.getY() + 0.5, nodePos.getZ() + 0.5,
                    20, 0.1, 0.1, 0.1, 0.0
            );
        }
    }
}