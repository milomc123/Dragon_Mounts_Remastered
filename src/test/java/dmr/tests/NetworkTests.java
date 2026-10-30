package dmr.tests;

import static net.minecraft.world.InteractionHand.MAIN_HAND;
import static net.minecraft.world.item.DyeColor.WHITE;

import dmr.DMRTestConstants;
import dmr.DragonMounts.common.handlers.DragonWhistleHandler;
import dmr.DragonMounts.network.packets.DismountDragonPacket;
import dmr.DragonMounts.network.packets.DragonAgeSyncPacket;
import dmr.DragonMounts.network.packets.DragonAttackPacket;
import dmr.DragonMounts.network.packets.DragonBreathPacket;
import dmr.DragonMounts.network.packets.DragonBreathTargetSyncPacket;
import dmr.DragonMounts.network.packets.DragonCommandPacket;
import dmr.DragonMounts.network.packets.DragonStatePacket;
import dmr.DragonMounts.registry.DragonBreedsRegistry;
import dmr.DragonMounts.registry.ModEntities;
import dmr.DragonMounts.server.items.DragonWhistleItem;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

/**
 * Tests for the network packet handling functionality of the mod. These tests
 * verify that network packets can be properly encoded, decoded, and processed
 * to ensure reliable communication between client and server.
 */
@PrefixGameTestTemplate(false)
@ForEachTest(groups = "Network")
public class NetworkTests {

    @EmptyTemplate(floor = true)
    @GameTest
    @TestHolder
    public static void rejectsUnauthorizedEntityPackets(ExtendedGameTestHelper helper) {
        var owner = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        var stranger = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        var dragon = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS);
        dragon.setBreed(DragonBreedsRegistry.getDefault());
        dragon.tamedFor(owner, true);
        owner.setPos(dragon.getX(), dragon.getY(), dragon.getZ());
        stranger.setPos(dragon.getX(), dragon.getY(), dragon.getZ());
        dragon.setOrderedToSit(true);
        new DragonStatePacket(dragon.getId(), 1).handle(null, stranger);
        if (!dragon.isOrderedToSit()) helper.fail("Stranger commanded an owned dragon");
        new DragonStatePacket(dragon.getId(), 1).handle(null, owner);
        if (dragon.isOrderedToSit()) helper.fail("Owner's valid command was rejected");
        owner.setPos(dragon.getX() + 20, dragon.getY(), dragon.getZ());
        new DragonStatePacket(dragon.getId(), 0).handle(null, owner);
        if (dragon.isOrderedToSit()) helper.fail("Out-of-range inventory command was accepted");
        dragon.setAge(-100);
        new DragonAgeSyncPacket(dragon.getId(), 0).handle(null, stranger);
        if (dragon.getAge() != -100) helper.fail("Client supplied server-authoritative age");
        new DragonBreathPacket(dragon.getId()).handleServer(null, stranger);
        if (dragon.hasBreathTarget()) helper.fail("Stranger triggered breath attack");
        new DragonAttackPacket(dragon.getId()).handleServer(null, stranger);
        if (dragon.swinging) helper.fail("Stranger triggered bite attack");
        var targetPacket = DragonBreathTargetSyncPacket.forPositionTarget(
                dragon.getId(), dragon.blockPosition().above());
        targetPacket.handle(null, stranger);
        if (dragon.hasBreathTarget()) helper.fail("Client supplied server-authoritative breath target");
        owner.startRiding(dragon, true);
        new DismountDragonPacket(owner.getId(), true).handle(null, stranger);
        if (owner.getVehicle() != dragon) helper.fail("Stranger dismounted another player");
        new DismountDragonPacket(owner.getId(), true).handle(null, owner);
        if (owner.isPassenger()) helper.fail("Player could not dismount themselves");
        helper.succeed();
    }

    @EmptyTemplate(floor = true)
    @GameTest
    @TestHolder
    public static void validatesDragonCommandsAndRiderActions(ExtendedGameTestHelper helper) {
        var owner = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);
        var dragon = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS);
        dragon.setBreed(DragonBreedsRegistry.getDefault());
        DragonWhistleHandler.setDragon(owner, dragon, 0);
        owner.setItemInHand(MAIN_HAND, DragonWhistleItem.getWhistleItem(WHITE));
        for (int command : new int[] {-1, 7, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            new DragonCommandPacket(command).handleServer(null, owner);
        }
        new DragonBreathPacket(dragon.getId()).handleServer(null, owner);
        if (dragon.hasBreathTarget()) helper.fail("Unmounted player triggered rider attack");
        new DragonCommandPacket(0).handleServer(null, owner);
        if (!dragon.isOrderedToSit()) helper.fail("Valid bound-dragon command was rejected");
        dragon.setOrderedToSit(false);
        owner.startRiding(dragon, true);
        new DragonBreathPacket(dragon.getId()).handleServer(null, owner);
        if (!dragon.hasBreathTarget()) helper.fail("Controlling rider's breath attack was rejected");
        new DragonAttackPacket(dragon.getId()).handleServer(null, owner);
        if (!dragon.swinging) helper.fail("Controlling rider's bite attack was rejected");
        helper.succeed();
    }

    /**
     * Tests the DragonStatePacket encoding and decoding functionality.
     *
     * <p>
     * This test verifies that: 1. A DragonStatePacket can be created with the
     * correct entity ID and state 2. The packet can be properly encoded to a byte
     * buffer 3. The packet can be properly decoded from a byte buffer 4. The
     * decoded packet contains the same values as the original packet
     *
     * @param helper
     *               The game test helper
     */
    @EmptyTemplate(floor = true)
    @GameTest
    @TestHolder
    public static void testDragonStatePacket(ExtendedGameTestHelper helper) {
        // Create a player
        var player = helper.makeTickingMockServerPlayerInLevel(GameType.DEFAULT_MODE);

        // Create a dragon
        var dragon = helper.spawn(ModEntities.DRAGON_ENTITY.get(), DMRTestConstants.TEST_POS);

        // Create a packet
        var packet = new DragonStatePacket(dragon.getId(), 1);

        // Test encoding and decoding
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        DragonStatePacket.STREAM_CODEC.encode(buffer, packet);
        DragonStatePacket decodedPacket = DragonStatePacket.STREAM_CODEC.decode(buffer);

        // Verify the packet has the correct values
        if (packet.getEntityId() != dragon.getId()) {
            helper.fail("Packet entity ID doesn't match expected value");
        }

        if (packet.getState() != 1) {
            helper.fail("Packet state doesn't match expected value");
        }

        // Verify the decoded packet has the correct values
        if (decodedPacket.getEntityId() != dragon.getId()) {
            helper.fail("Decoded packet entity ID doesn't match expected value");
        }

        if (decodedPacket.getState() != 1) {
            helper.fail("Decoded packet state doesn't match expected value");
        }

        helper.succeed();
    }
}
