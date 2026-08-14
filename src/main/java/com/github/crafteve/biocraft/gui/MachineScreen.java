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
 *   <li>缩写文本框：左上角 (27,10)（由旧 (28,8) 对称变形：左右各扩 1px、
 *       上下各压缩 2px 保持中心）；高 12；宽 = 文字宽 + 左右各 2px 内边距
 *       + 各 1px 边框；1px 边框为主题色原色（不加深），填充为主题色向白
 *       混合 4/5（更浅），2px 圆角（程序化 L 形角）；文字用 vanilla 默认
 *       8px 位图字体上下居中，文字色 = 主题色加深</li>
 *   <li>displayname：文本框右缘 + 4px，纯黑文字；中文走 vanilla 默认字体
 *       （MC 自动回退 16px unicode 字形，非自定义字体）垂直居中于文本框，
 *       英文走 8px vanilla 字体与缩写文本同行</li>
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

    /**
     * 缩写文本框左上角 (27,10)：以旧 (28,8) 为中心，左右各扩 1px（x-1）
     * 上下各压缩 2px（y+2），保持中心不动的对称变形
     */
    private static final int ABBR_X = 27, ABBR_Y = 10;

    /** 缩写文本框高度（12px = 原 16px 上下各压缩 2px，8px 字体上下居中） */
    private static final int ABBR_H = 12;

    /** 缩写文本框内边距（文字左右各 2px = 原 1px 再加 1px） */
    private static final int ABBR_PAD = 2;

    /** 缩写文本框边框厚度（1px） */
    private static final int ABBR_BORDER = 1;

    /** 缩写文本框圆角半径（2px 圆角，角上 2×2 区域只画 L 形边框） */
    private static final int ABBR_CORNER = 2;

    /** displayname 与文本框右缘的间距（4px） */
    private static final int NAME_GAP = 4;

    /** displayname 文字颜色（纯黑） */
    private static final int NAME_COLOR = 0xFF000000;

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
    }

    /**
     * 标题区：方块物品图标 + 缩写文本框 + displayname
     * <p>
     * 主题色取自酶类别（MachineCategory），加深/变浅由线性混合推导：
     * 边框色 = 主题色原色（不加深）；缩写文字色 = 主题色 × 3/5（压暗）；
     * 填充色 = 主题色向白色混合 4/5（比之前更浅）
     * <p>
     * 文本框：1px 边框 + 填充 + 2px 圆角，程序化圆角——四角 2×2 区域
     * 只保留 L 形边框像素，无角尖；displayname 在文本框右缘 4px 处，
     * 纯黑文字，全程 vanilla 默认字体（中文由 MC 自动回退到 16px
     * unicode 字形，非自定义字体）：中文垂直居中于文本框，英文与
     * 文本框内缩写文本同 y
     *
     * @param graphics 渲染器
     */
    private void drawTitleArea(GuiGraphics graphics) {
        // 方块 3D 物品图标：16×16 标准物品图标尺寸，左上角 (8,8)
        ItemStack blockStack = new ItemStack(blockEntity.getBlockState().getBlock());
        graphics.renderItem(blockStack, this.leftPos + ITEM_X, this.topPos + ITEM_Y);

        // 缩写文本框：程序化 2px 圆角矩形（1px 边框 + 填充）
        String abbr = enzymeData.abbreviation();
        int theme = MachineCategory.byId(enzymeData.category()).getThemeColor();
        int borderColor = theme;
        int textColor = darken(theme);
        int fillColor = lighten(theme);
        int textW = this.font.width(abbr);
        int boxW = textW + (ABBR_PAD + ABBR_BORDER) * 2;
        int boxX = this.leftPos + ABBR_X;
        int boxY = this.topPos + ABBR_Y;
        drawRoundedBox(graphics, boxX, boxY, boxW, ABBR_H, ABBR_CORNER, borderColor, fillColor);

        // 缩写文字：vanilla 默认 8px 位图字体，12px 高内上下居中（y+2），
        // 左右于边框+内边距之后（x+3）——文字色用加深主题色（用户确认无误）
        // 注意 y 必须加 topPos（垂直偏移），误用 leftPos 会向下漂移（已修复）
        graphics.drawString(this.font, abbr,
                boxX + ABBR_BORDER + ABBR_PAD,
                boxY + (ABBR_H - 8) / 2, textColor, false);

        // displayname：文本框右缘 + 4px，纯黑文字；中文走 vanilla 默认字体
        // （MC 自动回退 16px unicode 字形），垂直居中于文本框（y = boxY-2），
        // 英文走 8px vanilla 字体与缩写文本同行（y = boxY+2）
        String language = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
        boolean chinese = language != null && language.startsWith("zh");
        String name = chinese ? enzymeData.nameZn() : enzymeData.nameEn();
        int nameX = boxX + boxW + NAME_GAP;
        int nameY = chinese ? boxY + (ABBR_H - 16) / 2 : boxY + (ABBR_H - 8) / 2;
        graphics.drawString(this.font, name, nameX, nameY, NAME_COLOR, false);
    }

    /**
     * 程序化圆角矩形（1px 边框 + 填充色，四角 2×2 圆角）
     * <p>
     * 无圆角贴图时的纯代码方案：边框画成"顶/底/左/右四条 + 四角 L 形"，
     * 角上 2×2 区域仅保留弧线边框像素，角尖留空露出背景——形成 2px 圆角
     * <p>
     * 注意四角必须全部补齐：早期版本漏画左下/右下 L 形，导致竖边下方
     * 缺失 1~2px 边框（实测 bug，已修复）
     *
     * @param graphics    渲染器
     * @param x           矩形左上角 x（屏幕坐标）
     * @param y           矩形左上角 y（屏幕坐标）
     * @param w           矩形宽
     * @param h           矩形高
     * @param corner      圆角半径
     * @param borderColor 边框颜色
     * @param fillColor   填充颜色
     */
    private static void drawRoundedBox(GuiGraphics graphics, int x, int y, int w, int h,
                                       int corner, int borderColor, int fillColor) {
        // 填充层：边框内 1px 缩进
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, fillColor);
        // 顶边与底边（左右各留 corner 缺口）
        graphics.fill(x + corner, y, x + w - corner, y + 1, borderColor);
        graphics.fill(x + corner, y + h - 1, x + w - corner, y + h, borderColor);
        // 左边与右边（上下各留 corner 缺口）
        graphics.fill(x, y + corner, x + 1, y + h - corner, borderColor);
        graphics.fill(x + w - 1, y + corner, x + w, y + h - corner, borderColor);
        // 四角 L 形边框（corner×corner 区域内只画弧线像素）：
        // 左上/右上各 2 像素（i 循环两轮），左下/右下各 3 像素
        // （竖边向底部延伸 corner 像素 + 底边向角延伸 corner-1 像素）
        for (int i = 0; i < corner; i++) {
            // 左上
            graphics.fill(x + corner - 1 - i, y + i, x + corner - i, y + i + 1, borderColor);
            graphics.fill(x + i, y + corner - 1 - i, x + i + 1, y + corner - i, borderColor);
            // 右上
            graphics.fill(x + w - corner + i, y + i, x + w - corner + 1 + i, y + i + 1, borderColor);
            graphics.fill(x + w - 1 - i, y + corner - 1 - i, x + w - i, y + corner - i, borderColor);
            // 左下（竖边向下延伸）
            graphics.fill(x, y + h - corner + i, x + 1, y + h - corner + 1 + i, borderColor);
            if (i < corner - 1) {
                // 左下（底边向左延伸）
                graphics.fill(x + 1 + i, y + h - 1, x + 2 + i, y + h, borderColor);
            }
            // 右下（竖边向下延伸）
            graphics.fill(x + w - 1, y + h - corner + i, x + w, y + h - corner + 1 + i, borderColor);
            if (i < corner - 1) {
                // 右下（底边向右延伸）
                graphics.fill(x + w - 2 - i, y + h - 1, x + w - 1 - i, y + h, borderColor);
            }
        }
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
