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
import net.minecraft.world.item.ItemStack;

/**
 * 酶工厂配方类别（JEI 显示层核心）
 * <p>
 * 布局规格（像素单位，一行只放一个信息）：
 * <pre>
 *   y=2    [槽1] [槽2]   ⇌/→    [槽1] [槽2]   槽 18x18、槽距 20、标准槽纹理
 *   y=22   Keq = 4.8×10³                     行 1：仅 Keq（白字 + 阴影）
 *   y=34   [酶槽]                            行 2：酶工厂方块槽（CATALYST 可交互，
 *                                                  hover 显示酶名，点击查看本酶配方）
 *   y=56   正向速率最大值 0.18 个/tick        行 3：饱和可达正向速率（白字 + 阴影）
 *   y=65   逆向速率最大值 0.026 个/tick       行 4：饱和可达逆向速率（白字 + 阴影）
 * </pre>
 * 输入槽从 x=2 起排布，箭头区固定在输入末槽右侧（arrowX = 2 + nIn*20 + 8），
 * 输出槽在箭头右侧（outputX = arrowX + 24）；卡宽固定 154（容纳最多 3 输入 + 3 输出）
 * <p>
 * 槽位统一使用 JEI 标准槽纹理；Km 不占卡片行位（每槽 tooltip 中显示）；
 * 固定活性物种（H₂O/H⁺）仅在 tooltip 中说明；主信息全部 MC 标准字体白字带阴影
 */
public class EnzymeFactoryRecipeCategory implements IRecipeCategory<EnzymeRecipeDisplay> {
    /** 槽位尺寸与间距 */
    private static final int SLOT = 18;
    private static final int SLOT_GAP = 20;
    /** 槽区垂直起点 */
    private static final int SLOT_Y = 2;
    /** Keq 文本行（槽底 20 + 2） */
    private static final int KEQ_Y = 22;
    /** 酶工厂方块槽（行 2） */
    private static final int MACHINE_SLOT_Y = 34;
    /** Vmax 两行（酶槽下方 4px，各自独立一行） */
    private static final int VMAX_F_Y = 56;
    private static final int VMAX_B_Y = 65;
    /** 配方卡尺寸（background 为 null 时必须覆写 getWidth/getHeight） */
    private static final int WIDTH = 154;
    private static final int HEIGHT = 74;

    /** 文本颜色：主信息全白带阴影；tooltip 说明灰色 */
    private static final int COLOR_WHITE = 0xFFFFFF;
    private static final int COLOR_DIM = 0x9E9E9E;
    private static final int COLOR_ARROW = 0xFFC0C0C0;

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
     * 配方卡高度（槽 + Keq 行 + 酶槽 + Vmax 两行）
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
     * 反应物/产物槽角色同时驱动 JEI 的点击用途反向索引；
     * 酶槽用 CATALYST 角色：hover 显示酶名、点击直接查看本酶配方，
     * 与 registerRecipeCatalysts 的全局注册指向同一配方，不冲突
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

        // 酶工厂方块槽：标准槽纹理，CATALYST 角色（可交互），tooltip 显示酶名
        IRecipeSlotBuilder machineSlot = builder
                .addSlot(RecipeIngredientRole.CATALYST, 2, MACHINE_SLOT_Y)
                .addItemStack(recipe.machineStack());
        machineSlot.setStandardSlotBackground();
        machineSlot.addTooltipCallback((view, tooltip) ->
                tooltip.add(Component.literal(recipe.displayName())
                        .withStyle(style -> style.withColor(COLOR_WHITE))));
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
     * 配方卡绘制：箭头 + Keq 行 + Vmax 两行（每行只放一个信息）
     * <p>
     * 主信息全部 MC 标准字体白字带阴影（小字号阴影避免模糊）
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

        // 行 1：仅 Keq（箭头正下方），一行只放一个信息
        graphics.drawString(font,
                Component.translatable("jei.biocraft.keq", CompatRenderUtil.formatKeq(recipe.keq()))
                        .withStyle(style -> style.withColor(COLOR_WHITE)),
                arrowX - 4, KEQ_Y, COLOR_WHITE, true);

        // 行 3/4：正向/逆向饱和可达最大速率，各自独立一行（中文文案，两位有效数字，个/tick）
        graphics.drawString(font,
                Component.translatable("jei.biocraft.vmax_f", CompatRenderUtil.formatRate(recipe.vmaxFPerTick()))
                        .withStyle(style -> style.withColor(COLOR_WHITE)),
                2, VMAX_F_Y, COLOR_WHITE, true);
        graphics.drawString(font,
                Component.translatable("jei.biocraft.vmax_b", CompatRenderUtil.formatRate(recipe.vmaxBPerTick()))
                        .withStyle(style -> style.withColor(COLOR_WHITE)),
                2, VMAX_B_Y, COLOR_WHITE, true);
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
