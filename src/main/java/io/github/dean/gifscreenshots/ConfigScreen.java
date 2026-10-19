package io.github.dean.gifscreenshots;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
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
		int x = width / 2 - 100, y = height / 4;
		addRenderableWidget(new IntSlider(x, y, "gif-screenshots.seconds", 1, Config.MAX_SECONDS, c.seconds, v -> c.seconds = v));
		addRenderableWidget(new IntSlider(x, y + 24, "gif-screenshots.fps", 1, Config.MAX_FPS, c.fps, v -> c.fps = v));
		addRenderableWidget(new IntSlider(x, y + 48, "gif-screenshots.width", Config.MIN_WIDTH, Config.MAX_WIDTH, c.width, v -> c.width = v));
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(x, y + 96, 200, 20).build());
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
