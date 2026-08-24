package dev.neverket.minimap.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jspecify.annotations.Nullable;

/** Loader-specific access to the custom GUI render-state hooks exposed by Minecraft 26.2. */
public interface GuiGraphicsAccess {
	void submit(GuiGraphicsExtractor graphics, GuiElementRenderState renderState);

	@Nullable ScreenRectangle peekScissor(GuiGraphicsExtractor graphics);
}
