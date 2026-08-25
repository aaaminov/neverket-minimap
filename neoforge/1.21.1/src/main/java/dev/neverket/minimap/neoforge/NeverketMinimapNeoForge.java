package dev.neverket.minimap.neoforge;

import dev.neverket.minimap.NeverketMinimapClient;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
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

@Mod(value = NeverketMinimapNeoForge.MOD_ID, dist = Dist.CLIENT)
public final class NeverketMinimapNeoForge {
	public static final String MOD_ID = "neverket_minimap";
	private static final ResourceLocation HUD_LAYER = ResourceLocation.fromNamespaceAndPath(
		NeverketMinimapClient.MOD_ID, "minimap"
	);

	private final NeverketMinimapClient client = new NeverketMinimapClient();

	public NeverketMinimapNeoForge(IEventBus modEventBus, ModContainer modContainer) {
		this.client.initialize(FMLPaths.CONFIGDIR.get());
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
		this.client.keyMappings().forEach(event::register);
	}

	private void registerGuiLayers(RegisterGuiLayersEvent event) {
		event.registerBelow(VanillaGuiLayers.CAMERA_OVERLAYS, HUD_LAYER, this.client::render);
	}

	private void clientTick(ClientTickEvent.Post event) {
		this.client.tick(Minecraft.getInstance());
	}

	private void gameShuttingDown(GameShuttingDownEvent event) {
		this.client.close();
	}
}
