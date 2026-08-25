package dev.neverket.minimap.client;

import dev.neverket.minimap.config.ModConfig;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
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
		this.addHeader(Component.translatable("group.neverket-minimap.quick_marker"));
		ModConfig.QuickMarkerIcon[] icons = ModConfig.QuickMarkerIcon.values();
		for (int index = 0; index < icons.length; index += 2) {
			IconChoiceButton left = this.iconButton(icons[index]);
			IconChoiceButton right = index + 1 < icons.length ? this.iconButton(icons[index + 1]) : null;
			if (right == null) {
				this.addBigWidget(left);
			} else {
				this.list.addSmall(left, right);
			}
		}

		this.addHeader(Component.translatable("group.neverket-minimap.edge_markers"));
		EdgeMarkerSlider edgeMarkers = new EdgeMarkerSlider(this.config);
		edgeMarkers.setTooltip(Tooltip.create(Component.translatable("description.neverket-minimap.edge_banner_markers")));
		this.addBigWidget(edgeMarkers);
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_N) {
			this.minecraft.setScreen(null);
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public void removed() {
		this.config.save();
		super.removed();
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, this.width, this.height, 0x72000000);
		graphics.flush();
	}

	private IconChoiceButton iconButton(ModConfig.QuickMarkerIcon icon) {
		IconChoiceButton button = new IconChoiceButton(icon);
		button.setTooltip(Tooltip.create(Component.translatable("description.neverket-minimap.quick_marker_icon")));
		return button;
	}

	private void addHeader(Component title) {
		StringWidget header = new StringWidget(310, 20, title, this.font).alignLeft();
		this.list.addSmall(header, null);
	}

	private void addBigWidget(net.minecraft.client.gui.components.AbstractWidget widget) {
		widget.setWidth(310);
		this.list.addSmall(widget, null);
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
		public void onPress() {
			MarkerSettingsScreen.this.config.quickMarkerIcon = this.icon;
		}

		@Override
		protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
			super.renderWidget(graphics, mouseX, mouseY, partialTick);
			if (MarkerSettingsScreen.this.config.quickMarkerIcon == this.icon) {
				graphics.renderOutline(this.getX(), this.getY(), this.getWidth(), this.getHeight(), 0xFFFFFFFF);
			}
			MarkerSettingsScreen.this.markerRenderer.drawQuickIcon(graphics, this.icon, this.getX() + 13, this.getY() + this.getHeight() / 2);
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
