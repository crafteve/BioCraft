package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.EnzymeFactoryBlockEntity;
import com.github.crafteve.biocraft.blockentity.MachineCategory;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 酶工厂屏幕（experiment/gui-remake 分支全新重建）
 * <p>
 * 重建第一版（v1）：整张 gui_v1.png 作为 GUI 基底 1:1 blit，
 * 不做任何缩放与虚化；标题区（方块图标 + 缩写文本框）已就位，
 * 物种槽与仪表盘待逐项追加
 * <p>
 * GUI 画布尺寸与基底贴图一致（gui_v1.png 为 256×256）：
 * 画布左上角 = 贴图左上角，容器坐标（leftPos/topPos）即贴图 blit 原点
 * <p>
 * 标题区布局（GUI 内相对坐标）：
 * <ul>
 *   <li>方块物品图标：左上角 (8,8)，16×16（renderItem 标准物品图标尺寸）</li>
 *   <li>缩写文本框：1px 矩形框架（不倒圆角），左上角 (28,10)、下沿 y=21；
 *       宽 = 文字宽 + 左右各 2px 内边距 + 各 1px 边框；边框为主题色原色
 *       （补 alpha），填充为主题色向白混合 4/5；中轴线 15.5——框内文字与
 *       displayname 均以 15.5 为垂直中轴（8px 字形 y=12、16px 中文 y=8），
 *       全部绝对定位</li>
 *   <li>displayname：文本框右缘 + 4px，纯黑文字，vanilla 默认字体
 *       （中文由 MC 自动回退 16px unicode 字形）</li>
 *   <li>INPUT 标签：(9,30)；OUTPUT 标签：(195,30)，英文大写 8px 纯黑</li>
 * </ul>
 * 字体约定：全程使用 Minecraft 自带字体（含中文的 unicode 自动回退），
 * 不加载任何自定义 TTF 字体资源
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    /** GUI 基底贴图（用户手绘，256×256，含背包区视觉） */
    private static final ResourceLocation GUI_BG =
            ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "textures/gui/gui_v1.png");

    /** GUI 画布宽 = 基底贴图宽（1:1 blit，杜绝缩放虚化） */
    private static final int GUI_W = 256;

    /** GUI 画布高 = 基底贴图高 */
    private static final int GUI_H = 256;

    /** 方块物品图标左上角（16×16 标准物品图标） */
    private static final int ITEM_X = 8, ITEM_Y = 8;

    /** 缩写文本框左上角 (28,10)，左下/右侧下沿 y=21（1px 矩形框架，不倒圆角） */
    private static final int ABBR_X = 28, ABBR_Y = 10;

    /** 文本框 y 范围 10~21（11px），中轴线 = (10+21)/2 = 15.5 */
    private static final int ABBR_Y_BOTTOM = 21;

    /** 缩写文本框内边距（文字左右各 2px） */
    private static final int ABBR_PAD = 2;

    /** 缩写文本框边框厚度（1px） */
    private static final int ABBR_BORDER = 1;

    /** displayname 与文本框右缘的间距（4px） */
    private static final int NAME_GAP = 4;

    /** displayname 文字颜色（纯黑） */
    private static final int NAME_COLOR = 0xFF000000;

    /** INPUT 标签左上角（英文大写，vanilla 8px 字体） */
    private static final int INPUT_X = 9, INPUT_Y = 30;

    /** OUTPUT 标签左上角 */
    private static final int OUTPUT_X = 195, OUTPUT_Y = 30;

    // 滚动卡片容器约束（JSON 数据驱动布局的样板：容器/卡片/间距/颜色全部为约束值）
    /** 滚动容器左上角 (7,41)，区域 y 41~162，宽 56 */
    private static final int SCROLL_X = 7, SCROLL_Y = 41, SCROLL_W = 56, SCROLL_H = 121;

    /** 卡片尺寸 56×24，间距 4，卡片色 #c6c6c6（0xC6C6C6 24 位 RGB，补 alpha 使用） */
    private static final int CARD_W = 56, CARD_H = 24, CARD_GAP = 4;

    /** 卡片步进（高 + 间距） */
    private static final int CARD_STEP = CARD_H + CARD_GAP;

    /** 视口内完整可见卡片数（121/28 = 4 张，余 9px） */
    private static final int VISIBLE_CARDS = 4;

    /** 测试占位卡片数（滚动机制演示用，将来由酶数据条目数驱动） */
    private static final int CARD_COUNT = 10;

    /** 卡片颜色（#c6c6c6 补 alpha） */
    private static final int CARD_COLOR = 0xFFC6C6C6;

    /** 卡片编号文字颜色（测试占位用） */
    private static final int CARD_TEXT_COLOR = 0xFF333333;

    /** 当前滚动卡片张数（0 = 顶部，滚轮逐张步进，越界钳制） */
    private int scrollCards;

    private final EnzymeFactoryBlockEntity blockEntity;
    private final EnzymeFactoryData enzymeData;

    /**
     * @param menu            菜单实例
     * @param playerInventory 玩家物品栏
     * @param title           窗口标题
     */
    public MachineScreen(MachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_W;
        this.imageHeight = GUI_H;
        this.blockEntity = menu.getBlockEntity();
        this.enzymeData = menu.getEnzymeData();
    }

    /**
     * 渲染入口：super（背景 + 槽位 + 物品）+ 悬停物品 tooltip
     * <p>
     * 1.21.1 的 AbstractContainerScreen.render 不再渲染悬停槽位 tooltip，
     * 必须由子类显式调用 renderTooltip（见 AGENTS.md 欠账 13）
     *
     * @param graphics    渲染器
     * @param mouseX      鼠标 x（屏幕坐标）
     * @param mouseY      鼠标 y（屏幕坐标）
     * @param partialTick 部分 tick（渲染插值）
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * 主画布：基底贴图 1:1 blit + 标题区（方块图标 + 缩写文本框）
     * <p>
     * 注意 renderBg 阶段尚未平移 leftPos/topPos，所有坐标必须加容器偏移
     * （与 renderSlot 的相对坐标语义不同）
     *
     * @param graphics    渲染器
     * @param partialTick 部分 tick
     * @param mouseX      鼠标 x
     * @param mouseY      鼠标 y
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(GUI_BG, this.leftPos, this.topPos, 0, 0, GUI_W, GUI_H, GUI_W, GUI_H);
        drawTitleArea(graphics);
        drawScrollCards(graphics);
    }

    /**
     * 滚动卡片元素：视口 (7,41)~(63,162) 内的 #c6c6c6 卡片列表
     * <p>
     * 滚动机制：
     * <ul>
     *   <li>卡片 56×24、间距 4，纵向按 28px 步进排列</li>
     *   <li>enableScissor 裁剪视口——超出视口上/下边界的卡片部分被裁掉，
     *       即"上方卡片消失、下方卡片出现"的滚动视觉</li>
     *   <li>scrollCards 为滚动张数（滚轮逐张步进），钳制 [0, 总数-可见数]，
     *       数据不足一屏时不滚动</li>
     * </ul>
     * 卡片内容当前为编号占位（测试滚动机制用），将来由酶数据条目驱动
     *
     * @param graphics 渲染器
     */
    private void drawScrollCards(GuiGraphics graphics) {
        int x = this.leftPos + SCROLL_X;
        int y = this.topPos + SCROLL_Y;
        // 视口裁剪：仅 (x, y)~(x+56, y+121) 内可见
        graphics.enableScissor(x, y, x + SCROLL_W, y + SCROLL_H);
        for (int i = 0; i < CARD_COUNT; i++) {
            int cardY = y + i * CARD_STEP - scrollCards * CARD_STEP;
            graphics.fill(x, cardY, x + CARD_W, cardY + CARD_H, CARD_COLOR);
            // 编号文字：卡片 24px 高内 8px 字上下居中（cardY+8）
            graphics.drawString(this.font, String.format("%02d", i + 1),
                    x + 2, cardY + 8, CARD_TEXT_COLOR, false);
        }
        graphics.disableScissor();
    }

    /**
     * 滚轮事件：悬停在滚动卡片视口内时接管滚轮，逐张滚动卡片
     * <p>
     * 悬停判定用屏幕坐标减去容器偏移还原为 GUI 相对坐标；
     * 滚轮向上（delta>0）看更上方的卡片，向下看更下方
     *
     * @param mouseX      鼠标 x（屏幕坐标）
     * @param mouseY      鼠标 y（屏幕坐标）
     * @param horizontalAmount 水平滚轮增量（本元素不使用）
     * @param verticalAmount   垂直滚轮增量（向上为正）
     * @return 是否消费事件
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        int localX = (int) mouseX - this.leftPos;
        int localY = (int) mouseY - this.topPos;
        if (localX >= SCROLL_X && localX < SCROLL_X + SCROLL_W
                && localY >= SCROLL_Y && localY < SCROLL_Y + SCROLL_H) {
            int delta = verticalAmount > 0 ? -1 : 1;
            this.scrollCards = Math.max(0,
                    Math.min(scrollCards + delta, CARD_COUNT - VISIBLE_CARDS));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /**
     * 标题区：方块物品图标 + 缩写文本框 + displayname + INPUT/OUTPUT 标签
     * <p>
     * 主题色取自酶类别（MachineCategory），加深/变浅由线性混合推导：
     * 边框色 = 主题色原色（补 alpha，不加深）；缩写文字色 = 主题色 × 3/5；
     * 填充色 = 主题色向白色混合 4/5（浅）
     * <p>
     * 文本框：1px 矩形框架（不倒圆角），左上 (28,10)、下沿 y=21，
     * 中轴线 15.5——框内文字与 displayname 均以 15.5 为垂直中轴：
     * 8px 字形中心 = y+3.5 → y = 12；16px 中文（MC 自动回退 unicode，
     * 超出框范围无视）中心 = y+8 → y = 8；均为绝对定位
     *
     * @param graphics 渲染器
     */
    private void drawTitleArea(GuiGraphics graphics) {
        // 方块 3D 物品图标：16×16 标准物品图标尺寸，左上角 (8,8)
        ItemStack blockStack = new ItemStack(blockEntity.getBlockState().getBlock());
        graphics.renderItem(blockStack, this.leftPos + ITEM_X, this.topPos + ITEM_Y);

        // 缩写文本框：1px 矩形框架（无圆角），y 范围 10~21
        String abbr = enzymeData.abbreviation();
        int theme = MachineCategory.byId(enzymeData.category()).getThemeColor();
        // 边框色必须补 alpha：MachineCategory 主题色是 24 位 RGB（alpha=0），
        // 直接 fill 会画出全透明矩形导致边框"直接消失"（实测 bug，已修复）
        int borderColor = theme | 0xFF000000;
        int textColor = darken(theme);
        int fillColor = lighten(theme);
        int textW = this.font.width(abbr);
        int boxW = textW + (ABBR_PAD + ABBR_BORDER) * 2;
        int boxX = this.leftPos + ABBR_X;
        int boxY = this.topPos + ABBR_Y;
        int boxY2 = this.topPos + ABBR_Y_BOTTOM + 1;
        graphics.fill(boxX, boxY, boxX + boxW, boxY2, borderColor);
        graphics.fill(boxX + ABBR_BORDER, boxY + ABBR_BORDER,
                boxX + boxW - ABBR_BORDER, boxY2 - ABBR_BORDER, fillColor);

        // 缩写文字：vanilla 默认 8px 位图字体，绝对定位（不写居中公式）：
        // 中轴线 15.5（y 范围 10~21），8px 字形中心 = y+3.5 → y = boxY + 2
        // 左右于边框+内边距之后（x+3），文字色用加深主题色
        graphics.drawString(this.font, abbr,
                boxX + ABBR_BORDER + ABBR_PAD,
                boxY + 2, textColor, false);

        // displayname：文本框右缘 + 4px，纯黑文字，绝对定位：
        // 中文与英文统一按 8px 处理（实测 MC 中文渲染也是 8px 高，非 16px），
        // 与缩写文本同中轴（y = boxY + 2）
        String language = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
        boolean chinese = language != null && language.startsWith("zh");
        String name = chinese ? enzymeData.nameZn() : enzymeData.nameEn();
        int nameX = boxX + boxW + NAME_GAP;
        int nameY = boxY + 2;
        graphics.drawString(this.font, name, nameX, nameY, NAME_COLOR, false);

        // INPUT / OUTPUT 标签：英文大写，vanilla 8px 字体，纯黑
        graphics.drawString(this.font, "INPUT", this.leftPos + INPUT_X, this.topPos + INPUT_Y, NAME_COLOR, false);
        graphics.drawString(this.font, "OUTPUT", this.leftPos + OUTPUT_X, this.topPos + OUTPUT_Y, NAME_COLOR, false);
    }

    /**
     * 颜色压暗（乘以 3/5 线性系数）
     *
     * @param color ARGB 颜色
     * @return 压暗后的 ARGB 颜色（alpha 保留）
     */
    private static int darken(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return 0xFF000000 | (r * 3 / 5 << 16) | (g * 3 / 5 << 8) | (b * 3 / 5);
    }

    /**
     * 颜色提亮（向白色混合 4/5，比早期 3/5 更浅）
     *
     * @param color ARGB 颜色
     * @return 提亮后的 ARGB 颜色（alpha 保留）
     */
    private static int lighten(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return 0xFF000000 | ((r + (255 - r) * 4 / 5) << 16)
                | ((g + (255 - g) * 4 / 5) << 8) | (b + (255 - b) * 4 / 5);
    }

    /**
     * 不绘制 vanilla 标签（全部文字由 renderBg 自绘）
     *
     * @param graphics 渲染器
     * @param mouseX   鼠标 x
     * @param mouseY   鼠标 y
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 空实现
    }
}
