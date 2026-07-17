package dev.neverket.minimap.client;

import dev.neverket.minimap.config.ModConfig;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class SettingsScreen extends OptionsSubScreen {
	private final ModConfig config;
	private AbstractWidget mapDetailWidget;
	private AbstractWidget nightDarknessWidget;
	private AbstractWidget minimapBorderColorWidget;

	public SettingsScreen(ModConfig config) {
		super(null, Minecraft.getInstance().options, Component.translatable("screen.neverket-minimap.settings"));
		this.config = config;
	}

	@Override
	protected void addOptions() {
		this.list.addHeader(Component.translatable("group.neverket-minimap.minimap"));
		this.list.addSmall(
			this.toggleButton("visible", () -> this.config.visible, value -> this.config.visible = value),
			this.cycleButton("corner", () -> enumValue("corner", this.config.corner), () -> this.config.corner = this.config.corner.next())
		);
		this.list.addSmall(
			this.intSlider("size", this.config.size, 96, 256, 8, value -> this.config.size = value,
				value -> Component.translatable("value.neverket-minimap.pixels", value).getString()),
			this.cycleButton("shape", () -> enumValue("shape", this.config.shape), () -> this.config.shape = this.config.shape.next())
		);
		this.list.addSmall(
			this.intSlider("zoom", this.config.zoom, 1, 32, 1, value -> this.config.zoom = value,
				value -> Component.translatable("value.neverket-minimap.blocks_per_pixel", value).getString()),
			this.intSlider("opacity", Math.round(this.config.opacity * 100.0F), 25, 100, 5,
				value -> this.config.opacity = value / 100.0F, value -> value + "%")
		);
		this.list.addSmall(
			this.toggleButton("coordinates", () -> this.config.showCoordinates, value -> this.config.showCoordinates = value),
			this.toggleButton("cardinals", () -> this.config.showCardinalDirections, value -> this.config.showCardinalDirections = value)
		);
		this.minimapBorderColorWidget = this.cycleButton("minimap_border_color", () -> enumValue("minimap_border_color", this.config.minimapBorderColor), () ->
			this.config.minimapBorderColor = this.config.minimapBorderColor.next());
		this.list.addSmall(
			this.toggleButton("minimap_border", () -> this.config.showMinimapBorder, value -> {
				this.config.showMinimapBorder = value;
				this.updateDependentWidgets();
			}),
			this.minimapBorderColorWidget
		);

		this.list.addHeader(Component.translatable("group.neverket-minimap.map_appearance"));
		this.list.addSmall(
			this.cycleButton("unknown", () -> enumValue("unknown", this.config.unknownTerrain), () -> this.config.unknownTerrain = this.config.unknownTerrain.next()),
			this.cycleButton("map_lighting", () -> enumValue("map_lighting", this.config.mapLightingMode), () -> {
				this.config.mapLightingMode = this.config.mapLightingMode.next();
				this.updateDependentWidgets();
			})
		);
		this.nightDarknessWidget = this.intSlider("night_darkness", Math.round(this.config.nightDarkness * 100.0F), 0, 100, 5,
			value -> this.config.nightDarkness = value / 100.0F, value -> value + "%");
		this.list.addSmall(
			this.nightDarknessWidget,
			this.toggleButton("terrain_contours", () -> this.config.showTerrainContours, value -> this.config.showTerrainContours = value)
		);
		this.list.addBig(this.intSlider("terrain_contour_range", this.config.terrainContourRangeChunks, 2, 32, 1,
			value -> this.config.terrainContourRangeChunks = value,
			value -> Component.translatable("value.neverket-minimap.chunks", value).getString()));

		this.list.addHeader(Component.translatable("group.neverket-minimap.recording"));
		this.mapDetailWidget = this.cycleButton("map_detail_mode", () -> enumValue("map_detail_mode", this.config.mapDetailMode), () ->
			this.config.mapDetailMode = this.config.mapDetailMode.next()
		);
		this.list.addSmall(
			this.cycleButton("recording_mode", () -> enumValue("recording_mode", this.config.recordingMode), () -> {
				this.config.recordingMode = this.config.recordingMode.next();
				this.updateDependentWidgets();
			}),
			this.mapDetailWidget
		);

		this.list.addHeader(Component.translatable("group.neverket-minimap.fullscreen"));
		this.list.addSmall(
			this.toggleButton("fullscreen", () -> this.config.fullscreenEnabled, value -> this.config.fullscreenEnabled = value),
			this.toggleButton("pause_fullscreen", () -> this.config.pauseOnFullscreenMap, value -> this.config.pauseOnFullscreenMap = value)
		);
		this.list.addSmall(
			this.cycleButton("biome_highlight_color", () -> enumValue("biome_highlight_color", this.config.biomeHighlightColor), () ->
				this.config.biomeHighlightColor = this.config.biomeHighlightColor.next()),
			this.intSlider("biome_highlight_opacity", Math.round(this.config.biomeHighlightOpacity * 100.0F), 5, 100, 5,
				value -> this.config.biomeHighlightOpacity = value / 100.0F, value -> value + "%")
		);
		this.list.addSmall(
			this.toggleButton("cursor_biome", () -> this.config.showCursorBiome, value -> this.config.showCursorBiome = value),
			this.toggleButton("recording_area_border", () -> this.config.showRecordingAreaOnBiomeHighlight,
				value -> this.config.showRecordingAreaOnBiomeHighlight = value)
		);

		this.list.addHeader(Component.translatable("group.neverket-minimap.markers"));
		this.list.addBig(this.navigationButton());
		this.updateDependentWidgets();
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
	public void removed() {
		this.config.save();
		super.removed();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		this.extractBlurredBackground(graphics);
		graphics.fill(0, 0, this.width, this.height, 0x72000000);
	}

	private AbstractWidget toggleButton(String key, Supplier<Boolean> current, java.util.function.Consumer<Boolean> change) {
		return this.configButton(key, () -> onOff(current.get()), () -> change.accept(!current.get()));
	}

	private AbstractWidget cycleButton(String key, Supplier<String> value, Runnable change) {
		return this.configButton(key, value, change);
	}

	private AbstractWidget configButton(String key, Supplier<String> value, Runnable change) {
		Button button = Button.builder(label(key, value.get()), pressed -> {
			change.run();
			pressed.setMessage(label(key, value.get()));
		}).build();
		this.setDescription(button, key, false);
		return button;
	}

	private AbstractWidget intSlider(
		String key,
		int initial,
		int minimum,
		int maximum,
		int step,
		IntConsumer change,
		IntFunction<String> format
	) {
		IntSlider slider = new IntSlider(key, initial, minimum, maximum, step, change, format);
		this.setDescription(slider, key, false);
		return slider;
	}

	private AbstractWidget navigationButton() {
		Button button = Button.builder(Component.translatable("screen.neverket-minimap.markers_button"), pressed ->
			this.minecraft.gui.setScreen(new MarkerSettingsScreen(this, this.config))
		).build();
		button.setTooltip(Tooltip.create(Component.translatable("description.neverket-minimap.markers_button")));
		return button;
	}

	private void updateDependentWidgets() {
		if (this.nightDarknessWidget != null) {
			boolean active = this.config.mapLightingMode == ModConfig.MapLightingMode.DAY_NIGHT;
			this.nightDarknessWidget.active = active;
			this.setDescription(this.nightDarknessWidget, "night_darkness", !active);
		}
		if (this.mapDetailWidget != null) {
			boolean active = this.config.recordingMode == ModConfig.RecordingMode.MAPS;
			this.mapDetailWidget.active = active;
			this.setDescription(this.mapDetailWidget, "map_detail_mode", !active);
		}
		if (this.minimapBorderColorWidget != null) {
			this.minimapBorderColorWidget.active = this.config.showMinimapBorder;
			this.setDescription(this.minimapBorderColorWidget, "minimap_border_color", !this.config.showMinimapBorder);
		}
	}

	private void setDescription(AbstractWidget widget, String key, boolean disabled) {
		String suffix = disabled ? ".disabled" : "";
		widget.setTooltip(Tooltip.create(Component.translatable("description.neverket-minimap." + key + suffix)));
	}

	private static Component label(String key, String value) {
		return Component.translatable("option.neverket-minimap." + key).append(": " + value);
	}

	private static String onOff(boolean value) {
		return Component.translatable(value ? "options.on" : "options.off").getString();
	}

	private static String enumValue(String key, Enum<?> value) {
		return Component.translatable("value.neverket-minimap." + key + "." + value.name().toLowerCase(Locale.ROOT)).getString();
	}

	private static final class IntSlider extends AbstractSliderButton {
		private final String key;
		private final int minimum;
		private final int maximum;
		private final int step;
		private final IntConsumer change;
		private final IntFunction<String> format;
		private int current;

		private IntSlider(String key, int initial, int minimum, int maximum, int step, IntConsumer change, IntFunction<String> format) {
			super(0, 0, 150, 20, Component.empty(), normalize(initial, minimum, maximum));
			this.key = key;
			this.minimum = minimum;
			this.maximum = maximum;
			this.step = step;
			this.change = change;
			this.format = format;
			this.current = snap(initial, minimum, maximum, step);
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			if (this.format != null) {
				this.setMessage(label(this.key, this.format.apply(this.current)));
			}
		}

		@Override
		protected void applyValue() {
			this.current = snap(
				(int)Math.round(this.minimum + this.value * (this.maximum - this.minimum)),
				this.minimum,
				this.maximum,
				this.step
			);
			this.value = normalize(this.current, this.minimum, this.maximum);
			this.change.accept(this.current);
			this.updateMessage();
		}

		private static int snap(int value, int minimum, int maximum, int step) {
			int steps = Math.round((value - minimum) / (float)step);
			return Math.clamp(minimum + steps * step, minimum, maximum);
		}

		private static double normalize(int value, int minimum, int maximum) {
			return Math.clamp((value - minimum) / (double)Math.max(1, maximum - minimum), 0.0, 1.0);
		}
	}
}
