package dev.cartographer.minimap.client;

import dev.cartographer.minimap.config.ModConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class SettingsScreen extends Screen {
	private static final int BUTTON_WIDTH = 190;
	private final ModConfig config;
	private Button mapDetailButton;
	private Button nightDarknessButton;

	public SettingsScreen(ModConfig config) {
		super(Component.translatable("screen.neverket-minimap.settings"));
		this.config = config;
	}

	@Override
	protected void init() {
		int left = this.width / 2 - BUTTON_WIDTH - 4;
		int right = this.width / 2 + 4;
		int y = 25;

		this.cyclingButton(left, y, "corner", () -> this.config.corner.name(), () -> this.config.corner = this.config.corner.next());
		this.cyclingButton(right, y, "size", () -> this.config.size + " px", () -> this.config.size = this.config.size >= 256 ? 96 : this.config.size + 32);
		y += 20;
		this.cyclingButton(left, y, "shape", () -> this.config.shape.name(), () -> this.config.shape = this.config.shape.next());
		this.cyclingButton(right, y, "opacity", () -> Math.round(this.config.opacity * 100) + "%", () -> this.config.opacity = this.config.opacity >= 1.0F ? 0.25F : this.config.opacity + 0.25F);
		y += 20;
		this.cyclingButton(left, y, "zoom", () -> this.config.zoom + " blocks/px", () -> this.config.zoom = this.config.zoom >= 32 ? 1 : this.config.zoom * 2);
		this.cyclingButton(right, y, "coordinates", () -> onOff(this.config.showCoordinates), () -> this.config.showCoordinates = !this.config.showCoordinates);
		y += 20;
		this.cyclingButton(left, y, "cardinals", () -> onOff(this.config.showCardinalDirections), () -> this.config.showCardinalDirections = !this.config.showCardinalDirections);
		this.cyclingButton(right, y, "unknown", () -> this.config.unknownTerrain.name(), () -> this.config.unknownTerrain = this.config.unknownTerrain.next());
		y += 20;
		this.cyclingButton(left, y, "fullscreen", () -> onOff(this.config.fullscreenEnabled), () -> this.config.fullscreenEnabled = !this.config.fullscreenEnabled);
		this.cyclingButton(right, y, "visible", () -> onOff(this.config.visible), () -> this.config.visible = !this.config.visible);
		y += 20;
		this.cyclingButton(left, y, "terrain_contours", () -> onOff(this.config.showTerrainContours), () -> this.config.showTerrainContours = !this.config.showTerrainContours);
		this.cyclingButton(right, y, "terrain_contour_range", () -> this.config.terrainContourRangeChunks + " chunks", () -> {
			this.config.terrainContourRangeChunks = this.config.terrainContourRangeChunks >= 32 ? 4 : this.config.terrainContourRangeChunks + 4;
		});
		y += 20;
		this.cyclingButton(left, y, "recording_mode", () -> enumValue("recording_mode", this.config.recordingMode), () -> {
			this.config.recordingMode = this.config.recordingMode.next();
			this.updateMapDetailButton();
		});
		this.mapDetailButton = this.cyclingButton(
			right,
			y,
			"map_detail_mode",
			() -> enumValue("map_detail_mode", this.config.mapDetailMode),
			() -> this.config.mapDetailMode = this.config.mapDetailMode.next()
		);
		this.updateMapDetailButton();
		y += 20;
		this.cyclingButton(
			left,
			y,
			"cursor_biome",
			() -> onOff(this.config.showCursorBiome),
			() -> this.config.showCursorBiome = !this.config.showCursorBiome
		);
		this.cyclingButton(
			right,
			y,
			"map_lighting",
			() -> enumValue("map_lighting", this.config.mapLightingMode),
			() -> {
				this.config.mapLightingMode = this.config.mapLightingMode.next();
				this.updateNightDarknessButton();
			}
		);
		y += 20;
		this.nightDarknessButton = this.cyclingButton(
			left,
			y,
			"night_darkness",
			() -> Math.round(this.config.nightDarkness * 100.0F) + "%",
			() -> this.config.nightDarkness = this.config.nightDarkness >= 0.75F ? 0.15F : this.config.nightDarkness + 0.15F
		);
		this.cyclingButton(
			right,
			y,
			"pause_fullscreen",
			() -> onOff(this.config.pauseOnFullscreenMap),
			() -> this.config.pauseOnFullscreenMap = !this.config.pauseOnFullscreenMap
		);
		this.updateNightDarknessButton();

		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose()).bounds(this.width / 2 - 100, this.height - 32, 200, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		graphics.centeredText(this.font, this.title, this.width / 2, 10, 0xFFFFFFFF);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	private Button cyclingButton(int x, int y, String key, Value value, Runnable change) {
		Button button = Button.builder(label(key, value.get()), pressed -> {
			change.run();
			this.config.changed();
			pressed.setMessage(label(key, value.get()));
		}).bounds(x, y, BUTTON_WIDTH, 20).build();
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
		return Component.translatable("value.neverket-minimap." + key + "." + value.name().toLowerCase(java.util.Locale.ROOT)).getString();
	}

	@FunctionalInterface
	private interface Value {
		String get();
	}
}
