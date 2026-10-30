package dmr.DragonMounts.network.packets;

import dmr.DragonMounts.DMR;
import dmr.DragonMounts.network.PacketHelper;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registry for all packet types in the mod.
 * <p>
 * This class is responsible for registering all packet types with the event system.
 * It utilizes the package-private constructors of packet classes to create instances
 * for registration.
 */
public class PacketRegistry {

    /**
     * Registers all packet types with the event system.
     *
     * @param event The registration event
     */
    public static void registerEvent(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(DMR.MOD_ID);
        registerPackets(registrar);
    }

    /**
     * Registers all packet types with the registrar.
     *
     * @param registrar The payload registrar
     */
    private static void registerPackets(PayloadRegistrar registrar) {
        // Dragon state and control packets
        PacketHelper.registerPacket(registrar, new DragonStatePacket());
        PacketHelper.registerServerboundPacket(registrar, new DragonCommandPacket());
        PacketHelper.registerServerboundPacket(registrar, new SummonDragonPacket());
        PacketHelper.registerPacket(registrar, new DismountDragonPacket());

        // Dragon data sync packets
        PacketHelper.registerClientboundPacket(registrar, new SyncDataPackPacket());
        PacketHelper.registerClientboundPacket(registrar, new CompleteDataSync());
        PacketHelper.registerClientboundPacket(registrar, new DragonAgeSyncPacket());
        PacketHelper.registerClientboundPacket(registrar, new DragonRespawnDelayPacket());
        PacketHelper.registerClientboundPacket(registrar, new DragonNBTSync());

        // Dragon action packets
        PacketHelper.registerServerboundPacket(registrar, new DragonAttackPacket());
        PacketHelper.registerServerboundPacket(registrar, new DragonBreathPacket());
        PacketHelper.registerClientboundPacket(registrar, new DragonBreathTargetSyncPacket());

        // Client configuration packets
        PacketHelper.registerPacket(registrar, new ConfigSyncPacket());

        // Egg and inventory packets
        PacketHelper.registerClientboundPacket(registrar, new BlankEggSyncPacket());
        PacketHelper.registerClientboundPacket(registrar, new RequestDragonInventoryPacket());
        PacketHelper.registerClientboundPacket(registrar, new ClearDragonInventoryPacket());
    }
}
