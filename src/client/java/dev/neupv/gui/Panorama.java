/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates Panorama.java, Copyright (C) 2022 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 *
 * NEU rendered a perspective cube map with raw GL calls, which the 26.3 GUI renderer does not allow. Here the
 * 4 side faces are pre-joined into one strip (512x128, generated from NEU's images) and scrolled horizontally,
 * which gives the same "rotating view of the island" effect without the vertical perspective.
 */
package dev.neupv.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public final class Panorama {
	private static final int STRIP_W = 512, STRIP_H = 128;
	private static final Map<String, Identifier> CACHE = new HashMap<>();

	private Panorama() {}

	/** Same lookup order as NEU: location_day/night, then location, then unknown. */
	static Identifier forLocation(String location, String identifier) {
		return CACHE.computeIfAbsent(location + "|" + identifier, k -> {
			for (String name : new String[]{location + "_" + identifier, location}) {
				Identifier id = Draw.tex("panorama/" + name + ".png");
				if (Minecraft.getInstance().getResourceManager().getResource(id).isPresent()) return id;
			}
			return Draw.tex("panorama/unknown.png");
		});
	}

	public static void draw(GuiGraphicsExtractor g, float angle, int x, int y, int w, int h, String location, String identifier) {
		Identifier id = forLocation(location, identifier);
		int regionW = Math.round(w * (STRIP_H / (float) h));
		float a = ((angle % 360) + 360) % 360;
		int u = Math.round(a / 360f * STRIP_W) % STRIP_W;
		int first = Math.min(regionW, STRIP_W - u);
		int firstW = Math.round(first * (h / (float) STRIP_H));
		g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, 0, firstW, h, first, STRIP_H, STRIP_W, STRIP_H);
		if (first < regionW) {
			g.blit(RenderPipelines.GUI_TEXTURED, id, x + firstW, y, 0, 0, w - firstW, h, regionW - first, STRIP_H, STRIP_W, STRIP_H);
		}
	}
}
