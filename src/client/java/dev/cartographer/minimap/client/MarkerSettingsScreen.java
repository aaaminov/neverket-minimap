package dev.cartographer.minimap.client;

import dev.cartographer.minimap.config.ModConfig;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class MarkerSettingsScreen extends OptionsSubScreen {
	private final ModConfig config;
	private final MapMarkerRenderer markerRenderer;

	public MarkerSettingsScreen(Screen parent, ModConfig config) {
		super(parent, Minecraft.getInstance().options, Component.translatable("screen.neverket-minimap.marker_settings"));
		this.config = config;
		this.markerRenderer = new MapMarkerRenderer(Minecraft.getInstance());
	}

	@Override
	protected void addOptions() {
		this.list.addHeader(Component.translatable("group.neverket-minimap.quick_marker"));
		ModConfig.QuickMarkerIcon[] icons = ModConfig.QuickMarkerIcon.values();
		for (int index = 0; index < icons.length; index += 2) {
			IconChoiceButton left = this.iconButton(icons[index]);
			IconChoiceButton right = index + 1 < icons.length ? this.iconButton(icons[index + 1]) : null;
			if (right == null) {
				this.list.addBig(left);
			} else {
				this.list.addSmall(left, right);
			}
		}

		this.list.addHeader(Component.translatable("group.neverket-minimap.edge_markers"));
		EdgeMarkerSlider edgeMarkers = new EdgeMarkerSlider(this.config);
		edgeMarkers.setTooltip(Tooltip.create(Component.translatable("description.neverket-minimap.edge_banner_markers")));
		this.list.addBig(edgeMarkers);
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
	public void removed() {
		this.config.save();
		super.removed();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		this.extractBlurredBackground(graphics);
		graphics.fill(0, 0, this.width, this.height, 0x72000000);
	}

	private IconChoiceButton iconButton(ModConfig.QuickMarkerIcon icon) {
		IconChoiceButton button = new IconChoiceButton(icon);
		button.setOverrideRenderHighlightedSprite(() -> this.config.quickMarkerIcon == icon);
		button.setTooltip(Tooltip.create(Component.translatable("description.neverket-minimap.quick_marker_icon")));
		return button;
	}

	private final class IconChoiceButton extends AbstractButton {
		private final ModConfig.QuickMarkerIcon icon;

		private IconChoiceButton(ModConfig.QuickMarkerIcon icon) {
			super(0, 0, 150, 20, Component.translatable(
				"value.neverket-minimap.quick_marker_icon." + icon.name().toLowerCase(Locale.ROOT)
			));
			this.icon = icon;
		}

		@Override
		public void onPress(InputWithModifiers input) {
			MarkerSettingsScreen.this.config.quickMarkerIcon = this.icon;
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			this.extractDefaultSprite(graphics);
			MarkerSettingsScreen.this.markerRenderer.drawQuickIcon(graphics, this.icon, this.getX() + 13, this.getY() + this.getHeight() / 2);
			graphics.centeredText(
				MarkerSettingsScreen.this.font,
				this.getMessage(),
				this.getX() + this.getWidth() / 2 + 7,
				this.getY() + (this.getHeight() - 8) / 2,
				this.active ? 0xFFFFFFFF : 0xFFA0A0A0
			);
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			this.defaultButtonNarrationText(output);
		}
	}

	private static final class EdgeMarkerSlider extends AbstractSliderButton {
		private final ModConfig config;

		private EdgeMarkerSlider(ModConfig config) {
			super(0, 0, 310, 20, Component.empty(), config.maxEdgeBannerMarkers / 32.0);
			this.config = config;
			this.updateMessage();
		}

		@Override
		protected void updateMessage() {
			if (this.config != null) {
				this.setMessage(Component.translatable("option.neverket-minimap.edge_banner_markers")
					.append(": " + this.config.maxEdgeBannerMarkers));
			}
		}

		@Override
		protected void applyValue() {
			this.config.maxEdgeBannerMarkers = Math.clamp((int)Math.round(this.value * 32.0), 0, 32);
			this.value = this.config.maxEdgeBannerMarkers / 32.0;
			this.updateMessage();
		}
	}
}
