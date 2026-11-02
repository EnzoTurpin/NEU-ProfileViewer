/*
 * Port of the NotEnoughUpdates profile viewer to Minecraft 26.3.
 * Based on NotEnoughUpdates, Copyright (C) 2022-2025 NotEnoughUpdates contributors, LGPL-3.0-or-later.
 */
package dev.neupv.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Number formatting, same output as NEU's StringUtils. */
public final class Fmt {
	private static final DecimalFormat GROUPED = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
	private static final String[] SUFFIXES = {"", "k", "M", "B", "T"};

	private Fmt() {}

	public static String number(double n) {
		synchronized (GROUPED) {
			return GROUPED.format(Math.round(n));
		}
	}

	public static String tenths(double n) {
		return String.format(Locale.US, "%.1f", n);
	}

	public static String shortNumber(double n) {
		if (Math.abs(n) < 1000) return String.valueOf((long) n);
		int idx = 0;
		double v = n;
		while (Math.abs(v) >= 1000 && idx < SUFFIXES.length - 1) {
			v /= 1000;
			idx++;
		}
		String s = v >= 100 ? String.valueOf((long) v) : String.format(Locale.US, v >= 10 ? "%.1f" : "%.2f", v);
		if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		return s + SUFFIXES[idx];
	}

	/** Replacement for ChatFormatting.getByName (removed in 26.3). */
	public static net.minecraft.ChatFormatting formatting(String name) {
		if (name == null) return null;
		for (net.minecraft.ChatFormatting f : net.minecraft.ChatFormatting.values()) {
			if (f.name().equalsIgnoreCase(name.trim())) return f;
		}
		return null;
	}

	public static String stripColor(String s) {
		return s == null ? null : s.replaceAll("(?i)§[0-9a-fk-orz]", "");
	}
}
