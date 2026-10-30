package dmr.tests;

import dmr.DMRTestConstants;
import dmr.DragonMounts.common.events.DragonWhistleEvent;
import dmr.DragonMounts.common.handlers.DragonWhistleHandler;
import dmr.DragonMounts.config.ServerConfig;
import dmr.DragonMounts.registry.DragonBreedsRegistry;
import dmr.DragonMounts.registry.ModCapabilities;
import dmr.DragonMounts.registry.ModEntities;
import dmr.DragonMounts.server.worlddata.DragonWorldData;
import dmr.DragonMounts.server.worlddata.DragonWorldDataManager;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

@PrefixGameTestTemplate(false)
@ForEachTest(groups = "Dragon Death Persistence")
public class DragonDeathPersistenceTests {
    @EmptyTemplate
    @GameTest
    @TestHolder
    public static void deathRecordsRoundTrip(ExtendedGameTestHelper helper) {
        var data = new DragonWorldData();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        data.deadDragons.add(first);
        data.deadDragons.add(second);
        data.deathDelay.put(first, 37);
        data.deathDelay.put(second, -1);
        data.deathMessages.put(first, "First death");
        data.deathMessages.put(second, "Permanent death");
        var tag = data.save(new CompoundTag(), helper.getLevel().registryAccess());
        // Count is redundant: records must be recovered from the list itself.
        tag.putInt("num", 0);
        var restored = DragonWorldData.load(tag, helper.getLevel().registryAccess());
        if (!restored.deadDragons.equals(data.deadDragons)
                || !restored.deathDelay.equals(data.deathDelay)
                || !restored.deathMessages.equals(data.deathMessages)) {
            helper.fail("Death identities, timers, or messages did not survive save/load");
        }
        var legacyRoot = new CompoundTag();
        legacyRoot.putUUID("uuid", first);
        legacyRoot.putInt("delay", 37);
        legacyRoot.putString("message", "Recoverable legacy death");
        var recovered = DragonWorldData.load(legacyRoot, helper.getLevel().registryAccess());
        if (!recovered.deadDragons.contains(first) || recovered.deathDelay.get(first) != 37) {
            helper.fail("Recoverable root-level death from an older save was lost");
        }
        helper.succeed();
    }

    @EmptyTemplate(floor = true)
    @GameTest
    @TestHolder
    public static void migratesDeathsAndPreservesExpiredRecords(ExtendedGameTestHelper helper) {
        var level = helper.getLevel();
        var nether = level.getServer().getLevel(Level.NETHER);
        var legacy = DragonWorldDataManager.getInstance(nether);
        var id = UUID.randomUUID();
        legacy.deadDragons.add(id);
        legacy.deathDelay.put(id, 2);
        legacy.deathMessages.put(id, "Legacy record");
        try {
            var global = DragonWorldDataManager.getDeathData(level);
            if (!DragonWorldDataManager.isDragonDead(nether, id) || !legacy.deadDragons.isEmpty()) {
                helper.fail("Dimension-local death was not migrated to the global store");
            }
            DragonWhistleEvent.onWorldTick(new LevelTickEvent.Post(() -> true, nether));
            if (global.deathDelay.get(id) != 2) helper.fail("Death timer ticked twice across dimensions");
            global.setDirty(false);
            DragonWhistleEvent.onWorldTick(new LevelTickEvent.Post(() -> true, level));
            if (global.deathDelay.get(id) != 1 || !global.isDirty()) {
                helper.fail("Death timer progress was not marked for saving");
            }
            for (int tick = 0; tick < 5; tick++) {
                DragonWhistleEvent.onWorldTick(new LevelTickEvent.Post(() -> true, level));
            }
            if (!DragonWorldDataManager.isDragonDead(level, id) || global.deathDelay.get(id) != 0) {
                helper.fail("Expired offline death was forgotten before the owner returned");
            }
        } finally {
            DragonWorldDataManager.clearDragonData(level, id);
        }
        helper.succeed();
    }

    @EmptyTemplate(floor = true)
    @GameTest
    @TestHolder
    public static void permanentDeathSurvivesConfigChangeAndOwnerReturn(ExtendedGameTestHelper helper) {
        var player = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        var dragon = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS);
        dragon.setBreed(DragonBreedsRegistry.getDefault());
        DragonWhistleHandler.setDragon(player, dragon, 0);
        var id = dragon.getDragonUUID();
        var oldAllow = ServerConfig.ALLOW_RESPAWN;
        try {
            ServerConfig.ALLOW_RESPAWN = false;
            DragonWorldDataManager.setDragonDead(dragon, "{\"text\":\"Offline death\"}");
            for (int tick = 0; tick < 25; tick++) {
                DragonWhistleEvent.onWorldTick(new LevelTickEvent.Post(() -> true, helper.getLevel()));
            }
            if (DragonWorldDataManager.getDeathDelay(helper.getLevel(), id) != -1) {
                helper.fail("Permanent offline death expired");
            }
            ServerConfig.ALLOW_RESPAWN = true;
            DragonWhistleEvent.onEntityJoinWorld(new EntityJoinLevelEvent(player, helper.getLevel()));
            var cap = player.getData(ModCapabilities.PLAYER_CAPABILITY);
            if (cap.dragonInstances.containsKey(0) || cap.dragonNBTs.containsKey(0) || cap.lastSummons.containsKey(0)) {
                helper.fail("A permanently dead dragon remained callable after login");
            }
        } finally {
            ServerConfig.ALLOW_RESPAWN = oldAllow;
            DragonWorldDataManager.clearDragonData(helper.getLevel(), id);
        }
        helper.succeed();
    }

    @EmptyTemplate(floor = true)
    @GameTest
    @TestHolder
    public static void ownerReceivesDeathFromAnotherDimension(ExtendedGameTestHelper helper) {
        var player = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        var dragon = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS);
        dragon.setBreed(DragonBreedsRegistry.getDefault());
        DragonWhistleHandler.setDragon(player, dragon, 1);
        var id = dragon.getDragonUUID();
        var legacy =
                DragonWorldDataManager.getInstance(helper.getLevel().getServer().getLevel(Level.NETHER));
        legacy.deadDragons.add(id);
        legacy.deathDelay.put(id, 51);
        legacy.deathMessages.put(id, "{\"text\":\"Nether death\"}");
        var oldAllow = ServerConfig.ALLOW_RESPAWN;
        try {
            ServerConfig.ALLOW_RESPAWN = true;
            DragonWhistleEvent.onEntityJoinWorld(new EntityJoinLevelEvent(player, helper.getLevel()));
            var cap = player.getData(ModCapabilities.PLAYER_CAPABILITY);
            if (cap.respawnDelays.getOrDefault(1, 0) != 51) {
                helper.fail("Login in Overworld missed the dragon's Nether death cooldown");
            }
            if (DragonWorldDataManager.isDragonDead(helper.getLevel(), id)) {
                helper.fail("Consumed death record was retained");
            }
        } finally {
            ServerConfig.ALLOW_RESPAWN = oldAllow;
            DragonWorldDataManager.clearDragonData(helper.getLevel(), id);
        }
        helper.succeed();
    }
}
