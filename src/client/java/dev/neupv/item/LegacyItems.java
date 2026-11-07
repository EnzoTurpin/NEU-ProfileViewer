/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import dev.neupv.NeuPv;
import dev.neupv.repo.NeuRepo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts Hypixel's 1.8.9 item NBT (numeric ids + damage values) into 26.3 item stacks.
 * The id table (assets/neupv/data/legacy_items.json) was generated from PrismarineJS minecraft-data 1.8 (MIT)
 * and checked against the 26.3 item list.
 */
public final class LegacyItems {
	private static final Pattern TEXTURE_VALUE = Pattern.compile("Value:\\\\?\"([A-Za-z0-9+/=]+)\\\\?\"");
	private static final Pattern COLOR = Pattern.compile("color:(\\d+)");
	public static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "SPECIAL", "VERY SPECIAL", "DIVINE"};
	private static final String[] RARITY_PREFIX = {"§f§l", "§a§l", "§9§l", "§5§l", "§6§l", "§d§l", "§c§l", "§c§l", "§b§l"};
	private static JsonObject table;

	record LegacyStackSpec(String legacyName, int damage, int count, String name, List<String> lore, String texture, Integer color, boolean glint, String internalName) {}

	private static final Pattern ITEM_MODEL = Pattern.compile("ItemModel:\\\\?\"([a-z0-9_.-]+:[a-z0-9_./-]+)\\\\?\"");

	private LegacyItems() {}

	private static JsonObject table() {
		if (table == null) {
			try (InputStream in = LegacyItems.class.getResourceAsStream("/assets/neupv/data/legacy_items.json")) {
				table = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (Exception e) {
				NeuPv.LOGGER.error("Missing legacy item table", e);
				table = new JsonObject();
			}
		}
		return table;
	}

	/** Decodes a base64 + gzip NBT inventory ("data" field of the API) into items; empty slots are null. */
	public static List<PvItem> decodeInventory(String base64) {
		if (base64 == null || base64.isEmpty()) return Collections.emptyList();
		try {
			CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(Base64.getDecoder().decode(base64)), NbtAccounter.unlimitedHeap());
			ListTag list = root.getListOrEmpty("i");
			List<PvItem> out = new ArrayList<>(list.size());
			for (int i = 0; i < list.size(); i++) {
				out.add(fromLegacyNbt(list.getCompoundOrEmpty(i)));
			}
			return out;
		} catch (Exception e) {
			NeuPv.LOGGER.warn("Could not decode an inventory", e);
			return Collections.emptyList();
		}
	}

	/** Same, for raw gzip bytes (backpacks' "item_contents"). */
	public static List<PvItem> decodeInventory(byte[] gzip) {
		return decodeInventory(Base64.getEncoder().encodeToString(gzip));
	}

	public static PvItem fromLegacyNbt(CompoundTag nbt) {
		if (nbt == null || nbt.isEmpty() || !nbt.contains("id")) return null;
		int numericId = nbt.getShortOr("id", (short) 0);
		String legacyName = J(table(), "byId", String.valueOf(numericId));
		if (legacyName == null) legacyName = nbt.getStringOr("id", "stone").replace("minecraft:", "");
		int damage = nbt.getShortOr("Damage", (short) 0);
		int count = nbt.getByteOr("Count", (byte) 1);
		CompoundTag tag = nbt.getCompoundOrEmpty("tag");
		CompoundTag display = tag.getCompoundOrEmpty("display");
		String name = display.getStringOr("Name", "");
		List<String> lore = new ArrayList<>();
		ListTag loreTag = display.getListOrEmpty("Lore");
		for (int i = 0; i < loreTag.size(); i++) lore.add(loreTag.getStringOr(i, ""));
		Integer color = display.contains("color") ? display.getIntOr("color", 0) : null;
		String texture = null;
		ListTag textures = tag.getCompoundOrEmpty("SkullOwner").getCompoundOrEmpty("Properties").getListOrEmpty("textures");
		if (!textures.isEmpty()) texture = textures.getCompoundOrEmpty(0).getStringOr("Value", null);
		boolean glint = !tag.getListOrEmpty("ench").isEmpty();
		String sbId = tag.getCompoundOrEmpty("ExtraAttributes").getStringOr("id", null);
		String internal = resolveInternalName(sbId, tag);
		LegacyStackSpec spec = new LegacyStackSpec(legacyName, damage, count, name, lore, texture, color, glint, internal);
		return new PvItem(internal, name, lore, count, tag, spec);
	}

	/** Builds an item from a NEU repo item json (items/ID.json), used for pet icons and similar. */
	public static PvItem fromRepo(String internalName) {
		JsonObject json = NeuRepo.item(internalName);
		if (json == null) return null;
		String itemId = dev.neupv.util.J.str(json, "itemid", "minecraft:stone").replace("minecraft:", "");
		int damage = dev.neupv.util.J.i(json, "damage", 0);
		String nbt = dev.neupv.util.J.str(json, "nbttag", "");
		String texture = null;
		Matcher m = TEXTURE_VALUE.matcher(nbt);
		if (m.find()) texture = m.group(1);
		Integer color = null;
		Matcher c = COLOR.matcher(nbt);
		if (c.find()) color = Integer.parseInt(c.group(1));
		List<String> lore = new ArrayList<>();
		JsonArray l = dev.neupv.util.J.arr(json, "lore");
		if (l != null) for (JsonElement e : l) lore.add(e.getAsString());
		String name = dev.neupv.util.J.str(json, "displayname", internalName);
		LegacyStackSpec spec = new LegacyStackSpec(itemId, damage, 1, name, lore, texture, color, nbt.contains("ench:["), internalName);
		return new PvItem(internalName, name, lore, 1, new CompoundTag(), spec);
	}

	/** Simple vanilla icon with a name, for tabs and skill icons. */
	public static ItemStack icon(Item item, String name) {
		return rename(new ItemStack(item), name);
	}

	/**
	 * Gives an item a display name that always wins: ITEM_NAME alone is ignored by player heads ("X's Head") and
	 * potions ("Uncraftable Potion"), so CUSTOM_NAME is set too, without the default italics.
	 */
	public static ItemStack rename(ItemStack s, String name) {
		Component c = Component.literal(name);
		s.set(DataComponents.ITEM_NAME, c);
		s.set(DataComponents.CUSTOM_NAME, c.copy().withStyle(st -> st.withItalic(false)));
		return s;
	}

	/** Player head with a base64 texture. */
	public static ItemStack skull(String name, String texture) {
		return build(new LegacyStackSpec("skull", 3, 1, name, List.of(), texture, null, false, null));
	}

	/**
	 * NEU internal names: pets become TYPE;RARITY_INDEX, enchanted books ENCH;LEVEL, runes RUNE;LEVEL; everything
	 * else is the ExtraAttributes id.
	 */
	static String resolveInternalName(String sbId, CompoundTag tag) {
		if (sbId == null) return null;
		CompoundTag ea = tag.getCompoundOrEmpty("ExtraAttributes");
		switch (sbId) {
			case "PET" -> {
				String petInfo = ea.getStringOr("petInfo", null);
				if (petInfo != null) {
					try {
						JsonObject pi = JsonParser.parseString(petInfo).getAsJsonObject();
						String tier = pi.get("tier").getAsString();
						for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equals(tier)) return pi.get("type").getAsString() + ";" + i;
					} catch (Exception ignored) {
					}
				}
			}
			case "ENCHANTED_BOOK" -> {
				CompoundTag ench = ea.getCompoundOrEmpty("enchantments");
				if (ench.keySet().size() == 1) {
					String e = ench.keySet().iterator().next();
					return e.toUpperCase() + ";" + ench.getIntOr(e, 1);
				}
			}
			case "RUNE", "UNIQUE_RUNE" -> {
				CompoundTag runes = ea.getCompoundOrEmpty("runes");
				if (runes.keySet().size() == 1) {
					String r = runes.keySet().iterator().next();
					return r.toUpperCase() + "_RUNE;" + runes.getIntOr(r, 1);
				}
			}
			default -> {
			}
		}
		return sbId.replace(":", "-");
	}

	public static int rarityFromLore(List<String> lore) {
		for (int i = lore.size() - 1; i >= 0; i--) {
			String line = lore.get(i);
			// Recombobulated items look like "§d§l§ka§r §d§lMYTHIC ACCESSORY §d§l§ka"
			int obf = line.indexOf("§r ");
			if (line.contains("§k") && obf >= 0) line = line.substring(obf + 3);
			for (int r = RARITIES.length - 1; r >= 0; r--) {
				if (line.startsWith(RARITY_PREFIX[r] + RARITIES[r])) return r;
			}
		}
		return -1;
	}

	static ItemStack build(LegacyStackSpec spec) {
		// Items whose repo entry became plain paper + a Hypixel resource-pack model (trophy fish, July 2026):
		// off Hypixel that model is missing, so fall back to the skull texture they had before.
		if (spec.internalName() != null && hypixelModel(spec.internalName()) == null) {
			JsonObject old = legacyLook(spec.internalName());
			if (old != null) {
				spec = new LegacyStackSpec(dev.neupv.util.J.str(old, "itemid", "stone"), dev.neupv.util.J.i(old, "damage", 0), spec.count(),
					spec.name(), spec.lore(), dev.neupv.util.J.str(old, "texture", null),
					old.has("color") ? old.get("color").getAsInt() : spec.color(), spec.glint() || dev.neupv.util.J.bool(old, "glint", false), null);
			}
		}
		Item item = Items.BARRIER;
		String modern = J(table(), "meta", spec.legacyName() + ":" + spec.damage());
		if (modern == null) modern = J(table(), "byName", spec.legacyName());
		if (modern == null) modern = spec.legacyName();
		Identifier id = Identifier.tryParse("minecraft:" + modern);
		if (id != null && BuiltInRegistries.ITEM.containsKey(id)) item = BuiltInRegistries.ITEM.getValue(id);
		ItemStack s = new ItemStack(item, Math.max(1, spec.count()));
		if (spec.name() != null && !spec.name().isEmpty()) {
			Component name = Component.literal(spec.name());
			s.set(DataComponents.ITEM_NAME, name);
			// Player heads take their name from the profile ("X's Head") unless a custom name is set.
			if (item == Items.PLAYER_HEAD) s.set(DataComponents.CUSTOM_NAME, name.copy().withStyle(st -> st.withItalic(false)));
		}
		if (spec.color() != null) s.set(DataComponents.DYED_COLOR, new DyedItemColor(spec.color()));
		if (spec.glint()) s.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		applyHypixelModel(s, spec.internalName());
		if (spec.texture() != null && item == Items.PLAYER_HEAD) {
			String texture = cleanBase64(spec.texture());
			PropertyMap props = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", texture)));
			UUID uuid = UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8));
			s.set(DataComponents.PROFILE, ResolvableProfile.createResolved(new GameProfile(uuid, "neupv", props)));
		}
		return s;
	}

	/**
	 * Hypixel now ships real item models in its server resource pack, and NEU-REPO items name them
	 * (ItemModel:"hypixel_skyblock:item/..."). When that model is loaded (i.e. on Hypixel), use it so icons look
	 * exactly like in game; otherwise keep the legacy vanilla fallback.
	 */
	private static void applyHypixelModel(ItemStack s, String internalName) {
		Identifier model = hypixelModel(internalName);
		if (model != null) s.set(DataComponents.ITEM_MODEL, model);
	}

	/** The repo's Hypixel item model for this item if it is currently loaded (i.e. Hypixel's pack is active), else null. */
	private static Identifier hypixelModel(String internalName) {
		if (internalName == null) return null;
		JsonObject repo = NeuRepo.item(internalName);
		if (repo == null) return null;
		Matcher m = ITEM_MODEL.matcher(dev.neupv.util.J.str(repo, "nbttag", ""));
		if (!m.find()) return null;
		Identifier model = Identifier.tryParse(m.group(1));
		if (model == null || model.getNamespace().equals("minecraft")) return null;
		try {
			var models = net.minecraft.client.Minecraft.getInstance().getModelManager();
			var missing = models.getItemModel(Identifier.fromNamespaceAndPath("neupv", "definitely_missing_model"));
			return models.getItemModel(model) != missing ? model : null;
		} catch (Exception e) {
			return null;
		}
	}

	private static JsonObject legacyLooks;

	/**
	 * Look (item id, damage, skull texture, colour) an item had in the NEU repo before it became paper + a Hypixel
	 * resource-pack model (assets/neupv/data/legacy_looks.json, ~840 items: trophy fish, equipment, abiphones...).
	 */
	private static JsonObject legacyLook(String internalName) {
		if (legacyLooks == null) {
			try (InputStream in = LegacyItems.class.getResourceAsStream("/assets/neupv/data/legacy_looks.json")) {
				legacyLooks = in == null ? new JsonObject()
					: JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("items");
			} catch (Exception e) {
				legacyLooks = new JsonObject();
			}
		}
		return legacyLooks.has(internalName) ? legacyLooks.getAsJsonObject(internalName) : null;
	}

	/** Some repo/API skull textures have broken base64 padding that makes authlib throw; normalise it. */
	static String cleanBase64(String b64) {
		String t = b64.replaceAll("[^A-Za-z0-9+/]", "");
		int pad = (4 - t.length() % 4) % 4;
		if (pad == 3) t = t.substring(0, t.length() - 1);
		else t = t + "=".repeat(pad);
		return t;
	}

	private static String J(JsonObject o, String section, String key) {
		JsonObject sec = o.getAsJsonObject(section);
		if (sec == null || !sec.has(key)) return null;
		return sec.get(key).getAsString();
	}
}
