package dev.neupv.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.neupv.config.PvConfig;
import dev.neupv.gui.CollectionsData;
import dev.neupv.gui.CollectionsPage;
import dev.neupv.gui.PetsData;
import dev.neupv.gui.ProfileViewerScreen;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import static dev.neupv.test.TestData.check;

/** Collections + Pets tabs: data checks and screenshots on a synthetic profile. */
public class CollectionsPetsGameTest implements FabricClientGameTest {
	private static final String[][] COLLECTIONS = {
		{"WHEAT", "1250000"}, {"CARROT_ITEM", "8400000"}, {"POTATO_ITEM", "350000"}, {"PUMPKIN", "640000"}, {"MELON", "2100000"},
		{"SEEDS", "45000"}, {"MUSHROOM_COLLECTION", "92000"}, {"INK_SACK:3", "1100000"}, {"CACTUS", "260000"}, {"SUGAR_CANE", "3500000"},
		{"FEATHER", "18000"}, {"LEATHER", "95000"}, {"PORK", "12000"}, {"RAW_CHICKEN", "40000"}, {"MUTTON", "8000"}, {"RABBIT", "3000"},
		{"NETHER_STALK", "4200000"}, {"COBBLESTONE", "2700000"}, {"COAL", "180000"}, {"IRON_INGOT", "450000"}, {"GOLD_INGOT", "260000"},
		{"DIAMOND", "75000"}, {"INK_SACK:4", "300000"}, {"EMERALD", "120000"}, {"REDSTONE", "1500000"}, {"QUARTZ", "50000"},
		{"OBSIDIAN", "100000"}, {"GLOWSTONE_DUST", "30000"}, {"GRAVEL", "15000"}, {"ICE", "500000"}, {"NETHERRACK", "4000"},
		{"SAND", "9000"}, {"ENDER_STONE", "220000"}, {"MITHRIL_ORE", "2600000"}, {"HARD_STONE", "9800000"}, {"GEMSTONE_COLLECTION", "16000000"},
		{"MYCEL", "60000"}, {"SAND:1", "40000"}, {"SULPHUR_ORE", "3000"}, {"GLACITE", "1200000"}, {"TUNGSTEN", "800000"}, {"UMBER", "650000"},
		{"ROTTEN_FLESH", "210000"}, {"BONE", "400000"}, {"STRING", "90000"}, {"SPIDER_EYE", "60000"}, {"SULPHUR", "50000"},
		{"ENDER_PEARL", "300000"}, {"GHAST_TEAR", "6000"}, {"SLIME_BALL", "20000"}, {"BLAZE_ROD", "120000"}, {"MAGMA_CREAM", "300000"},
		{"CHILI_PEPPER", "800"}, {"LOG", "160000"}, {"LOG:1", "30000"}, {"LOG:2", "45000"}, {"LOG_2:1", "120000"}, {"LOG_2", "25000"},
		{"LOG:3", "12000"}, {"RAW_FISH", "40000"}, {"RAW_FISH:1", "9000"}, {"RAW_FISH:2", "2000"}, {"RAW_FISH:3", "5000"},
		{"PRISMARINE_SHARD", "1500"}, {"PRISMARINE_CRYSTALS", "1200"}, {"CLAY_BALL", "3000"}, {"WATER_LILY", "18000"},
		{"INK_SACK", "12000"}, {"SPONGE", "8000"}, {"MAGMA_FISH", "700000"}, {"AGARICUS_CAP", "2500"}, {"CADUCOUS_STEM", "40000"},
		{"HALF_EATEN_CARROT", "10000"}, {"HEMOVIBE", "100000"}, {"METAL_HEART", "200"}, {"WILTED_BERBERIS", "3000"}, {"TIMITE", "30000"}};

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitFor(mc -> NeuRepo.isLoaded(), 20 * 300);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(60);
			SkyblockProfiles[] holder = new SkyblockProfiles[1];
			context.runOnClient(mc -> {
				PvConfig.get().useSoopyNetworth = false;
				mc.options.guiScale().set(1);
				mc.resizeGui();
				SkyblockProfiles profiles = new SkyblockProfiles(TestData.NOTCH);
				profiles.loadFromJson(TestData.profilesResponse(CollectionsPetsGameTest::member, CollectionsPetsGameTest::coop), TestData.hypixelPlayer());
				holder[0] = profiles;
				checks(profiles);
			});
			shot(context, holder[0], ProfileViewerScreen.Page.COLLECTIONS, "collections-farming", () -> CollectionsPage.selectCategory(0));
			shot(context, holder[0], ProfileViewerScreen.Page.COLLECTIONS, "collections-mining", () -> CollectionsPage.selectCategory(1));
			shot(context, holder[0], ProfileViewerScreen.Page.PETS, "pets", () -> {});
			// hover the second pet of the grid to capture its tooltip
			double[] pos = new double[2];
			context.runOnClient(mc -> {
				int guiLeft = (mc.getWindow().getGuiScaledWidth() - ProfileViewerScreen.SIZE_X) / 2;
				int guiTop = (mc.getWindow().getGuiScaledHeight() - ProfileViewerScreen.SIZE_Y) / 2;
				float pad = (190 - 5 * 20) / 6f, ypad = (202 - 4 * 20) / 5f;
				pos[0] = guiLeft + 5 + pad + (pad + 20) + 10;
				pos[1] = guiTop + 7 + ypad + 10;
			});
			context.getInput().setCursorPos(pos[0], pos[1]);
			context.waitTicks(10);
			context.takeScreenshot("colpets-pets-tooltip");
		}
	}

	private static void shot(ClientGameTestContext context, SkyblockProfiles profiles, ProfileViewerScreen.Page page, String name, Runnable setup) {
		context.runOnClient(mc -> {
			setup.run();
			ProfileViewerScreen.currentPage = page;
			mc.setScreenAndShow(new ProfileViewerScreen(profiles));
		});
		context.waitTicks(100);
		context.takeScreenshot("colpets-" + name);
	}

	private static void checks(SkyblockProfiles profiles) {
		// Ender Dragon legendary: 25,353,230 xp = level 100
		PetsData.PetLevel max = PetsData.level("ENDER_DRAGON", "LEGENDARY", 25_353_230);
		check(max.currentLevel() == 100 && max.maxed(), "legendary pet at 25,353,230 xp is level 100 (got " + max.currentLevel() + ")");
		PetsData.PetLevel l1 = PetsData.level("ENDER_DRAGON", "LEGENDARY", 0);
		check(l1.currentLevel() == 1 && l1.expRequiredForNextLevel() == 660, "legendary level 1 needs 660 xp (got " + l1.expRequiredForNextLevel() + ")");
		PetsData.PetLevel common = PetsData.level("BEE", "COMMON", 100);
		check(common.currentLevel() == 2, "common pet at 100 xp is level 2 (got " + common.currentLevel() + ")");
		PetsData.PetLevel golden = PetsData.level("GOLDEN_DRAGON", "LEGENDARY", 30_000_000);
		check(golden.maxLevel() == 200 && golden.currentLevel() > 100, "golden dragon goes past 100 (level " + golden.currentLevel() + "/" + golden.maxLevel() + ")");

		JsonObject boosted = new JsonObject();
		boosted.addProperty("type", "BLUE_WHALE");
		boosted.addProperty("tier", "EPIC");
		boosted.addProperty("heldItem", "PET_ITEM_TIER_BOOST");
		check(PetsData.effectiveRarity(boosted) == 4, "tier boost raises EPIC blue whale to LEGENDARY");

		PetsData.PetDisplay dragon = PetsData.create(pet("ENDER_DRAGON", "LEGENDARY", 25_353_230, true, "PET_ITEM_TEXTBOOK", 3, null));
		check(dragon.name().equals("§7[Lvl 100] §6Ender Dragon"), "pet name with level: " + dragon.name());
		check(dragon.lore().stream().anyMatch(l -> l.contains("Strength: §c+50")), "pet stats interpolated at level 100: " + dragon.lore());
		check(dragon.lore().stream().anyMatch(l -> l.startsWith("§6Held Item: ")), "held item in lore");
		check(dragon.lore().stream().anyMatch(l -> l.contains("(3/10) Pet Candy Used")), "candy in lore");
		check(dragon.lore().stream().anyMatch(l -> l.contains("MAX LEVEL")), "max level line");
		check(dragon.lore().get(dragon.lore().size() - 1).contains("LEGENDARY"), "rarity line kept last");
		check(dragon.lore().stream().noneMatch(l -> l.contains("Right-click")), "repo 'Right-click' line removed");

		JsonObject resource = JsonParser.parseString("{\"success\":true,\"collections\":{\"FARMING\":{\"name\":\"Farming\",\"items\":{"
			+ "\"WHEAT\":{\"name\":\"Wheat\",\"maxTiers\":3,\"tiers\":[{\"tier\":1,\"amountRequired\":50,\"unlocks\":[]},"
			+ "{\"tier\":2,\"amountRequired\":1000000,\"unlocks\":[]},{\"tier\":3,\"amountRequired\":2000000,\"unlocks\":[]}]}}}}}").getAsJsonObject();
		SkyblockProfiles.SkyblockProfile p = profiles.getProfile("Apple");
		CollectionsData.ProfileInfo info = CollectionsData.compute(p.getOuterProfileJson(), profiles.getUuidString(), resource);
		check(info != null, "collection info computed");
		CollectionsData.CollectionInfo wheat = info.collections().get("WHEAT");
		check(wheat.total().longValue() == 1_250_000 + 250_000 && wheat.personal().longValue() == 1_250_000,
			"wheat total sums co-op members (" + wheat.total() + ") and personal is own (" + wheat.personal() + ")");
		check(wheat.unlockedTiers() == 2, "wheat tier II unlocked");
		check(info.craftedGenerators().get("WHEAT") == 11 && info.craftedGenerators().get("COBBLESTONE") == 12,
			"crafted minions: highest tier over members " + info.craftedGenerators());
	}

	private static JsonObject pet(String type, String tier, double exp, boolean active, String held, int candy, String skin) {
		JsonObject p = new JsonObject();
		p.addProperty("uuid", java.util.UUID.nameUUIDFromBytes(type.getBytes()).toString());
		p.addProperty("type", type);
		p.addProperty("exp", exp);
		p.addProperty("active", active);
		p.addProperty("tier", tier);
		if (held != null) p.addProperty("heldItem", held);
		else p.add("heldItem", com.google.gson.JsonNull.INSTANCE);
		p.addProperty("candyUsed", candy);
		if (skin != null) p.addProperty("skin", skin);
		else p.add("skin", com.google.gson.JsonNull.INSTANCE);
		return p;
	}

	private static void member(JsonObject member) {
		JsonObject col = new JsonObject();
		for (String[] c : COLLECTIONS) col.addProperty(c[0], Long.parseLong(c[1]));
		member.add("collection", col);
		JsonArray gens = new JsonArray();
		for (String g : new String[]{"WHEAT_1", "WHEAT_2", "WHEAT_9", "CARROT_12", "POTATO_5", "PUMPKIN_11", "MELON_3", "COCOA_7", "CACTUS_6",
			"SUGAR_CANE_10", "COBBLESTONE_12", "COAL_8", "IRON_11", "GOLD_4", "DIAMOND_2", "SNOW_11", "MITHRIL_6", "HARD_STONE_3",
			"ZOMBIE_7", "SKELETON_5", "REVENANT_12", "OAK_1", "FISHING_9", "CLAY_4", "VAMPIRE_2"}) gens.add(g);
		member.getAsJsonObject("player_data").add("crafted_generators", gens);

		JsonArray pets = new JsonArray();
		pets.add(pet("ENDER_DRAGON", "LEGENDARY", 25_353_230, true, "PET_ITEM_TEXTBOOK", 3, null));
		pets.add(pet("GOLDEN_DRAGON", "LEGENDARY", 48_000_000, false, null, 0, null));
		pets.add(pet("BLUE_WHALE", "EPIC", 4_500_000, false, "PET_ITEM_TIER_BOOST", 0, null));
		pets.add(pet("BEE", "RARE", 120_000, false, null, 10, null));
		pets.add(pet("ELEPHANT", "LEGENDARY", 12_000_000, false, "PET_ITEM_IRON_CLAWS_COMMON", 0, null));
		pets.add(pet("SHEEP", "LEGENDARY", 9_000_000, false, null, 0, "SHEEP_PURPLE"));
		pets.add(pet("ROCK", "EPIC", 300_000, false, null, 0, null));
		pets.add(pet("SILVERFISH", "COMMON", 5_000, false, null, 0, null));
		pets.add(pet("RABBIT", "MYTHIC", 30_000_000, false, null, 0, null));
		pets.add(pet("GRIFFIN", "UNCOMMON", 800_000, false, "PET_ITEM_LUCKY_CLOVER", 0, null));
		pets.add(pet("BAT", "UNCOMMON", 25_000, false, null, 1, null));
		pets.add(pet("WOLF", "EPIC", 2_000_000, false, null, 0, null));
		pets.add(pet("TIGER", "LEGENDARY", 25_353_230, false, null, 0, null));
		pets.add(pet("SPIDER", "RARE", 15_000, false, null, 0, null));
		pets.add(pet("PARROT", "LEGENDARY", 1_000_000, false, null, 0, null));
		pets.add(pet("GUARDIAN", "MYTHIC", 6_000_000, false, null, 0, null));
		pets.add(pet("SKELETON_HORSE", "LEGENDARY", 400_000, false, null, 0, null));
		pets.add(pet("MITHRIL_GOLEM", "LEGENDARY", 25_353_230, false, null, 0, null));
		pets.add(pet("SCATHA", "EPIC", 3_000_000, false, null, 0, null));
		pets.add(pet("JELLYFISH", "LEGENDARY", 900_000, false, null, 0, null));
		pets.add(pet("BABY_YETI", "EPIC", 10_000, false, null, 0, null));
		pets.add(pet("MONKEY", "LEGENDARY", 7_000_000, false, null, 0, null));
		member.add("pets_data", TestData.obj("pets", pets));
	}

	private static void coop(JsonObject profile) {
		JsonObject coop = profile.getAsJsonObject("members").getAsJsonObject("0123456789abcdef0123456789abcdef");
		JsonObject col = new JsonObject();
		col.addProperty("WHEAT", 250_000);
		col.addProperty("COBBLESTONE", 100_000);
		coop.add("collection", col);
		JsonArray gens = new JsonArray();
		gens.add("WHEAT_11");
		gens.add("COBBLESTONE_10");
		coop.getAsJsonObject("player_data").add("crafted_generators", gens);
	}
}
