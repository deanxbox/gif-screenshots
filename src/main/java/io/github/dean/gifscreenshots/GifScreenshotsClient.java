package io.github.dean.gifscreenshots;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GifScreenshotsClient implements ClientModInitializer {
	public static final String MOD_ID = "gif-screenshots";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		KeyMapping key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.gif-screenshots.record", InputConstants.Type.KEYSYM, InputConstants.KEY_F9, KeyMapping.Category.MISC));
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
			Identifier.fromNamespaceAndPath(MOD_ID, "rec"), (g, tick) -> {
				if (GifRecorder.showIndicator()) draw(g);
			});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (key.consumeClick()) GifRecorder.start(client);
		});
	}

	public static void draw(GuiGraphicsExtractor g) {
				Minecraft mc = Minecraft.getInstance();
				String t = "REC";
				int w = 10 + mc.font.width(t), h = 9, m = 6;
				int c = Config.INSTANCE.corner;
				int x = (c & 1) == 0 ? m : g.guiWidth() - w - m;
				int y = c < 2 ? m : g.guiHeight() - h - m;
				g.fill(x, y + 1, x + 7, y + 8, 0xFFE02020);
				g.text(mc.font, t, x + 10, y, 0xFFE02020);
	}
}
