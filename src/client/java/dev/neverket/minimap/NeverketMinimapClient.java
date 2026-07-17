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
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NeverketMinimapClient implements ClientModInitializer {
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

	@Override
	public void onInitializeClient() {
		Minecraft minecraft = Minecraft.getInstance();
		Path configRoot = FabricLoader.getInstance().getConfigDir();
		Path configDirectory = configRoot.resolve(MOD_ID);
		migrateLegacyConfig(configRoot.resolve(LEGACY_MOD_ID), configDirectory);
		this.config = ModConfig.load(configDirectory.resolve("config.json"));
		this.session = new WorldSession(configDirectory.resolve("worlds"), LOGGER, this.config);
		this.renderer = new MinimapRenderer(minecraft, this.session, this.config);

		KeyMapping.Category category = KeyMapping.Category.register(id("controls"));
		this.toggleKey = register("toggle", GLFW.GLFW_KEY_H, category);
		this.zoomKey = register("zoom", GLFW.GLFW_KEY_EQUAL, category);
		this.fullscreenKey = register("fullscreen", GLFW.GLFW_KEY_M, category);
		this.settingsKey = register("settings", GLFW.GLFW_KEY_N, category);
		this.biomeHighlightKey = register("biome_highlight", GLFW.GLFW_KEY_SPACE, category);
		this.chunkDebugKey = register("chunk_debug", GLFW.GLFW_KEY_LEFT_CONTROL, category);

		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.HOTBAR, id("minimap"), this.renderer::render);
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> this.close());
		LOGGER.info("Neverket Minimap initialized");
	}

	private void tick(Minecraft minecraft) {
		this.session.tick(minecraft);
		boolean controlDown = InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
			|| InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);
		boolean quickMarkerShortcut = controlDown && InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_M);
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
				minecraft.gui.setScreen(minecraft.gui.screen() instanceof FullscreenMapScreen
					? null
					: new FullscreenMapScreen(this.session, this.config, this.biomeHighlightKey, this.chunkDebugKey));
			}
		}
		while (this.settingsKey.consumeClick()) {
			boolean settingsOpen = minecraft.gui.screen() instanceof SettingsScreen
				|| minecraft.gui.screen() instanceof MarkerSettingsScreen;
			minecraft.gui.setScreen(settingsOpen ? null : new SettingsScreen(this.config));
		}
		if (quickMarkerShortcut && !this.quickMarkerShortcutDown && minecraft.gui.screen() == null
			&& minecraft.player != null && minecraft.level != null) {
			if (this.session.atlas().quickMarker().isPresent()) {
				this.session.atlas().removeQuickMarker();
			} else {
				this.session.atlas().putQuickMarker(new QuickMarker(
					minecraft.level.dimension().identifier().toString(),
					(int)Math.floor(minecraft.player.getX()),
					(int)Math.floor(minecraft.player.getZ()),
					System.currentTimeMillis()
				));
			}
			this.session.saveNow();
		}
		this.quickMarkerShortcutDown = quickMarkerShortcut;
	}

	private void close() {
		this.session.close();
		this.renderer.close();
		this.config.save();
	}

	private static KeyMapping register(String name, int key, KeyMapping.Category category) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + MOD_ID + "." + name, InputConstants.Type.KEYSYM, key, category));
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
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
