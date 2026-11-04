/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates ProfileViewer.java (collection maps) and util/hypixelapi/Collection.kt
 * (ProfileCollectionInfo), Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.item.LegacyItems;
import dev.neupv.util.J;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Static collection/minion layout of NEU's profile viewer, and the per-profile collection data (ProfileCollectionInfo). */
public final class CollectionsData {
	public static final String COLLECTIONS_URL = "https://api.hypixel.net/v2/resources/skyblock/collections";
	private static final Pattern GENERATOR = Pattern.compile("^([^0-9]+)_([0-9]+)$");

	private static LinkedHashMap<ItemStack, List<String>> catToCollection;
	private static LinkedHashMap<ItemStack, List<String>> catToMinion;
	private static Map<String, ItemStack> collectionDisplay;

	private CollectionsData() {}

	public static LinkedHashMap<ItemStack, List<String>> catToCollection() {
		init();
		return catToCollection;
	}

	public static LinkedHashMap<ItemStack, List<String>> catToMinion() {
		init();
		return catToMinion;
	}

	public static Map<String, ItemStack> collectionDisplay() {
		init();
		return collectionDisplay;
	}

	private static List<String> list(String... s) {
		return Arrays.asList(s);
	}

	private static ItemStack icon(Item item, String name) {
		return LegacyItems.icon(item, name);
	}

	private static void init() {
		if (catToCollection != null) return;
		ItemStack farming = icon(Items.GOLDEN_HOE, "§eFarming");
		ItemStack mining = icon(Items.STONE_PICKAXE, "§7Mining");
		ItemStack combat = icon(Items.STONE_SWORD, "§cCombat");
		ItemStack foraging = icon(Items.OAK_SAPLING, "§2Foraging");
		ItemStack fishing = icon(Items.FISHING_ROD, "§bFishing");
		ItemStack rift = icon(Items.MYCELIUM, "§5Rift");

		LinkedHashMap<ItemStack, List<String>> cols = new LinkedHashMap<>();
		cols.put(farming, list("WHEAT", "CARROT_ITEM", "POTATO_ITEM", "PUMPKIN", "MELON", "SEEDS", "MUSHROOM_COLLECTION",
			"INK_SACK:3", "CACTUS", "SUGAR_CANE", "FEATHER", "LEATHER", "PORK", "RAW_CHICKEN", "MUTTON", "RABBIT", "NETHER_STALK"));
		cols.put(mining, list("COBBLESTONE", "COAL", "IRON_INGOT", "GOLD_INGOT", "DIAMOND", "INK_SACK:4", "EMERALD", "REDSTONE",
			"QUARTZ", "OBSIDIAN", "GLOWSTONE_DUST", "GRAVEL", "ICE", "NETHERRACK", "SAND", "ENDER_STONE", null, "MITHRIL_ORE",
			"HARD_STONE", "GEMSTONE_COLLECTION", "MYCEL", "SAND:1", "SULPHUR_ORE", "GLACITE", "TUNGSTEN", "UMBER"));
		cols.put(combat, list("ROTTEN_FLESH", "BONE", "STRING", "SPIDER_EYE", "SULPHUR", "ENDER_PEARL", "GHAST_TEAR", "SLIME_BALL",
			"BLAZE_ROD", "MAGMA_CREAM", null, null, null, null, "CHILI_PEPPER"));
		cols.put(foraging, list("LOG", "LOG:1", "LOG:2", "LOG_2:1", "LOG_2", "LOG:3", null));
		cols.put(fishing, list("RAW_FISH", "RAW_FISH:1", "RAW_FISH:2", "RAW_FISH:3", "PRISMARINE_SHARD", "PRISMARINE_CRYSTALS",
			"CLAY_BALL", "WATER_LILY", "INK_SACK", "SPONGE", "MAGMA_FISH"));
		cols.put(rift, list("AGARICUS_CAP", "CADUCOUS_STEM", "HALF_EATEN_CARROT", "HEMOVIBE", "METAL_HEART", "WILTED_BERBERIS", "TIMITE"));

		LinkedHashMap<ItemStack, List<String>> mins = new LinkedHashMap<>();
		mins.put(farming, list("WHEAT", "CARROT", "POTATO", "PUMPKIN", "MELON", null, "MUSHROOM", "COCOA", "CACTUS", "SUGAR_CANE",
			"CHICKEN", "COW", "PIG", null, "SHEEP", "RABBIT", "NETHER_WARTS"));
		mins.put(mining, list("COBBLESTONE", "COAL", "IRON", "GOLD", "DIAMOND", "LAPIS", "EMERALD", "REDSTONE", "QUARTZ", "OBSIDIAN",
			"GLOWSTONE", "GRAVEL", "ICE", null, "SAND", "ENDER_STONE", "SNOW", "MITHRIL", "HARD_STONE", null, "MYCELIUM", "RED_SAND",
			null, null, null, null));
		mins.put(combat, list("ZOMBIE", "SKELETON", "SPIDER", "CAVESPIDER", "CREEPER", "ENDERMAN", "GHAST", "SLIME", "BLAZE",
			"MAGMA_CUBE", "REVENANT", "TARANTULA", "VOIDLING", "INFERNO"));
		mins.put(foraging, list("OAK", "SPRUCE", "BIRCH", "DARK_OAK", "ACACIA", "JUNGLE", "FLOWER"));
		mins.put(fishing, list("FISHING", null, null, null, null, null, "CLAY", null, null, null));
		mins.put(rift, list(null, null, null, "VAMPIRE"));

		Map<String, ItemStack> d = new HashMap<>();
		/* FARMING COLLECTIONS */
		d.put("WHEAT", icon(Items.WHEAT, "§eWheat"));
		d.put("CARROT_ITEM", icon(Items.CARROT, "§eCarrot"));
		d.put("POTATO_ITEM", icon(Items.POTATO, "§ePotato"));
		d.put("PUMPKIN", icon(Items.PUMPKIN, "§ePumpkin"));
		d.put("MELON", icon(Items.MELON_SLICE, "§eMelon"));
		d.put("SEEDS", icon(Items.WHEAT_SEEDS, "§eSeeds"));
		d.put("MUSHROOM_COLLECTION", icon(Items.RED_MUSHROOM, "§eMushroom"));
		d.put("INK_SACK:3", icon(Items.COCOA_BEANS, "§eCocoa Beans"));
		d.put("CACTUS", icon(Items.CACTUS, "§eCactus"));
		d.put("SUGAR_CANE", icon(Items.SUGAR_CANE, "§eSugar Cane"));
		d.put("FEATHER", icon(Items.FEATHER, "§eFeather"));
		d.put("LEATHER", icon(Items.LEATHER, "§eLeather"));
		d.put("PORK", icon(Items.PORKCHOP, "§eRaw Porkchop"));
		d.put("RAW_CHICKEN", icon(Items.CHICKEN, "§eRaw Chicken"));
		d.put("MUTTON", icon(Items.MUTTON, "§eMutton"));
		d.put("RABBIT", icon(Items.RABBIT, "§eRaw Rabbit"));
		d.put("NETHER_STALK", icon(Items.NETHER_WART, "§eNether Wart"));
		/* MINING COLLECTIONS */
		d.put("COBBLESTONE", icon(Items.COBBLESTONE, "§7Cobblestone"));
		d.put("COAL", icon(Items.COAL, "§7Coal"));
		d.put("IRON_INGOT", icon(Items.IRON_INGOT, "§7Iron Ingot"));
		d.put("GOLD_INGOT", icon(Items.GOLD_INGOT, "§7Gold Ingot"));
		d.put("DIAMOND", icon(Items.DIAMOND, "§7Diamond"));
		d.put("INK_SACK:4", icon(Items.LAPIS_LAZULI, "§7Lapis Lazuli"));
		d.put("EMERALD", icon(Items.EMERALD, "§7Emerald"));
		d.put("REDSTONE", icon(Items.REDSTONE, "§7Redstone"));
		d.put("QUARTZ", icon(Items.QUARTZ, "§7Nether Quartz"));
		d.put("OBSIDIAN", icon(Items.OBSIDIAN, "§7Obsidian"));
		d.put("GLOWSTONE_DUST", icon(Items.GLOWSTONE_DUST, "§7Glowstone Dust"));
		d.put("GRAVEL", icon(Items.GRAVEL, "§7Gravel"));
		d.put("ICE", icon(Items.ICE, "§7Ice"));
		d.put("NETHERRACK", icon(Items.NETHERRACK, "§7Netherrack"));
		d.put("SAND", icon(Items.SAND, "§7Sand"));
		d.put("ENDER_STONE", icon(Items.END_STONE, "§7End Stone"));
		d.put("MITHRIL_ORE", icon(Items.PRISMARINE_CRYSTALS, "§7Mithril"));
		d.put("HARD_STONE", icon(Items.STONE, "§7Hard Stone"));
		d.put("GEMSTONE_COLLECTION", LegacyItems.skull("§7Gemstone",
			"ewogICJ0aW1lc3RhbXAiIDogMTYxODA4Mzg4ODc3MSwKICAicHJvZmlsZUlkIiA6ICJjNTBhZmE4YWJlYjk0ZTQ1OTRiZjFiNDI1YTk4MGYwMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJUd29FQmFlIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2FhYzE1ZjZmY2YyY2U5NjNlZjRjYTcxZjFhODY4NWFkYjk3ZWI3NjllMWQxMTE5NGNiYmQyZTk2NGE4ODk3OGMiCiAgICB9CiAgfQp9"));
		d.put("MYCEL", icon(Items.MYCELIUM, "§7Mycelium"));
		d.put("SAND:1", icon(Items.RED_SAND, "§7Red Sand"));
		d.put("SULPHUR_ORE", icon(Items.GLOWSTONE_DUST, "§7Sulphur"));
		d.put("GLACITE", icon(Items.PACKED_ICE, "§7Glacite"));
		d.put("TUNGSTEN", LegacyItems.skull("§7Tungsten",
			"ewogICJ0aW1lc3RhbXAiIDogMTcwODkxNDY4MjE4MCwKICAicHJvZmlsZUlkIiA6ICJiZDNhNWRmY2ZkZjg0NDczOTViZDJiZmUwNGY0YzAzMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJwcmVja3Jhc25vIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2Q4MTFmM2U3MjNiYmQ0NjM5M2Y4YWFkODU1NmIxZGY4ZWQzM2Y1NTliZTgyN2Y0N2ZlNzM2ZjcwNGMzNTU4NmUiCiAgICB9CiAgfQp9"));
		d.put("UMBER", LegacyItems.skull("§7Umber",
			"ewogICJ0aW1lc3RhbXAiIDogMTcwODkxOTkzMjc2MSwKICAicHJvZmlsZUlkIiA6ICJiMmM5ZTUyZjNhODU0YzQ4YmJjMmMwM2JjM2U0NzdiMiIsCiAgInByb2ZpbGVOYW1lIiA6ICJJU05FRVpFREFUWU9VIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2I1NjViNWFhODNkNGFhN2Y3YWYyMmRjMTI3MWIyZjBiMjc0NDFmOWFjMTQ5NWY2YjQ2NTNjZjY4ZGZiMTA1ZWYiCiAgICB9CiAgfQp9"));
		/* COMBAT COLLECTIONS */
		d.put("ROTTEN_FLESH", icon(Items.ROTTEN_FLESH, "§cRotten Flesh"));
		d.put("BONE", icon(Items.BONE, "§cBone"));
		d.put("STRING", icon(Items.STRING, "§cString"));
		d.put("SPIDER_EYE", icon(Items.SPIDER_EYE, "§cSpider Eye"));
		d.put("SULPHUR", icon(Items.GUNPOWDER, "§cGunpowder"));
		d.put("ENDER_PEARL", icon(Items.ENDER_PEARL, "§cEnder Pearl"));
		d.put("GHAST_TEAR", icon(Items.GHAST_TEAR, "§cGhast Tear"));
		d.put("SLIME_BALL", icon(Items.SLIME_BALL, "§cSlimeball"));
		d.put("BLAZE_ROD", icon(Items.BLAZE_ROD, "§cBlaze Rod"));
		d.put("MAGMA_CREAM", icon(Items.MAGMA_CREAM, "§cMagma Cream"));
		d.put("CHILI_PEPPER", LegacyItems.skull("§cChili Pepper",
			"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjg1OWM4ZGYxMTA5YzA4YTc1NjI3NWYxZDI4ODdjMjc0ODA0OWZlMzM4Nzc3NjlhN2I0MTVkNTZlZGE0NjlkOCJ9fX0"));
		/* FORAGING COLLECTIONS */
		d.put("LOG", icon(Items.OAK_LOG, "§2Oak Wood"));
		d.put("LOG:1", icon(Items.SPRUCE_LOG, "§2Spruce Wood"));
		d.put("LOG:2", icon(Items.BIRCH_LOG, "§2Birch Wood"));
		d.put("LOG_2:1", icon(Items.DARK_OAK_LOG, "§2Dark Oak Wood"));
		d.put("LOG_2", icon(Items.ACACIA_LOG, "§2Acacia Wood"));
		d.put("LOG:3", icon(Items.JUNGLE_LOG, "§2Jungle Wood"));
		/* FISHING COLLECTIONS */
		d.put("RAW_FISH", icon(Items.COD, "§bRaw Fish"));
		d.put("RAW_FISH:1", icon(Items.SALMON, "§bRaw Salmon"));
		d.put("RAW_FISH:2", icon(Items.TROPICAL_FISH, "§bClownfish"));
		d.put("RAW_FISH:3", icon(Items.PUFFERFISH, "§bPufferfish"));
		d.put("PRISMARINE_SHARD", icon(Items.PRISMARINE_SHARD, "§bPrismarine Shard"));
		d.put("PRISMARINE_CRYSTALS", icon(Items.PRISMARINE_CRYSTALS, "§bPrismarine Crystals"));
		d.put("CLAY_BALL", icon(Items.CLAY_BALL, "§bClay"));
		d.put("WATER_LILY", icon(Items.LILY_PAD, "§bLily Pad"));
		d.put("INK_SACK", icon(Items.INK_SAC, "§bInk Sac"));
		d.put("SPONGE", icon(Items.SPONGE, "§bSponge"));
		d.put("MAGMA_FISH", LegacyItems.skull("§bMagmafish",
			"ewogICJ0aW1lc3RhbXAiIDogMTY0MjQ4ODA3MDY2NiwKICAicHJvZmlsZUlkIiA6ICIzNDkxZjJiOTdjMDE0MWE2OTM2YjFjMjJhMmEwMGZiNyIsCiAgInByb2ZpbGVOYW1lIiA6ICJKZXNzc3N1aGgiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjU2YjU5NTViMjk1NTIyYzk2ODk0ODE5NjBjMDFhOTkyY2ExYzc3NTRjZjRlZTMxM2M4ZGQwYzM1NmQzMzVmIgogICAgfQogIH0KfQ"));
		/* RIFT COLLECTIONS */
		d.put("AGARICUS_CAP", LegacyItems.skull("§5Agaricus Cap",
			"eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGNlMGEyMzBhY2Q2NDM2YWJjODZmMTNiZTcyZTliYTk0NTM3ZWU1NGYwMzI1YmI4NjI1NzdhMWUwNjJmMzcifX19"));
		d.put("CADUCOUS_STEM", icon(Items.ROSE_BUSH, "§5Caducous Stem"));
		d.put("HALF_EATEN_CARROT", icon(Items.CARROT, "§5Half-Eaten Carrot"));
		d.put("HEMOVIBE", icon(Items.REDSTONE_ORE, "§5Hemovibe"));
		d.put("METAL_HEART", LegacyItems.skull("§5Living Metal Heart",
			"ewogICJ0aW1lc3RhbXAiIDogMTY3NjQ3NjQ1NjcyNywKICAicHJvZmlsZUlkIiA6ICI5MGQ1NDY0OGEzNWE0YmExYTI2Yjg1YTg4NTU4OGJlOSIsCiAgInByb2ZpbGVOYW1lIiA6ICJFdW4wbWlhIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlL2YwMjc4ZWU1M2E1M2I3NzMzYzdiODQ1MmZjZjc5NGRmYmZiYzNiMDMyZTc1MGE2OTkzNTczYjViZDAyOTkxMzUiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ"));
		d.put("WILTED_BERBERIS", icon(Items.DEAD_BUSH, "§5Wilted Berberis"));
		d.put("TIMITE", LegacyItems.skull("§5Timite",
			"ewogICJ0aW1lc3RhbXAiIDogMTcyNTMyNTgwNDE0MywKICAicHJvZmlsZUlkIiA6ICIxMTM1Njg1ZTk3ZGE0ZjYyYTliNDQ3MzA0NGFiZjQ0MSIsCiAgInByb2ZpbGVOYW1lIiA6ICJNYXJpb1dsZXMiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDIxNzdhYzk5N2ZmOTcwOTAwY2QxNzkyN2ZiMzFjNGUyMGZhYzI2MzY0OWIxYjU1ODU1NWJjYzZmMGQ1YjVkZCIKICAgIH0KICB9Cn0="));

		catToMinion = mins;
		collectionDisplay = d;
		catToCollection = cols;
	}

	// ---- ProfileCollectionInfo (Collection.kt) ----

	public record Tier(int tier, long amountRequired) {}

	public record CollectionInfo(String name, List<Tier> tiers, BigInteger total, BigInteger personal, int unlockedTiers) {
		public long maxAmount() {
			return tiers.isEmpty() ? 0 : tiers.get(tiers.size() - 1).amountRequired();
		}
	}

	public record ProfileInfo(Map<String, CollectionInfo> collections, Map<String, Integer> craftedGenerators) {}

	/**
	 * NEU ProfileCollectionInfo.getCollectionData: collection counts summed over every member, unlocked tiers from the
	 * Hypixel collections resource, highest crafted tier of each minion over every member. Null when the selected
	 * member has no "collection" object (collection API disabled).
	 */
	public static ProfileInfo compute(JsonObject outerProfile, String mainUuid, JsonObject resource) {
		JsonObject members = J.obj(outerProfile, "members");
		if (members == null) return null;
		JsonObject main = J.obj(members, mainUuid.replace("-", ""));
		JsonObject mainCollection = main == null ? null : J.obj(main, "collection");
		if (mainCollection == null) return null;
		List<JsonObject> memberCollections = new ArrayList<>();
		Map<String, Integer> generators = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : members.entrySet()) {
			if (!e.getValue().isJsonObject()) continue;
			JsonObject c = J.obj(e.getValue(), "collection");
			if (c != null) memberCollections.add(c);
			for (JsonElement gen : J.arrOrEmpty(e.getValue(), "player_data.crafted_generators")) {
				if (!gen.isJsonPrimitive()) continue;
				Matcher m = GENERATOR.matcher(gen.getAsString());
				if (!m.matches()) continue;
				generators.merge(m.group(1), Integer.parseInt(m.group(2)), Math::max);
			}
		}
		Map<String, CollectionInfo> out = new LinkedHashMap<>();
		JsonObject categories = J.objOrEmpty(resource, "collections");
		for (Map.Entry<String, JsonElement> cat : categories.entrySet()) {
			for (Map.Entry<String, JsonElement> item : J.objOrEmpty(cat.getValue(), "items").entrySet()) {
				String name = item.getKey();
				List<Tier> tiers = new ArrayList<>();
				for (JsonElement t : J.arrOrEmpty(item.getValue(), "tiers")) {
					tiers.add(new Tier(J.i(t, "tier", -1), J.lng(t, "amountRequired", -1)));
				}
				BigInteger total = BigInteger.ZERO;
				for (JsonObject c : memberCollections) total = total.add(big(c, name));
				BigInteger personal = big(mainCollection, name);
				int unlocked = 0;
				for (Tier t : tiers) if (BigInteger.valueOf(t.amountRequired()).compareTo(total) <= 0) unlocked++;
				out.put(name, new CollectionInfo(J.str(item.getValue(), "name", name), tiers, total, personal, unlocked));
			}
		}
		return new ProfileInfo(out, generators);
	}

	private static BigInteger big(JsonObject o, String key) {
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonPrimitive()) return BigInteger.ZERO;
		try {
			return e.getAsBigInteger();
		} catch (NumberFormatException ex) {
			return BigInteger.valueOf((long) e.getAsDouble());
		}
	}
}
