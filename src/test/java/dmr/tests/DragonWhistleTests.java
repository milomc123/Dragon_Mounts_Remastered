package dmr.tests;

import dmr.DMRTestConstants;
import dmr.DragonMounts.common.events.DragonWhistleEvent;
import dmr.DragonMounts.common.handlers.DragonWhistleHandler;
import dmr.DragonMounts.common.handlers.DragonWhistleHandler.DragonInstance;
import dmr.DragonMounts.config.ServerConfig;
import dmr.DragonMounts.network.packets.CompleteDataSync;
import dmr.DragonMounts.registry.DragonBreedsRegistry;
import dmr.DragonMounts.registry.ModCapabilities;
import dmr.DragonMounts.registry.ModEntities;
import dmr.DragonMounts.registry.ModItems;
import dmr.DragonMounts.server.entity.TameableDragonEntity;
import dmr.DragonMounts.server.inventory.DragonInventoryHandler;
import dmr.DragonMounts.server.items.DragonWhistleItem;
import dmr.DragonMounts.server.worlddata.DragonWorldDataManager;
import dmr.DragonMounts.util.PlayerStateUtils;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

@PrefixGameTestTemplate(false)
@ForEachTest(groups = "Dragon Whistles")
public class DragonWhistleTests {

    @EmptyTemplate(floor = true)
    @GameTest
    @TestHolder
    public static void cancelledSummonRestoresBindingAndInventory(ExtendedGameTestHelper helper) {
        var player = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        var dragon = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS);
        dragon.setBreed(DragonBreedsRegistry.getDefault());
        DragonWhistleHandler.setDragon(player, dragon, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, DragonWhistleItem.getWhistleItem(DyeColor.WHITE));
        var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        var moved = (TameableDragonEntity) dragon.changeDimension(new DimensionTransition(
                nether, new Vec3(100, 100, 100), Vec3.ZERO, 0, 0, true, DimensionTransition.DO_NOTHING));
        var id = moved.getDragonUUID();
        var sourceInventory = moved.getDragonInventory();
        sourceInventory.inventory.setItem(3, new ItemStack(Blocks.DIAMOND_BLOCK, 5));
        moved.discard();
        var sourceData = DragonWorldDataManager.getInstance(nether);
        var destinationData = DragonWorldDataManager.getInstance(helper.getLevel());
        var oldDestinationInventory = DragonInventoryHandler.getOrCreateInventory(helper.getLevel(), id);
        oldDestinationInventory.inventory.setItem(3, new ItemStack(Blocks.GOLD_BLOCK, 2));
        var cap = player.getData(ModCapabilities.PLAYER_CAPABILITY);
        var previous = cap.serializeNBT(helper.getLevel().registryAccess());
        var savedSnapshot = cap.dragonNBTs.get(0);
        var invalid = savedSnapshot.copy();
        invalid.putString("id", "minecraft:item");
        cap.dragonNBTs.put(0, invalid);
        if (DragonWhistleHandler.callDragon(player)) helper.fail("Invalid snapshot was summoned");
        cap.dragonNBTs.put(0, savedSnapshot);
        if (sourceData.dragonInventories.get(id) != sourceInventory
                || destinationData.dragonInventories.get(id) != oldDestinationInventory) {
            helper.fail("Failed reconstruction moved or overwrote inventory");
        }
        boolean[] observed = {false};
        Consumer<EntityJoinLevelEvent> reject = event -> {
            if (event.getEntity() instanceof TameableDragonEntity candidate && id.equals(candidate.getDragonUUID())) {
                observed[0] = true;
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(reject);
        try {
            if (DragonWhistleHandler.callDragon(player)) helper.fail("Cancelled spawn reported success");
            if (!observed[0]) helper.fail("Test did not reach the spawn event");
            if (!previous.equals(cap.serializeNBT(helper.getLevel().registryAccess()))) {
                helper.fail("Cancelled spawn changed binding, NBT, or summon ID");
            }
            if (sourceData.dragonInventories.get(id) != sourceInventory
                    || sourceInventory.inventory.getItem(3).getCount() != 5
                    || destinationData.dragonInventories.get(id) != oldDestinationInventory
                    || oldDestinationInventory.inventory.getItem(3).getCount() != 2) {
                helper.fail("Cancelled spawn did not restore both dimensions' inventories");
            }
            if (cap.lastCall != null) helper.fail("Cancelled summon consumed the cooldown");
        } finally {
            NeoForge.EVENT_BUS.unregister(reject);
        }
        if (!DragonWhistleHandler.callDragon(player)) helper.fail("Could not retry after cancelled summon");
        var replacement = DragonWhistleHandler.findDragon(player, 0);
        if (replacement == null
                || replacement.getDragonInventory() != sourceInventory
                || sourceData.dragonInventories.containsKey(id)) {
            helper.fail("Successful retry did not commit inventory transfer");
        }
        helper.succeed();
    }

    @EmptyTemplate
    @GameTest
    @TestHolder
    public static void completeSyncTargetsNamedPlayer(ExtendedGameTestHelper helper) {
        var player = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        var cap = player.getData(ModCapabilities.PLAYER_CAPABILITY);
        cap.dragonsHatched = 7;
        var tag = new CompoundTag();
        tag.putInt("dragonsHatched", 42);
        var foreignPacket = new CompleteDataSync(Integer.MAX_VALUE, tag);
        foreignPacket.handleClient(null, player);
        if (cap.dragonsHatched != 7) helper.fail("Another player's packet overwrote the recipient");
        var trackedPlayer = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        var trackedPacket = new CompleteDataSync(trackedPlayer.getId(), tag);
        trackedPacket.handleClient(null, player);
        if (cap.dragonsHatched != 7 || trackedPlayer.getData(ModCapabilities.PLAYER_CAPABILITY).dragonsHatched != 42) {
            helper.fail("Tracking snapshot was not applied exclusively to the named player");
        }
        var ownPacket = new CompleteDataSync(player.getId(), tag);
        ownPacket.handle(null, player);
        if (cap.dragonsHatched != 7) helper.fail("Server accepted client-supplied owner data");
        cap.lastSummons.put(0, UUID.randomUUID());
        ownPacket.handleClient(null, player);
        if (cap.dragonsHatched != 42 || !cap.lastSummons.isEmpty()) {
            helper.fail("Own snapshot did not replace capability state completely");
        }
        helper.succeed();
    }

    @EmptyTemplate(floor = true)
    @GameTest
    @TestHolder
    public static void unboundDragonCannotChangeWhiteBinding(ExtendedGameTestHelper helper) {
        var player = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        var bound = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS);
        bound.setBreed(DragonBreedsRegistry.getDefault());
        DragonWhistleHandler.setDragon(player, bound, 0);
        var unbound = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS.offset(3, 0, 0));
        unbound.setBreed(DragonBreedsRegistry.getDefault());
        unbound.tamedFor(player, true);
        if (DragonWhistleHandler.getDragonSummonIndex(player, unbound.getDragonUUID()) != -1) {
            helper.fail("Unbound dragon resolved to a real whistle slot");
        }
        var join = new EntityJoinLevelEvent(unbound, helper.getLevel());
        DragonWhistleEvent.onEntityJoinWorld(join);
        if (join.isCanceled()) helper.fail("Unbound dragon was rejected by another dragon's summon ID");
        var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        unbound.changeDimension(new DimensionTransition(
                nether, new Vec3(0, 100, 0), Vec3.ZERO, 0, 0, true, DimensionTransition.DO_NOTHING));
        var cap = player.getData(ModCapabilities.PLAYER_CAPABILITY);
        if (!cap.dragonInstances.get(0).getUUID().equals(bound.getDragonUUID())) {
            helper.fail("Unbound dragon's dimension transfer replaced the white whistle binding");
        }
        var allowRespawn = ServerConfig.ALLOW_RESPAWN;
        try {
            ServerConfig.ALLOW_RESPAWN = false;
            DragonWhistleEvent.onEntityDeath(
                    new LivingDeathEvent(unbound, unbound.damageSources().generic()));
            if (!cap.dragonInstances.get(0).getUUID().equals(bound.getDragonUUID())) {
                helper.fail("Unbound dragon's death cleared the white whistle binding");
            }
        } finally {
            ServerConfig.ALLOW_RESPAWN = allowRespawn;
        }
        helper.succeed();
    }

    @EmptyTemplate(floor = true)
    @GameTest
    @TestHolder
    public static void dimensionTransferPreservesInventory(ExtendedGameTestHelper helper) {
        var player = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        var dragon = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS);
        dragon.setBreed(DragonBreedsRegistry.getDefault());
        DragonWhistleHandler.setDragon(player, dragon, 1);
        var inventory = dragon.getDragonInventory();
        inventory.inventory.setItem(3, new ItemStack(Blocks.DIAMOND_BLOCK, 5));
        var source = helper.getLevel();
        var sourceData = DragonWorldDataManager.getInstance(source);
        var uuid = dragon.getDragonUUID();
        var same = (TameableDragonEntity) dragon.changeDimension(new DimensionTransition(
                source, dragon.position().add(2, 0, 0), Vec3.ZERO, 0, 0, true, DimensionTransition.DO_NOTHING));
        if (same.getDragonInventory() != inventory
                || same.getInventory().getItem(3).getCount() != 5) {
            helper.fail("Same-dimension teleport lost inventory");
        }
        var nether = source.getServer().getLevel(Level.NETHER);
        var targetData = DragonWorldDataManager.getInstance(nether);
        sourceData.setDirty(false);
        targetData.setDirty(false);
        var moved = (TameableDragonEntity) same.changeDimension(new DimensionTransition(
                nether, new Vec3(0, 100, 0), Vec3.ZERO, 0, 0, true, DimensionTransition.DO_NOTHING));
        if (moved == null
                || moved.getDragonInventory() != inventory
                || moved.getInventory().getItem(3).getCount() != 5) {
            helper.fail("Cross-dimension transfer lost inventory");
        }
        if (sourceData.dragonInventories.containsKey(uuid) || !sourceData.isDirty() || !targetData.isDirty()) {
            helper.fail("Inventory transfer did not persist removal and insertion");
        }
        // A stale source must never overwrite an inventory that already reached the destination.
        DragonWorldDataManager.transferDragonInventory(source, nether, uuid);
        if (moved.getDragonInventory() != inventory) helper.fail("Missing source overwrote destination inventory");
        var cap = player.getData(ModCapabilities.PLAYER_CAPABILITY);
        if (!cap.dragonInstances.get(1).getDimension().equals("minecraft:the_nether")) {
            helper.fail("Binding location was not updated after transfer");
        }
        moved.discard();
        targetData.dragonInventories.remove(uuid);
        targetData.setDirty();
        helper.succeed();
    }

    @EmptyTemplate(floor = true)
    @GameTest
    @TestHolder
    public static void unavailableDragonUsesSnapshotAndRejectsOldEntity(ExtendedGameTestHelper helper) {
        var player = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        var dragon = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS);
        dragon.setBreed(DragonBreedsRegistry.getDefault());
        DragonWhistleHandler.setDragon(player, dragon, 1);
        var oldId = dragon.getUUID();
        var persistentId = dragon.getDragonUUID();
        var snapshot = player.getData(ModCapabilities.PLAYER_CAPABILITY)
                .dragonNBTs
                .get(1)
                .copy();
        dragon.discard();
        player.setItemInHand(InteractionHand.MAIN_HAND, DragonWhistleItem.getWhistleItem(DyeColor.ORANGE));
        if (!DragonWhistleHandler.callDragon(player)) helper.fail("Snapshot summon failed");
        var replacement = DragonWhistleHandler.findDragon(player, 1);
        if (replacement == null
                || replacement.getUUID().equals(oldId)
                || !replacement.getDragonUUID().equals(persistentId)) {
            helper.fail("Snapshot summon did not retain persistent identity with a new entity ID");
        }
        var oldEntity = ModEntities.DRAGON_ENTITY.get().create(helper.getLevel());
        oldEntity.load(snapshot);
        var join = new EntityJoinLevelEvent(oldEntity, helper.getLevel());
        DragonWhistleEvent.onEntityJoinWorld(join);
        if (!join.isCanceled()) helper.fail("Obsolete entity was accepted after snapshot reconstruction");
        if (DragonWhistleHandler.findDragon(player, -1) != null) helper.fail("Invalid binding resolved a dragon");
        helper.succeed();
    }

    /**
     * Tests the DragonWhistleHandler.getDragonWhistleItem method.
     *
     * <p>
     * This test verifies that: 1. When a player has no dragon whistle, the method
     * returns null 2. When a player has a dragon whistle in their main hand and the
     * corresponding dragon data, the method correctly returns the whistle item
     *
     * @param helper
     *               The game test helper
     */
    @EmptyTemplate(floor = true)
    @GameTest
    @TestHolder
    public static void findWhistleItem(ExtendedGameTestHelper helper) {
        var player = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        player.moveToCentre();

        // Test with no whistle
        var whistleItem = DragonWhistleHandler.getDragonWhistleItem(player);
        if (whistleItem != null) {
            helper.fail("Found whistle when none should exist");
        }

        // Test with whistle in main hand
        for (var whistle : ModItems.DRAGON_WHISTLES.values()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(whistle.get()));

            // Setup player capability to recognize this whistle
            var cap = player.getData(ModCapabilities.PLAYER_CAPABILITY);
            cap.setPlayerInstance(player);
            var handler = PlayerStateUtils.getHandler(player);
            handler.dragonNBTs.put(
                    ((DragonWhistleItem) whistle.get()).getColor().getId(), new CompoundTag());
            handler.dragonInstances.put(
                    ((DragonWhistleItem) whistle.get()).getColor().getId(),
                    new DragonInstance(player.level, UUID.randomUUID(), UUID.randomUUID()));

            whistleItem = DragonWhistleHandler.getDragonWhistleItem(player);
            if (whistleItem == null) {
                helper.fail("Could not find whistle in main hand");
            }
        }

        helper.succeed();
    }

    /**
     * Tests the DragonWhistleHandler.getDragonSummonIndex method.
     *
     * <p>
     * This test verifies that: 1. When a player has no dragon whistle, the method
     * returns -1 2. When a player has a dragon whistle in their main hand and the
     * corresponding dragon data, the method correctly returns the whistle's color
     * ID
     *
     * @param helper
     *               The game test helper
     */
    @EmptyTemplate(floor = true)
    @GameTest
    @TestHolder
    public static void getWhistleIndex(ExtendedGameTestHelper helper) {
        var player = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        player.moveToCentre();

        // Test with no whistle
        var index = DragonWhistleHandler.getDragonSummonIndex(player);
        if (index != -1) {
            helper.fail("Found whistle index when none should exist");
        }

        // Test with whistle in main hand
        for (var whistle : ModItems.DRAGON_WHISTLES.values()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(whistle.get()));

            // Setup player capability to recognize this whistle
            player.getData(ModCapabilities.PLAYER_CAPABILITY).setPlayerInstance(player);
            var handler = PlayerStateUtils.getHandler(player);
            int whistleId = ((DragonWhistleItem) whistle.get()).getColor().getId();
            handler.dragonNBTs.put(whistleId, new CompoundTag());
            handler.dragonInstances.put(
                    whistleId, new DragonInstance(player.level, UUID.randomUUID(), UUID.randomUUID()));

            index = DragonWhistleHandler.getDragonSummonIndex(player);
            if (index != whistleId) {
                helper.fail("Incorrect whistle index: expected " + whistleId + " but got " + index);
            }
        }

        helper.succeed();
    }

    /**
     * Tests the DragonWhistleHandler.setDragon method.
     *
     * <p>
     * This test verifies that: 1. When a dragon is set to a whistle index, the
     * dragon instance is properly stored in the player's capability 2. The stored
     * dragon instance has the correct UUID matching the original dragon
     *
     * @param helper
     *               The game test helper
     */
    @EmptyTemplate(floor = true)
    @GameTest
    @TestHolder
    public static void bindDragonToWhistle(ExtendedGameTestHelper helper) {
        var player = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        player.moveToCentre();
        var dragon = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS);
        dragon.setBreed(DragonBreedsRegistry.getDefault());

        // Set dragon to whistle
        DragonWhistleHandler.setDragon(player, dragon, 1);

        // Verify dragon is set
        if (!player.getData(ModCapabilities.PLAYER_CAPABILITY).dragonInstances.containsKey(1)) {
            helper.fail("Dragon instance not found in player capability");
        }

        var instance = player.getData(ModCapabilities.PLAYER_CAPABILITY)
                .dragonInstances
                .get(1);
        if (instance == null) {
            helper.fail("Dragon instance is null");
        }

        if (!instance.getUUID().equals(dragon.getDragonUUID())) {
            helper.fail("Dragon UUID mismatch");
        }

        helper.succeed();
    }

    /**
     * Tests the DragonWhistleHandler.canCall method when a player has no whistle.
     *
     * <p>
     * This test verifies that: 1. When a player has no dragon whistle (index = -1),
     * the canCall method returns false
     *
     * @param helper
     *               The game test helper
     */
    @EmptyTemplate(floor = true)
    @GameTest
    @TestHolder
    public static void cannotCallWithoutWhistle(ExtendedGameTestHelper helper) {
        var player = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        player.moveToCentre();

        // Test with no whistle
        boolean canCall = DragonWhistleHandler.canCall(player, -1);
        if (canCall) {
            helper.fail("Player can call dragon with no whistle");
        }

        helper.succeed();
    }

    /**
     * Tests the DragonWhistleHandler.canCall method when a player has a whistle but
     * no dragon.
     *
     * <p>
     * This test verifies that: 1. When a player has a dragon whistle but no
     * associated dragon, the canCall method returns false
     *
     * @param helper
     *               The game test helper
     */
    @EmptyTemplate(floor = true)
    @GameTest
    @TestHolder
    public static void cannotCallWithoutDragon(ExtendedGameTestHelper helper) {
        var player = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        player.moveToCentre();

        // Test with whistle but no dragon
        boolean canCall = DragonWhistleHandler.canCall(player, 1);
        if (canCall) {
            helper.fail("Player can call dragon when no dragon exists");
        }

        helper.succeed();
    }

    /**
     * Tests the DragonWhistleHandler.findDragon method.
     *
     * <p>
     * This test verifies that: 1. When a dragon is set to a whistle index, the
     * findDragon method can locate the dragon 2. The found dragon has the correct
     * UUID matching the original dragon
     *
     * @param helper
     *               The game test helper
     */
    @EmptyTemplate(floor = true)
    @GameTest
    @TestHolder
    public static void locateBoundDragon(ExtendedGameTestHelper helper) {
        var player = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        player.moveToCentre();
        var dragon = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS);
        dragon.setBreed(DragonBreedsRegistry.getDefault());

        // Set dragon to whistle
        DragonWhistleHandler.setDragon(player, dragon, 1);

        // Find dragon
        var foundDragon = DragonWhistleHandler.findDragon(player, 1);
        if (foundDragon == null) {
            helper.fail("Could not find dragon");
        }

        if (!foundDragon.getDragonUUID().equals(dragon.getDragonUUID())) {
            helper.fail("Found wrong dragon");
        }

        helper.succeed();
    }

    /**
     * Tests the DragonWhistleHandler.callDragon method.
     *
     * <p>
     * This test verifies that: 1. When a player has a dragon whistle and an
     * associated dragon, the callDragon method returns true 2. The dragon can be
     * successfully called using the whistle
     *
     * @param helper
     *               The game test helper
     */
    @EmptyTemplate
    @GameTest
    @TestHolder
    public static void callBoundDragon(ExtendedGameTestHelper helper) {
        var player = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);

        var dragon = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS);
        dragon.setBreed(DragonBreedsRegistry.getDefault());

        // Set dragon to whistle
        DragonWhistleHandler.setDragon(player, dragon, 1);

        // Setup player capability to recognize this whistle
        var cap = player.getData(ModCapabilities.PLAYER_CAPABILITY);
        cap.setPlayerInstance(player);
        var handler = PlayerStateUtils.getHandler(player);

        // Add a whistle to player's hand
        for (var whistle : ModItems.DRAGON_WHISTLES.values()) {
            int whistleId = ((DragonWhistleItem) whistle.get()).getColor().getId();
            if (whistleId == 1) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(whistle.get()));
                handler.dragonInstances.put(
                        whistleId, new DragonInstance(player.level, dragon.getUUID(), dragon.getDragonUUID()));
                handler.dragonNBTs.put(whistleId, new CompoundTag());
                break;
            }
        }

        // Call dragon
        boolean result = DragonWhistleHandler.callDragon(player);
        if (!result) {
            helper.fail("Failed to call dragon");
        }

        helper.succeed();
    }

    /**
     * Tests the DragonWhistleHandler.summonDragon method.
     *
     * <p>
     * This test verifies that: 1. When a player has a dragon whistle and an
     * associated dragon, the summonDragon method updates the lastCall timestamp 2.
     * The lastCall timestamp is different after summoning the dragon
     *
     * @param helper
     *               The game test helper
     */
    @EmptyTemplate
    @GameTest
    @TestHolder
    public static void summonUpdatesLastCall(ExtendedGameTestHelper helper) {
        var player = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);

        var dragon = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS);
        dragon.setBreed(DragonBreedsRegistry.getDefault());

        // Set dragon to whistle
        DragonWhistleHandler.setDragon(player, dragon, 1);

        // Setup player capability to recognize this whistle
        var cap = player.getData(ModCapabilities.PLAYER_CAPABILITY);
        cap.setPlayerInstance(player);
        var handler = PlayerStateUtils.getHandler(player);

        // Add a whistle to player's hand
        for (var whistle : ModItems.DRAGON_WHISTLES.values()) {
            int whistleId = ((DragonWhistleItem) whistle.get()).getColor().getId();

            if (whistleId == 1) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(whistle.get()));
                handler.dragonInstances.put(
                        whistleId, new DragonInstance(player.level, dragon.getUUID(), dragon.getDragonUUID()));
                handler.dragonNBTs.put(whistleId, new CompoundTag());
                break;
            }
        }

        // Record the last call time before summoning
        Long lastCallBefore = handler.lastCall;

        // Summon dragon
        DragonWhistleHandler.summonDragon(player);

        // Verify that lastCall was updated
        if (Objects.equals(handler.lastCall, lastCallBefore)) {
            helper.fail("Last call was not updated");
        }

        helper.succeed();
    }

    /**
     * Tests that a dragon follows the player when called with a whistle.
     *
     * <p>
     * This test verifies that:
     * 1. A dragon can be tamed and bound to a whistle
     * 2. When called with a whistle, the dragon will follow the player
     * 3. The dragon will teleport to the player if it's too far away
     *
     * @param helper The game test helper
     */
    @EmptyTemplate(floor = true)
    @GameTest
    @TestHolder
    public static void dragonFollowsWhenCalled(ExtendedGameTestHelper helper) {
        var player = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        player.moveToCentre();

        // Spawn and tame a dragon
        var dragon = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS);
        dragon.setBreed(DragonBreedsRegistry.getDefault());
        dragon.tamedFor(player, true);

        // Set dragon to whistle
        DragonWhistleHandler.setDragon(player, dragon, 1);

        // Setup player capability to recognize this whistle
        var cap = player.getData(ModCapabilities.PLAYER_CAPABILITY);
        cap.setPlayerInstance(player);
        var handler = PlayerStateUtils.getHandler(player);

        // Add a whistle to player's hand
        for (var whistle : ModItems.DRAGON_WHISTLES.values()) {
            int whistleId = ((DragonWhistleItem) whistle.get()).getColor().getId();

            if (whistleId == 1) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(whistle.get()));
                break;
            }
        }

        // Move dragon far away
        dragon.setPos(dragon.getX() + 5, dragon.getY(), dragon.getZ() + 5);

        // Call the dragon
        boolean result = DragonWhistleHandler.callDragon(player);
        if (!result) {
            helper.fail("Failed to call dragon");
        }

        dragon = DragonWhistleHandler.findDragon(player, 1);

        // Tick entities to allow the dragon to respond
        for (int i = 0; i < 20; i++) {
            player.tick();
            dragon.tick();
        }

        // Verify the dragon is now close to the player
        if (dragon.position().distanceTo(player.position()) > 10) {
            helper.fail("Dragon did not teleport to player when called");
        }

        helper.succeed();
    }

    /**
     * Tests that multiple dragons can be called with different whistles.
     *
     * <p>
     * This test verifies that:
     * 1. Multiple dragons can be tamed and bound to different whistles
     * 2. Each dragon can be called using its specific whistle
     * 3. The correct dragon responds to each whistle
     *
     * @param helper The game test helper
     */
    // TODO Fix this test
    // @EmptyTemplate(floor = true)
    // @GameTest
    // @TestHolder
    public static void callMultipleDragons(ExtendedGameTestHelper helper) {
        var player = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        player.moveToCentre();

        // Spawn and tame multiple dragons
        var dragon1 = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS);
        var dragon2 = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS.offset(2, 0, 0));

        dragon1.setBreed(DragonBreedsRegistry.getDefault());
        dragon2.setBreed(DragonBreedsRegistry.getDefault());

        dragon1.tamedFor(player, true);
        dragon2.tamedFor(player, true);

        // Setup player capability
        var cap = player.getData(ModCapabilities.PLAYER_CAPABILITY);
        cap.setPlayerInstance(player);

        // Set dragons to different whistles
        DragonWhistleHandler.setDragon(player, dragon1, 0);
        DragonWhistleHandler.setDragon(player, dragon2, 1);

        // Move dragons far away
        dragon1.setPos(dragon1.getX() + 50, dragon1.getY(), dragon1.getZ() + 50);
        dragon2.setPos(dragon2.getX() - 50, dragon2.getY(), dragon2.getZ() - 50);

        dragon1.setOrderedToSit(true);
        dragon2.setOrderedToSit(true);

        // Disable ai to prevent dragons from moving when not meant to
        dragon1.setNoAi(true);
        dragon2.setNoAi(true);

        // Make sure falling doesnt impact the test result
        dragon1.setNoGravity(true);
        dragon2.setNoGravity(true);

        // Store original positions
        var dragon1OrigPos = dragon1.position();
        var dragon2OrigPos = dragon2.position();

        // Add whistle 0 to player's hand
        for (var whistle : ModItems.DRAGON_WHISTLES.values()) {
            int whistleId = ((DragonWhistleItem) whistle.get()).getColor().getId();

            if (whistleId == 0) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(whistle.get()));
                break;
            }
        }

        // Call dragon1 using whistle 0
        boolean callResult = DragonWhistleHandler.callDragon(player);

        if (!callResult) {
            helper.fail("Failed to call dragon1 with whistle 0");
        }

        dragon1 = DragonWhistleHandler.findDragon(player, 0);

        // Tick entities to allow the dragon to respond
        for (int i = 0; i < 20; i++) {
            player.tick();
            dragon1.tick();
            dragon2.tick();
        }

        // Verify dragon1 moved toward the player
        if (dragon1.position().distanceTo(player.position()) > 10) {
            helper.fail("Dragon1 did not teleport to player when called with whistle 0");
        }

        // Verify dragon2 stayed in place
        if (dragon2.position().distanceTo(dragon2OrigPos) > 5) {
            helper.fail("Dragon2 moved when whistle 0 was used");
        }

        // Move dragons far away again
        dragon1.setPos(dragon1.getX() + 50, dragon1.getY(), dragon1.getZ() + 50);
        dragon2.setPos(dragon2.getX() - 50, dragon2.getY(), dragon2.getZ() - 50);

        // Update original positions
        dragon1OrigPos = dragon1.position();

        // Add whistle 1 to player's hand
        for (var whistle : ModItems.DRAGON_WHISTLES.values()) {
            int whistleId = ((DragonWhistleItem) whistle.get()).getColor().getId();

            if (whistleId == 1) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(whistle.get()));
                break;
            }
        }

        // Call dragon2 using whistle 1
        callResult = DragonWhistleHandler.callDragon(player);

        if (!callResult) {
            helper.fail("Failed to call dragon2 with whistle 1");
        }

        dragon2 = DragonWhistleHandler.findDragon(player, 1);

        // Tick entities to allow the dragon to respond
        for (int i = 0; i < 20; i++) {
            player.tick();
            dragon1.tick();
            dragon2.tick();
        }

        // Verify dragon2 moved toward the player
        if (dragon2.position().distanceTo(player.position()) > 10) {
            helper.fail("Dragon2 did not teleport to player when called with whistle 1");
        }

        // Verify dragon1 stayed in place
        if (dragon1.position().distanceTo(dragon1OrigPos) > 5) {
            helper.fail("Dragon1 moved when whistle 1 was used");
        }

        helper.succeed();
    }

    /**
     * Tests that a dragon can be called across dimensions.
     *
     * <p>
     * This test verifies that:
     * 1. A dragon can be tamed and bound to a whistle
     * 2. When the dragon is in a different dimension, it can still be called
     * 3. The dragon's inventory is transferred correctly between dimensions
     *
     * @param helper The game test helper
     */
    @EmptyTemplate(floor = true)
    @GameTest(timeoutTicks = 400)
    @TestHolder
    public static void callAcrossDimensions(ExtendedGameTestHelper helper) {
        var player = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        player.moveToCentre();

        // Spawn and tame a dragon
        var dragon = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS);
        dragon.setBreed(DragonBreedsRegistry.getDefault());
        dragon.tamedFor(player, true);

        // Set dragon to whistle
        DragonWhistleHandler.setDragon(player, dragon, 0);

        // Setup player capability
        var cap = player.getData(ModCapabilities.PLAYER_CAPABILITY);
        cap.setPlayerInstance(player);

        // Add a whistle to player's hand
        for (var whistle : ModItems.DRAGON_WHISTLES.values()) {
            int whistleId = ((DragonWhistleItem) whistle.get()).getColor().getId();

            if (whistleId == 0) {
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(whistle.get()));
                break;
            }
        }

        var netherDim = helper.getLevel().getServer().getLevel(Level.NETHER);
        var chunkPos = new ChunkPos(0, 0);
        var destination = new BlockPos(8, 100, 8);
        boolean wasForced = netherDim.getForcedChunks().contains(chunkPos.toLong());
        netherDim.setChunkForced(0, 0, true);
        helper.testInfo.addListener(new GameTestListener() {
            private void cleanup() {
                if (!wasForced) netherDim.setChunkForced(0, 0, false);
            }

            @Override
            public void testStructureLoaded(GameTestInfo testInfo) {}

            @Override
            public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
                cleanup();
            }

            @Override
            public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
                cleanup();
            }

            @Override
            public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
                cleanup();
            }
        });
        dragon.setNoGravity(true);
        dragon.setNoAi(true);
        var entityId = dragon.getUUID();
        var dragonId = dragon.getDragonUUID();

        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (!netherDim.areEntitiesLoaded(chunkPos.toLong())
                            || !netherDim.isPositionEntityTicking(destination)) {
                        helper.fail("Waiting for the destination chunk's entities to load and tick");
                    }
                })
                .thenExecute(() -> {
                    var dimensionTransition = new DimensionTransition(
                            netherDim, new Vec3(8, 100, 8), Vec3.ZERO, 0, 0, true, DimensionTransition.DO_NOTHING);
                    var netherDragon = (TameableDragonEntity) dragon.changeDimension(dimensionTransition);
                    if (netherDragon == null) helper.fail("Dragon failed to transfer to the Nether");
                    netherDragon.equipChest(new ItemStack(Blocks.CHEST), SoundSource.MASTER);
                    netherDragon.getInventory().setItem(0, new ItemStack(ModItems.DRAGON_ARMOR.get(), 1));
                })
                .thenWaitUntil(() -> {
                    if (!(netherDim.getEntity(entityId) instanceof TameableDragonEntity)) {
                        helper.fail("Waiting for the destination chunk to expose the transferred dragon");
                    }
                })
                .thenExecute(() -> {
                    // Simulate stale dimension metadata left by an external teleport.
                    cap.setDragonInstance(0, new DragonInstance(player.level(), entityId, dragonId));

                    if (!DragonWhistleHandler.callDragon(player)) {
                        helper.fail("Failed to call dragon from another dimension");
                    }
                    var checkDragon = DragonWhistleHandler.findDragon(player, 0);
                    if (checkDragon == null) {
                        helper.fail("Dragon was not found after being called from another dimension");
                    }
                    if (!checkDragon.getUUID().equals(entityId)
                            || !checkDragon.getDragonUUID().equals(dragonId)) {
                        helper.fail("Calling a loaded dragon reconstructed it instead of transferring it");
                    }
                    if (netherDim.getEntity(entityId) != null) {
                        helper.fail("Original dragon remained loaded after cross-dimension call");
                    }
                    if (checkDragon.level() != player.level()) {
                        helper.fail("Called dragon stayed in the old dimension");
                    }
                    if (!DragonWhistleHandler.callDragon(player)
                            || DragonWhistleHandler.findDragon(player, 0) != checkDragon) {
                        helper.fail("Repeated call replaced the transferred dragon");
                    }
                    if (!checkDragon.hasChest() || checkDragon.getInventory().getItem(0).isEmpty()) {
                        helper.fail("Dragon's inventory was not transferred correctly between dimensions");
                    }
                    helper.succeed();
                });
    }

    /**
     * Tests that tamed dragons don't despawn when far away.
     *
     * <p>
     * This test verifies that:
     * 1. Multiple dragons can be tamed by a player
     * 2. Tamed dragons don't despawn when far away
     * 3. The removeWhenFarAway method returns false for tamed dragons
     *
     * @param helper The game test helper
     */
    @EmptyTemplate(floor = true)
    @GameTest
    @TestHolder
    public static void tamedDragonsDoNotDespawn(ExtendedGameTestHelper helper) {
        var player = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        player.moveToCentre();

        // Spawn and tame multiple dragons
        var dragon1 = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS);
        var dragon2 = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS.offset(2, 0, 0));
        var dragon3 = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS.offset(0, 0, 2));

        dragon1.setBreed(DragonBreedsRegistry.getDefault());
        dragon2.setBreed(DragonBreedsRegistry.getDefault());
        dragon3.setBreed(DragonBreedsRegistry.getDefault());

        // Tame all dragons
        dragon1.tamedFor(player, true);
        dragon2.tamedFor(player, true);
        dragon3.tamedFor(player, true);

        // Verify all dragons are tamed
        if (!dragon1.isTame() || !dragon2.isTame() || !dragon3.isTame()) {
            helper.fail("Not all dragons were tamed");
        }

        // Move dragons far away to test despawning
        dragon1.setPos(dragon1.getX() + 100, dragon1.getY(), dragon1.getZ() + 100);
        dragon2.setPos(dragon2.getX() + 100, dragon2.getY(), dragon2.getZ() - 100);
        dragon3.setPos(dragon3.getX() - 100, dragon3.getY(), dragon3.getZ() - 100);

        // Tick entities to allow potential despawning
        for (int i = 0; i < 100; i++) {
            player.tick();
            dragon1.tick();
            dragon2.tick();
            dragon3.tick();
        }

        // Verify dragons didn't despawn
        if (!dragon1.isAlive()) {
            helper.fail("Dragon1 despawned despite being tamed");
        }

        if (!dragon2.isAlive()) {
            helper.fail("Dragon2 despawned despite being tamed");
        }

        if (!dragon3.isAlive()) {
            helper.fail("Dragon3 despawned despite being tamed");
        }

        // Verify removeWhenFarAway returns false for tamed dragons
        if (dragon1.removeWhenFarAway(100) || dragon2.removeWhenFarAway(100) || dragon3.removeWhenFarAway(100)) {
            helper.fail("removeWhenFarAway returned true for tamed dragons");
        }

        helper.succeed();
    }

    /**
     * Tests that multiple dragons can be bound to different whistles.
     *
     * <p>
     * This test verifies that:
     * 1. Multiple dragons can be bound to different whistles
     * 2. Each dragon is correctly bound to its assigned whistle
     * 3. The correct whistle indices are assigned to each dragon
     *
     * @param helper The game test helper
     */
    @EmptyTemplate(floor = true)
    @GameTest
    @TestHolder
    public static void bindMultipleDragons(ExtendedGameTestHelper helper) {
        var player = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        player.moveToCentre();

        // Spawn and tame multiple dragons
        var dragon1 = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS);
        var dragon2 = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS.offset(2, 0, 0));

        dragon1.setBreed(DragonBreedsRegistry.getDefault());
        dragon2.setBreed(DragonBreedsRegistry.getDefault());

        // Tame all dragons
        dragon1.tamedFor(player, true);
        dragon2.tamedFor(player, true);

        // Verify all dragons are tamed
        if (!dragon1.isTame() || !dragon2.isTame()) {
            helper.fail("Not all dragons were tamed");
        }

        // Bind dragons to whistles
        var handler = PlayerStateUtils.getHandler(player);

        // Bind dragon1 to whistle index 0
        PlayerStateUtils.getHandler(player).setDragonToWhistle(dragon1, 0);

        // Verify dragon1 is bound to whistle index 0
        if (!PlayerStateUtils.getHandler(player).isBoundToWhistle(dragon1)) {
            helper.fail("Dragon1 was not bound to whistle");
        }

        // Bind dragon2 to whistle index 1
        PlayerStateUtils.getHandler(player).setDragonToWhistle(dragon2, 1);

        // Verify dragon2 is bound to whistle index 1
        if (!PlayerStateUtils.getHandler(player).isBoundToWhistle(dragon2)) {
            helper.fail("Dragon2 was not bound to whistle");
        }

        // Verify correct whistle indices
        int index1 = DragonWhistleHandler.getDragonSummonIndex(player, dragon1.getDragonUUID());
        int index2 = DragonWhistleHandler.getDragonSummonIndex(player, dragon2.getDragonUUID());

        if (index1 != 0) {
            helper.fail("Dragon1 was bound to wrong whistle index: " + index1);
        }

        if (index2 != 1) {
            helper.fail("Dragon2 was bound to wrong whistle index: " + index2);
        }

        helper.succeed();
    }
}
