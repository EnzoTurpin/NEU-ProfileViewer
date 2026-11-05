/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates HoppityLeaderboardRank.kt, Copyright (C) 2024 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.util.Fmt;
import dev.neupv.util.J;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;

import java.net.URI;

/**
 * Elitebot chocolate leaderboard rank of the viewed player/profile. NEU kept this in a global object reset on
 * profile change; here the request is cached per URL by {@link SkyblockProfiles#fetchUrl}, so switching profiles
 * simply polls another URL.
 */
final class ExtraHoppityLeaderboardRank {
	enum Status { LOADING, TOO_LOW, FOUND, ERROR }

	private ExtraHoppityLeaderboardRank() {}

	record Rank(Status status, int rank) {
		String display() {
			return switch (status) {
				case LOADING -> "Loading...";
				case TOO_LOW -> "Too Low";
				case FOUND -> Fmt.number(rank);
				case ERROR -> "Error";
			};
		}

		String additionalInfo() {
			return switch (status) {
				case LOADING -> "§eStill loading data.";
				case TOO_LOW -> "§eLeaderboard only has top 5,000 players.";
				case FOUND -> "§7#§b" + Fmt.number(rank) + " §7on the Elitebot chocolate leaderboard.";
				case ERROR -> "§cError while fetching leaderboard rank, try again later.";
			};
		}
	}

	/** Polled every frame. */
	static Rank get(SkyblockProfiles profiles, String uuid, String profileId) {
		if (uuid == null || profileId == null) return new Rank(Status.ERROR, -1);
		SkyblockProfiles.Fetch f = profiles.fetchUrl("https://api.elitebot.dev/leaderboard/rank/chocolate/" + uuid + "/" + profileId);
		return switch (f.state()) {
			case LOADING -> new Rank(Status.LOADING, -1);
			case ERROR -> {
				// errors when the player has never been loaded on elitebot before: load their account to add them to it
				profiles.fetchUrl("https://api.elitebot.dev/account/" + uuid);
				yield new Rank(Status.ERROR, -1);
			}
			case OK -> {
				int rank = J.i(f.json(), "rank", -1);
				yield rank == -1 ? new Rank(Status.TOO_LOW, -1) : new Rank(Status.FOUND, rank);
			}
		};
	}

	static void openWebsite(net.minecraft.client.gui.screens.Screen screen, Rank rank) {
		if (rank.status() == Status.LOADING) return;
		ConfirmLinkScreen.confirmLinkNow(screen, URI.create("https://elitebot.dev/leaderboard/chocolate"));
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
	}
}
