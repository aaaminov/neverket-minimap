package dev.cartographer.minimap.client;

import dev.cartographer.minimap.config.ModConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class FullscreenMapScreen extends Screen {
	private static final int TEXTURE_WIDTH = 1024;
	private static final int TEXTURE_HEIGHT = 512;
	private static final int MAP_GRID_BLOCKS = 128;
	private static final int MAP_MARGIN = 6;
	private static final int MAP_TOP = 34;
	private static final int MAP_BOTTOM = 14;

	private final WorldSession session;
	private final ModConfig config;
	private final MapViewTexture viewTexture;
	private double centerX;
	private double centerZ;
	private int zoom;
	private String dimension;
	private List<String> dimensions = List.of();
	private Button dimensionButton;

	public FullscreenMapScreen(WorldSession session, ModConfig config) {
		super(Component.translatable("screen.neverket-minimap.fullscreen"));
		this.session = session;
		this.config = config;
		this.centerX = this.minecraft.player == null ? 0 : this.minecraft.player.getX();
		this.centerZ = this.minecraft.player == null ? 0 : this.minecraft.player.getZ();
		this.zoom = config.zoom;
		this.dimension = this.minecraft.level == null ? "minecraft:overworld" : this.minecraft.level.dimension().identifier().toString();
		this.viewTexture = new MapViewTexture(
			this.minecraft,
			Identifier.fromNamespaceAndPath("neverket-minimap", "fullscreen_view"),
			TEXTURE_WIDTH,
			TEXTURE_HEIGHT
		);
	}

	@Override
	protected void init() {
		this.dimensions = new ArrayList<>(this.session.atlas().dimensions());
		this.dimensions.sort(Comparator.naturalOrder());
		if (!this.dimensions.contains(this.dimension) && !this.dimensions.isEmpty()) {
			this.dimension = this.dimensions.getFirst();
		}

		int dimensionWidth = Math.min(210, Math.max(120, this.width / 3));
		this.dimensionButton = this.addRenderableWidget(
			Button.builder(this.dimensionLabel(), button -> this.nextDimension()).bounds(MAP_MARGIN, 8, dimensionWidth, 20).build()
		);
		this.addRenderableWidget(
			Button.builder(Component.translatable("screen.neverket-minimap.to_player"), button -> this.centerOnPlayer())
				.bounds(this.width / 2 - 55, 8, 110, 20)
				.build()
		);
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.done"), button -> this.onClose()).bounds(this.width - 86, 8, 80, 20).build()
		);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int mapX = MAP_MARGIN;
		int mapY = MAP_TOP;
		int mapWidth = Math.max(64, this.width - MAP_MARGIN * 2);
		int mapHeight = Math.max(64, this.height - MAP_TOP - MAP_BOTTOM);
		this.viewTexture.update(
			this.session.atlas(), this.dimension, this.centerX, this.centerZ, this.zoom, mapWidth, mapHeight,
			false, this.config.unknownTerrain, this.config.showTerrainContours, this.config.terrainContourRangeChunks
		);
		this.viewTexture.blit(graphics, mapX, mapY, mapWidth, mapHeight, 0xFFFFFFFF);
		this.drawGrid(graphics, mapX, mapY, mapWidth, mapHeight);
		this.drawPlayer(graphics, mapX, mapY, mapWidth, mapHeight, partialTick);
		this.drawBorder(graphics, mapX, mapY, mapWidth, mapHeight);

		graphics.centeredText(
			this.font,
			Component.translatable("screen.neverket-minimap.hint", this.zoom),
			this.width / 2,
			this.height - 11,
			0xFFE0E0E0
		);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (event.button() == 0 && this.isInsideMap(event.x(), event.y())) {
			this.centerX -= dx * this.zoom;
			this.centerZ -= dy * this.zoom;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (scrollY != 0 && this.isInsideMap(x, y)) {
			int oldZoom = this.zoom;
			int newZoom = scrollY > 0 ? Math.max(1, oldZoom / 2) : Math.min(64, oldZoom * 2);
			if (newZoom != oldZoom) {
				double mapCenterX = this.width / 2.0;
				double mapCenterY = MAP_TOP + (this.height - MAP_TOP - MAP_BOTTOM) / 2.0;
				this.centerX += (x - mapCenterX) * (oldZoom - newZoom);
				this.centerZ += (y - mapCenterY) * (oldZoom - newZoom);
				this.zoom = newZoom;
			}
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
		return true;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	private void drawGrid(GuiGraphicsExtractor graphics, int mapX, int mapY, int mapWidth, int mapHeight) {
		double minWorldX = this.centerX - mapWidth * this.zoom / 2.0;
		double maxWorldX = this.centerX + mapWidth * this.zoom / 2.0;
		double minWorldZ = this.centerZ - mapHeight * this.zoom / 2.0;
		double maxWorldZ = this.centerZ + mapHeight * this.zoom / 2.0;
		long firstGridX = Math.floorDiv((long)Math.floor(minWorldX), MAP_GRID_BLOCKS) * MAP_GRID_BLOCKS;
		long firstGridZ = Math.floorDiv((long)Math.floor(minWorldZ), MAP_GRID_BLOCKS) * MAP_GRID_BLOCKS;

		graphics.enableScissor(mapX, mapY, mapX + mapWidth, mapY + mapHeight);
		for (long worldX = firstGridX; worldX <= maxWorldX; worldX += MAP_GRID_BLOCKS) {
			int screenX = (int)Math.round(mapX + mapWidth / 2.0 + (worldX - this.centerX) / this.zoom);
			graphics.fill(screenX, mapY, screenX + 1, mapY + mapHeight, 0x406C7480);
		}
		for (long worldZ = firstGridZ; worldZ <= maxWorldZ; worldZ += MAP_GRID_BLOCKS) {
			int screenY = (int)Math.round(mapY + mapHeight / 2.0 + (worldZ - this.centerZ) / this.zoom);
			graphics.fill(mapX, screenY, mapX + mapWidth, screenY + 1, 0x406C7480);
		}
		graphics.disableScissor();
	}

	private void drawPlayer(GuiGraphicsExtractor graphics, int mapX, int mapY, int mapWidth, int mapHeight, float partialTick) {
		if (this.minecraft.player == null || this.minecraft.level == null
			|| !this.dimension.equals(this.minecraft.level.dimension().identifier().toString())) {
			return;
		}
		var playerPosition = this.minecraft.player.getPosition(partialTick);
		int playerX = (int)Math.round(mapX + mapWidth / 2.0 + (playerPosition.x - this.centerX) / this.zoom);
		int playerY = (int)Math.round(mapY + mapHeight / 2.0 + (playerPosition.z - this.centerZ) / this.zoom);
		if (playerX >= mapX && playerX <= mapX + mapWidth && playerY >= mapY && playerY <= mapY + mapHeight) {
			MinimapRenderer.drawPlayerArrow(graphics, playerX, playerY, this.minecraft.player.getYRot(partialTick));
		}
	}

	private void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		graphics.fill(x - 1, y - 1, x + width + 1, y, 0xFF69717B);
		graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, 0xFF69717B);
		graphics.fill(x - 1, y, x, y + height, 0xFF69717B);
		graphics.fill(x + width, y, x + width + 1, y + height, 0xFF69717B);
	}

	private void nextDimension() {
		if (this.dimensions.isEmpty()) {
			return;
		}
		int index = this.dimensions.indexOf(this.dimension);
		this.dimension = this.dimensions.get((index + 1) % this.dimensions.size());
		if (this.minecraft.level != null && this.dimension.equals(this.minecraft.level.dimension().identifier().toString())) {
			this.centerOnPlayer();
		} else {
			this.fitDimension();
		}
		this.dimensionButton.setMessage(this.dimensionLabel());
	}

	private void centerOnPlayer() {
		if (this.minecraft.player == null || this.minecraft.level == null) {
			return;
		}
		this.dimension = this.minecraft.level.dimension().identifier().toString();
		this.centerX = this.minecraft.player.getX();
		this.centerZ = this.minecraft.player.getZ();
		this.dimensionButton.setMessage(this.dimensionLabel());
	}

	private void fitDimension() {
		int displayWidth = Math.max(64, this.width - MAP_MARGIN * 2);
		int displayHeight = Math.max(64, this.height - MAP_TOP - MAP_BOTTOM);
		this.session.atlas().bounds(this.dimension).ifPresent(bounds -> {
			this.centerX = (bounds.minX() + (double)bounds.maxXExclusive()) / 2.0;
			this.centerZ = (bounds.minZ() + (double)bounds.maxZExclusive()) / 2.0;
			double required = Math.max(bounds.width() / (double)Math.max(1, displayWidth - 16), bounds.height() / (double)Math.max(1, displayHeight - 16));
			int fittedZoom = 1;
			while (fittedZoom < required && fittedZoom < 64) {
				fittedZoom *= 2;
			}
			this.zoom = fittedZoom;
		});
	}

	private boolean isInsideMap(double x, double y) {
		return x >= MAP_MARGIN && x < this.width - MAP_MARGIN && y >= MAP_TOP && y < this.height - MAP_BOTTOM;
	}

	private Component dimensionLabel() {
		return Component.translatable("screen.neverket-minimap.dimension", this.dimension);
	}
}
