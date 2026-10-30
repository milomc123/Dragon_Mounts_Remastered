package dmr.DragonMounts.server.ai.navigation;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.*;

/** Uses one evaluator, including its node cache and lifecycle, for each search. */
public class DragonNodeEvaluator extends FlyNodeEvaluator {
    public enum Mode {
        GROUND,
        FLIGHT,
        SWIM
    }

    private Mode mode = Mode.GROUND;
    private final SwimNodeEvaluator swimming = new SwimNodeEvaluator(true);
    private final WalkNodeEvaluator walking = new WalkNodeEvaluator();

    public DragonNodeEvaluator(Mob mob) {}

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Mode getMode() {
        return mode;
    }

    private NodeEvaluator delegate() {
        return mode == Mode.SWIM ? swimming : walking;
    }

    @Override
    public void prepare(PathNavigationRegion level, Mob mob) {
        if (mode == Mode.FLIGHT) {
            super.prepare(level, mob);
        } else {
            delegate().setCanFloat(canFloat());
            delegate().setCanPassDoors(canPassDoors());
            delegate().setCanOpenDoors(canOpenDoors());
            delegate().prepare(level, mob);
        }
    }

    @Override
    public void done() {
        if (mode == Mode.FLIGHT) super.done();
        else delegate().done();
    }

    @Override
    public Node getStart() {
        return mode == Mode.FLIGHT ? super.getStart() : delegate().getStart();
    }

    @Override
    public Target getTarget(double x, double y, double z) {
        return mode == Mode.FLIGHT ? super.getTarget(x, y, z) : delegate().getTarget(x, y, z);
    }

    @Override
    public int getNeighbors(Node[] output, Node node) {
        return mode == Mode.FLIGHT
                ? super.getNeighbors(output, node)
                : delegate().getNeighbors(output, node);
    }

    @Override
    public PathType getPathTypeOfMob(PathfindingContext context, int x, int y, int z, Mob mob) {
        return mode == Mode.FLIGHT
                ? super.getPathTypeOfMob(context, x, y, z, mob)
                : delegate().getPathTypeOfMob(context, x, y, z, mob);
    }

    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        return mode == Mode.FLIGHT
                ? super.getPathType(context, x, y, z)
                : delegate().getPathType(context, x, y, z);
    }
}
