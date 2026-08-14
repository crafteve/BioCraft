package com.github.crafteve.biocraft.compat;

import com.github.crafteve.biocraft.reaction.KineticConstants;

import java.util.Locale;

/**
 * 配方显示层的共享格式化工具（JEI/EMI 两侧复用）
 * <p>
 * 数值格式化统一走本类，保证 JEI 与 EMI 显示的 Km/Keq/ΔG°′ 文本完全一致；
 * 化学常量（R/T₀/固定活性名单）复用引擎的 KineticConstants，不重复定义
 */
public final class CompatRenderUtil {
    private CompatRenderUtil() {
    }

    /**
     * 固定活性物种判定（H₂O/H⁺）
     * <p>
     * 委托引擎常量名单，显示层与引擎的"活性物种"语义保持同源
     *
     * @param itemId 物品注册名
     * @return true 表示固定活性物种（不进速率方程）
     */
    public static boolean isFixedActivity(String itemId) {
        return KineticConstants.FIXED_ACTIVITY_SPECIES.contains(itemId);
    }

    /**
     * 由 Keq 换算 ΔG°′（kJ/mol，298.15K）
     * <p>
     * ΔG°′ = −R·T₀·ln(Keq)，与引擎热力学同一基准（R=8.314 J/(mol·K)）
     *
     * @param keq 平衡常数（无量纲）
     * @return ΔG°′（kJ/mol）
     */
    public static double deltaGFromKeq(double keq) {
        return -KineticConstants.R * KineticConstants.T0 * Math.log(keq) / 1000.0;
    }

    /**
     * Keq 文本格式化：≥1000 或 <0.01 用科学计数（Unicode 上标），其余普通小数
     * <p>
     * 例：4800 → "4.8×10³"，0.3104 → "0.310"
     *
     * @param keq 平衡常数
     * @return 显示文本
     */
    public static String formatKeq(double keq) {
        if (keq >= 1000.0 || keq < 0.01) {
            int exp = (int) Math.floor(Math.log10(keq));
            double mantissa = keq / Math.pow(10, exp);
            return String.format(Locale.ROOT, "%.1f×10%d", mantissa, exp);
        }
        return String.format(Locale.ROOT, "%.3f", keq);
    }

    /**
     * Km 文本格式化：按量级取 1~3 位小数
     * <p>
     * 例：1.12 → "1.12"，0.046 → "0.046"，4.0 → "4.00"
     *
     * @param km 米氏常数（mM）
     * @return 显示文本
     */
    public static String formatKm(double km) {
        if (km >= 10.0) {
            return String.format(Locale.ROOT, "%.1f", km);
        }
        if (km >= 1.0) {
            return String.format(Locale.ROOT, "%.2f", km);
        }
        return String.format(Locale.ROOT, "%.3f", km);
    }

    /**
     * ΔG°′ 文本格式化（带符号一位小数）
     * <p>
     * 例：+2.9 / -21.0
     *
     * @param deltaG ΔG°′（kJ/mol）
     * @return 显示文本
     */
    public static String formatDeltaG(double deltaG) {
        return String.format(Locale.ROOT, "%+.1f", deltaG);
    }

    /**
     * kcat 文本格式化（一位小数）
     *
     * @param kcat 周转数（s⁻¹）
     * @return 显示文本
     */
    public static String formatKcat(double kcat) {
        return String.format(Locale.ROOT, "%.1f", kcat);
    }

    /**
     * 动力学变体文案的 lang key
     *
     * @param kinetic 动力学变体字符串（酶数据表 kinetic 字段）
     * @return 对应翻译键；未知变体回退限速酶文案
     */
    public static String kineticLangKey(String kinetic) {
        return switch (kinetic) {
            case "ISOMERASE" -> "jei.biocraft.kinetic.isomerase";
            case "OXIDO_LYASE" -> "jei.biocraft.kinetic.oxido_lyase";
            default -> "jei.biocraft.kinetic.limiting";
        };
    }
}
