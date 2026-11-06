/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates MiningPage, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonObject;
import dev.neupv.item.LegacyItems;
import dev.neupv.pv.Level;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.util.Fmt;
import dev.neupv.util.J;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** "Heart of the Mountain" tab. */
public class MiningPage extends PvPage {
	private static final Identifier MINING_PAGE_TEXTURE = Draw.tex("gui/mining/background.png");
	private static final Identifier FORGE_SLOT_BACKGROUND = Draw.tex("gui/mining/perk_background.png");
	private static final Map<String, String> CRYSTAL_TO_COLOR = new LinkedHashMap<>();

	static {
		CRYSTAL_TO_COLOR.put("jade", "§a");
		CRYSTAL_TO_COLOR.put("amethyst", "§5");
		CRYSTAL_TO_COLOR.put("amber", "§6");
		CRYSTAL_TO_COLOR.put("sapphire", "§b");
		CRYSTAL_TO_COLOR.put("topaz", "§e");
		CRYSTAL_TO_COLOR.put("jasper", "§d");
		CRYSTAL_TO_COLOR.put("ruby", "§c");
		CRYSTAL_TO_COLOR.put("opal", "§f");
		CRYSTAL_TO_COLOR.put("aquamarine", "§9");
		CRYSTAL_TO_COLOR.put("peridot", "§2");
		CRYSTAL_TO_COLOR.put("onyx", "§8");
		CRYSTAL_TO_COLOR.put("citrine", "§4");
	}

	private static ItemStack hotmSkillIcon, anvil;
	private final MiningHotmTree tree = new MiningHotmTree();
	private int treeLeft, treeTop, treeRight, treeBottom;

	public MiningPage(ProfileViewerScreen screen) {
		super(screen);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (treeLeft < mouseX && mouseX < treeRight && treeTop < mouseY && mouseY < treeBottom) {
			// NEU adds Mouse.getDWheel() (±120 per notch); one tree row per notch is easier to use.
			tree.scroll += (int) Math.round(vertical * 24);
			return true;
		}
		return false;
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		if (hotmSkillIcon == null) {
			hotmSkillIcon = new ItemStack(Items.IRON_PICKAXE);
			anvil = new ItemStack(Items.ANVIL);
		}
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		Draw.full(g, MINING_PAGE_TEXTURE, guiLeft, guiTop, ProfileViewerScreen.SIZE_X, ProfileViewerScreen.SIZE_Y);

		SkyblockProfiles.SkyblockProfile selected = getSelectedProfile();
		if (selected == null) return;
		JsonObject member = selected.getProfileJson();
		JsonObject core = J.objOrEmpty(member, "mining_core");
		JsonObject nodes = dev.neupv.pv.SkillTree.hotmNodes(member);

		float xStart = 22, yStartTop = 27;
		int x = guiLeft + 23, y = guiTop + 25, sectionWidth = 110;

		double mithril = J.dbl(core, "powder_mithril", 0), gemstone = J.dbl(core, "powder_gemstone", 0), glacite = J.dbl(core, "powder_glacite", 0);
		double mithrilSpent = J.dbl(core, "powder_spent_mithril", 0), gemstoneSpent = J.dbl(core, "powder_spent_gemstone", 0), glaciteSpent = J.dbl(core, "powder_spent_glacite", 0);

		double nucleusRuns = Double.MAX_VALUE;
		for (String crystal : List.of("amber", "amethyst", "jade", "sapphire", "topaz")) {
			nucleusRuns = Math.min(nucleusRuns, J.dbl(core, "crystals." + crystal + "_crystal.total_placed", 0));
		}

		Map<String, Level> levelingInfo = selected.getLevelingInfo();
		Level hotm = levelingInfo == null ? null : levelingInfo.get("hotm");
		if (hotm != null) PvWidgets.renderXpBar(screen, g, "§cHOTM", hotmSkillIcon, x, y, sectionWidth, hotm, mouseX, mouseY);

		// Powder
		Draw.aligned(g, "§2Mithril", "", guiLeft + xStart + 45, guiTop + yStartTop + 24, 115);
		Draw.aligned(g, "§dGemstone", "", guiLeft + xStart + 85, guiTop + yStartTop + 24, 115);
		Draw.aligned(g, "§bGlacite", "", guiLeft + xStart + 145, guiTop + yStartTop + 24, 115);
		Draw.aligned(g, "§fPowder:", "", guiLeft + xStart, guiTop + yStartTop + 44, 115);
		Draw.aligned(g, "§2" + shortNumber(mithril), "", guiLeft + xStart + 50, guiTop + yStartTop + 44, 115);
		Draw.aligned(g, "§d" + shortNumber(gemstone), "", guiLeft + xStart + 100, guiTop + yStartTop + 44, 115);
		Draw.aligned(g, "§b" + shortNumber(glacite), "", guiLeft + xStart + 150, guiTop + yStartTop + 44, 115);
		Draw.aligned(g, "§fTotal:", "", guiLeft + xStart, guiTop + yStartTop + 54, 115);
		Draw.aligned(g, "§2" + shortNumber(mithril + mithrilSpent), "", guiLeft + xStart + 50, guiTop + yStartTop + 54, 115);
		Draw.aligned(g, "§d" + shortNumber(gemstone + gemstoneSpent), "", guiLeft + xStart + 100, guiTop + yStartTop + 54, 115);
		Draw.aligned(g, "§b" + shortNumber(glacite + glaciteSpent), "", guiLeft + xStart + 150, guiTop + yStartTop + 54, 115);
		// Addition: exact available/spent/total per powder on hover of its column.
		if (mouseY >= guiTop + yStartTop + 22 && mouseY < guiTop + yStartTop + 64) {
			int col = mouseX >= guiLeft + xStart + 45 && mouseX < guiLeft + xStart + 95 ? 0
				: mouseX >= guiLeft + xStart + 95 && mouseX < guiLeft + xStart + 145 ? 1
				: mouseX >= guiLeft + xStart + 145 && mouseX < guiLeft + xStart + 195 ? 2 : -1;
			if (col == 0) screen.tooltipToDisplay = powderTooltip("§2", "Mithril", mithril, mithrilSpent);
			if (col == 1) screen.tooltipToDisplay = powderTooltip("§d", "Gemstone", gemstone, gemstoneSpent);
			if (col == 2) screen.tooltipToDisplay = powderTooltip("§b", "Glacite", glacite, glaciteSpent);
		}

		drawCrystals(g, core, nucleusRuns, mouseX, mouseY);

		treeLeft = guiLeft + 249;
		treeTop = guiTop + 16;
		treeRight = guiLeft + 412;
		treeBottom = guiTop + 185;
		tree.render(g, screen, treeLeft, treeTop, treeRight, treeBottom, nodes, hotm == null ? 0 : hotm.level, mouseX, mouseY);
		tree.markSelectedAbility(dev.neupv.pv.SkillTree.hotmSelectedAbility(member));

		int forgeX = guiLeft + 149, forgeY = guiTop + 20;
		Draw.rect(g, FORGE_SLOT_BACKGROUND, forgeX, forgeY, 16, 16, 0, 1, 0, 1, 20, 20);
		g.item(anvil, forgeX, forgeY);
		if (mouseX >= forgeX && mouseX < forgeX + 16 && mouseY >= forgeY && mouseY < forgeY + 16) {
			screen.tooltipToDisplay = MiningForge.tooltip(member, hotm == null ? 0 : (int) Math.floor(hotm.level));
		}
	}

	private void drawCrystals(GuiGraphicsExtractor g, JsonObject core, double nucleusRuns, int mouseX, int mouseY) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		int padding = 4;
		int rectStartX = 16 + padding, rectStartY = 97 + padding;
		int rectXSize = 214 - padding * 2;
		int originalRectYSize = 89;
		int rowHeight = 10, totalColumns = 2;
		int rectYSize = rowHeight * (int) Math.ceil((double) CRYSTAL_TO_COLOR.size() / totalColumns);
		int totalRows = rectYSize / rowHeight;
		int columnWidth = rectXSize / totalColumns - padding / totalColumns;
		int columnOffset = columnWidth + padding;

		int idx = 0;
		for (Map.Entry<String, String> crystal : CRYSTAL_TO_COLOR.entrySet()) {
			int row = idx % totalRows, col = idx / totalRows;
			String path = "crystals." + crystal.getKey() + "_crystal.";
			String state = J.str(core, path + "state", "NOT_FOUND");
			// NEU only checks FOUND: a crystal currently placed in the Crystal Nucleus showed as not found.
			boolean found = state.equals("FOUND") || state.equals("PLACED");
			String name = crystal.getValue() + capitalize(crystal.getKey());
			int cx = guiLeft + rectStartX + col * columnOffset, cy = guiTop + rectStartY + row * rowHeight;
			Draw.aligned(g, name + ":", "§f" + (found ? "§a✔" : "§c✖"), cx, cy, columnWidth);
			if (mouseX >= cx && mouseX < cx + columnWidth && mouseY >= cy && mouseY < cy + rowHeight - 1) {
				screen.tooltipToDisplay = PvWidgets.lines(name + " Crystal",
					"§7State: " + (state.equals("PLACED") ? "§bPlaced" : found ? "§aFound" : "§cNot found"),
					"§7Times found: §f" + Fmt.number(J.dbl(core, path + "total_found", 0)),
					"§7Times placed: §f" + Fmt.number(J.dbl(core, path + "total_placed", 0)));
			}
			idx++;
		}
		Draw.aligned(g, "§9Nucleus Runs Completed:", "§f" + MiningHotmLayout.formatNumber(nucleusRuns), guiLeft + rectStartX,
			guiTop + rectStartY + originalRectYSize - padding - 1.5f * Draw.font().lineHeight, rectXSize);
	}

	private static List<Component> powderTooltip(String color, String name, double available, double spent) {
		List<Component> tt = new ArrayList<>();
		tt.add(Component.literal(color + name + " Powder"));
		tt.add(Component.literal("§7Available: " + color + Fmt.number(available)));
		tt.add(Component.literal("§7Spent: " + color + Fmt.number(spent)));
		tt.add(Component.literal("§7Total: " + color + Fmt.number(available + spent)));
		return tt;
	}

	private static String capitalize(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
	}

	/** Exact NEU StringUtils.shortNumberFormat (lower-case k/m/b/t suffixes, truncated). */
	static String shortNumber(double n) {
		return shortNumber(n, 0);
	}

	private static final char[] SIZE_SUFFIX = {'k', 'm', 'b', 't'};

	private static String shortNumber(double n, int iteration) {
		if (n < 0) return "-" + shortNumber(-n, iteration);
		if (n < 1000) return n % 1 == 0 ? Integer.toString((int) n) : String.format(Locale.US, "%.2f", n);
		double d = ((long) n / 100) / 10.0;
		boolean isRound = (d * 10) % 10 == 0;
		return d < 1000 ? (isRound || d > 9.99 ? String.valueOf((int) d * 10 / 10) : String.valueOf(d)) + SIZE_SUFFIX[iteration]
			: shortNumber(d, iteration + 1);
	}

	@Override
	public void resetCache() {
		tree.resetCache();
	}
}
