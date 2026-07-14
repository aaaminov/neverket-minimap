package dev.cartographer.minimap.client;

import dev.cartographer.minimap.config.ModConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class FullscreenMapScreen extends Screen {
	private static final int TEXTURE_SIZE = 512;

	private final WorldSession session;
	private final ModConfig config;
	private final MapViewTexture viewTexture;
	private double centerX;
	private double centerZ;
	private int zoom;
	private String dimension;
	private List<String> dimensions = List.of();
	private Button dimensionButton;
	private boolean fitted;

	public FullscreenMapScreen(WorldSession session, ModConfig config) {
		super(Component.translatable("screen.cartographer-minimap.fullscreen"));
		this.session = session;
		this.config = config;
		this.centerX = this.minecraft.player == null ? 0 : this.minecraft.player.getX();
		this.centerZ = this.minecraft.player == null ? 0 : this.minecraft.player.getZ();
		this.zoom = config.zoom;
		this.dimension = this.minecraft.level == null ? "minecraft:overworld" : this.minecraft.level.dimension().identifier().toString();
		this.viewTexture = new MapViewTexture(this.minecraft, Identifier.fromNamespaceAndPath("cartographer-minimap", "fullscreen_view"), TEXTURE_SIZE);
	}

	@Override
	protected void init() {
		this.dimensions = new ArrayList<>(this.session.atlas().dimensions());
		this.dimensions.sort(Comparator.naturalOrder());
		if (!this.dimensions.contains(this.dimension) && !this.dimensions.isEmpty()) {
			this.dimension = this.dimensions.getFirst();
		}
		if (!this.fitted) {
			this.fitDimension();
			this.fitted = true;
		}
		this.dimensionButton = this.addRenderableWidget(Button.builder(this.dimensionLabel(), button -> this.nextDimension()).bounds(10, 10, 210, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose()).bounds(this.width - 110, 10, 100, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int displaySize = Math.max(64, Math.min(TEXTURE_SIZE, Math.min(this.width - 20, this.height - 52)));
		int mapX = (this.width - displaySize) / 2;
		int mapY = 40 + (this.height - 40 - displaySize) / 2;
		this.viewTexture.update(this.session.atlas(), this.dimension, this.centerX, this.centerZ, this.zoom, displaySize, false, this.config.unknownTerrain);

		graphics.blit(RenderPipelines.GUI_TEXTURED, this.viewTexture.id(), mapX, mapY, 0, 0, displaySize, displaySize, TEXTURE_SIZE, TEXTURE_SIZE);
		graphics.fill(mapX - 1, mapY - 1, mapX + displaySize + 1, mapY, 0xFFFFFFFF);
		graphics.fill(mapX - 1, mapY + displaySize, mapX + displaySize + 1, mapY + displaySize + 1, 0xFFFFFFFF);
		graphics.fill(mapX - 1, mapY, mapX, mapY + displaySize, 0xFFFFFFFF);
		graphics.fill(mapX + displaySize, mapY, mapX + displaySize + 1, mapY + displaySize, 0xFFFFFFFF);
		this.drawMapBoundaries(graphics, mapX, mapY, displaySize);

		if (this.minecraft.player != null && this.minecraft.level != null && this.dimension.equals(this.minecraft.level.dimension().identifier().toString())) {
			int playerX = (int)Math.round(mapX + displaySize / 2.0 + (this.minecraft.player.getX() - this.centerX) / this.zoom);
			int playerY = (int)Math.round(mapY + displaySize / 2.0 + (this.minecraft.player.getZ() - this.centerZ) / this.zoom);
			if (playerX >= mapX && playerX <= mapX + displaySize && playerY >= mapY && playerY <= mapY + displaySize) {
				MinimapRenderer.drawPlayerArrow(graphics, playerX, playerY, this.minecraft.player.getYRot());
			}
		}

		graphics.centeredText(this.font, Component.translatable("screen.cartographer-minimap.hint", this.zoom), this.width / 2, this.height - 10, 0xFFE0E0E0);
		super.extractRenderState(graphics, mouseX, mouseY, a);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (event.button() == 0) {
			this.centerX -= dx * this.zoom;
			this.centerZ -= dy * this.zoom;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (scrollY != 0) {
			this.zoom = scrollY > 0 ? Math.max(1, this.zoom / 2) : Math.min(256, this.zoom * 2);
			return true;
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public void removed() {
		this.viewTexture.close();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	private void nextDimension() {
		if (this.dimensions.isEmpty()) {
			return;
		}
		int index = this.dimensions.indexOf(this.dimension);
		this.dimension = this.dimensions.get((index + 1) % this.dimensions.size());
		this.fitDimension();
		this.dimensionButton.setMessage(this.dimensionLabel());
	}

	private void fitDimension() {
		int displaySize = Math.max(64, Math.min(TEXTURE_SIZE, Math.min(this.width - 20, this.height - 52)));
		this.session.atlas().bounds(this.dimension).ifPresent(bounds -> {
			this.centerX = (bounds.minX() + (double)bounds.maxXExclusive()) / 2.0;
			this.centerZ = (bounds.minZ() + (double)bounds.maxZExclusive()) / 2.0;
			double required = Math.max(bounds.width(), bounds.height()) / (double)Math.max(1, displaySize - 16);
			int fittedZoom = 1;
			while (fittedZoom < required && fittedZoom < 256) {
				fittedZoom *= 2;
			}
			this.zoom = fittedZoom;
		});
	}

	private void drawMapBoundaries(GuiGraphicsExtractor graphics, int mapX, int mapY, int displaySize) {
		for (var snapshot : this.session.atlas().snapshots()) {
			if (!snapshot.dimension().equals(this.dimension)) {
				continue;
			}
			int left = (int)Math.round(mapX + displaySize / 2.0 + (snapshot.minX() - this.centerX) / this.zoom);
			int right = (int)Math.round(mapX + displaySize / 2.0 + (snapshot.maxXExclusive() - this.centerX) / this.zoom);
			int top = (int)Math.round(mapY + displaySize / 2.0 + (snapshot.minZ() - this.centerZ) / this.zoom);
			int bottom = (int)Math.round(mapY + displaySize / 2.0 + (snapshot.maxZExclusive() - this.centerZ) / this.zoom);
			if (right < mapX || left > mapX + displaySize || bottom < mapY || top > mapY + displaySize) {
				continue;
			}
			left = Math.clamp(left, mapX, mapX + displaySize);
			right = Math.clamp(right, mapX, mapX + displaySize);
			top = Math.clamp(top, mapY, mapY + displaySize);
			bottom = Math.clamp(bottom, mapY, mapY + displaySize);
			if (top > mapY && top < mapY + displaySize) graphics.horizontalLine(left, right, top, 0x66000000);
			if (bottom > mapY && bottom < mapY + displaySize) graphics.horizontalLine(left, right, bottom, 0x66000000);
			if (left > mapX && left < mapX + displaySize) graphics.verticalLine(left, top, bottom, 0x66000000);
			if (right > mapX && right < mapX + displaySize) graphics.verticalLine(right, top, bottom, 0x66000000);
		}
	}

	private Component dimensionLabel() {
		return Component.translatable("screen.cartographer-minimap.dimension", this.dimension);
	}
}
