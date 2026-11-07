/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Storage tab: Hypixel's loadout data (wardrobe armor sets, equipment sets, loadouts). Not in NEU 2.6.0.
 */
package dev.neupv.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.item.LegacyItems;
import dev.neupv.item.PvItem;
import dev.neupv.util.Fmt;
import dev.neupv.util.J;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Hypixel moved the wardrobe from {@code inventory.wardrobe_contents} to {@code loadout.armor}:
 * {@code {"1": {"id": 1, "HELMET": {"type": 0, "data": "<base64 gzip NBT>"}, ...}, ..., "equipped_set": 9}}. Each piece
 * is a one-item inventory. {@code loadout.equipment} has the same shape with EQUIPMENT_SLOT_1..4 (necklace, cloak,
 * belt, gloves) and {@code loadout.loadouts} links an armor set, an equipment set, a pet (pets_data uniqueId), a power
 * stone and a tuning template.
 */
final class InventoriesLoadouts {
	static final String[] ARMOR_SLOTS = {"HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"};
	static final String[] EQUIPMENT_SLOTS = {"EQUIPMENT_SLOT_1", "EQUIPMENT_SLOT_2", "EQUIPMENT_SLOT_3", "EQUIPMENT_SLOT_4"};
	private static final String[] ARMOR_NAMES = {"Helmet", "Chestplate", "Leggings", "Boots"};
	private static final String[] EQUIPMENT_NAMES = {"Necklace", "Cloak", "Belt", "Gloves"};

	/** Loadouts drawn per page (each: a 10px label strip + a 9-slot row). */
	static final int PER_PAGE = 5;
	private static final int LABEL_H = 10, ROW_H = 18, ENTRY_H = LABEL_H + ROW_H;

	record Loadout(int id, String name, int armorSetId, int equipmentSetId, String powerStone, int tuningSlot, String petUuid,
				   int miningSlot, int foragingSlot) {}

	/** Armor sets by wardrobe slot (1-based), pieces helmet..boots (null = empty). */
	final TreeMap<Integer, PvItem[]> armorSets = new TreeMap<>();
	final TreeMap<Integer, PvItem[]> equipmentSets = new TreeMap<>();
	final List<Loadout> loadouts = new ArrayList<>();
	final int equippedArmorSet, equippedEquipmentSet;
	private final JsonObject member;
	private final Map<String, PetsData.PetDisplay> pets = new HashMap<>();

	private InventoriesLoadouts(JsonObject member) {
		this.member = member;
		JsonObject armor = J.objOrEmpty(member, "loadout.armor");
		readSets(armor, ARMOR_SLOTS, armorSets);
		JsonObject equipment = J.objOrEmpty(member, "loadout.equipment");
		readSets(equipment, EQUIPMENT_SLOTS, equipmentSets);
		equippedArmorSet = J.i(member, "inventory.wardrobe_equipped_slot", J.i(armor, "equipped_set", -1));
		equippedEquipmentSet = J.i(equipment, "equipped_set", -1);
		for (Map.Entry<String, JsonElement> e : J.objOrEmpty(member, "loadout.loadouts").entrySet()) {
			if (!e.getValue().isJsonObject()) continue;
			JsonObject l = e.getValue().getAsJsonObject();
			int id = J.i(l, "id", parseInt(e.getKey()));
			loadouts.add(new Loadout(id, J.str(l, "name", "Loadout " + id), J.i(l, "armor_set_id", -1),
				J.i(l, "equipment_set_id", -1), J.str(l, "power_stone", null), J.i(l, "tuning_points_slot", -1),
				J.str(l, "pet", null), J.i(l, "mining_core_selected_slot", -1), J.i(l, "foraging_core_selected_slot", -1)));
		}
		loadouts.sort(Comparator.comparingInt(Loadout::id));
	}

	/** Null when Hypixel sent no loadout object (older data: the wardrobe is then in inventory.wardrobe_contents). */
	static InventoriesLoadouts of(JsonObject member) {
		if (J.obj(member, "loadout") == null) return null;
		return new InventoriesLoadouts(member);
	}

	/**
	 * Hypixel leaves the equipped armor / equipment set empty (it is worn): show what the player wears instead, like
	 * the in-game wardrobe. invArmor is boots first, equipment necklace first.
	 */
	void fillEquipped(List<PvItem> invArmor, List<PvItem> equipmentContents) {
		fill(armorSets, equippedArmorSet, invArmor, true);
		fill(equipmentSets, equippedEquipmentSet, equipmentContents, false);
	}

	private static void fill(TreeMap<Integer, PvItem[]> sets, int id, List<PvItem> worn, boolean reversed) {
		if (id <= 0 || worn == null || worn.isEmpty()) return;
		PvItem[] set = sets.computeIfAbsent(id, k -> new PvItem[4]);
		for (PvItem p : set) if (p != null) return;
		for (int i = 0; i < 4; i++) {
			int src = reversed ? 3 - i : i;
			if (src < worn.size()) set[i] = worn.get(src);
		}
	}

	boolean hasWardrobe() {
		return !armorSets.isEmpty();
	}

	private static void readSets(JsonObject sets, String[] slots, TreeMap<Integer, PvItem[]> out) {
		for (Map.Entry<String, JsonElement> e : sets.entrySet()) {
			int id = parseInt(e.getKey());
			if (id <= 0 || !e.getValue().isJsonObject()) continue; // "equipped_set" is a number
			PvItem[] pieces = new PvItem[slots.length];
			for (int i = 0; i < slots.length; i++) {
				List<PvItem> items = LegacyItems.decodeInventory(J.str(e.getValue(), slots[i] + ".data", null));
				for (PvItem item : items) {
					if (item != null) {
						pieces[i] = item;
						break;
					}
				}
			}
			out.put(id, pieces);
		}
	}

	private static int parseInt(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	int pages() {
		return Math.max(1, (loadouts.size() + PER_PAGE - 1) / PER_PAGE);
	}

	/** The pet of a loadout, matched on pets_data.pets[].uniqueId (or uuid). */
	PetsData.PetDisplay pet(String uuid) {
		if (uuid == null) return null;
		if (pets.containsKey(uuid)) return pets.get(uuid);
		PetsData.PetDisplay found = null;
		for (JsonElement p : J.arrOrEmpty(member, "pets_data.pets")) {
			if (uuid.equals(J.str(p, "uniqueId", null)) || uuid.equals(J.str(p, "uuid", null))) {
				found = PetsData.create(p.getAsJsonObject());
				break;
			}
		}
		pets.put(uuid, found);
		return found;
	}

	static String powerStoneName(String id) {
		if (id == null || id.isEmpty()) return null;
		StringBuilder sb = new StringBuilder();
		for (String w : id.split("_")) {
			if (w.isEmpty()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(w.substring(0, 1).toUpperCase(Locale.ROOT)).append(w.substring(1).toLowerCase(Locale.ROOT));
		}
		return sb.toString();
	}

	/** Height of the loadouts panel for n entries (chest top 17 + entries + bottom 7). */
	static int panelHeight(int n) {
		return 17 + Math.max(1, n) * ENTRY_H + 7;
	}

	/**
	 * Draws one page of loadouts in a chest-like panel: per loadout a label strip (name, power stone) and one slot row
	 * (4 armor pieces, 4 equipment pieces, pet).
	 */
	void draw(ProfileViewerScreen screen, GuiGraphicsExtractor g, int x, int y, int page, int mouseX, int mouseY) {
		int from = page * PER_PAGE, to = Math.min(loadouts.size(), from + PER_PAGE);
		int n = Math.max(1, to - from);
		// PV-style panel: title bar, then per entry a label strip and a slot row (9 slots: 4 armor, 4 equipment, pet)
		InventoriesUi.pvPanel(g, x, y, 176, panelHeight(n));
		if (!loadouts.isEmpty()) {
			for (int i = 0; i < n; i++) InventoriesUi.pvSlotRow(g, x + 7, y + 17 + i * ENTRY_H + LABEL_H, 9);
		}
		g.text(Draw.font(), "Loadouts", x + 8, y + 6, 0xFFFFFFFF, true);
		String pageStr = (page + 1) + "/" + pages();
		g.text(Draw.font(), pageStr, x + 168 - Draw.font().width(pageStr), y + 6, 0xFFFFFFFF, true);

		if (loadouts.isEmpty()) {
			g.text(Draw.font(), "No loadouts", x + 8, y + 17 + 1, 0xFFAAAAAA, true);
			return;
		}
		for (int i = from; i < to; i++) {
			Loadout l = loadouts.get(i);
			int ey = y + 17 + (i - from) * ENTRY_H;
			String stone = powerStoneName(l.powerStone());
			int stoneW = stone == null ? 0 : Draw.font().width(stone);
			String name = Fmt.stripColor(l.name());
			int maxNameW = 160 - stoneW - (stone == null ? 0 : 6);
			if (Draw.font().width(name) > maxNameW) name = Draw.font().plainSubstrByWidth(name, maxNameW - 6) + "...";
			g.text(Draw.font(), name, x + 8, ey + 1, 0xFFFFFF55, true);
			if (stone != null) g.text(Draw.font(), stone, x + 168 - stoneW, ey + 1, 0xFFFF55FF, true);
			if (mouseX >= x + 7 && mouseX < x + 169 && mouseY >= ey && mouseY < ey + LABEL_H) {
				screen.tooltipToDisplay = tooltip(l);
			}

			int sy = ey + LABEL_H + 1;
			PvItem[] armor = armorSets.get(l.armorSetId());
			PvItem[] equipment = equipmentSets.get(l.equipmentSetId());
			for (int s = 0; s < 8; s++) {
				PvItem item = s < 4 ? (armor == null ? null : armor[s]) : (equipment == null ? null : equipment[s - 4]);
				int sx = x + 8 + s * 18;
				if (item == null) continue;
				g.item(item.stack(), sx, sy);
				g.itemDecorations(Draw.font(), item.stack(), sx, sy);
				if (mouseX >= sx && mouseX <= sx + 16 && mouseY >= sy && mouseY <= sy + 16) {
					List<Component> tt = new ArrayList<>(PvWidgets.itemTooltip(item));
					tt.add(Component.empty());
					tt.add(Component.literal(s < 4 ? "§8Wardrobe slot " + l.armorSetId() : "§8Equipment set " + l.equipmentSetId()));
					screen.tooltipToDisplay = tt;
				}
			}
			PetsData.PetDisplay pet = pet(l.petUuid());
			int px = x + 8 + 8 * 18;
			if (pet != null) {
				g.item(pet.stack(), px, sy);
				if (mouseX >= px && mouseX <= px + 16 && mouseY >= sy && mouseY <= sy + 16) screen.tooltipToDisplay = pet.tooltip();
			}
		}
	}

	private List<Component> tooltip(Loadout l) {
		List<Component> tt = new ArrayList<>();
		tt.add(Component.literal("§a" + l.name()));
		tt.add(Component.literal("§8Loadout #" + l.id()));
		tt.add(Component.empty());
		tt.add(Component.literal("§7Armor: " + setLine(armorSets.get(l.armorSetId()), "Wardrobe slot " + l.armorSetId(), l.armorSetId())));
		tt.add(Component.literal("§7Equipment: " + setLine(equipmentSets.get(l.equipmentSetId()), "Set " + l.equipmentSetId(), l.equipmentSetId())));
		PetsData.PetDisplay pet = pet(l.petUuid());
		tt.add(Component.literal("§7Pet: " + (pet != null ? pet.name() : l.petUuid() == null ? "§8None" : "§cNot found")));
		String stone = powerStoneName(l.powerStone());
		tt.add(Component.literal("§7Power: " + (stone == null ? "§8None" : "§a" + stone)));
		if (l.miningSlot() >= 0) tt.add(Component.literal("§7HOTM Loadout: §e#" + l.miningSlot()));
		if (l.foragingSlot() >= 0) tt.add(Component.literal("§7HOTF Loadout: §e#" + l.foragingSlot()));
		if (l.tuningSlot() >= 0) {
			tt.add(Component.empty());
			// accessory_bag_storage.tuning.slot_<tuning_points_slot> (Hypixel does not say how the menu numbers them)
			tt.add(Component.literal("§7Tuning: §8(slot_" + l.tuningSlot() + ")"));
			JsonObject slot = J.obj(member, "accessory_bag_storage.tuning.slot_" + l.tuningSlot());
			DecimalFormat df = new DecimalFormat("#.#", DecimalFormatSymbols.getInstance(Locale.US));
			boolean any = false;
			if (slot != null) {
				for (Map.Entry<String, String> stat : InventoriesPage.API_STAT_NAMES.entrySet()) {
					int points = J.i(slot, stat.getKey(), 0);
					if (points == 0) continue;
					any = true;
					tt.add(Component.literal("  " + stat.getValue() + ": +"
						+ df.format(points * InventoriesPage.TUNING_COEFFICIENTS.getOrDefault(stat.getKey(), 1f))
						+ "§8 (§e" + points + "§8 points)"));
				}
			}
			if (!any) tt.add(Component.literal("  §8No points"));
		}
		if (equipmentSets.containsKey(l.equipmentSetId()) || armorSets.containsKey(l.armorSetId())) {
			tt.add(Component.empty());
			PvItem[] armor = armorSets.get(l.armorSetId());
			PvItem[] equipment = equipmentSets.get(l.equipmentSetId());
			for (int i = 0; i < 4; i++) tt.add(Component.literal("§7" + ARMOR_NAMES[i] + ": " + pieceName(armor, i)));
			for (int i = 0; i < 4; i++) tt.add(Component.literal("§7" + EQUIPMENT_NAMES[i] + ": " + pieceName(equipment, i)));
		}
		return tt;
	}

	private static String pieceName(PvItem[] set, int i) {
		return set == null || set[i] == null ? "§8Empty" : set[i].displayName;
	}

	private static String setLine(PvItem[] set, String label, int id) {
		if (id < 0) return "§8None";
		if (set == null) return "§c" + label + " (missing)";
		int count = 0;
		for (PvItem p : set) if (p != null) count++;
		return "§e" + label + " §8(" + count + "/4 pieces)";
	}
}
