/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * New in this port (not in NEU 2.6.0): second page of the Garden tab, drawn in NEU's Garden style.
 */
package dev.neupv.gui;

import com.google.gson.JsonObject;
import dev.neupv.item.LegacyItems;
import dev.neupv.item.PvItem;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.util.Fmt;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Garden "Contests" page: Jacob's contests per crop, medals and Anita perks, farming toolkit and greenhouse.
 * Everything comes from the profile member json, so it works even when the garden endpoint is unreachable.
 */
public class GardenContestsPage extends PvPage {
	private static final Identifier BACKGROUND = Draw.tex("gui/profile_viewer/garden/contests_background.png");
	private static final String[] RARITY_COLOURS = {"§f", "§a", "§9", "§5", "§6", "§d", "§c", "§c", "§b"};
	private static final String[] RARITY_NAMES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "SPECIAL", "VERY SPECIAL", "DIVINE"};

	private GardenContests data;
	private JsonObject parsedMember;
	private List<String> mutationGrid;
	private final Map<String, PvItem> repoItems = new HashMap<>();
	private final Map<String, ItemStack> stacks = new HashMap<>();
	private int mouseX, mouseY;

	public GardenContestsPage(ProfileViewerScreen screen) {
		super(screen);
	}

	/** Parsed data of the selected profile (null if no profile); also used by the tests. */
	public GardenContests data() {
		SkyblockProfiles.SkyblockProfile selected = getSelectedProfile();
		if (selected == null) return null;
		JsonObject member = selected.getProfileJson();
		if (member != parsedMember || data == null) {
			data = GardenContests.parse(member == null ? new JsonObject() : member);
			parsedMember = member;
			mutationGrid = null;
		}
		return data;
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		GardenContests d = data();
		if (d == null) return;
		Draw.full(g, BACKGROUND, screen.guiLeft(), screen.guiTop(), ProfileViewerScreen.SIZE_X, ProfileViewerScreen.SIZE_Y);
		renderCrops(g, d);
		renderToolkit(g, d);
		renderMedals(g, d);
		renderGreenhouse(g, d);
	}

	// ---------------- Jacob's contests, per crop ----------------

	private void renderCrops(GuiGraphicsExtractor g, GardenContests d) {
		int left = screen.guiLeft() + 25, top = screen.guiTop() + 10;
		shadowed(g, "§eJacob's Farming Contests", left + 110, top + 5, 210);
		GardenData.Crop[] crops = GardenData.Crop.values();
		for (int i = 0; i < crops.length; i++) {
			GardenData.Crop crop = crops[i];
			GardenContests.CropStats s = d.crops.get(crop);
			int x = left + 6 + (i / 7) * 110;
			int y = top + 18 + (i % 7) * 16;
			g.item(repoStack(crop.itemId), x, y);
			GardenContests.Bracket best = s.highestBracket();
			String value = s.personalBest > 0 ? (best == null ? "§7" : best.color) + Fmt.shortNumber(s.personalBest) : "§8-";
			Draw.aligned(g, "§e" + crop.displayName, value, x + 18, y + 4, 86);
			if (hover(x, y, 104, 16)) screen.tooltipToDisplay = PvWidgets.lines(cropTooltip(crop, s).toArray(String[]::new));
		}
	}

	private List<String> cropTooltip(GardenData.Crop crop, GardenContests.CropStats s) {
		List<String> tt = new ArrayList<>();
		tt.add("§a" + crop.displayName);
		tt.add("§7Personal Best: " + (s.personalBest > 0 ? "§e" + Fmt.number(s.personalBest) : "§8None"));
		GardenContests.Bracket best = s.highestBracket();
		tt.add("§7Highest Bracket: " + (best == null ? "§8None" : best.colored()));
		tt.add("");
		tt.add("§7Contests: §e" + Fmt.number(s.participated) + " §8(" + Fmt.number(s.claimed) + " claimed)");
		if (s.bestPosition > 0) {
			tt.add("§7Best Placement: §e#" + Fmt.number(s.bestPosition) + "§7 of §e" + Fmt.number(s.bestParticipants));
			if (s.bestPositionDate != null) tt.add("§8" + s.bestPositionDate);
		} else {
			tt.add("§7Best Placement: §8None");
		}
		tt.add("");
		tt.add("§7Medals Earned:");
		boolean any = false;
		GardenContests.Bracket[] brackets = GardenContests.Bracket.values();
		for (int b = brackets.length - 1; b >= 0; b--) {
			if (s.medals[b] == 0) continue;
			tt.add(" " + brackets[b].colored() + "§7: §f" + s.medals[b]);
			any = true;
		}
		if (!any) tt.add(" §8None");
		tt.add("");
		StringBuilder unique = new StringBuilder("§7Unique Brackets:");
		for (GardenContests.Bracket b : brackets) {
			unique.append(' ').append(s.uniqueBrackets[b.ordinal()] ? b.color : "§8").append(b.displayName.charAt(0));
		}
		tt.add(unique.toString());
		return tt;
	}

	// ---------------- Farming toolkit ----------------

	private void renderToolkit(GuiGraphicsExtractor g, GardenContests d) {
		int left = screen.guiLeft() + 25, top = screen.guiTop() + 149;
		shadowed(g, "§eFarming Toolkit", left + 110, top + 5, 210);
		if (!d.toolkitUnlocked && d.toolkit.isEmpty()) {
			Draw.centered(g, "§cNot unlocked", left + 110, top + 27, true);
			return;
		}
		if (d.toolkit.isEmpty()) {
			Draw.centered(g, "§7No tools stored", left + 110, top + 27, true);
			return;
		}
		int n = d.toolkit.size();
		int spacing = Math.min(18, 212 / n);
		int x = left + 1 + (221 - (n - 1) * spacing - 16) / 2;
		int y = top + 18;
		for (GardenContests.Tool tool : d.toolkit) {
			Draw.rect(g, ProfileViewerScreen.PV_ELEMENTS, x - 1, y - 1, 18, 18, 0, 20 / 256f, 0, 20 / 256f, 256, 256);
			ItemStack stack = repoStack(tool.id());
			g.item(stack.isEmpty() ? new ItemStack(Items.DIAMOND_HOE) : stack, x, y);
			if (hover(x - 1, y - 1, 18, 18)) screen.tooltipToDisplay = PvWidgets.lines(toolTooltip(tool).toArray(String[]::new));
			x += spacing;
		}
	}

	/**
	 * Full item lore of a toolkit tool. Hypixel only stores the tool's ExtraAttributes (no display name or lore), so
	 * the lore is rebuilt like the in-game one: the repo item's lore (description, base stats, ability, requirements)
	 * with the real level, counters, enchantments, gemstones, reforge and recombobulation filled in.
	 */
	private List<String> toolTooltip(GardenContests.Tool tool) {
		CompoundTag ea = tool.attributes();
		PvItem repo = repoItem(tool.id());
		String name = repo == null ? "§f" + GardenContests.prettyId(tool.id()) : repo.displayName;
		int baseRarity = repo == null ? -1 : repo.rarity();
		int upgrades = ea.getIntOr("rarity_upgrades", 0);
		int rarity = baseRarity < 0 ? -1 : Math.min(baseRarity + upgrades, RARITY_COLOURS.length - 1);
		String plain = Fmt.stripColor(name);
		String modifier = ea.getStringOr("modifier", null);
		String colour = rarity >= 0 ? RARITY_COLOURS[rarity] : name.startsWith("§") && name.length() > 1 ? name.substring(0, 2) : "§f";
		List<String> tt = new ArrayList<>();
		tt.add(colour + (modifier == null ? "" : GardenContests.prettyId(modifier) + " ") + plain);

		List<String> lore = repo == null ? new ArrayList<>(List.of("§8Farming Tool", "")) : new ArrayList<>(repo.lore);
		// the repo's rarity line ("EPIC FARMING TOOL") is re-added at the end, recombobulated
		String type = "";
		if (baseRarity >= 0 && !lore.isEmpty()) {
			String last = Fmt.stripColor(lore.get(lore.size() - 1)).trim();
			String oldRarity = RARITY_NAMES[Math.min(baseRarity, RARITY_NAMES.length - 1)];
			if (last.startsWith(oldRarity)) {
				type = last.substring(oldRarity.length());
				lore.remove(lore.size() - 1);
			}
		}
		boolean inserted = false;
		for (int i = 0; i < lore.size(); i++) {
			String line = lore.get(i), clean = Fmt.stripColor(line).trim();
			if (clean.equals("Right-click to view recipes!")) {
				if (i + 1 < lore.size() && Fmt.stripColor(lore.get(i + 1)).trim().isEmpty()) i++;
				continue;
			}
			if (clean.startsWith("Level ") && clean.contains("->")) {
				// the repo's "Level 1 -> 2 (0/1k)" line and its progress bar: replaced by the tool's real data
				if (i + 1 < lore.size() && lore.get(i + 1).contains("§m")) i++;
				if (i + 1 < lore.size() && Fmt.stripColor(lore.get(i + 1)).trim().isEmpty()) i++;
				addToolData(tt, ea, tool);
				inserted = true;
				continue;
			}
			if (modifier != null && clean.equals("This item can be reforged!")) continue;
			tt.add(line);
		}
		if (!inserted) {
			if (!tt.get(tt.size() - 1).isEmpty()) tt.add("");
			addToolData(tt, ea, tool);
		}
		while (tt.size() > 1 && tt.get(tt.size() - 1).isEmpty()) tt.removeLast();
		if (rarity >= 0) {
			String c = RARITY_COLOURS[rarity] + "§l";
			tt.add(upgrades > 0 ? c + "§ka§r " + c + RARITY_NAMES[rarity] + type + " §ka" : c + RARITY_NAMES[rarity] + type);
		}
		tt.add("");
		tt.add("§8Toolkit slot: " + tool.crop().displayName);
		return tt;
	}

	/** Level, counters, gemstones and enchantments of a tool, each block followed by a blank line. */
	private static void addToolData(List<String> tt, CompoundTag ea, GardenContests.Tool tool) {
		int level = ea.getIntOr("levelable_lvl", 0);
		double exp = ea.getDoubleOr("levelable_exp", 0);
		int overclocks = ea.getIntOr("levelable_overclocks", 0);
		boolean any = false;
		if (level > 0 || exp > 0) {
			tt.add("§7Level §a" + Math.max(level, 1)
				+ (overclocks > 0 ? " §8(+" + overclocks + " overclock" + (overclocks > 1 ? "s" : "") + ")" : "")
				+ (exp > 0 ? " §8(" + Fmt.number((long) exp) + " xp)" : ""));
			any = true;
		}
		long counter = ea.getLongOr("mined_crops", 0);
		if (counter > 0) {
			tt.add("§7Counter: §e" + Fmt.number(counter) + " " + tool.crop().displayName);
			any = true;
		}
		long cultivating = ea.getLongOr("farmed_cultivating", 0);
		if (cultivating > 0) {
			tt.add("§7Cultivating: §e" + Fmt.number(cultivating));
			any = true;
		}
		int ffd = ea.getIntOr("farming_for_dummies_count", 0);
		if (ffd > 0) {
			tt.add("§7Farming for Dummies: §e" + ffd + "§7/§a5");
			any = true;
		}
		if (any) tt.add("");

		String gems = gemstones(ea.getCompoundOrEmpty("gems"));
		if (gems != null) {
			tt.add(gems);
			tt.add("");
		}

		CompoundTag enchants = ea.getCompoundOrEmpty("enchantments");
		if (!enchants.isEmpty()) {
			List<String> names = new ArrayList<>(enchants.keySet());
			names.sort(Comparator.comparing((String e) -> !e.startsWith("ultimate_")).thenComparing(e -> e));
			StringBuilder line = new StringBuilder();
			int onLine = 0;
			for (String e : names) {
				boolean ultimate = e.startsWith("ultimate_");
				String pretty = GardenContests.prettyId(ultimate ? e.substring("ultimate_".length()) : e);
				String text = (ultimate ? "§d§l" : "§9") + pretty + " " + roman(enchants.getIntOr(e, 1));
				if (onLine == 2) {
					tt.add(line.toString());
					line.setLength(0);
					onLine = 0;
				}
				if (onLine > 0) line.append("§9, ");
				line.append(text);
				onLine++;
			}
			if (!line.isEmpty()) tt.add(line.toString());
			tt.add("");
		}
	}

	private static final Map<String, String> GEM_SYMBOLS = Map.ofEntries(
		Map.entry("RUBY", "§c❤"), Map.entry("AMBER", "§6⸕"), Map.entry("SAPPHIRE", "§b✎"), Map.entry("JADE", "§a☘"),
		Map.entry("AMETHYST", "§5❈"), Map.entry("TOPAZ", "§e✧"), Map.entry("JASPER", "§d❁"), Map.entry("OPAL", "§f❂"),
		Map.entry("PERIDOT", "§2☘"), Map.entry("ONYX", "§8☠"), Map.entry("CITRINE", "§4☘"), Map.entry("AQUAMARINE", "§3α"));
	private static final Map<String, String> QUALITY_COLOURS = Map.of(
		"ROUGH", "§f", "FLAWED", "§a", "FINE", "§9", "FLAWLESS", "§5", "PERFECT", "§6");

	/** Gemstone slots like Hypixel's "[☘] [☘]": bracket in the quality colour, grey when the slot is empty. */
	private static String gemstones(CompoundTag gems) {
		if (gems.isEmpty()) return null;
		Set<String> slots = new java.util.TreeSet<>();
		for (net.minecraft.nbt.Tag t : gems.getListOrEmpty("unlocked_slots")) {
			if (t instanceof net.minecraft.nbt.StringTag(String v)) slots.add(v);
		}
		for (String k : gems.keySet()) if (k.matches("[A-Z]+_\\d+")) slots.add(k);
		if (slots.isEmpty()) return null;
		StringBuilder sb = new StringBuilder();
		for (String slot : slots) {
			String kind = slot.substring(0, slot.lastIndexOf('_'));
			String gem = gems.getStringOr(slot + "_gem", kind);
			String symbol = GEM_SYMBOLS.getOrDefault(gem, "§f◆");
			String quality = null;
			net.minecraft.nbt.Tag v = gems.get(slot);
			if (v instanceof net.minecraft.nbt.StringTag(String q)) quality = q;
			else if (v instanceof CompoundTag c) quality = c.getStringOr("quality", null);
			if (!sb.isEmpty()) sb.append(' ');
			if (quality != null) {
				String qc = QUALITY_COLOURS.getOrDefault(quality, "§7");
				sb.append(qc).append('[').append(symbol).append(qc).append(']');
			} else {
				sb.append("§8[§7").append(Fmt.stripColor(symbol)).append("§8]");
			}
		}
		return "§7Gemstones: " + sb;
	}

	// ---------------- Medals and Anita perks ----------------

	private void renderMedals(GuiGraphicsExtractor g, GardenContests d) {
		int left = screen.guiLeft() + 258, top = screen.guiTop() + 10;
		shadowed(g, "§eMedals & Perks", left + 74, top + 5, 136);

		// vanilla ingots: the repo's medal heads are NEU shop placeholders that all look alike
		ItemStack[] icons = {new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.COPPER_INGOT)};
		String[] names = {"§6Gold", "§fSilver", "§cBronze"};
		int[] counts = {d.goldMedals, d.silverMedals, d.bronzeMedals};
		for (int i = 0; i < 3; i++) {
			int x = left + 8 + i * 46;
			int y = top + 16;
			g.item(icons[i], x, y);
			Draw.text(g, "§f" + counts[i], x + 18, y + 4, true);
			if (hover(x, y, 44, 16)) {
				screen.tooltipToDisplay = PvWidgets.lines(names[i] + " Medals", "§7In medal inventory: §f" + counts[i], "", "§8Spent at Jacob's shop.");
			}
		}

		int x = left + 6, y = top + 37, w = 136;
		int totalMedals = 0;
		GardenContests.Bracket[] brackets = GardenContests.Bracket.values();
		int[] perBracket = new int[brackets.length];
		for (GardenContests.CropStats s : d.crops.values()) {
			totalMedals += s.totalMedals();
			for (int b = 0; b < brackets.length; b++) perBracket[b] += s.medals[b];
		}
		alignedWithHover(g, "§eContests", "§f" + Fmt.number(d.totalContests), x, y, w,
			List.of("§7Contests joined: §e" + Fmt.number(d.totalContests), "§7Rewards claimed: §e" + Fmt.number(d.totalClaimed)));
		y += 11;
		List<String> medalTt = new ArrayList<>();
		medalTt.add("§7Brackets reached in claimed contests:");
		for (int b = brackets.length - 1; b >= 0; b--) medalTt.add(" " + brackets[b].colored() + "§7: §f" + Fmt.number(perBracket[b]));
		alignedWithHover(g, "§eBrackets Reached", "§f" + Fmt.number(totalMedals), x, y, w, medalTt);
		y += 11;
		alignedWithHover(g, "§eDouble Drops", perk(d.doubleDrops, GardenContests.MAX_DOUBLE_DROPS), x, y, w,
			List.of("§aExtra Farming Fortune", "§7Anita's perk: §6+" + d.doubleDrops * 4 + "☘ Farming Fortune", "§7Tier: §e" + d.doubleDrops + "§7/§a" + GardenContests.MAX_DOUBLE_DROPS));
		y += 11;
		alignedWithHover(g, "§eFarming Level Cap", perk(d.farmingLevelCap, GardenContests.MAX_LEVEL_CAP), x, y, w,
			List.of("§aFarming Level Cap", "§7Max Farming level: §e" + (50 + d.farmingLevelCap), "§7Tier: §e" + d.farmingLevelCap + "§7/§a" + GardenContests.MAX_LEVEL_CAP));
		y += 11;
		alignedWithHover(g, "§ePersonal Bests", d.personalBestsUnlocked ? "§aUnlocked" : "§cLocked", x, y, w,
			List.of("§aPersonal Bests", "§7Tracks your best score per crop.", d.personalBestsUnlocked ? "§aUnlocked" : "§cNot unlocked"));
	}

	private static String perk(int value, int max) {
		return (value >= max ? "§6" : "§f") + value + "§7/§a" + max;
	}

	// ---------------- Greenhouse ----------------

	private void renderGreenhouse(GuiGraphicsExtractor g, GardenContests d) {
		int left = screen.guiLeft() + 258, top = screen.guiTop() + 104;
		shadowed(g, "§eGreenhouse", left + 74, top + 5, 136);
		List<String> grid = mutationGrid(d);
		Set<String> discovered = upper(d.discoveredGreenhouse), analyzed = upper(d.analyzedGreenhouse);
		int x = left + 6, w = 136;
		alignedWithHover(g, "§eDiscovered", "§f" + d.discoveredGreenhouse.size(), x, top + 16, w, nameList("§aDiscovered Crops", d.discoveredGreenhouse));
		int analyzedMutations = 0;
		for (String id : grid) if (analyzed.contains(id)) analyzedMutations++;
		alignedWithHover(g, "§eAnalyzed", (analyzedMutations >= grid.size() ? "§6" : "§f") + analyzedMutations + "§7/§a" + grid.size(), x, top + 27, w,
			nameList("§aAnalyzed Crops", d.analyzedGreenhouse));

		int cols = 10;
		for (int i = 0; i < grid.size(); i++) {
			String id = grid.get(i);
			int ix = left + 5 + (i % cols) * 14;
			int iy = top + 40 + (i / cols) * 11;
			boolean isDiscovered = discovered.contains(id) || analyzed.contains(id);
			ItemStack stack = isDiscovered ? repoStack(id) : new ItemStack(Items.FIREWORK_STAR);
			Draw.item(g, stack.isEmpty() ? new ItemStack(Items.SHORT_GRASS) : stack, ix, iy, 0.625f);
			if (hover(ix, iy, 11, 10)) {
				PvItem repo = repoItem(id);
				int rarity = repo == null ? -1 : repo.rarity();
				String name = repo == null ? "§f" + GardenContests.prettyId(id) : repo.displayName;
				List<String> tt = new ArrayList<>();
				tt.add(isDiscovered ? name : "§c???");
				if (rarity >= 0) tt.add(RARITY_COLOURS[rarity] + "§l" + RARITY_NAMES[rarity] + " MUTATION");
				tt.add("");
				tt.add(analyzed.contains(id) ? "§aAnalyzed" : isDiscovered ? "§eDiscovered, not analyzed" : "§cNot discovered");
				screen.tooltipToDisplay = PvWidgets.lines(tt.toArray(String[]::new));
			}
		}
	}

	/** Known mutations + the player's analyzed ones (always mutations), sorted by rarity then name. */
	private List<String> mutationGrid(GardenContests d) {
		if (mutationGrid != null) return mutationGrid;
		Set<String> ids = new LinkedHashSet<>(GardenContests.MUTATIONS);
		ids.addAll(upper(d.analyzedGreenhouse));
		List<String> out = new ArrayList<>(ids);
		out.sort(Comparator.comparingInt((String id) -> {
			PvItem item = repoItem(id);
			return item == null ? 99 : item.rarity();
		}).thenComparing(id -> id));
		mutationGrid = out;
		return out;
	}

	private List<String> nameList(String title, List<String> ids) {
		List<String> tt = new ArrayList<>();
		tt.add(title + " §7(" + ids.size() + ")");
		if (ids.isEmpty()) {
			tt.add("§8None");
			return tt;
		}
		List<String> names = new ArrayList<>();
		for (String id : ids) {
			PvItem item = repoItem(id.toUpperCase(Locale.ROOT));
			names.add(item == null ? "§7" + GardenContests.prettyId(id) : item.displayName);
		}
		names.sort(Comparator.comparing(Fmt::stripColor));
		// three per line: 40+ names would not fit on the screen one per line
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < names.size(); i++) {
			if (i % 3 != 0) line.append("§7, ");
			line.append(names.get(i));
			if (i % 3 == 2 || i == names.size() - 1) {
				tt.add(line.toString());
				line.setLength(0);
			}
		}
		return tt;
	}

	private static Set<String> upper(List<String> ids) {
		Set<String> out = new LinkedHashSet<>();
		for (String id : ids) out.add(id.toUpperCase(Locale.ROOT));
		return out;
	}

	// ---------------- helpers ----------------

	private PvItem repoItem(String id) {
		if (repoItems.containsKey(id)) return repoItems.get(id);
		PvItem item = LegacyItems.fromRepo(id);
		repoItems.put(id, item);
		return item;
	}

	private ItemStack repoStack(String id) {
		return stacks.computeIfAbsent(id, k -> {
			PvItem item = repoItem(k);
			return item == null ? ItemStack.EMPTY : item.stack();
		});
	}

	private boolean hover(int x, int y, int w, int h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	private static void shadowed(GuiGraphicsExtractor g, String s, int x, int y, int maxLength) {
		Draw.centeredMaxWidth(g, s, x, y + 4, true, maxLength, 0xFFFFFFFF);
	}

	private void alignedWithHover(GuiGraphicsExtractor g, String first, String second, int x, int y, int length, List<String> hover) {
		Draw.aligned(g, first, second, x, y, length);
		if (hover(x, y - 1, length, 10)) screen.tooltipToDisplay = PvWidgets.lines(hover.toArray(String[]::new));
	}

	private static String roman(int n) {
		String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
	}

	@Override
	public void resetCache() {
		data = null;
		parsedMember = null;
		mutationGrid = null;
	}
}
