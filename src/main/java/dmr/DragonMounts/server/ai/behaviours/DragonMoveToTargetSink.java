package dmr.DragonMounts.server.ai.behaviours;

import dmr.DragonMounts.server.ai.navigation.DragonPathfindingBudget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;

/** Defers planning before vanilla can erase WALK_TARGET on a failed path request. */
public class DragonMoveToTargetSink extends MoveToTargetSink {
    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Mob owner) {
        return DragonPathfindingBudget.hasTime(level) && super.checkExtraStartConditions(level, owner);
    }

    @Override
    protected void tick(ServerLevel level, Mob owner, long gameTime) {
        // An existing route remains usable while its moving target waits for a replan.
        if (DragonPathfindingBudget.hasTime(level)) super.tick(level, owner, gameTime);
    }
}
