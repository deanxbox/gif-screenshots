package io.github.dean.gifscreenshots;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.lwjgl.system.MemoryUtil;

/**
 * Render thread only copies raw pixels. A worker pipes them to ffmpeg, which records a near-lossless
 * temp video; that is then converted to a GIF with a proper palette, off the game's threads.
 */
public final class GifRecorder {
	private record Frame(byte[] rgba, long ns) {}
	private static final Frame END = new Frame(null, 0);
	private static final int POOL = 12; // raw frames in flight; buffers are reused so the GC never sees 8 MB per frame
	private static volatile boolean recording;
	private static boolean capture; // latched at frame start so the badge is skipped on captured frames
	private static long nextFrameNs, endNs, intervalNs;
	private static BlockingQueue<Frame> queue;
	private static BlockingQueue<byte[]> free;
	private static int srcW, srcH;

	private GifRecorder() {}

	public static void start(Minecraft mc) {
		if (recording) return;
		Config c = Config.INSTANCE;
		if (mc.getWindow().getWidth() <= 0 || mc.getWindow().getHeight() <= 0) return;
		intervalNs = 1_000_000_000L / c.fps;
		srcW = mc.getWindow().getWidth();
		srcH = mc.getWindow().getHeight();
		queue = new ArrayBlockingQueue<>(POOL + 1);
		free = new ArrayBlockingQueue<>(POOL);
		for (int i = 0; i < POOL; i++) free.add(new byte[0]); // sized lazily on first use
		int sw = srcW, sh = srcH;
		BlockingQueue<byte[]> fr = free;
		File dir = new File(mc.gameDirectory, Screenshot.SCREENSHOT_DIR);
		dir.mkdirs();
		File gif = new File(dir, "gif_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")) + ".gif");
		BlockingQueue<Frame> q = queue;
		long interval = intervalNs;
		Thread t = new Thread(() -> encode(mc, q, fr, sw, sh, gif, c.fps, c.width, c.ffmpeg, c.device, interval), "GIF-Encoder");
		t.setDaemon(true);
		t.start();
		nextFrameNs = System.nanoTime();
		endNs = nextFrameNs + c.seconds * 1_000_000_000L;
		recording = true;
		message(mc, Component.translatable("gif-screenshots.recording"));
	}

	/** Render thread, start of each frame: decide now whether this frame is captured. */
	public static void beginFrame() {
		long now = System.nanoTime();
		capture = recording && now < endNs && now >= nextFrameNs;
	}

	/** True when the REC badge should be drawn (never on a frame that ends up in the GIF). */
	public static boolean showIndicator() {
		return false; // drawn in captureThenBadge every frame, above other overlays (e.g. AFKCinematics letterbox bars)
	}

	/** Render thread, after the GUI pass: capture, then draw the badge in a second pass so it shows on screen but not in the GIF. */
	public static void captureThenBadge(net.minecraft.client.renderer.GameRenderer gr, net.minecraft.client.gui.render.GuiRenderer renderer) {
		
		onFrame();
		if (!recording || !Config.INSTANCE.indicator) return;
		Minecraft mc = Minecraft.getInstance();
		var state = gr.gameRenderState().guiRenderState;
		GifScreenshotsClient.draw(new net.minecraft.client.gui.GuiGraphicsExtractor(mc, state,
			mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight()));
		renderer.render();
	}

	/** Render thread, end of each frame. */
	public static void onFrame() {
		if (!recording) return;
		long now = System.nanoTime();
		if (now >= endNs) { recording = false; put(END); return; }
		if (!capture) return;
		capture = false;
		now = System.nanoTime();
		nextFrameNs += intervalNs;
		if (nextFrameNs < now) nextFrameNs = now + intervalNs; // fell behind: skip, don't burst
		BlockingQueue<Frame> q = queue;
		long stamp = now;
		BlockingQueue<byte[]> fr = free;
		int size = srcW * srcH * 4;
		Screenshot.takeScreenshot(Minecraft.getInstance().gameRenderer.mainRenderTarget(), img -> {
			try {
				byte[] buf = fr.poll(); // none free: encoder behind, frame dropped
				if (buf == null) return;
				if (img.getWidth() * img.getHeight() * 4 != size) { fr.offer(buf); return; } // window resized mid-recording
				if (buf.length != size) buf = new byte[size];
				MemoryUtil.memByteBuffer(img.getPointer(), size).get(buf); // NativeImage is RGBA in memory: no per-pixel work
				q.offer(new Frame(buf, stamp));
			} finally {
				img.close();
			}
		});
	}

	private static void put(Frame b) {
		try { queue.put(b); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
	}

	private static void encode(Minecraft mc, BlockingQueue<Frame> q, BlockingQueue<byte[]> free, int srcW, int srcH, File gif, int fps, int width, String ffmpeg, int device, long intervalNs) {
		File tmp = new File(gif.getParentFile(), gif.getName() + ".tmp.mkv");
		try {
			Frame first = q.take();
			if (first == END) throw new IllegalStateException("no frames captured");
			int w = Math.min(width, srcW) & ~1;
			// Stage 1: near-lossless intermediate, scaled once here so the encoder handles fewer pixels. Constant fps; late frames repeat the previous one so playback stays real-time.
			boolean gpu = device >= 0 && device < Config.gpus().size();
			ProcessBuilder pb = new ProcessBuilder(ffmpeg, "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgba",
				"-s", srcW + "x" + srcH, "-framerate", Integer.toString(fps), "-i", "-",
				"-vf", "scale=" + w + ":-2:flags=bicubic", "-pix_fmt", "yuv444p");
			// NVENC: constant-QP near-lossless on the chosen GPU; libx264: capped threads so the game keeps its cores.
			if (gpu) pb.command().addAll(java.util.List.of("-c:v", "h264_nvenc", "-gpu", Integer.toString(device), "-preset", "p1", "-rc", "constqp", "-qp", "10"));
			else pb.command().addAll(java.util.List.of("-c:v", "libx264", "-preset", "ultrafast", "-crf", "10",
				"-threads", Integer.toString(Math.max(2, Runtime.getRuntime().availableProcessors() / 4))));
			pb.command().add(tmp.getPath());
			pb.environment().put("CUDA_DEVICE_ORDER", "PCI_BUS_ID");
			Process p = pb.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
			int frames = 0;
			try (OutputStream out = p.getOutputStream()) {
				for (Frame f = first; f != END; f = q.take()) {
					long due = Math.max(1, Math.round((double) (f.ns - first.ns) / intervalNs) + 1);
					do { out.write(f.rgba); frames++; } while (frames < due);
					free.offer(f.rgba);
				}
			}
			if (p.waitFor() != 0) throw new IllegalStateException("ffmpeg recording failed");
			// Stage 2: video -> GIF with a palette built from the whole clip.
			Process g = new ProcessBuilder(ffmpeg, "-y", "-loglevel", "error", "-i", tmp.getPath(), "-vf",
				"split[a][b];[a]palettegen=stats_mode=diff[p];[b][p]paletteuse=dither=sierra2_4a:diff_mode=rectangle",
				"-loop", "0", gif.getPath()).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
			if (g.waitFor() != 0) throw new IllegalStateException("ffmpeg GIF conversion failed");
			message(mc, Component.translatable("gif-screenshots.saved", gif.getName()));
		} catch (Exception e) {
			GifScreenshotsClient.LOGGER.error("GIF encoding failed (is ffmpeg installed and on PATH?)", e);
			try { while (q.take() != END) { /* drain so the render thread never blocks on a full queue */ } } catch (InterruptedException ignored) { }
			message(mc, Component.translatable("gif-screenshots.failed", String.valueOf(e.getMessage())));
		} finally {
			tmp.delete();
		}
	}

	private static void message(Minecraft mc, Component c) {
		mc.execute(() -> { if (mc.player != null) mc.player.sendOverlayMessage(c); });
	}
}
