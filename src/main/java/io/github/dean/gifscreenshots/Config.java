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
	public int width = 1280;
	/** ffmpeg executable (full path or name on PATH). */
	public String ffmpeg = "ffmpeg";
	/** Encoding device: index of an NVIDIA GPU (NVENC), or -1 for CPU (libx264). Falls back to CPU if that GPU is absent. */
	public int device = 0;

	private static java.util.List<String> gpus;

	/** NVIDIA GPU names in PCI-bus order, matching the encoder's CUDA_DEVICE_ORDER. */
	public static synchronized java.util.List<String> gpus() {
		if (gpus == null) {
			gpus = java.util.List.of();
			Process p = null;
			try {
				p = new ProcessBuilder("nvidia-smi", "--query-gpu=pci.bus_id,name", "--format=csv,noheader")
					.redirectError(ProcessBuilder.Redirect.DISCARD).start();
				if (p.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0) {
					try (var r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
						gpus = r.lines().filter(l -> l.contains(",")).sorted()
							.map(l -> l.substring(l.indexOf(',') + 1).trim()).toList();
					}
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (java.io.IOException ignored) { /* no NVIDIA driver: CPU only */ }
			finally { if (p != null && p.isAlive()) p.destroyForcibly(); }
		}
		return gpus;
	}
	/** Red REC badge while recording; never part of the GIF. */
	public boolean indicator = true;
	/** 0 top-left, 1 top-right, 2 bottom-left, 3 bottom-right. */
	public int corner = 1;

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
		corner = Math.clamp(corner, 0, 3);
		device = Math.max(-1, device);
	}

	public void save() {
		clamp();
		try { Files.writeString(FILE, GSON.toJson(this)); }
		catch (Exception e) { GifScreenshotsClient.LOGGER.warn("Could not save config", e); }
	}
}
