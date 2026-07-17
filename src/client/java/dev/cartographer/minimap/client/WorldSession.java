package dev.cartographer.minimap.client;

import dev.cartographer.minimap.atlas.MapAtlas;
import dev.cartographer.minimap.storage.AtlasStorage;
import dev.cartographer.minimap.storage.WorldIdentityStore;
import dev.cartographer.minimap.config.ModConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
	private final ExecutorService saveExecutor;
	private String worldKey;
	private MapAtlas atlas = new MapAtlas();
	private long savedVersion;
	private CompletableFuture<Void> pendingSave;
	private String pendingSaveWorldKey;
	private long pendingSaveVersion;
	private boolean saveAfterPending;
	private int ticksUntilSave = AUTO_SAVE_INTERVAL_TICKS;

	public WorldSession(Path atlasDirectory, Logger logger, ModConfig config) {
		this.storage = new AtlasStorage(atlasDirectory);
		this.logger = logger;
		this.config = config;
		this.saveExecutor = Executors.newSingleThreadExecutor(runnable ->
			Thread.ofPlatform().daemon().name("neverket-minimap-save").unstarted(runnable)
		);
	}

	public void tick(Minecraft minecraft) {
		this.pollPendingSave();
		if (minecraft.level == null || minecraft.player == null) {
			this.unload();
			return;
		}

		String currentKey = this.identifyWorld(minecraft);
		if (!currentKey.equals(this.worldKey)) {
			this.finishPendingSave();
			this.saveSynchronouslyIfNeeded();
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
			this.scheduleSaveIfNeeded();
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
		if (this.pendingSave == null) {
			this.scheduleSaveIfNeeded();
		} else {
			this.saveAfterPending = true;
		}
	}

	private void unload() {
		if (this.worldKey != null) {
			this.finishPendingSave();
			this.saveSynchronouslyIfNeeded();
			this.worldKey = null;
			this.atlas = new MapAtlas();
			this.savedVersion = 0;
		}
	}

	private void scheduleSaveIfNeeded() {
		this.pollPendingSave();
		if (this.pendingSave != null || this.worldKey == null || this.atlas.version() == this.savedVersion) {
			return;
		}
		this.pendingSaveWorldKey = this.worldKey;
		this.pendingSaveVersion = this.atlas.version();
		AtlasStorage.SaveSnapshot snapshot = this.storage.snapshot(this.pendingSaveWorldKey, this.atlas);
		this.pendingSave = CompletableFuture.runAsync(() -> {
			try {
				this.storage.save(snapshot);
			} catch (IOException exception) {
				throw new CompletionException(exception);
			}
		}, this.saveExecutor);
	}

	private void saveSynchronouslyIfNeeded() {
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

	private void pollPendingSave() {
		if (this.pendingSave != null && this.pendingSave.isDone()) {
			this.completePendingSave();
			if (this.saveAfterPending) {
				this.saveAfterPending = false;
				this.scheduleSaveIfNeeded();
			}
		}
	}

	private void finishPendingSave() {
		if (this.pendingSave != null) {
			this.completePendingSave();
		}
		this.saveAfterPending = false;
	}

	private void completePendingSave() {
		String savedWorldKey = this.pendingSaveWorldKey;
		long completedVersion = this.pendingSaveVersion;
		try {
			this.pendingSave.join();
			if (savedWorldKey.equals(this.worldKey)) {
				this.savedVersion = Math.max(this.savedVersion, completedVersion);
			}
		} catch (CompletionException exception) {
			Throwable cause = exception.getCause() == null ? exception : exception.getCause();
			this.logger.error("Could not save minimap atlas for {}", savedWorldKey, cause);
			this.ticksUntilSave = Math.min(this.ticksUntilSave, 100);
		} finally {
			this.pendingSave = null;
			this.pendingSaveWorldKey = null;
			this.pendingSaveVersion = 0;
		}
	}

	@Override
	public void close() {
		this.finishPendingSave();
		this.saveSynchronouslyIfNeeded();
		this.saveExecutor.shutdown();
		try {
			this.saveExecutor.awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
		}
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
