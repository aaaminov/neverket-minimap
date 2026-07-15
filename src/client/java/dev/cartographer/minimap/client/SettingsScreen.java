package dev.cartographer.minimap.client;

import dev.cartographer.minimap.config.ModConfig;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class SettingsScreen extends Screen {
	private static final int BUTTON_WIDTH = 190;
	private static final int GROUP_INSET = 6;
	private static final int GROUPED_BUTTON_WIDTH = BUTTON_WIDTH - GROUP_INSET;
	private static final int BUTTON_HEIGHT = 16;
	private final ModConfig config;
	private Button mapDetailButton;
	private Button nightDarknessButton;

	public SettingsScreen(ModConfig config) {
		super(Component.translatable("screen.neverket-minimap.settings"));
		this.config = config;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - BUTTON_WIDTH - 4 + GROUP_INSET;
		int right = this.width / 2 + 4;
		int y = 29;

		this.cyclingButton(left, y, "visible", () -> onOff(this.config.visible), () -> this.config.visible = !this.config.visible);
		this.cyclingButton(right, y, "corner", () -> enumValue("corner", this.config.corner), () -> this.config.corner = this.config.corner.next());
		y += 17;
		this.cyclingButton(left, y, "size", () -> Component.translatable("value.neverket-minimap.pixels", this.config.size).getString(), () -> this.config.size = this.config.size >= 256 ? 96 : this.config.size + 32);
		this.cyclingButton(right, y, "shape", () -> enumValue("shape", this.config.shape), () -> this.config.shape = this.config.shape.next());
		y += 17;
		this.cyclingButton(left, y, "zoom", () -> Component.translatable("value.neverket-minimap.blocks_per_pixel", this.config.zoom).getString(), () -> this.config.zoom = this.config.zoom >= 32 ? 1 : this.config.zoom * 2);
		this.cyclingButton(right, y, "opacity", () -> Math.round(this.config.opacity * 100) + "%", () -> this.config.opacity = this.config.opacity >= 1.0F ? 0.25F : this.config.opacity + 0.25F);
		y += 17;
		this.cyclingButton(left, y, "coordinates", () -> onOff(this.config.showCoordinates), () -> this.config.showCoordinates = !this.config.showCoordinates);
		this.cyclingButton(right, y, "cardinals", () -> onOff(this.config.showCardinalDirections), () -> this.config.showCardinalDirections = !this.config.showCardinalDirections);

		y = 108;
		this.cyclingButton(left, y, "unknown", () -> enumValue("unknown", this.config.unknownTerrain), () -> this.config.unknownTerrain = this.config.unknownTerrain.next());
		this.cyclingButton(right, y, "map_lighting", () -> enumValue("map_lighting", this.config.mapLightingMode), () -> {
			this.config.mapLightingMode = this.config.mapLightingMode.next();
			this.updateNightDarknessButton();
		});
		y += 17;
		this.nightDarknessButton = this.cyclingButton(left, y, "night_darkness", () -> Math.round(this.config.nightDarkness * 100.0F) + "%", () ->
			this.config.nightDarkness = this.config.nightDarkness >= 1.0F ? 0.0F : Math.round((this.config.nightDarkness + 0.1F) * 10.0F) / 10.0F
		);
		this.cyclingButton(right, y, "terrain_contours", () -> onOff(this.config.showTerrainContours), () -> this.config.showTerrainContours = !this.config.showTerrainContours);
		y += 17;
		this.cyclingButton(left, y, "terrain_contour_range", () -> Component.translatable("value.neverket-minimap.chunks", this.config.terrainContourRangeChunks).getString(), () ->
			this.config.terrainContourRangeChunks = this.config.terrainContourRangeChunks >= 32 ? 4 : this.config.terrainContourRangeChunks + 4
		);

		y = 170;
		this.cyclingButton(left, y, "recording_mode", () -> enumValue("recording_mode", this.config.recordingMode), () -> {
			this.config.recordingMode = this.config.recordingMode.next();
			this.updateMapDetailButton();
		});
		this.mapDetailButton = this.cyclingButton(right, y, "map_detail_mode", () -> enumValue("map_detail_mode", this.config.mapDetailMode), () ->
			this.config.mapDetailMode = this.config.mapDetailMode.next()
		);

		y = 198;
		this.cyclingButton(left, y, "fullscreen", () -> onOff(this.config.fullscreenEnabled), () -> this.config.fullscreenEnabled = !this.config.fullscreenEnabled);
		this.cyclingButton(right, y, "pause_fullscreen", () -> onOff(this.config.pauseOnFullscreenMap), () -> this.config.pauseOnFullscreenMap = !this.config.pauseOnFullscreenMap);
		this.updateMapDetailButton();
		this.updateNightDarknessButton();

		this.addRenderableWidget(
			Button.builder(Component.translatable("screen.neverket-minimap.markers_button"), button ->
				this.minecraft.gui.setScreen(new MarkerSettingsScreen(this, this.config))
			).bounds(this.width / 2 - 100, this.height - 22, 96, 20).build()
		);
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.done"), button -> this.onClose())
				.bounds(this.width / 2 + 4, this.height - 22, 96, 20).build()
		);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);
		this.drawGroupTitle(graphics, "minimap", 20);
		this.drawGroupTitle(graphics, "map_appearance", 99);
		this.drawGroupTitle(graphics, "recording", 161);
		this.drawGroupTitle(graphics, "fullscreen", 189);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == GLFW.GLFW_KEY_N) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	private void drawGroupTitle(GuiGraphicsExtractor graphics, String key, int y) {
		graphics.text(this.font, Component.translatable("group.neverket-minimap." + key), this.width / 2 - BUTTON_WIDTH, y, 0xFFA0A0A0, false);
	}

	private Button cyclingButton(int x, int y, String key, Value value, Runnable change) {
		Button button = Button.builder(label(key, value.get()), pressed -> {
			change.run();
			this.config.changed();
			pressed.setMessage(label(key, value.get()));
		}).bounds(x, y, GROUPED_BUTTON_WIDTH, BUTTON_HEIGHT).build();
		this.addRenderableWidget(button);
		return button;
	}

	private static Component label(String key, String value) {
		return Component.translatable("option.neverket-minimap." + key).append(": " + value);
	}

	private static String onOff(boolean value) {
		return Component.translatable(value ? "options.on" : "options.off").getString();
	}

	private void updateMapDetailButton() {
		if (this.mapDetailButton != null) {
			this.mapDetailButton.active = this.config.recordingMode == ModConfig.RecordingMode.MAPS;
		}
	}

	private void updateNightDarknessButton() {
		if (this.nightDarknessButton != null) {
			this.nightDarknessButton.active = this.config.mapLightingMode == ModConfig.MapLightingMode.DAY_NIGHT;
		}
	}

	private static String enumValue(String key, Enum<?> value) {
		return Component.translatable("value.neverket-minimap." + key + "." + value.name().toLowerCase(Locale.ROOT)).getString();
	}

	@FunctionalInterface
	private interface Value {
		String get();
	}
}
