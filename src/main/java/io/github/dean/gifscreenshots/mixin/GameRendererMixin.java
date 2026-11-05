package io.github.dean.gifscreenshots.mixin;

import io.github.dean.gifscreenshots.GifRecorder;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	// Per-frame hook: the 20 Hz client tick is too slow for smooth GIFs.
	@Inject(method = "render", at = @At("HEAD"))
	private void gifscreenshots$begin(CallbackInfo ci) {
		GifRecorder.beginFrame();
	}

	@Shadow @Final private GuiRenderer guiRenderer;
	@Shadow @Final private net.minecraft.client.renderer.fog.FogRenderer fogRenderer;

	// After the GUI pass: capture the clean frame, then draw the REC badge on top.
	@Inject(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V", shift = At.Shift.AFTER))
	private void gifscreenshots$afterGui(CallbackInfo ci) {
		GifRecorder.captureThenBadge((GameRenderer) (Object) this, guiRenderer, fogRenderer.getBuffer(net.minecraft.client.renderer.fog.FogRenderer.FogMode.NONE));
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void gifscreenshots$frame(CallbackInfo ci) {
		GifRecorder.onFrame();
	}
}
