package dev.cartographer.minimap.client;

import dev.cartographer.minimap.atlas.MapAtlas;
import dev.cartographer.minimap.config.ModConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/** Incrementally records block-resolution colors from chunks already loaded by the client. */
public final class TerrainDataCollector {
	private static final int MAX_RANGE_CHUNKS = 32;
	private static final int MIN_BACKGROUND_CHUNKS_PER_TICK = 2;
	private static final int MAX_BACKGROUND_CHUNKS_PER_TICK = 16;
	private static final int MAX_SCAN_ATTEMPTS_PER_TICK = 128;
	private static final long SCAN_TIME_BUDGET_NANOS = 2_000_000L;

	private String dimension;
	private int scanOriginX;
	private int scanOriginZ;
	private int scanIndex;
	private int scanRange = -1;
	private List<ChunkOffset> scanOffsets = List.of();

	public void tick(Minecraft minecraft, MapAtlas atlas, ModConfig config) {
		if (minecraft.player == null || minecraft.level == null) {
			this.reset();
			return;
		}
		boolean recordAllExplored = config.recordingMode == ModConfig.RecordingMode.EXPLORED_TERRAIN;
		boolean addDetailToMaps = config.recordingMode == ModConfig.RecordingMode.MAPS
			&& config.mapDetailMode == ModConfig.MapDetailMode.LOADED_TERRAIN_DETAIL;
		if (!recordAllExplored && !addDetailToMaps) {
			return;
		}

		ClientLevel level = minecraft.level;
		String currentDimension = level.dimension().identifier().toString();
		int playerChunkX = Math.floorDiv((int)Math.floor(minecraft.player.getX()), 16);
		int playerChunkZ = Math.floorDiv((int)Math.floor(minecraft.player.getZ()), 16);
		int range = Math.min(minecraft.options.getEffectiveRenderDistance(), MAX_RANGE_CHUNKS);
		if (range != this.scanRange) {
			this.scanRange = range;
			this.scanOffsets = radialOffsets(range);
		}
		if (!currentDimension.equals(this.dimension) || this.scanIndex >= this.scanOffsets.size()
			|| Math.abs(playerChunkX - this.scanOriginX) > 2
			|| Math.abs(playerChunkZ - this.scanOriginZ) > 2) {
			this.dimension = currentDimension;
			this.scanOriginX = playerChunkX;
			this.scanOriginZ = playerChunkZ;
			this.scanIndex = 0;
		}

		this.tryRecord(level, atlas, config, currentDimension, playerChunkX, playerChunkZ);
		long scanDeadline = System.nanoTime() + SCAN_TIME_BUDGET_NANOS;
		for (int recorded = 0, attempts = 0;
			recorded < MAX_BACKGROUND_CHUNKS_PER_TICK
				&& attempts < MAX_SCAN_ATTEMPTS_PER_TICK
				&& this.scanIndex < this.scanOffsets.size()
				&& (recorded < MIN_BACKGROUND_CHUNKS_PER_TICK || System.nanoTime() < scanDeadline);
			this.scanIndex++, attempts++) {
			ChunkOffset offset = this.scanOffsets.get(this.scanIndex);
			int chunkX = this.scanOriginX + offset.x();
			int chunkZ = this.scanOriginZ + offset.z();
			if (this.tryRecord(level, atlas, config, currentDimension, chunkX, chunkZ)) {
				recorded++;
			}
		}
	}

	private boolean tryRecord(
		ClientLevel level,
		MapAtlas atlas,
		ModConfig config,
		String dimension,
		int chunkX,
		int chunkZ
	) {
		if (atlas.hasTerrainChunk(dimension, chunkX, chunkZ)) {
			return false;
		}
		if (config.recordingMode == ModConfig.RecordingMode.MAPS && !atlas.hasMapCoverage(dimension, chunkX, chunkZ)) {
			return false;
		}
		LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
		if (chunk == null) {
			return false;
		}
		atlas.putTerrainChunk(dimension, chunkX, chunkZ, this.sampleChunk(chunk, chunkX, chunkZ));
		return true;
	}

	private byte[] sampleChunk(LevelChunk chunk, int chunkX, int chunkZ) {
		byte[] colors = new byte[16 * 16];
		BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
		for (int z = 0; z < 16; z++) {
			int previousHeight = Integer.MIN_VALUE;
			for (int x = 0; x < 16; x++) {
				int worldX = chunkX * 16 + x;
				int worldZ = chunkZ * 16 + z;
				int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
				MapColor mapColor = MapColor.NONE;
				while (y >= chunk.getMinY() && mapColor == MapColor.NONE) {
					position.set(worldX, y, worldZ);
					BlockState state = chunk.getBlockState(position);
					mapColor = state.getMapColor(chunk, position);
					y--;
				}
				int surfaceHeight = y + 1;
				MapColor.Brightness brightness = MapColor.Brightness.NORMAL;
				if (previousHeight != Integer.MIN_VALUE) {
					if (surfaceHeight > previousHeight + 1) {
						brightness = MapColor.Brightness.HIGH;
					} else if (surfaceHeight < previousHeight - 1) {
						brightness = MapColor.Brightness.LOW;
					}
				}
				previousHeight = surfaceHeight;
				if (mapColor != MapColor.NONE) {
					colors[x + z * 16] = mapColor.getPackedId(brightness);
				}
			}
		}
		return colors;
	}

	private void reset() {
		this.dimension = null;
		this.scanIndex = 0;
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
}
