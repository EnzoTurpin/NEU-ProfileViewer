/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Not in NEU: Hypixel moved Heart of the Mountain data (2025) from "mining_core" to "skill_tree".
 */
package dev.neupv.pv;

import com.google.gson.JsonObject;
import dev.neupv.util.J;

/**
 * Reads Heart of the Mountain data from the current API layout, falling back to the old one:
 * <ul>
 *   <li>experience: skill_tree.experience.mining (was mining_core.experience)</li>
 *   <li>perks: skill_tree.nodes.&lt;slot&gt; for the selected loadout (was mining_core.nodes); the slot comes from
 *   skill_tree.selected_skill_tree_slot.mining (1 = "mining", 2 = "mining_2", ...)</li>
 *   <li>pickaxe ability: skill_tree.selected_ability.&lt;slot&gt; (was mining_core.selected_pickaxe_ability)</li>
 *   <li>Peak of the Mountain: "core_of_the_mountain" (was "special_0")</li>
 * </ul>
 */
public final class SkillTree {
	private SkillTree() {}

	private static String slot(JsonObject member) {
		int n = J.i(member, "skill_tree.selected_skill_tree_slot.mining", 1);
		return n <= 1 ? "mining" : "mining_" + n;
	}

	/** Suffix of per-tree keys for the selected HOTM tree: "" for tree 1, "_2" for tree 2, ... (e.g. powder_spent_mithril_2). */
	public static String hotmSlotSuffix(JsonObject member) {
		int n = J.i(member, "skill_tree.selected_skill_tree_slot.mining", 1);
		return n <= 1 ? "" : "_" + n;
	}

	public static double hotmExperience(JsonObject member) {
		if (J.get(member, "skill_tree.experience.mining") != null) return J.dbl(member, "skill_tree.experience.mining", 0);
		return J.dbl(member, "mining_core.experience", 0);
	}

	public static JsonObject hotmNodes(JsonObject member) {
		JsonObject nodes = J.obj(member, "skill_tree.nodes." + slot(member));
		if (nodes == null) nodes = J.obj(member, "skill_tree.nodes.mining");
		if (nodes == null) nodes = J.obj(member, "mining_core.nodes");
		return nodes == null ? new JsonObject() : nodes;
	}

	public static String hotmSelectedAbility(JsonObject member) {
		String a = J.str(member, "skill_tree.selected_ability." + slot(member), null);
		return a != null ? a : J.str(member, "mining_core.selected_pickaxe_ability", null);
	}

	/** Peak of the Mountain level, under its new or old id. */
	public static int peakOfTheMountain(JsonObject member) {
		JsonObject nodes = hotmNodes(member);
		return J.i(nodes, "core_of_the_mountain", J.i(nodes, "special_0", 0));
	}

	// ---- Heart of the Forest (Galatea, 2025): only exists in the skill_tree layout ----

	/** Selected HOTF loadout, 1-based (skill_tree.selected_skill_tree_slot.foraging). */
	public static int hotfSlotNumber(JsonObject member) {
		return Math.max(1, J.i(member, "skill_tree.selected_skill_tree_slot.foraging", 1));
	}

	/** Key of a per-tree entry: base for tree 1, base_N for tree N ("foraging", "foraging_2", "forest_3"...). */
	public static String hotfKey(String base, int slot) {
		return slot <= 1 ? base : base + "_" + slot;
	}

	public static double hotfExperience(JsonObject member) {
		return J.dbl(member, "skill_tree.experience.foraging", 0);
	}

	/** HOTF level from leveling.json "HOTF" (per-level xp, like HOTM) capped by leveling_caps.HOTF. */
	public static Level hotfLevel(JsonObject member, JsonObject leveling) {
		return Level.of(J.arr(leveling, "HOTF"), (float) hotfExperience(member), Level.cap(leveling, "HOTF"), false);
	}

	public static JsonObject hotfNodes(JsonObject member) {
		JsonObject nodes = J.obj(member, "skill_tree.nodes." + hotfKey("foraging", hotfSlotNumber(member)));
		if (nodes == null) nodes = J.obj(member, "skill_tree.nodes.foraging");
		return nodes == null ? new JsonObject() : nodes;
	}

	public static String hotfSelectedAbility(JsonObject member) {
		return J.str(member, "skill_tree.selected_ability." + hotfKey("foraging", hotfSlotNumber(member)), null);
	}

	/** Tokens of the Forest spent in a tree (skill_tree.tokens_spent.forest, forest_2...). */
	public static int hotfTokensSpent(JsonObject member, int slot) {
		return J.i(member, "skill_tree.tokens_spent." + hotfKey("forest", slot), 0);
	}

	/** Number of HOTF loadouts the profile has (nodes.foraging_N present), at least 1. */
	public static int hotfTreeCount(JsonObject member) {
		int n = 1;
		while (J.obj(member, "skill_tree.nodes.foraging_" + (n + 1)) != null) n++;
		return n;
	}
}
