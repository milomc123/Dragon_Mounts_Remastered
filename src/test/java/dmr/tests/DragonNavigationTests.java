package dmr.tests;

import dmr.DragonMounts.registry.DragonBreedsRegistry;
import dmr.DragonMounts.registry.ModEntities;
import dmr.DragonMounts.server.ai.navigation.DragonNodeEvaluator;
import dmr.DragonMounts.server.entity.TameableDragonEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.PathNavigationRegion;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

@PrefixGameTestTemplate(false)
@ForEachTest(groups = "DragonNavigation")
public class DragonNavigationTests {
    private static TameableDragonEntity dragon(ExtendedGameTestHelper helper) {
        var dragon = helper.spawn(ModEntities.DRAGON_ENTITY.get(), new BlockPos(3, 2, 3));
        dragon.setBreed(DragonBreedsRegistry.getDefault());
        dragon.setAge(0);
        dragon.setTame(true, true);
        dragon.setAgroState(dmr.DragonMounts.server.entity.DragonAgroState.PASSIVE);
        dragon.getEntityData().set(TameableDragonEntity.speedAttribute, .5f);
        dragon.setOnGround(true);
        return dragon;
    }

    @GameTest
    @TestHolder
    @EmptyTemplate(value = "16x8x16", floor = true)
    public static void immediatePathRequest(ExtendedGameTestHelper helper) {
        var dragon = dragon(helper);
        var path = dragon.getNavigation().createPath(dragon.blockPosition().offset(4, 0, 0), 1);
        helper.assertTrue(path != null && path.canReach(), "A newly spawned dragon must not fail due to a cooldown");
        helper.succeed();
    }

    @GameTest
    @TestHolder
    @EmptyTemplate(value = "16x8x16", floor = true)
    public static void entityAndBlockRequestsAgree(ExtendedGameTestHelper helper) {
        var dragon = dragon(helper);
        var other = helper.spawn(net.minecraft.world.entity.EntityType.PIG, new BlockPos(8, 2, 3));
        var entityPath = dragon.getNavigation().createPath(other, 1);
        var blockPath = dragon.getNavigation().createPath(other.blockPosition(), 1);
        helper.assertTrue(entityPath != null && blockPath != null, "Both path entry points must work immediately");
        helper.assertTrue(
                entityPath.canReach() == blockPath.canReach(), "Entry points must use the same movement mode");
        helper.succeed();
    }

    @GameTest
    @TestHolder
    @EmptyTemplate(value = "16x8x16", floor = true)
    public static void walkingEvaluatorReleasesContext(ExtendedGameTestHelper helper) {
        var dragon = dragon(helper);
        class InspectableEvaluator extends DragonNodeEvaluator {
            InspectableEvaluator() {
                super(dragon);
            }

            boolean released() {
                return currentContext == null && mob == null;
            }
        }
        var evaluator = new InspectableEvaluator();
        var pos = dragon.blockPosition();
        var region = new PathNavigationRegion(helper.getLevel(), pos.offset(-8, -4, -8), pos.offset(8, 4, 8));
        evaluator.prepare(region, dragon);
        helper.assertTrue(evaluator.getStart() != null, "Walking must provide its own start node");
        evaluator.done();
        helper.assertTrue(evaluator.released(), "Evaluator must release its world and entity references");
        helper.succeed();
    }

    @GameTest
    @TestHolder
    @EmptyTemplate(value = "16x8x16", floor = true)
    public static void sitCancelsMovementAndCombat(ExtendedGameTestHelper helper) {
        var dragon = dragon(helper);
        var pig = helper.spawn(net.minecraft.world.entity.EntityType.PIG, new BlockPos(8, 2, 3));
        dragon.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pig, 1, 1));
        dragon.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, pig);
        dragon.setOrderedToSit(true);
        helper.assertTrue(dragon.getNavigation().isDone(), "Sitting must cancel navigation");
        helper.assertTrue(
                !dragon.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET), "Sitting must clear movement");
        helper.assertTrue(
                !dragon.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET), "Sitting must clear combat");
        helper.succeed();
    }

    @GameTest
    @TestHolder
    @EmptyTemplate(value = "16x8x16", floor = true)
    public static void hatchlingNeverPlansFlight(ExtendedGameTestHelper helper) {
        var dragon = dragon(helper);
        dragon.setBaby(true);
        var navigation = (dmr.DragonMounts.server.ai.navigation.DragonPathNavigation) dragon.getNavigation();
        navigation.createPath(dragon.blockPosition().offset(4, 4, 0), 0);
        helper.assertTrue(!navigation.isFlightPath(), "A hatchling must not fall back to flight");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 100)
    @TestHolder
    @EmptyTemplate(value = "16x8x16", floor = true)
    public static void groundRouteMakesProgressWithoutFlying(ExtendedGameTestHelper helper) {
        var dragon = dragon(helper);
        var target = dragon.blockPosition().offset(5, 0, 0);
        double initialY = dragon.getY();
        helper.assertTrue(
                dragon.getNavigation().moveTo(target.getX() + .5, target.getY(), target.getZ() + .5, 1),
                "Ground route must start");
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(
                    dragon.distanceToSqr(target.getX() + .5, target.getY(), target.getZ() + .5) < 9,
                    "Dragon must make real progress along a ground route");
            helper.assertTrue(
                    !dragon.isFlying() && Math.abs(dragon.getY() - initialY) < .5,
                    "Walking must stay grounded: initialY=" + initialY + " y=" + dragon.getY() + " flying="
                            + dragon.isFlying() + " flightPath="
                            + ((dmr.DragonMounts.server.ai.navigation.DragonPathNavigation) dragon.getNavigation())
                                    .isFlightPath());
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 100)
    @TestHolder
    @EmptyTemplate(value = "16x12x16", floor = true)
    public static void elevatedTargetTriggersTakeoff(ExtendedGameTestHelper helper) {
        var dragon = dragon(helper);
        var target = dragon.blockPosition().offset(5, 5, 0);
        double initialY = dragon.getY();
        helper.assertTrue(
                dragon.getNavigation().moveTo(target.getX() + .5, target.getY(), target.getZ() + .5, 1),
                "A flight route must start");
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(dragon.getY() > initialY + 1, "Dragon must actually take off along a flight route");
            helper.succeed();
        });
    }

    @GameTest
    @TestHolder
    @EmptyTemplate(value = "16x8x16", floor = true)
    public static void groundRoutePreservesDetour(ExtendedGameTestHelper helper) {
        var dragon = dragon(helper);
        for (BlockPos pos : BlockPos.betweenClosed(7, 2, 0, 7, 6, 6)) {
            helper.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE);
        }
        var navigation = (dmr.DragonMounts.server.ai.navigation.DragonPathNavigation) dragon.getNavigation();
        var path = navigation.createPath(helper.absolutePos(new BlockPos(11, 2, 3)), 0);
        helper.assertTrue(path != null && path.canReach(), "A route around the wall must be found");
        helper.assertTrue(!navigation.isFlightPath(), "A reachable ground detour must not require flight");
        helper.assertTrue(path.getNextNodeIndex() == 0, "Required detour nodes must not be skipped");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 120)
    @TestHolder
    @EmptyTemplate(value = "16x8x16", floor = true)
    public static void failedRouteBacksOffAndRetries(ExtendedGameTestHelper helper) {
        var dragon = dragon(helper);
        dragon.setBaby(true);
        for (BlockPos pos : BlockPos.betweenClosed(7, 2, 2, 9, 6, 4)) {
            helper.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE);
        }
        class CountingNavigation extends dmr.DragonMounts.server.ai.navigation.DragonPathNavigation {
            int searches;

            CountingNavigation(TameableDragonEntity subject) {
                super(subject, helper.getLevel());
            }

            @Override
            protected net.minecraft.world.level.pathfinder.PathFinder createPathFinder(int maxNodes) {
                super.createPathFinder(maxNodes);
                return new net.minecraft.world.level.pathfinder.PathFinder(nodeEvaluator, maxNodes) {
                    @Override
                    public net.minecraft.world.level.pathfinder.Path findPath(
                            PathNavigationRegion region,
                            net.minecraft.world.entity.Mob mob,
                            java.util.Set<BlockPos> targets,
                            float range,
                            int accuracy,
                            float multiplier) {
                        searches++;
                        return super.findPath(region, mob, targets, range, accuracy, multiplier);
                    }
                };
            }
        }
        var navigation = new CountingNavigation(dragon);
        var target = helper.absolutePos(new BlockPos(8, 2, 3));
        var first = navigation.createPath(target, 0);
        helper.assertTrue(first == null || !first.canReach(), "The solid target must be unreachable");
        navigation.createPath(target, 0);
        helper.assertTrue(navigation.searches == 1, "An immediate failed-route retry must reuse the result");
        helper.runAfterDelay(90, () -> {
            navigation.createPath(target, 0);
            helper.assertTrue(navigation.searches == 2, "An unreachable target must eventually be searched again");
            helper.succeed();
        });
    }

    @GameTest
    @TestHolder
    @EmptyTemplate(value = "16x8x16", floor = true)
    public static void exhaustedBudgetPreservesWalkTarget(ExtendedGameTestHelper helper) {
        var dragon = dragon(helper);
        var level = helper.getLevel();
        dragon.getBrain()
                .setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(dragon.blockPosition().offset(5, 0, 0), 1, 1));
        dmr.DragonMounts.server.ai.navigation.DragonPathfindingBudget.recordSearch(level, 1_000_000_000L);
        var sink = new dmr.DragonMounts.server.ai.behaviours.DragonMoveToTargetSink();
        helper.assertTrue(
                !sink.tryStart(level, dragon, level.getGameTime()),
                "Planning must wait when the shared budget is exhausted");
        helper.assertTrue(
                dragon.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "Budget exhaustion must not masquerade as an unreachable target");
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(
                    dmr.DragonMounts.server.ai.navigation.DragonPathfindingBudget.hasTime(level),
                    "The shared budget must reset on the next level tick");
            helper.succeed();
        });
    }
}
