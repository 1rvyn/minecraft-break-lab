package dev.johns.breaktelemetry;

import com.github.retrooper.packetevents.PacketEvents;
import dev.johns.breaktelemetry.capture.CaptureManager;
import dev.johns.breaktelemetry.command.BreakLabCommand;
import dev.johns.breaktelemetry.packet.BreakPacketListener;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.plugin.java.JavaPlugin;

public final class BreakTelemetryPlugin extends JavaPlugin {
    private CaptureManager captures;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        captures = new CaptureManager(this, getDataFolder().toPath().resolve("captures"));
        PacketEvents.getAPI().getEventManager().registerListener(new BreakPacketListener(captures));
        PacketEvents.getAPI().init();

        BreakLabCommand command = new BreakLabCommand(captures);
        getCommand("breaklab").setExecutor(command);
        getCommand("breaklab").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(command, this);
        getLogger().info("BreakTelemetry ready. Use /breaklab start baseline or /breaklab start modded.");
    }

    @Override
    public void onDisable() {
        if (captures != null) captures.stopAll();
        if (PacketEvents.getAPI() != null) PacketEvents.getAPI().terminate();
    }
}
