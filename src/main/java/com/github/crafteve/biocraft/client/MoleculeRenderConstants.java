package com.github.crafteve.biocraft.client;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * 分子键线式渲染的共享常量（贴图生成管线的参数集中地）
 * <p>
 * 原散落在 MoleculeTextureCache 中，拆分绘制管线后集中到本类，
 * 供几何、键线、符号三个绘制辅助类共用，避免循环引用
 */
final class MoleculeRenderConstants {

    /** 超采样倍率：以 4x 分辨率绘制后线性缩小显示，细线平滑无锯齿、不错位 */
    static final int SUPERSAMPLE = 4;

    /** 目标最大高度（px，逻辑尺寸） */
    static final int TARGET_HEIGHT = 256;

    /** 目标最大宽度（px，逻辑尺寸） */
    static final int MAX_WIDTH = 512;

    /** 画布四周留白（px，逻辑尺寸） */
    static final int PADDING = 12;

    /** 目标平均键长（px，逻辑尺寸） */
    static final double BOND_LENGTH_PX = 16.0;

    /** 键线显示宽度（px，逻辑尺寸） */
    static final float BOND_STROKE_WIDTH = 0.8f;

    /** 双键平行线偏移距离（px，逻辑尺寸） */
    static final double DOUBLE_BOND_OFFSET = 1.4;

    /** 三键副键偏移距离（px，逻辑尺寸） */
    static final double TRIPLE_BOND_OFFSET = 2.6;

    /** 符号高度与键长的比例 */
    static final double SYMBOL_RATIO = 0.45;

    /** 符号深色底块与文字边缘的留白（px，逻辑尺寸） */
    static final double SYMBOL_BG_PADDING = 1.0;

    /** 重原子数上限，超过则判定为过于复杂的分子，不生成结构图 */
    static final int MAX_HEAVY_ATOMS = 150;

    /** 键线颜色（亮白，深色 tooltip 背景上清晰） */
    static final Color BOND_COLOR = new Color(0xE0, 0xE0, 0xE0);

    /** 符号深色底块颜色（不透明，接近 tooltip 深色背景，用于截断键线） */
    static final Color SYMBOL_BG_COLOR = new Color(0x10, 0x10, 0x18);

    /** 杂原子 MC 风格色板：元素符号 -> ARGB 颜色 */
    static final Map<String, Integer> ATOM_COLORS = new HashMap<>();

    static {
        ATOM_COLORS.put("O", 0xFFFF5555);
        ATOM_COLORS.put("N", 0xFF5555FF);
        ATOM_COLORS.put("P", 0xFFFFAA00);
        ATOM_COLORS.put("S", 0xFFFFFF55);
        ATOM_COLORS.put("Cl", 0xFF55FF55);
        ATOM_COLORS.put("Na", 0xFFFFD700);
        ATOM_COLORS.put("K", 0xFFAA66FF);
        ATOM_COLORS.put("Fe", 0xFFFF8C42);
        ATOM_COLORS.put("Mg", 0xFF7CFC00);
        ATOM_COLORS.put("Ca", 0xFFE6E6E6);
        ATOM_COLORS.put("B", 0xFFE6E6E6);
        ATOM_COLORS.put("I", 0xFFAA66CC);
    }

    private MoleculeRenderConstants() {
    }
}
