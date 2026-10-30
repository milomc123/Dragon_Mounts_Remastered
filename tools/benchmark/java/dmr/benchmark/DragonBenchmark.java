package dmr.benchmark;

import dmr.DragonMounts.DMR;
import dmr.DragonMounts.registry.DragonBreedsRegistry;
import dmr.DragonMounts.registry.ModEntities;
import dmr.DragonMounts.registry.ModMemoryModuleTypes;
import dmr.DragonMounts.server.ai.navigation.DragonPathNavigation;
import dmr.DragonMounts.server.entity.DragonAgroState;
import dmr.DragonMounts.server.entity.TameableDragonEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in dedicated-server harness; excluded from normal builds. */
@EventBusSubscriber(modid = DMR.MOD_ID)
public final class DragonBenchmark {
    private static final int WARMUP = Integer.getInteger("dmr.benchmark.warmup", 100);
    private static final int SAMPLES = Integer.getInteger("dmr.benchmark.samples", 200);
    private static final int[] COUNTS = {1, 20, 100};
    private static final String[] SCENARIOS = {"idle", "reachable", "blocked"};
    private static final List<BenchmarkDragon> dragons = new ArrayList<>();
    private static final List<String> rows = new ArrayList<>();
    private static final long[] times = new long[SAMPLES];
    private static int phase = -1;
    private static int tick;
    private static long start;
    private static long requests, pathNanos, nullPaths, reachablePaths, arrivals, entityTicks;
    private static long searches, expandedNodes, searchNanos;
    private static boolean running;

    @SubscribeEvent
    public static void started(ServerStartedEvent event) throws Exception {
        if (!Boolean.getBoolean("dmr.benchmark")) return;
        ServerLevel level = event.getServer().overworld();
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
        level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(0, event.getServer());
        level.setDayTime(6000);
        for (int x = -2; x <= 9; x++) for (int z = -2; z <= 9; z++) {
            level.getChunk(x, z);
            level.setChunkForced(x, z, true);
        }
        rows.add("scenario,dragons,samples,mean_ms,p50_ms,p95_ms,p99_ms,path_requests,path_request_ms,null_paths,reachable_paths,arrival_ticks,entity_ticks,searches,expanded_nodes,search_ms");
        running = true;
        next(level);
    }

    private static void next(ServerLevel level) {
        for (BenchmarkDragon dragon : dragons) dragon.discard();
        dragons.clear();
        phase++;
        if (phase == COUNTS.length * SCENARIOS.length) return;
        int count = COUNTS[phase % COUNTS.length];
        String scenario = SCENARIOS[phase / COUNTS.length];
        for (int i = 0; i < 100; i++) {
            int x = (i % 10) * 12;
            int z = (i / 10) * 12;
            // A solid target volume prevents both walking and flying from reaching its centre.
            for (BlockPos pos : BlockPos.betweenClosed(x + 5, -60, z - 3, x + 11, -51, z + 3)) {
                level.setBlock(pos, scenario.equals("blocked") ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
        }
        for (int i = 0; i < count; i++) {
            BenchmarkDragon dragon = new BenchmarkDragon(level);
            dragon.setBreed(DragonBreedsRegistry.getDefault());
            dragon.setAge(0);
            dragon.getEntityData().set(TameableDragonEntity.healthAttribute, 0.5f);
            dragon.getEntityData().set(TameableDragonEntity.speedAttribute, 0.5f);
            dragon.getEntityData().set(TameableDragonEntity.damageAttribute, 0.5f);
            dragon.getEntityData().set(TameableDragonEntity.maxScaleAttribute, 0.5f);
            dragon.setTame(true, true);
            dragon.setOwnerUUID(new UUID(0, 1));
            dragon.setAgroState(DragonAgroState.PASSIVE);
            dragon.setPersistenceRequired();
            dragon.getRandom().setSeed(1000L + i);
            dragon.moveTo((i % 10) * 12 + 0.5, -60, (i / 10) * 12 + 0.5, 0, 0);
            level.addFreshEntity(dragon);
            if (scenario.equals("idle")) dragon.setOrderedToSit(true);
            dragons.add(dragon);
        }
        tick = 0;
        DMR.LOGGER.info("BENCHMARK phase={} scenario={} dragons={}", phase, scenario, count);
    }

    @SubscribeEvent
    public static void pre(ServerTickEvent.Pre event) {
        if (!running) return;
        String scenario = SCENARIOS[phase / COUNTS.length];
        for (int i = 0; i < dragons.size(); i++) {
            BenchmarkDragon dragon = dragons.get(i);
            dragon.getBrain().setMemory(ModMemoryModuleTypes.IDLE_TICKS.get(), 0);
            if (!scenario.equals("idle") && tick % 20 == i % 20) {
                int offset = scenario.equals("blocked") ? 8 : (tick / 100 % 2 == 0 ? 4 : -4);
                BlockPos target = new BlockPos((i % 10) * 12 + offset, -60, (i / 10) * 12);
                dragon.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 1.0F, 1));
            }
        }
        if (tick == WARMUP) {
            requests = pathNanos = nullPaths = reachablePaths = arrivals = entityTicks = 0;
            searches = expandedNodes = searchNanos = 0;
        }
        start = System.nanoTime();
    }

    @SubscribeEvent
    public static void post(ServerTickEvent.Post event) throws Exception {
        if (!running) return;
        long elapsed = System.nanoTime() - start;
        if (tick >= WARMUP) {
            times[tick - WARMUP] = elapsed;
            for (int i = 0; i < dragons.size(); i++) {
                int offset = SCENARIOS[phase / COUNTS.length].equals("blocked") ? 8 : (tick / 100 % 2 == 0 ? 4 : -4);
                if (dragons.get(i).distanceToSqr((i % 10) * 12 + offset + .5, -60, (i / 10) * 12 + .5) < 9) arrivals++;
            }
        }
        tick++;
        if (tick != WARMUP + SAMPLES) return;
        long[] sorted = times.clone();
        Arrays.sort(sorted);
        double mean = Arrays.stream(sorted).average().orElseThrow() / 1e6;
        String row = String.format(Locale.ROOT, "%s,%d,%d,%.6f,%.6f,%.6f,%.6f,%d,%.6f,%d,%d,%d,%d,%d,%d,%.6f",
                SCENARIOS[phase / COUNTS.length], dragons.size(), SAMPLES, mean,
                sorted[SAMPLES / 2] / 1e6, sorted[(int)(SAMPLES * .95)] / 1e6, sorted[(int)(SAMPLES * .99)] / 1e6,
                requests, pathNanos / 1e6, nullPaths, reachablePaths, arrivals, entityTicks, searches, expandedNodes, searchNanos / 1e6);
        rows.add(row);
        Files.write(Path.of("benchmark.csv"), rows);
        DMR.LOGGER.info("BENCHMARK result {}", row);
        next(event.getServer().overworld());
        if (phase == COUNTS.length * SCENARIOS.length) {
            running = false;
            event.getServer().halt(false);
        }
    }

    public static final class BenchmarkDragon extends TameableDragonEntity {
        BenchmarkDragon(Level level) { super(ModEntities.DRAGON_ENTITY.get(), level); }
        @Override protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
            return new TimedNavigation(this, level);
        }
        @Override public void tick() {
            if (running && tick >= WARMUP) entityTicks++;
            super.tick();
        }
    }

    public static final class TimedNavigation extends DragonPathNavigation {
        TimedNavigation(TameableDragonEntity dragon, Level level) { super(dragon, level); setCanFloat(true); }
        @Override protected net.minecraft.world.level.pathfinder.PathFinder createPathFinder(int maxNodes) {
            super.createPathFinder(maxNodes);
            var counted = new CountingEvaluator(nodeEvaluator);
            return new net.minecraft.world.level.pathfinder.PathFinder(counted, maxNodes) {
                @Override public net.minecraft.world.level.pathfinder.Path findPath(
                        net.minecraft.world.level.PathNavigationRegion region, net.minecraft.world.entity.Mob mob,
                        java.util.Set<BlockPos> targets, float range, int accuracy, float multiplier) {
                    long before = System.nanoTime();
                    var result = super.findPath(region, mob, targets, range, accuracy, multiplier);
                    if (running && tick >= WARMUP) {
                        searches++;
                        searchNanos += System.nanoTime() - before;
                    }
                    return result;
                }
            };
        }
        @Override public net.minecraft.world.level.pathfinder.Path createPath(BlockPos pos, int accuracy) {
            long before = System.nanoTime();
            var result = super.createPath(pos, accuracy);
            if (running && tick >= WARMUP) {
                requests++;
                pathNanos += System.nanoTime() - before;
                if (result == null) nullPaths++;
                else if (result.canReach()) reachablePaths++;
            }
            return result;
        }
    }
    private static final class CountingEvaluator extends net.minecraft.world.level.pathfinder.NodeEvaluator {
        private final net.minecraft.world.level.pathfinder.NodeEvaluator actual;
        CountingEvaluator(net.minecraft.world.level.pathfinder.NodeEvaluator actual) { this.actual = actual; }
        @Override public void prepare(net.minecraft.world.level.PathNavigationRegion region, net.minecraft.world.entity.Mob mob) { actual.prepare(region, mob); }
        @Override public void done() { actual.done(); }
        @Override public net.minecraft.world.level.pathfinder.Node getStart() { return actual.getStart(); }
        @Override public net.minecraft.world.level.pathfinder.Target getTarget(double x, double y, double z) { return actual.getTarget(x, y, z); }
        @Override public int getNeighbors(net.minecraft.world.level.pathfinder.Node[] output, net.minecraft.world.level.pathfinder.Node node) {
            if (running && tick >= WARMUP) expandedNodes++;
            return actual.getNeighbors(output, node);
        }
        @Override public net.minecraft.world.level.pathfinder.PathType getPathTypeOfMob(
                net.minecraft.world.level.pathfinder.PathfindingContext context, int x, int y, int z, net.minecraft.world.entity.Mob mob) {
            return actual.getPathTypeOfMob(context, x, y, z, mob);
        }
        @Override public net.minecraft.world.level.pathfinder.PathType getPathType(
                net.minecraft.world.level.pathfinder.PathfindingContext context, int x, int y, int z) {
            return actual.getPathType(context, x, y, z);
        }
    }

}
