package dmr.DragonMounts.network.packets;

import dmr.DragonMounts.network.AbstractMessage;
import dmr.DragonMounts.server.entity.TameableDragonEntity;
import java.util.Optional;
import lombok.Getter;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class DragonStatePacket extends AbstractMessage<DragonStatePacket> {
    public static final StreamCodec<FriendlyByteBuf, DragonStatePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            DragonStatePacket::getEntityId,
            ByteBufCodecs.INT,
            DragonStatePacket::getState,
            DragonStatePacket::new);

    @Getter
    private final int entityId;

    @Getter
    private final int state;

    /**
     * Empty constructor for NetworkHandler.
     */
    DragonStatePacket() {
        this.entityId = -1;
        this.state = -1;
    }

    /**
     * Creates a new packet with the given parameters.
     *
     * @param entityId The ID of the entity
     * @param state The state to set
     */
    public DragonStatePacket(int entityId, int state) {
        this.entityId = entityId;
        this.state = state;
    }

    @Override
    protected String getTypeName() {
        return "dragon_state";
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, DragonStatePacket> streamCodec() {
        return STREAM_CODEC;
    }

    public void handle(IPayloadContext supplier, Player player) {
        var level = player.level;
        var entity = level.getEntity(getEntityId());

        if (state < 0 || state > 2) return;
        if (entity instanceof TameableDragonEntity dragon
                && dragon.isAlive()
                && dragon.getControllingPassenger() == null) {
            if (!level.isClientSide && (!dragon.isOwnedBy(player) || dragon.distanceToSqr(player) >= 64)) return;
            switch (getState()) {
                case 0 -> { // Sit
                    dragon.setWanderTarget(Optional.empty());
                    dragon.setOrderedToSit(true);
                }
                case 1 -> { // Follow
                    dragon.setOrderedToSit(false);
                    dragon.setWanderTarget(Optional.empty());
                }
                case 2 -> { // Wander
                    dragon.setOrderedToSit(false);
                    dragon.setWanderTarget(Optional.of(GlobalPos.of(level.dimension(), player.blockPosition())));
                }
            }
            if (!level.isClientSide) {
                PacketDistributor.sendToPlayersTrackingEntity(dragon, this);
            }
        }
    }
}
