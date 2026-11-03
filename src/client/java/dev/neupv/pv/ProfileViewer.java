/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates ProfileViewer.java, Copyright (C) 2022 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.pv;

import dev.neupv.config.PvConfig;
import dev.neupv.net.Mojang;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class ProfileViewer {
	private static final Map<UUID, SkyblockProfiles> PROFILES = new HashMap<>();

	public sealed interface LoadResult {
		record Found(SkyblockProfiles profiles) implements LoadResult {}

		record NotFound(String name) implements LoadResult {}

		record Failed(String message) implements LoadResult {}
	}

	private ProfileViewer() {}

	/** Resolves the name and calls back on the client thread, in every case (NEU never called back for unknown names). */
	public static void loadPlayerByName(String name, Consumer<LoadResult> callback) {
		Minecraft mc = Minecraft.getInstance();
		Mojang.uuidOf(name).whenComplete((uuid, err) -> mc.execute(() -> {
			if (err != null) {
				callback.accept(new LoadResult.Failed("Mojang API unreachable"));
			} else if (uuid.isEmpty()) {
				callback.accept(new LoadResult.NotFound(name));
			} else {
				SkyblockProfiles p = PROFILES.computeIfAbsent(uuid.get(), SkyblockProfiles::new);
				p.resetCache();
				callback.accept(new LoadResult.Found(p));
			}
		}));
	}

	public static void saveSearch(String username) {
		if (username == null) return;
		String lower = username.toLowerCase(Locale.ROOT);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && lower.equals(mc.player.getGameProfile().name().toLowerCase(Locale.ROOT))) return;
		var list = PvConfig.get().previousProfileSearches;
		list.remove(lower);
		list.add(0, lower);
		while (list.size() > 6) list.remove(list.size() - 1);
		PvConfig.save();
	}
}
