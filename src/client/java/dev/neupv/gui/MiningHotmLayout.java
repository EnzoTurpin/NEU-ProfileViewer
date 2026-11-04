/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates hotm/HotmTreeLayout.kt, hotm/HotmTreeScreen.kt (HotmTreeRenderer: lisp evaluation and
 * perk tooltips) and hotm/ExtraLispMethods.kt, Copyright (C) 2023-2024 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import moe.nea.lisp.CoreBindings;
import moe.nea.lisp.LispAst;
import moe.nea.lisp.LispData;
import moe.nea.lisp.LispExecutionContext;
import moe.nea.lisp.LispParser;
import moe.nea.lisp.StackFrame;
import moe.nea.lisp.bind.AutoBinder;
import moe.nea.lisp.bind.LispBinding;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The HOTM tree described by NEU-REPO {@code constants/hotmlayout.json}: perk positions, and lore/cost/item computed by
 * nealisp programs. Pure logic (no rendering), so it can be tested outside of Minecraft.
 */
public final class MiningHotmLayout {
	private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z\\-A-Z_0-9]+)\\}");
	private static final Set<String> FIXED_KEYS = Set.of("name", "x", "y", "maxLevel", "powder", "cost", "lore");

	public record LoreLine(String text, LispAst.Program condition) {}

	public static final class Perk {
		public final String key, name;
		public final int x, y, maxLevel;
		final LispAst.Program powder;
		final List<LoreLine> lore;
		final Map<String, LispAst.Program> compiledFunctions = new LinkedHashMap<>();

		Perk(String key, JsonObject json) {
			this.key = key;
			this.name = json.get("name").getAsString();
			this.x = json.get("x").getAsInt();
			this.y = json.get("y").getAsInt();
			this.maxLevel = json.get("maxLevel").getAsInt();
			this.powder = parse("hotmlayout.json:perk:" + name + ":powder", json.get("powder").getAsString());
			this.lore = new ArrayList<>();
			for (JsonElement e : json.getAsJsonArray("lore")) {
				if (e.isJsonObject()) {
					JsonObject o = e.getAsJsonObject();
					lore.add(new LoreLine(o.get("text").getAsString(), parse("<json>", o.get("onlyIf").getAsString())));
				} else {
					lore.add(new LoreLine(e.getAsString(), null));
				}
			}
			for (Map.Entry<String, JsonElement> extra : json.entrySet()) {
				if (FIXED_KEYS.contains(extra.getKey())) continue;
				compiledFunctions.put(extra.getKey(), parse("hotmlayout.json:perk:" + name + ":" + extra.getKey(), extra.getValue().getAsString()));
			}
			compiledFunctions.put("cost", parse("hotmlayout.json:perk:" + name + ":cost", json.get("cost").getAsString()));
		}
	}

	/** Result of evaluating one perk at one level: its tooltip and the NEU repo id of its icon (null = unknown). */
	public record PerkState(List<String> tooltip, String itemId) {}

	public final Map<String, Perk> perks = new LinkedHashMap<>();
	private final Map<String, String> powderCostLines = new LinkedHashMap<>();
	private final Set<Long> gridNodes = new java.util.HashSet<>();
	public final int xSize, ySize;
	private final LispExecutionContext lec = new LispExecutionContext();

	/** @param file the whole hotmlayout.json ({prelude: [...], hotm: {perks, powders}}) */
	public MiningHotmLayout(JsonObject file) {
		JsonObject hotm = file.getAsJsonObject("hotm");
		for (Map.Entry<String, JsonElement> e : hotm.getAsJsonObject("powders").entrySet()) {
			powderCostLines.put(e.getKey(), e.getValue().getAsJsonObject().get("costLine").getAsString());
		}
		int maxX = 0, maxY = 0;
		for (Map.Entry<String, JsonElement> e : hotm.getAsJsonObject("perks").entrySet()) {
			Perk p = new Perk(e.getKey(), e.getValue().getAsJsonObject());
			perks.put(e.getKey(), p);
			gridNodes.add(gridKey(p.x, p.y));
			maxX = Math.max(maxX, p.x);
			maxY = Math.max(maxY, p.y);
		}
		xSize = maxX;
		ySize = maxY;

		lec.setupStandardBindings();
		new AutoBinder().bindTo(new ExtraLispMethods(), lec.getRootStackFrame());
		JsonArray prelude = file.getAsJsonArray("prelude");
		if (prelude != null) {
			for (int i = 0; i < prelude.size(); i++) {
				lec.executeProgram(lec.getRootStackFrame(), parse("hotmlayout.json:prelude[" + i + "]", prelude.get(i).getAsString()));
			}
		}
	}

	private static long gridKey(int x, int y) {
		return ((long) x << 32) | (y & 0xFFFFFFFFL);
	}

	public boolean hasNode(int x, int y) {
		return gridNodes.contains(gridKey(x, y));
	}

	static LispAst.Program parse(String name, String source) {
		return LispParser.Companion.parse(name, source);
	}

	private static int levelOf(Map<String, JsonElement> levels, String key) {
		JsonElement e = levels.get(key);
		if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber()) return 0;
		return e.getAsInt();
	}

	/** NEU HotmTreeRenderer.calculatePerkProperties + createPerkTooltip + getPerkItem. */
	public PerkState evaluate(Perk perk, Map<String, JsonElement> levels, double hotmLevel) {
		int level = levelOf(levels, perk.key);
		StackFrame bindings = lec.genBindings();
		bindings.setValueLocal("potm", new LispData.LispNumber(levelOf(levels, "special_0")));
		bindings.setValueLocal("hotm", new LispData.LispNumber(hotmLevel));
		bindings.setValueLocal("other-perk-level", LispData.Companion.externalCall("other-perk-level", (args, errorReporter) -> {
			if (args.size() != 1 || !(args.get(0) instanceof LispData.LispString s)) {
				return errorReporter.invoke("Need exactly one perk name to look up");
			}
			return new LispData.LispNumber(levelOf(levels, s.getString()));
		}));
		bindings.setValueLocal("level", new LispData.LispNumber(level == 0 ? 1 : level));
		bindings.setValueLocal("maxLevel", new LispData.LispNumber(perk.maxLevel));
		bindings.setValueLocal("level0", new LispData.LispNumber(level));
		Map<String, LispData> values = new LinkedHashMap<>();
		for (Map.Entry<String, LispAst.Program> f : perk.compiledFunctions.entrySet()) {
			values.put(f.getKey(), lec.executeProgram(bindings.fork(), f.getValue()));
		}
		return new PerkState(createTooltip(perk, level, values, bindings), itemId(values.get("item")));
	}

	private static String itemId(LispData data) {
		if (data instanceof LispData.Atom a) return a.getLabel();
		if (data instanceof LispData.LispString s) return s.getString();
		return null;
	}

	private List<String> createTooltip(Perk perk, int level, Map<String, LispData> values, StackFrame bindings) {
		String title = level == perk.maxLevel ? "§a" + perk.name : level == 0 ? "§c" + perk.name : "§e" + perk.name;
		List<String> lines = new ArrayList<>();
		lines.add(title);
		if (perk.maxLevel != 1) {
			lines.add(level != perk.maxLevel ? "§7Level " + level + "§8/" + perk.maxLevel : "§7Level " + level);
			lines.add("");
		}
		for (LoreLine line : perk.lore) {
			if (line.condition() != null) {
				LispData result = lec.executeProgram(bindings.fork(), line.condition());
				Boolean truthy = CoreBindings.INSTANCE.isTruthy(result == null ? LispData.LispNil.INSTANCE : result);
				if (truthy != null && !truthy) continue;
			}
			lines.add(line.text());
		}
		if (level != 0 && level != perk.maxLevel) {
			lines.add("");
			StackFrame powderFrame = bindings.fork();
			for (String powder : powderCostLines.keySet()) powderFrame.setValueLocal(powder, new LispData.LispString(powder));
			LispData powder = lec.executeProgram(powderFrame, perk.powder);
			String costLine = powder instanceof LispData.LispString s ? powderCostLines.get(s.getString()) : null;
			lines.add(costLine == null ? "<lisp-error>" : costLine);
		}
		List<String> out = new ArrayList<>(lines.size());
		for (String line : lines) out.add(substitute(line, values));
		return out;
	}

	private static String substitute(String line, Map<String, LispData> values) {
		Matcher m = PLACEHOLDER.matcher(line);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			String name = m.group(1);
			LispData value = values.get(name);
			String replacement;
			if (value instanceof LispData.LispString s) replacement = s.getString();
			else if (value instanceof LispData.LispNumber n) replacement = name.equals("cost") ? formatNumber((int) Math.floor(n.getValue())) : formatNumber(n.getValue());
			else replacement = "<lisp-error>";
			m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
		}
		m.appendTail(sb);
		return sb.toString();
	}

	/** NEU StringUtils.formatNumber: US grouping, up to 3 fraction digits. */
	public static String formatNumber(double n) {
		NumberFormat f = NumberFormat.getInstance(Locale.US);
		return f.format(n);
	}

	/** NEU ExtraLispMethods: math helpers used by hotmlayout.json. Must stay public for the reflective binder. */
	public static final class ExtraLispMethods {
		@LispBinding(name = "pow")
		public LispData.LispNumber powFunc(double base, double exponent) {
			return new LispData.LispNumber(Math.pow(base, exponent));
		}

		@LispBinding(name = "round")
		public LispData.LispNumber roundFunc(double value) {
			return new LispData.LispNumber(Math.rint(value));
		}

		@LispBinding(name = "ceil")
		public LispData.LispNumber ceilFunc(double value) {
			return new LispData.LispNumber(Math.ceil(value));
		}

		@LispBinding(name = "floor")
		public LispData.LispNumber floorFunc(double value) {
			return new LispData.LispNumber(Math.floor(value));
		}

		@LispBinding(name = "format-int")
		public LispData.LispString intToString(double base) {
			return new LispData.LispString(formatNumber((int) base));
		}
	}
}
