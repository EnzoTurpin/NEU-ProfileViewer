/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates PetLeveling.kt, ItemUtils.createPetItemstackFromPetInfo / petToolTipXPExtendPetOverlay,
 * NEUManager.getPetLoreReplacements and GuiProfileViewer.PET_STAT_BOOSTS,
 * Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.item.LegacyItems;
import dev.neupv.item.PvItem;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.Fmt;
import dev.neupv.util.J;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

/** Pet levels, pet item stacks and their lore, as NEU builds them for the Pets tab. */
public final class PetsData {
	/** NEU PetInfoOverlay.Rarity names, index = rarity number used in the repo ids (TYPE;N). */
	public static final String[] RARITY_NAMES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};
	public static final String[] RARITY_COLOURS = {"§f", "§a", "§9", "§5", "§6", "§d"};

	private static final Map<String, Map<String, Float>> PET_STAT_BOOSTS = Map.of(
		"PET_ITEM_BIG_TEETH_COMMON", Map.of("CRIT_CHANCE", 5f),
		"PET_ITEM_HARDENED_SCALES_UNCOMMON", Map.of("DEFENCE", 25f),
		"PET_ITEM_LUCKY_CLOVER", Map.of("MAGIC_FIND", 7f),
		"PET_ITEM_SHARPENED_CLAWS_UNCOMMON", Map.of("CRIT_DAMAGE", 15f));
	private static final Map<String, Map<String, Float>> PET_STAT_BOOSTS_MULT = Map.of(
		"PET_ITEM_IRON_CLAWS_COMMON", Map.of("CRIT_DAMAGE", 1.4f, "CRIT_CHANCE", 1.4f),
		"PET_ITEM_TEXTBOOK", Map.of("INTELLIGENCE", 2f));

	private PetsData() {}

	public static int rarityIndex(String tier) {
		for (int i = 0; i < RARITY_NAMES.length; i++) if (RARITY_NAMES[i].equals(tier)) return i;
		return -1;
	}

	// ---- PetLeveling.kt ----

	/**
	 * NEU PetLeveling.PetLevel. NEU summed the level costs in a Float (runningFold(0F)), which loses precision past
	 * ~16M xp; here the sums are exact longs.
	 */
	public record PetLevel(int currentLevel, int maxLevel, long expRequiredForNextLevel, long expRequiredForMaxLevel,
		double expInCurrentLevel, double expTotal) {
		public double percentageToNextLevel() {
			return expRequiredForNextLevel == 0 ? 0 : expInCurrentLevel / expRequiredForNextLevel;
		}

		public boolean maxed() {
			return currentLevel >= maxLevel;
		}
	}

	public static PetLevel level(String type, String rarity, double exp) {
		List<Long> individual = ladder(type, rarity);
		long[] cumulative = new long[individual.size() + 1];
		for (int i = 0; i < individual.size(); i++) cumulative[i + 1] = cumulative[i] + individual.get(i);
		int lastLe = 0;
		for (int i = 0; i < cumulative.length; i++) if (cumulative[i] <= exp) lastLe = i;
		int level = lastLe + 1;
		long expForNext = level > individual.size() ? individual.get(individual.size() - 1) : individual.get(level - 1);
		double inCurrent = level >= cumulative.length
			? exp - cumulative[cumulative.length - 1]
			: Math.max(0, expForNext - (cumulative[level] - exp));
		return new PetLevel(level, cumulative.length, expForNext, cumulative[cumulative.length - 1], inCurrent, exp);
	}

	private static List<Long> ladder(String type, String rarity) {
		List<Long> stub = List.of(1L, 1L);
		JsonObject pets = NeuRepo.constant("pets");
		JsonArray base = J.arr(pets, "pet_levels");
		if (base == null) return stub;
		List<Long> levels = longs(base);
		JsonObject rarityOffsets = J.obj(pets, "pet_rarity_offset");
		int maxLevel = 100;
		JsonObject custom = J.obj(pets, "custom_pet_leveling." + type);
		if (custom != null) {
			JsonArray customLevels = J.arr(custom, "pet_levels");
			switch (J.i(custom, "type", 0)) {
				case 1 -> {
					if (customLevels == null) return stub;
					levels.addAll(longs(customLevels));
				}
				case 2 -> {
					if (customLevels == null) return stub;
					levels = longs(customLevels);
				}
				default -> {
				}
			}
			maxLevel = J.i(custom, "max_level", maxLevel);
			JsonObject ro = J.obj(custom, "rarity_offset");
			if (ro != null) rarityOffsets = ro;
		}
		if (rarityOffsets == null || !rarityOffsets.has(rarity)) return stub;
		int offset = rarityOffsets.get(rarity).getAsInt();
		int from = Math.min(offset, levels.size());
		int to = Math.min(levels.size(), from + Math.max(0, maxLevel - 1));
		List<Long> out = new ArrayList<>(levels.subList(from, to));
		return out.isEmpty() ? stub : out;
	}

	private static List<Long> longs(JsonArray a) {
		List<Long> out = new ArrayList<>(a.size());
		for (JsonElement e : a) out.add(e.getAsLong());
		return out;
	}

	// ---- pet stack (ItemUtils.createPetItemstackFromPetInfo) ----

	/** A pet as shown in the grid: its icon, name and full lore (NEU's created pet ItemStack). */
	public record PetDisplay(JsonObject json, String type, int rarity, ItemStack stack, String name, List<String> lore, PetLevel level) {
		public List<Component> tooltip() {
			List<Component> tt = new ArrayList<>();
			tt.add(Component.literal(name));
			for (String l : lore) tt.add(Component.literal(l));
			return tt;
		}
	}

	/** Rarity after a Tier Boost, when the boosted pet exists in the repo (NEU Utils.canPetBeTierBoosted). */
	public static int effectiveRarity(JsonObject pet) {
		String type = J.str(pet, "type", "");
		int r = rarityIndex(J.str(pet, "tier", ""));
		if (r >= 0 && r < RARITY_NAMES.length - 1 && "PET_ITEM_TIER_BOOST".equals(J.str(pet, "heldItem", null))
			&& NeuRepo.item(type + ";" + (r + 1)) != null) r++;
		return r;
	}

	public static PetDisplay create(JsonObject pet) {
		String type = J.str(pet, "type", "");
		int rarity = effectiveRarity(pet);
		String tierName = rarity >= 0 ? RARITY_NAMES[rarity] : J.str(pet, "tier", "COMMON");
		String heldItem = J.str(pet, "heldItem", null);
		String skin = J.str(pet, "skin", null);
		int candyUsed = J.i(pet, "candyUsed", 0);
		double exp = J.dbl(pet, "exp", 0);
		PetLevel level = level(type, tierName, exp);

		PvItem repo = rarity >= 0 ? LegacyItems.fromRepo(type + ";" + rarity) : null;
		ItemStack stack;
		String name;
		List<String> lore = new ArrayList<>();
		if (repo == null) {
			stack = LegacyItems.icon(Items.PLAYER_HEAD, "§cUnknown Pet");
			name = "§cUnknown Pet";
			lore.add("§cThis pet is not saved in the repository");
			lore.add("");
			lore.add("§7" + type + " " + tierName);
		} else {
			stack = repo.stack().copy();
			Map<String, String> replacements = loreReplacements(type, tierName, level.currentLevel());
			if (heldItem != null) {
				modify(replacements, PET_STAT_BOOSTS.get(heldItem), (orig, mod) -> "" + (int) Math.floor(Float.parseFloat(orig) + mod));
				modify(replacements, PET_STAT_BOOSTS_MULT.get(heldItem), (orig, mod) -> "" + (int) Math.floor(Float.parseFloat(orig) * mod));
			}
			name = apply(replacements, repo.displayName);
			int secondLastBlank = -1, lastBlank = -1;
			for (int j = 0; j < repo.lore.size(); j++) {
				String line = repo.lore.get(j);
				if (line.trim().isEmpty()) {
					secondLastBlank = lastBlank;
					lastBlank = j;
				}
				lore.add(apply(replacements, line));
			}
			if (skin != null) {
				PvItem petSkin = LegacyItems.fromRepo("PET_SKIN_" + skin);
				if (petSkin != null) {
					var profile = petSkin.stack().get(DataComponents.PROFILE);
					if (profile != null) stack.set(DataComponents.PROFILE, profile);
					if (!lore.isEmpty()) lore.set(0, lore.get(0) + ", " + Fmt.stripColor(petSkin.displayName));
				}
			}
			for (int i = 0; i < lore.size(); i++) {
				if (Fmt.stripColor(lore.get(i)).startsWith("Right-click to add this pet to")) {
					lore.remove(i);
					// older repo items split the sentence on two lines ("your pet menu!")
					if (i < lore.size() - 1 && !lore.get(i).trim().isEmpty()) lore.remove(i);
					if (heldItem == null && i < lore.size() - 1 && lore.get(i).trim().isEmpty()) lore.remove(i);
					secondLastBlank = i - 1;
					break;
				}
			}
			if (secondLastBlank != -1) {
				List<String> petItemLore = new ArrayList<>();
				if (heldItem != null) {
					JsonObject heldJson = NeuRepo.item(heldItem);
					if (heldJson == null) {
						petItemLore.add("§cCould not find held item in repo!");
					} else {
						petItemLore.add("§6Held Item: " + J.str(heldJson, "displayname", heldItem));
						int blanks = 0;
						for (JsonElement e : J.arrOrEmpty(heldJson, "lore")) {
							String l = e.getAsString();
							if (l.trim().isEmpty()) blanks++;
							else if (blanks == 2) petItemLore.add(l);
							else if (blanks > 2) break;
						}
					}
				}
				if (candyUsed > 0) {
					if (!petItemLore.isEmpty()) petItemLore.add("");
					petItemLore.add("§a(" + candyUsed + "/10) Pet Candy Used");
					if (heldItem == null) petItemLore.add("");
				}
				lore.addAll(Math.min(secondLastBlank + 1, lore.size()), petItemLore);
			}
			addXpLines(lore, level);
		}
		stack.set(DataComponents.ITEM_NAME, Component.literal(name));
		return new PetDisplay(pet, type, rarity, stack, name, lore, level);
	}

	private static final DecimalFormat XP_FORMAT = new DecimalFormat("#,###,###.###", DecimalFormatSymbols.getInstance(Locale.US));

	/** NEU ItemUtils.petToolTipXPExtendPetOverlay: level progress and XP, inserted before the rarity line. */
	private static void addXpLines(List<String> lore, PetLevel level) {
		if (lore.size() < 2) return;
		List<String> add = new ArrayList<>();
		add.add("");
		if (level.maxed()) {
			add.add("§b§lMAX LEVEL");
		} else {
			double percent = Math.round(level.percentageToNextLevel() * 1000) / 10.0;
			add.add("§7Progress to Level " + (level.currentLevel() + 1) + ": §e" + percent + "%");
			StringBuilder sb = new StringBuilder();
			for (int j = 0; j < 20; j++) sb.append(j < percent / 5 ? "§2" : "§f").append("§l§m ");
			add.add(sb.toString());
			add.add("§7EXP: §e" + XP_FORMAT.format(level.expInCurrentLevel()) + "§6/§e" + XP_FORMAT.format(level.expRequiredForNextLevel()));
		}
		lore.addAll(lore.size() - 2, add);
	}

	private static <T> void modify(Map<String, String> base, Map<String, T> mods, BiFunction<String, T, String> mapper) {
		if (mods == null) return;
		for (Map.Entry<String, T> m : mods.entrySet()) {
			String v = base.get(m.getKey());
			if (v == null) continue;
			try {
				base.put(m.getKey(), (v.startsWith("+") ? "+" : "") + mapper.apply(v, m.getValue()));
			} catch (Exception ignored) {
			}
		}
	}

	private static String apply(Map<String, String> replacements, String text) {
		if (text == null) return "";
		for (Map.Entry<String, String> r : replacements.entrySet()) {
			String search = "{" + r.getKey() + "}";
			String value = r.getValue();
			// the current repo lore already has the sign ("+{STRENGTH}"), NEU's replacement adds one too
			if (value.startsWith("+")) text = text.replace("+" + search, search);
			text = text.replace(search, value);
		}
		return text;
	}

	private static String removeUnusedDecimal(double num) {
		return num % 1 == 0 ? String.valueOf((int) num) : String.valueOf(num);
	}

	/** NEU NEUManager.getPetLoreReplacements (level >= 1 branch; the PV always has a level). */
	static Map<String, String> loreReplacements(String petname, String tier, int level) {
		Map<String, String> replacements = new HashMap<>();
		replacements.put("LVL", String.valueOf(level));
		JsonObject petnums = NeuRepo.constant("petnums");
		JsonObject petInfoTier = J.obj(petnums, petname + "." + tier);
		if (petInfoTier == null || !petInfoTier.has("1") || !petInfoTier.has("100")) return replacements;
		JsonObject min = petInfoTier.getAsJsonObject("1");
		JsonObject max = petInfoTier.getAsJsonObject("100");
		int minStatsLevel = 0, maxStatsLevel = 100, statsLevelingType = -1;
		int statsLevel = level;
		String curve = J.str(petInfoTier, "stats_levelling_curve", null);
		if (curve != null) {
			String[] parts = curve.split(":");
			if (parts.length == 3) {
				minStatsLevel = Integer.parseInt(parts[0]);
				maxStatsLevel = Integer.parseInt(parts[1]);
				statsLevelingType = Integer.parseInt(parts[2]);
				if (statsLevelingType == 0 || statsLevelingType == 1) {
					if (level < minStatsLevel) statsLevel = 1;
					else if (level < maxStatsLevel) statsLevel = level - minStatsLevel + 1;
					else statsLevel = maxStatsLevel - minStatsLevel + 1;
				}
			}
		}
		float minMix = (maxStatsLevel - (minStatsLevel - (statsLevelingType == -1 ? 0 : 1)) - statsLevel) / 99f;
		float maxMix = (statsLevel - 1) / 99f;
		JsonArray otherMin = J.arrOrEmpty(min, "otherNums"), otherMax = J.arrOrEmpty(max, "otherNums");
		for (int i = 0; i < otherMax.size() && i < otherMin.size(); i++) {
			float val = otherMin.get(i).getAsFloat() * minMix + otherMax.get(i).getAsFloat() * maxMix;
			if (statsLevelingType == 1 && level < minStatsLevel) replacements.put(String.valueOf(i), "0");
			else replacements.put(String.valueOf(i), removeUnusedDecimal(Math.floor(val * 10) / 10f));
		}
		JsonObject statMinObj = J.objOrEmpty(min, "statNums");
		for (Map.Entry<String, JsonElement> e : J.objOrEmpty(max, "statNums").entrySet()) {
			if (statsLevelingType == 1 && level < minStatsLevel) {
				replacements.put(e.getKey(), "0");
			} else {
				float statMax = e.getValue().getAsFloat();
				float statMin = statMinObj.has(e.getKey()) ? statMinObj.get(e.getKey()).getAsFloat() : 0;
				float val = statMin * minMix + statMax * maxMix;
				replacements.put(e.getKey(), (statMin > 0 ? "+" : "") + removeUnusedDecimal(Math.floor(val * 10) / 10));
			}
		}
		return replacements;
	}
}
