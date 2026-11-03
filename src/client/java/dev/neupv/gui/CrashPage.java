/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates CrashRecoveryPage.java, Copyright (C) 2022 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 * Fix: no longer depends on the 3D player model being loaded (NEU's version could itself crash the game).
 */
package dev.neupv.gui;

import dev.neupv.NeuPv;
import dev.neupv.repo.NeuRepo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class CrashPage extends PvPage {
	private final Throwable exception;
	private final String timestamp = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now());
	private final String lastPage;
	private final String report;

	public CrashPage(ProfileViewerScreen screen, Throwable exception, String lastPage) {
		super(screen);
		this.exception = exception;
		this.lastPage = lastPage;
		StringWriter sw = new StringWriter();
		exception.printStackTrace(new PrintWriter(sw));
		this.report = "NEU Profile Viewer (26.3 port) crashed\n" +
			"Viewed Player: " + screen.playerNameForReport() + "\n" +
			"Viewed Player UUID: " + screen.uuidForReport() + "\n" +
			"Viewed Profile: " + screen.getProfileName() + "\n" +
			"Timestamp: " + timestamp + "\n" +
			"Last Viewed Page: " + lastPage + "\n" +
			"Repo Commit: " + NeuRepo.commit() + "\n\n" + sw;
		NeuPv.LOGGER.error(report);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		int cx = screen.guiLeft() + ProfileViewerScreen.SIZE_X / 2;
		int y = screen.guiTop() + 20;
		Draw.centeredScaled(g, "§cKA-BOOM!", cx, y, false, 2);
		y += 24;
		String[] lines = {
			"§cLooks like the profile viewer crashed on page " + lastPage + ".",
			"§cThe report below can be copied with the button.",
			"Viewed Player: " + screen.playerNameForReport(),
			"Viewed Profile: " + screen.getProfileName(),
			"Timestamp: " + timestamp,
			"",
			exception.toString()
		};
		for (String l : lines) {
			Draw.centeredMaxWidth(g, l, cx, y, false, ProfileViewerScreen.SIZE_X - 20, -1);
			y += 11;
		}
		for (StackTraceElement e : exception.getStackTrace()) {
			if (y >= screen.guiTop() + ProfileViewerScreen.SIZE_Y - 40) break;
			Draw.centeredMaxWidth(g, e.toString(), cx, y, false, ProfileViewerScreen.SIZE_X - 20, 0xFFAAAAAA);
			y += 11;
		}
		int bx = cx - 40, by = screen.guiTop() + ProfileViewerScreen.SIZE_Y - 30;
		g.fill(bx, by, bx + 80, by + 12, 0xC0101010);
		g.outline(bx, by, 80, 12, 0xFF808080);
		Draw.centered(g, "Copy Report", cx, by + 6, false);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int cx = screen.guiLeft() + ProfileViewerScreen.SIZE_X / 2;
		int by = screen.guiTop() + ProfileViewerScreen.SIZE_Y - 30;
		if (button == 0 && Draw.within(mouseX, mouseY, cx - 40, by, 80, 12)) {
			Minecraft.getInstance().keyboardHandler.setClipboard(report);
			return true;
		}
		return false;
	}
}
