/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Not in NEU: Heart of the Forest (Galatea, 2025), laid out like NEU's MiningPage (LGPL-3.0-or-later).
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.item.LegacyItems;
import dev.neupv.pv.Level;
import dev.neupv.pv.SkillTree;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.Fmt;
import dev.neupv.util.J;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * "Heart of the Forest" sub-page of the HOTM tab: HOTF level, Forest/Desert Whispers, the perk tree from
 * constants/hotflayout.json, and the Galatea foraging data that has no other page (tree gifts, Starlyn personal bests,
 * fish family, temples, Hina tasks, harp songs, honey, daily counters).
 */
public class MiningForestPage {
	private static final Identifier BACKGROUND = Draw.tex("gui/mining/background.png");
	private static final Identifier SLOT_BACKGROUND = Draw.tex("gui/mining/perk_background.png");
	private static final String[][] WHISPERS = {{"forest", "§2", "Forest"}, {"desert", "§6", "Desert"}};
	private static final String[][] GIFT_TREES = {{"FIG", "§a", "Fig"}, {"MANGROVE", "§2", "Mangrove"}, {"HELIX", "§6", "Helix"}};
	private static final Map<String, String[]> STARLYN = new LinkedHashMap<>();
	private static final Map<String, String> SONGS = new LinkedHashMap<>();

	static {
		STARLYN.put("FIG_LOG", new String[]{"§a", "Fig Log"});
		STARLYN.put("MANGROVE_LOG", new String[]{"§2", "Mangrove Log"});
		STARLYN.put("HELIX_LOG", new String[]{"§6", "Helix Log"});
		STARLYN.put("agatha", new String[]{"§d", "Agatha"});
		STARLYN.put("miria", new String[]{"§b", "Miria"});
		SONGS.put("hymn_joy", "Hymn to the Joy");
		SONGS.put("frere_jacques", "Frère Jacques");
		SONGS.put("amazing_grace", "Amazing Grace");
		SONGS.put("brahms", "Brahm's Lullaby");
		SONGS.put("happy_birthday", "Happy Birthday to You");
		SONGS.put("greensleeves", "Greensleeves");
		SONGS.put("jeopardy", "Geothermy?");
		SONGS.put("minuet", "Minuet");
		SONGS.put("joy_world", "Joy to the World");
		SONGS.put("pure_imagination", "Godly Imagination");
		SONGS.put("vie_en_rose", "La Vie en Rose");
		SONGS.put("fire_and_flames", "Through the Campfire");
		SONGS.put("pachelbel", "Pachelbel");
	}

	private static ItemStack hotfSkillIcon, loadoutIcon;
	private final ProfileViewerScreen screen;
	final MiningHotmTree tree = new MiningHotmTree("hotf");
	int treeLeft, treeTop, treeRight, treeBottom;

	MiningForestPage(ProfileViewerScreen screen) {
		this.screen = screen;
	}

	static ItemStack sideButtonIcon() {
		return LegacyItems.rename(new ItemStack(Items.OAK_SAPLING), "§2Heart of the Forest");
	}

	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		if (hotfSkillIcon == null) {
			hotfSkillIcon = new ItemStack(Items.IRON_AXE);
			loadoutIcon = new ItemStack(Items.OAK_SAPLING);
		}
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		Draw.full(g, BACKGROUND, guiLeft, guiTop, ProfileViewerScreen.SIZE_X, ProfileViewerScreen.SIZE_Y);

		SkyblockProfiles.SkyblockProfile selected = screen.getSelectedProfile();
		if (selected == null) return;
		JsonObject member = selected.getProfileJson();
		JsonObject core = J.objOrEmpty(member, "foraging_core");
		JsonObject foraging = J.objOrEmpty(member, "foraging");
		int slot = SkillTree.hotfSlotNumber(member);

		// HOTF level (same place and bar as HOTM; rainbow when maxed)
		int x = guiLeft + 23, y = guiTop + 25, sectionWidth = 110;
		JsonObject leveling = NeuRepo.leveling();
		Level hotf = leveling == null ? null : SkillTree.hotfLevel(member, leveling);
		if (hotf != null) {
			PvWidgets.renderXpBar(screen, g, "§2HOTF", hotfSkillIcon, x, y, sectionWidth, hotf, mouseX, mouseY);
			// renderXpBar shows the overflow level for anything but HOTM; HOTF has no overflow either
			if (hotf.maxed && mouseX > x && mouseX < x + 120 && mouseY > y - 4 && mouseY < y + 13) {
				screen.tooltipToDisplay = PvWidgets.lines("§6MAXED!", "§7Total XP: §2" + Fmt.number(hotf.totalXp));
			}
		} else {
			Draw.text(g, "§cMissing repo data", x, y, true);
		}

		// Whispers, like the powder table
		float xStart = 22, yStartTop = 27;
		int[] colX = {55, 125};
		Draw.aligned(g, "§fWhispers:", "", guiLeft + xStart, guiTop + yStartTop + 34, 115);
		Draw.aligned(g, "§fTotal:", "", guiLeft + xStart, guiTop + yStartTop + 44, 115);
		for (int i = 0; i < WHISPERS.length; i++) {
			String key = WHISPERS[i][0], color = WHISPERS[i][1];
			double total = J.dbl(core, "whispers." + key + ".total", 0);
			double spent = J.dbl(core, "whispers." + key + "." + slot + ".spent", 0);
			// "total" is everything earned; every loadout spends from it independently, like HOTM powder
			double available = Math.max(0, total - spent);
			Draw.aligned(g, color + WHISPERS[i][2], "", guiLeft + xStart + colX[i] - 5, guiTop + yStartTop + 22, 115);
			Draw.aligned(g, color + MiningPage.shortNumber(available), "", guiLeft + xStart + colX[i], guiTop + yStartTop + 34, 115);
			Draw.aligned(g, color + MiningPage.shortNumber(total), "", guiLeft + xStart + colX[i], guiTop + yStartTop + 44, 115);
			if (mouseY >= guiTop + yStartTop + 20 && mouseY < guiTop + yStartTop + 54
				&& mouseX >= guiLeft + xStart + colX[i] - 8 && mouseX < guiLeft + xStart + colX[i] + 55) {
				screen.tooltipToDisplay = whispersTooltip(core, key, color, WHISPERS[i][2], total, spent, slot);
			}
		}

		drawGalatea(g, member, core, foraging, mouseX, mouseY);

		// loadout slot (where HOTM has the forge)
		int slotX = guiLeft + 149, slotY = guiTop + 20;
		Draw.rect(g, SLOT_BACKGROUND, slotX, slotY, 16, 16, 0, 1, 0, 1, 20, 20);
		g.item(loadoutIcon, slotX, slotY);
		if (mouseX >= slotX && mouseX < slotX + 16 && mouseY >= slotY && mouseY < slotY + 16) {
			screen.tooltipToDisplay = loadoutTooltip(member, slot);
		}

		treeLeft = guiLeft + 249;
		treeTop = guiTop + 16;
		treeRight = guiLeft + 412;
		treeBottom = guiTop + 185;
		tree.render(g, screen, treeLeft, treeTop, treeRight, treeBottom, SkillTree.hotfNodes(member),
			hotf == null ? 0 : hotf.level, mouseX, mouseY);
		tree.markSelectedAbility(SkillTree.hotfSelectedAbility(member));
	}

	boolean overTree(double mouseX, double mouseY) {
		return treeLeft < mouseX && mouseX < treeRight && treeTop < mouseY && mouseY < treeBottom;
	}

	private record Line(String label, String value, List<Component> tooltip) {}

	/** Two columns of compact lines in the bottom-left box, like the HOTM crystals. */
	private void drawGalatea(GuiGraphicsExtractor g, JsonObject member, JsonObject core, JsonObject foraging, int mouseX, int mouseY) {
		List<Line> left = new ArrayList<>(), right = new ArrayList<>();

		// tree gifts
		JsonObject gifts = J.objOrEmpty(foraging, "tree_gifts");
		List<Component> giftsTt = new ArrayList<>();
		giftsTt.add(Component.literal("§2Tree Gifts"));
		double giftsTotal = 0;
		for (String[] t : GIFT_TREES) {
			double n = J.dbl(gifts, t[0], 0);
			giftsTotal += n;
			giftsTt.add(Component.literal(t[1] + t[2] + "§7: §f" + Fmt.number(n) + " §8(milestone " + J.i(gifts, "milestone_tier_claimed." + t[0], 0) + ")"));
		}
		giftsTt.add(Component.literal("§7Total: §f" + Fmt.number(giftsTotal)));
		left.add(new Line("§2Tree Gifts:", "§f" + MiningPage.shortNumber(giftsTotal), giftsTt));

		// fish family
		JsonArray fish = J.arrOrEmpty(foraging, "fish_family");
		List<Component> fishTt = new ArrayList<>();
		fishTt.add(Component.literal("§bFish Family §7(" + fish.size() + " found)"));
		StringBuilder row = new StringBuilder();
		int inRow = 0;
		for (JsonElement e : fish) {
			String name = pretty(e.getAsString().replace("_THE_FISH", ""));
			row.append(inRow == 0 ? "§7" : "§8, §7").append(name);
			if (++inRow == 4) {
				fishTt.add(Component.literal(row.toString()));
				row.setLength(0);
				inRow = 0;
			}
		}
		if (inRow > 0) fishTt.add(Component.literal(row.toString()));
		if (fish.isEmpty()) fishTt.add(Component.literal("§cNone found"));
		left.add(new Line("§bFish Family:", "§f" + fish.size(), fishTt));

		// temples
		JsonArray temples = J.arrOrEmpty(member, "temples.unlocked_temples");
		List<Component> templesTt = new ArrayList<>();
		templesTt.add(Component.literal("§eUnlocked Temples"));
		for (JsonElement e : temples) templesTt.add(Component.literal("§a✔ §f" + pretty(e.getAsString()) + " Temple"));
		if (temples.isEmpty()) templesTt.add(Component.literal("§cNone"));
		left.add(new Line("§eTemples:", "§f" + temples.size(), templesTt));

		// Hina tasks
		JsonObject hina = J.objOrEmpty(foraging, "hina.tasks");
		int completed = J.arrOrEmpty(hina, "completed_tasks").size();
		left.add(new Line("§dHina Tasks:", "§f" + completed, PvWidgets.lines("§dHina Tasks",
			"§7Completed: §f" + completed,
			"§7Rewards claimed: §f" + J.arrOrEmpty(hina, "claimed_rewards").size(),
			"§7Reward tier claimed: §f" + J.i(hina, "tier_claimed", 0))));

		// harp songs
		JsonObject harp = J.objOrEmpty(foraging, "songs.harp");
		Map<String, double[]> songs = new TreeMap<>();
		for (Map.Entry<String, JsonElement> e : harp.entrySet()) {
			String k = e.getKey();
			if (!k.startsWith("song_") || !e.getValue().isJsonPrimitive() || !e.getValue().getAsJsonPrimitive().isNumber()) continue;
			int field = k.endsWith("_perfect_completions") ? 1 : k.endsWith("_best_completion") ? 2 : k.endsWith("_completions") ? 0 : -1;
			if (field < 0) continue;
			String song = k.substring(5, k.length() - (field == 1 ? 20 : field == 2 ? 16 : 12));
			songs.computeIfAbsent(song, s -> new double[3])[field] = e.getValue().getAsDouble();
		}
		double songCompletions = 0;
		List<Component> songsTt = new ArrayList<>();
		songsTt.add(Component.literal("§9Harp Songs"));
		for (Map.Entry<String, double[]> s : songs.entrySet()) {
			double[] v = s.getValue();
			songCompletions += v[0];
			songsTt.add(Component.literal("§f" + SONGS.getOrDefault(s.getKey(), pretty(s.getKey())) + "§7: §a" + Fmt.number(v[0])
				+ (v[1] > 0 ? " §8(§6" + Fmt.number(v[1]) + " perfect§8)" : " §8(best " + Math.round(v[2] * 100) + "%)")));
		}
		if (songs.isEmpty()) songsTt.add(Component.literal("§cNo song played"));
		left.add(new Line("§9Harp Songs:", "§f" + Fmt.number(songCompletions), songsTt));

		// honey
		JsonObject smeared = J.objOrEmpty(foraging, "honey.smeared_trees");
		List<Component> honeyTt = new ArrayList<>();
		honeyTt.add(Component.literal("§6Honey"));
		honeyTt.add(Component.literal("§7Smeared trees: §f" + smeared.size()));
		for (Map.Entry<String, JsonElement> e : smeared.entrySet()) {
			honeyTt.add(Component.literal("§8 ■ §7" + pretty(e.getKey()) + (e.getValue().isJsonPrimitive() ? "§8: §f" + e.getValue().getAsString() : "")));
		}
		honeyTt.add(Component.literal("§7Refills: §f" + J.objOrEmpty(foraging, "honey.refill_times").size()));
		left.add(new Line("§6Smeared Trees:", "§f" + smeared.size(), honeyTt));

		// daily counters (foraging_core): only today's values count
		long today = LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay();
		int treesDay = J.i(core, "daily_trees_cut_day", -1), logsDay = J.i(core, "daily_log_cut_day", -1);
		int treesToday = treesDay == today ? J.i(core, "daily_trees_cut", 0) : 0;
		int giftsToday = treesDay == today ? J.i(core, "daily_gifts", 0) : 0;
		List<Component> dailyTt = new ArrayList<>();
		dailyTt.add(Component.literal("§aToday on Galatea"));
		dailyTt.add(Component.literal("§7Trees cut: §f" + treesToday));
		dailyTt.add(Component.literal("§7Tree gifts: §f" + giftsToday));
		JsonArray logs = J.arrOrEmpty(core, "daily_log_cut");
		dailyTt.add(Component.literal("§7Logs cut: §f" + (logsDay == today ? logs.size() : 0)));
		if (treesDay > 0 && treesDay != today) {
			dailyTt.add(Component.empty());
			dailyTt.add(Component.literal("§8Last tree cut: " + LocalDate.ofEpochDay(treesDay)));
		}
		left.add(new Line("§aTrees Today:", "§f" + treesToday, dailyTt));

		// Starlyn personal bests
		JsonObject bests = J.objOrEmpty(foraging, "starlyn.personal_bests");
		Map<String, Double> ordered = new LinkedHashMap<>();
		for (String k : STARLYN.keySet()) if (bests.has(k)) ordered.put(k, J.dbl(bests, k, 0));
		for (Map.Entry<String, JsonElement> e : bests.entrySet()) {
			if (!ordered.containsKey(e.getKey()) && e.getValue().isJsonPrimitive()) ordered.put(e.getKey(), e.getValue().getAsDouble());
		}
		for (Map.Entry<String, Double> e : ordered.entrySet()) {
			if (right.size() >= 6) break;
			String[] info = STARLYN.getOrDefault(e.getKey(), new String[]{"§f", pretty(e.getKey())});
			right.add(new Line(info[0] + info[1].replace(" Log", "") + " PB:", "§f" + MiningPage.shortNumber(e.getValue()),
				PvWidgets.lines("§6Starlyn Personal Best", info[0] + info[1] + "§7: §f" + Fmt.number(e.getValue()))));
		}
		if (ordered.isEmpty()) right.add(new Line("§6Starlyn PB:", "§7None", PvWidgets.lines("§6Starlyn Personal Bests", "§cNone")));

		// tokens and ability of the selected tree
		int slot = SkillTree.hotfSlotNumber(member);
		int tokens = SkillTree.hotfTokensSpent(member, slot);
		right.add(new Line("§aTokens Spent:", "§f" + tokens, loadoutTooltip(member, slot)));
		String ability = SkillTree.hotfSelectedAbility(member);
		String abilityName = abilityName(ability);
		right.add(new Line("§6Ability:", ability == null ? "§7None" : "§f" + abilityName,
			PvWidgets.lines("§6Selected Axe Ability", ability == null ? "§7None selected" : "§a" + abilityName)));

		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		int padding = 4, rowHeight = 10;
		int rectStartX = 16 + padding, rectStartY = 97 + padding + 1;
		int rectXSize = 214 - padding * 2;
		int columnWidth = rectXSize / 2 - padding / 2, columnOffset = columnWidth + padding;
		List<List<Line>> cols = List.of(left, right);
		for (int c = 0; c < 2; c++) {
			List<Line> col = cols.get(c);
			for (int r = 0; r < col.size(); r++) {
				Line line = col.get(r);
				int cx = guiLeft + rectStartX + c * columnOffset, cy = guiTop + rectStartY + r * rowHeight;
				Draw.aligned(g, line.label(), line.value(), cx, cy, columnWidth);
				if (mouseX >= cx && mouseX < cx + columnWidth && mouseY >= cy - 1 && mouseY < cy + rowHeight - 1) {
					screen.tooltipToDisplay = line.tooltip();
				}
			}
		}
	}

	private static String abilityName(String key) {
		if (key == null) return null;
		MiningHotmLayout l = MiningHotmTree.layout("hotf");
		MiningHotmLayout.Perk perk = l == null ? null : l.perks.get(key);
		return perk != null ? perk.name : pretty(key);
	}

	private List<Component> loadoutTooltip(JsonObject member, int slot) {
		List<Component> tt = new ArrayList<>();
		tt.add(Component.literal("§2Heart of the Forest Loadouts"));
		int count = Math.max(slot, SkillTree.hotfTreeCount(member));
		for (int i = 1; i <= count; i++) {
			String name = J.str(member, "skill_tree." + SkillTree.hotfKey("foraging", i) + ".custom_name", null);
			int perks = 0;
			JsonObject nodes = J.obj(member, "skill_tree.nodes." + SkillTree.hotfKey("foraging", i));
			if (nodes != null) for (Map.Entry<String, JsonElement> e : nodes.entrySet()) {
				if (!e.getKey().startsWith("toggle_") && e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()) perks++;
			}
			tt.add(Component.literal((i == slot ? "§a▶ " : "§7  ") + "Tree " + i + (name != null ? " §8(" + name + "§8)" : "")
				+ "§7: §a" + SkillTree.hotfTokensSpent(member, i) + " tokens§7, §f" + perks + (perks == 1 ? " perk" : " perks")));
		}
		tt.add(Component.empty());
		tt.add(Component.literal("§7Selected ability: " + (SkillTree.hotfSelectedAbility(member) == null ? "§7None"
			: "§6" + abilityName(SkillTree.hotfSelectedAbility(member)))));
		return tt;
	}

	private static List<Component> whispersTooltip(JsonObject core, String key, String color, String name, double total, double spent, int slot) {
		List<Component> tt = new ArrayList<>();
		tt.add(Component.literal(color + name + " Whispers"));
		tt.add(Component.literal("§7Available: " + color + Fmt.number(Math.max(0, total - spent))));
		tt.add(Component.literal("§7Spent (tree " + slot + "): " + color + Fmt.number(spent)));
		tt.add(Component.literal("§7Total: " + color + Fmt.number(total)));
		JsonObject w = J.objOrEmpty(core, "whispers." + key);
		List<Component> perTree = new ArrayList<>();
		for (int i = 1; w.has(String.valueOf(i)); i++) {
			double s = J.dbl(w, i + ".spent", 0);
			if (s > 0) perTree.add(Component.literal("§8 ■ §7Tree " + i + ": " + color + Fmt.number(s)));
		}
		if (perTree.size() > 1) {
			tt.add(Component.empty());
			tt.add(Component.literal("§7Spent per tree:"));
			tt.addAll(perTree);
		}
		return tt;
	}

	/** "SKELETON" / "fire_and_flames" -> "Skeleton" / "Fire And Flames". */
	static String pretty(String id) {
		StringBuilder sb = new StringBuilder();
		for (String w : id.toLowerCase(Locale.ROOT).split("_")) {
			if (w.isEmpty()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return sb.toString();
	}

	void resetCache() {
		tree.resetCache();
	}
}
