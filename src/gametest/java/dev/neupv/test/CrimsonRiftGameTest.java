package dev.neupv.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.neupv.config.PvConfig;
import dev.neupv.gui.ProfileViewerScreen;
import dev.neupv.pv.Level;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.J;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

import static dev.neupv.test.TestData.check;
import static dev.neupv.test.TestData.obj;

/** Crimson Isle and Rift tabs on synthetic Hypixel v2 data; screenshots both (and the rift ender chest). */
public class CrimsonRiftGameTest implements FabricClientGameTest {
	private static final String SOUL = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODdkODg1YjMyYjBkZDJkNmI3ZjFiNTgyYTM0MTg2ZjhhNTM3M2M0NjU4OWEyNzM0MjMxMzJiNDQ4YjgwMzQ2MiJ9fX0=";

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
				profiles.loadFromJson(TestData.profilesResponse(CrimsonRiftGameTest::member, p -> {}), TestData.hypixelPlayer());
				holder[0] = profiles;
				checks(profiles);
			});
			shot(context, holder[0], ProfileViewerScreen.Page.CRIMSON_ISLE, "crimson-isle");
			shot(context, holder[0], ProfileViewerScreen.Page.RIFT, "rift-inventory");
			context.getInput().pressKey(50); // GLFW_KEY_2 -> ender chest
			context.waitTicks(20);
			context.takeScreenshot("rift-enderchest");
			context.getInput().pressKey(262); // right arrow -> page 2
			context.waitTicks(20);
			context.takeScreenshot("rift-enderchest-p2");
		}
	}

	private static void shot(ClientGameTestContext context, SkyblockProfiles profiles, ProfileViewerScreen.Page page, String name) {
		context.runOnClient(mc -> {
			ProfileViewerScreen.currentPage = page;
			mc.setScreenAndShow(new ProfileViewerScreen(profiles));
		});
		context.waitTicks(60);
		context.takeScreenshot(name);
	}

	private static void checks(SkyblockProfiles profiles) {
		SkyblockProfiles.SkyblockProfile p = profiles.getProfile("Apple");
		JsonObject pj = p.getProfileJson();
		check(J.i(pj, "nether_island_player_data.kuudra_completed_tiers.infernal", 0) == 12, "kuudra infernal completions read");
		check(J.arrOrEmpty(pj, "rift.enigma.found_souls").size() == 37, "37 enigma souls");
		Level vamp = p.getLevelingInfo().get("vampire");
		check(vamp != null && (int) vamp.level == 4, "vampire level 4 from 1,200 xp (got " + (vamp == null ? null : vamp.level) + ")");
		check(dev.neupv.item.LegacyItems.decodeInventory(J.str(pj, "rift.inventory.ender_chest_contents.data", null)).size() == 90, "rift ender chest decodes 90 slots");
		check(NeuRepo.item("FRACTURED_MONTEZUMA_SOUL;3") != null, "montezuma repo item exists");
		check(NeuRepo.item("RIFT_TROPHY_MOUNTAIN") != null, "timecharm repo item exists");
	}

	private static void member(JsonObject m) {
		// ---------------- Crimson Isle ----------------
		JsonObject nether = new JsonObject();
		nether.addProperty("selected_faction", "barbarians");
		nether.addProperty("mages_reputation", -450);
		nether.addProperty("barbarians_reputation", 12_350);
		JsonObject kuudra = new JsonObject();
		kuudra.addProperty("none", 85);
		kuudra.addProperty("hot", 64);
		kuudra.addProperty("highest_wave_hot", 7);
		kuudra.addProperty("burning", 120);
		kuudra.addProperty("highest_wave_burning", 9);
		kuudra.addProperty("fiery", 48);
		kuudra.addProperty("highest_wave_fiery", 10);
		kuudra.addProperty("infernal", 12);
		kuudra.addProperty("highest_wave_infernal", 9);
		nether.add("kuudra_completed_tiers", kuudra);
		JsonObject dojo = new JsonObject();
		int[] points = {1000, 820, 1000, 650, 990, 400, 1000};
		String[] keys = {"mob_kb", "wall_jump", "archer", "sword_swap", "snake", "lock_head", "fireball"};
		for (int i = 0; i < keys.length; i++) {
			dojo.addProperty("dojo_points_" + keys[i], points[i]);
			dojo.addProperty("dojo_time_" + keys[i], 12_340 + i * 4_100);
		}
		nether.add("dojo", dojo);
		JsonObject matriarch = new JsonObject();
		matriarch.addProperty("pearls_collected", 5);
		matriarch.addProperty("last_attempt", 1_762_400_000_000L);
		nether.add("matriarch", matriarch);
		nether.add("abiphone", new JsonObject());
		m.add("nether_island_player_data", nether);

		// ---------------- Rift ----------------
		JsonObject rift = new JsonObject();
		JsonObject inv = new JsonObject();
		List<CompoundTag> invItems = new ArrayList<>();
		for (int i = 0; i < 36; i++) invItems.add(new CompoundTag());
		invItems.set(0, TestData.item(267, 0, "§9Anti-Bite Scarf", List.of("§7Damage: §c+60", "", "§9§lRARE SWORD"), "ANTI_BITE_SCARF", null, null));
		invItems.set(1, TestData.item(283, 0, "§5Silkwire Stick", List.of("§5§lEPIC SWORD"), "SILKWIRE_STICK", null, null));
		invItems.set(2, TestData.item(397, 3, "§aSkyBlock Menu", List.of("§aUNCOMMON"), "SKYBLOCK_MENU", null, SOUL));
		CompoundTag motes = TestData.item(341, 0, "§dMote Grubber Juice", List.of("§f§lCOMMON"), "MOTE_GRUBBER_JUICE", null, null);
		motes.putByte("Count", (byte) 12);
		invItems.set(8, motes);
		invItems.set(9, TestData.item(368, 0, "§9Wyldly Supreme", List.of("§9§lRARE"), "WYLDLY_SUPREME", null, null));
		invItems.set(13, TestData.item(264, 0, "§aCentury Cake", List.of("§a§lUNCOMMON"), "CENTURY_CAKE", null, null));
		invItems.set(20, TestData.item(289, 0, "§fAgaricus Cap", List.of("§f§lCOMMON"), "AGARICUS_CAP", null, null));
		invItems.set(35, TestData.item(351, 4, "§9Lapis", List.of("§f§lCOMMON"), null, null, null));
		inv.add("inv_contents", obj("data", TestData.encode(invItems)));
		inv.add("inv_armor", obj("data", TestData.encode(List.of(
			TestData.item(301, 0, "§5Eleanor's Slippers", List.of("§5§lEPIC BOOTS"), "ELEANOR_SLIPPERS", 0x9C2A99, null),
			TestData.item(300, 0, "§5Eleanor's Leggings", List.of("§5§lEPIC LEGGINGS"), "ELEANOR_LEGGINGS", 0x9C2A99, null),
			TestData.item(299, 0, "§5Eleanor's Tunic", List.of("§5§lEPIC CHESTPLATE"), "ELEANOR_TUNIC", 0x9C2A99, null),
			TestData.item(397, 3, "§5Eleanor's Cap", List.of("§5§lEPIC HELMET"), "ELEANOR_CAP", null, SOUL)))));
		inv.add("equipment_contents", obj("data", TestData.encode(List.of(
			TestData.item(397, 3, "§9Gill Necklace", List.of("§9§lRARE NECKLACE"), "GILL_NECKLACE", null, SOUL),
			new CompoundTag(),
			TestData.item(397, 3, "§5Lavawalker Belt", List.of("§5§lEPIC BELT"), "LAVAWALKER_BELT", null, SOUL),
			TestData.item(397, 3, "§6Gauntlet of Contagion", List.of("§6§lLEGENDARY GLOVES"), "GAUNTLET_OF_CONTAGION", null, SOUL)))));
		List<CompoundTag> ec = new ArrayList<>();
		for (int i = 0; i < 90; i++) {
			if (i % 7 == 0) ec.add(TestData.item(264, 0, "§fRift Gem #" + i, List.of("§f§lCOMMON"), "RIFT_GEM", null, null));
			else if (i % 11 == 0) ec.add(TestData.item(397, 3, "§9Enigma Thing", List.of("§9§lRARE"), "ENIGMA_THING", null, SOUL));
			else ec.add(new CompoundTag());
		}
		ec.set(50, TestData.item(368, 0, "§5Page Two Pearl", List.of("§5§lEPIC"), "PAGE_TWO", null, null));
		inv.add("ender_chest_contents", obj("data", TestData.encode(ec)));
		rift.add("inventory", inv);

		JsonObject deadCats = new JsonObject();
		JsonArray cats = new JsonArray();
		for (String c : new String[]{"vampire_cat", "wizard_cat", "barry_cat", "lagoon_cat", "village_cat", "mushroom_cat"}) cats.add(c);
		deadCats.add("found_cats", cats);
		JsonObject montezuma = new JsonObject();
		montezuma.addProperty("uuid", "8d2f3f0a2b6f4a5f9a4b8c1c2d3e4f50");
		montezuma.addProperty("type", "FRACTURED_MONTEZUMA_SOUL");
		montezuma.addProperty("exp", 0);
		montezuma.addProperty("active", false);
		montezuma.addProperty("tier", "EPIC");
		montezuma.addProperty("candyUsed", 0);
		deadCats.add("montezuma", montezuma);
		rift.add("dead_cats", deadCats);

		JsonArray souls = new JsonArray();
		for (int i = 0; i < 37; i++) souls.add("soul_" + i);
		rift.add("enigma", obj("found_souls", souls));
		JsonArray trophies = new JsonArray();
		for (String t : new String[]{"wyldly_supreme", "mirrored", "chicken_n_egg", "citizen", "mountain"}) {
			JsonObject o = new JsonObject();
			o.addProperty("type", t);
			o.addProperty("timestamp", 1_700_000_000_000L);
			o.addProperty("visits", 3);
			trophies.add(o);
		}
		rift.add("gallery", obj("secured_trophies", trophies));
		rift.add("castle", obj("grubber_stacks", 4));
		m.add("rift", rift);

		m.getAsJsonObject("currencies").addProperty("motes_purse", 412_345.6);
		m.add("player_stats", obj("rift", obj("lifetime_motes_earned", 5_678_901)));
		J.obj(m, "slayer.slayer_bosses").add("vampire", obj("xp", 1_200));
	}
}
