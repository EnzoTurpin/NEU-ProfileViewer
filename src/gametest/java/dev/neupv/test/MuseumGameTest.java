package dev.neupv.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.neupv.config.PvConfig;
import dev.neupv.gui.MuseumData;
import dev.neupv.gui.ProfileViewerScreen;
import dev.neupv.net.Mojang;
import dev.neupv.net.UrsaClient;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static dev.neupv.test.TestData.check;

/** Museum tab: pure parsing checks on synthetic API answers, then screenshots (data injected in the fetch cache). */
public class MuseumGameTest implements FabricClientGameTest {
	static final String PROFILE_ID = "4ae7a3c1-5b2c-4d8e-9f10-112233445566";
	private static final String UUID = Mojang.undashed(TestData.NOTCH);

	private static final ProfileViewerScreen[] SCREEN = new ProfileViewerScreen[1];

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitFor(mc -> NeuRepo.isLoaded(), 20 * 300);
		context.runOnClient(mc -> testParsing());
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(60);
			SkyblockProfiles[] holder = new SkyblockProfiles[1];
			context.runOnClient(mc -> {
				PvConfig.get().useSoopyNetworth = false;
				mc.options.guiScale().set(1);
				mc.resizeGui();
				SkyblockProfiles profiles = new SkyblockProfiles(TestData.NOTCH);
				profiles.loadFromJson(TestData.profilesResponse(m -> {}, p -> p.addProperty("profile_id", PROFILE_ID)), TestData.hypixelPlayer());
				injectFetch(profiles, UrsaClient.museum(PROFILE_ID), museumResponse());
				holder[0] = profiles;
				ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.MUSEUM;
				SCREEN[0] = new ProfileViewerScreen(profiles);
				mc.setScreenAndShow(SCREEN[0]);
			});
			context.waitTicks(40);
			// Click ROSETTA (combat index 15 -> column 1, row 2) to open the donation panel, and hover it.
			context.runOnClient(mc -> {
				ProfileViewerScreen s = SCREEN[0];
				int x = s.guiLeft() + 251 - 88 + 8 + 18 + 18 + 4, y = s.guiTop() + 71 + 36 + 4;
				TestHolder.pos = new double[]{x, y};
			});
			context.getInput().setCursorPos(TestHolder.pos[0], TestHolder.pos[1]);
			context.getInput().pressMouse(0);
			context.waitTicks(20);
			context.takeScreenshot("museum-combat");
			// Special category (key 8) and an error state (no proxy configured -> real fetch fails, must not crash)
			context.getInput().setCursorPos(0, 0);
			context.getInput().pressKey(56);
			context.waitTicks(20);
			context.takeScreenshot("museum-special");
			context.runOnClient(mc -> {
				SkyblockProfiles profiles = new SkyblockProfiles(TestData.NOTCH);
				profiles.loadFromJson(TestData.profilesResponse(), TestData.hypixelPlayer());
				mc.setScreenAndShow(new ProfileViewerScreen(profiles));
			});
			context.waitTicks(40);
			context.takeScreenshot("museum-error");
		}
	}

	static final class TestHolder {
		static double[] pos;
	}

	@SuppressWarnings("unchecked")
	static void injectFetch(SkyblockProfiles profiles, String key, JsonObject json) {
		try {
			Field f = SkyblockProfiles.class.getDeclaredField("fetches");
			f.setAccessible(true);
			((Map<String, SkyblockProfiles.Fetch>) f.get(profiles)).put(key, new SkyblockProfiles.Fetch(SkyblockProfiles.FetchState.OK, json, null));
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	private static JsonObject donation(long time, boolean borrowing, List<CompoundTag> items) {
		JsonObject o = new JsonObject();
		o.addProperty("donated_time", time);
		if (borrowing) o.addProperty("borrowing", true);
		JsonObject data = new JsonObject();
		data.addProperty("type", 0);
		data.addProperty("data", TestData.encode(items));
		o.add("items", data);
		return o;
	}

	static JsonObject museumResponse() {
		long day = 86_400_000L;
		long now = System.currentTimeMillis();
		JsonObject items = new JsonObject();
		items.add("ASPECT_OF_THE_JERRY", donation(now - 3 * day, false, List.of(
			TestData.item(268, 0, "§fAspect of the Jerry", List.of("§7Damage: §c+1", "", "§f§lCOMMON SWORD"), "ASPECT_OF_THE_JERRY", null, null))));
		items.add("ROSETTA", donation(now - 40 * day, true, List.of(
			TestData.item(397, 3, "§9Rosetta's Helmet", List.of("§9§lRARE HELMET"), "ROSETTA_HELMET", null, TestData.HEAD_TEXTURE),
			TestData.item(299, 0, "§9Rosetta's Chestplate", List.of("§9§lRARE CHESTPLATE"), "ROSETTA_CHESTPLATE", 0x111111, null),
			TestData.item(300, 0, "§9Rosetta's Leggings", List.of("§9§lRARE LEGGINGS"), "ROSETTA_LEGGINGS", 0x111111, null),
			TestData.item(301, 0, "§9Rosetta's Boots", List.of("§9§lRARE BOOTS"), "ROSETTA_BOOTS", 0x111111, null))));
		items.add("REVENANT_SWORD", donation(now - 5 * day, false, List.of(
			TestData.item(267, 0, "§9Revenant Falchion", List.of("§9§lRARE SWORD"), "REVENANT_SWORD", null, null))));
		items.add("END_SWORD", donation(now - 2 * 3_600_000L, false, List.of(
			TestData.item(276, 0, "§aEnd Sword", List.of("§a§lUNCOMMON SWORD"), "END_SWORD", null, null))));
		JsonArray special = new JsonArray();
		special.add(donation(now - 100 * day, false, List.of(
			TestData.item(354, 0, "§cNew Year Cake (Year 300)", List.of("§c§lSPECIAL"), "NEW_YEAR_CAKE", null, null))));
		special.add(donation(now - 1000 * day, false, List.of())); // empty donation: NEU threw on contents.get(0)
		JsonObject member = new JsonObject();
		member.addProperty("value", 1_234_567_890L);
		member.addProperty("appraisal", false);
		member.add("items", items);
		member.add("special", special);
		JsonObject members = new JsonObject();
		members.add(UUID, member);
		JsonObject resp = new JsonObject();
		resp.addProperty("success", true);
		resp.add("members", members);
		return resp;
	}

	private static void testParsing() {
		JsonObject repo = NeuRepo.constant("museum");
		check(repo != null, "museum.json available");
		MuseumData d = MuseumData.parse(museumResponse(), UUID, repo);
		check(d.value == 1_234_567_890L, "museum value parsed: " + d.value);
		check(d.categories.contains("combat") && d.categories.contains("fishing") && !d.categories.contains("special"),
			"current repo layout -> categories " + d.categories);
		Map<String, MuseumData.Donation> combat = d.donated.get("combat");
		check(combat.containsKey("ASPECT_OF_THE_JERRY") && !combat.get("ASPECT_OF_THE_JERRY").higherTier()
			&& "§fAspect of the Jerry".equals(combat.get("ASPECT_OF_THE_JERRY").items().get(0).displayName), "donated item decoded from base64 NBT");
		check(combat.get("ROSETTA").items().size() == 4, "armor set keeps its 4 pieces");
		check(combat.containsKey("UNDEAD_SWORD") && combat.get("UNDEAD_SWORD").higherTier(), "REVENANT_SWORD marks child UNDEAD_SWORD as donated as higher tier");
		check(!combat.containsKey("CELESTE"), "not donated -> missing");
		check(d.saved.get("ROSETTA").borrowing() && !d.saved.get("END_SWORD").borrowing(), "borrowing flag parsed");
		check(d.saved.containsKey("UNDEAD_SWORD"), "children inherit the donation date");
		check(d.special.size() == 1 && "NEW_YEAR_CAKE".equals(d.special.get(0).id()), "special items parsed, empty donation skipped");
		check(d.totalDonated() == 5, "total donations (4 + 1 higher tier) = " + d.totalDonated());
		check(d.maximum("total") > 100, "max_values.total read: " + d.maximum("total"));
		check(d.itemIdFor("ROSETTA").equals("ROSETTA_HELMET"), "armor_to_id mapping");

		check(MuseumData.parse(museumResponse(), "00000000000000000000000000000000", repo).value == MuseumData.API_DISABLED, "member missing -> API disabled");
		JsonObject noValue = museumResponse();
		noValue.getAsJsonObject("members").getAsJsonObject(UUID).remove("value");
		check(MuseumData.parse(noValue, UUID, repo).value == MuseumData.API_DISABLED, "no value -> API disabled");
		check(MuseumData.parse(museumResponse(), UUID, null).value == MuseumData.MISSING_REPO, "no repo -> missing repo data");

		// NEU 2.6.0's museum.json layout (top-level weapons / armor / rarities)
		JsonObject old = JsonParser.parseString("""
			{"weapons":["ASPECT_OF_THE_JERRY","UNDEAD_SWORD","REVENANT_SWORD"],"armor":["ROSETTA"],"rarities":["END_SWORD"],
			 "children":{"REVENANT_SWORD":"UNDEAD_SWORD"},"armor_to_id":{"ROSETTA":"ROSETTA_HELMET"},
			 "max_values":{"weapons":3,"armor":1,"rarities":1,"total":5,"special":1}}""").getAsJsonObject();
		MuseumData o = MuseumData.parse(museumResponse(), UUID, old);
		check(o.categories.equals(List.of("weapons", "armor", "rarities")), "old repo layout -> weapons/armor/rarities");
		check(o.donatedCount("weapons") == 3 && o.donatedCount("armor") == 1 && o.donatedCount("rarities") == 1, "old layout counts 3/1/1");
		check(o.totalDonated() == 5, "old layout total 5");
	}
}
