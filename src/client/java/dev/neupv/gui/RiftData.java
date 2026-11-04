/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Replaces NotEnoughUpdates' typed APIDataJson.Rift (Copyright (C) 2023 NotEnoughUpdates contributors,
 * LGPL-3.0-or-later) with J.* paths, and caches the decoded rift inventories.
 */
package dev.neupv.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.item.LegacyItems;
import dev.neupv.item.PvItem;
import dev.neupv.util.J;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Rift data of one member json ("rift" object), decoded once. Inventories are null when absent from the API. */
final class RiftData {
	final JsonObject member;
	final List<PvItem> inventory, armor, equipment, enderChest;
	final List<String> foundCats;
	/** null when the API has no montezuma pet (NEU's default type "UNKNOWN"). */
	final String montezumaType, montezumaTier;
	final int foundSouls, grubberStacks;
	final List<String> securedTrophies;
	final double motes;
	final long lifetimeMotes;

	private RiftData(JsonObject member) {
		this.member = member;
		JsonObject rift = J.obj(member, "rift");
		inventory = decode(rift, "inventory.inv_contents.data");
		armor = decode(rift, "inventory.inv_armor.data");
		equipment = decode(rift, "inventory.equipment_contents.data");
		enderChest = decode(rift, "inventory.ender_chest_contents.data");
		foundCats = strings(rift, "dead_cats.found_cats");
		String type = J.str(rift, "dead_cats.montezuma.type", null);
		montezumaType = type == null || type.equals("UNKNOWN") ? null : type;
		montezumaTier = J.str(rift, "dead_cats.montezuma.tier", "EPIC");
		foundSouls = J.arrOrEmpty(rift, "enigma.found_souls").size();
		grubberStacks = J.i(rift, "castle.grubber_stacks", 0);
		List<String> trophies = new ArrayList<>();
		if (J.arr(rift, "gallery.secured_trophies") != null) {
			for (JsonElement e : J.arr(rift, "gallery.secured_trophies")) {
				String t = e.isJsonObject() ? J.str(e, "type", null) : null;
				if (t != null) trophies.add(t);
			}
			securedTrophies = trophies;
		} else {
			securedTrophies = null;
		}
		motes = J.dbl(member, "currencies.motes_purse", 0);
		lifetimeMotes = J.lng(member, "player_stats.rift.lifetime_motes_earned", 0);
	}

	private static List<PvItem> decode(JsonObject rift, String path) {
		String data = J.str(rift, path, null);
		return data == null ? null : LegacyItems.decodeInventory(data);
	}

	private static List<String> strings(JsonObject rift, String path) {
		if (J.arr(rift, path) == null) return null;
		List<String> out = new ArrayList<>();
		for (JsonElement e : J.arr(rift, path)) if (e.isJsonPrimitive()) out.add(e.getAsString());
		return Collections.unmodifiableList(out);
	}

	private static RiftData cached;

	/** Decoded data for this member json (decoding gzip NBT every frame would be wasteful). */
	static RiftData of(JsonObject member) {
		if (cached == null || cached.member != member) cached = new RiftData(member);
		return cached;
	}
}
