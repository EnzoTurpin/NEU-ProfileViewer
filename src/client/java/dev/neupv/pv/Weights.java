/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates profileviewer/weight (Senither + Lily), Copyright (C) 2022-2025 NotEnoughUpdates contributors,
 * LGPL-3.0-or-later. Formulas unchanged.
 */
package dev.neupv.pv;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.neupv.repo.NeuRepo;
import dev.neupv.util.J;

import java.util.List;
import java.util.Map;

public final class Weights {
	public static final List<String> SKILL_NAMES = List.of("taming", "mining", "foraging", "enchanting", "farming", "combat", "fishing", "alchemy");
	public static final List<String> SLAYER_NAMES = List.of("wolf", "zombie", "spider", "enderman", "blaze", "vampire");
	public static final List<String> DUNGEON_CLASS_NAMES = List.of("healer", "mage", "berserk", "archer", "tank");
	public static final long SKILLS_LEVEL_50 = 55172425;
	public static final long SKILLS_LEVEL_60 = 111672425;
	public static final long CATACOMBS_LEVEL_50_XP = 569809640;

	private Weights() {}

	/** base + overflow, as NEU's WeightStruct. */
	public static final class W {
		public double base, overflow;

		W add(double b, double o) {
			base += b;
			overflow += o;
			return this;
		}

		public double raw() {
			return base + overflow;
		}
	}

	public record Result(W skills, W slayer, W dungeons) {
		public double total() {
			return skills.raw() + slayer.raw() + dungeons.raw();
		}
	}

	/** Returns null if weight.json is unavailable (it is embedded in the mod, so this should not happen). */
	public static Result senither(Map<String, Level> p) {
		JsonObject w = NeuRepo.weight();
		JsonObject leveling = NeuRepo.leveling();
		if (w == null || leveling == null) return null;
		W slayer = new W(), skills = new W(), dungeons = new W();
		for (String s : SLAYER_NAMES) {
			if (s.equals("blaze") || s.equals("vampire")) continue;
			JsonArray cur = J.arr(w, "senither.slayer." + s);
			double divider = cur.get(0).getAsDouble();
			double modifier = cur.get(1).getAsDouble();
			int xp = (int) p.get(s).totalXp;
			if (xp <= 1000000) {
				slayer.add(xp == 0 ? 0 : xp / divider, 0);
				continue;
			}
			double base = 1000000 / divider;
			double remaining = xp - 1000000;
			double overflow = 0;
			double initial = modifier;
			while (remaining > 0) {
				double left = Math.min(remaining, 1000000);
				overflow += Math.pow(left / (divider * (1.5 + modifier)), 0.942);
				modifier += initial;
				remaining -= left;
			}
			slayer.add(base, overflow);
		}
		for (String s : SKILL_NAMES) {
			JsonArray cur = J.arr(w, "senither.skills." + s);
			double exponent = cur.get(0).getAsDouble();
			double divider = cur.get(1).getAsDouble();
			float xp = p.get(s).totalXp;
			if (xp <= 0) continue;
			int maxLevel = s.equals("farming") ? 60 : J.i(leveling, "leveling_caps." + s, 50);
			double level = Level.of(J.arr(leveling, "leveling_xp"), xp, maxLevel, false).level;
			double maxLevelExp = maxLevel == 50 ? SKILLS_LEVEL_50 : SKILLS_LEVEL_60;
			double base = Math.pow(level * 10, 0.5 + exponent + (level / 100)) / 1250;
			if (xp <= maxLevelExp) skills.add(base, 0);
			else skills.add(Math.round(base), Math.pow((xp - maxLevelExp) / divider, 0.968));
		}
		senitherDungeon(dungeons, p.get("catacombs"), J.dbl(w, "senither.dungeons.catacombs", 0));
		for (String c : DUNGEON_CLASS_NAMES) senitherDungeon(dungeons, p.get(c), J.dbl(w, "senither.dungeons.classes." + c, 0));
		return new Result(skills, slayer, dungeons);
	}

	private static void senitherDungeon(W out, Level l, double factor) {
		double base = Math.pow(l.level, 4.5) * factor;
		if (l.totalXp <= CATACOMBS_LEVEL_50_XP) {
			out.add(base, 0);
			return;
		}
		double remaining = l.totalXp - CATACOMBS_LEVEL_50_XP;
		double splitter = (4 * CATACOMBS_LEVEL_50_XP) / base;
		out.add(Math.floor(base), Math.pow(remaining / splitter, 0.968));
	}

	public static Result lily(Map<String, Level> p, JsonObject profileJson) {
		JsonObject w = NeuRepo.weight();
		JsonObject leveling = NeuRepo.leveling();
		if (w == null || leveling == null) return null;
		W slayer = new W(), skills = new W(), dungeons = new W();
		for (String s : SLAYER_NAMES) lilySlayer(slayer, s, (int) p.get(s).totalXp, J.dbl(w, "lily.slayer.deprecation_scaling." + s, 0));
		JsonArray levelingXp = J.arr(leveling, "leveling_xp");
		double skillAverage = 0;
		for (String s : SKILL_NAMES) skillAverage += (int) Level.of(levelingXp, p.get(s).totalXp, 60, false).level;
		skillAverage /= SKILL_NAMES.size();
		double overall = J.dbl(w, "lily.skills.overall", 0);
		for (String s : SKILL_NAMES) {
			float xp = p.get(s).totalXp;
			int lvl = (int) Level.of(levelingXp, xp, 60, false).level;
			JsonArray srw = J.arr(w, "lily.skills.ratio_weight." + s);
			double last = srw.get(srw.size() - 1).getAsDouble();
			double base = ((12 * Math.pow((skillAverage / 60), 2.44780217148309)) * srw.get(lvl).getAsDouble() * last)
				+ (last * Math.pow(lvl / 60.0, Math.pow(2, 0.5)));
			base *= overall;
			double overflow = 0;
			if (xp > SKILLS_LEVEL_60) {
				double factor = J.dbl(w, "lily.skills.factors." + s, 0);
				double t = (Math.pow(xp - SKILLS_LEVEL_60, factor) / SKILLS_LEVEL_60) * J.dbl(w, "lily.skills.overflow_multipliers." + s, 0);
				if (t > 0) overflow += overall * t;
			}
			skills.add(base, overflow);
		}
		Level cata = p.get("catacombs");
		double extra = 0, n = 0;
		if (cata.totalXp < CATACOMBS_LEVEL_50_XP) n = 0.2 * Math.pow(cata.level / 50, 1.538679118869934);
		else extra = 500.0 * Math.pow((cata.totalXp - CATACOMBS_LEVEL_50_XP) / 142452410.0, 1.0 / 1.781925776625157);
		if (cata.level != 0) {
			if (cata.totalXp < CATACOMBS_LEVEL_50_XP) {
				dungeons.add(J.dbl(w, "lily.dungeons.overall", 0) * ((Math.pow(1.18340401286164044, (cata.level + 1)) - 1.05994990217254) * (1 + n)), 0);
			} else {
				dungeons.add((4100 + extra) * 2, 0);
			}
		}
		lilyCompletion(dungeons, w, profileJson, "normal");
		lilyCompletion(dungeons, w, profileJson, "master");
		return new Result(skills, slayer, dungeons);
	}

	private static void lilySlayer(W out, String name, int xp, double scaleFactor) {
		double score;
		double d = xp / 100000.0;
		if (xp >= 6416) {
			double D = (d - Math.pow(3, (-5.0 / 2))) * (d + Math.pow(3, -5.0 / 2));
			double u = Math.cbrt(3 * (d + Math.sqrt(D)));
			double v = Math.cbrt(3 * (d - Math.sqrt(D)));
			score = u + v - 1;
		} else {
			score = Math.sqrt(4.0 / 3) * Math.cos(Math.acos(d * Math.pow(3, 5.0 / 2)) / 3) - 1;
		}
		int intScore = (int) score;
		double distance = xp - (((Math.pow(intScore, 3) / 6) + (Math.pow(intScore, 2) / 2) + (intScore / 3.0)) * 100000);
		double effectiveDistance = distance * Math.pow(scaleFactor, intScore);
		double total = 0;
		for (int k = 0; k < intScore; k++) total += (Math.pow((k + 1), 2) + (k + 1)) * Math.pow(scaleFactor, (k + 1));
		double effectiveScore = 1000000 * total * (0.05 / scaleFactor) + effectiveDistance;
		double weight;
		switch (name) {
			case "zombie" -> weight = (effectiveScore / 9250) + (xp / 1000000.0);
			case "spider" -> weight = (effectiveScore / 7019.57) + ((xp * 1.6) / 1000000);
			case "wolf" -> weight = (effectiveScore / 2982.06) + ((xp * 3.6) / 1000000);
			case "enderman" -> weight = (effectiveScore / 996.3003) + ((xp * 10.0) / 1000000);
			case "blaze" -> weight = (effectiveScore / 935.0455) + ((xp * 10.0) / 1000000);
			default -> {
				return;
			}
		}
		out.add(2 * weight, 0);
	}

	private static void lilyCompletion(W out, JsonObject w, JsonObject profileJson, String mode) {
		JsonObject worth = J.obj(w, "lily.dungeons.completion_worth");
		JsonObject buffs = J.obj(w, "lily.dungeons.completion_buffs");
		if (worth == null || buffs == null) return;
		double max1000 = 0, mMax1000 = 0;
		for (Map.Entry<String, JsonElement> e : worth.entrySet()) {
			if (e.getKey().startsWith("catacombs_")) max1000 += e.getValue().getAsDouble();
			else mMax1000 += e.getValue().getAsDouble();
		}
		max1000 *= 1000;
		mMax1000 *= 1000;
		double upperBound = 1500;
		double score = 0;
		if (mode.equals("normal")) {
			JsonObject tc = J.obj(profileJson, "dungeons.dungeon_types.catacombs.tier_completions");
			if (tc == null) return;
			for (Map.Entry<String, JsonElement> f : tc.entrySet()) {
				if (!worth.has("catacombs_" + f.getKey())) continue;
				int amount = f.getValue().getAsInt();
				double excess = 0;
				if (amount > 1000) {
					excess = amount - 1000;
					amount = 1000;
				}
				double floorScore = amount * worth.get("catacombs_" + f.getKey()).getAsDouble();
				if (excess > 0) floorScore *= Math.log(excess / 1000 + 1) / Math.log(7.5) + 1;
				score += floorScore;
			}
			out.add(score / max1000 * upperBound * 2, 0);
		} else {
			JsonObject tc = J.obj(profileJson, "dungeons.dungeon_types.master_catacombs.tier_completions");
			if (tc == null) return;
			for (Map.Entry<String, JsonElement> f : tc.entrySet()) {
				if (buffs.has(f.getKey())) {
					int amount = f.getValue().getAsInt();
					double threshold = 20;
					if (amount >= threshold) upperBound += buffs.get(f.getKey()).getAsInt();
					else upperBound += buffs.get(f.getKey()).getAsInt() * Math.pow((amount / threshold), 1.840896416);
				}
				if (worth.has("master_catacombs_" + f.getKey())) {
					int amount = f.getValue().getAsInt();
					double excess = 0;
					if (amount > 1000) {
						excess = amount - 1000;
						amount = 1000;
					}
					double floorScore = amount * worth.get("master_catacombs_" + f.getKey()).getAsDouble();
					if (excess > 0) floorScore *= (Math.log((excess / 1000) + 1) / Math.log(6)) + 1;
					score += floorScore;
				}
			}
			out.add((score / mMax1000) * upperBound * 2, 0);
		}
	}
}
