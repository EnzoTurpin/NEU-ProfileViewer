/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates DungeonPage, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.item.LegacyItems;
import dev.neupv.item.PvItem;
import dev.neupv.pv.Level;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.pv.Weights;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.Fmt;
import dev.neupv.util.J;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Port of NEU's DungeonPage ("Dungeoneering" tab): catacombs on the first side page, master mode on the second. */
public class DungeonPage extends PvPage {
	private static final Identifier PV_DUNG = Draw.tex("gui/pv_dung.png");
	private static final String[] BOSS_FLOOR_NAMES = {"Bonzo", "Scarf", "Professor", "Thorn", "Livid", "Sadan", "Necron"};
	private static final String[] BOSS_FLOOR_HEADS = {
		"12716ecbf5b8da00b05f316ec6af61e8bd02805b21eb8e440151468dc656549c",
		"7de7bbbdf22bfe17980d4e20687e386f11d59ee1db6f8b4762391b79a5ac532d",
		"9971cee8b833a62fc2a612f3503437fdf93cad692d216b8cf90bbb0538c47dd8",
		"8b6a72138d69fbbd2fea3fa251cabd87152e4f1c97e5f986bf685571db3cc0",
		"c1007c5b7114abec734206d4fc613da4f3a0e99f71ff949cedadc99079135a0b",
		"fa06cb0c471c1c9bc169af270cd466ea701946776056e472ecdaeb49f0f4a4dc",
		"a435164c05cea299a3f016bbbed05706ebb720dac912ce4351c2296626aecd9a",
	};
	private static final int SECTION_WIDTH = 110;

	private static ItemStack deadBush, netherStar;
	private static ItemStack[] bossHeads;
	private static Map<String, ItemStack> classToIcon;
	private static ItemStack normalModeIcon, masterModeIcon;

	private static int floorTime = 7;
	private int floorLevelTo = -1;
	private long floorLevelToXP = -1;
	private boolean levelFieldError;

	public DungeonPage(ProfileViewerScreen screen) {
		super(screen);
	}

	private static void initIcons() {
		if (bossHeads == null) {
			deadBush = new ItemStack(Items.DEAD_BUSH);
			netherStar = new ItemStack(Items.NETHER_STAR);
			bossHeads = new ItemStack[7];
			for (int i = 0; i < 7; i++) {
				String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + BOSS_FLOOR_HEADS[i] + "\"}}}";
				bossHeads[i] = LegacyItems.skull("§e" + BOSS_FLOOR_NAMES[i], Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
			}
			classToIcon = new HashMap<>();
			ItemStack healer = new ItemStack(Items.SPLASH_POTION); // 1.8.9 potion:16389 = splash potion of healing
			healer.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING));
			classToIcon.put("healer", healer);
			classToIcon.put("mage", new ItemStack(Items.BLAZE_ROD));
			classToIcon.put("berserk", new ItemStack(Items.IRON_SWORD));
			classToIcon.put("archer", new ItemStack(Items.BOW));
			classToIcon.put("tank", new ItemStack(Items.LEATHER_CHESTPLATE));
		}
		// Repo items may arrive after the first frame: keep the vanilla fallbacks until the repo is loaded.
		if (normalModeIcon == null || !modeIconsFromRepo) {
			modeIconsFromRepo = NeuRepo.isLoaded();
			normalModeIcon = modeIcon("DUNGEON_STONE", Items.STONE_BRICKS, "§7Normal Mode");
			masterModeIcon = modeIcon("MASTER_SKULL_TIER_7", Items.WITHER_SKELETON_SKULL, "§7Master Mode");
		}
	}

	private static boolean modeIconsFromRepo;

	private static ItemStack modeIcon(String repoId, net.minecraft.world.item.Item fallback, String name) {
		PvItem item = LegacyItems.fromRepo(repoId);
		ItemStack s = item != null ? item.stack().copy() : new ItemStack(fallback);
		return LegacyItems.rename(s, name);
	}

	private EditBox levelField(int x, int y) {
		EditBox box = screen.pageField(this, "cataLevel", x, y, 20, 10);
		box.setMaxLength(3);
		box.setTextColor(levelFieldError ? 0xFFFF5555 : 0xFFFFFFFF);
		return box;
	}

	@Override
	public boolean hasSideButtons() {
		return true;
	}

	@Override
	public void draw(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTicks) {
		initIcons();
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		var font = Draw.font();

		Draw.full(g, PV_DUNG, guiLeft, guiTop, ProfileViewerScreen.SIZE_X, ProfileViewerScreen.SIZE_Y);

		JsonObject leveling = NeuRepo.leveling();
		SkyblockProfiles.SkyblockProfile selected = getSelectedProfile();
		Map<String, Level> levelingInfo = selected == null ? null : selected.getLevelingInfo();
		if (leveling == null || levelingInfo == null) {
			if (leveling == null) Draw.centered(g, "§cMissing repo data", guiLeft + ProfileViewerScreen.SIZE_X / 2f, guiTop + 101, true);
			levelField(guiLeft + 45, guiTop + 54).visible = false;
			drawSideButtons(g, mouseX, mouseY);
			return;
		}
		JsonObject profileInfo = selected.getProfileJson();

		boolean onMasterMode = ProfileViewerScreen.isOnSecondPage();
		String dungeonString = onMasterMode ? "master_catacombs" : "catacombs";
		String prefix = onMasterMode ? "M" : "F";

		shadowedString(g, "§c" + (onMasterMode ? "Master Mode" : "Catacombs"), guiLeft + ProfileViewerScreen.SIZE_X / 2f, guiTop + 5, SECTION_WIDTH);

		// Catacombs level, level calculator, misc stats and fastest times
		{
			Level levelObjCata = levelingInfo.get("cosmetic_catacombs");
			int levelFloored = (int) Math.floor(levelObjCata.level);
			int x = guiLeft + 23;
			int y = guiTop + 25;

			EditBox field = levelField(x + 22, y + 29);
			if (floorLevelTo == -1 && levelFloored >= 0) {
				field.setValue(String.valueOf(levelFloored + 1));
				calculateFloorLevelXP();
			}

			PvWidgets.renderXpBar(screen, g, "§cCatacombs", deadBush, x, y, SECTION_WIDTH, levelObjCata, mouseX, mouseY);

			Draw.aligned(g, "§eUntil Cata " + floorLevelTo + ": ", "§f" + Fmt.shortNumber(floorLevelToXP), x, y + 16, SECTION_WIDTH);

			if (mouseX > x && mouseX < x + SECTION_WIDTH && mouseY > y + 16 && mouseY < y + 24) {
				screen.tooltipToDisplay = runsTooltip(profileInfo, onMasterMode);
			}

			int calcLen = font.width("Calculate");
			shadowedString(g, "§fCalculate", x + SECTION_WIDTH - 17 - calcLen / 2f, y + 30, 100);

			// Random stats
			float secrets = (float) J.dbl(profileInfo, "dungeons.secrets", 0);
			float totalRunsF = 0, totalRunsF5 = 0, totalRunsM = 0, totalRunsM5 = 0;
			float mobKills = 0;
			for (int i = 1; i <= 7; i++) {
				float runs = f(profileInfo, "dungeons.dungeon_types.catacombs.tier_completions." + i);
				totalRunsF += runs;
				if (i >= 5) totalRunsF5 += runs;
				float mRuns = f(profileInfo, "dungeons.dungeon_types.master_catacombs.tier_completions." + i);
				totalRunsM += mRuns;
				if (i >= 5) totalRunsM5 += mRuns;
				mobKills += f(profileInfo, "dungeons.dungeon_types.catacombs.mobs_killed." + i);
				mobKills += f(profileInfo, "dungeons.dungeon_types.master_catacombs.mobs_killed." + i);
			}
			float totalRuns = totalRunsF + totalRunsM;

			int miscTopY = y + 55;
			Draw.aligned(g, "§eTotal Runs " + prefix, "§f" + (int) (onMasterMode ? totalRunsM : totalRunsF), x, miscTopY, SECTION_WIDTH);
			Draw.aligned(g, "§eTotal Runs (" + prefix + "5-7)  ", "§f" + (int) (onMasterMode ? totalRunsM5 : totalRunsF5), x, miscTopY + 10, SECTION_WIDTH);
			if (mouseX >= x && mouseX <= x + SECTION_WIDTH && mouseY >= miscTopY && mouseY < miscTopY + 18) {
				screen.tooltipToDisplay = DungeonRunsTooltip.build(profileInfo);
			}
			Draw.aligned(g, "§eSecrets (Total)  ", "§f" + Fmt.shortNumber(secrets), x, miscTopY + 20, SECTION_WIDTH);
			Draw.aligned(g, "§eSecrets (/Run)  ", "§f" + (Math.round(secrets / Math.max(1, totalRuns) * 100) / 100f), x, miscTopY + 30, SECTION_WIDTH);
			Draw.aligned(g, "§eMob Kills (Total)  ", "§f" + Fmt.shortNumber(mobKills), x, miscTopY + 40, SECTION_WIDTH);

			// Floor selector for the fastest times
			int y3 = y + 117;
			for (int i = 1; i <= 7; i++) {
				int w = font.width(String.valueOf(i));
				int bx = x + SECTION_WIDTH * i / 8 - w / 2;
				shadowedString(g, (i == floorTime ? "§e" : "§f") + i, bx + w / 2f, y3, 10);
			}

			String base = "dungeons.dungeon_types." + dungeonString;
			float timeNorm = f(profileInfo, base + ".fastest_time." + floorTime);
			float timeS = f(profileInfo, base + ".fastest_time_s." + floorTime);
			float timeSPlus = f(profileInfo, base + ".fastest_time_s_plus." + floorTime);
			Draw.aligned(g, "§eFloor " + floorTime + " ", "§f" + (timeNorm <= 0 ? "N/A" : prettyTime((long) timeNorm)), x, y3 + 10, SECTION_WIDTH);
			Draw.aligned(g, "§eFloor " + floorTime + " S", "§f" + (timeS <= 0 ? "N/A" : prettyTime((long) timeS)), x, y3 + 20, SECTION_WIDTH);
			Draw.aligned(g, "§eFloor " + floorTime + " S+", "§f" + (timeSPlus <= 0 ? "N/A" : prettyTime((long) timeSPlus)), x, y3 + 30, SECTION_WIDTH);
		}

		// Boss collections (completions)
		{
			int x = guiLeft + 161;
			int y = guiTop + 27;
			shadowedString(g, "§cBoss Collections", x + SECTION_WIDTH / 2f, y, SECTION_WIDTH);
			for (int i = 1; i <= 7; i++) {
				float compl = f(profileInfo, "dungeons.dungeon_types." + dungeonString + ".tier_completions." + i);
				Draw.item(g, bossHeads[i - 1], x - 4, y + 10 + 20 * (i - 1), 1.3f);
				Draw.aligned(g, String.format(Locale.ROOT, "§e%s (%s%d) ", BOSS_FLOOR_NAMES[i - 1], prefix, i), "§f" + (int) compl,
					x + 16, y + 18 + 20 * (i - 1), SECTION_WIDTH - 15);
			}
		}

		// Classes
		{
			int x = guiLeft + 298;
			int y = guiTop + 27;
			shadowedString(g, "§5Class Levels", x + SECTION_WIDTH / 2f, y, SECTION_WIDTH);

			JsonElement activeElement = J.get(profileInfo, "dungeons.selected_dungeon_class");
			String activeClass = activeElement != null && activeElement.isJsonPrimitive() && activeElement.getAsJsonPrimitive().isString()
				? activeElement.getAsString() : null;

			List<String> classes = Weights.DUNGEON_CLASS_NAMES;
			float classLevelSum = 0, classLevelSumOverflow = 0;
			int numMaxed = 0;
			for (int i = 0; i < classes.size(); i++) {
				String className = classes.get(i);
				String colour = className.equalsIgnoreCase(activeClass) ? "§a" : "§f";
				Level cached = levelingInfo.get("cosmetic_" + className);
				// Copy: NEU mutated the cached level object here.
				Level levelObj = copy(cached);
				if (levelObj.level >= 50) {
					levelObj.maxed = true;
					numMaxed++;
					classLevelSum += 50;
					classLevelSumOverflow += levelObj.level;
				} else {
					classLevelSum += levelObj.level;
				}
				PvWidgets.renderXpBar(screen, g, colour + Character.toUpperCase(className.charAt(0)) + className.substring(1),
					classToIcon.get(className), x, y + 20 + 24 * i, SECTION_WIDTH, levelObj, mouseX, mouseY);
			}

			Level classAverage = new Level();
			if (numMaxed == classes.size()) {
				classAverage.maxed = true;
				classAverage.level = classLevelSumOverflow / classes.size();
			} else {
				classAverage.level = classLevelSum / classes.size();
			}
			PvWidgets.renderXpBar(screen, g, "§fClass Average", netherStar, x, y + 20 + 24 * 5, SECTION_WIDTH, classAverage, mouseX, mouseY);
		}

		drawSideButtons(g, mouseX, mouseY);
	}

	private static Level copy(Level l) {
		Level c = new Level();
		c.level = l.level;
		c.maxXpForLevel = l.maxXpForLevel;
		c.maxed = l.maxed;
		c.maxLevel = l.maxLevel;
		c.totalXp = l.totalXp;
		return c;
	}

	/** Tooltip of the "Until Cata X" line: runs needed per floor (F5-F7 or M3-M7) and the expected time. */
	private List<Component> runsTooltip(JsonObject profileInfo, boolean master) {
		Minecraft mc = Minecraft.getInstance();
		boolean shift = mc.hasShiftDown();
		boolean ctrl = mc.hasControlDown();
		String type = master ? "master_catacombs" : "catacombs";
		int[] floors = master ? new int[]{3, 4, 5, 6, 7} : new int[]{5, 6, 7};
		float[] baseXp = master ? new float[]{35000, 55000, 70000, 100000, 300000} : new float[]{2400, 4880, 28000};
		float[] cap = master ? new float[]{50, 50, 50, 50, 50} : new float[]{150, 100, 50};
		String letter = master ? "M" : "F";

		List<String> lines = new ArrayList<>();
		lines.add("§eRemaining XP: §7" + String.format(Locale.US, "%,d", floorLevelToXP));
		long[] runs = new long[floors.length];
		for (int i = 0; i < floors.length; i++) {
			float completions = Math.min(f(profileInfo, "dungeons.dungeon_types." + type + ".tier_completions." + floors[i]), cap[i]);
			float xp = baseXp[i] * (completions / 100 + 1);
			if (!shift) xp *= 1.1f;
			runs[i] = (int) Math.ceil(floorLevelToXP / xp);
			lines.add(String.format(Locale.US, "# %s%d Runs (%s xp) : %d", letter, floors[i], Fmt.shortNumber(xp), runs[i]));
		}
		lines.add("");
		boolean hasTime = false;
		for (int i = 0; i < floors.length; i++) {
			float fastest = f(profileInfo, "dungeons.dungeon_types." + type + ".fastest_time_s_plus." + floors[i]);
			if (fastest > 1000) {
				lines.add(String.format(Locale.US, "Expected Time (%s%d) : %s", letter, floors[i], prettyTime(runs[i] * (long) (fastest * 1.2))));
				hasTime = true;
			}
		}
		if (hasTime) lines.add("");
		if (!shift) lines.add("[Hold §eSHIFT§7 to show without Expert Ring]");
		if (ctrl) {
			if (!shift) lines.add("");
			lines.add("Number of runs is calculated as [Remaining XP]/[XP per Run].");
			lines.add("The [XP per Run] is the average xp gained from an S+ run");
			lines.add("The §5Catacombs Expert Ring§7 is assumed to be used, unless §eSHIFT§7 is held.");
			lines.add("[Time per run] is calculated using Fastest S+ x 120%");
		} else {
			lines.add("[Hold §eCTRL§7 to see details]");
		}
		return PvWidgets.lines(lines.toArray(new String[0]));
	}

	private static float f(JsonObject json, String path) {
		return (float) J.dbl(json, path, 0);
	}

	/** NEU Utils.prettyTime. */
	static String prettyTime(long millis) {
		long seconds = millis / 1000 % 60;
		long minutes = (millis / 1000 / 60) % 60;
		long hours = (millis / 1000 / 60 / 60) % 24;
		long days = millis / 1000 / 60 / 60 / 24;
		if (millis < 0) return "Ended!";
		if (minutes == 0 && hours == 0 && days == 0) return seconds + "s";
		if (hours == 0 && days == 0) return minutes + "m" + seconds + "s";
		if (days == 0) return hours <= 6 ? hours + "h" + minutes + "m" + seconds + "s" : hours + "h";
		return days + "d" + hours + "h";
	}

	/**
	 * NEU Utils.renderShadowedString: text centred on x (top at y), shrunk to maxLength, with a soft dark outline
	 * instead of the usual drop shadow.
	 */
	static void shadowedString(GuiGraphicsExtractor g, String str, float x, float y, int maxLength) {
		var font = Draw.font();
		int strLen = font.width(str);
		float factor = maxLength < 0 ? 1 : Math.min(1, maxLength / (float) strLen);
		String clean = str.replaceAll("(?i)§[0-9a-f]", "");
		for (int xOff = -2; xOff <= 2; xOff++) {
			for (int yOff = -2; yOff <= 2; yOff++) {
				if (Math.abs(xOff) != Math.abs(yOff)) {
					int alpha = 200 / Math.max(Math.abs(xOff), Math.abs(yOff));
					Draw.centeredMaxWidth(g, clean, x + xOff / 2f * factor, y + 4 + yOff / 2f * factor, false, maxLength < 0 ? Integer.MAX_VALUE : maxLength, alpha << 24);
				}
			}
		}
		Draw.centeredMaxWidth(g, str, x, y + 4, false, maxLength < 0 ? Integer.MAX_VALUE : maxLength, 0xFF066CD3);
	}

	private void drawSideButtons(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		PvWidgets.sideButtons(screen, g, normalModeIcon, masterModeIcon, mouseX, mouseY);
	}

	private void calculateFloorLevelXP() {
		JsonObject leveling = NeuRepo.leveling();
		SkyblockProfiles.SkyblockProfile selected = getSelectedProfile();
		if (leveling == null || selected == null || selected.getLevelingInfo() == null) return;
		Level levelObjCata = selected.getLevelingInfo().get("cosmetic_catacombs");
		EditBox field = levelField(screen.guiLeft() + 45, screen.guiTop() + 54);
		try {
			int target = Integer.parseInt(field.getValue().trim());
			levelFieldError = false;
			floorLevelTo = target;
			JsonArray levelingArray = J.arr(leveling, "catacombs");
			float remaining = -((levelObjCata.level % 1) * levelObjCata.maxXpForLevel);
			if (levelingArray != null) {
				for (int level = 0; level < Math.min(floorLevelTo, levelingArray.size()); level++) {
					if (level < Math.floor(levelObjCata.level)) continue;
					remaining += levelingArray.get(level).getAsFloat();
				}
			}
			if (remaining < 0) remaining = 0;
			floorLevelToXP = (long) remaining;
		} catch (NumberFormatException e) {
			levelFieldError = true;
		}
		levelField(screen.guiLeft() + 45, screen.guiTop() + 54);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int guiLeft = screen.guiLeft(), guiTop = screen.guiTop();
		var font = Draw.font();

		switch (PvWidgets.sideButtonClicked(screen, mouseX, mouseY)) {
			case 1 -> {
				ProfileViewerScreen.setOnSecondPage(false);
				return true;
			}
			case 2 -> {
				ProfileViewerScreen.setOnSecondPage(true);
				return true;
			}
			default -> {
			}
		}

		int cW = font.width("Calculate");
		if (mouseX >= guiLeft + 23 + 110 - 17 - cW && mouseX <= guiLeft + 23 + 110 - 17 && mouseY >= guiTop + 55 && mouseY <= guiTop + 65) {
			calculateFloorLevelXP();
			return true;
		}

		int y = guiTop + 142;
		if (mouseY >= y - 2 && mouseY <= y + 9) {
			for (int i = 1; i <= 7; i++) {
				int w = font.width(String.valueOf(i));
				int x = guiLeft + 23 + 110 * i / 8 - w / 2;
				if (mouseX >= x - 2 && mouseX <= x + 7) {
					floorTime = i;
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public boolean keyPressed(int keyCode) {
		return false;
	}

	@Override
	public void resetCache() {
		floorLevelTo = -1;
		floorLevelToXP = -1;
		levelFieldError = false;
	}
}
