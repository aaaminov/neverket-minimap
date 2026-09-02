package dev.neverket.minimap.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.neverket.minimap.atlas.MapAtlas;
import dev.neverket.minimap.config.MapLighting;
import dev.neverket.minimap.config.PackedMapColor;
import dev.neverket.minimap.config.TerrainFogStyle;
import dev.neverket.minimap.geometry.MinimapProjection;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.material.MapColor;

public final class MapViewTexture implements AutoCloseable {
	private static final int DEFAULT_OVERSCAN = 4;
	private static final long MINIMAP_REFRESH_INTERVAL_NANOS = 250_000_000L;
	private static final long FULLSCREEN_REFRESH_INTERVAL_NANOS = 350_000_000L;
	private static final int NO_HEIGHT = Integer.MIN_VALUE;
	private static final byte LAND = 1;
	private static final byte WATER = 2;
	private static final int[] PACKED_MAP_COLORS = createPackedMapColors();

	private final Minecraft minecraft;
	private final ResourceLocation id;
	private final int viewWidth;
	private final int viewHeight;
	private final int centerSnapPixels;
	private final int overscan;
	private final int rotationPaddingX;
	private final int rotationPaddingY;
	private final int textureWidth;
	private final int textureHeight;
	private final int[] basePixels;
	private CrispDynamicTexture texture;
	private int uploadedTint = Integer.MIN_VALUE;
	private String lastDimension;
	private double lastSampleCenterX = Double.NaN;
	private double lastSampleCenterZ = Double.NaN;
	private double lastBlocksPerScreenPixel = Double.NaN;
	private int lastDisplayWidth;
	private int lastDisplayHeight;
	private boolean lastCircular;
	private float lastUnknownOpacity = Float.NaN;
	private boolean lastIncludeDetailedTerrain;
	private boolean lastDetailedTerrainRequiresMapCoverage;
	private MapAtlas lastAtlas;
	private long lastAtlasVersion = Long.MIN_VALUE;
	private boolean lastTerrainContours;
	private int lastTerrainContourRange;
	private long lastTerrainRefresh = Long.MIN_VALUE;
	private boolean lastHighlightKnownBiomes;
	private int lastBiomeHighlightColor;
	private float lastBiomeHighlightOpacity;
	private long lastBiomeHighlightRefresh = Long.MIN_VALUE;
	private long lastUploadNanos = Long.MIN_VALUE;
	private long lastUpdateDurationNanos;
	private int[] terrainHeightBuffer;
	private byte[] terrainKindBuffer;
	private byte[] terrainFadeBuffer;
	private float sourceU;
	private float sourceV;

	public MapViewTexture(Minecraft minecraft, ResourceLocation id, int viewWidth, int viewHeight) {
		this(minecraft, id, viewWidth, viewHeight, 0, false);
	}

	public MapViewTexture(Minecraft minecraft, ResourceLocation id, int viewWidth, int viewHeight, int centerSnapPixels) {
		this(minecraft, id, viewWidth, viewHeight, centerSnapPixels, false);
	}

	public MapViewTexture(
		Minecraft minecraft,
		ResourceLocation id,
		int viewWidth,
		int viewHeight,
		int centerSnapPixels,
		boolean rotationOverscan
	) {
		this.minecraft = minecraft;
		this.id = id;
		this.viewWidth = viewWidth;
		this.viewHeight = viewHeight;
		this.centerSnapPixels = Math.max(0, centerSnapPixels);
		this.rotationPaddingX = rotationOverscan ? MinimapProjection.rotationPadding(viewWidth, viewHeight) : 0;
		this.rotationPaddingY = rotationOverscan ? MinimapProjection.rotationPadding(viewHeight, viewWidth) : 0;
		int snapPadding = (int)Math.ceil((this.centerSnapPixels + 1) * Math.sqrt(2.0));
		this.overscan = Math.max(DEFAULT_OVERSCAN, snapPadding + Math.max(this.rotationPaddingX, this.rotationPaddingY));
		this.textureWidth = viewWidth + this.overscan * 2;
		this.textureHeight = viewHeight + this.overscan * 2;
		this.basePixels = new int[this.textureWidth * this.textureHeight];
		this.sourceU = this.overscan;
		this.sourceV = this.overscan;
	}

	public void update(
		MapAtlas atlas,
		TerrainContourCache contours,
		String dimension,
		double centerX,
		double centerZ,
		double blocksPerScreenPixel,
		int displayWidth,
		int displayHeight,
		boolean circular,
		float unknownOpacity,
		boolean includeDetailedTerrain,
		boolean detailedTerrainRequiresMapCoverage,
		boolean showTerrainContours,
		int terrainContourRangeChunks,
		boolean highlightKnownBiomes,
		int biomeHighlightColor,
		float biomeHighlightOpacity
	) {
		this.ensureCreated();
		double blocksPerTexturePixelX = blocksPerScreenPixel * displayWidth / this.viewWidth;
		double blocksPerTexturePixelZ = blocksPerScreenPixel * displayHeight / this.viewHeight;
		double sampleCenterX = this.snapCenter(centerX, blocksPerTexturePixelX);
		double sampleCenterZ = this.snapCenter(centerZ, blocksPerTexturePixelZ);
		this.sourceU = (float)(this.overscan + (centerX - sampleCenterX) / blocksPerTexturePixelX);
		this.sourceV = (float)(this.overscan + (centerZ - sampleCenterZ) / blocksPerTexturePixelZ);

		boolean terrainContours = showTerrainContours
			&& this.minecraft.level != null
			&& this.minecraft.player != null
			&& dimension.equals(this.minecraft.level.dimension().location().toString());
		int effectiveContourRange = terrainContours
			? Math.min(Math.min(terrainContourRangeChunks, this.minecraft.options.getEffectiveRenderDistance()), 32)
			: 0;
		long terrainRefresh = terrainContours ? contours.version() : 0L;
		boolean biomeHighlight = highlightKnownBiomes;
		long biomeHighlightRefresh = 0L;
		boolean geometryUnchanged = dimension.equals(this.lastDimension)
			&& sampleCenterX == this.lastSampleCenterX
			&& sampleCenterZ == this.lastSampleCenterZ
			&& blocksPerScreenPixel == this.lastBlocksPerScreenPixel
			&& displayWidth == this.lastDisplayWidth
			&& displayHeight == this.lastDisplayHeight
			&& circular == this.lastCircular
			&& unknownOpacity == this.lastUnknownOpacity
			&& includeDetailedTerrain == this.lastIncludeDetailedTerrain
			&& detailedTerrainRequiresMapCoverage == this.lastDetailedTerrainRequiresMapCoverage
			&& terrainContours == this.lastTerrainContours
			&& effectiveContourRange == this.lastTerrainContourRange
			&& biomeHighlight == this.lastHighlightKnownBiomes
			&& biomeHighlightColor == this.lastBiomeHighlightColor
			&& biomeHighlightOpacity == this.lastBiomeHighlightOpacity;
		if (geometryUnchanged) {
			boolean sameAtlas = atlas == this.lastAtlas;
			boolean contentUnchanged = sameAtlas
				&& terrainRefresh == this.lastTerrainRefresh
				&& biomeHighlightRefresh == this.lastBiomeHighlightRefresh
				&& atlas.version() == this.lastAtlasVersion;
			long elapsed = System.nanoTime() - this.lastUploadNanos;
			long refreshInterval = this.viewWidth <= 320
				? MINIMAP_REFRESH_INTERVAL_NANOS
				: FULLSCREEN_REFRESH_INTERVAL_NANOS;
			if (contentUnchanged || (sameAtlas && elapsed < refreshInterval)) {
				return;
			}
		}
		long updateStartedAt = System.nanoTime();

		int unknownAlpha = Math.round(Math.clamp(unknownOpacity, 0.0F, 1.0F) * 255.0F);
		int unknownColor = unknownAlpha << 24 | 0x101216;
		int pixelCount = this.textureWidth * this.textureHeight;
		this.ensureTerrainBuffers(terrainContours ? pixelCount : 0);
		int[] terrainHeights = terrainContours ? this.terrainHeightBuffer : null;
		byte[] terrainKinds = terrainContours ? this.terrainKindBuffer : null;
		byte[] terrainFade = terrainContours ? this.terrainFadeBuffer : null;
		MapAtlas.ColorSampler colorSampler = atlas.sampler(
			dimension, includeDetailedTerrain, detailedTerrainRequiresMapCoverage
		);
		TerrainContourCache.Sampler terrainSampler = terrainContours ? contours.sampler(dimension) : null;
		MapAtlas.BiomeSampler biomeSampler = biomeHighlight ? atlas.biomeSampler(dimension) : null;
		if (terrainHeights != null) {
			Arrays.fill(terrainHeights, NO_HEIGHT);
			Arrays.fill(terrainKinds, (byte)0);
			Arrays.fill(terrainFade, (byte)0);
		}

		for (int y = 0; y < this.textureHeight; y++) {
			for (int x = 0; x < this.textureWidth; x++) {
				double dx = x + 0.5 - this.textureWidth / 2.0;
				double dz = y + 0.5 - this.textureHeight / 2.0;
				int worldX = (int)Math.floor(sampleCenterX + dx * blocksPerTexturePixelX);
				int worldZ = (int)Math.floor(sampleCenterZ + dz * blocksPerTexturePixelZ);
				int packedColor = colorSampler.colorAt(worldX, worldZ);
				if (packedColor != 0) {
					int color = PACKED_MAP_COLORS[packedColor & 0xFF];
					if (biomeSampler != null && biomeSampler.biomeAt(worldX, worldZ) != null) {
						color = alphaOver(color, 0xFF000000 | biomeHighlightColor & 0xFFFFFF, biomeHighlightOpacity);
					}
					this.setPixel(x, y, color);
					continue;
				}

				int color = unknownColor;
				if (terrainHeights != null) {
					int index = x + y * this.textureWidth;
					this.sampleTerrain(
						worldX, worldZ, effectiveContourRange, index,
						terrainHeights, terrainKinds, terrainFade, terrainSampler
					);
					if (terrainHeights[index] != NO_HEIGHT) {
						float fade = Byte.toUnsignedInt(terrainFade[index]) / 255.0F;
						color = TerrainFogStyle.composeTerrain(unknownColor, terrainKinds[index] == WATER, fade);
					}
				}
				this.setPixel(x, y, color);
			}
		}
		if (terrainHeights != null) {
			this.smoothTerrainBoundaries(terrainHeights, terrainKinds, terrainFade, unknownColor);
			this.drawTerrainContours(terrainHeights, terrainKinds, terrainFade);
		}
		// The 1.21.1 GUI paths do not apply GPU tint consistently. Invalidate the
		// uploaded copy; blit() will upload these base pixels with the current tint.
		this.uploadedTint = Integer.MIN_VALUE;

		this.lastDimension = dimension;
		this.lastSampleCenterX = sampleCenterX;
		this.lastSampleCenterZ = sampleCenterZ;
		this.lastBlocksPerScreenPixel = blocksPerScreenPixel;
		this.lastDisplayWidth = displayWidth;
		this.lastDisplayHeight = displayHeight;
		this.lastCircular = circular;
		this.lastUnknownOpacity = unknownOpacity;
		this.lastIncludeDetailedTerrain = includeDetailedTerrain;
		this.lastDetailedTerrainRequiresMapCoverage = detailedTerrainRequiresMapCoverage;
		this.lastAtlas = atlas;
		this.lastTerrainContours = terrainContours;
		this.lastTerrainContourRange = effectiveContourRange;
		this.lastTerrainRefresh = terrainRefresh;
		this.lastHighlightKnownBiomes = biomeHighlight;
		this.lastBiomeHighlightColor = biomeHighlightColor;
		this.lastBiomeHighlightOpacity = biomeHighlightOpacity;
		this.lastBiomeHighlightRefresh = biomeHighlightRefresh;
		this.lastAtlasVersion = atlas.version();
		this.lastUploadNanos = System.nanoTime();
		this.lastUpdateDurationNanos = this.lastUploadNanos - updateStartedAt;
	}

	public long lastUpdateDurationNanos() {
		return this.lastUpdateDurationNanos;
	}

	private void ensureTerrainBuffers(int requiredSize) {
		if (requiredSize <= 0 || (this.terrainHeightBuffer != null && this.terrainHeightBuffer.length == requiredSize)) {
			return;
		}
		this.terrainHeightBuffer = new int[requiredSize];
		this.terrainKindBuffer = new byte[requiredSize];
		this.terrainFadeBuffer = new byte[requiredSize];
	}

	public void blit(GuiGraphics graphics, int x, int y, int width, int height, int color) {
		this.ensureCreated();
		this.uploadTinted(color);
		graphics.flush();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.enableBlend();
		try {
			graphics.blit(
				this.id, x, y, width, height, this.sourceU, this.sourceV,
				this.viewWidth, this.viewHeight, this.textureWidth, this.textureHeight
			);
		} finally {
			RenderSystem.disableBlend();
		}
	}

	/** Draws the diagonal source area used while a square minimap is rotated by the GUI pose. */
	public void blitOverscanned(GuiGraphics graphics, int x, int y, int width, int height, int color) {
		this.ensureCreated();
		this.uploadTinted(color);
		graphics.flush();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.enableBlend();
		int drawPaddingX = (int)Math.ceil((double)this.rotationPaddingX * width / this.viewWidth);
		int drawPaddingY = (int)Math.ceil((double)this.rotationPaddingY * height / this.viewHeight);
		try {
			graphics.blit(
				this.id, x - drawPaddingX, y - drawPaddingY, width + drawPaddingX * 2, height + drawPaddingY * 2,
				this.sourceU - this.rotationPaddingX, this.sourceV - this.rotationPaddingY,
				this.viewWidth + this.rotationPaddingX * 2, this.viewHeight + this.rotationPaddingY * 2,
				this.textureWidth, this.textureHeight
			);
		} finally {
			RenderSystem.disableBlend();
		}
	}

	/** Draws a screen-stable circular crop without baking the moving crop into the cached map texture. */
	public void blitCircular(GuiGraphics graphics, int x, int y, int width, int height, int color) {
		this.ensureCreated();
		this.uploadTinted(color);
		graphics.flush();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.enableBlend();
		double radius = Math.min(width, height) / 2.0;
		double centerX = width / 2.0;
		double centerY = height / 2.0;
		float sourceScaleX = (float)this.viewWidth / width;
		float sourceScaleY = (float)this.viewHeight / height;
		try {
			for (int row = 0; row < height; row++) {
				double dy = row + 0.5 - centerY;
				double halfSpan = Math.sqrt(Math.max(0.0, radius * radius - dy * dy));
				int left = Math.max(0, (int)Math.ceil(centerX - halfSpan - 0.5));
				int right = Math.min(width, (int)Math.floor(centerX + halfSpan - 0.5) + 1);
				if (left >= right) {
					continue;
				}
				graphics.blit(
					this.id, x + left, y + row, right - left, 1,
					this.sourceU + left * sourceScaleX, this.sourceV + row * sourceScaleY,
					Math.max(1, Math.round((right - left) * sourceScaleX)), Math.max(1, Math.round(sourceScaleY)),
					this.textureWidth, this.textureHeight
				);
			}
		} finally {
			RenderSystem.disableBlend();
		}
	}

	private void uploadTinted(int tint) {
		if (this.uploadedTint == tint) {
			return;
		}
		for (int y = 0; y < this.textureHeight; y++) {
			for (int x = 0; x < this.textureWidth; x++) {
				int index = x + y * this.textureWidth;
				int argb = MapLighting.applyTint(this.basePixels[index], tint);
				this.texture.getPixels().setPixelRGBA(x, y, FastColor.ABGR32.fromArgb32(argb));
			}
		}
		this.texture.upload();
		this.uploadedTint = tint;
	}

	private void sampleTerrain(
		int worldX,
		int worldZ,
		int rangeChunks,
		int index,
		int[] heights,
		byte[] kinds,
		byte[] fades,
		TerrainContourCache.Sampler sampler
	) {
		if (this.minecraft.player == null || rangeChunks <= 0) {
			return;
		}
		double dx = worldX - this.minecraft.player.getX();
		double dz = worldZ - this.minecraft.player.getZ();
		int rangeBlocks = rangeChunks * 16;
		double distanceSquared = dx * dx + dz * dz;
		if (distanceSquared >= (double)rangeBlocks * rangeBlocks) {
			return;
		}
		int sample = sampler.sampleAt(worldX, worldZ);
		if (sample == TerrainContourCache.NO_SAMPLE) {
			return;
		}

		int height = sample >> 1;
		double fadeStart = rangeBlocks * 0.75;
		double fade = 1.0;
		if (distanceSquared > fadeStart * fadeStart) {
			double distance = Math.sqrt(distanceSquared);
			fade = Math.clamp((rangeBlocks - distance) / Math.max(1.0, rangeBlocks - fadeStart), 0.0, 1.0);
		}
		fade = fade * fade * (3.0 - 2.0 * fade);
		heights[index] = height;
		kinds[index] = (sample & 1) != 0 ? WATER : LAND;
		fades[index] = (byte)Math.round(fade * 255.0);
	}

	private void smoothTerrainBoundaries(int[] heights, byte[] kinds, byte[] fades, int unknownColor) {
		for (int y = 1; y < this.textureHeight - 1; y++) {
			for (int x = 1; x < this.textureWidth - 1; x++) {
				int index = x + y * this.textureWidth;
				if (heights[index] == NO_HEIGHT) {
					continue;
				}
				byte kind = kinds[index];
				if (differentTerrain(kind, kinds[index - 1]) || differentTerrain(kind, kinds[index + 1])
					|| differentTerrain(kind, kinds[index - this.textureWidth]) || differentTerrain(kind, kinds[index + this.textureWidth])) {
					float fade = Byte.toUnsignedInt(fades[index]) / 255.0F;
					this.setPixel(x, y, TerrainFogStyle.composeBoundary(unknownColor, fade));
				}
			}
		}
	}

	private void drawTerrainContours(int[] heights, byte[] kinds, byte[] fades) {
		for (int y = 0; y < this.textureHeight - 1; y++) {
			for (int x = 0; x < this.textureWidth - 1; x++) {
				int index = x + y * this.textureWidth;
				int height = heights[index];
				if (height == NO_HEIGHT) {
					continue;
				}
				int band = Math.floorDiv(height, 16);
				int right = heights[index + 1];
				int down = heights[index + this.textureWidth];
				if ((right != NO_HEIGHT && kinds[index + 1] == kinds[index] && Math.floorDiv(right, 16) != band)
					|| (down != NO_HEIGHT && kinds[index + this.textureWidth] == kinds[index] && Math.floorDiv(down, 16) != band)) {
					float fade = Byte.toUnsignedInt(fades[index]) / 255.0F;
					int base = this.getPixel(x, y);
					this.setPixel(x, y, TerrainFogStyle.applyContour(base, fade));
				}
			}
		}
	}

	private double snapCenter(double center, double blocksPerTexturePixel) {
		if (this.centerSnapPixels == 0) {
			return Math.floor(center / blocksPerTexturePixel) * blocksPerTexturePixel;
		}
		double step = blocksPerTexturePixel * this.centerSnapPixels;
		return Math.floor(center / step) * step;
	}

	private static boolean differentTerrain(byte expected, byte actual) {
		return actual != 0 && actual != expected;
	}

	private static int lerpColor(int from, int to, float amount) {
		int a = Math.round(channel(from, 24) + (channel(to, 24) - channel(from, 24)) * amount);
		int r = Math.round(channel(from, 16) + (channel(to, 16) - channel(from, 16)) * amount);
		int g = Math.round(channel(from, 8) + (channel(to, 8) - channel(from, 8)) * amount);
		int b = Math.round(channel(from, 0) + (channel(to, 0) - channel(from, 0)) * amount);
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static int alphaOver(int base, int overlay, float opacity) {
		float alpha = channel(overlay, 24) / 255.0F * opacity;
		int opaqueOverlay = 0xFF000000 | overlay & 0xFFFFFF;
		return lerpColor(base, opaqueOverlay, alpha);
	}

	private static int channel(int color, int shift) {
		return color >>> shift & 0xFF;
	}

	private static int[] createPackedMapColors() {
		int[] colors = new int[256];
		for (int packed = 0; packed < colors.length; packed++) {
			MapColor mapColor = MapColor.byId(packed >> 2);
			colors[packed] = PackedMapColor.argb(mapColor.col, packed & 3);
		}
		return colors;
	}

	private void setPixel(int x, int y, int argb) {
		this.basePixels[x + y * this.textureWidth] = argb;
	}

	private int getPixel(int x, int y) {
		return this.basePixels[x + y * this.textureWidth];
	}

	private void ensureCreated() {
		if (this.texture == null) {
			this.texture = new CrispDynamicTexture(this.textureWidth, this.textureHeight);
			this.minecraft.getTextureManager().register(this.id, this.texture);
		}
	}

	private static final class CrispDynamicTexture extends DynamicTexture {
		private CrispDynamicTexture(int width, int height) {
			super(width, height, true);
			this.setFilter(false, false);
		}
	}

	@Override
	public void close() {
		if (this.texture != null) {
			this.minecraft.getTextureManager().release(this.id);
			this.texture = null;
			this.uploadedTint = Integer.MIN_VALUE;
		}
	}
}
