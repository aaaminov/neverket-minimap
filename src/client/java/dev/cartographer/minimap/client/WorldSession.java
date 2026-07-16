package dev.cartographer.minimap.client;

import dev.cartographer.minimap.atlas.MapAtlas;
import dev.cartographer.minimap.storage.AtlasStorage;
import dev.cartographer.minimap.storage.WorldIdentityStore;
import dev.cartographer.minimap.config.ModConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

public final class WorldSession implements AutoCloseable {
	private static final int AUTO_SAVE_INTERVAL_TICKS = 600;

	private final AtlasStorage storage;
	private final MapDataCollector collector = new MapDataCollector();
	private final TerrainDataCollector terrainCollector = new TerrainDataCollector();
	private final ModConfig config;
	private final Logger logger;
	private String worldKey;
	private MapAtlas atlas = new MapAtlas();
	private long savedVersion;
	private int ticksUntilSave = AUTO_SAVE_INTERVAL_TICKS;

	public WorldSession(Path atlasDirectory, Logger logger, ModConfig config) {
		this.storage = new AtlasStorage(atlasDirectory);
		this.logger = logger;
		this.config = config;
	}

	public void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.player == null) {
			this.unload();
			return;
		}

		String currentKey = this.identifyWorld(minecraft);
		if (!currentKey.equals(this.worldKey)) {
			this.saveIfNeeded();
			this.worldKey = currentKey;
			try {
				this.atlas = this.storage.load(currentKey);
				this.savedVersion = this.atlas.version();
			} catch (IOException | RuntimeException exception) {
				this.logger.error("Could not load minimap atlas for {}", currentKey, exception);
				this.atlas = new MapAtlas();
				this.savedVersion = 0;
			}
		}

		this.collector.tick(minecraft, this.atlas, this.config.recordingMode == ModConfig.RecordingMode.MAPS);
		this.terrainCollector.tick(minecraft, this.atlas, this.config);
		if (--this.ticksUntilSave <= 0) {
			this.saveIfNeeded();
			this.ticksUntilSave = AUTO_SAVE_INTERVAL_TICKS;
		}
	}

	public MapAtlas atlas() {
		return this.atlas;
	}

	public boolean active() {
		return this.worldKey != null;
	}

	public void saveNow() {
		this.saveIfNeeded();
	}

	private void unload() {
		if (this.worldKey != null) {
			this.saveIfNeeded();
			this.worldKey = null;
			this.atlas = new MapAtlas();
			this.savedVersion = 0;
		}
	}

	private void saveIfNeeded() {
		if (this.worldKey == null || this.atlas.version() == this.savedVersion) {
			return;
		}
		try {
			this.storage.save(this.worldKey, this.atlas);
			this.savedVersion = this.atlas.version();
		} catch (IOException exception) {
			this.logger.error("Could not save minimap atlas for {}", this.worldKey, exception);
		}
	}

	@Override
	public void close() {
		this.saveIfNeeded();
	}

	private String identifyWorld(Minecraft minecraft) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server != null) {
			Path root = server.getWorldPath(LevelResource.ROOT).normalize();
			Path fileName = root.getFileName();
			String folder = (fileName == null ? root : fileName).toString();
			try {
				return "singleplayer:" + folder + ":" + WorldIdentityStore.loadOrCreate(root);
			} catch (IOException exception) {
				this.logger.error("Could not create a stable minimap identity for {}", root, exception);
				return "singleplayer-unidentified:" + root.toAbsolutePath().normalize();
			}
		}

		ServerData serverData = minecraft.getCurrentServer();
		String address = serverData == null ? "unknown" : serverData.ip;
		return "server:" + address.trim().toLowerCase(Locale.ROOT);
	}
}
