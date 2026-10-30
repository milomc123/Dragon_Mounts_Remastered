package dmr.DragonMounts.common.handlers;

import dmr.DragonMounts.DMR;
import dmr.DragonMounts.ModConstants;
import dmr.DragonMounts.common.capability.DragonOwnerCapability;
import dmr.DragonMounts.common.capability.types.NBTInterface;
import dmr.DragonMounts.config.ServerConfig;
import dmr.DragonMounts.network.packets.CompleteDataSync;
import dmr.DragonMounts.network.packets.DragonStatePacket;
import dmr.DragonMounts.registry.ModCapabilities;
import dmr.DragonMounts.registry.ModItems;
import dmr.DragonMounts.registry.ModSounds;
import dmr.DragonMounts.server.entity.DragonConstants;
import dmr.DragonMounts.server.entity.TameableDragonEntity;
import dmr.DragonMounts.server.items.DragonWhistleItem;
import dmr.DragonMounts.server.worlddata.DragonWorldDataManager;
import dmr.DragonMounts.util.PlayerStateUtils;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.network.PacketDistributor;

public class DragonWhistleHandler {

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DragonInstance implements NBTInterface {

        String dimension;
        UUID entityId;
        UUID UUID;

        public DragonInstance(Level level, UUID entityId, UUID dragonUUID) {
            this.dimension = level.dimension().location().toString();
            this.entityId = entityId;
            this.UUID = dragonUUID;
        }

        public DragonInstance(TameableDragonEntity dragon) {
            this.dimension = dragon.level.dimension().location().toString();
            this.entityId = dragon.getUUID();
            this.UUID = dragon.getDragonUUID();
        }

        @Override
        public CompoundTag writeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", dimension);
            tag.putUUID("entityId", entityId);
            tag.putUUID("uuid", UUID);
            return tag;
        }

        @Override
        public void readNBT(CompoundTag base) {
            if (base.contains("dimension")) {
                dimension = base.getString("dimension");
            }
            if (base.contains("entityId")) {
                entityId = base.getUUID("entityId");
            }
            if (base.contains("uuid")) {
                UUID = base.getUUID("uuid");
            }
        }
    }

    /** Owner lookup for persistence, including owners in another dimension. */
    public static Player getOnlineOwner(TameableDragonEntity dragon) {
        var server = dragon.level().getServer();
        if (server != null && dragon.getOwnerUUID() != null) {
            var owner = server.getPlayerList().getPlayer(dragon.getOwnerUUID());
            if (owner != null) return owner;
        }
        return dragon.getOwner() instanceof Player player ? player : null;
    }

    public static DragonWhistleItem getDragonWhistleItem(Player player) {
        var state = PlayerStateUtils.getHandler(player);
        Function<DragonWhistleItem, Boolean> isValid = (DragonWhistleItem whistleItem) -> {
            if (whistleItem.getColor() == null) {
                return false;
            }
            return state.dragonNBTs.containsKey(whistleItem.getColor().getId());
        };

        // Main hand - first
        if (player.getInventory().getSelected().getItem() instanceof DragonWhistleItem whistleItem) {
            if (isValid.apply(whistleItem)) {
                return whistleItem;
            }
        }

        // Off hand - second
        if (player.getInventory().offhand.getFirst().getItem() instanceof DragonWhistleItem whistleItem) {
            if (isValid.apply(whistleItem)) {
                return whistleItem;
            }
        }

        // Hotbar - third
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).getItem() instanceof DragonWhistleItem whistleItem) {
                if (isValid.apply(whistleItem)) {
                    return whistleItem;
                }
            }
        }

        // Inventory - fourth
        for (int i = 9; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() instanceof DragonWhistleItem whistleItem) {
                if (isValid.apply(whistleItem)) {
                    return whistleItem;
                }
            }
        }

        return null;
    }

    public static int getDragonSummonIndex(Player player) {
        var whistleItem = getDragonWhistleItem(player);
        return whistleItem != null ? whistleItem.getColor().getId() : -1;
    }

    public static int getDragonSummonIndex(Player player, UUID dragonUUID) {
        var handler = PlayerStateUtils.getHandler(player);

        return handler.dragonInstances.entrySet().stream()
                .filter(entry ->
                        entry.getValue() != null && entry.getValue().UUID.equals(dragonUUID))
                .map(Entry::getKey)
                .findFirst()
                .orElse(-1);
    }

    public static void setDragon(Player player, TameableDragonEntity dragon, int index) {
        player.getData(ModCapabilities.PLAYER_CAPABILITY).setPlayerInstance(player);
        player.getData(ModCapabilities.PLAYER_CAPABILITY).setDragonToWhistle(dragon, index);
    }

    public static boolean canCall(Player player, int index) {
        var handler = PlayerStateUtils.getHandler(player);

        if (index == -1) {
            player.displayClientMessage(
                    Component.translatable("dmr.dragon_call.no_whistle").withStyle(ChatFormatting.RED), true);
            return false;
        }

        // Clean up invalid whistle data
        if (!player.level.isClientSide) {
            if ((handler.dragonNBTs.containsKey(index) && handler.dragonNBTs.get(index) == null)
                    || (handler.dragonInstances.containsKey(index) && handler.dragonInstances.get(index) == null)
                    || (handler.dragonInstances.containsKey(index) != handler.dragonNBTs.containsKey(index))) {
                handler.dragonNBTs.remove(index);
                handler.dragonInstances.remove(index);
                handler.respawnDelays.remove(index);
                PacketDistributor.sendToPlayer((ServerPlayer) player, new CompleteDataSync(player));
                return false;
            }
        }

        if (!handler.dragonInstances.containsKey(index) || handler.dragonInstances.get(index) == null) {
            if (!player.level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("dmr.dragon_call.nodragon").withStyle(ChatFormatting.RED), true);
            }
            return false;
        }

        if (handler.respawnDelays.getOrDefault(index, 0) > 0) {
            if (!player.level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable(
                                        "dmr.dragon_call.respawn", handler.respawnDelays.getOrDefault(index, 0) / 20)
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return false;
        }

        if (player.getVehicle() != null) {
            if (!player.level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("dmr.dragon_call.riding").withStyle(ChatFormatting.RED), true);
            }
            return false;
        }

        // TODO Implement a better handling of space checking for game tests
        if (ServerConfig.CALL_CHECK_SPACE && !GameTestHooks.isGametestEnabled()) {
            if (!player.level.noBlockCollision(
                    null, player.getBoundingBox().move(0, 1, 0).inflate(1, 1, 1))) {
                if (!player.level.isClientSide) {
                    player.displayClientMessage(
                            Component.translatable("dmr.dragon_call.nospace").withStyle(ChatFormatting.RED), true);
                }
                return false;
            }
        }

        if (handler.lastCall != null && ServerConfig.WHISTLE_COOLDOWN_CONFIG > 0) {
            if (handler.lastCall + ServerConfig.WHISTLE_COOLDOWN_CONFIG > System.currentTimeMillis()) {
                if (!player.level.isClientSide) {
                    player.displayClientMessage(
                            Component.translatable("dmr.dragon_call.on_cooldown")
                                    .withStyle(ChatFormatting.RED),
                            true);
                }
                return false;
            }
        }

        return true;
    }

    public static void summonDragon(Player player) {
        if (player != null) {
            if (callDragon(player)) {
                var handler = PlayerStateUtils.getHandler(player);
                handler.lastCall = System.currentTimeMillis();
                ModItems.DRAGON_WHISTLES.values().forEach(s -> {
                    if (!player.getCooldowns().isOnCooldown(s.get())) {
                        player.getCooldowns()
                                .addCooldown(
                                        s.get(),
                                        (int) TimeUnit.SECONDS.convert(
                                                        ServerConfig.WHISTLE_COOLDOWN_CONFIG, TimeUnit.MILLISECONDS)
                                                * 20);
                    }
                });
            }
        }
    }

    public static boolean callDragon(Player player) {
        if (player != null) {
            DragonOwnerCapability cap = player.getData(ModCapabilities.PLAYER_CAPABILITY);

            var summonItemIndex = getDragonSummonIndex(player);

            if (!canCall(player, summonItemIndex)) return false;

            Random rand = new Random();
            player.level.playSound(
                    null,
                    player.blockPosition(),
                    ModSounds.DRAGON_WHISTLE_SOUND.get(),
                    player.getSoundSource(),
                    0.75f,
                    (float) (ModConstants.DragonConstants.WHISTLE_BASE_PITCH
                            + rand.nextGaussian() / ModConstants.DragonConstants.WHISTLE_PITCH_DIVISOR));

            if (player.level.isClientSide) {
                return true; // Only process the remaining logic on the server side
            }

            DragonInstance instance = cap.dragonInstances.get(summonItemIndex);
            TameableDragonEntity dragon = findDragon(player, summonItemIndex);

            if (dragon != null) {
                dragon.ejectPassengers();
                if (dragon.level() != player.level()) {
                    var moved = dragon.changeDimension(new DimensionTransition(
                            (ServerLevel) player.level(),
                            player.position(),
                            Vec3.ZERO,
                            dragon.getYRot(),
                            dragon.getXRot(),
                            DimensionTransition.DO_NOTHING));
                    if (!(moved instanceof TameableDragonEntity movedDragon) || movedDragon.isRemoved()) return false;
                    dragon = movedDragon;
                }
                cap.setPlayerInstance(player);
                cap.setDragonToWhistle(dragon, summonItemIndex);
                dragon.setHealth(Math.max(ModConstants.DragonConstants.MIN_DRAGON_HEALTH, dragon.getHealth()));

                if (dragon.position().distanceTo(player.position())
                        <= DragonConstants.BASE_FOLLOW_RANGE * ModConstants.DragonConstants.FOLLOW_RANGE_MULTIPLIER) {
                    // Walk to player
                    dragon.setOrderedToSit(false);
                    dragon.setWanderTarget(Optional.empty());

                    cap.lastSummons.put(summonItemIndex, dragon.getUUID());

                    DMR.LOGGER.debug(
                            "Making dragon: {} follow player: {}",
                            dragon.getDragonUUID(),
                            player.getName().getString());
                    PacketDistributor.sendToPlayersTrackingEntity(
                            dragon,
                            new DragonStatePacket(dragon.getId(), ModConstants.DragonConstants.DRAGON_STATE_FOLLOW));
                } else {
                    // Teleport to player
                    dragon.setOrderedToSit(false);
                    dragon.setWanderTarget(Optional.empty());

                    cap.lastSummons.put(summonItemIndex, dragon.getUUID());

                    DMR.LOGGER.debug(
                            "Teleporting dragon: {} to player: {}",
                            dragon.getDragonUUID(),
                            player.getName().getString());

                    dragon.setPos(player.getX(), player.getY(), player.getZ());
                    PacketDistributor.sendToPlayersTrackingEntity(
                            dragon,
                            new DragonStatePacket(dragon.getId(), ModConstants.DragonConstants.DRAGON_STATE_FOLLOW));
                }
                ModCapabilities.syncCapability(player);
                return true;
            }

            return summonFromSnapshot(player, cap, summonItemIndex);
        }
        return false;
    }

    private static boolean summonFromSnapshot(Player player, DragonOwnerCapability cap, int index) {
        var instance = cap.dragonInstances.get(index);
        var previousNBT = cap.dragonNBTs.get(index);
        var previousSummon = cap.lastSummons.get(index);
        if (instance == null || previousNBT == null) return false;
        var id = instance.getUUID();
        var destination = DragonWorldDataManager.getInstance(player.level());
        var previousDestinationInventory = destination.dragonInventories.get(id);
        var dimension = ResourceLocation.tryParse(instance.getDimension());
        var sourceLevel = dimension == null
                ? null
                : player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        var source = sourceLevel == null ? null : DragonWorldDataManager.getInstance(sourceLevel);
        var previousSourceInventory = source == null ? null : source.dragonInventories.get(id);
        TameableDragonEntity newDragon = null;
        boolean committed = false;
        try {
            if (sourceLevel != null) {
                DragonWorldDataManager.transferDragonInventory(sourceLevel, player.level(), id);
            }
            newDragon = cap.createDragonEntity(player, player.level(), index);
            if (newDragon == null) return false;
            newDragon.setPos(player.getX(), player.getY(), player.getZ());
            // The join event needs the tentative ID to distinguish this summon from an obsolete entity.
            cap.lastSummons.put(index, newDragon.getUUID());
            if (!player.level().addFreshEntity(newDragon)) return false;
            cap.setDragonToWhistle(newDragon, index);
            committed = true;
        } finally {
            if (!committed) {
                if (newDragon != null) newDragon.discard();
                cap.dragonInstances.put(index, instance);
                cap.dragonNBTs.put(index, previousNBT);
                if (previousSummon == null) cap.lastSummons.remove(index);
                else cap.lastSummons.put(index, previousSummon);
                if (previousDestinationInventory == null) destination.dragonInventories.remove(id);
                else destination.dragonInventories.put(id, previousDestinationInventory);
                destination.setDirty();
                if (source != null && source != destination) {
                    if (previousSourceInventory == null) source.dragonInventories.remove(id);
                    else source.dragonInventories.put(id, previousSourceInventory);
                    source.setDirty();
                }
                ModCapabilities.syncCapability(player);
            }
        }
        ModCapabilities.syncCapability(player);
        PacketDistributor.sendToPlayersTrackingEntity(
                newDragon, new DragonStatePacket(newDragon.getId(), ModConstants.DragonConstants.DRAGON_STATE_FOLLOW));
        return true;
    }

    public static TameableDragonEntity findDragon(Player player, int index) {
        if (player.level.isClientSide) return null;
        var cap = player.getData(ModCapabilities.PLAYER_CAPABILITY);
        var instance = cap.dragonInstances.get(index);
        if (instance == null) return null;
        var server = player.getServer();
        if (server == null) return null;
        var entityId = cap.lastSummons.getOrDefault(index, instance.getEntityId());

        // Dimension metadata can be stale after portals or external teleports.
        // Search loaded worlds by entity UUID before considering snapshot reconstruction.
        for (var level : server.getAllLevels()) {
            var entity = level.getEntity(entityId);
            if (entity instanceof TameableDragonEntity dragon
                    && !dragon.isRemoved()
                    && instance.getUUID().equals(dragon.getDragonUUID())
                    && player.getUUID().equals(dragon.getOwnerUUID())) return dragon;
        }
        // Legacy bindings may only have the persistent dragon identity.
        if (!cap.lastSummons.containsKey(index)) {
            for (var level : server.getAllLevels()) {
                for (var entity : level.getAllEntities()) {
                    if (entity instanceof TameableDragonEntity dragon
                            && !dragon.isRemoved()
                            && instance.getUUID().equals(dragon.getDragonUUID())
                            && player.getUUID().equals(dragon.getOwnerUUID())) return dragon;
                }
            }
        }
        return null;
    }
}
