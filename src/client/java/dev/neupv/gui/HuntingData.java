/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Not in NEU: Attribute Shards / Safari data (Hypixel updates after NEU 2.6.0).
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.item.LegacyItems;
import dev.neupv.item.PvItem;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.J;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Attribute shards of a profile member (attributes.stacks + shards.owned) joined with NEU-REPO constants/attribute_shards.json. */
public final class HuntingData {
	public static final List<String> RARITY_ORDER = List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY");
	private static final Map<String, ItemStack> ICONS = new HashMap<>();
	private static final Map<String, PvItem> REPO_ITEMS = new HashMap<>();

	/** One attribute (= one shard type) of the repo, with the member's progress. */
	public static final class Attribute {
		public String id;            // attributes.stacks key, e.g. "lifeline"
		public String internalName;  // repo item, e.g. "ATTRIBUTE_SHARD_LIFELINE;1"
		public String shardName;     // "Kada Knight"
		public String abilityName;   // "Lifeline"
		public String rarity;        // "RARE"
		public String alignment;
		public String shardId;       // "R36"
		public List<String> family = new ArrayList<>();
		public int stacks;           // shards absorbed
		public int level, maxLevel;
		public int stacksForNext;    // cumulative shards needed for the next level (0 when maxed)
		public int stacksForMax;
		public int owned;            // shards in the Hunting Box
		public boolean known;        // present in attributes.stacks

		public boolean maxed() {
			return maxLevel > 0 && level >= maxLevel;
		}

		public int rarityIndex() {
			return Math.max(0, RARITY_ORDER.indexOf(rarity));
		}

		public ItemStack icon() {
			return HuntingData.icon(internalName);
		}

		public PvItem repoItem() {
			return HuntingData.repoItem(internalName);
		}
	}

	public static final class Info {
		public final List<Attribute> attributes = new ArrayList<>();
		public int unlocked, maxed, totalStacks, totalLevels, possible;
		public final Map<String, Integer> owned = new LinkedHashMap<>();
		public int ownedTotal, fused;
	}

	private HuntingData() {}

	public static String rarityColor(String rarity) {
		return switch (rarity == null ? "" : rarity) {
			case "UNCOMMON" -> "§a";
			case "RARE" -> "§9";
			case "EPIC" -> "§5";
			case "LEGENDARY" -> "§6";
			case "MYTHIC" -> "§d";
			default -> "§f";
		};
	}

	static ItemStack icon(String internalName) {
		return ICONS.computeIfAbsent(internalName == null ? "" : internalName, id -> {
			PvItem item = repoItem(id);
			return item != null ? item.stack() : LegacyItems.icon(Items.PRISMARINE_SHARD, "§fShard");
		});
	}

	static PvItem repoItem(String internalName) {
		if (internalName == null) return null;
		if (REPO_ITEMS.containsKey(internalName)) return REPO_ITEMS.get(internalName);
		PvItem item = LegacyItems.fromRepo(internalName);
		REPO_ITEMS.put(internalName, item);
		return item;
	}

	/** @return null when the repo constant is missing */
	public static Info compute(JsonObject member) {
		JsonObject constants = NeuRepo.constant("attribute_shards");
		if (constants == null || !constants.has("attributes")) return null;
		JsonObject leveling = J.objOrEmpty(constants, "attribute_levelling");
		Set<String> unconsumable = new HashSet<>();
		for (JsonElement e : J.arrOrEmpty(constants, "unconsumable_attributes")) unconsumable.add(e.getAsString());

		Info info = new Info();
		info.fused = J.i(member, "shards.fused", 0);
		Map<String, Integer> ownedByShard = new HashMap<>();
		for (JsonElement e : J.arrOrEmpty(member, "shards.owned")) {
			if (!e.isJsonObject()) continue;
			String type = J.str(e, "type", null);
			int amount = J.i(e, "amount_owned", 0);
			if (type == null) continue;
			ownedByShard.merge("SHARD_" + type, amount, Integer::sum);
			info.owned.merge(type, amount, Integer::sum);
			info.ownedTotal += amount;
		}

		JsonObject stacks = J.objOrEmpty(member, "attributes.stacks");
		Set<String> seen = new HashSet<>();
		for (JsonElement e : J.arrOrEmpty(constants, "attributes")) {
			if (!e.isJsonObject()) continue;
			Attribute a = new Attribute();
			a.internalName = J.str(e, "internalName", "");
			String base = a.internalName.contains(";") ? a.internalName.substring(0, a.internalName.indexOf(';')) : a.internalName;
			a.id = base.replaceFirst("^ATTRIBUTE_SHARD_", "").toLowerCase(Locale.ROOT);
			String bazaar = J.str(e, "bazaarName", "");
			a.shardName = J.str(e, "displayName", a.id);
			a.abilityName = J.str(e, "abilityName", a.id);
			a.rarity = J.str(e, "rarity", "COMMON");
			a.alignment = J.str(e, "alignment", null);
			a.shardId = J.str(e, "shardId", null);
			for (JsonElement f : J.arrOrEmpty(e, "family")) a.family.add(f.getAsString());
			a.owned = ownedByShard.getOrDefault(bazaar, 0);
			a.known = stacks.has(a.id);
			a.stacks = J.i(stacks, a.id, 0);
			seen.add(a.id);
			if (unconsumable.contains(bazaar) && !a.known) continue; // e.g. Chameleon: cannot be absorbed
			level(a, J.arr(leveling, a.rarity));
			info.attributes.add(a);
		}
		// attributes the repo does not know yet
		for (String id : stacks.keySet()) {
			if (seen.contains(id)) continue;
			Attribute a = new Attribute();
			a.id = id;
			a.internalName = "ATTRIBUTE_SHARD_" + id.toUpperCase(Locale.ROOT) + ";1";
			a.shardName = a.abilityName = HuntingPage.titleCase(id);
			a.rarity = "COMMON";
			a.known = true;
			a.stacks = J.i(stacks, id, 0);
			a.level = a.stacks > 0 ? 1 : 0;
			info.attributes.add(a);
		}

		for (Attribute a : info.attributes) {
			info.possible++;
			if (a.known && a.stacks > 0) info.unlocked++;
			if (a.maxed()) info.maxed++;
			info.totalStacks += a.stacks;
			info.totalLevels += a.level;
		}
		info.attributes.sort(Comparator.comparingInt((Attribute a) -> a.stacks > 0 ? 0 : 1)
			.thenComparing(Comparator.comparingInt((Attribute a) -> a.level).reversed())
			.thenComparing(Comparator.comparingInt(Attribute::rarityIndex).reversed())
			.thenComparing(a -> a.abilityName));
		return info;
	}

	/** attribute_levelling.RARITY = shards needed for each level (not cumulative). */
	private static void level(Attribute a, JsonArray perLevel) {
		if (perLevel == null) return;
		a.maxLevel = perLevel.size();
		int cumulative = 0;
		a.level = 0;
		a.stacksForNext = 0;
		for (JsonElement e : perLevel) {
			cumulative += e.getAsInt();
			if (a.stacks >= cumulative) a.level++;
			else if (a.stacksForNext == 0) a.stacksForNext = cumulative;
		}
		a.stacksForMax = cumulative;
	}
}
