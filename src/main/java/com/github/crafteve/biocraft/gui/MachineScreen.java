package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.EnzymeFactoryBlockEntity;
import com.github.crafteve.biocraft.blockentity.MachineCategory;
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
 * 酶工厂仪表盘屏幕（248×360 卡片式单页布局）
 * <p>
 * 五张卡片：
 * <ul>
 *   <li>标题卡：方块 3D 贴图 + 大类名（中/英）+ 紫框缩写 + 中英全名 + T/P/pH 环境框 + 状态灯</li>
 *   <li>输入卡（左）/ 输出卡（右）：物种子卡列表（浅色槽位 + 缩写 + 数量 + 浓度条，
 *       悬停槽位显示物品 tooltip）</li>
 *   <li>仪表盘（中）：反应方程式 + 净速率条 + 方向箭头 + 平衡条（紫白渐变 +
 *       Keq 菱形指针 + Q 圆点指针）+ v-t 折线图（5 秒窗口）+ 停摆红字</li>
 *   <li>背包卡（底，居中）：标准 vanilla 槽位渲染 4×9</li>
 * </ul>
 * 全部视觉 GuiGraphics 自绘（纸白学术风，主题紫），仅槽位/平衡条/指针用贴图资产；
 * 槽位浅色化通过覆写 renderSlot 实现（物种槽用自绘贴图，背包槽保持 vanilla）
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    // 贴图资产
    private static final ResourceLocation SLOT_LIGHT = ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "textures/gui/slot_light.png");
    private static final ResourceLocation BALANCE_BAR = ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "textures/gui/balance_bar.png");
    private static final ResourceLocation KEQ_POINT = ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "textures/gui/keq_point.png");
    private static final ResourceLocation Q_POINT = ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "textures/gui/q_point.png");

    // 主题色（纸白学术风）
    private static final int PAPER_WHITE = 0xFFF7F5F0;
    private static final int CARD_WHITE = 0xFFFFFFFF;
    private static final int INK = 0xFF1A1A1A;
    private static final int GRAY_TEXT = 0xFF777777;
    private static final int CARD_BORDER = 0xFF888888;
    private static final int SUB_CARD_BORDER = 0xFFBBBBBB;
    private static final int SUB_CARD_BG = 0xFFFAFAF8;
    private static final int PURPLE = 0xFF7050B0;
    private static final int PURPLE_LIGHT = 0xFF9060D0;
    private static final int PURPLE_DARK = 0xFF503080;
    private static final int PURPLE_TAG_BG = 0xFFE8E0F0;
    private static final int DANGER_RED = 0xFFD7252F;
    private static final int GREEN_OK = 0xFF40C040;
    private static final int BAR_TRACK = 0xFFE0E0E0;
    private static final int ENV_BG = 0xFFF0F0F0;
    private static final int ENV_BORDER = 0xFFAAAAAA;

    // 布局（GUI 坐标）
    private static final int GUI_W = 248;
    private static final int GUI_H = 360;
    private static final int TITLE_X = 8, TITLE_Y = 8, TITLE_W = 232, TITLE_H = 44;
    private static final int SIDE_CARD_X = 8, OUTPUT_CARD_X = 174, SIDE_CARD_W = 66;
    private static final int DASH_X = 78, DASH_W = 92;
    private static final int CARD_Y = 60, CARD_H = 164;
    private static final int SPECIES_Y0 = 82, SPECIES_GAP = 42;
    private static final int INV_Y = 232, INV_H = 120;

    // 仪表盘元素
    private static final int EQ_Y = 72;
    private static final int RATE_LABEL_Y = 94;
    private static final int RATE_BAR_Y = 99;
    private static final int ARROW_Y = 114;
    private static final int BALANCE_LABEL_Y = 128;
    private static final int BALANCE_BAR_Y = 134;
    private static final int QK_EQ_Y = 150;
    private static final int VT_LABEL_Y = 164;
    private static final int VT_Y = 170, VT_H = 22;
    private static final int STALL_Y = 214;

    private final EnzymeFactoryBlockEntity blockEntity;
    private final EnzymeFactoryData enzymeData;
    private final MachineCategory category;
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
        this.category = MachineCategory.byId(enzymeData.category());

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
     * <p>
     * AbstractContainerScreen.tick 为 final 方法，必须覆写 containerTick
     * （tick 内部调用）实现每 tick 逻辑
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
     * <p>
     * 1.21.1 机制（AGENTS.md 13 条）：AbstractContainerScreen.render 不渲染
     * hoveredSlot 的 tooltip，必须子类显式调用 renderTooltip
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * 主画布：纸白背景 + 五张卡片 + 全部仪表元素
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 纸白背景
        graphics.fill(0, 0, this.width, this.height, PAPER_WHITE);

        drawTitleCard(graphics);
        drawSpeciesCard(graphics, SIDE_CARD_X, "输入（底物）", 0, enzymeData.reactants().size());
        drawSpeciesCard(graphics, OUTPUT_CARD_X, "输出（产物）", enzymeData.reactants().size(),
                enzymeData.reactants().size() + enzymeData.products().size());
        drawDashboard(graphics);
        drawInventoryTitle(graphics);
    }

    /**
     * 标题卡：方块 3D 贴图 + 大类名（中/英）+ 紫框缩写 + 中英全名 + T/P/pH + 状态灯
     */
    private void drawTitleCard(GuiGraphics graphics) {
        drawCard(graphics, TITLE_X, TITLE_Y, TITLE_W, TITLE_H);

        // 方块 3D 贴图（物品渲染，含立体模型）
        ItemStack blockStack = new ItemStack(blockEntity.getBlockState().getBlock());
        graphics.renderItem(blockStack, this.leftPos + TITLE_X + 4, this.topPos + TITLE_Y + 4);

        // 大类名（中上英下）
        graphics.drawString(this.font, category.getDisplayName() + "工厂",
                this.leftPos + TITLE_X + 25, this.topPos + TITLE_Y + 9, INK, false);
        graphics.drawString(this.font, categoryEn(category) + " FACTORY",
                this.leftPos + TITLE_X + 25, this.topPos + TITLE_Y + 24, GRAY_TEXT, false);

        // 紫框缩写
        int abbrX = TITLE_X + 88;
        graphics.fill(this.leftPos + abbrX, this.topPos + TITLE_Y + 8,
                this.leftPos + abbrX + 30, this.topPos + TITLE_Y + 28, PURPLE_TAG_BG);
        graphics.renderOutline(this.leftPos + abbrX, this.topPos + TITLE_Y + 8, 30, 20, PURPLE);
        graphics.drawString(this.font, enzymeData.abbreviation(),
                this.leftPos + abbrX + 4, this.topPos + TITLE_Y + 13, PURPLE, true);

        // 中英全名
        int nameX = abbrX + 36;
        graphics.drawString(this.font, enzymeData.nameZn(),
                this.leftPos + nameX, this.topPos + TITLE_Y + 9, INK, false);
        graphics.drawString(this.font, enzymeData.nameEn(),
                this.leftPos + nameX, this.topPos + TITLE_Y + 24, GRAY_TEXT, false);

        // T/P/pH 环境框（右上）
        int envX = TITLE_X + TITLE_W - 58;
        graphics.fill(this.leftPos + envX, this.topPos + TITLE_Y + 6,
                this.leftPos + envX + 54, this.topPos + TITLE_Y + 38, ENV_BG);
        graphics.renderOutline(this.leftPos + envX, this.topPos + TITLE_Y + 6, 54, 32, ENV_BORDER);
        graphics.drawString(this.font, "T " + (int) Math.round(menu.getTemperature()) + "K",
                this.leftPos + envX + 4, this.topPos + TITLE_Y + 12, INK, false);
        graphics.drawString(this.font, "P 1.00 atm",
                this.leftPos + envX + 4, this.topPos + TITLE_Y + 21, INK, false);
        graphics.drawString(this.font, "pH 7.00",
                this.leftPos + envX + 4, this.topPos + TITLE_Y + 30, INK, false);

        // 状态灯（环境框下方左侧）
        int statusColor = menu.getStallCode() == 0 ? GREEN_OK : DANGER_RED;
        String statusText = menu.getStallCode() == 0 ? "正常运行" : "停摆";
        graphics.drawString(this.font, statusText,
                this.leftPos + TITLE_X + 4, this.topPos + TITLE_Y + 37, statusColor, false);
    }

    /**
     * 输入/输出卡：卡片 + 每物种子卡（槽位由 renderSlot 覆写画浅色背景）
     *
     * @param graphics  绘制上下文
     * @param cardX     卡片 x
     * @param title     卡片标题
     * @param slotStart 槽位起点（输入 0 / 输出 reactants.size()）
     * @param slotEnd   槽位终点
     */
    private void drawSpeciesCard(GuiGraphics graphics, int cardX, String title, int slotStart, int slotEnd) {
        drawCard(graphics, cardX, CARD_Y, SIDE_CARD_W, CARD_H);
        graphics.drawString(this.font, title,
                this.leftPos + cardX + 2, this.topPos + CARD_Y + 4, INK, false);

        int row = 0;
        for (int slotIndex = slotStart; slotIndex < slotEnd; slotIndex++) {
            int subY = SPECIES_Y0 + row * SPECIES_GAP;
            int subX = cardX + 2;
            // 子卡背景
            graphics.fill(this.leftPos + subX, this.topPos + subY - 4,
                    this.leftPos + subX + SIDE_CARD_W - 4, this.topPos + subY + 34, SUB_CARD_BG);
            graphics.renderOutline(this.leftPos + subX, this.topPos + subY - 4,
                    SIDE_CARD_W - 4, 38, SUB_CARD_BORDER);

            String speciesId = speciesIds.get(slotIndex);
            MoleculeItem item = itemBySpecies.get(speciesId);
            ItemStack stack = this.menu.getSlot(slotIndex).getItem();

            // 缩写
            graphics.drawString(this.font, item.getAbbreviation(),
                    this.leftPos + subX + 24, this.topPos + subY + 2, INK, true);
            // 数量
            graphics.drawString(this.font, "×" + stack.getCount(),
                    this.leftPos + subX + 24, this.topPos + subY + 13, GRAY_TEXT, false);
            // 浓度条（数量 + 引擎余量，语义色 = 物品染色）
            double concentration = (stack.getCount() + blockEntity.getRemainder(slotIndex)) / 64.0;
            int barColor = 0xFF000000 | item.getTintColor();
            graphics.fill(this.leftPos + subX + 24, this.topPos + subY + 22,
                    this.leftPos + subX + 56, this.topPos + subY + 28, BAR_TRACK);
            graphics.fill(this.leftPos + subX + 24, this.topPos + subY + 22,
                    this.leftPos + subX + 24 + (int) (32 * Math.min(concentration, 1.0)), this.topPos + subY + 28, barColor);
            row++;
        }
    }

    /**
     * 仪表盘：方程式 + 速率条 + 方向箭头 + 平衡条双指针 + v-t 图 + 停摆红字
     */
    private void drawDashboard(GuiGraphics graphics) {
        drawCard(graphics, DASH_X, CARD_Y, DASH_W, CARD_H);

        // 反应方程式（缩写渲染，⇌ 自绘双箭头避免字体缺字）
        String equation = renderEquation();
        graphics.drawString(this.font, equation,
                this.leftPos + DASH_X + 4, this.topPos + EQ_Y, PURPLE, true);
        graphics.drawString(this.font, enzymeData.reversible() ? "可逆反应" : "不可逆反应",
                this.leftPos + DASH_X + 30, this.topPos + EQ_Y + 11, GRAY_TEXT, false);

        // 净速率条
        graphics.drawString(this.font, "净速率 v",
                this.leftPos + DASH_X + 4, this.topPos + RATE_LABEL_Y, INK, false);
        double vmaxF = enzymeData.kcat() / KineticConstants.TIME_SCALE;
        double flux = menu.getFlux();
        double ratio = vmaxF > 0 ? Math.min(Math.abs(flux) / vmaxF, 1.0) : 0.0;
        graphics.fill(this.leftPos + DASH_X + 4, this.topPos + RATE_BAR_Y,
                this.leftPos + DASH_X + 4 + DASH_W - 14, this.topPos + RATE_BAR_Y + 6, BAR_TRACK);
        graphics.fill(this.leftPos + DASH_X + 4, this.topPos + RATE_BAR_Y,
                this.leftPos + DASH_X + 4 + (int) (ratio * (DASH_W - 14)), this.topPos + RATE_BAR_Y + 6, PURPLE);
        graphics.drawString(this.font, String.format("%.2f", flux),
                this.leftPos + DASH_X + 40, this.topPos + RATE_LABEL_Y, PURPLE, true);

        // 方向箭头
        String arrow;
        if (flux > 0.001) {
            arrow = ">>> 正向";
        } else if (flux < -0.001) {
            arrow = "<<< 逆向";
        } else {
            arrow = "≈ 平衡";
        }
        graphics.drawString(this.font, arrow,
                this.leftPos + DASH_X + 30, this.topPos + ARROW_Y, PURPLE, true);

        // 平衡条（紫白渐变贴图 + Keq 菱形指针 + Q 圆点指针）
        graphics.drawString(this.font, "平衡",
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
        graphics.drawString(this.font, String.format("Q/Keq=%.2f", q / keq),
                this.leftPos + DASH_X + 22, this.topPos + QK_EQ_Y, GRAY_TEXT, false);

        // v-t 图（5 秒窗口，粒度秒）
        graphics.drawString(this.font, "v-t 图（5s）",
                this.leftPos + DASH_X + 4, this.topPos + VT_LABEL_Y, INK, false);
        drawVtChart(graphics);

        // 停摆红字
        if (menu.getStallCode() == 1) {
            graphics.drawString(this.font, enzymeData.stallMessage(),
                    this.leftPos + DASH_X + 4, this.topPos + STALL_Y, DANGER_RED, false);
        }
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
        // 秒刻度（每 20 tick = 1 秒）
        for (int sec = 0; sec <= 5; sec++) {
            int tx = chartX + sec * chartW / 5;
            graphics.drawString(this.font, sec + "s",
                    this.leftPos + tx - 2, this.topPos + VT_Y + VT_H + 1, GRAY_TEXT, false);
        }
    }

    /**
     * 方程式的缩写渲染（长度超宽时截断）
     * <p>
     * 含化学计量系数（系数 > 1 时前缀）与固定活性物种（H₂O/H⁺），
     * 显示逻辑集中在客户端，引擎不再提供反渲染字符串
     */
    private String renderEquation() {
        StringBuilder sb = new StringBuilder();
        appendSpeciesSide(sb, enzymeData.reactants());
        sb.append(' ').append(enzymeData.reversible() ? '⇌' : '→').append(' ');
        appendSpeciesSide(sb, enzymeData.products());
        String equation = sb.toString();
        // 仪表盘宽 92，7px 字体下每字符约 4px，超 21 字符截断
        return equation.length() > 21 ? equation.substring(0, 21) : equation;
    }

    /**
     * 拼装一侧物种：化学计量系数（>1 时前缀）+ 缩写，'+' 连接
     *
     * @param sb    目标字符串构建器
     * @param specs 物种条目列表（反应物或产物）
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
     * 计算当前浓度商 Q = ∏产物浓度^系数 / ∏底物浓度^系数
     * <p>
     * 仅用速率项物种（固定活性 H₂O/H⁺ 不参与平衡式），
     * 浓度从槽位 count/64 派生（客户端本地，零流量）
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
     * 卡片背景：白底 + 灰描边
     */
    private void drawCard(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(this.leftPos + x, this.topPos + y,
                this.leftPos + x + w, this.topPos + y + h, CARD_WHITE);
        graphics.renderOutline(this.leftPos + x, this.topPos + y, w, h, CARD_BORDER);
    }

    /**
     * 背包卡标题（槽位本身由 vanilla renderSlot 渲染）
     */
    private void drawInventoryTitle(GuiGraphics graphics) {
        graphics.fill(this.leftPos + TITLE_X, this.topPos + INV_Y,
                this.leftPos + TITLE_X + TITLE_W, this.topPos + INV_Y + INV_H, CARD_WHITE);
        graphics.renderOutline(this.leftPos + TITLE_X, this.topPos + INV_Y, TITLE_W, INV_H, CARD_BORDER);
        graphics.drawString(this.font, "背包物品栏",
                this.leftPos + TITLE_X + 4, this.topPos + INV_Y + 4, INK, false);
    }

    /**
     * 槽位渲染覆写：物种槽用浅色贴图背景，背包槽保持 vanilla
     * <p>
     * 物种槽背景 = slot_light（浅色柔和），物品与数量由 renderSlotContents 绘制
     */
    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        int speciesCount = menu.getSpeciesSlotCount();
        if (slot.index < speciesCount) {
            graphics.blit(SLOT_LIGHT, this.leftPos + slot.x - 1, this.topPos + slot.y - 1,
                    0, 0, 18, 18, 18, 18);
            renderSlotContents(graphics, slot.getItem(), slot, null);
        } else {
            super.renderSlot(graphics, slot);
        }
    }

    /**
     * 大类英文名（标题卡副标题）
     */
    private static String categoryEn(MachineCategory category) {
        return switch (category) {
            case EC1 -> "OXIDOREDUCTASE";
            case EC2 -> "TRANSFERASE";
            case EC3 -> "HYDROLASE";
            case EC4 -> "LYASE";
            case EC5 -> "ISOMERASE";
            case EC6 -> "LIGASE";
            default -> "MACHINE";
        };
    }

    /**
     * 画水平折线段（Bresenham 简化版：按 x 步进的整数插值）
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
     * 不绘制 vanilla 标签（标题卡已自绘，避免"物品栏"等默认文字叠加）
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 空实现：全部文字由 renderBg 自绘
    }
}
