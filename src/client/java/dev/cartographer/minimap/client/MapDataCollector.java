package dev.cartographer.minimap.client;

import dev.cartographer.minimap.atlas.MapAtlas;
import dev.cartographer.minimap.atlas.MapSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
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
				changed |= atlas.put(new MapSnapshot(
					mapId.id(),
					mapData.dimension.identifier().toString(),
					mapData.centerX,
					mapData.centerZ,
					mapData.scale,
					mapData.colors
				));
			}
		}
		return changed;
	}
}
