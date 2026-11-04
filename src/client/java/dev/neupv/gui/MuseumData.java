/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates SkyblockProfiles.SkyblockProfile.MuseumData,
 * Copyright (C) 2023 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.item.LegacyItems;
import dev.neupv.item.PvItem;
import dev.neupv.util.J;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Parsed museum of one member: pure function of the /v2/skyblock/museum answer and the repo's museum.json
 * ({@link #parse}), so it can be checked without network.
 * <p>
 * Supports both museum.json layouts: the one NEU 2.6.0 was written for (top-level "weapons"/"armor"/"rarities"
 * arrays) and the current one (categories under "items": combat, farming, mining, ...). NEU 2.6.0 threw a
 * NullPointerException on the new layout and stayed on "Museum Data Loading!" forever.
 */
public final class MuseumData {
	/** Same codes as NEU: value >= 0 OK, -2 API disabled / member missing, -3 missing repo data. */
	public static final long API_DISABLED = -2, MISSING_REPO = -3;

	/** One donated slot: the donated items (a whole armor set has several), or a "donated as higher tier" placeholder. */
	public record Donation(String id, List<PvItem> items, boolean higherTier) {}

	public record Saved(long donatedTime, boolean borrowing) {}

	public final long value;
	/** Non-special categories, in repo order (e.g. weapons, armor, rarities or combat, farming, ...). */
	public final List<String> categories = new ArrayList<>();
	/** category -> repo item ids, in repo order. */
	public final Map<String, List<String>> categoryItems = new LinkedHashMap<>();
	/** category -> donated id -> donation. */
	public final Map<String, Map<String, Donation>> donated = new LinkedHashMap<>();
	public final List<Donation> special = new ArrayList<>();
	public final Map<String, Saved> saved = new HashMap<>();
	private final JsonObject repo;

	private MuseumData(long value, JsonObject repo) {
		this.value = value;
		this.repo = repo;
	}

	/**
	 * @param response the museum endpoint answer ({"success":true,"members":{uuid:{value, items, special}}})
	 * @param uuid     undashed member uuid
	 * @param repo     NEU-REPO constants/museum.json (may be null)
	 */
	public static MuseumData parse(JsonObject response, String uuid, JsonObject repo) {
		if (repo == null || categoryArrays(repo).isEmpty()) return new MuseumData(MISSING_REPO, repo);
		JsonObject member = J.obj(J.obj(response, "members"), uuid); // uuid has no dots, J path is safe
		if (member == null || J.get(member, "value") == null) return new MuseumData(API_DISABLED, repo);
		MuseumData data = new MuseumData(J.lng(member, "value", 0), repo);
		for (Map.Entry<String, JsonArray> e : categoryArrays(repo).entrySet()) {
			List<String> ids = new ArrayList<>();
			for (JsonElement id : e.getValue()) if (id.isJsonPrimitive()) ids.add(id.getAsString());
			data.categories.add(e.getKey());
			data.categoryItems.put(e.getKey(), ids);
			data.donated.put(e.getKey(), new HashMap<>());
		}

		JsonObject items = J.obj(member, "items");
		if (items != null) {
			for (Map.Entry<String, JsonElement> entry : items.entrySet()) {
				if (!entry.getValue().isJsonObject()) continue;
				JsonObject itemJson = entry.getValue().getAsJsonObject();
				String id = entry.getKey();
				String mapped = J.str(repo, "mapped_ids." + id, null);
				if (mapped != null && !data.isCategoryItem(id)) id = mapped;
				List<PvItem> contents = decode(itemJson);
				data.addSavedAndChildren(id, new Saved(J.lng(itemJson, "donated_time", 0), J.bool(itemJson, "borrowing", false)));
				data.store(new Donation(id, contents, false));
			}
		}
		for (JsonElement e : J.arrOrEmpty(member, "special")) {
			if (!e.isJsonObject()) continue;
			List<PvItem> contents = decode(e.getAsJsonObject());
			// NEU: contents.get(0) threw on an empty special donation
			if (contents.isEmpty()) continue;
			String id = contents.get(0).skyblockId == null ? "?" : contents.get(0).skyblockId;
			data.addSavedAndChildren(id, new Saved(J.lng(e, "donated_time", 0), false));
			data.special.add(new Donation(id, contents, false));
		}
		return data;
	}

	/** Non-special category -> item id array, for both repo layouts. */
	static Map<String, JsonArray> categoryArrays(JsonObject repo) {
		Map<String, JsonArray> out = new LinkedHashMap<>();
		if (repo == null) return out;
		if (J.arr(repo, "weapons") != null) {
			for (String c : List.of("weapons", "armor", "rarities")) out.put(c, J.arrOrEmpty(repo, c));
			return out;
		}
		JsonObject items = J.obj(repo, "items");
		if (items == null) return out;
		for (Map.Entry<String, JsonElement> e : items.entrySet()) {
			if (e.getKey().equals("special") || !e.getValue().isJsonArray()) continue;
			out.put(e.getKey(), e.getValue().getAsJsonArray());
		}
		return out;
	}

	private static List<PvItem> decode(JsonObject itemJson) {
		List<PvItem> out = new ArrayList<>();
		for (PvItem i : LegacyItems.decodeInventory(J.str(itemJson, "items.data", null))) if (i != null) out.add(i);
		return out;
	}

	private boolean isCategoryItem(String id) {
		for (List<String> ids : categoryItems.values()) if (ids.contains(id)) return true;
		return false;
	}

	/** NEU storeItem/processItems: files the donation under every category listing its id. */
	private void store(Donation d) {
		for (String cat : categories) {
			if (!categoryItems.get(cat).contains(d.id)) continue;
			Map<String, Donation> map = donated.get(cat);
			// NEU let a "higher tier" placeholder overwrite an item that really is in the museum
			if (d.higherTier && map.containsKey(d.id) && !map.get(d.id).higherTier) continue;
			map.put(d.id, d);
		}
	}

	/** NEU getDataAndChildren: lower tiers of a donated item count as donated ("Donated as higher tier"). */
	private void addSavedAndChildren(String name, Saved data) {
		JsonObject children = J.objOrEmpty(repo, "children");
		for (int guard = 0; guard < 64 && name != null; guard++) {
			if (saved.containsKey(name)) return;
			saved.put(name, data);
			String child = J.str(children, name, null);
			if (child == null) return;
			store(new Donation(child, Collections.emptyList(), true));
			name = child;
		}
	}

	/** Repo id -> NEU item id (armor sets are keyed by set name: ABYSSAL -> ABYSSAL_HELMET). */
	public String itemIdFor(String museumId) {
		return J.str(J.objOrEmpty(repo, "armor_to_id"), museumId, museumId);
	}

	public int maximum(String category) {
		return J.i(J.objOrEmpty(repo, "max_values"), category, 1);
	}

	public int totalDonated() {
		int n = 0;
		for (Map<String, Donation> m : donated.values()) n += m.size();
		return n;
	}

	public int donatedCount(String category) {
		if (category.equals("special")) return special.size();
		return donated.getOrDefault(category, Map.of()).size();
	}

	public boolean ok() {
		return value >= 0;
	}

	@Override
	public String toString() {
		return "MuseumData{value=" + value + ", donated=" + totalDonated() + ", special=" + special.size() + "}";
	}

	public boolean isDonated(String category, String id) {
		return donated.getOrDefault(category, Map.of()).containsKey(Objects.requireNonNull(id));
	}
}
