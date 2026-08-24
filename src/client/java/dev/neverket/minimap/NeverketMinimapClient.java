package dev.neverket.minimap;

import com.mojang.blaze3d.platform.InputConstants;
import dev.neverket.minimap.client.FullscreenMapScreen;
import dev.neverket.minimap.client.MarkerSettingsScreen;
import dev.neverket.minimap.client.MinimapRenderer;
import dev.neverket.minimap.client.SettingsScreen;
import dev.neverket.minimap.client.WorldSession;
import dev.neverket.minimap.config.ModConfig;
import dev.neverket.minimap.marker.QuickMarker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loader-independent client controller shared by Fabric and NeoForge. */
public final class NeverketMinimapClient implements AutoCloseable {
	public static final String MOD_ID = "neverket-minimap";
	private static final String LEGACY_MOD_ID = "cartographer-minimap";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private ModConfig config;
	private WorldSession session;
	private MinimapRenderer renderer;
	private KeyMapping toggleKey;
	private KeyMapping zoomKey;
	private KeyMapping fullscreenKey;
	private KeyMapping settingsKey;
	private KeyMapping biomeHighlightKey;
	private KeyMapping chunkDebugKey;
	private boolean quickMarkerShortcutDown;
	private boolean closed;

	public void initialize(Path configRoot) {
		Path configDirectory = configRoot.resolve(MOD_ID);
		migrateLegacyConfig(configRoot.resolve(LEGACY_MOD_ID), configDirectory);
		this.config = ModConfig.load(configDirectory.resolve("config.json"));
		this.session = new WorldSession(configDirectory.resolve("worlds"), LOGGER, this.config);

		String category = "key.category." + MOD_ID + ".controls";
		this.toggleKey = register("toggle", GLFW.GLFW_KEY_H, category);
		this.zoomKey = register("zoom", GLFW.GLFW_KEY_EQUAL, category);
		this.fullscreenKey = register("fullscreen", GLFW.GLFW_KEY_M, category);
		this.settingsKey = register("settings", GLFW.GLFW_KEY_N, category);
		this.biomeHighlightKey = register("biome_highlight", GLFW.GLFW_KEY_SPACE, category);
		this.chunkDebugKey = register("chunk_debug", GLFW.GLFW_KEY_LEFT_CONTROL, category);

		LOGGER.info("Neverket Minimap initialized");
	}

	public List<KeyMapping> keyMappings() {
		return List.of(this.toggleKey, this.zoomKey, this.fullscreenKey, this.settingsKey, this.biomeHighlightKey, this.chunkDebugKey);
	}

	public void tick(Minecraft minecraft) {
		this.session.tick(minecraft);
		long window = minecraft.getWindow().getWindow();
		boolean controlDown = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
			|| InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
		boolean quickMarkerShortcut = controlDown && InputConstants.isKeyDown(window, GLFW.GLFW_KEY_M);
		while (this.toggleKey.consumeClick()) {
			this.config.visible = !this.config.visible;
			this.config.changed();
		}
		while (this.zoomKey.consumeClick()) {
			this.config.zoom = this.config.zoom >= 32 ? 1 : this.config.zoom * 2;
			this.config.changed();
		}
		while (this.fullscreenKey.consumeClick()) {
			if (!controlDown && this.config.fullscreenEnabled && minecraft.level != null) {
				minecraft.setScreen(minecraft.screen instanceof FullscreenMapScreen
					? null
					: new FullscreenMapScreen(this.session, this.config, this.biomeHighlightKey, this.chunkDebugKey));
			}
		}
		while (this.settingsKey.consumeClick()) {
			boolean settingsOpen = minecraft.screen instanceof SettingsScreen
				|| minecraft.screen instanceof MarkerSettingsScreen;
			minecraft.setScreen(settingsOpen ? null : new SettingsScreen(this.config));
		}
		if (quickMarkerShortcut && !this.quickMarkerShortcutDown && minecraft.screen == null
			&& minecraft.player != null && minecraft.level != null) {
			if (this.session.atlas().quickMarker().isPresent()) {
				this.session.atlas().removeQuickMarker();
			} else {
				this.session.atlas().putQuickMarker(new QuickMarker(
					minecraft.level.dimension().location().toString(),
					(int)Math.floor(minecraft.player.getX()),
					(int)Math.floor(minecraft.player.getZ()),
					System.currentTimeMillis()
				));
			}
			this.session.saveNow();
		}
		this.quickMarkerShortcutDown = quickMarkerShortcut;
	}

	public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
		if (this.renderer == null) {
			Minecraft minecraft = Minecraft.getInstance();
			if (minecraft == null) {
				return;
			}
			this.renderer = new MinimapRenderer(minecraft, this.session, this.config);
		}
		this.renderer.render(graphics, deltaTracker);
	}

	public Screen createSettingsScreen(Screen parent) {
		return new SettingsScreen(parent, this.config);
	}

	@Override
	public void close() {
		if (this.closed) {
			return;
		}
		this.closed = true;
		this.session.close();
		if (this.renderer != null) {
			this.renderer.close();
		}
		this.config.save();
	}

	private static KeyMapping register(String name, int key, String category) {
		return new KeyMapping("key." + MOD_ID + "." + name, InputConstants.Type.KEYSYM, key, category);
	}

	private static void migrateLegacyConfig(Path legacyDirectory, Path configDirectory) {
		if (!Files.isDirectory(legacyDirectory)) {
			return;
		}
		try {
			if (!Files.exists(configDirectory)) {
				Files.move(legacyDirectory, configDirectory);
				LOGGER.info("Migrated minimap data from {} to {}", legacyDirectory, configDirectory);
				return;
			}
			try (var paths = Files.walk(legacyDirectory)) {
				var iterator = paths.iterator();
				while (iterator.hasNext()) {
					Path source = iterator.next();
					Path target = configDirectory.resolve(legacyDirectory.relativize(source));
					if (Files.isDirectory(source)) {
						Files.createDirectories(target);
					} else if (!Files.exists(target)) {
						Files.copy(source, target);
					}
				}
			}
			LOGGER.info("Merged missing legacy minimap data from {} into {}", legacyDirectory, configDirectory);
		} catch (IOException exception) {
			LOGGER.warn("Could not fully migrate legacy minimap data", exception);
		}
	}
}
