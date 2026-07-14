package dev.cartographer.minimap.client;

import dev.cartographer.minimap.atlas.MapAtlas;
import dev.cartographer.minimap.atlas.MapCoordinates;
import dev.cartographer.minimap.atlas.MapSnapshot;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Copies only map pixels already delivered to the vanilla client. */
public final class MapDataCollector {
	private int ticksUntilScan;

	public boolean tick(Minecraft minecraft, MapAtlas atlas) {
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
				MapLocation location = this.locate(minecraft, atlas, mapId, mapData);
				if (location == null) {
					continue;
				}
				changed |= atlas.put(new MapSnapshot(
					mapId.id(),
					location.dimension(),
					location.centerX(),
					location.centerZ(),
					mapData.scale,
					mapData.colors
				));
			}
		}
		return changed;
	}

	private MapLocation locate(Minecraft minecraft, MapAtlas atlas, MapId mapId, MapItemSavedData clientData) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		if (server != null) {
			MapItemSavedData serverData = server.overworld().getMapData(mapId);
			if (serverData != null) {
				return new MapLocation(serverData.dimension.identifier().toString(), serverData.centerX, serverData.centerZ);
			}
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

	private record MapLocation(String dimension, int centerX, int centerZ) {
	}
}
