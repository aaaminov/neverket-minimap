package dev.neverket.minimap.fabric.mixin;

import dev.neverket.minimap.fabric.NeverketMinimapFabric;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the minimap before vanilla HUD elements, including the F3 debug overlay. */
@Mixin(Gui.class)
abstract class GuiMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void neverketMinimap$renderBehindHud(
		GuiGraphics graphics,
		DeltaTracker deltaTracker,
		CallbackInfo callbackInfo
	) {
		NeverketMinimapFabric.renderHud(graphics, deltaTracker);
	}
}
