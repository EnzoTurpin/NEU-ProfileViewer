/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Not in NEU: Hunting skill, Attribute Shards and Safari (Hypixel updates after NEU 2.6.0).
 * Laid out like NEU's Heart of the Mountain page (same background panels, fonts, bars and slots).
 */
package dev.neupv.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.item.LegacyItems;
import dev.neupv.item.PvItem;
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

/** "Hunting" tab: Hunting skill + shard hunts, Safari, and the Attribute Shards grid. */
public class HuntingPage extends PvPage {
	private static final Identifier BACKGROUND = Draw.tex("gui/mining/background.png");
	private static final Identifier SLOT_BACKGROUND = Draw.tex("gui/mining/perk_background.png");
	private static final String[] ROMANS = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
	private static final String[][] HUNTS = {
		{"shard_combat_hunts", "§cCombat"}, {"shard_fishing_hunts", "§bFishing"},
		{"shard_forest_hunts", "§2Forest"}, {"shard_trap_hunts", "§6Trap"},
		{"shard_salt_hunts", "§fSalt"}};
	private static final Map<String, String> BIOMES = new LinkedHashMap<>();
	private static final List<String> TICKETS = List.of("economy", "basic", "premium", "first_class");

	static {
		BIOMES.put("cavern", "§6");
		BIOMES.put("forest", "§2");
		BIOMES.put("haunted", "§5");
		BIOMES.put("icy", "§b");
	}

	// right panel (Attribute Shards grid): pv_elements 20x20 slots, like NEU's collections
	private static final int GRID_COLS = 8, GRID_ROWS = 7, SLOT = 20;
	private static final int GRID_X = 250, GRID_Y = 32;
	private static final int PER_PAGE = GRID_COLS * GRID_ROWS;

	private static ItemStack huntingIcon, shardIcon;
	private HuntingData.Info info;
	private SkyblockProfiles.SkyblockProfile infoFor;
	private int page = 0;

	public HuntingPage(ProfileViewerScreen screen) {
		super(screen);
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		if (huntingIcon == null) {
			huntingIcon = LegacyItems.icon(Items.LEAD, "§3Hunting");
			shardIcon = LegacyItems.icon(Items.PRISMARINE_SHARD, "§dShards");
		}
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		Draw.full(g, BACKGROUND, guiLeft, guiTop, ProfileViewerScreen.SIZE_X, ProfileViewerScreen.SIZE_Y);

		SkyblockProfiles.SkyblockProfile selected = getSelectedProfile();
		if (selected == null) return;
		JsonObject member = selected.getProfileJson();
		if (infoFor != selected) {
			info = HuntingData.compute(member);
			infoFor = selected;
			page = 0;
		}

		Map<String, Level> levelingInfo = selected.getLevelingInfo();
		Level hunting = levelingInfo == null ? null : levelingInfo.get("hunting");
		if (hunting != null) PvWidgets.renderXpBar(screen, g, "§3Hunting", huntingIcon, guiLeft + 23, guiTop + 25, 110, hunting, mouseX, mouseY);

		drawShardSummary(g, member, mouseX, mouseY);
		drawHunts(g, member, mouseX, mouseY);
		drawSafari(g, J.objOrEmpty(member, "safari"), mouseX, mouseY);
		drawAttributes(g, mouseX, mouseY);
	}

	/** Next to the skill bar (where the HOTM page has its forge slot): fused / unique shards. */
	private void drawShardSummary(GuiGraphicsExtractor g, JsonObject member, int mouseX, int mouseY) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		int x = guiLeft + 149, y = guiTop + 20;
		Draw.rect(g, SLOT_BACKGROUND, x, y, 16, 16, 0, 1, 0, 1, 20, 20);
		g.item(shardIcon, x, y);
		int unique = J.i(member, "player_stats.unique_shards", 0);
		int fused = info == null ? J.i(member, "shards.fused", 0) : info.fused;
		Draw.aligned(g, "§dUnique:", "§f" + Fmt.number(unique), guiLeft + 170, guiTop + 19, 58);
		Draw.aligned(g, "§dFused:", "§f" + Fmt.number(fused), guiLeft + 170, guiTop + 29, 58);
		if (mouseX >= x && mouseX < guiLeft + 230 && mouseY >= guiTop + 17 && mouseY < guiTop + 40) {
			List<Component> tt = new ArrayList<>();
			tt.add(Component.literal("§dShards"));
			tt.add(Component.literal("§7Unique shards caught: §f" + Fmt.number(unique)));
			tt.add(Component.literal("§7Shards fused: §f" + Fmt.number(fused)));
			if (info != null && !info.owned.isEmpty()) {
				tt.add(Component.literal(""));
				tt.add(Component.literal("§7In the Hunting Box: §f" + Fmt.number(info.ownedTotal) + " §7(" + info.owned.size() + " types)"));
				List<Map.Entry<String, Integer>> owned = new ArrayList<>(info.owned.entrySet());
				owned.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
				int shown = 0;
				for (Map.Entry<String, Integer> e : owned) {
					if (shown++ == 12) {
						tt.add(Component.literal("§8...and " + (owned.size() - 12) + " more"));
						break;
					}
					tt.add(Component.literal(" §8- §f" + titleCase(e.getKey()) + "§7: §e" + Fmt.number(e.getValue())));
				}
			}
			screen.tooltipToDisplay = tt;
		}
	}

	/** Middle panel: hunts per hunting type. */
	private void drawHunts(GuiGraphicsExtractor g, JsonObject member, int mouseX, int mouseY) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		int x = guiLeft + 20, columnWidth = 101, columnOffset = 105;
		double total = 0;
		for (int i = 0; i < HUNTS.length + 1; i++) {
			int cx = x + (i % 2) * columnOffset, cy = guiTop + 54 + (i / 2) * 11;
			String label;
			double value;
			if (i < HUNTS.length) {
				value = J.dbl(member, "player_stats." + HUNTS[i][0], 0);
				total += value;
				label = HUNTS[i][1] + ":";
			} else {
				value = total;
				label = "§eTotal Hunts:";
			}
			Draw.aligned(g, label, "§f" + Fmt.number(value), cx, cy, columnWidth);
			if (hover(mouseX, mouseY, cx, cy - 1, columnWidth, 11)) {
				screen.tooltipToDisplay = i < HUNTS.length
					? PvWidgets.lines(HUNTS[i][1] + " Shard Hunts", "§7Hunts: §f" + Fmt.number(value))
					: PvWidgets.lines("§eShard Hunts", "§7Total hunts: §f" + Fmt.number(value),
					"§7Unique shards caught: §f" + Fmt.number(J.dbl(member, "player_stats.unique_shards", 0)));
			}
		}
	}

	/** Bottom panel: Safari captures, critters, milestones and tickets. */
	private void drawSafari(GuiGraphicsExtractor g, JsonObject safari, int mouseX, int mouseY) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		int x = guiLeft + 20, columnWidth = 101, columnOffset = 105, top = guiTop + 103, rowHeight = 12;
		int rightX = x + columnOffset;

		JsonObject captures = J.objOrEmpty(safari, "biome_captures");
		JsonObject tiers = J.objOrEmpty(safari, "milestone_claimed_tiers");
		Map<String, String> biomes = new LinkedHashMap<>(BIOMES);
		for (String k : captures.keySet()) biomes.putIfAbsent(k, "§a");
		for (String k : tiers.keySet()) biomes.putIfAbsent(k, "§a");

		Draw.centered(g, "§2Safari Captures", x + columnWidth / 2f, top + 4, true);
		int row = 1;
		double totalCaptures = 0;
		int totalTiers = 0;
		for (Map.Entry<String, String> b : biomes.entrySet()) {
			double n = J.dbl(captures, b.getKey(), 0);
			int tier = J.i(tiers, b.getKey(), 0);
			totalCaptures += n;
			totalTiers += tier;
			if (row > 4) continue; // only room for 4 biomes, the total still counts the others
			int cy = top + row * rowHeight;
			String name = b.getValue() + titleCase(b.getKey());
			Draw.aligned(g, name + ":", "§f" + Fmt.number(n), x, cy, columnWidth);
			if (hover(mouseX, mouseY, x, cy, columnWidth, rowHeight)) {
				screen.tooltipToDisplay = PvWidgets.lines(name, "§7Captures: §f" + Fmt.number(n), "§7Milestone tier claimed: §e" + tier);
			}
			row++;
		}
		int totalY = top + 5 * rowHeight;
		Draw.aligned(g, "§eTotal:", "§f" + Fmt.number(totalCaptures), x, totalY, columnWidth);
		if (hover(mouseX, mouseY, x, totalY, columnWidth, rowHeight)) {
			List<Component> tt = new ArrayList<>();
			tt.add(Component.literal("§eSafari Milestones"));
			for (String b : biomes.keySet()) {
				tt.add(Component.literal(biomes.get(b) + titleCase(b) + "§7: tier §e" + J.i(tiers, b, 0)));
			}
			tt.add(Component.literal("§7Total tiers claimed: §e" + totalTiers));
			screen.tooltipToDisplay = tt;
		}

		Draw.centered(g, "§2Critters", rightX + columnWidth / 2f, top + 4, true);
		List<String> critters = strings(safari, "discovered_critters");
		List<String> sparkling = strings(safari, "discovered_sparkling_critters");
		int y = top + rowHeight;
		Draw.aligned(g, "§aDiscovered:", "§f" + critters.size(), rightX, y, columnWidth);
		if (hover(mouseX, mouseY, rightX, y, columnWidth, rowHeight)) screen.tooltipToDisplay = listTooltip("§aDiscovered Critters", critters, "§f");
		y += rowHeight;
		Draw.aligned(g, "§dSparkling:", "§f" + sparkling.size(), rightX, y, columnWidth);
		if (hover(mouseX, mouseY, rightX, y, columnWidth, rowHeight)) screen.tooltipToDisplay = listTooltip("§dSparkling Critters", sparkling, "§d");
		y += rowHeight;
		Draw.aligned(g, "§dSparkling Caught:", "§f" + Fmt.number(J.dbl(safari, "total_captured_sparkling_critters", 0)), rightX, y, columnWidth);
		y += rowHeight;
		Draw.aligned(g, "§eMilestones:", "§f" + totalTiers, rightX, y, columnWidth);
		if (hover(mouseX, mouseY, rightX, y, columnWidth, rowHeight)) {
			List<Component> tt = new ArrayList<>();
			tt.add(Component.literal("§eSafari Milestones"));
			for (String b : biomes.keySet()) tt.add(Component.literal(biomes.get(b) + titleCase(b) + "§7: tier §e" + J.i(tiers, b, 0)));
			screen.tooltipToDisplay = tt;
		}
		y += rowHeight;
		JsonObject tickets = J.objOrEmpty(safari, "tickets");
		double totalTickets = 0;
		for (String k : tickets.keySet()) totalTickets += J.dbl(tickets, k, 0);
		Draw.aligned(g, "§9Tickets:", "§f" + Fmt.number(totalTickets), rightX, y, columnWidth);
		if (hover(mouseX, mouseY, rightX, y, columnWidth, rowHeight)) {
			List<Component> tt = new ArrayList<>();
			tt.add(Component.literal("§9Safari Tickets"));
			List<String> keys = new ArrayList<>(TICKETS);
			for (String k : tickets.keySet()) if (!keys.contains(k)) keys.add(k);
			for (String k : keys) tt.add(Component.literal("§7" + titleCase(k) + ": §f" + Fmt.number(J.dbl(tickets, k, 0))));
			screen.tooltipToDisplay = tt;
		}
	}

	/** Right panel: one pv_elements slot per attribute, filled orange by level (gold when maxed). */
	private void drawAttributes(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		int centerX = guiLeft + 330;
		if (info == null) {
			Draw.centered(g, "§cMissing repo data", centerX, guiTop + 100, true);
			return;
		}
		int pages = Math.max(1, (info.attributes.size() + PER_PAGE - 1) / PER_PAGE);
		page = Math.max(0, Math.min(page, pages - 1));

		String title = "§3Attributes " + (info.unlocked >= info.possible ? "§6" : "§f") + info.unlocked + "/" + info.possible;
		Draw.centered(g, title, centerX, guiTop + 24, true);
		int titleW = Draw.font().width(title);
		if (hover(mouseX, mouseY, centerX - titleW / 2, guiTop + 19, titleW, 10)) {
			screen.tooltipToDisplay = PvWidgets.lines("§3Attribute Shards",
				"§7Unlocked: §f" + info.unlocked + "§7/§f" + info.possible,
				"§7Maxed: §6" + info.maxed + "§7/§f" + info.possible,
				"§7Total levels: §f" + Fmt.number(info.totalLevels),
				"§7Shards absorbed: §f" + Fmt.number(info.totalStacks),
				"§7Shards fused: §f" + Fmt.number(info.fused),
				"", "§eScroll or use the arrows to change page");
		}
		if (pages > 1) {
			boolean leftHover = page > 0 && hover(mouseX, mouseY, guiLeft + 253, guiTop + 16, 12, 16);
			boolean rightHover = page < pages - 1 && hover(mouseX, mouseY, guiLeft + 395, guiTop + 16, 12, 16);
			if (page > 0) CollectionsPage.drawArrow(g, guiLeft + 253, guiTop + 16, false, leftHover);
			if (page < pages - 1) CollectionsPage.drawArrow(g, guiLeft + 395, guiTop + 16, true, rightHover);
			Draw.centered(g, "§fPage " + (page + 1) + "/" + pages, centerX, guiTop + 178, true);
		}

		int start = page * PER_PAGE;
		for (int i = 0; i < PER_PAGE && start + i < info.attributes.size(); i++) {
			HuntingData.Attribute a = info.attributes.get(start + i);
			int sx = guiLeft + GRID_X + (i % GRID_COLS) * SLOT, sy = guiTop + GRID_Y + (i / GRID_COLS) * SLOT;
			float progress = a.maxLevel <= 0 ? (a.level > 0 ? 1 : 0) : a.level / (float) a.maxLevel;
			CollectionsPage.drawProgressSlot(g, sx, sy, progress);
			ItemStack icon = a.icon();
			g.item(icon, sx + 2, sy + 2);
			if (a.stacks <= 0) {
				g.fill(sx + 2, sy + 2, sx + 18, sy + 18, 0xA0303030); // not unlocked: dimmed icon
			} else if (!a.maxed()) {
				g.itemDecorations(Draw.font(), icon, sx + 2, sy + 2, String.valueOf(a.level)); // maxed = full gold slot
			}
			if (hover(mouseX, mouseY, sx, sy, SLOT, SLOT)) screen.tooltipToDisplay = attributeTooltip(a);
		}
	}

	private static List<Component> attributeTooltip(HuntingData.Attribute a) {
		List<Component> tt = new ArrayList<>();
		String color = HuntingData.rarityColor(a.rarity);
		tt.add(Component.literal(color + a.shardName + " Shard"));
		tt.add(Component.literal("§6" + a.abilityName + (a.level > 0 ? " " + roman(a.level) : "")));
		PvItem repo = a.repoItem();
		if (repo != null && repo.lore.size() > 1) {
			// effect description of the repo item (written for level I), up to the first blank line
			for (int i = 1; i < repo.lore.size() && !Fmt.stripColor(repo.lore.get(i)).isBlank(); i++) {
				tt.add(Component.literal(repo.lore.get(i)));
			}
			if (a.level != 1) tt.add(Component.literal("§8(values shown for level I)"));
		}
		tt.add(Component.literal(""));
		if (a.stacks <= 0) {
			tt.add(Component.literal("§cNot unlocked"));
		} else if (a.maxed()) {
			tt.add(Component.literal("§7Level: §6" + a.level + "§7/§6" + a.maxLevel + " §6§lMAXED"));
		} else {
			tt.add(Component.literal("§7Level: §e" + a.level + "§7/§6" + a.maxLevel));
		}
		if (a.maxLevel > 0) {
			tt.add(Component.literal("§7Shards absorbed: §f" + Fmt.number(a.stacks) + "§7/§f" + Fmt.number(a.stacksForMax)));
			if (!a.maxed() && a.stacksForNext > 0) {
				tt.add(Component.literal("§7Next level: §e" + Fmt.number(a.stacksForNext - a.stacks) + " §7more shards"));
			}
		} else {
			tt.add(Component.literal("§7Shards absorbed: §f" + Fmt.number(a.stacks)));
		}
		tt.add(Component.literal("§7In the Hunting Box: §f" + Fmt.number(a.owned)));
		if (a.alignment != null) tt.add(Component.literal("§7Alignment: §f" + a.alignment));
		if (!a.family.isEmpty()) tt.add(Component.literal("§7Family: §f" + String.join(", ", a.family)));
		tt.add(Component.literal(""));
		tt.add(Component.literal(color + "§l" + a.rarity + " SHARD" + (a.shardId != null ? " §8(ID " + a.shardId + ")" : "")));
		return tt;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if ((button != 0 && button != 1) || info == null) return false;
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		int pages = Math.max(1, (info.attributes.size() + PER_PAGE - 1) / PER_PAGE);
		if (page > 0 && hover(mouseX, mouseY, guiLeft + 253, guiTop + 16, 12, 16)) {
			page--;
			CollectionsPage.playPressSound();
			return true;
		}
		if (page < pages - 1 && hover(mouseX, mouseY, guiLeft + 395, guiTop + 16, 12, 16)) {
			page++;
			CollectionsPage.playPressSound();
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (info == null || vertical == 0) return false;
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		if (!hover(mouseX, mouseY, guiLeft + 249, guiTop + 16, 163, 169)) return false;
		int pages = Math.max(1, (info.attributes.size() + PER_PAGE - 1) / PER_PAGE);
		page = Math.max(0, Math.min(pages - 1, page + (vertical < 0 ? 1 : -1)));
		return true;
	}

	@Override
	public void resetCache() {
		info = null;
		infoFor = null;
	}

	/** Test hook: the attributes as shown (sorted). */
	public HuntingData.Info info() {
		return info;
	}

	public int page() {
		return page;
	}

	private static boolean hover(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private static List<String> strings(JsonObject o, String key) {
		List<String> out = new ArrayList<>();
		for (JsonElement e : J.arrOrEmpty(o, key)) if (e.isJsonPrimitive()) out.add(e.getAsString());
		return out;
	}

	/** Names joined a few per line, so a long list stays a reasonable tooltip height. */
	private static List<Component> listTooltip(String title, List<String> ids, String color) {
		List<Component> tt = new ArrayList<>();
		tt.add(Component.literal(title + " §7(" + ids.size() + ")"));
		if (ids.isEmpty()) {
			tt.add(Component.literal("§8None"));
			return tt;
		}
		List<String> names = new ArrayList<>();
		for (String id : ids) names.add(titleCase(id));
		names.sort(String::compareToIgnoreCase);
		StringBuilder line = new StringBuilder();
		for (String n : names) {
			if (line.length() > 0 && Fmt.stripColor(line.toString()).length() + n.length() + 2 > 44) {
				tt.add(Component.literal(line + "§7,"));
				line = new StringBuilder();
			}
			if (line.length() > 0) line.append("§7, ");
			line.append(color).append(n);
		}
		if (line.length() > 0) tt.add(Component.literal(line.toString()));
		return tt;
	}

	static String titleCase(String s) {
		StringBuilder sb = new StringBuilder();
		for (String w : s.toLowerCase(Locale.ROOT).split("_")) {
			if (w.isEmpty()) continue;
			if (sb.length() > 0) sb.append(' ');
			sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return sb.toString();
	}

	private static String roman(int n) {
		return n >= 1 && n <= ROMANS.length ? ROMANS[n - 1] : String.valueOf(n);
	}
}
