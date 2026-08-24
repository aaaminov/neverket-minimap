package dev.neverket.minimap.fabric26;

import dev.neverket.minimap.NeverketMinimapClient;
import dev.neverket.minimap.client.GuiGraphicsAccess;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

public final class NeverketMinimapFabric26 implements ClientModInitializer {
	private static final GuiGraphicsAccess GUI_GRAPHICS_ACCESS = new GuiGraphicsAccess() {
		@Override
		public void submit(net.minecraft.client.gui.GuiGraphicsExtractor graphics,
				net.minecraft.client.renderer.state.gui.GuiElementRenderState renderState) {
			graphics.guiRenderState.addGuiElement(renderState);
		}

		@Override
		public net.minecraft.client.gui.navigation.ScreenRectangle peekScissor(
				net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
			return graphics.scissorStack.peek();
		}
	};
	private static final NeverketMinimapClient CLIENT = new NeverketMinimapClient();

	@Override
	public void onInitializeClient() {
		CLIENT.initialize(FabricLoader.getInstance().getConfigDir(), GUI_GRAPHICS_ACCESS);
		CLIENT.keyMappings().forEach(KeyMappingHelper::registerKeyMapping);
		ClientTickEvents.END_CLIENT_TICK.register(CLIENT::tick);
		HudElementRegistry.attachElementBefore(
			VanillaHudElements.HOTBAR,
			Identifier.fromNamespaceAndPath(NeverketMinimapClient.MOD_ID, "minimap"),
			CLIENT::render
		);
		ClientLifecycleEvents.CLIENT_STOPPING.register(minecraft -> CLIENT.close());
	}

	static Screen createSettingsScreen(Screen parent) {
		return CLIENT.createSettingsScreen(parent);
	}
}
