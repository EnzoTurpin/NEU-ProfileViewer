/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates NEUManager / Constants, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.repo;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.neupv.NeuPv;
import dev.neupv.net.Http;
import net.fabricmc.loader.api.FabricLoader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads the NEU data repository (https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO, MIT) into
 * config/neupv/repo, like NEU does, and exposes the constants used by the profile viewer.
 * <p>
 * weight.json and sblevels.json were deleted upstream on 2026-10-30 (PR #3221); when missing, the copy embedded
 * in this mod (taken from the last commit that had them) is used instead.
 */
public final class NeuRepo {
	private static final String OWNER_REPO = "NotEnoughUpdates/NotEnoughUpdates-REPO";
	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("neupv").resolve("repo");
	private static final Path COMMIT_FILE = DIR.resolveSibling("repo-commit.txt");
	private static final Map<String, JsonObject> CONSTANTS = new ConcurrentHashMap<>();
	private static final Map<String, Optional<JsonObject>> ITEMS = new ConcurrentHashMap<>();
	private static volatile boolean loaded = false;
	private static volatile String commit = "unknown";

	private NeuRepo() {}

	public static boolean isLoaded() {
		return loaded;
	}

	public static String commit() {
		return commit;
	}

	/** Any constants/NAME.json of the repo (loaded on first use), or the copy embedded in the mod, or null. */
	public static JsonObject constant(String name) {
		JsonObject o = CONSTANTS.get(name);
		if (o != null || !loaded) return o;
		o = readJson(DIR.resolve("constants").resolve(name + ".json"));
		if (o == null) o = readEmbedded(name);
		if (o != null) CONSTANTS.put(name, o);
		return o;
	}

	public static JsonObject leveling() {
		return constant("leveling");
	}

	public static JsonObject misc() {
		return constant("misc");
	}

	public static JsonObject parents() {
		return constant("parents");
	}

	public static JsonObject weight() {
		return constant("weight");
	}

	public static JsonObject sblevels() {
		return constant("sblevels");
	}

	/** NEU repo item json (items/ID.json), or null. */
	public static JsonObject item(String internalName) {
		if (internalName == null) return null;
		return ITEMS.computeIfAbsent(internalName, id -> {
			Path p = DIR.resolve("items").resolve(id + ".json");
			if (!Files.isRegularFile(p)) return Optional.empty();
			try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
				return Optional.of(JsonParser.parseReader(r).getAsJsonObject());
			} catch (Exception e) {
				return Optional.empty();
			}
		}).orElse(null);
	}

	public static CompletableFuture<Void> loadAsync() {
		return CompletableFuture.runAsync(() -> {
			try {
				update();
			} catch (Exception e) {
				NeuPv.LOGGER.error("Could not update the NEU repo, using the local copy if any", e);
			}
			reloadConstants();
		});
	}

	private static void update() throws Exception {
		JsonObject latest = Http.send("GET", "https://api.github.com/repos/" + OWNER_REPO + "/commits/master", null, null)
			.get().json();
		String sha = latest.get("sha").getAsString();
		if (Files.exists(COMMIT_FILE) && Files.readString(COMMIT_FILE).trim().equals(sha) && Files.isDirectory(DIR.resolve("constants"))) {
			commit = sha;
			return;
		}
		NeuPv.LOGGER.info("Downloading NEU repo {}", sha);
		byte[] zip = Http.send("GET", "https://github.com/" + OWNER_REPO + "/archive/" + sha + ".zip", null, null).get().body();
		Path tmp = DIR.resolveSibling("repo-new");
		deleteRecursively(tmp);
		try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zip))) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				String name = entry.getName();
				int slash = name.indexOf('/');
				if (slash < 0) continue;
				String rel = name.substring(slash + 1);
				if (!(rel.startsWith("constants/") || rel.startsWith("items/")) || entry.isDirectory()) continue;
				Path out = tmp.resolve(rel).normalize();
				if (!out.startsWith(tmp)) continue; // zip-slip guard
				Files.createDirectories(out.getParent());
				Files.copy(zis, out);
			}
		}
		deleteRecursively(DIR);
		Files.move(tmp, DIR);
		Files.writeString(COMMIT_FILE, sha);
		commit = sha;
		ITEMS.clear();
	}

	private static void reloadConstants() {
		for (String name : new String[]{"leveling", "misc", "parents", "pets", "petnums", "weight", "sblevels", "bestiary",
			"museum", "trophyfish", "essenceshops", "fairy_souls", "garden", "hoppity", "sacks", "hotmlayout", "rngscore"}) {
			JsonObject o = readJson(DIR.resolve("constants").resolve(name + ".json"));
			if (o == null) o = readEmbedded(name);
			if (o != null) CONSTANTS.put(name, o);
			else CONSTANTS.remove(name);
		}
		try {
			if (Files.exists(COMMIT_FILE)) commit = Files.readString(COMMIT_FILE).trim();
		} catch (Exception ignored) {
		}
		loaded = true;
		NeuPv.LOGGER.info("NEU repo constants loaded ({} files, commit {})", CONSTANTS.size(), commit);
	}

	private static JsonObject readJson(Path p) {
		if (!Files.isRegularFile(p)) return null;
		try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
			JsonElement e = JsonParser.parseReader(r);
			return e.isJsonObject() ? e.getAsJsonObject() : null;
		} catch (Exception e) {
			NeuPv.LOGGER.warn("Could not read {}", p, e);
			return null;
		}
	}

	private static JsonObject readEmbedded(String name) {
		try (InputStream in = NeuRepo.class.getResourceAsStream("/assets/neupv/data/" + name + ".json")) {
			if (in == null) return null;
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}

	private static void deleteRecursively(Path p) throws Exception {
		if (!Files.exists(p)) return;
		try (Stream<Path> s = Files.walk(p)) {
			for (Path f : s.sorted(Comparator.reverseOrder()).toList()) Files.delete(f);
		}
	}
}
