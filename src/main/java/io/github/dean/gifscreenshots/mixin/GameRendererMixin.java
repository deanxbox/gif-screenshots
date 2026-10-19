package io.github.dean.gifscreenshots.mixin;

import io.github.dean.gifscreenshots.GifRecorder;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
	// Per-frame hook: the 20 Hz client tick is too slow for smooth GIFs.
	@Inject(method = "render", at = @At("TAIL"))
	private void gifscreenshots$frame(CallbackInfo ci) {
		GifRecorder.onFrame();
	}
}
