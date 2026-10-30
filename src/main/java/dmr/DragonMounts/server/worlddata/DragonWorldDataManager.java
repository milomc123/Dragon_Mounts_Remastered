package dmr.DragonMounts.server.worlddata;

import dmr.DragonMounts.config.ServerConfig;
import dmr.DragonMounts.server.entity.TameableDragonEntity;
import dmr.DragonMounts.server.worlddata.DragonWorldData.DragonHistory;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

public class DragonWorldDataManager {

    public static DragonWorldData getInstance(Level level) {
        return DragonWorldData.getInstance(level);
    }

    /** Moves the shared inventory only when a real cross-dimension transfer occurs. */
    public static void transferDragonInventory(Level source, Level destination, UUID uuid) {
        if (source == destination) return;
        var sourceData = getInstance(source);
        var inventory = sourceData.dragonInventories.remove(uuid);
        if (inventory == null) return;
        var destinationData = getInstance(destination);
        destinationData.dragonInventories.put(uuid, inventory);
        sourceData.setDirty();
        destinationData.setDirty();
    }

    /** Server-wide death records live in the Overworld; inventories remain dimension-local. */
    public static DragonWorldData getDeathData(Level level) {
        var server = level.getServer();
        if (server == null) throw new IllegalArgumentException("Death records require a server level");
        var global = getInstance(server.overworld());
        // Migrate valid records written by previous versions in other dimensions.
        for (var otherLevel : server.getAllLevels()) {
            if (otherLevel == server.overworld()) continue;
            var legacy = getInstance(otherLevel);
            if (legacy.deadDragons.isEmpty()) continue;
            for (var id : legacy.deadDragons) {
                int delay = legacy.deathDelay.getOrDefault(id, 0);
                int existing = global.deathDelay.getOrDefault(id, 0);
                global.deadDragons.add(id);
                global.deathDelay.put(id, delay < 0 || existing < 0 ? -1 : Math.max(delay, existing));
                global.deathMessages.putIfAbsent(id, legacy.deathMessages.getOrDefault(id, ""));
            }
            legacy.deadDragons.clear();
            legacy.deathDelay.clear();
            legacy.deathMessages.clear();
            legacy.setDirty();
            global.setDirty();
        }
        return global;
    }

    public static void clearDragonData(Level level, UUID uuid) {
        DragonWorldData data = getDeathData(level);
        data.deadDragons.remove(uuid);
        data.deathDelay.remove(uuid);
        data.deathMessages.remove(uuid);
        data.setDirty();
    }

    public static boolean isDragonDead(Level level, UUID uuid) {
        DragonWorldData data = getDeathData(level);
        return data.deadDragons.contains(uuid);
    }

    public static int getDeathDelay(Level level, UUID uuid) {
        DragonWorldData data = getDeathData(level);
        return data.deathDelay.getOrDefault(uuid, 0);
    }

    public static String getDeathMessage(Level level, UUID uuid) {
        DragonWorldData data = getDeathData(level);
        return data.deathMessages.getOrDefault(uuid, "");
    }

    public static void setDragonDead(TameableDragonEntity dragon, String message) {
        if (dragon.getOwnerUUID() == null) return;
        var level = dragon.level;
        DragonWorldData data = getDeathData(level);
        data.deadDragons.add(dragon.getDragonUUID());
        data.deathDelay.put(dragon.getDragonUUID(), ServerConfig.ALLOW_RESPAWN ? ServerConfig.RESPAWN_TIME * 20 : -1);
        data.deathMessages.put(dragon.getDragonUUID(), message);
        data.setDirty();
    }

    public static void addDragonHistory(TameableDragonEntity dragon) {
        var level = dragon.level;
        DragonWorldData data = DragonWorldData.getInstance(level);
        var compound = new CompoundTag();
        dragon.save(compound);
        var owner = dragon.getOwner();
        var playerName = owner != null ? owner.getName().getString() : "Unknown";
        var name = dragon.getName();
        data.dragonHistory.put(
                dragon.getDragonUUID(),
                new DragonWorldData.DragonHistory(
                        dragon.getDragonUUID(), System.currentTimeMillis(), playerName, name, compound));
        data.setDirty();
    }

    public static DragonHistory getDragonHistory(Level level, UUID uuid) {
        DragonWorldData data = DragonWorldData.getInstance(level);
        return data.dragonHistory.get(uuid);
    }
}
