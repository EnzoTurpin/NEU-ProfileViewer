/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.neupv.NeuPv;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Same options as NEU's "Profile Viewer" config section, plus the proxy URL. Stored in config/neupv.json. */
public class PvConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("neupv.json");
	private static PvConfig instance;

	/**
	 * Base URL of an ursa-minor compatible proxy (https://github.com/NotEnoughUpdates/ursa-minor),
	 * e.g. "https://ursa.example.org". Required: the profile viewer cannot load data without it.
	 */
	public String ursaUrl = "";
	/**
	 * Optional URL of a JSON object {"ITEM_ID": lowestBinPrice, ...} used by the local networth (fallback when Soopy
	 * is disabled or down). NEU's own price server (moulberry.codes) is offline, so there is no default.
	 */
	public String lowestBinUrl = "";
	/** Page order, same ids as NEU (0 = Skills ... 13 = Garden). */
	public List<Integer> pageLayout = new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14));
	public boolean alwaysShowBingoTab = false;
	public boolean showPronounsInPv = false;
	public boolean useSoopyNetworth = true;
	public boolean displayWeight = true;
	public List<String> previousProfileSearches = new ArrayList<>();

	public static PvConfig get() {
		if (instance == null) load();
		return instance;
	}

	public static void load() {
		try {
			if (Files.exists(FILE)) {
				try (Reader r = Files.newBufferedReader(FILE, StandardCharsets.UTF_8)) {
					instance = GSON.fromJson(r, PvConfig.class);
				}
			}
		} catch (Exception e) {
			NeuPv.LOGGER.error("Could not read {}", FILE, e);
		}
		if (instance == null) instance = new PvConfig();
		if (instance.pageLayout == null) instance.pageLayout = new PvConfig().pageLayout;
		// tabs added after a config was written (e.g. 14 = Hunting) are appended so they show up
		for (int id : new PvConfig().pageLayout) if (!instance.pageLayout.contains(id)) instance.pageLayout.add(id);
		if (instance.previousProfileSearches == null) instance.previousProfileSearches = new ArrayList<>();
		save();
	}

	public static void save() {
		try {
			Files.createDirectories(FILE.getParent());
			try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, w);
			}
		} catch (Exception e) {
			NeuPv.LOGGER.error("Could not write {}", FILE, e);
		}
	}

	public String ursaRoot() {
		String s = ursaUrl == null ? "" : ursaUrl.trim();
		while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
		return s;
	}
}
