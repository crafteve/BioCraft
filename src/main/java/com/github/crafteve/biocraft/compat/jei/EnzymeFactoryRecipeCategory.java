package com.github.crafteve.biocraft.compat.jei;

import com.github.crafteve.biocraft.compat.CompatRenderUtil;
import com.github.crafteve.biocraft.compat.EnzymeRecipeDisplay;
import com.github.crafteve.biocraft.compat.EnzymeRecipeDisplay.Entry;
import com.github.crafteve.biocraft.init.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * 酶工厂配方类别（JEI 显示层核心）
 * <p>
 * 布局规格（像素单位）：
 * <pre>
 *   y=2    [槽1] [槽2]   ⇌/→    [槽1] [槽2]   槽 18x18、槽距 20、标准槽纹理
 *   y=22   Km=    Km=    Keq=    Km=    Km=    行 1：白字 + 阴影（MC 标准字体）
 *   y=34   [酶槽] [PGI]                      行 2：酶槽（标准纹理放酶方块）
 *   y=43   磷酸葡萄糖异构酶                         右侧上部缩写（主题色+阴影）
 *   y=56   Vmax_f = 0.79 个/tick · Vmax_b = 0.26 个/tick
 *                                             行 3：饱和可达最大速率（白字+阴影）
 * </pre>
 * 输入槽从 x=2 起排布，箭头区固定在输入末槽右侧（arrowX = 2 + nIn*20 + 8），
 * 输出槽在箭头右侧（outputX = arrowX + 24）；卡宽固定 154（容纳最多 3 输入 + 3 输出）
 * <p>
 * 槽位统一使用 JEI 标准槽纹理；固定活性物种（H₂O/H⁺）仅在 tooltip 中说明
 * （不参与速率计算）；Km/Keq/Vmax 全部白字带阴影，酶缩写用 EC 类别主题色
 */
public class EnzymeFactoryRecipeCategory implements IRecipeCategory<EnzymeRecipeDisplay> {
    /** 槽位尺寸与间距 */
    private static final int SLOT = 18;
    private static final int SLOT_GAP = 20;
    /** 槽区垂直起点 */
    private static final int SLOT_Y = 2;
    /** Km/Keq 文本行（槽底 20 + 2） */
    private static final int KM_Y = 22;
    /** 酶信息区：酶槽位置与右侧文字位置 */
    private static final int ENZ_SLOT_Y = 34;
    private static final int ENZ_TEXT_X = 24;
    private static final int ABBR_Y = 34;
    private static final int NAME_Y = 43;
    /** Vmax 行（酶槽下方 4px） */
    private static final int VMAX_Y = 56;
    /** 配方卡尺寸（background 为 null 时必须覆写 getWidth/getHeight） */
    private static final int WIDTH = 154;
    private static final int HEIGHT = 66;

    /** 文本颜色：主信息全白带阴影；tooltip 说明灰色 */
    private static final int COLOR_WHITE = 0xFFFFFF;
    private static final int COLOR_DIM = 0x9E9E9E;
    private static final int COLOR_ARROW = 0xFFC0C0C0;

    /** JEI 标准槽纹理（酶信息区槽位与配方槽共用同一视觉） */
    private final IDrawable slotDrawable;

    /**
     * @param guiHelper JEI 图形助手（注册类别时由插件传入，取标准槽纹理）
     */
    public EnzymeFactoryRecipeCategory(IGuiHelper guiHelper) {
        this.slotDrawable = guiHelper.getSlotDrawable();
    }

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
     * 配方卡高度（槽 + Km 行 + 酶信息块 + Vmax 行）
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
            configureSlot(slot, input);
            x += SLOT_GAP;
        }
        int outputX = arrowX(recipe) + 24;
        for (Entry output : recipe.outputs()) {
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.OUTPUT, outputX, SLOT_Y)
                    .addItemStack(output.stack());
            configureSlot(slot, output);
            outputX += SLOT_GAP;
        }
    }

    /**
     * 单槽配置：统一 JEI 标准槽纹理 + Km/固定活性/系数 tooltip
     *
     * @param slot  待配置的槽构建器
     * @param entry 展示条目
     */
    private void configureSlot(IRecipeSlotBuilder slot, Entry entry) {
        slot.setStandardSlotBackground();
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
     * 配方卡绘制：箭头 + Km/Keq 行 + 酶信息块（槽+缩写+显示名）+ Vmax 行
     * <p>
     * 主信息全部 MC 标准字体白字带阴影（小字号阴影避免模糊）；酶缩写用 EC 主题色带阴影
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

        // 行 1：Keq 画在箭头正下方，Km 画在每个槽正下方（白字带阴影）
        graphics.drawString(font,
                Component.translatable("jei.biocraft.keq", CompatRenderUtil.formatKeq(recipe.keq()))
                        .withStyle(style -> style.withColor(COLOR_WHITE)),
                arrowX - 4, KM_Y, COLOR_WHITE, true);
        int x = 2;
        for (Entry input : recipe.inputs()) {
            if (!input.fixedActivity()) {
                graphics.drawString(font,
                        Component.translatable("jei.biocraft.km", CompatRenderUtil.formatKm(input.km()))
                                .withStyle(style -> style.withColor(COLOR_WHITE)),
                        x, KM_Y, COLOR_WHITE, true);
            }
            x += SLOT_GAP;
        }
        int outputX = arrowX + 24;
        for (Entry output : recipe.outputs()) {
            if (!output.fixedActivity()) {
                graphics.drawString(font,
                        Component.translatable("jei.biocraft.km", CompatRenderUtil.formatKm(output.km()))
                                .withStyle(style -> style.withColor(COLOR_WHITE)),
                        outputX, KM_Y, COLOR_WHITE, true);
            }
            outputX += SLOT_GAP;
        }

        // 行 2：酶信息块——标准槽放酶方块，右侧上部缩写（主题色+阴影）、下部显示名（白+阴影）
        slotDrawable.draw(graphics, 2, ENZ_SLOT_Y);
        graphics.renderItem(recipe.machineStack(), 2 + 1, ENZ_SLOT_Y + 1);
        int theme = CompatRenderUtil.themeColor(recipe.ecCategory());
        graphics.drawString(font, recipe.abbreviation(), ENZ_TEXT_X, ABBR_Y, theme, true);
        graphics.drawString(font, recipe.displayName(), ENZ_TEXT_X, NAME_Y, COLOR_WHITE, true);

        // 行 3：正向/逆向饱和可达最大速率（个/tick，白字带阴影，两位小数）
        String vmaxText = "Vmax_f = " + CompatRenderUtil.formatRate(recipe.vmaxFPerTick())
                + " 个/tick · Vmax_b = " + CompatRenderUtil.formatRate(recipe.vmaxBPerTick())
                + " 个/tick";
        graphics.drawString(font, vmaxText, 2, VMAX_Y, COLOR_WHITE, true);
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
