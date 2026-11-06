/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Not in NEU: remembers Hypixel's own "Accessory Bag" / "Potion Bag" menu items so the Storage tab can use them.
 */
package dev.neupv.item;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.neupv.NeuPv;
import dev.neupv.util.Fmt;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Hypixel builds these items from its own resource pack (player heads / custom models) and they are not in the
 * NEU repo. Whenever a container screen shows one (e.g. the SkyBlock Menu), it is copied and saved to
 * config/neupv/bag_icons.json; the Storage tab then shows exactly the item Hypixel uses.
 */
public final class BagIcons {
	private static final Map<String, String> NAME_TO_KEY = Map.of("Accessory Bag", "talisman_bag", "Potion Bag", "potion_bag");
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("neupv").resolve("bag_icons.json");
	private static final Map<String, ItemStack> ICONS = new HashMap<>();
	private static JsonObject saved;
	private static int tick;

	private BagIcons() {}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof AbstractContainerScreen<?> containerScreen) {
				ScreenEvents.afterTick(screen).register(s -> {
					if (++tick % 10 == 0) scan(containerScreen);
				});
			}
		});
	}

	private static void scan(AbstractContainerScreen<?> screen) {
		for (Slot slot : screen.getMenu().slots) {
			ItemStack stack = slot.getItem();
			if (stack.isEmpty()) continue;
			String key = NAME_TO_KEY.get(Fmt.stripColor(stack.getHoverName().getString()).trim());
			if (key == null) continue;
			ItemStack known = ICONS.get(key);
			if (known != null && ItemStack.isSameItemSameComponents(known, stack)) continue;
			ItemStack copy = stack.copyWithCount(1);
			ICONS.put(key, copy);
			save(key, copy);
		}
	}

	/** Hypixel's item for this inventory if it was seen once, else the given fallback. */
	public static ItemStack orDefault(String key, ItemStack fallback) {
		ItemStack s = ICONS.get(key);
		if (s == null) s = load(key);
		return s != null ? s : fallback;
	}

	private static HolderLookup.Provider registries() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null ? mc.level.registryAccess() : null;
	}

	private static void save(String key, ItemStack stack) {
		HolderLookup.Provider reg = registries();
		if (reg == null) return;
		try {
			JsonElement encoded = ItemStack.CODEC.encodeStart(reg.createSerializationContext(JsonOps.INSTANCE), stack).getOrThrow();
			JsonObject all = readFile();
			all.add(key, encoded);
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, all.toString(), StandardCharsets.UTF_8);
			saved = all;
			NeuPv.LOGGER.info("Saved Hypixel icon for {}", key);
		} catch (Exception e) {
			NeuPv.LOGGER.warn("Could not save the {} icon", key, e);
		}
	}

	private static ItemStack load(String key) {
		HolderLookup.Provider reg = registries();
		if (reg == null) return null;
		JsonObject all = readFile();
		if (!all.has(key)) return null;
		try {
			ItemStack s = ItemStack.CODEC.parse(reg.createSerializationContext(JsonOps.INSTANCE), all.get(key)).getOrThrow();
			ICONS.put(key, s);
			return s;
		} catch (Exception e) {
			return null;
		}
	}

	private static JsonObject readFile() {
		if (saved != null) return saved;
		try {
			saved = Files.exists(FILE) ? JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8)).getAsJsonObject() : new JsonObject();
		} catch (Exception e) {
			saved = new JsonObject();
		}
		return saved;
	}
}
