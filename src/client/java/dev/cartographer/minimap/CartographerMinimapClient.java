package dev.cartographer.minimap;

import com.mojang.blaze3d.platform.InputConstants;
import dev.cartographer.minimap.client.FullscreenMapScreen;
import dev.cartographer.minimap.client.MinimapRenderer;
import dev.cartographer.minimap.client.SettingsScreen;
import dev.cartographer.minimap.client.WorldSession;
import dev.cartographer.minimap.config.ModConfig;
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

public final class CartographerMinimapClient implements ClientModInitializer {
	public static final String MOD_ID = "cartographer-minimap";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private ModConfig config;
	private WorldSession session;
	private MinimapRenderer renderer;
	private KeyMapping toggleKey;
	private KeyMapping zoomKey;
	private KeyMapping fullscreenKey;
	private KeyMapping settingsKey;

	@Override
	public void onInitializeClient() {
		Minecraft minecraft = Minecraft.getInstance();
		var configDirectory = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
		this.config = ModConfig.load(configDirectory.resolve("config.json"));
		this.session = new WorldSession(configDirectory.resolve("worlds"), LOGGER);
		this.renderer = new MinimapRenderer(minecraft, this.session, this.config);

		KeyMapping.Category category = KeyMapping.Category.register(id("controls"));
		this.toggleKey = register("toggle", GLFW.GLFW_KEY_H, category);
		this.zoomKey = register("zoom", GLFW.GLFW_KEY_EQUAL, category);
		this.fullscreenKey = register("fullscreen", GLFW.GLFW_KEY_M, category);
		this.settingsKey = register("settings", GLFW.GLFW_KEY_K, category);

		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("minimap"), (graphics, deltaTracker) -> this.renderer.render(graphics));
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> this.close());
		LOGGER.info("Cartographer Minimap initialized");
	}

	private void tick(Minecraft minecraft) {
		this.session.tick(minecraft);
		while (this.toggleKey.consumeClick()) {
			this.config.visible = !this.config.visible;
			this.config.changed();
		}
		while (this.zoomKey.consumeClick()) {
			this.config.zoom = this.config.zoom >= 32 ? 1 : this.config.zoom * 2;
			this.config.changed();
		}
		while (this.fullscreenKey.consumeClick()) {
			if (this.config.fullscreenEnabled && minecraft.level != null) {
				minecraft.gui.setScreen(new FullscreenMapScreen(this.session, this.config));
			}
		}
		while (this.settingsKey.consumeClick()) {
			minecraft.gui.setScreen(new SettingsScreen(this.config));
		}
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
}
