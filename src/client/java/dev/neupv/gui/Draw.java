/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Rendering helpers replacing NEU's Utils.draw* (1.8.9 immediate-mode GL) with the 26.3 GuiGraphicsExtractor API.
 */
package dev.neupv.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class Draw {
	public static final Identifier BAR_BG = Identifier.withDefaultNamespace("hud/experience_bar_background");
	public static final Identifier BAR_FG = Identifier.withDefaultNamespace("hud/experience_bar_progress");

	private Draw() {}

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	public static Identifier tex(String path) {
		return Identifier.fromNamespaceAndPath("neupv", "textures/" + path);
	}

	/** Same contract as NEU's Utils.drawTexturedRect(x, y, w, h, uMin, uMax, vMin, vMax) with normalized UVs. */
	public static void rect(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h, float uMin, float uMax, float vMin, float vMax, int texW, int texH) {
		int u = Math.round(uMin * texW);
		int v = Math.round(vMin * texH);
		int rw = Math.round((uMax - uMin) * texW);
		int rh = Math.round((vMax - vMin) * texH);
		if (rw <= 0 || rh <= 0) return;
		g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, rw, rh, texW, texH);
	}

	/** Same as rect, multiplied by an ARGB colour (e.g. 0xFF909090 to darken). */
	public static void rectTinted(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h, float uMin, float uMax, float vMin, float vMax, int texW, int texH, int argb) {
		int u = Math.round(uMin * texW);
		int v = Math.round(vMin * texH);
		int rw = Math.round((uMax - uMin) * texW);
		int rh = Math.round((vMax - vMin) * texH);
		if (rw <= 0 || rh <= 0) return;
		g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, rw, rh, texW, texH, argb);
	}

	public static void full(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h) {
		g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0, 0, w, h, w, h, w, h);
	}

	public static void text(GuiGraphicsExtractor g, String s, float x, float y, boolean shadow) {
		g.text(font(), s, Math.round(x), Math.round(y), 0xFFFFFFFF, shadow);
	}

	public static void centered(GuiGraphicsExtractor g, String s, float x, float y, boolean shadow) {
		int w = font().width(s);
		g.text(font(), s, Math.round(x - w / 2f), Math.round(y - 4), 0xFFFFFFFF, shadow);
	}

	public static void centeredScaled(GuiGraphicsExtractor g, String s, float x, float y, boolean shadow, float scale) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		centered(g, s, 0, 0, shadow);
		g.pose().popMatrix();
	}

	public static void centeredMaxWidth(GuiGraphicsExtractor g, String s, float x, float y, boolean shadow, int maxWidth, int color) {
		int w = font().width(s);
		float scale = w > maxWidth ? maxWidth / (float) w : 1f;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font(), s, -w / 2, -4, color, shadow);
		g.pose().popMatrix();
	}

	public static void scaled(GuiGraphicsExtractor g, String s, float x, float y, boolean shadow, float scale) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font(), s, 0, 0, 0xFFFFFFFF, shadow);
		g.pose().popMatrix();
	}

	/** NEU's Utils.renderAlignedString: left text at x, right text right-aligned to x + width. */
	public static void aligned(GuiGraphicsExtractor g, String left, String right, float x, float y, int width) {
		String both = left + " " + right;
		if (font().width(both) >= width) {
			// NEU: renderShadowedString(first + " " + second, x + length / 2f, y, length) -> centred and shrunk to fit
			centeredMaxWidth(g, both, x + width / 2f, y + 4, true, width, 0xFFFFFFFF);
			return;
		}
		g.text(font(), left, Math.round(x), Math.round(y), 0xFFFFFFFF, true);
		g.text(font(), right, Math.round(x + width - font().width(right)), Math.round(y), 0xFFFFFFFF, true);
	}

	public static void item(GuiGraphicsExtractor g, ItemStack stack, float x, float y, float scale) {
		if (stack == null || stack.isEmpty()) return;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.item(stack, 0, 0);
		g.pose().popMatrix();
	}

	/** XP-style bar, same layout as NEU's renderBar (left half + right half of the 182px vanilla bar). */
	public static void bar(GuiGraphicsExtractor g, float x, float y, float xSize, float completed) {
		completed = Math.round(completed / 0.05f) * 0.05f;
		int ix = Math.round(x), iy = Math.round(y), half = Math.round(xSize / 2f);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_BG, 182, 5, 0, 0, ix, iy, half, 5);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_BG, 182, 5, 182 - half, 0, ix + half, iy, half, 5);
		int filled = Math.round(xSize * completed);
		if (filled > 0) {
			int left = Math.min(filled, half);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_FG, 182, 5, 0, 0, ix, iy, left, 5);
			int right = filled - half;
			if (right > 0) g.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_FG, 182, 5, 182 - half, 0, ix + half, iy, right, 5);
		}
	}

	private static int[] barPixels; // ARGB of hud/experience_bar_progress, 182x5
	private static int barW = 182, barH = 5;

	/** Pixels of the vanilla (or resource pack) XP bar progress sprite, read once. */
	private static int[] barPixels() {
		if (barPixels != null) return barPixels;
		try (var in = Minecraft.getInstance().getResourceManager()
			.open(Identifier.withDefaultNamespace("textures/gui/sprites/hud/experience_bar_progress.png"))) {
			java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
			barW = img.getWidth();
			barH = img.getHeight();
			barPixels = img.getRGB(0, 0, barW, barH, null, 0, barW);
		} catch (Exception e) {
			barPixels = new int[0];
		}
		return barPixels;
	}

	/**
	 * Maxed bar, exact port of NEU's "make_gold" shader applied to the same XP bar texture as normal bars:
	 * every texel keeps its brightness and alpha, its hue becomes hue + time / 10 s + 4 * screen-x (clip space),
	 * and its saturation is scaled to 70 %. The 26.3 GUI cannot run custom GLSL, so it is drawn texel by texel.
	 */
	public static void goldBar(GuiGraphicsExtractor g, float x, float y, float xSize, long startTime) {
		int[] px = barPixels();
		int ix = Math.round(x), iy = Math.round(y), w = Math.round(xSize), half = Math.round(xSize / 2f);
		if (px.length == 0) { // texture unreadable: plain full bar
			bar(g, x, y, xSize, 1);
			return;
		}
		float guiWidth = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		float amount = (startTime - System.currentTimeMillis()) / 10000f;
		org.joml.Vector2f p = new org.joml.Vector2f();
		float[] hsb = new float[3];
		for (int col = 0; col < w; col++) {
			// same texel mapping as Draw.bar: left half from the start of the sprite, right half from its end
			int u = col < half ? col : barW - (w - col);
			if (u < 0 || u >= barW) continue;
			g.pose().transformPosition(ix + col + 0.5f, iy, p);
			float clipX = p.x / guiWidth * 2f - 1f;
			for (int row = 0; row < Math.min(5, barH); row++) {
				int argb = px[row * barW + u];
				int a = argb >>> 24;
				if (a == 0) continue;
				java.awt.Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, hsb);
				float hue = (((hsb[0] + amount + clipX * 4f) % 1f) + 1f) % 1f;
				// NEU scaled saturation to 70 %, which looks washed out on the 26.3 bar texture: keep it vivid instead
				int rgb = java.awt.Color.HSBtoRGB(hue, Math.min(1f, 0.6f + hsb[1] * 0.45f), Math.min(1f, hsb[2] * 1.12f)) & 0xFFFFFF;
				g.fill(ix + col, iy + row, ix + col + 1, iy + row + 1, (a << 24) | rgb);
			}
		}
	}

	public static boolean within(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx <= x + w && my >= y && my <= y + h;
	}
}
