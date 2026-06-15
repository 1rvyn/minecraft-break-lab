package dev.johns.breaktelemetry.command;

import dev.johns.breaktelemetry.capture.CaptureManager;
import dev.johns.breaktelemetry.capture.CaptureSession;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BreakLabCommand implements CommandExecutor, TabCompleter, Listener {
    private static final Material[] ARENA_BLOCKS = {
            Material.STONE, Material.COBBLESTONE, Material.DEEPSLATE, Material.OAK_LOG,
            Material.OAK_PLANKS, Material.DIRT, Material.SAND, Material.GLASS, Material.OBSIDIAN
    };
    private static final Material[] PICKAXE_MATRIX_BLOCKS = {
            Material.STONE, Material.STONE, Material.STONE,
            Material.COBBLESTONE, Material.COBBLESTONE, Material.COBBLESTONE,
            Material.DEEPSLATE, Material.DEEPSLATE, Material.DEEPSLATE
    };
    private final CaptureManager captures;

    public BreakLabCommand(CaptureManager captures) {
        this.captures = captures;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command must be run by a player.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("/breaklab start <label>, mark <text>, status, stop, arena reset|stone|matrix, setup");
            return true;
        }
        try {
            switch (args[0].toLowerCase()) {
                case "start" -> start(player, args);
                case "mark" -> mark(player, args);
                case "status" -> status(player);
                case "stop" -> stop(player);
                case "arena" -> arena(player, args);
                case "setup" -> setup(player);
                default -> player.sendMessage("Unknown subcommand.");
            }
        } catch (IOException exception) {
            player.sendMessage("Capture I/O failed: " + exception.getMessage());
        }
        return true;
    }

    private void start(Player player, String[] args) throws IOException {
        String sessionLabel = args.length > 1 ? args[1].toLowerCase() : "baseline";
        if (!sessionLabel.matches("[a-z0-9._-]{1,64}")) {
            player.sendMessage("Label must be 1-64 characters using letters, numbers, dot, dash, or underscore."); return;
        }
        CaptureSession session = captures.start(player, sessionLabel);
        player.sendMessage("Started " + sessionLabel + " capture " + session.id());
    }

    private void mark(Player player, String[] args) {
        String text = args.length > 1 ? String.join(" ", List.of(args).subList(1, args.length)) : "mark";
        captures.record(player, "MARK", Map.of("text", text));
        player.sendMessage("Marked capture: " + text);
    }

    private void status(Player player) {
        player.sendMessage(captures.session(player.getUniqueId())
                .map(session -> "Active " + session.label() + " session; records=" + session.recordCount())
                .orElse("No active capture."));
    }

    private void stop(Player player) throws IOException {
        Path path = captures.stop(player.getUniqueId()).orElse(null);
        player.sendMessage(path == null ? "No active capture." : "Capture saved to " + path);
    }

    private void arena(Player player, String[] args) {
        if (args.length < 2 || (!args[1].equalsIgnoreCase("reset")
                && !args[1].equalsIgnoreCase("stone") && !args[1].equalsIgnoreCase("matrix"))) {
            player.sendMessage("Use /breaklab arena reset, /breaklab arena stone, or /breaklab arena matrix"); return;
        }
        Location origin = player.getLocation().getBlock().getLocation().add(3, -1, 0);
        World world = origin.getWorld();
        for (int x = -1; x <= ARENA_BLOCKS.length; x++) {
            for (int z = -2; z <= 2; z++) world.getBlockAt(origin.getBlockX() + x, origin.getBlockY(), origin.getBlockZ() + z).setType(Material.SMOOTH_STONE);
        }
        boolean stoneOnly = args[1].equalsIgnoreCase("stone");
        boolean pickaxeMatrix = args[1].equalsIgnoreCase("matrix");
        for (int i = 0; i < ARENA_BLOCKS.length; i++) {
            world.getBlockAt(origin.getBlockX() + i, origin.getBlockY() + 1, origin.getBlockZ())
                    .setType(stoneOnly ? Material.STONE : pickaxeMatrix ? PICKAXE_MATRIX_BLOCKS[i] : ARENA_BLOCKS[i]);
        }
        String description = stoneOnly ? " stone blocks." : pickaxeMatrix
                ? " pickaxe matrix blocks (stone, cobblestone, deepslate)." : " test blocks.";
        player.sendMessage("Reset arena with " + ARENA_BLOCKS.length + description);
    }

    private void setup(Player player) {
        player.setGameMode(org.bukkit.GameMode.SURVIVAL);
        player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
        ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
        pickaxe.addEnchantment(Enchantment.EFFICIENCY, 1);
        player.getInventory().setItemInMainHand(pickaxe);
        player.setFoodLevel(20);
        player.setSaturation(20);
        captures.record(player, "TEST_SETUP", Map.of("preset", "iron_pickaxe_efficiency_1_stone"));
        player.sendMessage("Set Survival, cleared effects, and equipped an Efficiency I iron pickaxe.");
    }

    @EventHandler public void onDamage(BlockDamageEvent event) {
        captures.record(event.getPlayer(), "BUKKIT_DAMAGE", CaptureManager.blockSnapshot(event.getBlock()));
    }
    @EventHandler public void onDamageAbort(BlockDamageAbortEvent event) {
        captures.record(event.getPlayer(), "BUKKIT_DAMAGE_ABORT", CaptureManager.blockSnapshot(event.getBlock()));
    }
    @EventHandler public void onBreak(BlockBreakEvent event) {
        captures.record(event.getPlayer(), "BUKKIT_BREAK", CaptureManager.blockSnapshot(event.getBlock()));
    }
    @EventHandler public void onHeld(PlayerItemHeldEvent event) {
        captures.record(event.getPlayer(), "BUKKIT_HELD_SLOT", Map.of("previous", event.getPreviousSlot(), "new", event.getNewSlot()));
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        try { captures.stop(event.getPlayer().getUniqueId()); } catch (IOException ignored) { }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return filter(List.of("start", "mark", "status", "stop", "arena", "setup"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) return filter(List.of("baseline", "modded-1.15"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("arena")) return filter(List.of("reset", "stone", "matrix"), args[1]);
        return List.of();
    }

    private static List<String> filter(List<String> values, String prefix) {
        List<String> result = new ArrayList<>();
        for (String value : values) if (value.startsWith(prefix.toLowerCase())) result.add(value);
        return result;
    }
}
