/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates TrophyFish.java, Copyright (C) 2022 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import java.util.EnumMap;
import java.util.Map;

/** One trophy fish type of a profile: catches per rarity and total. */
public final class TrophyFish {
	public enum Rarity { BRONZE, SILVER, GOLD, DIAMOND }

	/** Internal (API) name, e.g. "golden_fish", "obfuscated_fish_1". */
	public final String internalName;
	public final Map<Rarity, Integer> rarities = new EnumMap<>(Rarity.class);
	public int total;

	public TrophyFish(String internalName) {
		this.internalName = internalName;
	}

	public void add(Rarity rarity, int value) {
		rarities.putIfAbsent(rarity, value);
	}

	/** Highest rarity caught, or null. */
	public Rarity highest() {
		Rarity best = null;
		for (Rarity r : rarities.keySet()) if (best == null || r.ordinal() > best.ordinal()) best = r;
		return best;
	}

	public int sumOfRarities() {
		int s = 0;
		for (int v : rarities.values()) s += Math.max(0, v);
		return s;
	}
}
