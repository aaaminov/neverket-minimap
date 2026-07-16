package dev.cartographer.minimap.client;

import dev.cartographer.minimap.atlas.MapAtlas;
import dev.cartographer.minimap.config.ModConfig;
import dev.cartographer.minimap.marker.BannerMarker;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
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
		List<ProjectedMarker> edgeBanners = new ArrayList<>();
		for (BannerMarker marker : atlas.bannerMarkers(dimension)) {
			ProjectedMarker projected = this.project(
				new MarkerView(false, marker.x(), marker.z(), marker.name(), marker.assetId(), marker.modifiedAt()),
				centerX, centerZ, blocksPerPixel, mapX, mapY, mapWidth, mapHeight, circular
			);
			(projected.onMap() ? visible : edgeBanners).add(projected);
		}
		edgeBanners.sort(Comparator.comparingDouble(marker -> marker.distanceSquared(centerX, centerZ)));
		visible.addAll(edgeBanners.subList(0, Math.min(config.maxEdgeBannerMarkers, edgeBanners.size())));

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
		if (identifier.getNamespace().equals("neverket-minimap") && identifier.getPath().startsWith("custom/")) {
			drawCustomIcon(graphics, identifier.getPath().substring("custom/".length()), x, y);
			return;
		}
		if (identifier.getNamespace().equals("neverket-minimap") && identifier.getPath().startsWith("tinted/")) {
			drawTintedVanillaIcon(graphics, identifier.getPath().substring("tinted/".length()), x, y);
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
			case CYAN_POINT -> "neverket-minimap:tinted/cyan_point";
			case GREEN_POINT -> "neverket-minimap:tinted/green_point";
			case YELLOW_X -> "neverket-minimap:tinted/yellow_x";
			case PURPLE_X -> "neverket-minimap:tinted/purple_x";
			case GOLD_DIAMOND -> "neverket-minimap:custom/gold_diamond";
			case WHITE_STAR -> "neverket-minimap:custom/white_star";
			case ORANGE_FLAG -> "neverket-minimap:custom/orange_flag";
		};
	}

	private void drawTintedVanillaIcon(GuiGraphicsExtractor graphics, String name, int x, int y) {
		boolean cross = name.endsWith("_x");
		Identifier asset = (cross ? MapDecorationTypes.TARGET_X : MapDecorationTypes.TARGET_POINT).value().assetId();
		int tint = switch (name) {
			case "cyan_point" -> 0xFF59DDE3;
			case "green_point" -> 0xFF72D572;
			case "yellow_x" -> 0xFFFFD75A;
			case "purple_x" -> 0xFFC58AF9;
			default -> 0xFFFFFFFF;
		};
		TextureAtlasSprite sprite = this.minecraft.getAtlasManager()
			.getAtlasOrThrow(AtlasIds.MAP_DECORATIONS)
			.getSprite(asset);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x - ICON_HALF, y - ICON_HALF, ICON_SIZE, ICON_SIZE, tint);
	}

	private static void drawCustomIcon(GuiGraphicsExtractor graphics, String name, int centerX, int centerY) {
		PixelIcon icon = switch (name) {
			case "gold_diamond" -> new PixelIcon(0xFFFFCB45, 0xFFFFEE9A, 0xFF6B4B16, new String[] {
				".........", "....D....", "...DCD...", "..DCCCD..", ".DCCACCD.",
				"..DCCCD..", "...DCD...", "....D....", "........."
			});
			case "white_star" -> new PixelIcon(0xFFE8E8E8, 0xFFFFFFFF, 0xFF686868, new String[] {
				".........", "....C....", "....C....", ".CCCCC...", "..CAC....",
				"..CCC....", ".C...C...", ".........", "........."
			});
			case "orange_flag" -> new PixelIcon(0xFFF1873D, 0xFFFFC26D, 0xFF6C3820, new String[] {
				".........", "..DCCCC..", "..DCACC..", "..DCCCC..", "..D.......",
				"..D.......", "..D.......", ".DDD......", "........."
			});
			default -> null;
		};
		if (icon == null) {
			return;
		}
		int startX = centerX - 4;
		int startY = centerY - 4;
		for (int row = 0; row < icon.rows().length; row++) {
			String pixels = icon.rows()[row];
			for (int column = 0; column < pixels.length(); column++) {
				int color = switch (pixels.charAt(column)) {
					case 'D' -> icon.dark();
					case 'C' -> icon.primary();
					case 'A' -> icon.accent();
					default -> 0;
				};
				if (color != 0) {
					graphics.fill(startX + column, startY + row, startX + column + 1, startY + row + 1, color);
				}
			}
		}
	}

	private record PixelIcon(int primary, int accent, int dark, String[] rows) {
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
