package com.github.crafteve.biocraft.compat.jei;

import com.github.crafteve.biocraft.compat.CompatRenderUtil;
import com.github.crafteve.biocraft.compat.EnzymeRecipeDisplay;
import com.github.crafteve.biocraft.compat.EnzymeRecipeDisplay.Entry;
import com.github.crafteve.biocraft.init.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 酶工厂配方类别（JEI 显示层核心）
 * <p>
 * 布局规格（完整硬核版信息卡，像素单位）：
 * <pre>
 *   [槽1] [槽2]   ⇌/→    [槽1] [槽2]      槽 18x18、槽距 20、槽 y=2
 *   Km=    Km=    Keq=    Km=    Km=       Km 文本 y=22（槽正下方）
 *   ──────────────────────────────────
 *   [PGI] EC5 · 异构酶                     信息条行 1 y=34
 *   kcat = 79.0 s⁻¹ · T = 298 K           信息条行 2 y=44
 *   ΔG°′ = +2.9 kJ/mol · 激活剂: —        信息条行 3 y=54
 * </pre>
 * 输入槽从 x=2 起排布，箭头区固定在输入末槽右侧（arrowX = 2 + nIn*20 + 8），
 * 输出槽在箭头右侧（outputX = arrowX + 24）；卡宽固定 154（容纳最多 3 输入 + 3 输出），
 * 输入数量少时右侧留白，布局保持整齐；信息区按行分散（行内不堆叠，长度充裕）
 * <p>
 * 槽位使用 JEI 自带纹理（输入标准框/输出输出框），固定活性物种（H₂O/H⁺）
 * 叠加灰色半透明遮罩 + tooltip 说明"不参与速率计算"；Km 显示在槽下文本与槽 tooltip 中
 */
public class EnzymeFactoryRecipeCategory implements IRecipeCategory<EnzymeRecipeDisplay> {
    /** 槽位尺寸与间距 */
    private static final int SLOT = 18;
    private static final int SLOT_GAP = 20;
    /** 槽区垂直起点 */
    private static final int SLOT_Y = 2;
    /** Km 文本行（槽底 20 + 2） */
    private static final int KM_Y = 22;
    /** 信息条行坐标（三行分散布局，避免行内信息挤叠） */
    private static final int INFO_LINE_1_Y = 34;
    private static final int INFO_LINE_2_Y = 44;
    private static final int INFO_LINE_3_Y = 54;
    /** 配方卡尺寸（background 为 null 时必须覆写 getWidth/getHeight） */
    private static final int WIDTH = 154;
    private static final int HEIGHT = 66;

    /** 文本颜色（与物品 tooltip 惯例一致：缩写黄、类别蓝、数值紫、说明灰） */
    private static final int COLOR_ABBR = 0xFFD700;
    private static final int COLOR_CATEGORY = 0x4FC3F7;
    private static final int COLOR_VALUE = 0xB57EDC;
    private static final int COLOR_DIM = 0x9E9E9E;
    private static final int COLOR_ARROW = 0xFFC0C0C0;

    /** 固定活性物种槽灰色遮罩：18x18 半透明深灰，叠加在标准槽纹理之上 */
    private static final IDrawable FIXED_ACTIVITY_OVERLAY = new IDrawable() {
        @Override
        public int getWidth() {
            return SLOT;
        }

        @Override
        public int getHeight() {
            return SLOT;
        }

        @Override
        public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
            graphics.fill(xOffset, yOffset, xOffset + SLOT, yOffset + SLOT, 0x80404040);
        }
    };

    /**
     * 本类别对应的配方类型
     *
     * @return 酶工厂配方类型
     */
    @Override
    public RecipeType<EnzymeRecipeDisplay> getRecipeType() {
        return BioCraftJeiPlugin.ENZYME_FACTORY;
    }

    /**
     * 类别标题（JEI 侧边栏类别名）
     *
     * @return 翻译组件
     */
    @Override
    public Component getTitle() {
        return Component.translatable("jei.biocraft.enzyme_factory");
    }

    /**
     * 类别图标：第一个酶工厂方块（PGI 机器）的物品图标
     *
     * @return 16x16 图标绘制器
     */
    @Override
    public IDrawable getIcon() {
        return new IDrawable() {
            @Override
            public int getWidth() {
                return 16;
            }

            @Override
            public int getHeight() {
                return 16;
            }

            @Override
            public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
                graphics.renderItem(new ItemStack(ModBlocks.enzymeItems().get(0).get()), xOffset, yOffset);
            }
        };
    }

    /**
     * 配方卡宽度（固定容纳 3 输入 + 3 输出的最大场景）
     *
     * @return 宽度像素
     */
    @Override
    public int getWidth() {
        return WIDTH;
    }

    /**
     * 配方卡高度（槽 + Km 行 + 两行信息条）
     *
     * @return 高度像素
     */
    @Override
    public int getHeight() {
        return HEIGHT;
    }

    /**
     * 布槽：输入在左、输出在右，中间留出箭头区
     * <p>
     * 槽位角色（INPUT/OUTPUT）同时驱动 JEI 的点击用途反向索引，
     * 玩家点任意分子物品即可追溯参与的所有酶反应
     *
     * @param builder 布局构建器
     * @param recipe  展示模型
     * @param focuses 焦点组（本类别不使用）
     */
    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, EnzymeRecipeDisplay recipe, IFocusGroup focuses) {
        int x = 2;
        for (Entry input : recipe.inputs()) {
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.INPUT, x, SLOT_Y)
                    .addItemStack(input.stack());
            configureSlot(slot, RecipeIngredientRole.INPUT, input);
            x += SLOT_GAP;
        }
        int outputX = arrowX(recipe) + 24;
        for (Entry output : recipe.outputs()) {
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.OUTPUT, outputX, SLOT_Y)
                    .addItemStack(output.stack());
            configureSlot(slot, RecipeIngredientRole.OUTPUT, output);
            outputX += SLOT_GAP;
        }
    }

    /**
     * 单槽配置：JEI 自带槽位纹理（输入槽标准框、输出槽输出框），
     * 固定活性物种叠加灰色遮罩；全部槽挂 Km/固定活性/系数 tooltip
     *
     * @param slot  待配置的槽构建器
     * @param role  槽位角色（决定标准/输出槽纹理）
     * @param entry 展示条目
     */
    private void configureSlot(IRecipeSlotBuilder slot, RecipeIngredientRole role, Entry entry) {
        if (role == RecipeIngredientRole.OUTPUT) {
            slot.setOutputSlotBackground();
        } else {
            slot.setStandardSlotBackground();
        }
        if (entry.fixedActivity()) {
            slot.setOverlay(FIXED_ACTIVITY_OVERLAY, 0, 0);
        }
        slot.addTooltipCallback((view, tooltip) -> {
            if (entry.fixedActivity()) {
                tooltip.add(Component.translatable("jei.biocraft.fixed_activity")
                        .withStyle(style -> style.withColor(COLOR_DIM)));
            } else {
                tooltip.add(Component.translatable("jei.biocraft.km", CompatRenderUtil.formatKm(entry.km()))
                        .withStyle(style -> style.withColor(COLOR_DIM)));
            }
            if (entry.count() > 1) {
                tooltip.add(Component.translatable("jei.biocraft.count", entry.count())
                        .withStyle(style -> style.withColor(COLOR_DIM)));
            }
        });
    }

    /**
     * 配方卡绘制：箭头 + Keq + 槽下 Km + 底部三行信息条（全部无阴影，避免小字号模糊）
     *
     * @param recipe     展示模型
     * @param slotsView  已布局的槽视图（本类别不使用）
     * @param graphics   绘制上下文
     * @param mouseX     鼠标 x（本类别不使用）
     * @param mouseY     鼠标 y（本类别不使用）
     */
    @Override
    public void draw(EnzymeRecipeDisplay recipe, IRecipeSlotsView slotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        int arrowX = arrowX(recipe);

        // 箭头区：水平线 + 三角，可逆 ⇌ 双头、不可逆 → 单头；箭头垂直居中于槽
        int arrowY = SLOT_Y + SLOT / 2 - 1;
        drawArrow(graphics, arrowX, arrowY, recipe.reversible());

        // Keq 文本画在箭头正下方（与 Km 行同高，x 不重叠）
        graphics.drawString(font,
                Component.translatable("jei.biocraft.keq", CompatRenderUtil.formatKeq(recipe.keq()))
                        .withStyle(style -> style.withColor(COLOR_DIM)),
                arrowX - 4, KM_Y, COLOR_DIM, false);

        // 槽下 Km 文本（固定活性物种不显示 Km 数值）
        int x = 2;
        for (Entry input : recipe.inputs()) {
            if (!input.fixedActivity()) {
                graphics.drawString(font,
                        Component.translatable("jei.biocraft.km", CompatRenderUtil.formatKm(input.km()))
                                .withStyle(style -> style.withColor(COLOR_DIM)),
                        x, KM_Y, COLOR_DIM, false);
            }
            x += SLOT_GAP;
        }
        int outputX = arrowX + 24;
        for (Entry output : recipe.outputs()) {
            if (!output.fixedActivity()) {
                graphics.drawString(font,
                        Component.translatable("jei.biocraft.km", CompatRenderUtil.formatKm(output.km()))
                                .withStyle(style -> style.withColor(COLOR_DIM)),
                        outputX, KM_Y, COLOR_DIM, false);
            }
            outputX += SLOT_GAP;
        }

        // 信息条行 1：缩写黄字徽标 + EC 类别与动力学变体（蓝）
        MutableComponent line1 = Component.literal("[" + recipe.abbreviation() + "]")
                .withStyle(style -> style.withColor(COLOR_ABBR));
        line1.append(Component.literal(" " + recipe.ecCategory() + " · ")
                .withStyle(style -> style.withColor(COLOR_CATEGORY)));
        line1.append(Component.translatable(CompatRenderUtil.kineticLangKey(recipe.kinetic()))
                .withStyle(style -> style.withColor(COLOR_CATEGORY)));
        graphics.drawString(font, line1, 2, INFO_LINE_1_Y, COLOR_DIM, false);

        // 信息条行 2：kcat（紫）+ 最适温度（灰）
        MutableComponent line2 = Component.translatable("jei.biocraft.kcat", CompatRenderUtil.formatKcat(recipe.kcat()))
                .withStyle(style -> style.withColor(COLOR_VALUE));
        line2.append(Component.literal(" · ").withStyle(style -> style.withColor(COLOR_DIM)));
        line2.append(Component.translatable("jei.biocraft.temp",
                        String.format(Locale.ROOT, "%.0f", recipe.tempOptimum()))
                .withStyle(style -> style.withColor(COLOR_DIM)));
        graphics.drawString(font, line2, 2, INFO_LINE_2_Y, COLOR_DIM, false);

        // 信息条行 3：ΔG°′（紫）+ 激活剂列表（橙）；无激活剂显示占位横线
        MutableComponent line3 = Component.translatable("jei.biocraft.delta_g",
                        CompatRenderUtil.formatDeltaG(recipe.deltaG()))
                .withStyle(style -> style.withColor(COLOR_VALUE));
        line3.append(Component.literal("  ").withStyle(style -> style.withColor(COLOR_DIM)));
        if (recipe.activators().isEmpty()) {
            line3.append(Component.literal("—").withStyle(style -> style.withColor(COLOR_DIM)));
        } else {
            String names = recipe.activators().stream()
                    .map(stack -> stack.getHoverName().getString())
                    .collect(Collectors.joining(", "));
            line3.append(Component.translatable("jei.biocraft.activator", names)
                    .withStyle(style -> style.withColor(0xFF8C00)));
        }
        graphics.drawString(font, line3, 2, INFO_LINE_3_Y, COLOR_DIM, false);
    }

    /**
     * 箭头区起始 x：输入末槽右侧留 8px 间隙
     *
     * @param recipe 展示模型
     * @return 箭头区 x 坐标
     */
    private int arrowX(EnzymeRecipeDisplay recipe) {
        return 2 + recipe.inputs().size() * SLOT_GAP + 8;
    }

    /**
     * 绘制箭头：水平线 + 三角尖端
     * <p>
     * 三角用两格 fill 拼出 3 像素宽的实心尖端；可逆反应两端都画三角（⇌），
     * 不可逆只画右端（→）
     *
     * @param graphics   绘制上下文
     * @param x          箭头区起点（线左端）
     * @param y          线顶 y
     * @param reversible 是否可逆（决定双头/单头）
     */
    private void drawArrow(GuiGraphics graphics, int x, int y, boolean reversible) {
        int length = reversible ? 18 : 10;
        graphics.fill(x, y, x + length, y + 1, COLOR_ARROW);
        // 右三角：尖端在 (x+length, y+1)
        graphics.fill(x + length, y, x + length + 1, y + 3, COLOR_ARROW);
        graphics.fill(x + length - 1, y + 1, x + length, y + 2, COLOR_ARROW);
        if (reversible) {
            // 左三角：尖端在 (x, y+1)
            graphics.fill(x - 1, y, x, y + 3, COLOR_ARROW);
            graphics.fill(x, y + 1, x + 1, y + 2, COLOR_ARROW);
        }
    }
}
