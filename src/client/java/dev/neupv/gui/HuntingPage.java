/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Not in NEU: Hunting skill, Attribute Shards and Safari (Hypixel updates after NEU 2.6.0).
 */
package dev.neupv.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** TODO: new "Hunting" tab (Hunting skill, Attribute Shards, Safari). */
public class HuntingPage extends PvPage {
	public HuntingPage(ProfileViewerScreen screen) {
		super(screen);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		int cx = screen.guiLeft() + ProfileViewerScreen.SIZE_X / 2;
		int cy = screen.guiTop() + ProfileViewerScreen.SIZE_Y / 2;
		Draw.centered(g, "§3Hunting", cx, cy - 6, true);
		Draw.centered(g, "§7Not ported yet", cx, cy + 6, true);
	}
}
