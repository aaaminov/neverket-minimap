package dev.neverket.minimap.client;

import dev.neverket.minimap.atlas.MapAtlas;
import dev.neverket.minimap.atlas.RecordingArea;
import dev.neverket.minimap.config.ModConfig;
import java.util.ArrayDeque;
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
	private static final int MAX_CHUNK_UPDATES_PER_TICK = 2;
	private static final long UPDATE_BUDGET_NANOS = 1_000_000L;
	private static final int DISCOVERY_RESCAN_INTERVAL_TICKS = 40;
	private static final int PLAYER_REFRESH_INTERVAL_TICKS = 20;
	private static final int NEARBY_REFRESH_INTERVAL_TICKS = 10;
	private static final int BACKGROUND_REFRESH_INTERVAL_TICKS = 40;
	private static final int MAX_REFRESH_ATTEMPTS_PER_TICK = 16;
	private static final int DEBUG_HISTORY_SIZE = 16;
	private static final List<ChunkOffset> NEARBY_OFFSETS = List.of(
		new ChunkOffset(0, -1), new ChunkOffset(1, 0), new ChunkOffset(0, 1), new ChunkOffset(-1, 0),
		new ChunkOffset(1, -1), new ChunkOffset(1, 1), new ChunkOffset(-1, 1), new ChunkOffset(-1, -1)
	);

	private ClientLevel level;
	private String dimension;
	private int scanIndex;
	private int refreshIndex;
	private int nearbyIndex;
	private int ticksUntilDiscoveryRescan;
	private int ticksUntilPlayerRefresh;
	private int ticksUntilNearbyRefresh;
	private int ticksUntilBackgroundRefresh;
	private int currentFarRing;
	private int lastPlayerChunkX = Integer.MIN_VALUE;
	private int lastPlayerChunkZ = Integer.MIN_VALUE;
	private int contourRange;
	private int scanRange = -1;
	private List<ChunkOffset> scanOffsets = List.of();
	private final ArrayDeque<ChunkUpdate> recentUpdates = new ArrayDeque<>(DEBUG_HISTORY_SIZE);

	public void tick(Minecraft minecraft, MapAtlas atlas, TerrainContourCache contours, ModConfig config) {
		if (minecraft.player == null || minecraft.level == null) {
			this.reset();
			return;
		}
		ClientLevel level = minecraft.level;
		String currentDimension = level.dimension().identifier().toString();
		int playerChunkX = Math.floorDiv((int)Math.floor(minecraft.player.getX()), 16);
		int playerChunkZ = Math.floorDiv((int)Math.floor(minecraft.player.getZ()), 16);
		int range = recordingRangeChunks(minecraft);
		int currentContourRange = terrainContourRangeChunks(minecraft, config);
		boolean contourRangeChanged = currentContourRange != this.contourRange;
		this.contourRange = currentContourRange;
		boolean rangeChanged = range != this.scanRange;
		boolean worldChanged = level != this.level || !currentDimension.equals(this.dimension);
		if (rangeChanged) {
			this.scanRange = range;
			this.scanOffsets = radialOffsets(range);
		}
		if (worldChanged || rangeChanged) {
			this.level = level;
			this.dimension = currentDimension;
			this.scanIndex = 0;
			this.refreshIndex = 0;
			this.nearbyIndex = 0;
			this.ticksUntilDiscoveryRescan = 0;
			this.ticksUntilPlayerRefresh = PLAYER_REFRESH_INTERVAL_TICKS;
			this.ticksUntilNearbyRefresh = NEARBY_REFRESH_INTERVAL_TICKS;
			this.ticksUntilBackgroundRefresh = BACKGROUND_REFRESH_INTERVAL_TICKS;
			this.currentFarRing = 0;
			if (worldChanged) {
				this.recentUpdates.clear();
				contours.clear();
			}
		}
		boolean playerChunkChanged = playerChunkX != this.lastPlayerChunkX || playerChunkZ != this.lastPlayerChunkZ;
		if (worldChanged || rangeChanged || contourRangeChanged || playerChunkChanged) {
			contours.retainWithin(currentDimension, playerChunkX, playerChunkZ, this.contourRange);
			if (playerChunkChanged) {
				this.scanIndex = 0;
				this.refreshIndex = 0;
				this.nearbyIndex = 0;
				this.ticksUntilDiscoveryRescan = 0;
				this.ticksUntilNearbyRefresh = 0;
				this.currentFarRing = 0;
			}
			this.lastPlayerChunkX = playerChunkX;
			this.lastPlayerChunkZ = playerChunkZ;
		}
		if (this.scanIndex >= this.scanOffsets.size()) {
			if (this.ticksUntilDiscoveryRescan > 0) {
				this.ticksUntilDiscoveryRescan--;
			} else {
				this.scanIndex = 0;
			}
		}
		boolean discoverySweepActive = this.scanIndex < this.scanOffsets.size();
		long updateDeadline = System.nanoTime() + UPDATE_BUDGET_NANOS;
		int updates = this.tryRecord(
			level, atlas, contours, config, currentDimension, playerChunkX, playerChunkZ, UpdateKind.DISCOVERY
		) ? 1 : 0;
		if (this.ticksUntilPlayerRefresh > 0) {
			this.ticksUntilPlayerRefresh--;
		}
		if (updates < MAX_CHUNK_UPDATES_PER_TICK && System.nanoTime() < updateDeadline && this.ticksUntilPlayerRefresh <= 0) {
			this.ticksUntilPlayerRefresh = PLAYER_REFRESH_INTERVAL_TICKS;
			if (this.tryRecord(
				level, atlas, contours, config, currentDimension, playerChunkX, playerChunkZ, UpdateKind.PLAYER_REFRESH
			)) updates++;
		}
		if (this.ticksUntilNearbyRefresh > 0) {
			this.ticksUntilNearbyRefresh--;
		}
		if (updates < MAX_CHUNK_UPDATES_PER_TICK && System.nanoTime() < updateDeadline && this.ticksUntilNearbyRefresh <= 0) {
			this.ticksUntilNearbyRefresh = NEARBY_REFRESH_INTERVAL_TICKS;
			if (this.refreshNearbyChunk(
				level, atlas, contours, config, currentDimension, playerChunkX, playerChunkZ
			)) updates++;
		}
		for (int attempts = 0;
			attempts < MAX_SCAN_ATTEMPTS_PER_TICK
				&& updates < MAX_CHUNK_UPDATES_PER_TICK
				&& this.scanIndex < this.scanOffsets.size()
				&& System.nanoTime() < updateDeadline;
			this.scanIndex++, attempts++) {
			ChunkOffset offset = this.scanOffsets.get(this.scanIndex);
			this.currentFarRing = offset.ring();
			int chunkX = playerChunkX + offset.x();
			int chunkZ = playerChunkZ + offset.z();
			boolean discovered = this.tryRecord(
				level, atlas, contours, config, currentDimension, chunkX, chunkZ, UpdateKind.DISCOVERY
			);
			if (discovered) {
				updates++;
			}
		}
		if (discoverySweepActive && this.scanIndex >= this.scanOffsets.size()) {
			this.ticksUntilDiscoveryRescan = DISCOVERY_RESCAN_INTERVAL_TICKS;
		}

		if (this.ticksUntilBackgroundRefresh > 0) {
			this.ticksUntilBackgroundRefresh--;
		}
		if (updates == 0 && this.ticksUntilBackgroundRefresh <= 0) {
			this.ticksUntilBackgroundRefresh = BACKGROUND_REFRESH_INTERVAL_TICKS;
			this.refreshNextLoadedChunk(level, atlas, contours, config, currentDimension, playerChunkX, playerChunkZ);
		}
	}

	static int recordingRangeChunks(Minecraft minecraft) {
		return Math.min(minecraft.options.getEffectiveRenderDistance(), MAX_RANGE_CHUNKS);
	}

	static int terrainContourRangeChunks(Minecraft minecraft, ModConfig config) {
		return Math.min(recordingRangeChunks(minecraft), config.terrainContourRangeChunks);
	}

	public List<ChunkUpdate> recentUpdates() {
		return List.copyOf(this.recentUpdates);
	}

	public int currentFarRing() {
		return this.currentFarRing;
	}

	private boolean refreshNearbyChunk(
		ClientLevel level,
		MapAtlas atlas,
		TerrainContourCache contours,
		ModConfig config,
		String dimension,
		int playerChunkX,
		int playerChunkZ
	) {
		for (int attempts = 0; attempts < NEARBY_OFFSETS.size(); attempts++) {
			ChunkOffset offset = NEARBY_OFFSETS.get(this.nearbyIndex);
			this.nearbyIndex = (this.nearbyIndex + 1) % NEARBY_OFFSETS.size();
			if (this.tryRecord(
				level, atlas, contours, config, dimension,
				playerChunkX + offset.x(), playerChunkZ + offset.z(), UpdateKind.NEARBY_REFRESH
			)) {
				return true;
			}
		}
		return false;
	}

	private void refreshNextLoadedChunk(
		ClientLevel level,
		MapAtlas atlas,
		TerrainContourCache contours,
		ModConfig config,
		String dimension,
		int playerChunkX,
		int playerChunkZ
	) {
		for (int attempts = 0; attempts < MAX_REFRESH_ATTEMPTS_PER_TICK; attempts++) {
			ChunkOffset offset = this.scanOffsets.get(this.refreshIndex);
			this.currentFarRing = offset.ring();
			this.refreshIndex = (this.refreshIndex + 1) % this.scanOffsets.size();
			int chunkX = playerChunkX + offset.x();
			int chunkZ = playerChunkZ + offset.z();
			if (chunkX == playerChunkX && chunkZ == playerChunkZ) {
				continue;
			}
			if (this.tryRecord(
				level, atlas, contours, config, dimension, chunkX, chunkZ, UpdateKind.BACKGROUND_REFRESH
			)) {
				return;
			}
		}
	}

	private boolean tryRecord(
		ClientLevel level,
		MapAtlas atlas,
		TerrainContourCache contours,
		ModConfig config,
		String dimension,
		int chunkX,
		int chunkZ,
		UpdateKind updateKind
	) {
		boolean mapCovered = config.recordingMode != ModConfig.RecordingMode.MAPS
			|| atlas.hasMapCoverage(dimension, chunkX, chunkZ);
		boolean recordTerrain = mapCovered && (config.recordingMode == ModConfig.RecordingMode.EXPLORED_TERRAIN
			|| config.mapDetailMode == ModConfig.MapDetailMode.LOADED_TERRAIN_DETAIL);
		boolean hasTerrain = atlas.hasTerrainChunk(dimension, chunkX, chunkZ);
		boolean hasBiomes = atlas.hasBiomeChunk(dimension, chunkX, chunkZ);
		boolean needsTerrain = recordTerrain && (updateKind.refreshExisting() || !hasTerrain);
		boolean needsBiomes = mapCovered && (updateKind.refreshExisting() || !hasBiomes);
		boolean insideContourRange = RecordingArea.containsChunkOffset(
			chunkX - this.lastPlayerChunkX, chunkZ - this.lastPlayerChunkZ, this.contourRange
		);
		boolean needsContours = config.showTerrainContours && insideContourRange
			&& (updateKind.refreshExisting() || !contours.hasChunk(dimension, chunkX, chunkZ));
		if (!needsTerrain && !needsBiomes && !needsContours) {
			return false;
		}
		LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
		if (chunk == null) {
			return false;
		}
		long startedAt = System.nanoTime();
		if (needsBiomes) {
			atlas.putBiomeChunk(dimension, chunkX, chunkZ, this.sampleBiomeChunk(level, chunk, chunkX, chunkZ));
		}
		if (needsTerrain || needsContours) {
			SurfaceChunkSamples samples = this.sampleSurfaceChunk(level, chunk, chunkX, chunkZ);
			if (needsContours) {
				contours.putChunk(dimension, chunkX, chunkZ, samples.heights(), samples.kinds());
			}
			if (needsTerrain) {
				atlas.putTerrainChunk(dimension, chunkX, chunkZ, samples.colors());
			}
		}
		this.recordUpdate(new ChunkUpdate(
			dimension, chunkX, chunkZ, updateKind, System.nanoTime() - startedAt, level.getGameTime()
		));
		return true;
	}

	private void recordUpdate(ChunkUpdate update) {
		this.recentUpdates.removeIf(previous -> previous.dimension().equals(update.dimension())
			&& previous.chunkX() == update.chunkX() && previous.chunkZ() == update.chunkZ());
		this.recentUpdates.addFirst(update);
		while (this.recentUpdates.size() > DEBUG_HISTORY_SIZE) {
			this.recentUpdates.removeLast();
		}
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

	private SurfaceChunkSamples sampleSurfaceChunk(ClientLevel level, LevelChunk chunk, int chunkX, int chunkZ) {
		byte[] colors = new byte[16 * 16];
		short[] heights = new short[16 * 16];
		byte[] kinds = new byte[16 * 16];
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
				int index = x + z * 16;
				heights[index] = (short)sample.height;
				kinds[index] = sample.waterDepth > 0 ? TerrainContourCache.WATER : TerrainContourCache.LAND;
				if (sample.mapColor != MapColor.NONE) {
					MapColor.Brightness brightness = sample.mapColor == MapColor.WATER
						? waterBrightness(sample.waterDepth, worldX, worldZ)
						: terrainBrightness(sample.height, previousHeight, worldX, worldZ);
					colors[index] = sample.mapColor.getPackedId(brightness);
				}
				previousHeight = sample.height;
			}
		}
		return new SurfaceChunkSamples(colors, heights, kinds);
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
		this.nearbyIndex = 0;
		this.ticksUntilDiscoveryRescan = 0;
		this.ticksUntilPlayerRefresh = 0;
		this.ticksUntilNearbyRefresh = 0;
		this.ticksUntilBackgroundRefresh = 0;
		this.currentFarRing = 0;
		this.lastPlayerChunkX = Integer.MIN_VALUE;
		this.lastPlayerChunkZ = Integer.MIN_VALUE;
		this.contourRange = 0;
		this.recentUpdates.clear();
	}

	private static List<ChunkOffset> radialOffsets(int range) {
		List<ChunkOffset> offsets = new ArrayList<>((range * 2 + 1) * (range * 2 + 1));
		for (int z = -range; z <= range; z++) {
			for (int x = -range; x <= range; x++) {
				if (RecordingArea.containsChunkOffset(x, z, range)) {
					offsets.add(new ChunkOffset(x, z));
				}
			}
		}
		offsets.sort(Comparator.comparingInt(ChunkOffset::distanceSquared)
			.thenComparingInt(ChunkOffset::z)
			.thenComparingInt(ChunkOffset::x));
		return List.copyOf(offsets);
	}

	private record ChunkOffset(int x, int z) {
		private int distanceSquared() {
			return this.x * this.x + this.z * this.z;
		}

		private int ring() {
			return (int)Math.ceil(Math.sqrt(this.distanceSquared()));
		}
	}

	public enum UpdateKind {
		DISCOVERY(false), PLAYER_REFRESH(true), NEARBY_REFRESH(true), BACKGROUND_REFRESH(true);

		private final boolean refreshExisting;

		UpdateKind(boolean refreshExisting) {
			this.refreshExisting = refreshExisting;
		}

		private boolean refreshExisting() {
			return this.refreshExisting;
		}
	}

	public record ChunkUpdate(
		String dimension,
		int chunkX,
		int chunkZ,
		UpdateKind kind,
		long durationNanos,
		long gameTime
	) {
	}

	private static final class SurfaceSample {
		private int height;
		private int waterDepth;
		private MapColor mapColor = MapColor.NONE;
	}

	private record SurfaceChunkSamples(byte[] colors, short[] heights, byte[] kinds) {
	}
}
