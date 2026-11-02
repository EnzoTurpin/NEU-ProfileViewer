/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.net;

import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Name to UUID lookup. Fix compared to NEU: an unknown name (Mojang answers 404) completes with
 * Optional.empty(), a network error completes exceptionally; in NEU both silently did nothing.
 */
public final class Mojang {
	private static final Map<String, UUID> NAME_TO_UUID = new ConcurrentHashMap<>();

	private Mojang() {}

	public static void remember(String name, UUID uuid) {
		NAME_TO_UUID.put(name.toLowerCase(Locale.ROOT), uuid);
	}

	public static CompletableFuture<Optional<UUID>> uuidOf(String name) {
		String lower = name.toLowerCase(Locale.ROOT);
		UUID cached = NAME_TO_UUID.get(lower);
		if (cached != null) return CompletableFuture.completedFuture(Optional.of(cached));
		return Http.getJson("https://api.minecraftservices.com/minecraft/profile/lookup/name/" + lower)
			.handle((json, err) -> {
				if (err != null) {
					int status = Http.statusOf(err);
					if (status == 404 || status == 204) return Optional.<UUID>empty();
					throw new RuntimeException(err);
				}
				Optional<UUID> id = parse(json);
				id.ifPresent(u -> NAME_TO_UUID.put(lower, u));
				return id;
			});
	}

	private static Optional<UUID> parse(JsonObject json) {
		if (json == null || !json.has("id")) return Optional.empty();
		return Optional.of(dashless(json.get("id").getAsString()));
	}

	public static UUID dashless(String s) {
		String d = s.replace("-", "");
		return new UUID(Long.parseUnsignedLong(d.substring(0, 16), 16), Long.parseUnsignedLong(d.substring(16, 32), 16));
	}

	public static String undashed(UUID uuid) {
		return uuid.toString().replace("-", "");
	}
}
