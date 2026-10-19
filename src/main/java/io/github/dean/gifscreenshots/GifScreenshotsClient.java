package io.github.dean.gifscreenshots;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GifScreenshotsClient implements ClientModInitializer {
	public static final String MOD_ID = "gif-screenshots";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		KeyMapping key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.gif-screenshots.record", InputConstants.Type.KEYBOARD, InputConstants.KEY_F9, KeyMapping.Category.MISC));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (key.consumeClick()) GifRecorder.start(client);
		});
	}
}
