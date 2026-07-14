package dev.cartographer.minimap.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public Corner corner = Corner.TOP_RIGHT;
	public int size = 160;
	public Shape shape = Shape.SQUARE;
	public float opacity = 0.9F;
	public int zoom = 2;
	public boolean showCoordinates = true;
	public boolean showCardinalDirections = true;
	public UnknownTerrain unknownTerrain = UnknownTerrain.DARK;
	public boolean fullscreenEnabled = true;
	public boolean visible = true;
	public boolean showTerrainContours = false;
	public int terrainContourRangeChunks = 8;
	public RecordingMode recordingMode = RecordingMode.MAPS;
	public MapDetailMode mapDetailMode = MapDetailMode.VANILLA_PIXELS;
	public boolean showCursorBiome = true;
	public MapLightingMode mapLightingMode = MapLightingMode.DAY_NIGHT;

	private transient Path path;
	private transient long revision;

	public static ModConfig load(Path path) {
		ModConfig config = null;
		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				config = GSON.fromJson(reader, ModConfig.class);
			} catch (IOException | RuntimeException ignored) {
				// Invalid user configuration falls back to safe defaults and is replaced on the next save.
			}
		}
		if (config == null) {
			config = new ModConfig();
		}
		config.path = path;
		config.sanitize();
		return config;
	}

	public void save() {
		if (this.path == null) {
			return;
		}
		this.sanitize();
		try {
			Files.createDirectories(this.path.getParent());
			try (Writer writer = Files.newBufferedWriter(this.path, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
			this.revision++;
		} catch (IOException exception) {
			throw new IllegalStateException("Could not save Neverket Minimap configuration", exception);
		}
	}

	public long revision() {
		return this.revision;
	}

	public void changed() {
		this.save();
	}

	private void sanitize() {
		if (this.corner == null) this.corner = Corner.TOP_RIGHT;
		if (this.shape == null) this.shape = Shape.SQUARE;
		if (this.unknownTerrain == null) this.unknownTerrain = UnknownTerrain.DARK;
		if (this.recordingMode == null) this.recordingMode = RecordingMode.MAPS;
		if (this.mapDetailMode == null) this.mapDetailMode = MapDetailMode.VANILLA_PIXELS;
		if (this.mapLightingMode == null) this.mapLightingMode = MapLightingMode.DAY_NIGHT;
		this.size = Math.clamp(this.size, 96, 256);
		this.opacity = Math.clamp(this.opacity, 0.25F, 1.0F);
		this.zoom = Math.clamp(this.zoom, 1, 32);
		this.terrainContourRangeChunks = Math.clamp(this.terrainContourRangeChunks, 2, 32);
	}

	public enum Corner {
		TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

		public Corner next() {
			return values()[(this.ordinal() + 1) % values().length];
		}
	}

	public enum Shape {
		SQUARE, CIRCLE;

		public Shape next() {
			return values()[(this.ordinal() + 1) % values().length];
		}
	}

	public enum UnknownTerrain {
		DARK, TRANSPARENT;

		public UnknownTerrain next() {
			return values()[(this.ordinal() + 1) % values().length];
		}
	}

	public enum RecordingMode {
		MAPS, EXPLORED_TERRAIN;

		public RecordingMode next() {
			return values()[(this.ordinal() + 1) % values().length];
		}
	}

	public enum MapDetailMode {
		VANILLA_PIXELS, LOADED_TERRAIN_DETAIL;

		public MapDetailMode next() {
			return values()[(this.ordinal() + 1) % values().length];
		}
	}

	public enum MapLightingMode {
		ALWAYS_BRIGHT, DAY_NIGHT;

		public MapLightingMode next() {
			return values()[(this.ordinal() + 1) % values().length];
		}
	}
}
