package dev.cartographer.minimap.client;

import dev.cartographer.minimap.config.ModConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

public final class FullscreenMapScreen extends Screen {
	private static final int MIN_TEXTURE_WIDTH = 512;
	private static final int MAX_TEXTURE_WIDTH = 1536;
	private static final int MIN_TEXTURE_HEIGHT = 256;
	private static final int MAX_TEXTURE_HEIGHT = 896;
	private static final int PAN_SNAP_TEXTURE_PIXELS = 64;
	private static final int MAP_GRID_BLOCKS = 128;
	private static final int MAP_MARGIN = 6;
	private static final int MAP_TOP = 34;
	private static final int MAP_BOTTOM = 14;

	private final WorldSession session;
	private final ModConfig config;
	private MapViewTexture viewTexture;
	private int textureWidth;
	private int textureHeight;
	private double centerX;
	private double centerZ;
	private int zoom;
	private String dimension;
	private List<String> dimensions = List.of();
	private Button dimensionButton;
	private boolean dragging;
	private String biomeCacheDimension;
	private int biomeCacheX = Integer.MIN_VALUE;
	private int biomeCacheZ = Integer.MIN_VALUE;
	private String biomeCache = "";

	public FullscreenMapScreen(WorldSession session, ModConfig config) {
		super(Component.translatable("screen.neverket-minimap.fullscreen"));
		this.session = session;
		this.config = config;
		this.centerX = this.minecraft.player == null ? 0 : this.minecraft.player.getX();
		this.centerZ = this.minecraft.player == null ? 0 : this.minecraft.player.getZ();
		this.zoom = config.zoom;
		this.dimension = this.minecraft.level == null ? "minecraft:overworld" : this.minecraft.level.dimension().identifier().toString();
	}

	@Override
	protected void init() {
		this.resizeViewTexture();
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
			false, this.config.unknownTerrain, this.useDetailedTerrain(), this.detailedTerrainRequiresMapCoverage(), this.dragging,
			this.config.showTerrainContours, this.config.terrainContourRangeChunks
		);
		boolean viewingCurrentDimension = this.minecraft.level != null
			&& this.dimension.equals(this.minecraft.level.dimension().identifier().toString());
		int mapTint = viewingCurrentDimension ? MinimapRenderer.mapTint(this.minecraft, this.config, 1.0F) : 0xFFFFFFFF;
		this.viewTexture.blit(graphics, mapX, mapY, mapWidth, mapHeight, mapTint);
		this.drawGrid(graphics, mapX, mapY, mapWidth, mapHeight);
		this.drawPlayer(graphics, mapX, mapY, mapWidth, mapHeight, partialTick);
		this.drawBorder(graphics, mapX, mapY, mapWidth, mapHeight);
		this.drawCursorInfo(graphics, mouseX, mouseY, mapX, mapY, mapWidth, mapHeight);

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
			this.dragging = true;
			this.centerX -= dx * this.zoom;
			this.centerZ -= dy * this.zoom;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0) {
			this.dragging = false;
		}
		return super.mouseReleased(event);
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
		if (this.viewTexture != null) {
			this.viewTexture.close();
		}
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return this.config.pauseOnFullscreenMap;
	}

	@Override
	public boolean isInGameUi() {
		return true;
	}

	private void drawGrid(GuiGraphicsExtractor graphics, int mapX, int mapY, int mapWidth, int mapHeight) {
		int gridStep = MAP_GRID_BLOCKS;
		while ((double)gridStep / this.zoom < 8.0) {
			gridStep *= 2;
		}
		int gridAlpha = this.zoom >= 32 ? 0x18 : this.zoom >= 16 ? 0x28 : 0x40;
		int gridColor = gridAlpha << 24 | 0x6C7480;
		double minWorldX = this.centerX - mapWidth * this.zoom / 2.0;
		double maxWorldX = this.centerX + mapWidth * this.zoom / 2.0;
		double minWorldZ = this.centerZ - mapHeight * this.zoom / 2.0;
		double maxWorldZ = this.centerZ + mapHeight * this.zoom / 2.0;
		long firstGridX = Math.floorDiv((long)Math.floor(minWorldX), gridStep) * gridStep;
		long firstGridZ = Math.floorDiv((long)Math.floor(minWorldZ), gridStep) * gridStep;

		graphics.enableScissor(mapX, mapY, mapX + mapWidth, mapY + mapHeight);
		for (long worldX = firstGridX; worldX <= maxWorldX; worldX += gridStep) {
			int screenX = (int)Math.round(mapX + mapWidth / 2.0 + (worldX - this.centerX) / this.zoom);
			graphics.fill(screenX, mapY, screenX + 1, mapY + mapHeight, gridColor);
		}
		for (long worldZ = firstGridZ; worldZ <= maxWorldZ; worldZ += gridStep) {
			int screenY = (int)Math.round(mapY + mapHeight / 2.0 + (worldZ - this.centerZ) / this.zoom);
			graphics.fill(mapX, screenY, mapX + mapWidth, screenY + 1, gridColor);
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

	private void drawCursorInfo(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int mapX, int mapY, int mapWidth, int mapHeight) {
		if (!this.isInsideMap(mouseX, mouseY)) {
			return;
		}
		int worldX = (int)Math.floor(this.centerX + (mouseX - (mapX + mapWidth / 2.0)) * this.zoom);
		int worldZ = (int)Math.floor(this.centerZ + (mouseY - (mapY + mapHeight / 2.0)) * this.zoom);
		String text = "X: " + worldX + "  Z: " + worldZ;
		if (this.config.showCursorBiome) {
			String biome = this.biomeAt(worldX, worldZ);
			if (!biome.isEmpty()) {
				text += "  /  " + Component.translatable("screen.neverket-minimap.biome", biome).getString();
			}
		}
		int textX = mapX + 5;
		int textY = mapY + mapHeight - 12;
		graphics.fill(textX - 3, textY - 2, textX + this.font.width(text) + 3, textY + 10, 0xA0101216);
		graphics.text(this.font, text, textX, textY, 0xFFF0F0F0, true);
	}

	private String biomeAt(int worldX, int worldZ) {
		if (this.minecraft.level == null || !this.dimension.equals(this.minecraft.level.dimension().identifier().toString())) {
			return "";
		}
		int biomeX = Math.floorDiv(worldX, 4);
		int biomeZ = Math.floorDiv(worldZ, 4);
		if (this.dimension.equals(this.biomeCacheDimension) && biomeX == this.biomeCacheX && biomeZ == this.biomeCacheZ) {
			return this.biomeCache;
		}
		this.biomeCacheDimension = this.dimension;
		this.biomeCacheX = biomeX;
		this.biomeCacheZ = biomeZ;
		LevelChunk chunk = this.minecraft.level.getChunkSource().getChunk(
			Math.floorDiv(worldX, 16), Math.floorDiv(worldZ, 16), ChunkStatus.FULL, false
		);
		if (chunk == null) {
			this.biomeCacheX = Integer.MIN_VALUE;
			this.biomeCacheZ = Integer.MIN_VALUE;
			return Component.translatable("screen.neverket-minimap.biome_unknown").getString();
		}
		int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, worldX & 15, worldZ & 15);
		this.biomeCache = this.minecraft.level.getBiome(new BlockPos(worldX, height, worldZ))
			.unwrapKey()
			.map(key -> Component.translatable("biome." + key.identifier().getNamespace() + "." + key.identifier().getPath()).getString())
			.orElseGet(() -> Component.translatable("screen.neverket-minimap.biome_unknown").getString());
		return this.biomeCache;
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

	private void resizeViewTexture() {
		int mapWidth = Math.max(64, this.width - MAP_MARGIN * 2);
		int mapHeight = Math.max(64, this.height - MAP_TOP - MAP_BOTTOM);
		int requiredWidth = adaptiveTextureSize(mapWidth, MIN_TEXTURE_WIDTH, MAX_TEXTURE_WIDTH);
		int requiredHeight = adaptiveTextureSize(mapHeight, MIN_TEXTURE_HEIGHT, MAX_TEXTURE_HEIGHT);
		if (this.viewTexture != null && requiredWidth == this.textureWidth && requiredHeight == this.textureHeight) {
			return;
		}
		if (this.viewTexture != null) {
			this.viewTexture.close();
		}
		this.textureWidth = requiredWidth;
		this.textureHeight = requiredHeight;
		this.viewTexture = new MapViewTexture(
			this.minecraft,
			Identifier.fromNamespaceAndPath("neverket-minimap", "fullscreen_view"),
			requiredWidth,
			requiredHeight,
			PAN_SNAP_TEXTURE_PIXELS
		);
	}

	private static int adaptiveTextureSize(int displaySize, int minimum, int maximum) {
		return Math.clamp(displaySize, minimum, maximum);
	}

	private boolean useDetailedTerrain() {
		return this.config.recordingMode == ModConfig.RecordingMode.EXPLORED_TERRAIN
			|| this.config.mapDetailMode == ModConfig.MapDetailMode.LOADED_TERRAIN_DETAIL;
	}

	private boolean detailedTerrainRequiresMapCoverage() {
		return this.config.recordingMode == ModConfig.RecordingMode.MAPS;
	}

	private Component dimensionLabel() {
		return Component.translatable("screen.neverket-minimap.dimension", this.dimension);
	}
}
