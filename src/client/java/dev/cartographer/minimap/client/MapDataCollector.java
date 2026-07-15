package dev.cartographer.minimap.client;

import dev.cartographer.minimap.atlas.MapAtlas;
import dev.cartographer.minimap.atlas.MapCoordinates;
import dev.cartographer.minimap.atlas.MapSnapshot;
import dev.cartographer.minimap.marker.BannerMarker;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapBanner;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Copies map pixels and banner decorations already delivered to the vanilla client. */
public final class MapDataCollector {
	private int ticksUntilScan;

	public boolean tick(Minecraft minecraft, MapAtlas atlas, boolean collectMapPixels) {
		if (minecraft.player == null || minecraft.level == null) {
			return false;
		}
		if (this.ticksUntilScan-- > 0) {
			return false;
		}
		this.ticksUntilScan = 9;

		boolean changed = false;
		for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = minecraft.player.getInventory().getItem(slot);
			MapId mapId = stack.get(DataComponents.MAP_ID);
			if (mapId == null) {
				continue;
			}

			MapItemSavedData mapData = minecraft.level.getMapData(mapId);
			if (mapData != null) {
				MapItemSavedData serverData = this.serverData(minecraft, mapId);
				MapLocation location = this.locate(minecraft, atlas, mapId, mapData, serverData);
				if (location == null) {
					continue;
				}
				if (collectMapPixels) {
					changed |= atlas.put(new MapSnapshot(
						mapId.id(),
						location.dimension(),
						location.centerX(),
						location.centerZ(),
						mapData.scale,
						mapData.colors
					));
				}
				changed |= atlas.replaceBannerMarkers(
					mapId.id(), this.bannerMarkers(mapId.id(), location, mapData, serverData)
				);
			}
		}
		return changed;
	}

	private MapLocation locate(
		Minecraft minecraft,
		MapAtlas atlas,
		MapId mapId,
		MapItemSavedData clientData,
		MapItemSavedData serverData
	) {
		if (serverData != null) {
			return new MapLocation(serverData.dimension.identifier().toString(), serverData.centerX, serverData.centerZ);
		}

		MapSnapshot known = atlas.findById(mapId.id()).orElse(null);
		if (known != null && known.scale() == clientData.scale) {
			return new MapLocation(known.dimension(), known.centerX(), known.centerZ());
		}

		List<MapDecoration> playerMarkers = new ArrayList<>();
		for (MapDecoration decoration : clientData.getDecorations()) {
			if (decoration.type().value() == MapDecorationTypes.PLAYER.value()) {
				playerMarkers.add(decoration);
			}
		}
		if (playerMarkers.size() != 1) {
			return null;
		}

		MapDecoration marker = playerMarkers.getFirst();
		return new MapLocation(
			minecraft.level.dimension().identifier().toString(),
			MapCoordinates.centerFromPlayerMarker(minecraft.player.getX(), marker.x(), clientData.scale),
			MapCoordinates.centerFromPlayerMarker(minecraft.player.getZ(), marker.y(), clientData.scale)
		);
	}

	private MapItemSavedData serverData(Minecraft minecraft, MapId mapId) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		return server == null ? null : server.overworld().getMapData(mapId);
	}

	private List<BannerMarker> bannerMarkers(
		int mapId,
		MapLocation location,
		MapItemSavedData clientData,
		MapItemSavedData serverData
	) {
		MapItemSavedData exactData = serverData != null ? serverData : clientData;
		List<BannerMarker> markers = new ArrayList<>();
		long observedAt = System.currentTimeMillis();
		for (MapBanner banner : exactData.getBanners()) {
			markers.add(new BannerMarker(
				mapId,
				location.dimension(),
				banner.pos().getX(),
				banner.pos().getZ(),
				banner.name().map(component -> component.getString()).orElse(""),
				banner.getDecoration().value().assetId().toString(),
				observedAt
			));
		}
		if (!markers.isEmpty() || serverData != null) {
			return markers;
		}

		double blocksPerDecorationUnit = (1 << clientData.scale) / 2.0;
		for (MapDecoration decoration : clientData.getDecorations()) {
			String assetId = decoration.type().value().assetId().toString();
			if (!decoration.type().value().assetId().getPath().endsWith("_banner")) {
				continue;
			}
			markers.add(new BannerMarker(
				mapId,
				location.dimension(),
				(int)Math.round(location.centerX() + decoration.x() * blocksPerDecorationUnit),
				(int)Math.round(location.centerZ() + decoration.y() * blocksPerDecorationUnit),
				decoration.name().map(component -> component.getString()).orElse(""),
				assetId,
				observedAt
			));
		}
		return markers;
	}

	private record MapLocation(String dimension, int centerX, int centerZ) {
	}
}
