package dev.cartographer.minimap.client;

import dev.cartographer.minimap.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public final class MinimapRenderer implements AutoCloseable {
	private static final int TEXTURE_SIZE = 256;
	private static final int MARGIN = 8;

	private final Minecraft minecraft;
	private final WorldSession session;
	private final ModConfig config;
	private final MapViewTexture viewTexture;

	public MinimapRenderer(Minecraft minecraft, WorldSession session, ModConfig config) {
		this.minecraft = minecraft;
		this.session = session;
		this.config = config;
		this.viewTexture = new MapViewTexture(minecraft, Identifier.fromNamespaceAndPath("cartographer-minimap", "hud_view"), TEXTURE_SIZE);
	}

	public void render(GuiGraphicsExtractor graphics) {
		if (!this.config.visible || this.minecraft.player == null || this.minecraft.level == null || !this.session.active()) {
			return;
		}

		int size = this.config.size;
		int x = switch (this.config.corner) {
			case TOP_LEFT, BOTTOM_LEFT -> MARGIN;
			case TOP_RIGHT, BOTTOM_RIGHT -> graphics.guiWidth() - size - MARGIN;
		};
		int y = switch (this.config.corner) {
			case TOP_LEFT, TOP_RIGHT -> MARGIN;
			case BOTTOM_LEFT, BOTTOM_RIGHT -> graphics.guiHeight() - size - MARGIN;
		};

		String dimension = this.minecraft.level.dimension().identifier().toString();
		this.viewTexture.update(
			this.session.atlas(), dimension, this.minecraft.player.getX(), this.minecraft.player.getZ(), this.config.zoom, size,
			this.config.shape == ModConfig.Shape.CIRCLE, this.config.unknownTerrain
		);

		int tint = ((int)(this.config.opacity * 255.0F) << 24) | 0xFFFFFF;
		graphics.blit(RenderPipelines.GUI_TEXTURED, this.viewTexture.id(), x, y, 0, 0, size, size, TEXTURE_SIZE, TEXTURE_SIZE, tint);
		if (this.config.shape == ModConfig.Shape.SQUARE) {
			drawBorder(graphics, x, y, size);
			this.drawMapBoundaries(graphics, dimension, x, y, size);
		}
		drawPlayerArrow(graphics, x + size / 2, y + size / 2, this.minecraft.player.getYRot());

		if (this.config.showCardinalDirections) {
			graphics.centeredText(this.minecraft.font, "N", x + size / 2, y + 3, 0xFFFFFFFF);
			graphics.centeredText(this.minecraft.font, "S", x + size / 2, y + size - 11, 0xFFFFFFFF);
			graphics.text(this.minecraft.font, "W", x + 4, y + size / 2 - 4, 0xFFFFFFFF, true);
			graphics.text(this.minecraft.font, "E", x + size - 10, y + size / 2 - 4, 0xFFFFFFFF, true);
		}
		if (this.config.showCoordinates) {
			String coordinates = (int)Math.floor(this.minecraft.player.getX()) + ", " + (int)Math.floor(this.minecraft.player.getZ());
			graphics.centeredText(this.minecraft.font, coordinates, x + size / 2, y + size - 22, 0xFFFFFFFF);
		}
	}

	static void drawPlayerArrow(GuiGraphicsExtractor graphics, int centerX, int centerY, float yawDegrees) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(centerX, centerY);
		graphics.pose().rotate((float)Math.toRadians(yawDegrees));
		graphics.fill(-1, -7, 2, 4, 0xFFFFFFFF);
		graphics.fill(-3, -6, 4, -3, 0xFFFF4040);
		graphics.pose().popMatrix();
	}

	private static void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int size) {
		graphics.fill(x - 1, y - 1, x + size + 1, y, 0xCCFFFFFF);
		graphics.fill(x - 1, y + size, x + size + 1, y + size + 1, 0xCCFFFFFF);
		graphics.fill(x - 1, y, x, y + size, 0xCCFFFFFF);
		graphics.fill(x + size, y, x + size + 1, y + size, 0xCCFFFFFF);
	}

	private void drawMapBoundaries(GuiGraphicsExtractor graphics, String dimension, int x, int y, int size) {
		double playerX = this.minecraft.player.getX();
		double playerZ = this.minecraft.player.getZ();
		for (var snapshot : this.session.atlas().snapshots()) {
			if (!snapshot.dimension().equals(dimension)) continue;
			int left = (int)Math.round(x + size / 2.0 + (snapshot.minX() - playerX) / this.config.zoom);
			int right = (int)Math.round(x + size / 2.0 + (snapshot.maxXExclusive() - playerX) / this.config.zoom);
			int top = (int)Math.round(y + size / 2.0 + (snapshot.minZ() - playerZ) / this.config.zoom);
			int bottom = (int)Math.round(y + size / 2.0 + (snapshot.maxZExclusive() - playerZ) / this.config.zoom);
			if (right < x || left > x + size || bottom < y || top > y + size) continue;
			left = Math.clamp(left, x, x + size);
			right = Math.clamp(right, x, x + size);
			top = Math.clamp(top, y, y + size);
			bottom = Math.clamp(bottom, y, y + size);
			if (top > y && top < y + size) graphics.horizontalLine(left, right, top, 0x55000000);
			if (bottom > y && bottom < y + size) graphics.horizontalLine(left, right, bottom, 0x55000000);
			if (left > x && left < x + size) graphics.verticalLine(left, top, bottom, 0x55000000);
			if (right > x && right < x + size) graphics.verticalLine(right, top, bottom, 0x55000000);
		}
	}

	@Override
	public void close() {
		this.viewTexture.close();
	}
}
