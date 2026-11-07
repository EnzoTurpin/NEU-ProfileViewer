/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates GuiProfileViewerPage.java, Copyright (C) 2022 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import dev.neupv.pv.SkyblockProfiles;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public abstract class PvPage {
	protected final ProfileViewerScreen screen;

	protected PvPage(ProfileViewerScreen screen) {
		this.screen = screen;
	}

	public SkyblockProfiles.SkyblockProfile getSelectedProfile() {
		return screen.getSelectedProfile();
	}

	public abstract void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks);

	/** @return true if the click was consumed */
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return false;
	}

	/**
	 * NEU acted on mouse release in several pages. Here the screen calls mouseReleased right after a press that
	 * mouseClicked did not consume (so one click = one action), unless the page needs the real release
	 * (drag gestures) and returns true here.
	 */
	public boolean wantsRealRelease() {
		return false;
	}

	public void mouseReleased(double mouseX, double mouseY, int button) {}

	/** True if the page draws the two left side buttons (PvWidgets.sideButtons); the panel border is opened under the pressed one. */
	public boolean hasSideButtons() {
		return false;
	}

	/** Mouse wheel, in PV coordinates. @return true if consumed */
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		return false;
	}

	/** @return true if the key was consumed */
	public boolean keyPressed(int keyCode) {
		return false;
	}

	public void resetCache() {}
}
