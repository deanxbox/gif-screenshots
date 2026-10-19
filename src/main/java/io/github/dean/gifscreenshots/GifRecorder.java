package io.github.dean.gifscreenshots;

import com.mojang.blaze3d.platform.NativeImage;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;

/** Captures frames on the render thread, encodes on a worker so the game does not stall. */
public final class GifRecorder {
	private static final BufferedImage END = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
	private static volatile boolean recording;
	private static long nextFrameNs, endNs, intervalNs;
	private static int outW, outH;
	private static BlockingQueue<BufferedImage> queue;

	private GifRecorder() {}

	public static void start(Minecraft mc) {
		if (recording) return;
		Config c = Config.INSTANCE;
		int srcW = mc.getWindow().getWidth(), srcH = mc.getWindow().getHeight();
		if (srcW <= 0 || srcH <= 0) return;
		outW = Math.min(c.width, srcW);
		outH = Math.max(1, Math.round(outW * (float) srcH / srcW));
		intervalNs = 1_000_000_000L / c.fps;
		int delay = Math.max(1, Math.round(100f / c.fps)); // GIF delay is in 1/100 s
		queue = new ArrayBlockingQueue<>(8);
		File out = new File(new File(mc.gameDirectory, Screenshot.SCREENSHOT_DIR),
			"gif_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")) + ".gif");
		out.getParentFile().mkdirs();
		BlockingQueue<BufferedImage> q = queue;
		Thread t = new Thread(() -> encode(mc, q, out, delay), "GIF-Encoder");
		t.setDaemon(true);
		t.start();
		nextFrameNs = System.nanoTime();
		endNs = nextFrameNs + c.seconds * 1_000_000_000L;
		recording = true;
		message(mc, Component.translatable("gif-screenshots.recording"));
	}

	/** Render thread, end of each frame. */
	public static void onFrame() {
		if (!recording) return;
		long now = System.nanoTime();
		if (now >= endNs) { recording = false; put(END); return; }
		if (now < nextFrameNs) return;
		nextFrameNs += intervalNs;
		if (nextFrameNs < now) nextFrameNs = now + intervalNs; // fell behind: skip, don't burst
		Minecraft mc = Minecraft.getInstance();
		int w = outW, h = outH;
		BlockingQueue<BufferedImage> q = queue;
		Screenshot.takeScreenshot(mc.getMainRenderTarget(), img -> {
			try {
				q.offer(scale(img, w, h)); // encoder behind: frame dropped
			} finally {
				img.close();
			}
		});
	}

	private static void put(BufferedImage b) {
		try { queue.put(b); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
	}

	// Nearest-neighbour: cheap enough for the render thread.
	private static BufferedImage scale(NativeImage src, int w, int h) {
		BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		int sw = src.getWidth(), sh = src.getHeight();
		int[] px = src.getPixelsABGR();
		int[] dst = new int[w * h];
		for (int y = 0; y < h; y++) {
			int row = (int) ((long) y * sh / h) * sw;
			for (int x = 0; x < w; x++) {
				int p = px[row + (int) ((long) x * sw / w)];
				dst[y * w + x] = ((p & 0xFF) << 16) | (p & 0xFF00) | ((p >> 16) & 0xFF);
			}
		}
		out.setRGB(0, 0, w, h, dst, 0, w);
		return out;
	}

	private static void encode(Minecraft mc, BlockingQueue<BufferedImage> q, File file, int delay) {
		try (ImageOutputStream ios = ImageIO.createImageOutputStream(file)) {
			ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
			writer.setOutput(ios);
			writer.prepareWriteSequence(null);
			ImageWriteParam param = writer.getDefaultWriteParam();
			int frames = 0;
			for (BufferedImage f; (f = q.take()) != END; frames++) {
				IIOMetadata meta = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(f), param);
				configure(meta, delay, frames == 0);
				writer.writeToSequence(new IIOImage(f, null, meta), param);
			}
			writer.endWriteSequence();
			writer.dispose();
			if (frames == 0) throw new IllegalStateException("no frames captured");
			message(mc, Component.translatable("gif-screenshots.saved", file.getName()));
		} catch (Exception e) {
			GifScreenshotsClient.LOGGER.error("GIF encoding failed", e);
			message(mc, Component.translatable("gif-screenshots.failed", String.valueOf(e.getMessage())));
		}
	}

	private static void configure(IIOMetadata meta, int delay, boolean first) throws Exception {
		String fmt = meta.getNativeMetadataFormatName();
		IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(fmt);
		IIOMetadataNode gce = child(root, "GraphicControlExtension");
		gce.setAttribute("disposalMethod", "none");
		gce.setAttribute("userInputFlag", "FALSE");
		gce.setAttribute("transparentColorFlag", "FALSE");
		gce.setAttribute("delayTime", Integer.toString(delay));
		gce.setAttribute("transparentColorIndex", "0");
		if (first) { // loop forever
			IIOMetadataNode app = new IIOMetadataNode("ApplicationExtension");
			app.setAttribute("applicationID", "NETSCAPE");
			app.setAttribute("authenticationCode", "2.0");
			app.setUserObject(new byte[] {1, 0, 0});
			child(root, "ApplicationExtensions").appendChild(app);
		}
		meta.setFromTree(fmt, root);
	}

	private static IIOMetadataNode child(IIOMetadataNode root, String name) {
		for (int i = 0; i < root.getLength(); i++)
			if (root.item(i).getNodeName().equalsIgnoreCase(name)) return (IIOMetadataNode) root.item(i);
		IIOMetadataNode n = new IIOMetadataNode(name);
		root.appendChild(n);
		return n;
	}

	private static void message(Minecraft mc, Component c) {
		mc.execute(() -> { if (mc.player != null) mc.player.sendOverlayMessage(c); });
	}
}
