package dev.cartographer.minimap.client;

import dev.cartographer.minimap.atlas.MapAtlas;
import dev.cartographer.minimap.config.ModConfig.UnknownTerrain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.MapColor;

public final class MapViewTexture implements AutoCloseable {
	private final Minecraft minecraft;
	private final Identifier id;
	private final int textureSize;
	private DynamicTexture texture;
	private String lastDimension;
	private double lastCenterX = Double.NaN;
	private double lastCenterZ = Double.NaN;
	private double lastBlocksPerScreenPixel = Double.NaN;
	private int lastDisplaySize;
	private boolean lastCircular;
	private UnknownTerrain lastUnknown;
	private long lastAtlasVersion = Long.MIN_VALUE;

	public MapViewTexture(Minecraft minecraft, Identifier id, int textureSize) {
		this.minecraft = minecraft;
		this.id = id;
		this.textureSize = textureSize;
	}

	public Identifier id() {
		this.ensureCreated();
		return this.id;
	}

	public void update(
		MapAtlas atlas,
		String dimension,
		double centerX,
		double centerZ,
		double blocksPerScreenPixel,
		int displaySize,
		boolean circular,
		UnknownTerrain unknown
	) {
		this.ensureCreated();
		if (dimension.equals(this.lastDimension)
			&& centerX == this.lastCenterX
			&& centerZ == this.lastCenterZ
			&& blocksPerScreenPixel == this.lastBlocksPerScreenPixel
			&& displaySize == this.lastDisplaySize
			&& circular == this.lastCircular
			&& unknown == this.lastUnknown
			&& atlas.version() == this.lastAtlasVersion) {
			return;
		}

		double blocksPerTexturePixel = blocksPerScreenPixel * displaySize / this.textureSize;
		double radiusSquared = this.textureSize * this.textureSize / 4.0;
		int unknownColor = unknown == UnknownTerrain.DARK ? 0xFF101216 : 0;
		for (int y = 0; y < this.textureSize; y++) {
			for (int x = 0; x < this.textureSize; x++) {
				double dx = x + 0.5 - this.textureSize / 2.0;
				double dz = y + 0.5 - this.textureSize / 2.0;
				if (circular && dx * dx + dz * dz > radiusSquared) {
					this.texture.getPixels().setPixel(x, y, 0);
					continue;
				}

				int worldX = (int)Math.floor(centerX + dx * blocksPerTexturePixel);
				int worldZ = (int)Math.floor(centerZ + dz * blocksPerTexturePixel);
				int packedColor = atlas.colorAt(dimension, worldX, worldZ);
				this.texture.getPixels().setPixel(x, y, packedColor == 0 ? unknownColor : MapColor.getColorFromPackedId(packedColor));
			}
		}
		this.texture.upload();

		this.lastDimension = dimension;
		this.lastCenterX = centerX;
		this.lastCenterZ = centerZ;
		this.lastBlocksPerScreenPixel = blocksPerScreenPixel;
		this.lastDisplaySize = displaySize;
		this.lastCircular = circular;
		this.lastUnknown = unknown;
		this.lastAtlasVersion = atlas.version();
	}

	private void ensureCreated() {
		if (this.texture == null) {
			this.texture = new DynamicTexture(() -> "Cartographer Minimap " + this.id, this.textureSize, this.textureSize, true);
			this.minecraft.getTextureManager().register(this.id, this.texture);
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
