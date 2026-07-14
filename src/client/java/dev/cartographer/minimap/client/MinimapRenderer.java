package dev.cartographer.minimap.client;

import dev.cartographer.minimap.config.ModConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;

public final class MinimapRenderer implements AutoCloseable {
	private static final int TEXTURE_SIZE = 256;
	private static final int MARGIN = 8;

	private final Minecraft minecraft;
	private final WorldSession session;
	private final ModConfig config;
	private final MapViewTexture viewTexture;

	public MinimapRenderer(Minecraft minecraft, WorldSession session, ModConfig config) {
		this.minecraft = minecraft;
		this.session = session;
		this.config = config;
		this.viewTexture = new MapViewTexture(minecraft, Identifier.fromNamespaceAndPath("neverket-minimap", "hud_view"), TEXTURE_SIZE, TEXTURE_SIZE);
	}

	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		if (!this.config.visible || this.minecraft.player == null || this.minecraft.level == null || !this.session.active()) {
			return;
		}

		int size = this.config.size;
		int x = switch (this.config.corner) {
			case TOP_LEFT, BOTTOM_LEFT -> MARGIN;
			case TOP_RIGHT -> graphics.guiWidth() - size - MARGIN - (this.hasVisibleEffects() ? 28 : 0);
			case BOTTOM_RIGHT -> graphics.guiWidth() - size - MARGIN;
		};
		int y = switch (this.config.corner) {
			case TOP_LEFT, TOP_RIGHT -> MARGIN;
			case BOTTOM_LEFT, BOTTOM_RIGHT -> Math.max(MARGIN, graphics.guiHeight() - size - MARGIN - 52);
		};

		float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
		var playerPosition = this.minecraft.player.getPosition(partialTick);
		String dimension = this.minecraft.level.dimension().identifier().toString();
		this.viewTexture.update(
			this.session.atlas(), dimension, playerPosition.x, playerPosition.z, this.config.zoom, size, size,
			this.config.shape == ModConfig.Shape.CIRCLE, this.config.unknownTerrain,
			this.useDetailedTerrain(), false,
			this.config.showTerrainContours, this.config.terrainContourRangeChunks
		);

		int tint = mapTint(this.minecraft, this.config, this.config.opacity);
		this.viewTexture.blit(graphics, x, y, size, size, tint);
		if (this.config.shape == ModConfig.Shape.SQUARE) {
			drawBorder(graphics, x, y, size);
		}
		drawPlayerArrow(graphics, x + size / 2, y + size / 2, this.minecraft.player.getYRot(partialTick));

		if (this.config.showCardinalDirections) {
			graphics.centeredText(this.minecraft.font, "N", x + size / 2, y + 3, 0xFFFFFFFF);
			graphics.centeredText(this.minecraft.font, "S", x + size / 2, y + size - 11, 0xFFFFFFFF);
			graphics.text(this.minecraft.font, "W", x + 4, y + size / 2 - 4, 0xFFFFFFFF, true);
			graphics.text(this.minecraft.font, "E", x + size - 10, y + size / 2 - 4, 0xFFFFFFFF, true);
		}
		if (this.config.showCoordinates) {
			String coordinates = (int)Math.floor(playerPosition.x) + ", " + (int)Math.floor(playerPosition.z);
			int coordinatesY = y + size - (this.config.showCardinalDirections ? 22 : 11);
			graphics.centeredText(this.minecraft.font, coordinates, x + size / 2, coordinatesY, 0xFFFFFFFF);
		}
	}

	private boolean useDetailedTerrain() {
		return this.config.recordingMode == ModConfig.RecordingMode.EXPLORED_TERRAIN
			|| this.config.mapDetailMode == ModConfig.MapDetailMode.LOADED_TERRAIN_DETAIL;
	}

	private boolean hasVisibleEffects() {
		return this.minecraft.player != null
			&& this.minecraft.player.getActiveEffects().stream().anyMatch(effect -> effect.isVisible() && effect.showIcon());
	}

	static int mapTint(Minecraft minecraft, ModConfig config, float opacity) {
		float brightness = 1.0F;
		if (config.mapLightingMode == ModConfig.MapLightingMode.DAY_NIGHT && minecraft.level != null
			&& minecraft.level.dimensionType().hasSkyLight() && !minecraft.level.dimensionType().hasFixedTime()) {
			brightness = 1.0F - Math.clamp(minecraft.level.getSkyDarken() / 15.0F, 0.0F, 1.0F) * 0.45F;
		}
		int alpha = Math.round(Math.clamp(opacity, 0.0F, 1.0F) * 255.0F);
		int channel = Math.round(brightness * 255.0F);
		return alpha << 24 | channel << 16 | channel << 8 | channel;
	}

	static void drawPlayerArrow(GuiGraphicsExtractor graphics, int centerX, int centerY, float yawDegrees) {
		TextureAtlasSprite sprite = Minecraft.getInstance()
			.getAtlasManager()
			.getAtlasOrThrow(AtlasIds.MAP_DECORATIONS)
			.getSprite(MapDecorationTypes.PLAYER.value().assetId());
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, centerY);
		graphics.pose().rotate((float)Math.toRadians(yawDegrees + 180.0F));
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, -5, -5, 10, 10);
		graphics.pose().popMatrix();
	}

	private static void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int size) {
		graphics.fill(x - 1, y - 1, x + size + 1, y, 0xCCFFFFFF);
		graphics.fill(x - 1, y + size, x + size + 1, y + size + 1, 0xCCFFFFFF);
		graphics.fill(x - 1, y, x, y + size, 0xCCFFFFFF);
		graphics.fill(x + size, y, x + size + 1, y + size, 0xCCFFFFFF);
	}

	@Override
	public void close() {
		this.viewTexture.close();
	}
}
