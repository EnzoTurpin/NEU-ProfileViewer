/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates NEUManager.doesStackMatchSearch / multiSearchString / searchString,
 * Copyright (C) 2022-2024 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import dev.neupv.item.PvItem;

import java.util.Locale;

/** Item search of the Storage tab: same query language as NEU ("title:", "desc:", "id:", "|" and "&"). */
final class InventoriesSearch {
	private InventoriesSearch() {}

	static boolean matches(PvItem item, String query) {
		if (item == null) return false;
		String name = item.displayName == null ? "" : item.displayName;
		if (query.startsWith("title:")) return multiSearchString(name, query.substring(6));
		if (query.startsWith("desc:")) return multiSearchString(String.join(" ", item.lore) + " ", query.substring(5));
		if (query.startsWith("id:")) return query.substring(3).equalsIgnoreCase(item.skyblockId);
		boolean result = false;
		if (!query.trim().contains(" ")) {
			StringBuilder sb = new StringBuilder();
			for (char c : query.toCharArray()) sb.append(c).append(' ');
			result = multiSearchString(name, sb.toString());
		}
		result = result || multiSearchString(name, query);
		StringBuilder lore = new StringBuilder();
		Integer color = item.legacyTag.getCompoundOrEmpty("display").getInt("color").orElse(null);
		// NEU: "#rrggbb" of leather armour (only dyed leather carries display.color in SkyBlock data)
		if (color != null) lore.append(String.format("#%06x ", color));
		for (String l : item.lore) lore.append(l).append(' ');
		return result || multiSearchString(lore.toString(), query);
	}

	private static boolean multiSearchString(String match, String query) {
		boolean totalMatches = false;
		StringBuilder query2 = new StringBuilder();
		char lastOp = '|';
		for (char c : query.toCharArray()) {
			if (c == '|' || c == '&') {
				boolean m = searchString(match, query2.toString());
				totalMatches = lastOp == '|' ? totalMatches || m : totalMatches && m;
				query2 = new StringBuilder();
				lastOp = c;
			} else {
				query2.append(c);
			}
		}
		boolean m = searchString(match, query2.toString());
		return lastOp == '|' ? totalMatches || m : totalMatches && m;
	}

	private static String clean(String str) {
		return str.replaceAll("(§.)|[^#0-9a-zA-Z ]", "").toLowerCase(Locale.ROOT).trim();
	}

	private static boolean searchString(String toSearch, String query) {
		int lastStringMatch = -1;
		String[] splitToSearch = clean(toSearch).split(" ");
		String[] queryArray = clean(query).split(" ");
		String currentSearch = queryArray[0];
		int queryIndex = 0;
		boolean matchedLastQueryItem = false;
		for (int k = 0; k < splitToSearch.length; k++) {
			if (queryIndex - 1 != -1 && (queryArray.length - queryIndex) > (splitToSearch.length - k)) continue;
			if (splitToSearch[k].startsWith(currentSearch)) {
				if ((lastStringMatch != -1 ? lastStringMatch : k - 1) == k - 1) {
					lastStringMatch = k;
					if (queryIndex + 1 != queryArray.length) {
						queryIndex++;
						currentSearch = queryArray[queryIndex];
					} else {
						matchedLastQueryItem = true;
					}
				}
			} else if (queryIndex != 0) {
				queryIndex = 0;
				currentSearch = queryArray[queryIndex];
				lastStringMatch = -1;
			}
		}
		return matchedLastQueryItem;
	}
}
