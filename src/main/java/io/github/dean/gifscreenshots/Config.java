package io.github.dean.gifscreenshots;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public class Config {
	public static final int MAX_SECONDS = 30, MAX_FPS = 30, MIN_WIDTH = 160, MAX_WIDTH = 1920;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("gif-screenshots.json");
	public static Config INSTANCE = load();

	public int seconds = 5;
	public int fps = 15;
	/** Output width in px; height follows the window aspect ratio. */
	public int width = 640;

	private static Config load() {
		try {
			Config c = GSON.fromJson(Files.readString(FILE), Config.class);
			if (c != null) { c.clamp(); return c; }
		} catch (Exception ignored) { /* missing or corrupt: use defaults */ }
		return new Config();
	}

	public void clamp() {
		seconds = Math.clamp(seconds, 1, MAX_SECONDS);
		fps = Math.clamp(fps, 1, MAX_FPS);
		width = Math.clamp(width, MIN_WIDTH, MAX_WIDTH);
	}

	public void save() {
		clamp();
		try { Files.writeString(FILE, GSON.toJson(this)); }
		catch (Exception e) { GifScreenshotsClient.LOGGER.warn("Could not save config", e); }
	}
}
