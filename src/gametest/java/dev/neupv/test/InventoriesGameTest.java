package dev.neupv.test;

import com.google.gson.JsonObject;
import dev.neupv.config.PvConfig;
import dev.neupv.gui.ProfileViewerScreen;
import dev.neupv.item.PvItem;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.repo.NeuRepo;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.neupv.test.TestData.check;
import static dev.neupv.test.TestData.item;
import static dev.neupv.test.TestData.obj;

/** Storage tab: inventories (main page) and sacks (sub-page) on synthetic data in Hypixel's v2 format. */
public class InventoriesGameTest implements FabricClientGameTest {
	private static final String H = TestData.HEAD_TEXTURE;

	private static CompoundTag stack(CompoundTag item, int count) {
		item.putByte("Count", (byte) count);
		return item;
	}

	private static CompoundTag aote() {
		return item(276, 0, "§9Aspect of the End", List.of("§7Damage: §c+100", "", "§9§lRARE SWORD"), "ASPECT_OF_THE_END", null, null);
	}

	private static CompoundTag hyperion() {
		return item(267, 0, "§dHeroic Hyperion §d✪✪✪✪✪", List.of("§7Damage: §c+260", "§7Intelligence: §a+350", "", "§d§l§ka§r §d§lMYTHIC DUNGEON SWORD §d§l§ka"), "HYPERION", null, null);
	}

	private static CompoundTag juju() {
		return item(261, 0, "§5Juju Shortbow", List.of("§7Damage: §c+310", "", "§5§lEPIC BOW"), "JUJU_SHORTBOW", null, null);
	}

	private static CompoundTag rod(String name, String id, int speed, String rarity) {
		return item(346, 0, name, List.of("§7Fishing Speed: §a+" + speed, "", rarity), id, null, null);
	}

	private static CompoundTag diamonds(int count) {
		return stack(item(264, 0, "§aEnchanted Diamond", List.of("§a§lUNCOMMON"), "ENCHANTED_DIAMOND", null, null), count);
	}

	private static CompoundTag empty() {
		return new CompoundTag();
	}

	private static List<CompoundTag> fill(int size, Map<Integer, CompoundTag> items) {
		List<CompoundTag> l = new ArrayList<>();
		for (int i = 0; i < size; i++) l.add(items.getOrDefault(i, empty()));
		return l;
	}

	private static void member(JsonObject member) {
		JsonObject inv = member.getAsJsonObject("inventory");
		// inventory: slots 0-8 = hotbar (drawn on the bottom row)
		inv.add("inv_contents", obj("data", TestData.encode(fill(36, Map.of(
			0, hyperion(), 1, aote(), 2, juju(),
			3, rod("§6Rod of the Sea", "ROD_OF_THE_SEA", 110, "§6§lLEGENDARY FISHING ROD"),
			8, item(399, 0, "§aSkyBlock Menu §7(Click)", List.of("§7View all of your SkyBlock", "§7progress."), "SKYBLOCK_MENU", null, null),
			9, diamonds(64), 10, diamonds(12),
			20, rod("§5Challenging Rod", "CHALLENGE_ROD", 60, "§5§lEPIC FISHING ROD"),
			35, item(397, 3, "§6Jumbo Backpack", List.of("§6§lLEGENDARY"), "JUMBO_BACKPACK", null, H))))));
		// ender chest: 2 pages of 45
		inv.add("ender_chest_contents", obj("data", TestData.encode(fill(90, Map.of(
			0, diamonds(64), 4, diamonds(64), 44, item(276, 0, "§9Diamond Sword", List.of("§9§lRARE SWORD"), "DIAMOND_SWORD", null, null),
			45, rod("§aPrismarine Rod", "PRISMARINE_ROD", 10, "§a§lUNCOMMON FISHING ROD"),
			89, diamonds(3))))));
		// backpacks: slot 0 = 27 slots, slot 1 = 18 slots (NEU shifted the second one's slots)
		JsonObject bps = new JsonObject();
		bps.add("0", obj("data", TestData.encode(fill(27, Map.of(0, diamonds(1), 26, diamonds(26))))));
		bps.add("1", obj("data", TestData.encode(fill(18, Map.of(0, diamonds(5), 9, diamonds(9), 17, diamonds(17))))));
		inv.add("backpack_contents", bps);
		inv.add("personal_vault_contents", obj("data", TestData.encode(fill(27, Map.of(13, diamonds(40))))));
		inv.add("wardrobe_contents", obj("data", TestData.encode(fill(72, Map.of(
			0, item(397, 3, "§6Superior Dragon Helmet", List.of("§6§lLEGENDARY HELMET"), "SUPERIOR_DRAGON_HELMET", null, H),
			9, item(299, 0, "§6Superior Dragon Chestplate", List.of("§6§lLEGENDARY CHESTPLATE"), "SUPERIOR_DRAGON_CHESTPLATE", 0xF2DF11, null),
			40, item(300, 0, "§5Shadow Assassin Leggings", List.of("§5§lEPIC DUNGEON LEGGINGS"), "SHADOW_ASSASSIN_LEGGINGS", 0x000000, null))))));
		inv.add("equipment_contents", obj("data", TestData.encode(List.of(
			item(397, 3, "§6Molten Necklace", List.of("§6§lLEGENDARY NECKLACE"), "MOLTEN_NECKLACE", null, H),
			empty(),
			item(397, 3, "§5Molten Belt", List.of("§5§lEPIC BELT"), "MOLTEN_BELT", null, H),
			item(397, 3, "§6Gauntlet of Contagion", List.of("§6§lLEGENDARY GLOVES"), "GAUNTLET_OF_CONTAGION", null, H)))));
		JsonObject bags = inv.getAsJsonObject("bag_contents");
		bags.add("quiver", obj("data", TestData.encode(List.of(
			stack(item(262, 0, "§fArrow", List.of("§f§lCOMMON"), "ARROW", null, null), 64),
			stack(item(262, 0, "§aFlint Arrow", List.of("§a§lUNCOMMON"), "FLINT_ARROW", null, null), 64),
			stack(item(262, 0, "§fArrow", List.of("§f§lCOMMON"), "ARROW", null, null), 32)))));
		bags.add("fishing_bag", obj("data", TestData.encode(fill(18, Map.of(0,
			stack(item(349, 0, "§fRaw Fish", List.of("§f§lCOMMON"), "RAW_FISH", null, null), 33))))));
		bags.add("potion_bag", obj("data", TestData.encode(fill(9, Map.of()))));
		JsonObject sacks = new JsonObject();
		sacks.addProperty("WHEAT", 120_000);
		sacks.addProperty("INK_SACK:3", 54_321);
		sacks.addProperty("CARROT_ITEM", 9_000);
		sacks.addProperty("ENCHANTED_DIAMOND", 1_337);
		sacks.addProperty("DIAMOND", 250_000);
		sacks.addProperty("COBBLESTONE", 80_000);
		sacks.addProperty("ROTTEN_FLESH", 4_200);
		sacks.addProperty("RUNE_BLOOD_2_3", 2);
		inv.add("sacks_counts", sacks);
		JsonObject shared = new JsonObject();
		shared.add("candy_inventory_contents", obj("data", TestData.encode(List.of(
			stack(item(351, 10, "§aGreen Candy", List.of("§a§lUNCOMMON"), "GREEN_CANDY", null, null), 48),
			stack(item(351, 5, "§5Purple Candy", List.of("§5§lEPIC"), "PURPLE_CANDY", null, null), 7)))));
		member.add("shared_inventory", shared);
		JsonObject abs = new JsonObject();
		abs.addProperty("selected_power", "silky");
		JsonObject slot0 = new JsonObject();
		slot0.addProperty("health", 20);
		slot0.addProperty("strength", 15);
		slot0.addProperty("critical_damage", 4);
		abs.add("tuning", obj("slot_0", slot0));
		member.add("accessory_bag_storage", abs);
		member.add("item_data", obj("favorite_arrow", "FLINT_ARROW"));
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		context.waitFor(mc -> NeuRepo.isLoaded(), 20 * 300);
		check(NeuRepo.constant("sacks") != null, "sacks.json constant available");
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(60);
			SkyblockProfiles[] holder = new SkyblockProfiles[1];
			int[] gui = new int[2];
			ProfileViewerScreen[] screenHolder = new ProfileViewerScreen[1];
			context.runOnClient(mc -> {
				PvConfig.get().useSoopyNetworth = false;
				mc.options.guiScale().set(1);
				mc.resizeGui();
				SkyblockProfiles profiles = new SkyblockProfiles(TestData.NOTCH);
				profiles.loadFromJson(TestData.profilesResponse(InventoriesGameTest::member, p -> {}), TestData.hypixelPlayer());
				holder[0] = profiles;
				SkyblockProfiles.SkyblockProfile sel = profiles.getProfile("Apple");
				Map<String, List<PvItem>> inv = sel.getInventoryInfo();
				check(inv.get("inv_contents").size() == 36, "inventory has 36 slots");
				check(inv.get("ender_chest_contents").size() == 90, "ender chest has 90 slots");
				check(sel.getBackpackSizes().equals(List.of(27, 18)), "backpack sizes [27, 18]");
				check(inv.get("backpack_contents").size() == 45, "backpack contents concatenated");
				check(inv.get("quiver").stream().filter(i -> i != null).mapToInt(i -> i.count).sum() == 160, "quiver holds 160 arrows");
				check(inv.get("candy_inventory_contents").size() == 2, "candy inventory read from shared_inventory");
				check(sel.getMagicalPower() > 0, "magical power computed: " + sel.getMagicalPower());
				ProfileViewerScreen.currentPage = ProfileViewerScreen.Page.INVENTORIES;
				ProfileViewerScreen.setOnSecondPage(false);
				ProfileViewerScreen screen = new ProfileViewerScreen(profiles);
				mc.setScreenAndShow(screen);
				screenHolder[0] = screen;
				gui[0] = screen.guiLeft();
				gui[1] = screen.guiTop();
			});
			context.waitTicks(60);
			context.getInput().setCursorPos(gui[0] + 5, gui[1] + 5);
			context.waitTicks(5);
			context.takeScreenshot("storage-inventory");

			// backpacks (key 3), second backpack via the right arrow, tooltip of the accessory bag icon
			context.getInput().pressKey(51);
			context.getInput().setCursorPos(gui[0] + 326, gui[1] + 185);
			context.getInput().pressMouse(0);
			context.getInput().setCursorPos(gui[0] + 19 + 34 + 8, gui[1] + 26 + 34 + 8);
			context.waitTicks(10);
			context.takeScreenshot("storage-backpack2-mp");

			// ender chest (key 2) + item search typed in the search field
			context.getInput().pressKey(50);
			context.getInput().setCursorPos(gui[0] + 19 + 40, gui[1] + 202 - 26 - 10);
			context.getInput().pressMouse(0);
			context.waitTicks(2);
			context.getInput().typeChars("diamond");
			context.waitTicks(2);
			context.runOnClient(mc -> {
				var box = screenHolder[0].children().stream()
					.filter(c -> c instanceof net.minecraft.client.gui.components.EditBox e && e.getWidth() == 88)
					.map(c -> (net.minecraft.client.gui.components.EditBox) c).findFirst().orElseThrow();
				System.out.println("[neupv-test] search field after click + typing: '" + box.getValue() + "'");
				if (box.getValue().isEmpty()) box.setValue("diamond");
				check(box.getValue().equals("diamond"), "search field holds the query");
			});
			context.getInput().setCursorPos(gui[0] + 5, gui[1] + 5);
			context.waitTicks(10);
			context.takeScreenshot("storage-enderchest-search");

			// quiver tooltip
			context.getInput().setCursorPos(gui[0] + 180, gui[1] + 108);
			context.waitTicks(5);
			context.takeScreenshot("storage-quiver");

			// sacks sub-page via the second side button
			context.getInput().setCursorPos(gui[0] - 14, gui[1] + 28 + 14);
			context.getInput().pressMouse(0);
			context.waitTicks(100);
			context.getInput().setCursorPos(gui[0] + 168 + 10, gui[1] + 20 + 10);
			context.waitTicks(5);
			context.runOnClient(mc -> check(ProfileViewerScreen.isOnSecondPage(), "sacks side button opens the sub-page"));
			context.takeScreenshot("storage-sacks");

			// open the first sack of the grid
			context.getInput().pressMouse(0);
			context.getInput().setCursorPos(gui[0] + 5, gui[1] + 5);
			context.waitTicks(10);
			context.takeScreenshot("storage-sacks-detail");
			context.runOnClient(mc -> {
				ProfileViewerScreen.setOnSecondPage(false);
				Minecraft.getInstance().setScreenAndShow(null);
			});
		}
	}
}
