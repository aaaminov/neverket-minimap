package dev.neverket.minimap.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.neverket.minimap.atlas.MapAtlas;
import dev.neverket.minimap.config.ModConfig.UnknownTerrain;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.MapColor;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

public final class MapViewTexture implements AutoCloseable {
	private static final int DEFAULT_OVERSCAN = 4;
	private static final long MINIMAP_REFRESH_INTERVAL_NANOS = 100_000_000L;
	private static final long FULLSCREEN_REFRESH_INTERVAL_NANOS = 350_000_000L;
	private static final int NO_HEIGHT = Integer.MIN_VALUE;
	private static final byte LAND = 1;
	private static final byte WATER = 2;
	private static final int[] PACKED_MAP_COLORS = createPackedMapColors();

	private final Minecraft minecraft;
	private final Identifier id;
	private final int viewWidth;
	private final int viewHeight;
	private final int centerSnapPixels;
	private final int overscan;
	private final int textureWidth;
	private final int textureHeight;
	private CrispDynamicTexture texture;
	private String lastDimension;
	private double lastSampleCenterX = Double.NaN;
	private double lastSampleCenterZ = Double.NaN;
	private double lastBlocksPerScreenPixel = Double.NaN;
	private int lastDisplayWidth;
	private int lastDisplayHeight;
	private boolean lastCircular;
	private UnknownTerrain lastUnknown;
	private boolean lastDimTransparentUnknown;
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

	public MapViewTexture(Minecraft minecraft, Identifier id, int viewWidth, int viewHeight) {
		this(minecraft, id, viewWidth, viewHeight, 0);
	}

	public MapViewTexture(Minecraft minecraft, Identifier id, int viewWidth, int viewHeight, int centerSnapPixels) {
		this.minecraft = minecraft;
		this.id = id;
		this.viewWidth = viewWidth;
		this.viewHeight = viewHeight;
		this.centerSnapPixels = Math.max(0, centerSnapPixels);
		this.overscan = Math.max(DEFAULT_OVERSCAN, centerSnapPixels + 1);
		this.textureWidth = viewWidth + this.overscan * 2;
		this.textureHeight = viewHeight + this.overscan * 2;
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
		UnknownTerrain unknown,
		boolean dimTransparentUnknown,
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
			&& dimension.equals(this.minecraft.level.dimension().identifier().toString());
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
			&& unknown == this.lastUnknown
			&& dimTransparentUnknown == this.lastDimTransparentUnknown
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

		int unknownColor = unknown == UnknownTerrain.DARK ? 0xFF101216 : dimTransparentUnknown ? 0x50101216 : 0;
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
					this.texture.getPixels().setPixel(x, y, color);
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
						int terrainColor = this.terrainColor(unknown, terrainKinds[index] == WATER);
						color = lerpColor(unknownColor, terrainColor, fade);
					}
				}
				this.texture.getPixels().setPixel(x, y, color);
			}
		}
		if (terrainHeights != null) {
			this.smoothTerrainBoundaries(terrainHeights, terrainKinds, terrainFade, unknown, unknownColor);
			this.drawTerrainContours(terrainHeights, terrainKinds, terrainFade);
		}
		this.texture.upload();

		this.lastDimension = dimension;
		this.lastSampleCenterX = sampleCenterX;
		this.lastSampleCenterZ = sampleCenterZ;
		this.lastBlocksPerScreenPixel = blocksPerScreenPixel;
		this.lastDisplayWidth = displayWidth;
		this.lastDisplayHeight = displayHeight;
		this.lastCircular = circular;
		this.lastUnknown = unknown;
		this.lastDimTransparentUnknown = dimTransparentUnknown;
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

	public void blit(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		this.ensureCreated();
		graphics.blit(
			RenderPipelines.GUI_TEXTURED, this.id, x, y, this.sourceU, this.sourceV, width, height,
			this.viewWidth, this.viewHeight, this.textureWidth, this.textureHeight, color
		);
	}

	/** Draws a screen-stable circular crop without baking the moving crop into the cached map texture. */
	public void blitCircular(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
		this.ensureCreated();
		AbstractTexture renderedTexture = this.minecraft.getTextureManager().getTexture(this.id);
		graphics.guiRenderState.addGuiElement(new CircularBlitRenderState(
			TextureSetup.singleTexture(renderedTexture.getTextureView(), renderedTexture.getSampler()),
			new Matrix3x2f(graphics.pose()), x, y, width, height, this.sourceU, this.sourceV,
			this.textureWidth, this.textureHeight, color, graphics.scissorStack.peek()
		));
	}

	private record CircularBlitRenderState(
		TextureSetup textureSetup,
		Matrix3x2fc pose,
		int x,
		int y,
		int width,
		int height,
		float sourceU,
		float sourceV,
		int textureWidth,
		int textureHeight,
		int color,
		@Nullable ScreenRectangle scissorArea,
		@Nullable ScreenRectangle bounds
	) implements GuiElementRenderState {
		private CircularBlitRenderState(
			TextureSetup textureSetup,
			Matrix3x2fc pose,
			int x,
			int y,
			int width,
			int height,
			float sourceU,
			float sourceV,
			int textureWidth,
			int textureHeight,
			int color,
			@Nullable ScreenRectangle scissorArea
		) {
			this(
				textureSetup, pose, x, y, width, height, sourceU, sourceV, textureWidth, textureHeight, color,
				scissorArea, bounds(x, y, width, height, pose, scissorArea)
			);
		}

		@Override
		public com.mojang.blaze3d.pipeline.RenderPipeline pipeline() {
			return RenderPipelines.GUI_TEXTURED;
		}

		@Override
		public void buildVertices(VertexConsumer vertices) {
			double radius = Math.min(this.width, this.height) / 2.0;
			double centerX = this.width / 2.0;
			double centerY = this.height / 2.0;
			for (int row = 0; row < this.height; row++) {
				double dy = row + 0.5 - centerY;
				double halfSpan = Math.sqrt(Math.max(0.0, radius * radius - dy * dy));
				int left = Math.max(0, (int)Math.ceil(centerX - halfSpan - 0.5));
				int right = Math.min(this.width, (int)Math.floor(centerX + halfSpan - 0.5) + 1);
				if (left >= right) {
					continue;
				}
				float u0 = (this.sourceU + left) / this.textureWidth;
				float u1 = (this.sourceU + right) / this.textureWidth;
				float v0 = (this.sourceV + row) / this.textureHeight;
				float v1 = (this.sourceV + row + 1) / this.textureHeight;
				int x0 = this.x + left;
				int x1 = this.x + right;
				int y0 = this.y + row;
				int y1 = y0 + 1;
				vertices.addVertexWith2DPose(this.pose, x0, y0).setUv(u0, v0).setColor(this.color);
				vertices.addVertexWith2DPose(this.pose, x0, y1).setUv(u0, v1).setColor(this.color);
				vertices.addVertexWith2DPose(this.pose, x1, y1).setUv(u1, v1).setColor(this.color);
				vertices.addVertexWith2DPose(this.pose, x1, y0).setUv(u1, v0).setColor(this.color);
			}
		}

		private static @Nullable ScreenRectangle bounds(
			int x, int y, int width, int height, Matrix3x2fc pose, @Nullable ScreenRectangle scissorArea
		) {
			ScreenRectangle bounds = new ScreenRectangle(x, y, width, height).transformMaxBounds(pose);
			return scissorArea == null ? bounds : scissorArea.intersection(bounds);
		}
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

	private int terrainColor(UnknownTerrain unknown, boolean water) {
		if (unknown == UnknownTerrain.DARK) {
			return water ? 0xFF292929 : 0xFF5A5A5A;
		}
		return water ? 0x88292929 : 0x885A5A5A;
	}

	private void smoothTerrainBoundaries(int[] heights, byte[] kinds, byte[] fades, UnknownTerrain unknown, int unknownColor) {
		int transitionColor = unknown == UnknownTerrain.DARK ? 0xFF424242 : 0x88424242;
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
					this.texture.getPixels().setPixel(x, y, lerpColor(unknownColor, transitionColor, fade));
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
					int base = this.texture.getPixels().getPixel(x, y);
					this.texture.getPixels().setPixel(x, y, alphaOver(base, 0xFF090B0D, fade * 0.16F));
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
			colors[packed] = MapColor.getColorFromPackedId(packed);
		}
		return colors;
	}

	private void ensureCreated() {
		if (this.texture == null) {
			this.texture = new CrispDynamicTexture(() -> "Neverket Minimap " + this.id, this.textureWidth, this.textureHeight);
			this.minecraft.getTextureManager().register(this.id, this.texture);
		}
	}

	private static final class CrispDynamicTexture extends DynamicTexture {
		private CrispDynamicTexture(java.util.function.Supplier<String> label, int width, int height) {
			super(label, width, height, true);
			this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
		}
	}

	@Override
	public void close() {
		if (this.texture != null) {
			this.minecraft.getTextureManager().release(this.id);
			this.texture = null;
		}
	}
}
