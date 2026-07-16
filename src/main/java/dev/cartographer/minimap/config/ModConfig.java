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

	public Corner corner = Corner.TOP_LEFT;
	public int size = 160;
	public Shape shape = Shape.SQUARE;
	public float opacity = 0.9F;
	public int zoom = 2;
	public boolean showCoordinates = true;
	public boolean showCardinalDirections = true;
	public boolean showMinimapBorder = true;
	public MinimapBorderColor minimapBorderColor = MinimapBorderColor.WHITE;
	public UnknownTerrain unknownTerrain = UnknownTerrain.DARK;
	public boolean fullscreenEnabled = true;
	public boolean pauseOnFullscreenMap = true;
	public boolean visible = true;
	public boolean showTerrainContours = false;
	public int terrainContourRangeChunks = 8;
	public RecordingMode recordingMode = RecordingMode.MAPS;
	public MapDetailMode mapDetailMode = MapDetailMode.VANILLA_PIXELS;
	public boolean showCursorBiome = true;
	public MapLightingMode mapLightingMode = MapLightingMode.DAY_NIGHT;
	public float nightDarkness = 0.5F;
	public QuickMarkerIcon quickMarkerIcon = QuickMarkerIcon.TARGET_POINT;
	public int maxEdgeBannerMarkers = 5;
	public BiomeHighlightColor biomeHighlightColor = BiomeHighlightColor.CYAN;
	public float biomeHighlightOpacity = 0.35F;

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
		if (this.corner == null) this.corner = Corner.TOP_LEFT;
		if (this.shape == null) this.shape = Shape.SQUARE;
		if (this.unknownTerrain == null) this.unknownTerrain = UnknownTerrain.DARK;
		if (this.recordingMode == null) this.recordingMode = RecordingMode.MAPS;
		if (this.mapDetailMode == null) this.mapDetailMode = MapDetailMode.VANILLA_PIXELS;
		if (this.mapLightingMode == null) this.mapLightingMode = MapLightingMode.DAY_NIGHT;
		if (this.quickMarkerIcon == null) this.quickMarkerIcon = QuickMarkerIcon.TARGET_POINT;
		if (this.minimapBorderColor == null) this.minimapBorderColor = MinimapBorderColor.WHITE;
		if (this.biomeHighlightColor == null) this.biomeHighlightColor = BiomeHighlightColor.CYAN;
		this.size = Math.clamp(this.size, 96, 256);
		this.opacity = Math.clamp(this.opacity, 0.25F, 1.0F);
		this.nightDarkness = !Float.isFinite(this.nightDarkness)
			? 0.5F
			: Math.round(Math.clamp(this.nightDarkness, 0.0F, 1.0F) * 20.0F) / 20.0F;
		this.biomeHighlightOpacity = !Float.isFinite(this.biomeHighlightOpacity)
			? 0.35F
			: Math.round(Math.clamp(this.biomeHighlightOpacity, 0.05F, 1.0F) * 20.0F) / 20.0F;
		this.zoom = Math.clamp(this.zoom, 1, 32);
		this.terrainContourRangeChunks = Math.clamp(this.terrainContourRangeChunks, 2, 32);
		this.maxEdgeBannerMarkers = Math.clamp(this.maxEdgeBannerMarkers, 0, 32);
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

	public enum QuickMarkerIcon {
		TARGET_POINT, TARGET_X, RED_MARKER, BLUE_MARKER, RED_X,
		CYAN_POINT, GREEN_POINT, YELLOW_POINT, PURPLE_POINT, WHITE_POINT,
		GREEN_MARKER, YELLOW_MARKER, PURPLE_MARKER, ORANGE_MARKER,
		CYAN_X, GREEN_X, YELLOW_X, PURPLE_X, ORANGE_X;

		public QuickMarkerIcon next() {
			return values()[(this.ordinal() + 1) % values().length];
		}
	}

	public enum MinimapBorderColor {
		WHITE(0xFFFFFF), GRAY(0xA0A0A0), BLACK(0x202020), GOLD(0xFFD45A), RED(0xE45A5A),
		GREEN(0x62C46B), BLUE(0x62A8E5), PURPLE(0xB477E8);

		private final int rgb;

		MinimapBorderColor(int rgb) {
			this.rgb = rgb;
		}

		public int argb() {
			return 0xCC000000 | this.rgb;
		}

		public MinimapBorderColor next() {
			return values()[(this.ordinal() + 1) % values().length];
		}
	}

	public enum BiomeHighlightColor {
		YELLOW(0xFFE066), CYAN(0x55DDE0), GREEN(0x68D391), MAGENTA(0xE879F9), ORANGE(0xFB923C), WHITE(0xFFFFFF);

		private final int rgb;

		BiomeHighlightColor(int rgb) {
			this.rgb = rgb;
		}

		public int rgb() {
			return this.rgb;
		}

		public BiomeHighlightColor next() {
			return values()[(this.ordinal() + 1) % values().length];
		}
	}
}
