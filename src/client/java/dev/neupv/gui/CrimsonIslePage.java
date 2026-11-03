/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates CrimsonIslePage, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** TODO: port of NEU's CrimsonIslePage ("Crimson Isle" tab). */
public class CrimsonIslePage extends PvPage {
	public CrimsonIslePage(ProfileViewerScreen screen) {
		super(screen);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		int cx = screen.guiLeft() + ProfileViewerScreen.SIZE_X / 2;
		int cy = screen.guiTop() + ProfileViewerScreen.SIZE_Y / 2;
		Draw.centered(g, "§eCrimson Isle", cx, cy - 6, true);
		Draw.centered(g, "§7Not ported yet", cx, cy + 6, true);
	}
}
