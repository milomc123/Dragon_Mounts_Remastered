package dmr.DragonMounts.server.ai.sensors;

import dmr.DragonMounts.data.EntityTagProvider;
import dmr.DragonMounts.server.entity.DragonAgroState;
import dmr.DragonMounts.server.entity.TameableDragonEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.NearestVisibleLivingEntitySensor;
import net.minecraft.world.entity.ai.sensing.Sensor;

public class DragonAttackablesSensor extends NearestVisibleLivingEntitySensor {

    @Override
    protected boolean isMatchingEntity(LivingEntity attacker, LivingEntity target) {
        var dragon = (TameableDragonEntity) attacker;
        if (!isClose(attacker, target) || !Sensor.isEntityAttackable(attacker, target)) return false;
        var owner = dragon.getOwner();
        if (target.isAlliedTo(dragon)
                || dragon.isTame() && (owner == null || target == owner || target.isAlliedTo(owner))) return false;
        if (dragon.isTame() && dragon.getAgroState() != DragonAgroState.AGGRESSIVE) return false;

        // Only hunt non-tamed dragons from other spawn groups
        if (target instanceof TameableDragonEntity otherDragon) {
            if (java.util.Objects.equals(dragon.getSpawnGroupId(), otherDragon.getSpawnGroupId())) {
                return false;
            } else if (!otherDragon.isTame() && !dragon.isTame()) {
                return true;
            }
        }

        return target.getType()
                .is(
                        dragon.isTame()
                                ? EntityTagProvider.DRAGON_HUNTING_TARGET
                                : EntityTagProvider.WILD_DRAGON_HUNTING_TARGET);
    }

    private boolean isClose(LivingEntity attacker, LivingEntity target) {
        return target.distanceToSqr(attacker) <= (((TameableDragonEntity) attacker).isTame() ? 16.0D : 64.0D);
    }

    @Override
    protected MemoryModuleType<LivingEntity> getMemory() {
        return MemoryModuleType.NEAREST_ATTACKABLE;
    }
}
