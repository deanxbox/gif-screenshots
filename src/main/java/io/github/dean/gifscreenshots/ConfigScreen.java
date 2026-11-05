package io.github.dean.gifscreenshots;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

public class ConfigScreen extends Screen {
	private final Screen parent;

	public ConfigScreen(Screen parent) {
		super(Component.translatable("gif-screenshots.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		Config c = Config.INSTANCE;
		int x = width / 2 - 100, y = Math.max(8, (height - 176) / 2);
		addRenderableWidget(new IntSlider(x, y, "gif-screenshots.seconds", 1, Config.MAX_SECONDS, c.seconds, v -> c.seconds = v));
		addRenderableWidget(new IntSlider(x, y + 24, "gif-screenshots.fps", 1, Config.MAX_FPS, c.fps, v -> c.fps = v));
		addRenderableWidget(new IntSlider(x, y + 48, "gif-screenshots.width", Config.MIN_WIDTH, Config.MAX_WIDTH, c.width, v -> c.width = v));
		addRenderableWidget(CycleButton.onOffBuilder(c.indicator).create(x, y + 72, 200, 20,
			Component.translatable("gif-screenshots.indicator"), (b, v) -> c.indicator = v));
		addRenderableWidget(CycleButton.builder((Integer v) -> Component.translatable("gif-screenshots.corner." + v), c.corner)
			.withValues(0, 1, 2, 3).create(x, y + 96, 200, 20,
				Component.translatable("gif-screenshots.corner"), (b, v) -> c.corner = v));
		var gpus = Config.gpus();
		var devices = new java.util.ArrayList<Integer>();
		for (int i = 0; i < gpus.size(); i++) devices.add(i);
		devices.add(-1);
		if (!devices.contains(c.device)) c.device = -1;
		addRenderableWidget(CycleButton.builder((Integer v) -> v < 0 ? Component.translatable("gif-screenshots.device.cpu")
				: Component.literal(v + ": " + gpus.get(v).replaceFirst("^NVIDIA ", "").replaceFirst("^GeForce ", "")),
			c.device).withValues(devices).create(x, y + 120, 200, 20,
				Component.translatable("gif-screenshots.device"), (b, v) -> c.device = v));
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x, y + 152, 200, 20).build());
	}

	@Override
	public void onClose() {
		Config.INSTANCE.save();
		minecraft.setScreenAndShow(parent);
	}

	private static class IntSlider extends AbstractSliderButton {
		private final String key;
		private final int min, max;
		private final java.util.function.IntConsumer setter;

		IntSlider(int x, int y, String key, int min, int max, int cur, java.util.function.IntConsumer setter) {
			super(x, y, 200, 20, Component.empty(), (cur - min) / (double) (max - min));
			this.key = key; this.min = min; this.max = max; this.setter = setter;
			updateMessage();
		}

		private int val() { return min + (int) Math.round(value * (max - min)); }

		@Override protected void updateMessage() { setMessage(Component.translatable(key, val())); }
		@Override protected void applyValue() { setter.accept(val()); }
	}
}
