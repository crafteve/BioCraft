package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.EnzymeFactoryBlockEntity;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.item.MoleculeItem;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import com.github.crafteve.biocraft.reaction.KineticConstants;
import com.github.crafteve.biocraft.reaction.ReactionDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 酶工厂仪表盘屏幕（256×256 贴图优先架构）
 * <p>
 * 渲染分层：
 * <ul>
 *   <li>底层：enzyme_background.png（用户手绘，256×256，含背包区视觉），
 *       renderBg 第一步 1:1 blit 整张（无缩放无虚化）</li>
 *   <li>卡片框架：标题/输入/输出/仪表盘四张卡片由代码绘制（fill+描边，
 *       背包区不画框——背景贴图已含）</li>
 *   <li>贴图元素：槽位（slot_light 18×18）、平衡条（balance_bar）、
 *       Keq/Q 指针（keq_point/q_point）</li>
 *   <li>动态元素（代码）：全部文字（shadow=false 防重影）、浓度/速率填充条、
 *       v-t 折线、物品图标</li>
 * </ul>
 * 布局坐标（256×256）：
 * <ul>
 *   <li>标题栏 y8~52；输入卡 x8~74 / 仪表盘 x78~170 / 输出卡 x174~240，y60~166</li>
 *   <li>背包区由背景贴图提供：主背包视觉起点 (48,174)、快捷栏 (48,232)，
 *       槽位 18×18 间距 2px（步进 20），Slot 坐标 = 视觉起点 + 1</li>
 * </ul>
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    // 贴图资产
    private static final ResourceLocation ENZYME_BG = ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "textures/gui/enzyme_background.png");
    private static final ResourceLocation SLOT = ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "textures/gui/slot.png");
    private static final ResourceLocation BALANCE_BAR = ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "textures/gui/balance_bar.png");
    private static final ResourceLocation KEQ_POINT = ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "textures/gui/keq_point.png");
    private static final ResourceLocation Q_POINT = ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "textures/gui/q_point.png");

    // GUI 尺寸 = 底层贴图尺寸（1:1 blit，杜绝缩放虚化）
    private static final int GUI_W = 256;
    private static final int GUI_H = 256;

    // 主题色
    private static final int INK = 0xFF1A1A1A;
    private static final int GRAY_TEXT = 0xFF777777;
    private static final int CARD_BORDER = 0xFF888888;
    private static final int SUB_CARD_BORDER = 0xFFBBBBBB;
    private static final int SUB_CARD_BG = 0xFFFAFAF8;
    private static final int PURPLE = 0xFF7050B0;
    private static final int PURPLE_LIGHT = 0xFF9060D0;
    private static final int PURPLE_DARK = 0xFF503080;
    private static final int PURPLE_TAG_BG = 0xFFE8E0F0;
    private static final int BAR_TRACK = 0xFFE0E0E0;

    // 布局（GUI 坐标）
    private static final int TITLE_X = 8, TITLE_Y = 8, TITLE_W = 240, TITLE_H = 32;
    private static final int SIDE_CARD_X = 8, OUTPUT_CARD_X = 174, SIDE_CARD_W = 66;
    private static final int DASH_X = 78, DASH_W = 92;
    private static final int CARD_Y = 60, CARD_H = 106;
    // 物种子卡：槽位 Slot 坐标 x=11/177，y = 65 + 行号×32（每行高 32）
    private static final int SPECIES_Y0 = 65, SPECIES_GAP = 32;

    // 仪表盘元素（紧凑布局，y 60~166）
    private static final int EQ_Y = 69;
    private static final int REVERSIBLE_Y = 79;
    private static final int RATE_LABEL_Y = 90;
    private static final int RATE_BAR_Y = 95;
    private static final int ARROW_Y = 105;
    private static final int BALANCE_LABEL_Y = 116;
    private static final int BALANCE_BAR_Y = 122;
    private static final int QK_EQ_Y = 134;
    private static final int VT_LABEL_Y = 141;
    private static final int VT_Y = 147, VT_H = 18;

    /** 矢量字体 id（assets/biocraft/font/enzyme.json，simhei TTF，size 10） */
    private static final ResourceLocation ENZYME_FONT =
            ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "enzyme");

    private final EnzymeFactoryBlockEntity blockEntity;
    private final EnzymeFactoryData enzymeData;
    private final List<String> speciesIds;
    private final Map<String, MoleculeItem> itemBySpecies;
    private final int[] vHistory = new int[100];
    private int vHistoryCount;

    /**
     * @param menu             菜单实例
     * @param playerInventory  玩家物品栏
     * @param title            窗口标题
     */
    public MachineScreen(MachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_W;
        this.imageHeight = GUI_H;
        this.blockEntity = menu.getBlockEntity();
        this.enzymeData = menu.getEnzymeData();

        // 物种顺序重建（与引擎构建一致：反应物顺序 + 产物顺序，去重）
        this.speciesIds = new ArrayList<>();
        for (EnzymeFactoryData.SpeciesSpec spec : enzymeData.reactants()) {
            if (!speciesIds.contains(spec.item())) {
                speciesIds.add(spec.item());
            }
        }
        for (EnzymeFactoryData.SpeciesSpec spec : enzymeData.products()) {
            if (!speciesIds.contains(spec.item())) {
                speciesIds.add(spec.item());
            }
        }
        this.itemBySpecies = new HashMap<>();
        for (String id : speciesIds) {
            itemBySpecies.put(id, ModItems.byId(id).get());
        }

        int[] history = menu.getFluxHistory();
        vHistoryCount = Math.min(history.length, vHistory.length);
        System.arraycopy(history, 0, vHistory, 0, vHistoryCount);
    }

    /**
     * 每 tick 追加通量到 v-t 环形缓冲（打开期间 DATA_FLUX 每 tick 同步，零额外流量）
     */
    @Override
    protected void containerTick() {
        super.containerTick();
        if (vHistoryCount >= vHistory.length) {
            System.arraycopy(vHistory, 1, vHistory, 0, vHistory.length - 1);
            vHistory[vHistory.length - 1] = (int) Math.round(menu.getFlux() * 1000.0);
        } else {
            vHistory[vHistoryCount++] = (int) Math.round(menu.getFlux() * 1000.0);
        }
    }

    /**
     * 渲染入口：super（背景 + 槽位 + 物品）+ 悬停物品 tooltip
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * 主画布：底层贴图 1:1 blit + 卡片框架 + 全部仪表元素
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 底层贴图（用户手绘，256×256，含背包区；1:1 blit 无缩放）
        graphics.blit(ENZYME_BG, this.leftPos, this.topPos, 0, 0, GUI_W, GUI_H, GUI_W, GUI_H);

        drawTitleCard(graphics);
        drawSpeciesCard(graphics, SIDE_CARD_X, "输入（底物）", 0, enzymeData.reactants().size());
        drawSpeciesCard(graphics, OUTPUT_CARD_X, "输出（产物）", enzymeData.reactants().size(),
                enzymeData.reactants().size() + enzymeData.products().size());
        drawDashboard(graphics);
    }

    /**
     * 标题卡（上下压缩，简洁布局）：
     * 方块 3D 贴图（左侧垂直居中）→ 紫框缩写 → displayname，右侧 T/pH 横排
     */
    private void drawTitleCard(GuiGraphics graphics) {
        drawCard(graphics, TITLE_X, TITLE_Y, TITLE_W, TITLE_H);

        // 方块 3D 贴图：左侧上下居中（卡片高 32，图标 16×16 → y 偏移 8）
        ItemStack blockStack = new ItemStack(blockEntity.getBlockState().getBlock());
        graphics.renderItem(blockStack, this.leftPos + TITLE_X + 4, this.topPos + TITLE_Y + 8);

        // 紫框缩写（宽度按内容自适应，垂直居中）
        String abbr = enzymeData.abbreviation();
        int abbrW = textWidth(abbr) + 10;
        int abbrX = TITLE_X + 26;
        int abbrY = TITLE_Y + (TITLE_H - 20) / 2;
        graphics.fill(this.leftPos + abbrX, this.topPos + abbrY,
                this.leftPos + abbrX + abbrW, this.topPos + abbrY + 20, PURPLE_TAG_BG);
        graphics.renderOutline(this.leftPos + abbrX, this.topPos + abbrY, abbrW, 20, PURPLE);
        drawText(graphics, abbr,
                this.leftPos + abbrX + 5, this.topPos + abbrY + 5, PURPLE, false);

        // displayname（紫框右侧，垂直居中；超出与 T/pH 的间隙时按宽度截断）
        int nameX = abbrX + abbrW + 8;
        int nameY = TITLE_Y + (TITLE_H - 10) / 2;
        String env = "T " + (int) Math.round(menu.getTemperature()) + "K  pH 7.00";
        int envX = TITLE_X + TITLE_W - 8 - textWidth(env);
        int availW = envX - nameX - 6;
        String name = enzymeData.nameZn();
        if (textWidth(name) > availW) {
            name = this.font.plainSubstrByWidth(name, Math.max(availW - 10, 10)) + "…";
        }
        drawText(graphics, name,
                this.leftPos + nameX, this.topPos + nameY, INK, false);

        // T / pH 横排（右侧，垂直居中）
        drawText(graphics, env,
                this.leftPos + envX, this.topPos + nameY, GRAY_TEXT, false);
    }

    /**
     * 输入/输出卡：卡片框架 + 每物种子卡（槽位由 renderSlot 覆写画浅色背景）
     */
    private void drawSpeciesCard(GuiGraphics graphics, int cardX, String title, int slotStart, int slotEnd) {
        drawCard(graphics, cardX, CARD_Y, SIDE_CARD_W, CARD_H);
        drawText(graphics, title,
                this.leftPos + cardX + 2, this.topPos + CARD_Y + 3, INK, false);

        int row = 0;
        for (int slotIndex = slotStart; slotIndex < slotEnd; slotIndex++) {
            int subY = SPECIES_Y0 + row * SPECIES_GAP;
            int subX = cardX + 2;
            // 子卡背景（槽位区域 18×18 + 右侧信息区）
            graphics.fill(this.leftPos + subX, this.topPos + subY - 3,
                    this.leftPos + subX + SIDE_CARD_W - 4, this.topPos + subY + 28, SUB_CARD_BG);
            graphics.renderOutline(this.leftPos + subX, this.topPos + subY - 3,
                    SIDE_CARD_W - 4, 31, SUB_CARD_BORDER);

            String speciesId = speciesIds.get(slotIndex);
            MoleculeItem item = itemBySpecies.get(speciesId);
            ItemStack stack = this.menu.getSlot(slotIndex).getItem();

            // 缩写
            drawText(graphics, item.getAbbreviation(),
                    this.leftPos + subX + 24, this.topPos + subY - 2, INK, false);
            // 数量
            drawText(graphics, "×" + stack.getCount(),
                    this.leftPos + subX + 24, this.topPos + subY + 7, GRAY_TEXT, false);
            // 浓度条（数量 + 引擎余量，语义色 = 物品染色）
            double concentration = (stack.getCount() + blockEntity.getRemainder(slotIndex)) / 64.0;
            int barColor = 0xFF000000 | item.getTintColor();
            graphics.fill(this.leftPos + subX + 24, this.topPos + subY + 18,
                    this.leftPos + subX + 56, this.topPos + subY + 24, BAR_TRACK);
            graphics.fill(this.leftPos + subX + 24, this.topPos + subY + 18,
                    this.leftPos + subX + 24 + (int) (32 * Math.min(concentration, 1.0)), this.topPos + subY + 24, barColor);
            row++;
        }
    }

    /**
     * 仪表盘：方程式 + 速率条 + 方向箭头 + 平衡条双指针 + v-t 图
     */
    private void drawDashboard(GuiGraphics graphics) {
        drawCard(graphics, DASH_X, CARD_Y, DASH_W, CARD_H);

        // 反应方程式
        String equation = renderEquation();
        drawText(graphics, equation,
                this.leftPos + DASH_X + 4, this.topPos + EQ_Y, PURPLE, false);
        drawText(graphics, enzymeData.reversible() ? "可逆反应" : "不可逆反应",
                this.leftPos + DASH_X + 30, this.topPos + REVERSIBLE_Y, GRAY_TEXT, false);

        // 净速率条
        drawText(graphics, "净速率 v",
                this.leftPos + DASH_X + 4, this.topPos + RATE_LABEL_Y, INK, false);
        double vmaxF = enzymeData.kcat() / KineticConstants.TIME_SCALE;
        double flux = menu.getFlux();
        double ratio = vmaxF > 0 ? Math.min(Math.abs(flux) / vmaxF, 1.0) : 0.0;
        graphics.fill(this.leftPos + DASH_X + 4, this.topPos + RATE_BAR_Y,
                this.leftPos + DASH_X + 4 + DASH_W - 14, this.topPos + RATE_BAR_Y + 6, BAR_TRACK);
        graphics.fill(this.leftPos + DASH_X + 4, this.topPos + RATE_BAR_Y,
                this.leftPos + DASH_X + 4 + (int) (ratio * (DASH_W - 14)), this.topPos + RATE_BAR_Y + 6, PURPLE);
        drawText(graphics, String.format("%.2f", flux),
                this.leftPos + DASH_X + 40, this.topPos + RATE_LABEL_Y, PURPLE, false);

        // 方向箭头
        String arrow;
        if (flux > 0.001) {
            arrow = ">>> 正向";
        } else if (flux < -0.001) {
            arrow = "<<< 逆向";
        } else {
            arrow = "≈ 平衡";
        }
        drawText(graphics, arrow,
                this.leftPos + DASH_X + 30, this.topPos + ARROW_Y, PURPLE, false);

        // 平衡条（紫白渐变贴图 + Keq 菱形指针 + Q 圆点指针）
        drawText(graphics, "平衡",
                this.leftPos + DASH_X + 4, this.topPos + BALANCE_LABEL_Y, INK, false);
        int barX = DASH_X + 4;
        int barW = 78;
        graphics.blit(BALANCE_BAR, this.leftPos + barX, this.topPos + BALANCE_BAR_Y, 0, 0, barW, 10, barW, 10);

        double keq = enzymeData.keq();
        double q = computeQ();
        int keqX = barX + (int) Math.round(barW * keq / (1.0 + keq)) - 4;
        int qX = barX + (int) Math.round(barW * q / (1.0 + q)) - 4;
        int pointY = BALANCE_BAR_Y + 1;
        graphics.blit(KEQ_POINT, this.leftPos + keqX, this.topPos + pointY, 0, 0, 9, 9, 9, 9);
        graphics.blit(Q_POINT, this.leftPos + qX, this.topPos + pointY, 0, 0, 9, 9, 9, 9);
        drawText(graphics, String.format("Q/Keq=%.2f", q / keq),
                this.leftPos + DASH_X + 22, this.topPos + QK_EQ_Y, GRAY_TEXT, false);

        // v-t 图（5 秒窗口，粒度秒）
        drawText(graphics, "v-t 图（5s）",
                this.leftPos + DASH_X + 4, this.topPos + VT_LABEL_Y, INK, false);
        drawVtChart(graphics);
    }

    /**
     * v-t 折线图：vHistory（100 tick = 5 秒）按 min..max 归一化绘制 + 秒刻度
     */
    private void drawVtChart(GuiGraphics graphics) {
        int chartW = DASH_W - 14;
        int chartX = DASH_X + 4;
        graphics.fill(this.leftPos + chartX, this.topPos + VT_Y,
                this.leftPos + chartX + chartW, this.topPos + VT_Y + VT_H, 0xFFFAFAF8);
        graphics.renderOutline(this.leftPos + chartX, this.topPos + VT_Y, chartW, VT_H, 0xFFCCCCCC);

        if (vHistoryCount < 2) {
            return;
        }
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int i = 0; i < vHistoryCount; i++) {
            min = Math.min(min, vHistory[i]);
            max = Math.max(max, vHistory[i]);
        }
        if (max == min) {
            max = min + 1;
        }
        for (int i = 1; i < vHistoryCount; i++) {
            int x1 = chartX + (i - 1) * chartW / (vHistoryCount - 1);
            int x2 = chartX + i * chartW / (vHistoryCount - 1);
            int y1 = VT_Y + VT_H - 1 - (vHistory[i - 1] - min) * (VT_H - 2) / (max - min);
            int y2 = VT_Y + VT_H - 1 - (vHistory[i] - min) * (VT_H - 2) / (max - min);
            drawLine(graphics, this.leftPos + x1, this.topPos + y1, this.leftPos + x2, this.topPos + y2, PURPLE);
        }
        for (int sec = 0; sec <= 5; sec++) {
            int tx = chartX + sec * chartW / 5;
            drawText(graphics, sec + "s",
                    this.leftPos + tx - 2, this.topPos + VT_Y + VT_H + 1, GRAY_TEXT, false);
        }
    }

    /**
     * 方程式的缩写渲染（含化学计量系数前缀，长度超宽截断）
     */
    private String renderEquation() {
        StringBuilder sb = new StringBuilder();
        appendSpeciesSide(sb, enzymeData.reactants());
        sb.append(' ').append(enzymeData.reversible() ? '⇌' : '→').append(' ');
        appendSpeciesSide(sb, enzymeData.products());
        String equation = sb.toString();
        return equation.length() > 21 ? equation.substring(0, 21) : equation;
    }

    /**
     * 拼装一侧物种：化学计量系数（>1 时前缀）+ 缩写，'+' 连接
     */
    private void appendSpeciesSide(StringBuilder sb, List<EnzymeFactoryData.SpeciesSpec> specs) {
        for (int i = 0; i < specs.size(); i++) {
            if (i > 0) {
                sb.append('+');
            }
            EnzymeFactoryData.SpeciesSpec spec = specs.get(i);
            if (spec.count() > 1) {
                sb.append(spec.count());
            }
            sb.append(itemBySpecies.get(spec.item()).getAbbreviation());
        }
    }

    /**
     * 计算当前浓度商 Q = ∏产物浓度^系数 / ∏底物浓度^系数（客户端派生，零流量）
     */
    private double computeQ() {
        ReactionDefinition definition = blockEntity.getSimulator().getDefinition();
        double numerator = 1.0;
        for (ReactionDefinition.SpeciesEntry entry : definition.getRateProducts()) {
            double c = this.menu.getSlot(entry.index()).getItem().getCount() / 64.0;
            numerator *= Math.pow(Math.max(c, 1e-9), entry.coeff());
        }
        double denominator = 1.0;
        for (ReactionDefinition.SpeciesEntry entry : definition.getRateReactants()) {
            double c = this.menu.getSlot(entry.index()).getItem().getCount() / 64.0;
            denominator *= Math.pow(Math.max(c, 1e-9), entry.coeff());
        }
        return numerator / denominator;
    }

    /**
     * 卡片框架：白底 + 灰描边（背包区不画——背景贴图已含）
     */
    private void drawCard(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(this.leftPos + x, this.topPos + y,
                this.leftPos + x + w, this.topPos + y + h, 0xFFFFFFFF);
        graphics.renderOutline(this.leftPos + x, this.topPos + y, w, h, CARD_BORDER);
    }

    /**
     * 绘制矢量字体文字（组件携带 biocraft:enzyme 字体 id，FontManager 已加载该字体）
     * <p>
     * 渲染走 Minecraft.font 的 fontFilter 按组件字体 id 查表——不能直接
     * drawString(String)（默认走位图 default 字体），必须用 withFont 组件
     */
    private void drawText(GuiGraphics graphics, String text, int x, int y, int color, boolean shadow) {
        graphics.drawString(this.font, Component.literal(text).withStyle(style -> style.withFont(ENZYME_FONT)),
                x, y, color, shadow);
    }

    /**
     * 矢量字体文字宽度（与 drawText 同字体，保证对齐一致）
     */
    private int textWidth(String text) {
        return this.font.width(Component.literal(text).withStyle(style -> style.withFont(ENZYME_FONT)));
    }

    /**
     * 槽位渲染覆写：全部槽位（物种槽 + 背包槽）统一用用户手绘 slot.png
     * <p>
     * 坐标语义（重要）：AbstractContainerScreen.render 在调用 renderSlot 前已执行
     * pose.translate(leftPos, topPos)——此处必须用相对坐标（slot.x 直接用），
     * 加 leftPos 会导致整体偏移 (leftPos, topPos)（曾踩坑：槽位贴图全部往右下偏移
     * 而交互判定不受影响，因 isHovering 用屏幕绝对坐标）
     * <p>
     * Slot 坐标 = 16×16 内容区左上角（用户定位点），槽位贴图 18×18（内容区 + 1px
     * 边框）blit 于 slot.x-1；物品图标 16×16 画于 slot.x 正好填满内容区
     */
    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        graphics.blit(SLOT, slot.x - 1, slot.y - 1, 0, 0, 18, 18, 18, 18);
        ItemStack stack = slot.getItem();
        if (!stack.isEmpty()) {
            // seed 用槽位位置（vanilla 同款），防止多个相同物品同帧闪烁
            graphics.renderItem(stack, slot.x, slot.y, slot.x + slot.y * this.imageWidth);
            // 数量文字与 IItemDecorator（G6P 缩写等）必须经 renderItemDecorations：
            // 其内部 z 提升 200，保证盖过物品（z=150）与槽贴图（z=0），
            // 手动 drawString 在 z=0 会被物品遮挡（曾踩坑：数量显示在物品贴图下方）
            graphics.renderItemDecorations(this.font, stack, slot.x, slot.y, null);
        }
    }

    /**
     * 画水平折线段（按 x 步进的整数插值）
     */
    private static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps == 0) {
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + dx * i / steps;
            int y = y1 + dy * i / steps;
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    /**
     * 不绘制 vanilla 标签（全部文字由 renderBg 自绘）
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 空实现
    }
}
