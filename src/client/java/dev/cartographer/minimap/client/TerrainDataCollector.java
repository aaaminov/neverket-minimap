package dev.cartographer.minimap.client;

import dev.cartographer.minimap.atlas.MapAtlas;
import dev.cartographer.minimap.config.ModConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** Incrementally records surface colors and biome quart samples from chunks already loaded by the client. */
public final class TerrainDataCollector {
	private static final int MAX_RANGE_CHUNKS = 32;
	private static final int MAX_SCAN_ATTEMPTS_PER_TICK = 128;
	private static final int DISCOVERY_RESCAN_INTERVAL_TICKS = 20;
	private static final int PLAYER_REFRESH_INTERVAL_TICKS = 10;
	private static final int BACKGROUND_REFRESH_INTERVAL_TICKS = 4;
	private static final int MAX_REFRESH_ATTEMPTS_PER_TICK = 64;

	private ClientLevel level;
	private String dimension;
	private int scanIndex;
	private int refreshIndex;
	private int ticksUntilDiscoveryRescan;
	private int ticksUntilPlayerRefresh;
	private int ticksUntilBackgroundRefresh;
	private int scanRange = -1;
	private List<ChunkOffset> scanOffsets = List.of();

	public void tick(Minecraft minecraft, MapAtlas atlas, ModConfig config) {
		if (minecraft.player == null || minecraft.level == null) {
			this.reset();
			return;
		}
		ClientLevel level = minecraft.level;
		String currentDimension = level.dimension().identifier().toString();
		int playerChunkX = Math.floorDiv((int)Math.floor(minecraft.player.getX()), 16);
		int playerChunkZ = Math.floorDiv((int)Math.floor(minecraft.player.getZ()), 16);
		int range = recordingRangeChunks(minecraft);
		boolean rangeChanged = range != this.scanRange;
		if (rangeChanged) {
			this.scanRange = range;
			this.scanOffsets = radialOffsets(range);
		}
		if (level != this.level || rangeChanged || !currentDimension.equals(this.dimension)) {
			this.level = level;
			this.dimension = currentDimension;
			this.scanIndex = 0;
			this.refreshIndex = 0;
			this.ticksUntilDiscoveryRescan = 0;
			this.ticksUntilPlayerRefresh = PLAYER_REFRESH_INTERVAL_TICKS;
			this.ticksUntilBackgroundRefresh = BACKGROUND_REFRESH_INTERVAL_TICKS;
		}

		if (this.scanIndex >= this.scanOffsets.size()) {
			if (this.ticksUntilDiscoveryRescan > 0) {
				this.ticksUntilDiscoveryRescan--;
			} else {
				this.scanIndex = 0;
			}
		}
		boolean discoverySweepActive = this.scanIndex < this.scanOffsets.size();
		boolean sampled = this.tryRecord(level, atlas, config, currentDimension, playerChunkX, playerChunkZ, false);
		if (this.ticksUntilPlayerRefresh > 0) {
			this.ticksUntilPlayerRefresh--;
		}
		if (!sampled && this.ticksUntilPlayerRefresh <= 0) {
			this.ticksUntilPlayerRefresh = PLAYER_REFRESH_INTERVAL_TICKS;
			sampled = this.tryRecord(level, atlas, config, currentDimension, playerChunkX, playerChunkZ, true);
		}
		for (int attempts = 0;
			!sampled && attempts < MAX_SCAN_ATTEMPTS_PER_TICK && this.scanIndex < this.scanOffsets.size();
			this.scanIndex++, attempts++) {
			ChunkOffset offset = this.scanOffsets.get(this.scanIndex);
			int chunkX = playerChunkX + offset.x();
			int chunkZ = playerChunkZ + offset.z();
			sampled = this.tryRecord(level, atlas, config, currentDimension, chunkX, chunkZ, false);
		}
		if (discoverySweepActive && this.scanIndex >= this.scanOffsets.size()) {
			this.ticksUntilDiscoveryRescan = DISCOVERY_RESCAN_INTERVAL_TICKS;
		}

		if (this.ticksUntilBackgroundRefresh > 0) {
			this.ticksUntilBackgroundRefresh--;
		}
		if (!sampled && this.ticksUntilBackgroundRefresh <= 0) {
			this.ticksUntilBackgroundRefresh = BACKGROUND_REFRESH_INTERVAL_TICKS;
			this.refreshNextLoadedChunk(level, atlas, config, currentDimension, playerChunkX, playerChunkZ);
		}
	}

	static int recordingRangeChunks(Minecraft minecraft) {
		return Math.min(minecraft.options.getEffectiveRenderDistance(), MAX_RANGE_CHUNKS);
	}

	private void refreshNextLoadedChunk(
		ClientLevel level,
		MapAtlas atlas,
		ModConfig config,
		String dimension,
		int playerChunkX,
		int playerChunkZ
	) {
		for (int attempts = 0; attempts < MAX_REFRESH_ATTEMPTS_PER_TICK; attempts++) {
			ChunkOffset offset = this.scanOffsets.get(this.refreshIndex);
			this.refreshIndex = (this.refreshIndex + 1) % this.scanOffsets.size();
			int chunkX = playerChunkX + offset.x();
			int chunkZ = playerChunkZ + offset.z();
			if (chunkX == playerChunkX && chunkZ == playerChunkZ) {
				continue;
			}
			if (this.tryRecord(level, atlas, config, dimension, chunkX, chunkZ, true)) {
				return;
			}
		}
	}

	private boolean tryRecord(
		ClientLevel level,
		MapAtlas atlas,
		ModConfig config,
		String dimension,
		int chunkX,
		int chunkZ,
		boolean refreshExisting
	) {
		if (config.recordingMode == ModConfig.RecordingMode.MAPS && !atlas.hasMapCoverage(dimension, chunkX, chunkZ)) {
			return false;
		}
		boolean recordTerrain = config.recordingMode == ModConfig.RecordingMode.EXPLORED_TERRAIN
			|| config.mapDetailMode == ModConfig.MapDetailMode.LOADED_TERRAIN_DETAIL;
		boolean hasTerrain = atlas.hasTerrainChunk(dimension, chunkX, chunkZ);
		boolean hasBiomes = atlas.hasBiomeChunk(dimension, chunkX, chunkZ);
		if (refreshExisting && !(recordTerrain && hasTerrain) && !hasBiomes) {
			return false;
		}
		boolean needsTerrain = recordTerrain && (refreshExisting || !hasTerrain);
		boolean needsBiomes = refreshExisting || !hasBiomes;
		if (!needsTerrain && !needsBiomes) {
			return false;
		}
		LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
		if (chunk == null) {
			return false;
		}
		if (needsBiomes) {
			atlas.putBiomeChunk(dimension, chunkX, chunkZ, this.sampleBiomeChunk(level, chunk, chunkX, chunkZ));
		}
		if (needsTerrain) {
			atlas.putTerrainChunk(dimension, chunkX, chunkZ, this.sampleChunk(level, chunk, chunkX, chunkZ));
		}
		return true;
	}

	private String[] sampleBiomeChunk(ClientLevel level, LevelChunk chunk, int chunkX, int chunkZ) {
		String[] biomes = new String[4 * 4];
		BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
		for (int quartZ = 0; quartZ < 4; quartZ++) {
			for (int quartX = 0; quartX < 4; quartX++) {
				int localX = quartX * 4 + 2;
				int localZ = quartZ * 4 + 2;
				int worldX = chunkX * 16 + localX;
				int worldZ = chunkZ * 16 + localZ;
				int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
				position.set(worldX, height, worldZ);
				biomes[quartX + quartZ * 4] = level.getBiome(position)
					.unwrapKey()
					.map(key -> key.identifier().toString())
					.orElse("");
			}
		}
		return biomes;
	}

	private byte[] sampleChunk(ClientLevel level, LevelChunk chunk, int chunkX, int chunkZ) {
		byte[] colors = new byte[16 * 16];
		BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos depthPosition = new BlockPos.MutableBlockPos();
		SurfaceSample sample = new SurfaceSample();
		LevelChunk northChunk = level.getChunkSource().getChunk(chunkX, chunkZ - 1, ChunkStatus.FULL, false);
		for (int x = 0; x < 16; x++) {
			double previousHeight = Double.NaN;
			if (northChunk != null) {
				this.sampleSurface(
					northChunk, chunkX * 16 + x, chunkZ * 16 - 1, x, 15, position, depthPosition, sample
				);
				previousHeight = sample.height;
			}
			for (int z = 0; z < 16; z++) {
				int worldX = chunkX * 16 + x;
				int worldZ = chunkZ * 16 + z;
				this.sampleSurface(chunk, worldX, worldZ, x, z, position, depthPosition, sample);
				if (sample.mapColor != MapColor.NONE) {
					MapColor.Brightness brightness = sample.mapColor == MapColor.WATER
						? waterBrightness(sample.waterDepth, worldX, worldZ)
						: terrainBrightness(sample.height, previousHeight, worldX, worldZ);
					colors[x + z * 16] = sample.mapColor.getPackedId(brightness);
				}
				previousHeight = sample.height;
			}
		}
		return colors;
	}

	private void sampleSurface(
		LevelChunk chunk,
		int worldX,
		int worldZ,
		int localX,
		int localZ,
		BlockPos.MutableBlockPos position,
		BlockPos.MutableBlockPos depthPosition,
		SurfaceSample result
	) {
		int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
		MapColor mapColor = MapColor.NONE;
		BlockState state = null;
		while (y >= chunk.getMinY() && mapColor == MapColor.NONE) {
			position.set(worldX, y, worldZ);
			state = chunk.getBlockState(position);
			mapColor = state.getMapColor(chunk, position);
			if (mapColor == MapColor.NONE) {
				y--;
			}
		}

		int waterDepth = 0;
		if (state != null && !state.getFluidState().isEmpty()) {
			int depthY = y - 1;
			do {
				depthPosition.set(worldX, depthY--, worldZ);
				waterDepth++;
			} while (depthY > chunk.getMinY() && !chunk.getBlockState(depthPosition).getFluidState().isEmpty());
			if (!state.isFaceSturdy(chunk, position, Direction.UP)) {
				state = state.getFluidState().createLegacyBlock();
				mapColor = state.getMapColor(chunk, position);
			}
		}
		result.height = y;
		result.waterDepth = waterDepth;
		result.mapColor = mapColor;
	}

	private static MapColor.Brightness waterBrightness(int depth, int worldX, int worldZ) {
		double shade = depth * 0.1 + ((worldX + worldZ) & 1) * 0.2;
		if (shade < 0.5) {
			return MapColor.Brightness.HIGH;
		}
		return shade > 0.9 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
	}

	private static MapColor.Brightness terrainBrightness(double height, double previousHeight, int worldX, int worldZ) {
		if (Double.isNaN(previousHeight)) {
			return MapColor.Brightness.NORMAL;
		}
		double shade = (height - previousHeight) * 4.0 / 5.0 + (((worldX + worldZ) & 1) - 0.5) * 0.4;
		if (shade > 0.6) {
			return MapColor.Brightness.HIGH;
		}
		return shade < -0.6 ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
	}

	private void reset() {
		this.level = null;
		this.dimension = null;
		this.scanIndex = 0;
		this.refreshIndex = 0;
		this.ticksUntilDiscoveryRescan = 0;
		this.ticksUntilPlayerRefresh = 0;
		this.ticksUntilBackgroundRefresh = 0;
	}

	private static List<ChunkOffset> radialOffsets(int range) {
		List<ChunkOffset> offsets = new ArrayList<>((range * 2 + 1) * (range * 2 + 1));
		for (int z = -range; z <= range; z++) {
			for (int x = -range; x <= range; x++) {
				offsets.add(new ChunkOffset(x, z));
			}
		}
		offsets.sort(Comparator.comparingInt(ChunkOffset::distanceSquared));
		return List.copyOf(offsets);
	}

	private record ChunkOffset(int x, int z) {
		private int distanceSquared() {
			return this.x * this.x + this.z * this.z;
		}
	}

	private static final class SurfaceSample {
		private int height;
		private int waterDepth;
		private MapColor mapColor = MapColor.NONE;
	}
}
