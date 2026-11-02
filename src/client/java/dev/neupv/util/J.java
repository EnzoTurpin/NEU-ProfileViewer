/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates (https://github.com/NotEnoughUpdates/NotEnoughUpdates),
 * Copyright (C) 2022-2025 NotEnoughUpdates contributors, licensed under LGPL-3.0-or-later.
 */
package dev.neupv.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Dotted-path JSON helpers, equivalent of NEU's Utils.getElement* family. */
public final class J {
	private J() {}

	public static JsonElement get(JsonElement element, String path) {
		JsonElement cur = element;
		for (String part : path.split("\\.")) {
			if (cur == null || !cur.isJsonObject()) return null;
			cur = cur.getAsJsonObject().get(part);
		}
		return cur == null || cur.isJsonNull() ? null : cur;
	}

	public static JsonObject obj(JsonElement element, String path) {
		JsonElement e = get(element, path);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	public static JsonObject objOrEmpty(JsonElement element, String path) {
		JsonObject o = obj(element, path);
		return o == null ? new JsonObject() : o;
	}

	public static JsonArray arr(JsonElement element, String path) {
		JsonElement e = get(element, path);
		return e != null && e.isJsonArray() ? e.getAsJsonArray() : null;
	}

	public static JsonArray arrOrEmpty(JsonElement element, String path) {
		JsonArray a = arr(element, path);
		return a == null ? new JsonArray() : a;
	}

	public static String str(JsonElement element, String path, String def) {
		JsonElement e = get(element, path);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
		} catch (Exception ex) {
			return def;
		}
	}

	public static double dbl(JsonElement element, String path, double def) {
		JsonElement e = get(element, path);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsDouble() : def;
		} catch (Exception ex) {
			return def;
		}
	}

	public static long lng(JsonElement element, String path, long def) {
		JsonElement e = get(element, path);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsLong() : def;
		} catch (Exception ex) {
			return def;
		}
	}

	public static int i(JsonElement element, String path, int def) {
		return (int) lng(element, path, def);
	}

	public static boolean bool(JsonElement element, String path, boolean def) {
		JsonElement e = get(element, path);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsBoolean() : def;
		} catch (Exception ex) {
			return def;
		}
	}
}
