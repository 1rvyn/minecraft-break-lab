package dev.johns.breaktelemetry.packet;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientAnimation;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerBlockPlacement;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPong;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientUseItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAcknowledgeBlockChanges;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerAcknowledgePlayerDigging;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockBreakAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerKeepAlive;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMultiBlockChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import dev.johns.breaktelemetry.capture.CaptureManager;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;

public final class BreakPacketListener extends PacketListenerAbstract {
    private final CaptureManager captures;

    public BreakPacketListener(CaptureManager captures) {
        this.captures = captures;
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player player) || captures.session(player.getUniqueId()).isEmpty()) return;
        long observedEpochMillis = System.currentTimeMillis();
        long observedNanos = System.nanoTime();

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging packet = new WrapperPlayClientPlayerDigging(event);
            String action = packet.getAction().name();
            String eventName = switch (action) {
                case "START_DIGGING" -> "DIG_START";
                case "CANCELLED_DIGGING" -> "DIG_ABORT";
                case "FINISHED_DIGGING" -> "DIG_FINISH";
                default -> "PLAYER_ACTION_" + action;
            };
            Map<String, Object> data = positionData(packet.getBlockPosition());
            data.put("face", packet.getBlockFace().name());
            data.put("sequenceId", packet.getSequence());
            record(player, eventName, data, observedEpochMillis, observedNanos);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.ANIMATION) {
            WrapperPlayClientAnimation packet = new WrapperPlayClientAnimation(event);
            record(player, "ARM_ANIMATION", Map.of("hand", packet.getHand().name()), observedEpochMillis, observedNanos);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            WrapperPlayClientHeldItemChange packet = new WrapperPlayClientHeldItemChange(event);
            record(player, "HELD_SLOT_PACKET", Map.of("slot", packet.getSlot()), observedEpochMillis, observedNanos);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.CLIENT_TICK_END) {
            record(player, "CLIENT_TICK_END", Map.of(), observedEpochMillis, observedNanos);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.KEEP_ALIVE) {
            WrapperPlayClientKeepAlive packet = new WrapperPlayClientKeepAlive(event);
            record(player, "CLIENT_KEEP_ALIVE", Map.of("id", packet.getId()), observedEpochMillis, observedNanos);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.PONG) {
            WrapperPlayClientPong packet = new WrapperPlayClientPong(event);
            record(player, "CLIENT_PONG", Map.of("id", packet.getId()), observedEpochMillis, observedNanos);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT) {
            WrapperPlayClientPlayerBlockPlacement packet = new WrapperPlayClientPlayerBlockPlacement(event);
            Map<String, Object> data = positionData(packet.getBlockPosition());
            data.put("hand", packet.getHand().name());
            data.put("face", packet.getFace().name());
            data.put("sequenceId", packet.getSequence());
            data.put("cursorX", packet.getCursorPosition().getX());
            data.put("cursorY", packet.getCursorPosition().getY());
            data.put("cursorZ", packet.getCursorPosition().getZ());
            record(player, "USE_BLOCK", data, observedEpochMillis, observedNanos);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.USE_ITEM) {
            WrapperPlayClientUseItem packet = new WrapperPlayClientUseItem(event);
            record(player, "USE_ITEM", Map.of(
                    "hand", packet.getHand().name(),
                    "sequenceId", packet.getSequence(),
                    "yaw", packet.getYaw(),
                    "pitch", packet.getPitch()
            ), observedEpochMillis, observedNanos);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION
                || event.getPacketType() == PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION
                || event.getPacketType() == PacketType.Play.Client.PLAYER_ROTATION
                || event.getPacketType() == PacketType.Play.Client.PLAYER_FLYING) {
            WrapperPlayClientPlayerFlying packet = new WrapperPlayClientPlayerFlying(event);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("hasPosition", packet.hasPositionChanged());
            data.put("hasRotation", packet.hasRotationChanged());
            data.put("onGroundPacket", packet.isOnGround());
            var location = packet.getLocation();
            data.put("packetX", location.getX()); data.put("packetY", location.getY()); data.put("packetZ", location.getZ());
            data.put("packetYaw", location.getYaw()); data.put("packetPitch", location.getPitch());
            record(player, "MOVEMENT_PACKET", data, observedEpochMillis, observedNanos);
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player) || captures.session(player.getUniqueId()).isEmpty()) return;
        long observedEpochMillis = System.currentTimeMillis();
        long observedNanos = System.nanoTime();

        if (event.getPacketType() == PacketType.Play.Server.ACKNOWLEDGE_BLOCK_CHANGES) {
            WrapperPlayServerAcknowledgeBlockChanges packet = new WrapperPlayServerAcknowledgeBlockChanges(event);
            record(player, "SERVER_ACK_BLOCK_CHANGES", Map.of("sequenceId", packet.getSequence()),
                    observedEpochMillis, observedNanos);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.ACKNOWLEDGE_PLAYER_DIGGING) {
            WrapperPlayServerAcknowledgePlayerDigging packet = new WrapperPlayServerAcknowledgePlayerDigging(event);
            Map<String, Object> data = positionData(packet.getBlockPosition());
            data.put("action", packet.getAction().name());
            data.put("successful", packet.isSuccessful());
            data.put("blockId", packet.getBlockId());
            record(player, "SERVER_ACK_DIGGING", data, observedEpochMillis, observedNanos);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.BLOCK_CHANGE) {
            WrapperPlayServerBlockChange packet = new WrapperPlayServerBlockChange(event);
            Map<String, Object> data = positionData(packet.getBlockPosition());
            data.put("blockId", packet.getBlockId());
            data.put("packetBlockState", packet.getBlockState().toString());
            record(player, "SERVER_BLOCK_CHANGE", data, observedEpochMillis, observedNanos);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.MULTI_BLOCK_CHANGE) {
            WrapperPlayServerMultiBlockChange packet = new WrapperPlayServerMultiBlockChange(event);
            Map<String, Object> data = new LinkedHashMap<>();
            Vector3i chunk = packet.getChunkPosition();
            data.put("chunkX", chunk.getX());
            data.put("chunkY", chunk.getY());
            data.put("chunkZ", chunk.getZ());
            data.put("blockCount", packet.getBlocks().length);
            record(player, "SERVER_MULTI_BLOCK_CHANGE", data, observedEpochMillis, observedNanos);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.BLOCK_BREAK_ANIMATION) {
            WrapperPlayServerBlockBreakAnimation packet = new WrapperPlayServerBlockBreakAnimation(event);
            Map<String, Object> data = positionData(packet.getBlockPosition());
            data.put("entityId", packet.getEntityId());
            data.put("destroyStage", packet.getDestroyStage());
            record(player, "SERVER_BREAK_ANIMATION", data, observedEpochMillis, observedNanos);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.KEEP_ALIVE) {
            WrapperPlayServerKeepAlive packet = new WrapperPlayServerKeepAlive(event);
            record(player, "SERVER_KEEP_ALIVE", Map.of("id", packet.getId()), observedEpochMillis, observedNanos);
            return;
        }

        if (event.getPacketType() == PacketType.Play.Server.PING) {
            WrapperPlayServerPing packet = new WrapperPlayServerPing(event);
            record(player, "SERVER_PING", Map.of("id", packet.getId()), observedEpochMillis, observedNanos);
        }
    }

    private void record(Player player, String event, Map<String, Object> data,
                        long observedEpochMillis, long observedNanos) {
        captures.recordPacket(player.getUniqueId(), event, data, observedEpochMillis, observedNanos);
    }

    private static Map<String, Object> positionData(Vector3i position) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("target", position.getX() + "," + position.getY() + "," + position.getZ());
        data.put("targetX", position.getX());
        data.put("targetY", position.getY());
        data.put("targetZ", position.getZ());
        return data;
    }
}
