package dev.neverket.minimap.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.neverket.minimap.atlas.MapAtlas;
import dev.neverket.minimap.config.ModConfig;
import dev.neverket.minimap.marker.BannerMarker;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;

/** Shared projection, rendering and tooltip behavior for minimap markers. */
public final class MapMarkerRenderer {
	private static final int ICON_SIZE = 10;
	private static final int ICON_HALF = ICON_SIZE / 2;
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
	private static final Map<String, Identifier> RECOLORED_TEXTURES = new HashMap<>();

	private final Minecraft minecraft;

	public MapMarkerRenderer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	public MarkerHit render(
		GuiGraphicsExtractor graphics,
		MapAtlas atlas,
		ModConfig config,
		String dimension,
		double centerX,
		double centerZ,
		double blocksPerPixel,
		int mapX,
		int mapY,
		int mapWidth,
		int mapHeight,
		boolean circular,
		int mouseX,
		int mouseY,
		boolean tooltips
	) {
		List<ProjectedMarker> visible = new ArrayList<>();
		PriorityQueue<ProjectedMarker> edgeBanners = new PriorityQueue<>(
			Comparator.comparingDouble((ProjectedMarker marker) -> marker.distanceSquared(centerX, centerZ)).reversed()
		);
		for (BannerMarker marker : atlas.bannerMarkers(dimension)) {
			ProjectedMarker projected = this.project(
				new MarkerView(false, marker.x(), marker.z(), marker.name(), marker.assetId(), marker.modifiedAt()),
				centerX, centerZ, blocksPerPixel, mapX, mapY, mapWidth, mapHeight, circular
			);
			if (projected.onMap()) {
				visible.add(projected);
			} else if (config.maxEdgeBannerMarkers > 0) {
				edgeBanners.add(projected);
				if (edgeBanners.size() > config.maxEdgeBannerMarkers) {
					edgeBanners.poll();
				}
			}
		}
		visible.addAll(edgeBanners);

		atlas.quickMarker()
			.filter(marker -> marker.dimension().equals(dimension))
			.map(marker -> this.project(
				new MarkerView(true, marker.x(), marker.z(), "", quickMarkerAsset(config.quickMarkerIcon), marker.modifiedAt()),
				centerX, centerZ, blocksPerPixel, mapX, mapY, mapWidth, mapHeight, circular
			))
			.ifPresent(visible::add);

		MarkerHit hovered = null;
		for (ProjectedMarker marker : visible) {
			this.drawIcon(graphics, marker.view().assetId(), marker.screenX(), marker.screenY());
			if (Math.abs(mouseX - marker.screenX()) <= ICON_HALF + 2 && Math.abs(mouseY - marker.screenY()) <= ICON_HALF + 2) {
				hovered = marker.hit();
			}
		}
		if (tooltips && hovered != null) {
			graphics.setComponentTooltipForNextFrame(this.minecraft.font, tooltip(hovered), mouseX, mouseY);
		}
		return hovered;
	}

	private ProjectedMarker project(
		MarkerView marker,
		double centerX,
		double centerZ,
		double blocksPerPixel,
		int mapX,
		int mapY,
		int mapWidth,
		int mapHeight,
		boolean circular
	) {
		double dx = (marker.x() - centerX) / blocksPerPixel;
		double dz = (marker.z() - centerZ) / blocksPerPixel;
		double halfWidth = mapWidth / 2.0 - 1;
		double halfHeight = mapHeight / 2.0 - 1;
		double screenCenterX = mapX + mapWidth / 2.0;
		double screenCenterY = mapY + mapHeight / 2.0;
		boolean onMap;
		if (circular) {
			double radius = Math.min(halfWidth, halfHeight);
			double distance = Math.hypot(dx, dz);
			onMap = distance <= radius;
			if (!onMap && distance > 0.0) {
				double factor = radius / distance;
				dx *= factor;
				dz *= factor;
			}
		} else {
			onMap = Math.abs(dx) <= halfWidth && Math.abs(dz) <= halfHeight;
			if (!onMap) {
				dx = Math.clamp(dx, -halfWidth, halfWidth);
				dz = Math.clamp(dz, -halfHeight, halfHeight);
			}
		}
		return new ProjectedMarker(
			marker,
			(int)Math.round(screenCenterX + dx),
			(int)Math.round(screenCenterY + dz),
			onMap
		);
	}

	private void drawIcon(GuiGraphicsExtractor graphics, String assetId, int x, int y) {
		Identifier identifier;
		try {
			identifier = Identifier.parse(assetId);
		} catch (RuntimeException ignored) {
			identifier = MapDecorationTypes.TARGET_POINT.value().assetId();
		}
		if (identifier.getNamespace().equals("neverket-minimap") && identifier.getPath().startsWith("recolored/")) {
			this.drawRecoloredVanillaIcon(graphics, identifier.getPath().substring("recolored/".length()), x, y);
			return;
		}
		TextureAtlasSprite sprite = this.minecraft.getAtlasManager()
			.getAtlasOrThrow(AtlasIds.MAP_DECORATIONS)
			.getSprite(identifier);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x - ICON_HALF, y - ICON_HALF, ICON_SIZE, ICON_SIZE);
	}

	public void drawQuickIcon(GuiGraphicsExtractor graphics, ModConfig.QuickMarkerIcon icon, int x, int y) {
		this.drawIcon(graphics, quickMarkerAsset(icon), x, y);
	}

	private static List<Component> tooltip(MarkerHit marker) {
		return List.of(
			displayName(marker.quick(), marker.name()),
			coordinates(marker.x(), marker.z()),
			modified(marker.modifiedAt())
		);
	}

	public static Component displayName(boolean quick, String name) {
		if (name != null && !name.isBlank()) {
			return Component.literal(name);
		}
		return Component.translatable(quick
			? "marker.neverket-minimap.quick.default_name"
			: "marker.neverket-minimap.banner.default_name");
	}

	public static Component coordinates(int x, int z) {
		return Component.translatable("marker.neverket-minimap.coordinates", x, z);
	}

	public static Component modified(long modifiedAt) {
		String time = TIME_FORMAT.format(Instant.ofEpochMilli(modifiedAt).atZone(ZoneId.systemDefault()));
		return Component.translatable("marker.neverket-minimap.modified", time);
	}

	public static String quickMarkerAsset(ModConfig.QuickMarkerIcon icon) {
		return switch (icon) {
			case TARGET_POINT -> MapDecorationTypes.TARGET_POINT.value().assetId().toString();
			case TARGET_X -> MapDecorationTypes.TARGET_X.value().assetId().toString();
			case RED_MARKER -> MapDecorationTypes.RED_MARKER.value().assetId().toString();
			case BLUE_MARKER -> MapDecorationTypes.BLUE_MARKER.value().assetId().toString();
			case RED_X -> MapDecorationTypes.RED_X.value().assetId().toString();
			case CYAN_POINT -> "neverket-minimap:recolored/point/cyan";
			case GREEN_POINT -> "neverket-minimap:recolored/point/green";
			case YELLOW_POINT -> "neverket-minimap:recolored/point/yellow";
			case PURPLE_POINT -> "neverket-minimap:recolored/point/purple";
			case WHITE_POINT -> "neverket-minimap:recolored/point/white";
			case GREEN_MARKER -> "neverket-minimap:recolored/marker/green";
			case YELLOW_MARKER -> "neverket-minimap:recolored/marker/yellow";
			case PURPLE_MARKER -> "neverket-minimap:recolored/marker/purple";
			case ORANGE_MARKER -> "neverket-minimap:recolored/marker/orange";
			case CYAN_X -> "neverket-minimap:recolored/cross/cyan";
			case GREEN_X -> "neverket-minimap:recolored/cross/green";
			case YELLOW_X -> "neverket-minimap:recolored/cross/yellow";
			case PURPLE_X -> "neverket-minimap:recolored/cross/purple";
			case ORANGE_X -> "neverket-minimap:recolored/cross/orange";
		};
	}

	private void drawRecoloredVanillaIcon(GuiGraphicsExtractor graphics, String specification, int centerX, int centerY) {
		Identifier texture = RECOLORED_TEXTURES.get(specification);
		if (texture == null) {
			texture = this.createRecoloredTexture(specification);
			if (texture != null) {
				RECOLORED_TEXTURES.put(specification, texture);
			}
		}
		if (texture != null) {
			graphics.blit(
				RenderPipelines.GUI_TEXTURED, texture, centerX - ICON_HALF, centerY - ICON_HALF,
				0.0F, 0.0F, ICON_SIZE, ICON_SIZE, 8, 8, 8, 8
			);
			return;
		}
		this.drawIcon(graphics, fallbackAsset(specification), centerX, centerY);
	}

	private Identifier createRecoloredTexture(String specification) {
		String[] parts = specification.split("/", 2);
		if (parts.length != 2) {
			return null;
		}
		String source = switch (parts[0]) {
			case "point" -> "target_point";
			case "marker" -> "red_marker";
			case "cross" -> "red_x";
			default -> null;
		};
		MarkerPalette palette = switch (parts[1]) {
			case "cyan" -> new MarkerPalette(0xFF249098, 0xFF43C9D0, 0xFF8AF1F3);
			case "green" -> new MarkerPalette(0xFF397D3D, 0xFF62B967, 0xFFA3E59A);
			case "yellow" -> new MarkerPalette(0xFF9C761E, 0xFFE0B533, 0xFFFFE783);
			case "purple" -> new MarkerPalette(0xFF704394, 0xFFA968D0, 0xFFDBA7F5);
			case "orange" -> new MarkerPalette(0xFF9A4D20, 0xFFE17635, 0xFFFFB064);
			case "white" -> new MarkerPalette(0xFF929292, 0xFFD0D0D0, 0xFFFFFFFF);
			default -> null;
		};
		if (source == null || palette == null) {
			return null;
		}

		Identifier sourceTexture = Identifier.withDefaultNamespace("textures/map/decorations/" + source + ".png");
		try (InputStream input = this.minecraft.getResourceManager().open(sourceTexture)) {
			NativeImage image = NativeImage.read(input);
			if (image.getWidth() != 8 || image.getHeight() != 8) {
				image.close();
				return null;
			}
			for (int y = 0; y < image.getHeight(); y++) {
				for (int x = 0; x < image.getWidth(); x++) {
					image.setPixel(x, y, recolorPixel(image.getPixel(x, y), source, palette));
				}
			}
			Identifier texture = Identifier.fromNamespaceAndPath("neverket-minimap", "generated/marker/" + specification);
			this.minecraft.getTextureManager().register(texture, new DynamicTexture(texture::toString, image));
			return texture;
		} catch (IOException | RuntimeException ignored) {
			return null;
		}
	}

	private static int recolorPixel(int pixel, String source, MarkerPalette palette) {
		int alpha = pixel >>> 24;
		int rgb = pixel & 0xFFFFFF;
		if (alpha == 0 || rgb == 0) {
			return pixel;
		}
		int replacement = switch (source) {
			case "target_point", "red_marker" -> switch (rgb) {
				case 0xBC1812 -> palette.dark();
				case 0xE01D16 -> palette.primary();
				case 0xFF2119 -> palette.highlight();
				default -> pixel;
			};
			case "red_x" -> switch (rgb) {
				case 0x99130E -> palette.dark();
				case 0xC41D17 -> palette.primary();
				case 0xFF1109 -> palette.highlight();
				default -> pixel;
			};
			default -> pixel;
		};
		return replacement == pixel ? pixel : alpha << 24 | replacement & 0xFFFFFF;
	}

	private static String fallbackAsset(String specification) {
		if (specification.startsWith("marker/")) {
			return MapDecorationTypes.RED_MARKER.value().assetId().toString();
		}
		if (specification.startsWith("cross/")) {
			return MapDecorationTypes.RED_X.value().assetId().toString();
		}
		return MapDecorationTypes.TARGET_POINT.value().assetId().toString();
	}

	private record MarkerPalette(int dark, int primary, int highlight) {
	}

	private record MarkerView(boolean quick, int x, int z, String name, String assetId, long modifiedAt) {
	}

	private record ProjectedMarker(MarkerView view, int screenX, int screenY, boolean onMap) {
		private double distanceSquared(double centerX, double centerZ) {
			double dx = this.view.x() - centerX;
			double dz = this.view.z() - centerZ;
			return dx * dx + dz * dz;
		}

		private MarkerHit hit() {
			return new MarkerHit(
				this.view.quick(), this.view.x(), this.view.z(), this.view.name(), this.view.modifiedAt(), this.onMap
			);
		}
	}

	public record MarkerHit(boolean quick, int x, int z, String name, long modifiedAt, boolean onMap) {
	}
}
