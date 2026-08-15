package com.github.crafteve.biocraft.compat.jei;

import com.github.crafteve.biocraft.compat.CompatRenderUtil;
import com.github.crafteve.biocraft.compat.EnzymeRecipeDisplay;
import com.github.crafteve.biocraft.compat.EnzymeRecipeDisplay.Entry;
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

/**
 * 酶工厂配方类别（JEI 显示层核心，每酶一个专属类别实例）
 * <p>
 * 布局规格（像素单位，文本元素一律左对齐）：
 * <pre>
 *   y=2    [槽1] [槽2]  →(JEI 自带箭头)  [槽1] [槽2]   标准槽纹理，箭头无视可逆统一右向
 *   y=34   [酶槽] [PGI]                              行 1：酶方块槽（CATALYST 可交互，
 *   y=43   磷酸葡萄糖异构酶                                hover 显示酶名，点击查看本酶配方）
 *                                                    右侧上部缩写（主题色+阴影）、下部显示名（白+阴影）
 *   y=56   Keq = 4.8×10³                             行 2：Keq（左对齐 x=2，白字+阴影，
 *   y=65   正向速率最大值 0.18 /tick                     位于酶槽下方、速率上方）
 *   y=74   逆向速率最大值 0.026 /tick                行 3/4：饱和可达速率（左对齐 x=2）
 * </pre>
 * 输入槽从 x=2 起排布，箭头区固定在输入末槽右侧（arrowX = 2 + nIn*20 + 8），
 * 输出槽在箭头右侧（outputX = arrowX + 30）；卡宽固定 160（容纳最多 3 输入 + 3 输出）
 * <p>
 * 槽位统一使用 JEI 标准槽纹理；Km 不占卡片行位（每槽 tooltip 中显示）；
 * 主信息全部 MC 标准字体白字带阴影
 */
public class EnzymeFactoryRecipeCategory implements IRecipeCategory<EnzymeRecipeDisplay> {
    /** 槽位尺寸与间距 */
    private static final int SLOT = 18;
    private static final int SLOT_GAP = 20;
    /** 槽区垂直起点 */
    private static final int SLOT_Y = 2;
    /** 酶信息区：酶槽位置与右侧文字位置 */
    private static final int MACHINE_SLOT_Y = 34;
    private static final int ENZ_TEXT_X = 24;
    private static final int ABBR_Y = 34;
    private static final int NAME_Y = 43;
    /** 数值三行（酶槽下方 4px 起，左对齐）：Keq + 正逆向速率 */
    private static final int KEQ_Y = 56;
    private static final int VMAX_F_Y = 65;
    private static final int VMAX_B_Y = 74;
    /** 文本区统一左对齐起点 */
    private static final int TEXT_X = 2;
    /** 配方卡尺寸（background 为 null 时必须覆写 getWidth/getHeight） */
    private static final int WIDTH = 160;
    private static final int HEIGHT = 83;

    /** 文本颜色：主信息全白带阴影；tooltip 说明灰色 */
    private static final int COLOR_WHITE = 0xFFFFFF;
    private static final int COLOR_DIM = 0x9E9E9E;

    /** 本类别对应的配方类型（每酶专属） */
    private final RecipeType<EnzymeRecipeDisplay> recipeType;

    /** 本类别展示的酶（标题/图标/酶槽/信息区数据源） */
    private final EnzymeRecipeDisplay display;

    /** JEI 自带配方箭头纹理（无视可逆统一右向） */
    private final IDrawable recipeArrow;

    /**
     * @param recipeType 该酶的专属配方类型
     * @param display    该酶的展示模型
     * @param guiHelper  JEI 图形助手（标准槽纹理、配方箭头）
     */
    public EnzymeFactoryRecipeCategory(RecipeType<EnzymeRecipeDisplay> recipeType,
                                       EnzymeRecipeDisplay display, IGuiHelper guiHelper) {
        this.recipeType = recipeType;
        this.display = display;
        this.recipeArrow = guiHelper.getRecipeArrow();
    }

    /**
     * 本类别对应的配方类型
     *
     * @return 该酶专属配方类型
     */
    @Override
    public RecipeType<EnzymeRecipeDisplay> getRecipeType() {
        return recipeType;
    }

    /**
     * 类别标题（JEI 侧边栏类别名）：该酶显示名
     *
     * @return 酶显示名组件
     */
    @Override
    public Component getTitle() {
        return Component.literal(display.displayName());
    }

    /**
     * 类别图标：该酶工厂方块的物品图标
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
                graphics.renderItem(display.machineStack(), xOffset, yOffset);
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
     * 配方卡高度（槽 + Keq 行 + 酶信息块 + Vmax 两行）
     *
     * @return 高度像素
     */
    @Override
    public int getHeight() {
        return HEIGHT;
    }

    /**
     * 布槽：反应物/产物槽（INPUT/OUTPUT）+ 酶工厂方块槽（CATALYST）
     * <p>
     * 反应物/产物槽角色同时驱动 JEI 的点击用途反向索引（跨类别全局）；
     * 酶槽用 CATALYST 角色：hover 显示酶名、点击直接查看本酶配方
     *
     * @param builder 布局构建器
     * @param recipe  展示模型
     * @param focuses 焦点组（本类别不使用）
     */
    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, EnzymeRecipeDisplay recipe, IFocusGroup focuses) {
        int x = TEXT_X;
        for (Entry input : recipe.inputs()) {
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.INPUT, x, SLOT_Y)
                    .addItemStack(input.stack());
            configureSlot(slot, input);
            x += SLOT_GAP;
        }
        int outputX = arrowX(recipe) + 30;
        for (Entry output : recipe.outputs()) {
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.OUTPUT, outputX, SLOT_Y)
                    .addItemStack(output.stack());
            configureSlot(slot, output);
            outputX += SLOT_GAP;
        }

        // 酶工厂方块槽：标准槽纹理，CATALYST 角色（可交互）。
        // 不再自定义 tooltip 回调：JEI 对槽内物品自带名称显示（即酶名），
        // 再加回调会与默认名称重复成两行（此前实测的显示 bug）
        IRecipeSlotBuilder machineSlot = builder
                .addSlot(RecipeIngredientRole.CATALYST, TEXT_X, MACHINE_SLOT_Y)
                .addItemStack(recipe.machineStack());
        machineSlot.setStandardSlotBackground();
    }

    /**
     * 单槽配置：统一 JEI 标准槽纹理 + 固定活性/系数 tooltip
     * <p>
     * 不再显示 Km（策划决定取消）：物种槽 tooltip 只保留固定活性说明
     * 与化学计量系数，避免信息噪音
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
            }
            if (entry.count() > 1) {
                tooltip.add(Component.translatable("jei.biocraft.count", entry.count())
                        .withStyle(style -> style.withColor(COLOR_DIM)));
            }
        });
    }

    /**
     * 配方卡绘制：JEI 自带右箭头 + 酶信息块 + 数值三行（Keq + 正逆向速率）
     * <p>
     * 全部文本元素左对齐（TEXT_X=2，酶信息块文字随酶槽右移）；
     * 主信息 MC 标准字体白字带阴影；酶缩写用 EC 主题色带阴影
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

        // JEI 自带配方箭头（24x17），无视可逆统一右向；垂直与槽区对齐
        recipeArrow.draw(graphics, arrowX, SLOT_Y + 1);

        // 数值区行 1：Keq（左对齐），位于酶槽下方、速率上方
        graphics.drawString(font,
                Component.translatable("jei.biocraft.keq", CompatRenderUtil.formatKeq(recipe.keq()))
                        .withStyle(style -> style.withColor(COLOR_WHITE)),
                TEXT_X, KEQ_Y, COLOR_WHITE, true);

        // 行 2：酶信息块——酶槽由 setRecipe 布（CATALYST 可交互），
        // 右侧上部缩写（主题色+阴影）、下部显示名（白+阴影）
        int theme = CompatRenderUtil.themeColor(recipe.ecCategory());
        graphics.drawString(font, recipe.abbreviation(), ENZ_TEXT_X, ABBR_Y, theme, true);
        graphics.drawString(font, recipe.displayName(), ENZ_TEXT_X, NAME_Y, COLOR_WHITE, true);

        // 行 2/3：正向/逆向饱和可达最大速率，各自独立一行（中文文案，两位有效数字，/tick）
        graphics.drawString(font,
                Component.translatable("jei.biocraft.vmax_f", CompatRenderUtil.formatRate(recipe.vmaxFPerTick()))
                        .withStyle(style -> style.withColor(COLOR_WHITE)),
                TEXT_X, VMAX_F_Y, COLOR_WHITE, true);
        graphics.drawString(font,
                Component.translatable("jei.biocraft.vmax_b", CompatRenderUtil.formatRate(recipe.vmaxBPerTick()))
                        .withStyle(style -> style.withColor(COLOR_WHITE)),
                TEXT_X, VMAX_B_Y, COLOR_WHITE, true);
    }

    /**
     * 箭头区起始 x：输入末槽右侧留 8px 间隙
     *
     * @param recipe 展示模型
     * @return 箭头区 x 坐标
     */
    private int arrowX(EnzymeRecipeDisplay recipe) {
        return TEXT_X + recipe.inputs().size() * SLOT_GAP + 8;
    }
}
