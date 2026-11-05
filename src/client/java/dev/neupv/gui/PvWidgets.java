/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Shared widgets ported from NEU's GuiProfileViewer (renderXpBar, getPercentage) and Utils (drawPvSideButton),
 * Copyright (C) 2022-2024 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import dev.neupv.item.PvItem;
import dev.neupv.pv.Level;
import dev.neupv.pv.Weights;
import dev.neupv.util.Fmt;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PvWidgets {
	private PvWidgets() {}

	/** Tooltip of a SkyBlock item: its name then its lore, as on Hypixel. */
	public static List<Component> itemTooltip(PvItem item) {
		List<Component> tt = new ArrayList<>();
		if (item == null) return tt;
		tt.add(Component.literal(item.displayName == null || item.displayName.isEmpty() ? item.stack().getHoverName().getString() : item.displayName));
		for (String line : item.lore) tt.add(Component.literal(line));
		return tt;
	}

	public static List<Component> lines(String... lines) {
		List<Component> tt = new ArrayList<>();
		for (String l : lines) tt.add(Component.literal(l));
		return tt;
	}

	/** NEU GuiProfileViewer.getPercentage. */
	public static float percentage(String skillName, Level level) {
		if (level.maxed) return 100;
		if (skillName.contains("catacombs")) return level.totalXp / Weights.CATACOMBS_LEVEL_50_XP * 100;
		if (skillName.equalsIgnoreCase("vampire")) return level.totalXp / 2400 * 100;
		if (Weights.SLAYER_NAMES.contains(skillName)) return level.totalXp / 1000000 * 100;
		if (skillName.equalsIgnoreCase("social")) return level.totalXp / 272800 * 100;
		if (skillName.equalsIgnoreCase("garden")) return level.totalXp / 60120 * 100;
		return level.totalXp / (level.maxLevel == 60 ? Weights.SKILLS_LEVEL_60 : Weights.SKILLS_LEVEL_50) * 100;
	}

	/** Exact port of NEU GuiProfileViewer.renderXpBar (name + level, bar, tooltip, 0.7-scaled icon). */
	public static void renderXpBar(ProfileViewerScreen screen, GuiGraphicsExtractor g, String skillName, ItemStack stack,
		int x, int y, int xSize, Level levelObj, int mouseX, int mouseY) {
		float level = levelObj.level;
		Draw.aligned(g, skillName, "§f" + (int) Math.floor(level), x + 14, y - 4, xSize - 20);
		String lower = Fmt.stripColor(skillName).toLowerCase(Locale.ROOT);
		boolean dungeonLike = skillName.contains("Catacombs") || Weights.DUNGEON_CLASS_NAMES.stream().anyMatch(lower::contains);
		if (levelObj.maxed || (dungeonLike && levelObj.level >= 50)) Draw.goldBar(g, x, y + 6, xSize, screen.startTime);
		else Draw.bar(g, x, y + 6, xSize, level % 1);

		if (mouseX > x && mouseX < x + 120 && mouseY > y - 4 && mouseY < y + 13) {
			String levelStr;
			String totalXpStr = null;
			if (skillName.contains("Catacombs")) {
				totalXpStr = "§7Total XP: §5" + Fmt.number(levelObj.totalXp) + "§8 (" + Fmt.tenths(percentage(lower, levelObj)) + "% to 50)";
			}
			if (levelObj.maxed) {
				levelStr = !skillName.contains("HOTM")
					? "§6MAXED!§7 (Overflow level: " + String.format(Locale.US, "%.2f", levelObj.level) + ")"
					: "§6MAXED!";
			} else if (skillName.contains("Class Average")) {
				levelStr = "Progress: §5" + String.format(Locale.US, "%.1f", level % 1 * 100) + "%";
				totalXpStr = "Exact Class Average: §f" + String.format(Locale.US, "%.2f", levelObj.level);
			} else {
				int maxXp = (int) levelObj.maxXpForLevel;
				levelStr = "§5" + Fmt.shortNumber(Math.round((level % 1) * maxXp)) + "/" + Fmt.shortNumber(maxXp)
					+ (skillName.contains("Catacombs") && levelObj.level >= 50 ? "§7 (Overflow level: " + String.format(Locale.US, "%.2f", levelObj.level) + ")" : "");
			}
			screen.tooltipToDisplay = totalXpStr != null ? lines(levelStr, totalXpStr) : lines(levelStr);
		}
		Draw.item(g, stack, x, y - 6, 0.7f);
	}

	/** NEU Utils.drawPvSideButton: buttons on the left edge used to switch between a page and its sub-page. */
	public static void sideButton(ProfileViewerScreen screen, GuiGraphicsExtractor g, int yIndex, ItemStack icon, boolean pressed, int mouseX, int mouseY) {
		int x = screen.guiLeft() - 28, y = screen.guiTop() + yIndex * 28;
		float uMin = 193 / 256f, uMax = 223 / 256f, vMin = 200 / 256f, vMax = 228 / 256f;
		if (pressed) {
			uMin = 224 / 256f;
			uMax = 1f;
			if (yIndex != 0) {
				vMin = 228 / 256f;
				vMax = 1f;
			}
		}
		if (pressed) Draw.rect(g, ProfileViewerScreen.PV_ELEMENTS, x, y, 32, 28, uMin, uMax, vMin, vMax, 256, 256);
		else Draw.rectTinted(g, ProfileViewerScreen.PV_ELEMENTS, x, y, 28, 28, uMin, uMax, vMin, vMax, 256, 256, 0xFF8A8A8A);
		g.item(icon, x + 8, y + 7);
		if (!pressed) g.fill(x + 6, y + 5, x + 26, y + 25, 0x50000000);
		if (mouseY > y && mouseX > x && mouseY < y + 28 && mouseX < x + 28) screen.tooltipToDisplay = List.of(icon.getHoverName());
	}

	/** Draws the two side buttons (index 0 = main page, 1 = sub-page) with the right one pressed. */
	public static void sideButtons(ProfileViewerScreen screen, GuiGraphicsExtractor g, ItemStack first, ItemStack second, int mouseX, int mouseY) {
		boolean onSecond = ProfileViewerScreen.isOnSecondPage();
		sideButton(screen, g, 0, first, !onSecond, mouseX, mouseY);
		sideButton(screen, g, 1, second, onSecond, mouseX, mouseY);
	}

	/** NEU ProfileViewerUtils.onSlotToChangePage: 1 = first side button clicked, 2 = second, 0 = none. */
	public static int sideButtonClicked(ProfileViewerScreen screen, double mouseX, double mouseY) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		if (mouseX >= guiLeft - 29 && mouseX <= guiLeft) {
			if (mouseY >= guiTop && mouseY <= guiTop + 28) return 1;
			if (mouseY + 28 >= guiTop && mouseY <= guiTop + 28 * 2) return 2;
		}
		return 0;
	}
}
