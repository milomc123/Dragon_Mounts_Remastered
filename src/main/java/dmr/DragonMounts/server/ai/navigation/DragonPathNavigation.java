package dmr.DragonMounts.server.ai.navigation;

import dmr.DragonMounts.server.ai.navigation.DragonNodeEvaluator.Mode;
import dmr.DragonMounts.server.entity.TameableDragonEntity;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags.Fluids;
import org.jetbrains.annotations.Nullable;

public class DragonPathNavigation extends FlyingPathNavigation {
    protected final TameableDragonEntity dragon;

    public DragonPathNavigation(TameableDragonEntity dragon, Level level) {
        super(dragon, level);

        this.dragon = dragon;

        setMaxVisitedNodesMultiplier(1f);
    }

    private DragonNodeEvaluator dragonNodeEvaluator;

    @Override
    protected PathFinder createPathFinder(int pMaxVisitedNodes) {
        this.dragonNodeEvaluator = new DragonNodeEvaluator(mob);
        this.nodeEvaluator = dragonNodeEvaluator;
        return new PathFinder(this.nodeEvaluator, pMaxVisitedNodes);
    }

    @Override
    public boolean canCutCorner(PathType pathType) {
        return super.canCutCorner(pathType)
                || (pathType == PathType.WATER && dragonNodeEvaluator.getMode() == Mode.SWIM);
    }

    @Override
    protected Vec3 getTempMobPos() {
        return mob.position();
    }

    @Override
    protected double getGroundY(Vec3 p_217794_) {
        return dragonNodeEvaluator.getMode() == Mode.SWIM ? p_217794_.y : super.getGroundY(p_217794_);
    }

    private Set<BlockPos> failedTargets = Set.of();
    private BlockPos failedFrom;
    private long nextRetry;
    private int retryDelay = 10;
    private Path failedPath;
    private Mode failedMode = Mode.GROUND;
    private int failedAccuracy;
    private float failedRange;
    private int failedRegionOffset;
    private boolean failedOffsetUpward;
    private boolean failedCanFly;

    @Override
    protected @Nullable Path createPath(
            Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange) {
        if (this.path != null && !this.path.isDone() && targets.contains(getTargetPos())) return this.path;
        boolean swimming = dragon.getBreed() != null
                && dragon.getBreed().getImmunities().contains("drown")
                && targets.stream()
                        .anyMatch(pos -> dragon.level().getFluidState(pos).is(Fluids.WATER));
        Mode mode = swimming ? Mode.SWIM : dragon.isFlying() && dragon.canFly() ? Mode.FLIGHT : Mode.GROUND;
        long now = level.getGameTime();
        boolean sameFailure = targets.equals(failedTargets)
                && failedFrom != null
                && failedFrom.distSqr(mob.blockPosition()) <= 4
                && mode == failedMode
                && accuracy == failedAccuracy
                && followRange == failedRange
                && regionOffset == failedRegionOffset
                && offsetUpward == failedOffsetUpward
                && dragon.canFly() == failedCanFly;
        if (sameFailure && now < nextRetry) {
            return failedPath != null && !failedPath.isDone() ? failedPath : null;
        }
        boolean repeatedFailure = sameFailure;
        dragonNodeEvaluator.setMode(mode);
        Path result = search(targets, regionOffset, offsetUpward, accuracy, followRange);
        if (mode == Mode.GROUND && dragon.canFly() && (result == null || !result.canReach())) {
            // The vanilla cache must not return a ground path during the flight search.
            Path previous = this.path;
            this.path = null;
            dragonNodeEvaluator.setMode(Mode.FLIGHT);
            Path flight = search(targets, regionOffset, offsetUpward, accuracy, followRange);
            this.path = previous;
            if (flight != null
                    && (flight.canReach() || result == null || flight.getDistToTarget() < result.getDistToTarget())) {
                result = flight;
                mode = Mode.FLIGHT;
            }
            dragonNodeEvaluator.setMode(mode);
        }
        if (result == null || !result.canReach()) {
            retryDelay = repeatedFailure ? Math.min(80, retryDelay * 2) : 10;
            failedTargets = Set.copyOf(targets);
            failedFrom = mob.blockPosition();
            failedMode = mode;
            failedAccuracy = accuracy;
            failedRange = followRange;
            failedRegionOffset = regionOffset;
            failedOffsetUpward = offsetUpward;
            failedCanFly = dragon.canFly();
            failedPath = result;
            nextRetry = now + retryDelay + Math.floorMod(mob.getId(), 5);
        } else {
            failedTargets = Set.of();
            failedFrom = null;
            failedPath = null;
            retryDelay = 10;
        }
        return result;
    }

    private @Nullable Path search(
            Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange) {
        long started = System.nanoTime();
        try {
            return super.createPath(targets, regionOffset, offsetUpward, accuracy, followRange);
        } finally {
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                DragonPathfindingBudget.recordSearch(serverLevel, System.nanoTime() - started);
            }
        }
    }

    @Override
    public void recomputePath() {
        // A terrain update invalidates a previous negative result.
        nextRetry = 0;
        super.recomputePath();
    }

    public boolean isFlightPath() {
        return dragonNodeEvaluator.getMode() == Mode.FLIGHT;
    }

    @Override
    protected boolean canUpdatePath() {
        return true;
    }

    @Override
    protected boolean canMoveDirectly(Vec3 p_217796_, Vec3 p_217797_) {
        return (dragonNodeEvaluator.getMode() == Mode.SWIM
                && this.mob.isInLiquid()
                && isClearForMovementBetween(this.mob, p_217796_, p_217797_, true));
    }

    public boolean isStableDestination(BlockPos pPos) {
        if (dragonNodeEvaluator.getMode() == Mode.FLIGHT) {
            return this.level.getBlockState(pPos).isAir();
        }

        if (dragonNodeEvaluator.getMode() == Mode.SWIM) {
            return this.level.getFluidState(pPos).is(Fluids.WATER);
        }

        BlockPos blockpos = pPos.below();
        return this.level.getBlockState(blockpos).isSolidRender(this.level, blockpos);
    }
}
