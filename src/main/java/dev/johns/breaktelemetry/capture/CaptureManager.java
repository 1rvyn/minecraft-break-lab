package dev.johns.breaktelemetry.capture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class CaptureManager {
    private final Plugin plugin;
    private final Path outputDirectory;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Map<UUID, CaptureSession> sessions = new ConcurrentHashMap<>();

    public CaptureManager(Plugin plugin, Path outputDirectory) {
        this.plugin = plugin;
        this.outputDirectory = outputDirectory;
    }

    public CaptureSession start(Player player, String label) throws IOException {
        CaptureSession previous = sessions.remove(player.getUniqueId());
        if (previous != null) {
            try {
                previous.close();
            } catch (IOException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not finalize previous capture; starting a new one", exception);
            }
        }
        CaptureSession session = new CaptureSession(outputDirectory, label, player, mapper);
        sessions.put(player.getUniqueId(), session);
        return session;
    }

    public Optional<CaptureSession> session(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public Optional<Path> stop(UUID playerId) throws IOException {
        CaptureSession session = sessions.remove(playerId);
        if (session == null) return Optional.empty();
        session.close();
        return Optional.of(session.jsonlPath());
    }

    public void stopAll() {
        for (UUID playerId : sessions.keySet()) {
            try { stop(playerId); }
            catch (IOException exception) { plugin.getLogger().log(Level.SEVERE, "Could not close capture", exception); }
        }
    }

    public void recordAsync(UUID playerId, String event, Map<String, Object> packetData) {
        recordPacket(playerId, event, packetData, System.currentTimeMillis(), System.nanoTime());
    }

    public void recordPacket(UUID playerId, String event, Map<String, Object> packetData,
                             long observedEpochMillis, long observedNanos) {
        if (!sessions.containsKey(playerId)) return;
        Runnable append = () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null) return;
            Map<String, Object> data = new LinkedHashMap<>(packetData);
            data.putAll(playerSnapshot(player));
            addTargetSnapshot(player, data);
            appendObserved(player, event, data, observedEpochMillis, observedNanos);
        };
        if (Bukkit.isPrimaryThread()) append.run();
        else Bukkit.getScheduler().runTask(plugin, append);
    }

    public void record(Player player, String event, Map<String, Object> data) {
        if (!Bukkit.isPrimaryThread()) {
            recordAsync(player.getUniqueId(), event, data);
            return;
        }
        Map<String, Object> enriched = new LinkedHashMap<>(data);
        enriched.putAll(playerSnapshot(player));
        append(player, event, enriched);
    }

    private void append(Player player, String event, Map<String, Object> data) {
        appendObserved(player, event, data, System.currentTimeMillis(), System.nanoTime());
    }

    private void appendObserved(Player player, String event, Map<String, Object> data,
                                long observedEpochMillis, long observedNanos) {
        CaptureSession session = sessions.get(player.getUniqueId());
        if (session == null) return;
        try {
            session.appendObserved(event, Bukkit.getCurrentTick(), data, observedEpochMillis, observedNanos);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Capture write failed; stopping session", exception);
            try { stop(player.getUniqueId()); } catch (IOException ignored) { }
        }
    }

    private static void addTargetSnapshot(Player player, Map<String, Object> data) {
        Object x = data.get("targetX");
        Object y = data.get("targetY");
        Object z = data.get("targetZ");
        if (x instanceof Number targetX && y instanceof Number targetY && z instanceof Number targetZ) {
            Block block = player.getWorld().getBlockAt(targetX.intValue(), targetY.intValue(), targetZ.intValue());
            data.put("blockType", block.getType().name());
            data.put("blockData", block.getBlockData().getAsString());
        }
    }

    private static Map<String, Object> playerSnapshot(Player player) {
        Location location = player.getLocation();
        ItemStack item = player.getInventory().getItemInMainHand();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("world", player.getWorld().getName());
        snapshot.put("x", location.getX()); snapshot.put("y", location.getY()); snapshot.put("z", location.getZ());
        snapshot.put("yaw", location.getYaw()); snapshot.put("pitch", location.getPitch());
        snapshot.put("ping", player.getPing());
        snapshot.put("gameMode", player.getGameMode().name());
        snapshot.put("onGround", player.isOnGround());
        snapshot.put("heldSlot", player.getInventory().getHeldItemSlot());
        snapshot.put("tool", item.getType().name());
        snapshot.put("toolAmount", item.getAmount());
        snapshot.put("enchantments", item.getEnchantments().entrySet().stream()
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey().getKey().asString(), entry.getValue()), Map::putAll));
        snapshot.put("effects", player.getActivePotionEffects().stream()
                .map(effect -> effect.getType().getKey().asString() + ":" + effect.getAmplifier()).toList());
        snapshot.put("blockInteractionRange", player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE) == null
                ? null : player.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).getValue());
        return snapshot;
    }

    public static Map<String, Object> blockSnapshot(Block block) {
        return Map.of("target", block.getX() + "," + block.getY() + "," + block.getZ(),
                "blockType", block.getType().name(), "blockData", block.getBlockData().getAsString());
    }
}
