package dev.neverket.minimap.neoforge26;

import dev.neverket.minimap.NeverketMinimapClient;
import dev.neverket.minimap.client.GuiGraphicsAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

@Mod(value = NeverketMinimapNeoForge26.MOD_ID, dist = Dist.CLIENT)
public final class NeverketMinimapNeoForge26 {
	public static final String MOD_ID = "neverket_minimap";
	private static final Identifier HUD_LAYER = Identifier.fromNamespaceAndPath(
		NeverketMinimapClient.MOD_ID, "minimap"
	);
	private static final GuiGraphicsAccess GUI_GRAPHICS_ACCESS = new GuiGraphicsAccess() {
		@Override
		public void submit(GuiGraphicsExtractor graphics, GuiElementRenderState renderState) {
			graphics.submitGuiElementRenderState(renderState);
		}

		@Override
		public ScreenRectangle peekScissor(GuiGraphicsExtractor graphics) {
			return graphics.peekScissorStack();
		}
	};

	private final NeverketMinimapClient client = new NeverketMinimapClient();

	public NeverketMinimapNeoForge26(IEventBus modEventBus, ModContainer modContainer) {
		this.client.initialize(FMLPaths.CONFIGDIR.get(), GUI_GRAPHICS_ACCESS);
		modContainer.registerExtensionPoint(
			IConfigScreenFactory.class,
			(container, parent) -> this.client.createSettingsScreen(parent)
		);
		modEventBus.addListener(this::registerKeyMappings);
		modEventBus.addListener(this::registerGuiLayers);
		NeoForge.EVENT_BUS.addListener(this::clientTick);
		NeoForge.EVENT_BUS.addListener(this::gameShuttingDown);
	}

	private void registerKeyMappings(RegisterKeyMappingsEvent event) {
		event.registerCategory(this.client.keyMappingCategory());
		this.client.keyMappings().forEach(event::register);
	}

	private void registerGuiLayers(RegisterGuiLayersEvent event) {
		event.registerAbove(VanillaGuiLayers.HOTBAR, HUD_LAYER, this.client::render);
	}

	private void clientTick(ClientTickEvent.Post event) {
		this.client.tick(Minecraft.getInstance());
	}

	private void gameShuttingDown(GameShuttingDownEvent event) {
		this.client.close();
	}
}
