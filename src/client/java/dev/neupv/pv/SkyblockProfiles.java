/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates SkyblockProfiles.java, Copyright (C) 2023 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.pv;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.config.PvConfig;
import dev.neupv.item.LegacyItems;
import dev.neupv.item.PvItem;
import dev.neupv.net.Http;
import dev.neupv.net.Mojang;
import dev.neupv.net.UrsaClient;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.J;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * All data of one player. Every field is only read and written on the client thread (network callbacks hop back
 * with Minecraft#execute), which removes the HashMap races of the original.
 */
public class SkyblockProfiles {
	public enum State { LOADING, LOADED, NO_SKYBLOCK, ERROR }

	private static final List<String> INVENTORY_NAMES = List.of("inv_armor", "fishing_bag", "quiver", "ender_chest_contents",
		"backpack_contents", "personal_vault_contents", "wardrobe_contents", "potion_bag", "inv_contents", "talisman_bag",
		"candy_inventory_contents", "equipment_contents");
	private static final List<String> SKILLS = List.of("taming", "mining", "foraging", "enchanting", "carpentry", "farming",
		"combat", "fishing", "alchemy", "runecrafting", "social");
	private static final String MOULBERRY_UUID = "d0e05de76067454dbeaec6d19d886191";

	private final UUID uuid;
	private final String uuidStr;
	private State state = State.LOADING;
	private String error;
	private long lastAttempt = 0;
	private boolean requestInFlight = false;
	private Map<String, SkyblockProfile> nameToProfile;
	private final List<String> profileNames = new ArrayList<>();
	private JsonArray profilesArray;
	private String latestProfileName;
	private JsonObject hypixelPlayer;
	private JsonObject playerStatus;
	private boolean statusRequested;
	private JsonObject guild;
	private boolean guildRequested;
	private boolean inGuild = true;
	private long soopyNetworthPos = -1, soopyWeightPos = -1; // -1 default, -2 loading, -3 error
	private boolean soopyRequested;

	public SkyblockProfiles(UUID uuid) {
		this.uuid = uuid;
		this.uuidStr = Mojang.undashed(uuid);
	}

	public UUID getUuid() {
		return uuid;
	}

	public String getUuidString() {
		return uuidStr;
	}

	public State getState() {
		return state;
	}

	public String getError() {
		return error;
	}

	public JsonObject getHypixelPlayer() {
		return hypixelPlayer;
	}

	public String getDisplayName() {
		return J.str(hypixelPlayer, "displayname", null);
	}

	public List<String> getProfileNames() {
		return profileNames;
	}

	public String getLatestProfileName() {
		return latestProfileName;
	}

	public SkyblockProfile getProfile(String name) {
		return nameToProfile == null || name == null ? null : nameToProfile.get(name);
	}

	/** Clears everything so the next frame refetches (HTTP responses may still come from the 500 s cache). */
	public void resetCache() {
		state = State.LOADING;
		error = null;
		nameToProfile = null;
		profileNames.clear();
		profilesArray = null;
		playerStatus = null;
		statusRequested = false;
		guild = null;
		guildRequested = false;
		soopyRequested = false;
		soopyNetworthPos = soopyWeightPos = -1;
		lastAttempt = 0;
		fetches.clear();
	}

	/** Called every frame by the screen; starts or retries (every 15 s) the profile request. */
	public void tickLoad() {
		if (state == State.LOADED || state == State.NO_SKYBLOCK || requestInFlight) return;
		long now = System.currentTimeMillis();
		if (now - lastAttempt < 15_000) return;
		lastAttempt = now;
		requestInFlight = true;
		if (hypixelPlayer == null) {
			UrsaClient.get(UrsaClient.player(uuid)).thenAccept(json -> {
				if (J.bool(json, "success", false) && json.get("player") != null && json.get("player").isJsonObject()) {
					hypixelPlayer = json.getAsJsonObject("player");
				}
			});
		}
		UrsaClient.get(UrsaClient.profiles(uuid)).whenComplete((json, err) -> {
			requestInFlight = false;
			if (err != null || json == null || !J.bool(json, "success", false)) {
				state = State.ERROR;
				error = err != null ? describe(err) : J.str(json, "cause", "Unknown error");
				return;
			}
			parseProfiles(json);
		});
	}

	private static String describe(Throwable t) {
		int status = Http.statusOf(t);
		if (status == 429) return "Rate limited by Hypixel, retrying...";
		if (status > 0) return "Proxy answered HTTP " + status;
		while (t.getCause() != null && t.getCause() != t) t = t.getCause();
		return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
	}

	/** Test hook: injects API responses directly, without any network request. */
	public void loadFromJson(JsonObject profilesResponse, JsonObject hypixelPlayer) {
		this.hypixelPlayer = hypixelPlayer;
		parseProfiles(profilesResponse);
	}

	private void parseProfiles(JsonObject json) {
		JsonElement profiles = json.get("profiles");
		Map<String, SkyblockProfile> map = new LinkedHashMap<>();
		profileNames.clear();
		latestProfileName = null;
		if (profiles != null && profiles.isJsonArray()) {
			for (JsonElement pe : profiles.getAsJsonArray()) {
				JsonObject profile = pe.getAsJsonObject();
				JsonObject members = J.obj(profile, "members");
				if (members == null || !members.has(uuidStr)) continue;
				JsonObject member = members.getAsJsonObject(uuidStr);
				if (member.has("coop_invitation") && !J.bool(member, "coop_invitation.confirmed", true)) continue;
				String name = J.str(profile, "cute_name", "?");
				if (J.bool(profile, "selected", false)) latestProfileName = name;
				map.put(name, new SkyblockProfile(profile));
				profileNames.add(name);
			}
			profilesArray = profiles.getAsJsonArray();
		}
		nameToProfile = map;
		if (map.isEmpty()) {
			state = State.NO_SKYBLOCK;
			return;
		}
		if (latestProfileName == null) latestProfileName = profileNames.get(0);
		state = State.LOADED;
	}

	public JsonObject getPlayerStatus() {
		if (playerStatus == null && !statusRequested) {
			statusRequested = true;
			UrsaClient.get(UrsaClient.status(uuid)).thenAccept(json -> {
				if (J.bool(json, "success", false)) playerStatus = J.obj(json, "session");
			}).exceptionally(t -> {
				statusRequested = false;
				return null;
			});
		}
		return playerStatus;
	}

	public JsonObject getGuild() {
		if (guild == null && !guildRequested) {
			guildRequested = true;
			UrsaClient.get(UrsaClient.guild(uuid)).thenAccept(json -> {
				if (!J.bool(json, "success", false)) return;
				if (json.get("guild") == null || json.get("guild").isJsonNull()) inGuild = false;
				else guild = json.getAsJsonObject("guild");
			});
		}
		return guild;
	}

	public boolean isInGuild() {
		return inGuild;
	}

	// ---- Generic lazy requests (museum, garden, bingo, ...) ----

	public enum FetchState { LOADING, OK, ERROR }

	public record Fetch(FetchState state, JsonObject json, String error) {}

	private final Map<String, Fetch> fetches = new HashMap<>();

	/**
	 * Lazily requests an ursa path once (e.g. UrsaClient.museum(profileId)) and caches the answer for this player.
	 * Call it every frame: it returns LOADING until the response arrives, then OK (with the JSON) or ERROR.
	 */
	public Fetch fetchUrsa(String path) {
		Fetch f = fetches.get(path);
		if (f != null) return f;
		f = new Fetch(FetchState.LOADING, null, null);
		fetches.put(path, f);
		UrsaClient.get(path).whenComplete((json, err) -> {
			if (err != null) fetches.put(path, new Fetch(FetchState.ERROR, null, describe(err)));
			else if (!J.bool(json, "success", false)) fetches.put(path, new Fetch(FetchState.ERROR, json, J.str(json, "cause", "Unknown error")));
			else fetches.put(path, new Fetch(FetchState.OK, json, null));
		});
		return f;
	}

	/** Same for any public URL (no proxy; e.g. Hypixel resources, Elitebot). Callback-free, polled every frame. */
	public Fetch fetchUrl(String url) {
		Fetch f = fetches.get(url);
		if (f != null) return f;
		f = new Fetch(FetchState.LOADING, null, null);
		fetches.put(url, f);
		Minecraft mc = Minecraft.getInstance();
		Http.getJson(url).whenComplete((json, err) -> mc.execute(() -> fetches.put(url,
			err != null ? new Fetch(FetchState.ERROR, null, describe(err)) : new Fetch(FetchState.OK, json, null))));
		return f;
	}

	// ---- Soopy networth / leaderboards (same endpoints as NEU) ----

	private void loadSoopy() {
		if (soopyRequested || profilesArray == null) return;
		soopyRequested = true;
		soopyNetworthPos = -2;
		soopyWeightPos = -2;
		Minecraft mc = Minecraft.getInstance();
		Http.getJson("https://api.soopy.dev/lb/lbpos/networth/" + uuidStr)
			.whenComplete((j, t) -> mc.execute(() -> soopyNetworthPos = soopyPos(j, t)));
		Http.getJson("https://api.soopy.dev/lb/lbpos/weight/" + uuidStr)
			.whenComplete((j, t) -> mc.execute(() -> soopyWeightPos = soopyPos(j, t)));
		Http.send("POST", "https://soopy.dev/api/v2/player_networth2/" + uuidStr, null, profilesArray.toString())
			.thenApply(Http.Response::json)
			.whenComplete((j, t) -> mc.execute(() -> {
				for (SkyblockProfile p : nameToProfile.values()) {
					String id = J.str(p.outer, "profile_id", "").replace("-", "");
					JsonObject data = t == null ? J.obj(j, "data." + id) : null;
					p.soopyNetworth = new SoopyNetworth(t == null && J.bool(j, "success", false) ? data : null);
				}
			}));
	}

	private static long soopyPos(JsonObject j, Throwable t) {
		if (t != null || j == null || !J.bool(j, "success", false) || !j.has("data")) return -3;
		return j.get("data").getAsLong();
	}

	public long getSoopyNetworthLeaderboardPosition() {
		loadSoopy();
		return MOULBERRY_UUID.equals(uuidStr) ? 1 : soopyNetworthPos;
	}

	public long getSoopyWeightLeaderboardPosition() {
		loadSoopy();
		return MOULBERRY_UUID.equals(uuidStr) ? 1 : soopyWeightPos;
	}

	public boolean isProfileMaxSoopyWeight(String profileName) {
		String best = "";
		double bestWeight = 0;
		for (Map.Entry<String, SkyblockProfile> e : nameToProfile.entrySet()) {
			if (!e.getValue().skillsApiEnabled()) continue;
			Weights.Result r = Weights.senither(e.getValue().getLevelingInfo());
			if (r == null) continue; // NEU crashed here once weight.json disappeared
			if (r.total() > bestWeight) {
				bestWeight = r.total();
				best = e.getKey();
			}
		}
		return best.equals(profileName);
	}

	public static final class SoopyNetworth {
		public final long networth; // -2 error, -1 loading
		public final Map<String, Long> categories = new LinkedHashMap<>();

		SoopyNetworth(JsonObject data) {
			if (data == null || data.get("total") == null || data.get("total").isJsonNull()) {
				networth = -2;
				return;
			}
			networth = data.get("total").getAsLong();
			Map<String, Long> tmp = new HashMap<>();
			JsonObject cats = J.objOrEmpty(data, "categories");
			for (Map.Entry<String, JsonElement> e : cats.entrySet()) {
				if (!e.getValue().isJsonNull()) tmp.put(e.getKey(), e.getValue().getAsLong());
			}
			tmp.entrySet().stream().sorted(Comparator.comparingLong(e -> -e.getValue())).forEachOrdered(e -> categories.put(e.getKey(), e.getValue()));
		}

		private SoopyNetworth() {
			networth = -1;
		}

		static final SoopyNetworth LOADING = new SoopyNetworth();
	}

	// ---- One profile ----

	public class SkyblockProfile {
		final JsonObject outer;
		private final String gamemode;
		private Map<String, List<PvItem>> inventories;
		private List<Integer> backpackSizes;
		private Map<String, Level> levelingInfo;
		private Integer magicalPower;
		private Long networth;
		private SoopyNetworth soopyNetworth;

		SkyblockProfile(JsonObject outer) {
			this.outer = outer;
			this.gamemode = J.str(outer, "game_mode", null);
		}

		public JsonObject getOuterProfileJson() {
			return outer;
		}

		public JsonObject getProfileJson() {
			return J.objOrEmpty(outer, "members." + uuidStr);
		}

		public String getGamemode() {
			return gamemode;
		}

		public boolean skillsApiEnabled() {
			return J.get(getProfileJson(), "player_data.experience.SKILL_COMBAT") != null;
		}

		public double getSkyblockLevel() {
			return J.i(getProfileJson(), "leveling.experience", 0) / 100.0;
		}

		public ChatFormatting getSkyblockLevelColour() {
			JsonObject colours = J.obj(NeuRepo.sblevels(), "sblevel_colours");
			ChatFormatting colour = ChatFormatting.WHITE;
			if (colours == null) return colour;
			double lvl = getSkyblockLevel();
			for (Map.Entry<String, JsonElement> e : colours.entrySet()) {
				try {
					ChatFormatting f = dev.neupv.util.Fmt.formatting(e.getValue().getAsString());
					if (f != null && lvl >= Integer.parseInt(e.getKey())) colour = f;
				} catch (RuntimeException ignored) {
				}
			}
			return colour;
		}

		public Map<String, List<PvItem>> getInventoryInfo() {
			if (inventories != null) return inventories;
			JsonObject pj = getProfileJson();
			inventories = new HashMap<>();
			backpackSizes = new ArrayList<>();
			for (String inv : INVENTORY_NAMES) {
				List<PvItem> items = new ArrayList<>();
				if (inv.equals("backpack_contents")) {
					JsonObject bps = J.obj(pj, "inventory.backpack_contents");
					if (bps != null) {
						List<String> keys = new ArrayList<>(bps.keySet());
						keys.sort(Comparator.comparingInt(k -> {
							try {
								return Integer.parseInt(k);
							} catch (NumberFormatException e) {
								return 0;
							}
						}));
						for (String k : keys) {
							List<PvItem> bp = LegacyItems.decodeInventory(J.str(bps.get(k), "data", null));
							backpackSizes.add(bp.size());
							items.addAll(bp);
						}
					}
				} else {
					String path = "inventory." + inv + ".data";
					if (inv.endsWith("bag") || inv.equals("quiver")) path = "inventory.bag_contents." + inv + ".data";
					else if (inv.equals("candy_inventory_contents")) path = "shared_inventory.candy_inventory_contents.data";
					items.addAll(LegacyItems.decodeInventory(J.str(pj, path, null)));
				}
				inventories.put(inv, items);
			}
			return inventories;
		}

		public List<Integer> getBackpackSizes() {
			getInventoryInfo();
			return backpackSizes;
		}

		/** Exact port of NEU's getLevelingInfo. */
		public Map<String, Level> getLevelingInfo() {
			if (levelingInfo != null) return levelingInfo;
			JsonObject leveling = NeuRepo.leveling();
			if (leveling == null) return null;
			JsonObject pj = getProfileJson();
			Map<String, Level> out = new HashMap<>();
			for (String skill : SKILLS) {
				float xp = 0;
				if (skill.equals("social")) {
					for (Map.Entry<String, JsonElement> m : J.objOrEmpty(outer, "members").entrySet()) {
						xp += (float) J.dbl(m.getValue(), "player_data.experience.SKILL_SOCIAL", 0);
					}
				} else {
					xp += (float) J.dbl(pj, "player_data.experience.SKILL_" + skill.toUpperCase(Locale.ROOT), 0);
				}
				JsonArray arr = J.arr(leveling, skill.equals("runecrafting") ? "runecrafting_xp" : skill.equals("social") ? "social" : "leveling_xp");
				int max = Level.cap(leveling, skill);
				if (skill.equals("farming")) max += J.i(pj, "jacobs_contest.perks.farming_level_cap", 0);
				if (skill.equals("taming")) max += J.arrOrEmpty(pj, "pets_data.pet_care.pet_types_sacrificed").size();
				out.put(skill, Level.of(arr, xp, max, false));
			}
			out.put("hotm", Level.of(J.arr(leveling, "HOTM"), (float) J.dbl(pj, "mining_core.experience", 0), Level.cap(leveling, "HOTM"), false));
			float cataXp = (float) J.dbl(pj, "dungeons.dungeon_types.catacombs.experience", 0);
			out.put("catacombs", Level.of(J.arr(leveling, "catacombs"), cataXp, Level.cap(leveling, "catacombs"), false));
			out.put("cosmetic_catacombs", Level.of(J.arr(leveling, "catacombs"), cataXp, 100, false));
			for (String c : Weights.DUNGEON_CLASS_NAMES) {
				float cxp = (float) J.dbl(pj, "dungeons.player_classes." + c + ".experience", 0);
				out.put(c, Level.of(J.arr(leveling, "catacombs"), cxp, Level.cap(leveling, "catacombs"), false));
				out.put("cosmetic_" + c, Level.of(J.arr(leveling, "catacombs"), cxp, 99, false));
			}
			for (String s : Weights.SLAYER_NAMES) {
				float sxp = (float) J.dbl(pj, "slayer.slayer_bosses." + s + ".xp", 0);
				out.put(s, Level.of(J.arr(leveling, "slayer_xp." + s), sxp, s.equals("vampire") ? 5 : 9, true));
			}
			return levelingInfo = out;
		}

		/** Pets with tier and exp, and the active one. */
		public List<JsonObject> getPets() {
			List<JsonObject> pets = new ArrayList<>();
			for (JsonElement e : J.arrOrEmpty(getProfileJson(), "pets_data.pets")) {
				if (e.isJsonObject() && e.getAsJsonObject().has("tier") && e.getAsJsonObject().has("exp")) pets.add(e.getAsJsonObject());
			}
			return pets;
		}

		public JsonObject getActivePet() {
			for (JsonObject p : getPets()) if (J.bool(p, "active", false)) return p;
			return null;
		}

		/**
		 * Magical power. Same rules as NEU, with the family de-duplication fixed: when two tiers of the same
		 * family are in the bag, the LOWER one is dropped (NEU dropped the higher one and under-counted).
		 */
		public int getMagicalPower() {
			if (magicalPower != null) return magicalPower;
			List<PvItem> bag = getInventoryInfo().get("talisman_bag");
			if (bag == null || bag.isEmpty()) return magicalPower = -1;
			Map<String, Integer> acc = new LinkedHashMap<>();
			bag.stream().filter(i -> i != null && i.skyblockId != null)
				.sorted(Comparator.comparingInt(i -> -i.rarity()))
				.forEach(i -> acc.putIfAbsent(i.skyblockId, i.rarity()));
			JsonObject upgrades = J.obj(NeuRepo.misc(), "talisman_upgrades");
			JsonObject parents = NeuRepo.parents();
			JsonObject pj = getProfileJson();
			Set<String> superseded = new HashSet<>();
			if (upgrades != null) {
				for (String id : acc.keySet()) {
					JsonArray ups = J.arr(upgrades, id);
					if (ups == null) continue;
					for (JsonElement u : ups) {
						if (acc.containsKey(u.getAsString())) {
							superseded.add(id); // a higher tier of this family is present
							break;
						}
					}
				}
			}
			int power = 0;
			if (J.get(pj, "rift.access.consumed_prism") != null) power += 11;
			Set<String> ignored = new HashSet<>();
			boolean countedCrabHat = false;
			for (Map.Entry<String, Integer> e : acc.entrySet()) {
				String id = e.getKey();
				if (ignored.contains(id) || superseded.contains(id)) continue;
				for (JsonElement child : J.arrOrEmpty(parents, id)) ignored.add(child.getAsString());
				int rarity = e.getValue();
				if (id.equals("HEGEMONY_ARTIFACT")) power += mpForRarity(rarity);
				if (id.equals("ABICASE")) power += J.arrOrEmpty(pj, "nether_island_player_data.abiphone.active_contacts").size() / 2;
				if (id.startsWith("PARTY_HAT") || id.startsWith("BALLOON_HAT")) {
					if (countedCrabHat) continue;
					countedCrabHat = true;
				}
				power += mpForRarity(rarity);
			}
			return magicalPower = power;
		}

		private static int mpForRarity(int r) {
			return switch (r) {
				case 0, 6 -> 3;
				case 1, 7 -> 5;
				case 2 -> 8;
				case 3 -> 12;
				case 4 -> 16;
				case 5 -> 22;
				default -> 0;
			};
		}

		public SoopyNetworth getSoopyNetworth() {
			if (soopyNetworth != null) return soopyNetworth;
			loadSoopy();
			return SoopyNetworth.LOADING;
		}

		/**
		 * Local networth, same method as NEU (item prices x1.3 + pets + bank + purse), computed in long arithmetic:
		 * NEU cast to int and capped at ~2.1 billion.
		 */
		public long getNetworth() {
			if (networth != null) return networth;
			Prices.refreshIfNeeded();
			long total = 0;
			for (List<PvItem> inv : getInventoryInfo().values()) {
				for (PvItem item : inv) {
					if (item == null || item.skyblockId == null) continue;
					total += (long) (Prices.price(item.skyblockId) * item.count);
					byte[] contents = item.legacyTag.getCompoundOrEmpty("ExtraAttributes").getByteArray(item.skyblockId.equals("NEW_YEAR_CAKE_BAG") ? "new_year_cake_bag_data" : backpackKey(item)).orElse(null);
					if (contents != null) {
						for (PvItem inner : LegacyItems.decodeInventory(contents)) {
							if (inner != null && inner.skyblockId != null) total += (long) (Prices.price(inner.skyblockId) * inner.count);
						}
					}
				}
			}
			if (total == 0) return -1;
			total = (long) (total * 1.3);
			for (JsonObject pet : getPets()) {
				String tier = J.str(pet, "tier", "");
				for (int i = 0; i < 6; i++) {
					if (LegacyItems.RARITIES[i].equals(tier)) total += (long) Prices.price(J.str(pet, "type", "") + ";" + i);
				}
			}
			total += (long) J.dbl(getProfileJson(), "banking.balance", 0);
			total += (long) J.dbl(outer, "banking.balance", 0);
			total += (long) J.dbl(getProfileJson(), "currencies.coin_purse", 0);
			return networth = total;
		}

		private String backpackKey(PvItem item) {
			// e.g. "small_backpack_data", "jumbo_backpack_data"
			return item.skyblockId.toLowerCase(Locale.ROOT) + "_data";
		}
	}
}
