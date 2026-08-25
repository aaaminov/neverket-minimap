package dev.neverket.minimap.fabric;

import dev.neverket.minimap.NeverketMinimapClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

public final class NeverketMinimapFabric implements ClientModInitializer {
	private static final NeverketMinimapClient CLIENT = new NeverketMinimapClient();

	@Override
	public void onInitializeClient() {
		CLIENT.initialize(FabricLoader.getInstance().getConfigDir());
		CLIENT.keyMappings().forEach(KeyBindingHelper::registerKeyBinding);
		ClientTickEvents.END_CLIENT_TICK.register(CLIENT::tick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> CLIENT.close());
	}

	public static void renderHud(GuiGraphics graphics, DeltaTracker deltaTracker) {
		CLIENT.render(graphics, deltaTracker);
	}

	static Screen createSettingsScreen(Screen parent) {
		return CLIENT.createSettingsScreen(parent);
	}
}
