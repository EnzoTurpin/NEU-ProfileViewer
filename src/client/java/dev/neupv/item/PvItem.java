/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * One SkyBlock item as read from the Hypixel API (legacy 1.8.9 NBT). Plays the role of the JSON items NEU builds
 * with getJsonFromNBTEntry: the PV logic works on the SkyBlock id / name / lore, the ItemStack is only for icons.
 */
public final class PvItem {
	public final String skyblockId;
	public final String displayName;
	public final List<String> lore;
	public final int count;
	public final CompoundTag legacyTag;
	private final LegacyItems.LegacyStackSpec spec;
	private ItemStack stack;

	PvItem(String skyblockId, String displayName, List<String> lore, int count, CompoundTag legacyTag, LegacyItems.LegacyStackSpec spec) {
		this.skyblockId = skyblockId;
		this.displayName = displayName;
		this.lore = lore;
		this.count = count;
		this.legacyTag = legacyTag;
		this.spec = spec;
	}

	/** Built lazily, on first render (needs the item registries). */
	public ItemStack stack() {
		if (stack == null) stack = LegacyItems.build(spec);
		return stack;
	}

	/** Rarity index from the last lore lines (0 = common ... 5 = mythic, 6 special, 7 very special, 8 divine), -1 if none. */
	public int rarity() {
		return LegacyItems.rarityFromLore(lore);
	}
}
