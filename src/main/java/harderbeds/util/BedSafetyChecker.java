package harderbeds.util;

import harderbeds.Harderbeds;
import harderbeds.config.ModConfig;
import harderbeds.util.CheckBedLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

import java.util.List;


public class BedSafetyChecker {

    private static final int HORIZONTAL_SEARCH_RADIUS = 20;
    private static final int VERTICAL_SEARCH_RADIUS = 10;
    private static final int INNER_EXCLUSION_RADIUS = 3;
    private static final int MIN_LIGHT_LEVEL = 8;


    public static boolean isBedAllowed(Level world, BlockPos bedPos) {
        if (CheckBedLocation.isWithinVillageStructure(world, bedPos)) {
            if (Harderbeds.debug) System.out.println("[BedSafety] Beds within villages cannot be used.");
            return false;
        } else {
            return true;
        }
    }

    public static boolean isBedSafe(Level world, BlockPos bedPos, Player player) {
        if (world.isClientSide() || !(world instanceof ServerLevel serverWorld)) {
            return true;
        }
        if (serverWorld.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
            return true;
        }

        Holder<Biome> biomeEntry = world.getBiome(bedPos);
        if (biomeEntry.is(Biomes.MUSHROOM_FIELDS)) {
            if (Harderbeds.debug) System.out.println("[BedSafety] SAFE: Bed is in Mushroom Fields biome.");
            return true;
        }

        // --- Primary check: existing hostile mobs in the area ---
        // Fast — no dummy mob, just check already-loaded entities.
        // If any existing hostile mob can reach the bed, bail out immediately.
        if (existingHostileMobCanReachBed(serverWorld, bedPos, player)) {
            if (Harderbeds.debug) System.out.println("[BedSafety] UNSAFE: Existing hostile mob can reach the bed.");
            return false;
        }

        // --- Secondary check: potential spawn locations + dummy pathfinding ---
        // Only reached when no currently-loaded mob poses a threat.
        BlockPos potentialSpawnPos = findSpawnLocationAndPathing(serverWorld, bedPos, player);
        if (potentialSpawnPos != null) {
            if (Harderbeds.debug) System.out.println("[BedSafety] UNSAFE: Found a mob path to the player/bed from: " + potentialSpawnPos);
            return false;
        }

        if (Harderbeds.debug) System.out.println("[BedSafety] SAFE: No threats found near the bed.");
        return true;
    }


    // -------------------------------------------------------------------------
    // Primary check — existing hostile mobs
    // -------------------------------------------------------------------------

    private static boolean existingHostileMobCanReachBed(ServerLevel world, BlockPos bedPos, Player player) {
        AABB searchBox = new AABB(
                bedPos.getX() - HORIZONTAL_SEARCH_RADIUS,
                bedPos.getY() - VERTICAL_SEARCH_RADIUS,
                bedPos.getZ() - HORIZONTAL_SEARCH_RADIUS,
                bedPos.getX() + HORIZONTAL_SEARCH_RADIUS,
                bedPos.getY() + VERTICAL_SEARCH_RADIUS,
                bedPos.getZ() + HORIZONTAL_SEARCH_RADIUS
        );

        List<Mob> nearbyMobs = world.getEntitiesOfClass(Mob.class, searchBox,
                mob -> mob.isAlive() && mob instanceof net.minecraft.world.entity.monster.Monster);

        for (Mob mob : nearbyMobs) {
            Path path = mob.getNavigation().createPath(player, 0);
            if (isPathViable(path, bedPos, world)) {
                if (Harderbeds.debug) System.out.println("[BedSafety] Existing mob at " + mob.blockPosition() + " can reach the bed.");
                if (ModConfig.getSettings().isMobPathVisualizationEnabled()) {
                    spawnPathParticles(path, mob);
                }
                return true;
            }
        }
        return false;
    }


    // -------------------------------------------------------------------------
    // Secondary check — potential spawn locations + dummy pathfinding
    // -------------------------------------------------------------------------

    private static BlockPos findSpawnLocationAndPathing(ServerLevel world, BlockPos bedPos, Player player) {
        RandomSource random = world.getRandom();

        int horizontalRange = (HORIZONTAL_SEARCH_RADIUS * 2) + 1;
        int verticalRange   = (VERTICAL_SEARCH_RADIUS   * 2) + 1;

        // Build all (x, z) pairs as a flat list, then shuffle that list so pairs stay correlated.
        int totalPairs = horizontalRange * horizontalRange;
        int[] xzPairs = new int[totalPairs * 2];
        int idx = 0;
        for (int dx = -HORIZONTAL_SEARCH_RADIUS; dx <= HORIZONTAL_SEARCH_RADIUS; dx++) {
            for (int dz = -HORIZONTAL_SEARCH_RADIUS; dz <= HORIZONTAL_SEARCH_RADIUS; dz++) {
                xzPairs[idx++] = bedPos.getX() + dx;
                xzPairs[idx++] = bedPos.getZ() + dz;
            }
        }
        shufflePairs(xzPairs, totalPairs, random);

        // Search top-to-bottom to prefer higher spawn locations.
        int topY    = bedPos.getY() + VERTICAL_SEARCH_RADIUS;
        int bottomY = bedPos.getY() - VERTICAL_SEARCH_RADIUS;

        for (int y = topY; y >= bottomY; y--) {
            if (Math.abs(y - bedPos.getY()) <= INNER_EXCLUSION_RADIUS) continue;

            for (int p = 0; p < totalPairs; p++) {
                int x = xzPairs[p * 2];
                int z = xzPairs[p * 2 + 1];

                if (Math.abs(x - bedPos.getX()) <= INNER_EXCLUSION_RADIUS &&
                        Math.abs(z - bedPos.getZ()) <= INNER_EXCLUSION_RADIUS) {
                    continue;
                }

                BlockPos.MutableBlockPos testPos = new BlockPos.MutableBlockPos(x, y, z);

                if (!world.hasChunkAt(testPos)) continue;
                if (world.getBlockState(testPos).isAir()) continue;
                if (!world.getBlockState(testPos).isRedstoneConductor(world, testPos)) continue;

                BlockPos abovePos    = testPos.above();
                BlockPos twoAbovePos = testPos.above(2);

                if (!world.getBlockState(abovePos).isAir())    continue;
                if (!world.getBlockState(twoAbovePos).isAir()) continue;
                if (world.getBrightness(LightLayer.BLOCK, abovePos) >= MIN_LIGHT_LEVEL) continue;

                if (Harderbeds.debug) System.out.println("[SpawnLoc] Potential spawn at " + testPos +
                        " (dx=" + Math.abs(x - bedPos.getX()) +
                        ", dy=" + Math.abs(y - bedPos.getY()) +
                        ", dz=" + Math.abs(z - bedPos.getZ()) + ")");

                if (canMobReachBedViaDummy(world, testPos.immutable(), bedPos, player)) {
                    if (Harderbeds.debug) System.out.println("[BedSafety] UNSAFE: Dummy mob can reach bed from " + testPos);
                    return testPos.immutable();
                }
            }
        }
        return null;
    }


    // -------------------------------------------------------------------------
    // Dummy-mob pathfinding (secondary check only)
    // -------------------------------------------------------------------------

    private static boolean canMobReachBedViaDummy(ServerLevel world, BlockPos start, BlockPos target, Player player) {


        EntityType<Zombie> zombieType = (EntityType<Zombie>)
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                        .getValue(net.minecraft.resources.Identifier.withDefaultNamespace("zombie"));
        Mob dummyMob = new Zombie(zombieType, world);

        try {
            dummyMob.setSilent(true);
            dummyMob.setInvisible(true);
            dummyMob.getNavigation().setCanFloat(true);
            dummyMob.setPosRaw(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);

            // finalizeSpawn without adding to world — sets up internal state needed for navigation.
            dummyMob.finalizeSpawn(world, world.getCurrentDifficultyAt(start), EntitySpawnReason.COMMAND, null);

            dummyMob.setTarget(player);
            dummyMob.tick();
            dummyMob.tick();

            Path path = dummyMob.getNavigation().createPath(player, 0);

            if (path == null || path.getNodeCount() < 2) {
                if (Harderbeds.debug) System.out.println("[PathCheck] Path null or too short.");
                return false;
            }

            if (pathContainsBlockingDoor(path, world)) {
                if (Harderbeds.debug) System.out.println("[PathCheck] Path blocked by closed door.");
                return false;
            }

            boolean reachable = isPathViable(path, target, world);

            if (reachable && ModConfig.getSettings().isMobPathVisualizationEnabled()) {
                spawnPathParticles(path, dummyMob);
            }

            if (Harderbeds.debug) System.out.println("[PathCheck] Path reaches target: " + reachable);
            return reachable;

        } finally {
            // Always clean up the dummy mob, whether we return true or false.
            dummyMob.discard();
        }
    }


    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    /**
     * Determines whether a computed path is viable — either it explicitly reaches
     * the target, or its endpoint lands within 1 block² of the target.
     */
    private static boolean isPathViable(Path path, BlockPos target, Level world) {
        if (path == null || path.getNodeCount() < 2) return false;
        if (pathContainsBlockingDoor(path, world))   return false;
        if (path.canReach())                          return true;

        Node endNode = path.getEndNode();
        if (endNode == null) return false;
        BlockPos pathEnd = new BlockPos(endNode.x, endNode.y, endNode.z);
        return target.distSqr(pathEnd) <= 1;
    }

    private static boolean pathContainsBlockingDoor(Path path, Level world) {
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            BlockPos pos = new BlockPos(node.x, node.y, node.z);
            BlockState state = world.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock) {
                Boolean open = state.getValue(DoorBlock.OPEN);
                if (open == null || !open) return true;
            }
        }
        return false;
    }

    /**
     * Shuffles (x, z) coordinate pairs in the flat array without breaking pair correlation.
     * Each "pair" occupies two consecutive slots: [x0, z0, x1, z1, ...].
     */
    private static void shufflePairs(int[] array, int pairCount, RandomSource random) {
        for (int i = pairCount - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            // Swap pair i with pair j
            int ai = i * 2, aj = j * 2;
            int tmpX = array[ai], tmpZ = array[ai + 1];
            array[ai]     = array[aj];
            array[ai + 1] = array[aj + 1];
            array[aj]     = tmpX;
            array[aj + 1] = tmpZ;
        }
    }

    private static void spawnPathParticles(Path path, LivingEntity entity) {
        ServerLevel serverWorld = (ServerLevel) entity.level();

        Node endNode = path.getEndNode();
        BlockPos endPos = new BlockPos(endNode.x, endNode.y, endNode.z);
        serverWorld.sendParticles(ParticleTypes.FLAME,
                endPos.getX() + 0.5, endPos.getY() + 0.5, endPos.getZ() + 0.5,
                30, 0.2, 0.2, 0.2, 0.0);

        for (int i = 0; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            BlockPos nodePos = new BlockPos(node.x, node.y, node.z);
            serverWorld.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    nodePos.getX() + 0.5, nodePos.getY() + 0.5, nodePos.getZ() + 0.5,
                    20, 0.1, 0.1, 0.1, 0.0);
        }
    }
}