/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Placeholder for tabs that are not ported yet (the port is done tab by tab).
 */
package dev.neupv.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public class NotPortedPage extends PvPage {
	private final String name;

	public NotPortedPage(ProfileViewerScreen screen, String name) {
		super(screen);
		this.name = name;
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		int cx = screen.guiLeft() + ProfileViewerScreen.SIZE_X / 2;
		int cy = screen.guiTop() + ProfileViewerScreen.SIZE_Y / 2;
		Draw.centered(g, "§e" + name, cx, cy - 6, true);
		Draw.centered(g, "§7Not ported yet", cx, cy + 6, true);
	}
}
