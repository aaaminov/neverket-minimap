package dev.neverket.minimap.client;

import dev.neverket.minimap.atlas.RecordingArea;
import dev.neverket.minimap.config.ModConfig;
import dev.neverket.minimap.marker.QuickMarker;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.glfw.GLFW;

public final class FullscreenMapScreen extends Screen {
	private static final int MIN_TEXTURE_WIDTH = 512;
	private static final int MAX_TEXTURE_WIDTH = 1536;
	private static final int MIN_TEXTURE_HEIGHT = 256;
	private static final int MAX_TEXTURE_HEIGHT = 896;
	private static final int PAN_SNAP_TEXTURE_PIXELS = 64;
	private static final int MAP_GRID_BLOCKS = 128;
	private static final int MAP_MARGIN = 6;
	private static final int MAP_TOP = 30;
	private static final int MAP_BOTTOM = 6;
	private static final long CHUNK_DEBUG_MAX_AGE_TICKS = 100L;

	private final WorldSession session;
	private final ModConfig config;
	private final KeyMapping biomeHighlightKey;
	private final KeyMapping chunkDebugKey;
	private MapViewTexture viewTexture;
	private int textureWidth;
	private int textureHeight;
	private double centerX;
	private double centerZ;
	private int zoom;
	private String dimension;
	private boolean dragging;
	private String biomeCacheDimension;
	private int biomeCacheX = Integer.MIN_VALUE;
	private int biomeCacheZ = Integer.MIN_VALUE;
	private String biomeCache = "";
	private final MapMarkerRenderer markerRenderer;
	private MapMarkerRenderer.MarkerHit hoveredMarker;
	private boolean legendVisible;
	private int legendX;
	private int legendY;
	private int legendWidth;
	private int legendHeight;
	private boolean biomeHighlightDown;
	private boolean chunkDebugDown;

	public FullscreenMapScreen(
		WorldSession session,
		ModConfig config,
		KeyMapping biomeHighlightKey,
		KeyMapping chunkDebugKey
	) {
		super(Component.translatable("screen.neverket-minimap.fullscreen"));
		this.session = session;
		this.config = config;
		this.biomeHighlightKey = biomeHighlightKey;
		this.chunkDebugKey = chunkDebugKey;
		this.markerRenderer = new MapMarkerRenderer(this.minecraft);
		this.centerX = this.minecraft.player == null ? 0 : this.minecraft.player.getX();
		this.centerZ = this.minecraft.player == null ? 0 : this.minecraft.player.getZ();
		this.zoom = config.zoom;
		this.dimension = this.minecraft.level == null ? "minecraft:overworld" : this.minecraft.level.dimension().identifier().toString();
	}

	@Override
	protected void init() {
		this.resizeViewTexture();
		this.addRenderableWidget(
			Button.builder(Component.translatable("screen.neverket-minimap.legend_button"), button ->
				this.legendVisible = !this.legendVisible
			).bounds(this.width - 230, 5, 84, 20).build()
		);
		this.addRenderableWidget(
			Button.builder(Component.translatable("screen.neverket-minimap.to_player"), button -> this.centerOnPlayer())
				.bounds(this.width - 142, 5, 78, 20)
				.build()
		);
		this.addRenderableWidget(
			Button.builder(Component.translatable("gui.done"), button -> this.onClose()).bounds(this.width - 60, 5, 54, 20).build()
		);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		int mapX = MAP_MARGIN;
		int mapY = MAP_TOP;
		int mapWidth = Math.max(64, this.width - MAP_MARGIN * 2);
		int mapHeight = Math.max(64, this.height - MAP_TOP - MAP_BOTTOM);
		boolean highlightKnownBiomes = this.biomeHighlightDown || this.biomeHighlightKey.isDown();
		boolean showChunkDebug = this.chunkDebugDown || this.chunkDebugKey.isDown();
		this.viewTexture.update(
			this.session.atlas(), this.session.terrainContours(), this.dimension,
			this.centerX, this.centerZ, this.zoom, mapWidth, mapHeight,
			false, this.config.unknownTerrain, false, this.useDetailedTerrain(), this.detailedTerrainRequiresMapCoverage(), this.dragging,
			this.config.showTerrainContours, this.config.terrainContourRangeChunks,
			highlightKnownBiomes, this.config.biomeHighlightColor.rgb(), this.config.biomeHighlightOpacity
		);
		boolean viewingCurrentDimension = this.minecraft.level != null
			&& this.dimension.equals(this.minecraft.level.dimension().identifier().toString());
		int mapTint = viewingCurrentDimension ? MinimapRenderer.mapTint(this.minecraft, this.config, 1.0F) : 0xFFFFFFFF;
		this.viewTexture.blit(graphics, mapX, mapY, mapWidth, mapHeight, mapTint);
		this.drawGrid(graphics, mapX, mapY, mapWidth, mapHeight);
		this.drawRecordingAreaBorder(graphics, mapX, mapY, mapWidth, mapHeight, highlightKnownBiomes);
		this.drawChunkUpdateDebug(graphics, mapX, mapY, mapWidth, mapHeight, showChunkDebug);
		this.hoveredMarker = this.markerRenderer.render(
			graphics, this.session.atlas(), this.config, this.dimension,
			this.centerX, this.centerZ, this.zoom,
			mapX, mapY, mapWidth, mapHeight, false, mouseX, mouseY, true
		);
		this.drawPlayer(graphics, mapX, mapY, mapWidth, mapHeight, partialTick);
		this.drawBorder(graphics, mapX, mapY, mapWidth, mapHeight);
		this.drawStatusBar(graphics, mouseX, mouseY, mapX, mapY, mapWidth, mapHeight);
		this.drawLegend(graphics, mapX, mapY, mapWidth);
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		this.extractBlurredBackground(graphics);
		graphics.fill(0, 0, this.width, this.height, 0x78000000);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (this.biomeHighlightKey.matches(event)) {
			this.biomeHighlightDown = true;
			return true;
		}
		if (this.chunkDebugKey.matches(event)) {
			this.chunkDebugDown = true;
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_M) {
			this.onClose();
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_L) {
			this.legendVisible = !this.legendVisible;
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean keyReleased(KeyEvent event) {
		if (this.biomeHighlightKey.matches(event)) {
			this.biomeHighlightDown = false;
			return true;
		}
		if (this.chunkDebugKey.matches(event)) {
			this.chunkDebugDown = false;
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (this.biomeHighlightKey.matchesMouse(event)) {
			this.biomeHighlightDown = true;
			return true;
		}
		if (this.chunkDebugKey.matchesMouse(event)) {
			this.chunkDebugDown = true;
			return true;
		}
		if (event.button() == 1 && this.isInsideMap(event.x(), event.y()) && !this.isInsideLegend(event.x(), event.y())) {
			if (this.hoveredMarker != null && this.hoveredMarker.quick()) {
				this.session.atlas().removeQuickMarker();
				this.session.saveNow();
				return true;
			}
			int worldX;
			int worldZ;
			if (this.hoveredMarker != null) {
				worldX = this.hoveredMarker.x();
				worldZ = this.hoveredMarker.z();
			} else {
				worldX = this.worldXAt(event.x());
				worldZ = this.worldZAt(event.y());
			}
			this.session.atlas().putQuickMarker(new QuickMarker(
				this.dimension, worldX, worldZ, System.currentTimeMillis()
			));
			this.session.saveNow();
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (event.button() == 0 && this.isInsideMap(event.x(), event.y()) && !this.isInsideLegend(event.x(), event.y())) {
			this.dragging = true;
			this.centerX -= dx * this.zoom;
			this.centerZ -= dy * this.zoom;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (this.biomeHighlightKey.matchesMouse(event)) {
			this.biomeHighlightDown = false;
			return true;
		}
		if (this.chunkDebugKey.matchesMouse(event)) {
			this.chunkDebugDown = false;
			return true;
		}
		if (event.button() == 0) {
			this.dragging = false;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (scrollY != 0 && this.isInsideMap(x, y) && !this.isInsideLegend(x, y)) {
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

	private void drawStatusBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int mapX, int mapY, int mapWidth, int mapHeight) {
		boolean cursorOnMap = mouseX >= mapX && mouseX < mapX + mapWidth && mouseY >= mapY && mouseY < mapY + mapHeight;
		int worldX = cursorOnMap
			? (int)Math.floor(this.centerX + (mouseX - (mapX + mapWidth / 2.0)) * this.zoom)
			: (int)Math.floor(this.centerX);
		int worldZ = cursorOnMap
			? (int)Math.floor(this.centerZ + (mouseY - (mapY + mapHeight / 2.0)) * this.zoom)
			: (int)Math.floor(this.centerZ);
		String text;
		if (this.biomeVisibleAt(worldX, worldZ)) {
			String biome = this.biomeAt(worldX, worldZ);
			if (biome.isEmpty()) {
				biome = Component.translatable("screen.neverket-minimap.biome_unknown").getString();
			}
			text = Component.translatable("screen.neverket-minimap.position", worldX, worldZ, biome).getString();
		} else {
			text = Component.translatable("screen.neverket-minimap.position_without_biome", worldX, worldZ).getString();
		}
		int maxWidth = Math.max(40, mapWidth - 36);
		if (this.font.width(text) > maxWidth) {
			text = this.font.plainSubstrByWidth(text, Math.max(20, maxWidth - this.font.width("..."))) + "...";
		}
		int textX = mapX + 18;
		int textY = mapY + mapHeight - 22;
		graphics.fill(textX - 4, textY - 3, textX + this.font.width(text) + 4, textY + 11, 0x90101216);
		graphics.text(this.font, text, textX, textY, 0xFFF0F0F0, true);
		graphics.text(
			this.font,
			Component.translatable("screen.neverket-minimap.zoom_status", this.zoom),
			MAP_MARGIN,
			11,
			0xFFF0F0F0,
			true
		);
	}

	private boolean biomeVisibleAt(int worldX, int worldZ) {
		return this.config.showCursorBiome && (this.config.recordingMode == ModConfig.RecordingMode.MAPS
			? this.session.atlas().colorAt(this.dimension, worldX, worldZ, false) != 0
			: this.session.atlas().colorAt(this.dimension, worldX, worldZ, true) != 0);
	}

	private void drawRecordingAreaBorder(
		GuiGraphicsExtractor graphics,
		int mapX,
		int mapY,
		int mapWidth,
		int mapHeight,
		boolean highlightKnownBiomes
	) {
		if (!highlightKnownBiomes || !this.config.showRecordingAreaOnBiomeHighlight
			|| this.minecraft.player == null || this.minecraft.level == null
			|| !this.dimension.equals(this.minecraft.level.dimension().identifier().toString())) {
			return;
		}
		int range = TerrainDataCollector.recordingRangeChunks(this.minecraft);
		double worldCenterX = this.minecraft.player.getX();
		double worldCenterZ = this.minecraft.player.getZ();
		double screenCenterX = mapX + mapWidth / 2.0 + (worldCenterX - this.centerX) / this.zoom;
		double screenCenterY = mapY + mapHeight / 2.0 + (worldCenterZ - this.centerZ) / this.zoom;
		double radius = RecordingArea.radiusBlocks(range) / (double)this.zoom;
		int color = 0xD0000000 | this.config.biomeHighlightColor.rgb();

		graphics.enableScissor(mapX, mapY, mapX + mapWidth, mapY + mapHeight);
		if (radius < 1.0) {
			int x = (int)Math.floor(screenCenterX);
			int y = (int)Math.floor(screenCenterY);
			if (x >= mapX && x < mapX + mapWidth && y >= mapY && y < mapY + mapHeight) {
				graphics.fill(x, y, x + 1, y + 1, color);
			}
		} else {
			int minX = Math.max(mapX, (int)Math.ceil(screenCenterX - radius - 0.5));
			int maxX = Math.min(mapX + mapWidth - 1, (int)Math.floor(screenCenterX + radius - 0.5));
			for (int x = minX; x <= maxX; x++) {
				double dx = x + 0.5 - screenCenterX;
				double radicand = radius * radius - dx * dx;
				if (radicand < 0.0) {
					continue;
				}
				double dy = Math.sqrt(radicand);
				this.drawRecordingAreaPixel(graphics, x, (int)Math.round(screenCenterY - dy), mapX, mapY, mapWidth, mapHeight, color);
				this.drawRecordingAreaPixel(graphics, x, (int)Math.round(screenCenterY + dy), mapX, mapY, mapWidth, mapHeight, color);
			}
			int minY = Math.max(mapY, (int)Math.ceil(screenCenterY - radius - 0.5));
			int maxY = Math.min(mapY + mapHeight - 1, (int)Math.floor(screenCenterY + radius - 0.5));
			for (int y = minY; y <= maxY; y++) {
				double dy = y + 0.5 - screenCenterY;
				double radicand = radius * radius - dy * dy;
				if (radicand < 0.0) {
					continue;
				}
				double dx = Math.sqrt(radicand);
				this.drawRecordingAreaPixel(graphics, (int)Math.round(screenCenterX - dx), y, mapX, mapY, mapWidth, mapHeight, color);
				this.drawRecordingAreaPixel(graphics, (int)Math.round(screenCenterX + dx), y, mapX, mapY, mapWidth, mapHeight, color);
			}
		}
		graphics.disableScissor();
	}

	private void drawRecordingAreaPixel(
		GuiGraphicsExtractor graphics,
		int x,
		int y,
		int mapX,
		int mapY,
		int mapWidth,
		int mapHeight,
		int color
	) {
		if (x >= mapX && x < mapX + mapWidth && y >= mapY && y < mapY + mapHeight) {
			graphics.fill(x, y, x + 1, y + 1, color);
		}
	}

	private void drawChunkUpdateDebug(
		GuiGraphicsExtractor graphics,
		int mapX,
		int mapY,
		int mapWidth,
		int mapHeight,
		boolean visible
	) {
		if (!visible) {
			return;
		}
		long gameTime = this.minecraft.level == null ? Long.MIN_VALUE : this.minecraft.level.getGameTime();
		List<TerrainDataCollector.ChunkUpdate> updates = new ArrayList<>();
		for (TerrainDataCollector.ChunkUpdate update : this.session.recentTerrainUpdates()) {
			long age = gameTime - update.gameTime();
			if (update.dimension().equals(this.dimension) && age >= 0L && age <= CHUNK_DEBUG_MAX_AGE_TICKS) {
				updates.add(update);
			}
		}
		if (updates.isEmpty()) {
			return;
		}

		graphics.enableScissor(mapX, mapY, mapX + mapWidth, mapY + mapHeight);
		for (int index = updates.size() - 1; index >= 0; index--) {
			TerrainDataCollector.ChunkUpdate update = updates.get(index);
			double minWorldX = update.chunkX() * 16.0;
			double minWorldZ = update.chunkZ() * 16.0;
			int left = (int)Math.floor(mapX + mapWidth / 2.0 + (minWorldX - this.centerX) / this.zoom);
			int top = (int)Math.floor(mapY + mapHeight / 2.0 + (minWorldZ - this.centerZ) / this.zoom);
			int right = Math.max(left + 1, (int)Math.ceil(
				mapX + mapWidth / 2.0 + (minWorldX + 16.0 - this.centerX) / this.zoom
			));
			int bottom = Math.max(top + 1, (int)Math.ceil(
				mapY + mapHeight / 2.0 + (minWorldZ + 16.0 - this.centerZ) / this.zoom
			));
			if (right <= mapX || left >= mapX + mapWidth || bottom <= mapY || top >= mapY + mapHeight) {
				continue;
			}
			int alpha = updates.size() == 1
				? 0xD8
				: Math.max(0x20, 0xD8 - index * (0xB8 / (updates.size() - 1)));
			int rgb = debugChunkColor(update.kind());
			int clippedLeft = Math.max(left, mapX);
			int clippedTop = Math.max(top, mapY);
			int clippedRight = Math.min(right, mapX + mapWidth);
			int clippedBottom = Math.min(bottom, mapY + mapHeight);
			graphics.fill(clippedLeft, clippedTop, clippedRight, clippedBottom, alpha << 24 | rgb);
			if (index == 0) {
				int outline = 0xFF000000 | rgb;
				graphics.fill(clippedLeft, clippedTop, clippedRight, Math.min(clippedTop + 1, clippedBottom), outline);
				graphics.fill(clippedLeft, Math.max(clippedBottom - 1, clippedTop), clippedRight, clippedBottom, outline);
				graphics.fill(clippedLeft, clippedTop, Math.min(clippedLeft + 1, clippedRight), clippedBottom, outline);
				graphics.fill(Math.max(clippedRight - 1, clippedLeft), clippedTop, clippedRight, clippedBottom, outline);
			}
		}
		graphics.disableScissor();

		TerrainDataCollector.ChunkUpdate latest = updates.getFirst();
		double averageDurationMillis = updates.stream()
			.mapToLong(TerrainDataCollector.ChunkUpdate::durationNanos)
			.average()
			.orElse(0.0) / 1_000_000.0;
		double maximumDurationMillis = updates.stream()
			.mapToLong(TerrainDataCollector.ChunkUpdate::durationNanos)
			.max()
			.orElse(0L) / 1_000_000.0;
		String kind = Component.translatable(
			"value.neverket-minimap.chunk_update_kind." + latest.kind().name().toLowerCase(Locale.ROOT)
		).getString();
		String duration = String.format(Locale.ROOT, "%.2f", latest.durationNanos() / 1_000_000.0);
		String averageDuration = String.format(Locale.ROOT, "%.2f", averageDurationMillis);
		String maximumDuration = String.format(Locale.ROOT, "%.2f", maximumDurationMillis);
		String textureDuration = String.format(Locale.ROOT, "%.2f", this.viewTexture.lastUpdateDurationNanos() / 1_000_000.0);
		int range = TerrainDataCollector.recordingRangeChunks(this.minecraft);
		String[] lines = {
			Component.translatable("screen.neverket-minimap.chunk_debug.title").getString(),
			Component.translatable("screen.neverket-minimap.chunk_debug.area", range).getString(),
			Component.translatable(
				"screen.neverket-minimap.chunk_debug.fog_area",
				TerrainDataCollector.terrainContourRangeChunks(this.minecraft, this.config)
			).getString(),
			Component.translatable(
				"screen.neverket-minimap.chunk_debug.far_ring", this.session.currentTerrainUpdateRing(), range
			).getString(),
			Component.translatable(
				"screen.neverket-minimap.chunk_debug.latest", kind, latest.chunkX(), latest.chunkZ()
			).getString(),
			Component.translatable("screen.neverket-minimap.chunk_debug.scan_latest", duration).getString(),
			Component.translatable(
				"screen.neverket-minimap.chunk_debug.scan_summary", averageDuration, maximumDuration
			).getString(),
			Component.translatable("screen.neverket-minimap.chunk_debug.texture", textureDuration).getString(),
			Component.translatable("screen.neverket-minimap.chunk_debug.history", updates.size()).getString()
		};
		int maxTextWidth = Math.max(40, mapWidth - 28);
		int textWidth = 0;
		for (int index = 0; index < lines.length; index++) {
			if (this.font.width(lines[index]) > maxTextWidth) {
				lines[index] = this.font.plainSubstrByWidth(
					lines[index], Math.max(20, maxTextWidth - this.font.width("..."))
				) + "...";
			}
			textWidth = Math.max(textWidth, this.font.width(lines[index]));
		}
		int panelWidth = textWidth + 12;
		int panelHeight = lines.length * 11 + 9;
		int panelX = mapX + mapWidth - panelWidth - 6;
		int panelY = mapY + 6;
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xC0101216);
		for (int index = 0; index < lines.length; index++) {
			graphics.text(
				this.font, lines[index], panelX + 6, panelY + 5 + index * 11,
				index == 0 ? 0xFFFFFFFF : 0xFFE0E0E0, true
			);
		}
	}

	private static int debugChunkColor(TerrainDataCollector.UpdateKind kind) {
		return switch (kind) {
			case DISCOVERY -> 0x55DDE0;
			case PLAYER_REFRESH -> 0xFFB347;
			case NEARBY_REFRESH -> 0xF6E05E;
			case BACKGROUND_REFRESH -> 0x68D391;
		};
	}

	private void drawLegend(GuiGraphicsExtractor graphics, int mapX, int mapY, int mapWidth) {
		if (!this.legendVisible) {
			this.legendWidth = 0;
			this.legendHeight = 0;
			return;
		}
		Component title = Component.translatable("screen.neverket-minimap.legend.title");
		Component[] lines = {
			Component.translatable("screen.neverket-minimap.legend.pan"),
			Component.translatable("screen.neverket-minimap.legend.marker"),
			Component.translatable("screen.neverket-minimap.legend.zoom"),
			Component.translatable("screen.neverket-minimap.legend.biomes", this.biomeHighlightKey.getTranslatedKeyMessage()),
			Component.translatable("screen.neverket-minimap.legend.chunk_debug", this.chunkDebugKey.getTranslatedKeyMessage()),
			Component.translatable("screen.neverket-minimap.legend.center"),
			Component.translatable("screen.neverket-minimap.legend.close"),
			Component.translatable("screen.neverket-minimap.legend.hide")
		};
		int contentWidth = this.font.width(title);
		for (Component line : lines) {
			contentWidth = Math.max(contentWidth, this.font.width(line));
		}
		this.legendWidth = contentWidth + 16;
		this.legendHeight = 20 + lines.length * 12;
		this.legendX = mapX + 6;
		this.legendY = mapY + 6;
		graphics.fill(this.legendX, this.legendY, this.legendX + this.legendWidth, this.legendY + this.legendHeight, 0xD0101216);
		graphics.fill(this.legendX, this.legendY, this.legendX + this.legendWidth, this.legendY + 1, 0xFF69717B);
		graphics.text(this.font, title, this.legendX + 8, this.legendY + 6, 0xFFFFFFFF, true);
		int lineY = this.legendY + 19;
		for (Component line : lines) {
			graphics.text(this.font, line, this.legendX + 8, lineY, 0xFFE0E0E0, false);
			lineY += 12;
		}
	}

	private String biomeAt(int worldX, int worldZ) {
		int biomeX = Math.floorDiv(worldX, 4);
		int biomeZ = Math.floorDiv(worldZ, 4);
		if (this.dimension.equals(this.biomeCacheDimension) && biomeX == this.biomeCacheX && biomeZ == this.biomeCacheZ) {
			return this.biomeCache;
		}
		this.biomeCacheDimension = this.dimension;
		this.biomeCacheX = biomeX;
		this.biomeCacheZ = biomeZ;
		String recordedBiome = this.session.atlas().biomeAt(this.dimension, worldX, worldZ);
		if (recordedBiome != null) {
			this.biomeCache = translatedBiome(recordedBiome);
			return this.biomeCache;
		}
		if (this.minecraft.level == null || !this.dimension.equals(this.minecraft.level.dimension().identifier().toString())) {
			this.biomeCache = Component.translatable("screen.neverket-minimap.biome_unknown").getString();
			return this.biomeCache;
		}
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

	private static String translatedBiome(String biomeId) {
		try {
			Identifier id = Identifier.parse(biomeId);
			return Component.translatable("biome." + id.getNamespace() + "." + id.getPath()).getString();
		} catch (RuntimeException ignored) {
			return Component.translatable("screen.neverket-minimap.biome_unknown").getString();
		}
	}

	private void centerOnPlayer() {
		if (this.minecraft.player == null || this.minecraft.level == null) {
			return;
		}
		this.dimension = this.minecraft.level.dimension().identifier().toString();
		this.centerX = this.minecraft.player.getX();
		this.centerZ = this.minecraft.player.getZ();
	}

	private boolean isInsideMap(double x, double y) {
		return x >= MAP_MARGIN && x < this.width - MAP_MARGIN && y >= MAP_TOP && y < this.height - MAP_BOTTOM;
	}

	private boolean isInsideLegend(double x, double y) {
		return this.legendVisible && x >= this.legendX && x < this.legendX + this.legendWidth
			&& y >= this.legendY && y < this.legendY + this.legendHeight;
	}

	private int worldXAt(double screenX) {
		return (int)Math.floor(this.centerX + (screenX - this.width / 2.0) * this.zoom);
	}

	private int worldZAt(double screenY) {
		double mapCenterY = MAP_TOP + (this.height - MAP_TOP - MAP_BOTTOM) / 2.0;
		return (int)Math.floor(this.centerZ + (screenY - mapCenterY) * this.zoom);
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

}
