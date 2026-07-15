package dev.cartographer.minimap.client;

import dev.cartographer.minimap.config.ModConfig;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class MarkerSettingsScreen extends Screen {
	private final Screen parent;
	private final ModConfig config;
	private final MapMarkerRenderer markerRenderer;

	public MarkerSettingsScreen(Screen parent, ModConfig config) {
		super(Component.translatable("screen.neverket-minimap.marker_settings"));
		this.parent = parent;
		this.config = config;
		this.markerRenderer = new MapMarkerRenderer(this.minecraft);
	}

	@Override
	protected void init() {
		int x = this.width / 2 - 114;
		int y = this.height / 2 - 38;
		this.cyclingButton(x, y, "quick_marker_icon", () -> Component.translatable(
			"value.neverket-minimap.quick_marker_icon." + this.config.quickMarkerIcon.name().toLowerCase(Locale.ROOT)
		).getString(), () -> this.config.quickMarkerIcon = this.config.quickMarkerIcon.next());
		this.cyclingButton(x, y + 38, "edge_banner_markers", () -> Integer.toString(this.config.maxEdgeBannerMarkers), () ->
			this.config.maxEdgeBannerMarkers = this.config.maxEdgeBannerMarkers >= 10 ? 0 : this.config.maxEdgeBannerMarkers + 1
		);
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.done"), button -> this.onClose()).bounds(x + 14, y + 70, 200, 20).build()
		);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.centeredText(this.font, this.title, this.width / 2, this.height / 2 - 78, 0xFFFFFFFF);
		graphics.text(this.font, Component.translatable("group.neverket-minimap.quick_marker"), this.width / 2 - 120, this.height / 2 - 60, 0xFFA0A0A0, false);
		this.markerRenderer.drawQuickIcon(graphics, this.config.quickMarkerIcon, this.width / 2, this.height / 2 - 48);
		graphics.text(this.font, Component.translatable("group.neverket-minimap.edge_markers"), this.width / 2 - 120, this.height / 2 - 10, 0xFFA0A0A0, false);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_N) {
			this.minecraft.gui.setScreen(null);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	private void cyclingButton(int x, int y, String key, Value value, Runnable change) {
		Button button = Button.builder(label(key, value.get()), pressed -> {
			change.run();
			this.config.changed();
			pressed.setMessage(label(key, value.get()));
		}).bounds(x, y, 228, 20).build();
		this.addRenderableWidget(button);
	}

	private static Component label(String key, String value) {
		return Component.translatable("option.neverket-minimap." + key).append(": " + value);
	}

	@FunctionalInterface
	private interface Value {
		String get();
	}
}
