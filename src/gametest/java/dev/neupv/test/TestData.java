package dev.neupv.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.neupv.config.PvConfig;
import dev.neupv.gui.ProfileViewerScreen;
import dev.neupv.net.Mojang;
import dev.neupv.pv.SkyblockProfiles;
import dev.neupv.pv.Weights;
import dev.neupv.repo.NeuRepo;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Synthetic SkyBlock data in Hypixel's exact API format, shared by all PV game tests. */
public final class TestData {
	public static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
	public static final String HEAD_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODdkODg1YjMyYjBkZDJkNmI3ZjFiNTgyYTM0MTg2ZjhhNTM3M2M0NjU4OWEyNzM0MjMxMzJiNDQ4YjgwMzQ2MiJ9fX0=";

	private TestData() {}

	public static void check(boolean ok, String what) {
		if (!ok) throw new AssertionError("FAILED: " + what);
		System.out.println("[neupv-test] OK: " + what);
	}

	/** Waits for the repo, creates a world, loads the synthetic profile and screenshots each requested tab. */
	public static void screenshotTabs(ClientGameTestContext context, Consumer<JsonObject> member, Consumer<JsonObject> profile,
		Consumer<SkyblockProfiles> checks, String prefix, ProfileViewerScreen.Page... pages) {
		context.waitFor(mc -> NeuRepo.isLoaded(), 20 * 300);
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			context.waitTicks(60);
			SkyblockProfiles[] holder = new SkyblockProfiles[1];
			context.runOnClient(mc -> {
				PvConfig.get().useSoopyNetworth = false;
				mc.options.guiScale().set(1);
				mc.resizeGui();
				SkyblockProfiles profiles = new SkyblockProfiles(NOTCH);
				profiles.loadFromJson(profilesResponse(member, profile), hypixelPlayer());
				holder[0] = profiles;
				if (checks != null) checks.accept(profiles);
			});
			for (ProfileViewerScreen.Page page : pages) {
				context.runOnClient(mc -> {
					ProfileViewerScreen.currentPage = page;
					mc.setScreenAndShow(new ProfileViewerScreen(holder[0]));
				});
				context.waitTicks(60);
				context.takeScreenshot(prefix + "-" + page.name().toLowerCase());
			}
		}
	}

	// ---------- synthetic data in Hypixel's format ----------

	public static CompoundTag item(int id, int damage, String name, List<String> lore, String sbId, Integer color, String texture) {
		CompoundTag nbt = new CompoundTag();
		nbt.putShort("id", (short) id);
		nbt.putShort("Damage", (short) damage);
		nbt.putByte("Count", (byte) 1);
		CompoundTag tag = new CompoundTag();
		CompoundTag display = new CompoundTag();
		display.putString("Name", name);
		ListTag l = new ListTag();
		for (String s : lore) l.add(StringTag.valueOf(s));
		display.put("Lore", l);
		if (color != null) display.putInt("color", color);
		tag.put("display", display);
		if (sbId != null) {
			CompoundTag ea = new CompoundTag();
			ea.putString("id", sbId);
			tag.put("ExtraAttributes", ea);
		}
		if (texture != null) {
			CompoundTag owner = new CompoundTag();
			CompoundTag props = new CompoundTag();
			ListTag textures = new ListTag();
			CompoundTag t = new CompoundTag();
			t.putString("Value", texture);
			textures.add(t);
			props.put("textures", textures);
			owner.put("Properties", props);
			tag.put("SkullOwner", owner);
		}
		nbt.put("tag", tag);
		return nbt;
	}

	public static String encode(List<CompoundTag> items) {
		try {
			CompoundTag root = new CompoundTag();
			ListTag list = new ListTag();
			list.addAll(items);
			root.put("i", list);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			NbtIo.writeCompressed(root, out);
			return Base64.getEncoder().encodeToString(out.toByteArray());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static String armorBase64() {
		CompoundTag chest = item(299, 0, "§6Superior Dragon Chestplate", List.of("§6§lLEGENDARY CHESTPLATE"), "SUPERIOR_DRAGON_CHESTPLATE", 0xF2DF11, null);
		CompoundTag ench = new CompoundTag();
		ench.putShort("id", (short) 0);
		ListTag enchList = new ListTag();
		enchList.add(ench);
		chest.getCompoundOrEmpty("tag").put("ench", enchList);
		return encode(List.of(
			item(301, 0, "§6Superior Dragon Boots", List.of("§6§lLEGENDARY BOOTS"), "SUPERIOR_DRAGON_BOOTS", 0xF25D18, null),
			item(300, 0, "§6Superior Dragon Leggings", List.of("§6§lLEGENDARY LEGGINGS"), "SUPERIOR_DRAGON_LEGGINGS", 0xF2DF11, null),
			chest,
			item(397, 3, "§6Superior Dragon Helmet", List.of("§6§lLEGENDARY HELMET"), "SUPERIOR_DRAGON_HELMET", null, HEAD_TEXTURE)));
	}

	public static String talismanBase64() {
		return encode(List.of(
			item(397, 3, "§aWolf Ring", List.of("§a§lUNCOMMON ACCESSORY"), "WOLF_RING", null, HEAD_TEXTURE),
			item(397, 3, "§fWolf Talisman", List.of("§f§lCOMMON ACCESSORY"), "WOLF_TALISMAN", null, HEAD_TEXTURE),
			item(397, 3, "§9Speed Artifact", List.of("§9§lRARE ACCESSORY"), "SPEED_ARTIFACT", null, HEAD_TEXTURE)));
	}

	public static JsonObject profilesResponse() {
		return profilesResponse(m -> {}, p -> {});
	}

	/** Synthetic v2 /skyblock/profiles answer for NOTCH; the customizers edit the member and the outer profile of "Apple". */
	public static JsonObject profilesResponse(java.util.function.Consumer<JsonObject> memberCustomizer, java.util.function.Consumer<JsonObject> profileCustomizer) {
		String uuid = Mojang.undashed(NOTCH);
		JsonObject member = new JsonObject();
		JsonObject exp = new JsonObject();
		String[] skills = {"COMBAT", "MINING", "FARMING", "FORAGING", "FISHING", "ENCHANTING", "ALCHEMY", "TAMING", "CARPENTRY", "RUNECRAFTING", "SOCIAL"};
		double[] xp = {9_000_000, 55_000_000, 111_000_000, 2_000_000, 7_000_000, 120_000_000, 55_172_425, 30_000_000, 6_000_000, 40_000, 1000};
		for (int i = 0; i < skills.length; i++) exp.addProperty("SKILL_" + skills[i], xp[i]);
		JsonObject playerData = new JsonObject();
		playerData.add("experience", exp);
		member.add("player_data", playerData);
		member.add("leveling", obj("experience", 24550));
		JsonObject bosses = new JsonObject();
		bosses.add("zombie", obj("xp", 500_000));
		bosses.add("spider", obj("xp", 120_000));
		bosses.add("wolf", obj("xp", 1_200_000));
		bosses.add("enderman", obj("xp", 30_000));
		bosses.add("blaze", obj("xp", 0));
		bosses.add("vampire", obj("xp", 0));
		member.add("slayer", obj("slayer_bosses", bosses));
		JsonObject cata = obj("experience", 50_000_000);
		JsonObject tc = new JsonObject();
		tc.addProperty("7", 120);
		cata.add("tier_completions", tc);
		JsonObject dungeons = new JsonObject();
		dungeons.add("dungeon_types", obj("catacombs", cata));
		JsonObject classes = new JsonObject();
		for (String c : Weights.DUNGEON_CLASS_NAMES) classes.add(c, obj("experience", 10_000_000));
		dungeons.add("player_classes", classes);
		member.add("dungeons", dungeons);
		JsonObject inventory = new JsonObject();
		inventory.add("inv_armor", obj("data", armorBase64()));
		JsonObject bags = new JsonObject();
		bags.add("talisman_bag", obj("data", talismanBase64()));
		inventory.add("bag_contents", bags);
		member.add("inventory", inventory);
		member.add("currencies", obj("coin_purse", 12_345_678));
		JsonObject perks = new JsonObject();
		perks.addProperty("farming_level_cap", 10);
		perks.addProperty("double_drops", 15);
		member.add("jacobs_contest", obj("perks", perks));
		JsonArray pets = new JsonArray();
		JsonObject pet = new JsonObject();
		pet.addProperty("type", "ENDER_DRAGON");
		pet.addProperty("tier", "LEGENDARY");
		pet.addProperty("exp", 25_000_000);
		pet.addProperty("active", true);
		pets.add(pet);
		member.add("pets_data", obj("pets", pets));

		JsonObject coopMember = new JsonObject();
		JsonObject coopData = new JsonObject();
		coopData.add("experience", obj("SKILL_SOCIAL", 500));
		coopMember.add("player_data", coopData);

		memberCustomizer.accept(member);
		JsonObject members = new JsonObject();
		members.add(uuid, member);
		members.add("0123456789abcdef0123456789abcdef", coopMember);
		JsonObject profile = new JsonObject();
		profile.addProperty("profile_id", UUID.randomUUID().toString());
		profile.addProperty("cute_name", "Apple");
		profile.addProperty("selected", true);
		profile.add("members", members);
		profile.add("banking", obj("balance", 1_000_000_000));
		profileCustomizer.accept(profile);
		JsonObject other = new JsonObject();
		other.addProperty("profile_id", UUID.randomUUID().toString());
		other.addProperty("cute_name", "Banana");
		other.addProperty("game_mode", "ironman");
		JsonObject otherMembers = new JsonObject();
		otherMembers.add(uuid, new JsonObject());
		other.add("members", otherMembers);
		JsonArray arr = new JsonArray();
		arr.add(profile);
		arr.add(other);
		JsonObject resp = new JsonObject();
		resp.addProperty("success", true);
		resp.add("profiles", arr);
		return resp;
	}

	public static JsonObject hypixelPlayer() {
		JsonObject p = new JsonObject();
		p.addProperty("displayname", "Notch");
		p.addProperty("newPackageRank", "MVP_PLUS");
		p.addProperty("rankPlusColor", "GOLD");
		return p;
	}

	public static JsonObject obj(String k, Number v) {
		JsonObject o = new JsonObject();
		o.addProperty(k, v);
		return o;
	}

	public static JsonObject obj(String k, String v) {
		JsonObject o = new JsonObject();
		o.addProperty(k, v);
		return o;
	}

	public static JsonObject obj(String k, com.google.gson.JsonElement v) {
		JsonObject o = new JsonObject();
		o.add(k, v);
		return o;
	}
}
