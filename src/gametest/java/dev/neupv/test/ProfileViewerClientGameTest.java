package dev.neupv.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.neupv.config.PvConfig;
import dev.neupv.gui.ProfileViewerScreen;
import dev.neupv.item.LegacyItems;
import dev.neupv.item.PvItem;
import dev.neupv.net.Mojang;
import dev.neupv.pv.Level;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.pv.Weights;
import dev.neupv.repo.NeuRepo;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.item.Items;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Launches the real 26.3 client, checks the PV data layer on a synthetic profile in Hypixel's exact format
 * (1.8.9 gzip NBT inventories, v2 profiles JSON) and renders the Skills tab for a screenshot.
 */
public class ProfileViewerClientGameTest implements FabricClientGameTest {
	private static final UUID NOTCH = TestData.NOTCH;
	private static final String HEAD_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODdkODg1YjMyYjBkZDJkNmI3ZjFiNTgyYTM0MTg2ZjhhNTM3M2M0NjU4OWEyNzM0MjMxMzJiNDQ4YjgwMzQ2MiJ9fX0=";

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitFor(mc -> NeuRepo.isLoaded(), 20 * 300);
		check(NeuRepo.weight() != null, "weight.json available (embedded fallback)");
		check(NeuRepo.sblevels() != null, "sblevels.json available (embedded fallback)");

		try {
			check(Mojang.uuidOf("zqxv_nonexist_9k").join().isEmpty(), "unknown player name -> empty (no silent failure)");
		} catch (java.util.concurrent.CompletionException e) {
			System.out.println("[neupv-test] SKIPPED unknown-name check, Mojang API error: " + e.getCause());
		}
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(60);
			context.runOnClient(mc -> {
				PvConfig.get().useSoopyNetworth = false;
				mc.options.guiScale().set(1);
				mc.resizeGui();
				testItems();
				SkyblockProfiles profiles = new SkyblockProfiles(NOTCH);
				profiles.loadFromJson(TestData.profilesResponse(), TestData.hypixelPlayer());
				testProfile(profiles);
				mc.setScreenAndShow(new ProfileViewerScreen(profiles));
			});
			context.waitTicks(100); // skin download, item models
			context.takeScreenshot("neupv-skills");
			context.runOnClient(mc -> ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.DUNGEON);
			context.waitTicks(5);
			context.takeScreenshot("neupv-not-ported-tab");
		}
	}

	private static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
		System.out.println("[neupv-test] OK: " + what);
	}

	private static void testItems() {
		List<PvItem> items = LegacyItems.decodeInventory(TestData.armorBase64());
		check(items.size() == 4, "armor decodes to 4 items");
		check(items.get(0).stack().is(Items.LEATHER_BOOTS), "id 301 -> leather_boots");
		check(items.get(0).stack().get(DataComponents.DYED_COLOR) != null, "leather colour kept");
		check(items.get(3).stack().is(Items.PLAYER_HEAD), "skull:3 -> player_head");
		check(items.get(3).stack().get(DataComponents.PROFILE) != null, "head texture kept");
		check("SUPERIOR_DRAGON_HELMET".equals(items.get(3).skyblockId), "SkyBlock id read");
		check(items.get(2).stack().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) != null, "enchant glint kept");
		check(items.get(3).rarity() == 4, "rarity from lore = LEGENDARY");
		List<PvItem> glass = LegacyItems.decodeInventory(TestData.encode(List.of(TestData.item(160, 14, "§cPane", List.of(), null, null, null))));
		check(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(glass.get(0).stack().getItem()).getPath().equals("red_stained_glass_pane"), "stained_glass_pane:14 -> red_stained_glass_pane");
	}

	private static void testProfile(SkyblockProfiles profiles) {
		check(profiles.getState() == SkyblockProfiles.State.LOADED, "profiles parsed");
		check("Apple".equals(profiles.getLatestProfileName()), "selected profile detected");
		SkyblockProfiles.SkyblockProfile p = profiles.getProfile("Apple");
		Map<String, Level> lv = p.getLevelingInfo();
		check(lv.get("combat").level >= 30 && lv.get("combat").level < 31, "combat level 30 (" + lv.get("combat").level + ")");
		check(lv.get("farming").maxLevel == 60, "farming cap 50 + jacob perk 10");
		check(lv.get("zombie").level >= 8 && lv.get("zombie").level < 9, "revenant slayer level 8 (" + lv.get("zombie").level + ")");
		check(lv.get("social").totalXp == 1500, "social summed over coop members");
		// Wolf Ring (UNCOMMON 5) + Wolf Talisman (COMMON, superseded) + Speed Artifact (RARE 8) = 13. NEU 2.6.0 gave 11.
		check(p.getMagicalPower() == 13, "magical power 13 with the family fix (got " + p.getMagicalPower() + ")");
		Weights.Result s = Weights.senither(lv);
		Weights.Result l = Weights.lily(lv, p.getProfileJson());
		check(s != null && s.total() > 0, "senither weight " + (s == null ? "null" : s.total()));
		check(l != null && l.total() > 0, "lily weight " + (l == null ? "null" : l.total()));
		check(p.getSkyblockLevel() == 245.5, "skyblock level 245.5");
		System.out.println("[neupv-test] senither=" + s.total() + " lily=" + l.total() + " level colour=" + p.getSkyblockLevelColour().name());
	}

}
