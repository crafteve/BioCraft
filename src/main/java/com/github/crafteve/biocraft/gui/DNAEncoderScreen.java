package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.DNAEncoderBlockEntity;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.item.MoleculeItem;
import com.github.crafteve.biocraft.network.ServerboundDnaSequencePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * DNA 编码器屏幕（缓冲池版 v2）
 * <p>
 * 布局分区（背景贴图坐标，相对 GUI 左上角）：
 * <ul>
 *   <li>按钮区 y=16：启动子/终止子按钮 + 右侧序列文本框（EditBox）</li>
 *   <li>按钮区 y=38：ATCG 碱基按钮（点击向文本框光标处插入字符）+ 合成按钮</li>
 *   <li>原料区 y=62：四根竖向缓冲进度条（颜色与对应碱基物品染色一致，
 *       悬停显示 库存/4096；悬停提示经 1.21.1 官方延迟 tooltip 机制
 *       setTooltipForNextRenderPass 注册，由 renderWithTooltip 统一绘制）</li>
 *   <li>原料区 y=106：四个碱基吸收槽（放入即吸收进缓冲池）</li>
 *   <li>输出槽 (134,62)：合成产物 DNA模板</li>
 * </ul>
 * 合成交互：点击直接发包（不做客户端预校验），服务端权威校验并以
 * actionbar 反馈失败原因，保证"点击必有响应"
 */
public class DNAEncoderScreen extends AbstractContainerScreen<DNAEncoderMenu> {
    /** GUI 背景贴图（256×256，实际绘制区域 176×206） */
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "textures/gui/dna_encoder.png");

    /** 碱基字母序列（与缓冲池索引一致：0=A 1=T 2=C 3=G） */
    private static final String BASE_CHARS = "ATCG";

    /** 四条进度条的 X 坐标（与碱基槽同列，宽 12 居中于 18 宽槽位上方） */
    private static final int[] BAR_X = {29, 47, 65, 83};
    /** 进度条区域 Y 与宽高（含 1px 边框的轨道从 x-1,y-1 起，填充区见常量） */
    private static final int BAR_Y = 62;
    private static final int BAR_W = 12;
    private static final int BAR_H = 34;

    /**
     * 进度条填充颜色：直接取对应碱基物品的染色值（与物品图标颜色一致）
     * <p>
     * 此前硬编码四色与物品实际颜色（如胸腺嘧啶紫色 vs 进度条红色）不协调，
     * 现改为读取 MoleculeItem.getTintColor，视觉上永远与碱基物品统一
     */
    private static final int[] BAR_COLORS = {
            baseColor("adenine"),
            baseColor("thymine"),
            baseColor("cytosine"),
            baseColor("guanine")
    };

    /** 序列输入框 */
    private EditBox sequenceBox;

    /**
     * @param menu             菜单实例
     * @param playerInventory  玩家物品栏
     * @param title            窗口标题
     */
    public DNAEncoderScreen(DNAEncoderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 206;
    }

    /**
     * 获取碱基物品的染色值（进度条填充色）
     *
     * @param substanceId 物质表 id
     * @return ARGB 颜色值
     */
    private static int baseColor(String substanceId) {
        MoleculeItem item = ModItems.byId(substanceId).get();
        return 0xFF000000 | item.getTintColor();
    }

    /**
     * 初始化界面组件：文本框与全部按钮
     * <p>
     * 组件坐标基于 guiLeft/guiTop（背景图左上角），与 Menu 槽位坐标同源；
     * 按钮均为 vanilla 组件，皮肤由游戏内置，背景贴图无需绘制按钮区域
     */
    @Override
    protected void init() {
        super.init();

        // 序列文本框：右侧，过滤非法字符（仅 A/T/C/G），长度上限与合成校验一致
        this.sequenceBox = new EditBox(this.font, this.leftPos + 116, this.topPos + 16, 52, 16,
                Component.translatable("gui.biocraft.sequence_label"));
        this.sequenceBox.setFilter(text -> text.chars().allMatch(c -> "ACGT".indexOf(c) >= 0));
        this.sequenceBox.setMaxLength(DNAEncoderBlockEntity.MAX_SEQUENCE_LENGTH);

        // 按钮区行 1：启动子 / 终止子（向文本框光标处插入序列片段）
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.biocraft.button.promoter"),
                        button -> insertSequence("TATAAT"))
                .bounds(this.leftPos + 8, this.topPos + 16, 50, 16)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.biocraft.button.terminator"),
                        button -> insertSequence("TTTTTT"))
                .bounds(this.leftPos + 62, this.topPos + 16, 50, 16)
                .build());

        // 按钮区行 2：四个碱基按钮（点击追加单字符）+ 合成按钮
        this.addRenderableWidget(Button.builder(Component.literal("A"), button -> insertSequence("A"))
                .bounds(this.leftPos + 8, this.topPos + 38, 22, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("T"), button -> insertSequence("T"))
                .bounds(this.leftPos + 33, this.topPos + 38, 22, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("C"), button -> insertSequence("C"))
                .bounds(this.leftPos + 58, this.topPos + 38, 22, 16).build());
        this.addRenderableWidget(Button.builder(Component.literal("G"), button -> insertSequence("G"))
                .bounds(this.leftPos + 83, this.topPos + 38, 22, 16).build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.biocraft.synthesize"),
                        button -> onSynthesizePressed())
                .bounds(this.leftPos + 116, this.topPos + 38, 52, 16)
                .build());
        this.addRenderableWidget(this.sequenceBox);
    }

    /**
     * 向文本框光标处插入序列文本（按钮共同行为）
     *
     * @param text 待插入的片段（碱基按钮为单字符，启动子/终止子为预设序列）
     */
    private void insertSequence(String text) {
        this.sequenceBox.insertText(text);
    }

    /**
     * 合成按钮点击处理
     * <p>
     * 不做客户端预校验（ContainerData 存在同步延迟，静默拦截会造成
     * "点击无响应"的困惑）：序列为空时直接忽略（无意义的请求），
     * 否则一律发包，由服务端权威校验并 actionbar 反馈失败原因
     */
    private void onSynthesizePressed() {
        String sequence = this.sequenceBox.getValue().trim();
        if (sequence.isEmpty()) {
            return;
        }
        PacketDistributor.sendToServer(new ServerboundDnaSequencePacket(sequence));
        this.sequenceBox.setValue("");
    }

    /**
     * 渲染入口：背景 + 槽位物品 + 文本 + 物品 tooltip + 进度条悬停提示
     * <p>
     * 物品 tooltip 的渲染机制（经 vanilla 源码确认）：
     * 1.21.1 重构后 AbstractContainerScreen.render 不再负责 tooltip 渲染，
     * 而是由各子类 Screen 在 render 中显式调用 this.renderTooltip——
     * InventoryScreen（背包）与 ContainerScreen（箱子）均如此。
     * 我们的 GUI 覆写 render 后必须补上这一调用，否则悬停槽位时
     * 物品 tooltip（含自研分子的分子式/结构图 tooltip）不会显示
     * <p>
     * 进度条悬停提示采用自绘方案（fill 背景 + drawString 文字），
     * 与进度条填充同一渲染管线，必然可见
     *
     * @param graphics    绘制上下文
     * @param mouseX      鼠标 X（屏幕坐标，GUI 缩放后）
     * @param mouseY      鼠标 Y（屏幕坐标，GUI 缩放后）
     * @param partialTick 部分 tick
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // 悬停槽位物品 tooltip（vanilla 1.21.1 机制：子类显式调用）
        this.renderTooltip(graphics, mouseX, mouseY);

        // 悬停提示：鼠标位于进度条区域内时自绘"碱基: 库存/上限"提示框
        for (int i = 0; i < 4; i++) {
            int x1 = this.leftPos + BAR_X[i];
            int y1 = this.topPos + BAR_Y;
            if (mouseX >= x1 && mouseX < x1 + BAR_W && mouseY >= y1 && mouseY < y1 + BAR_H) {
                String text = BASE_CHARS.charAt(i) + ": "
                        + this.menu.getBuffer(i) + "/" + DNAEncoderBlockEntity.MAX_BUFFER;
                int textWidth = this.font.width(text);
                // 提示框锚点：鼠标右下方偏移，超出 GUI 区域时翻转到左侧
                int tx = mouseX + 12;
                int ty = mouseY + 12;
                if (tx + textWidth + 6 > this.width) {
                    tx = mouseX - textWidth - 12;
                }
                if (ty + 11 > this.height) {
                    ty = mouseY - 14;
                }
                // 背景（深紫黑，接近原版 tooltip 底色）+ 文字
                graphics.fill(tx - 3, ty - 3, tx + textWidth + 3, ty + 9, 0xF0100010);
                graphics.drawString(this.font, text, tx, ty, 0xFFFFFFFF, false);
                return;
            }
        }
    }

    /**
     * 渲染背景贴图与缓冲进度条填充
     * <p>
     * 进度条轨道画在背景贴图上（静态），填充按缓冲/上限比例动态绘制：
     * 从轨道底部向上填充对应颜色（取自碱基物品染色值），缓冲区变化后
     * 数据经 ContainerData 每 tick 同步，填充高度随之更新
     *
     * @param graphics    绘制上下文
     * @param partialTick 部分 tick
     * @param mouseX      鼠标 X
     * @param mouseY      鼠标 Y
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);

        // 动态进度条填充（屏幕坐标 = 背景坐标 + leftPos/topPos）
        for (int i = 0; i < 4; i++) {
            int fillHeight = Math.round(BAR_H * (float) this.menu.getBuffer(i) / DNAEncoderBlockEntity.MAX_BUFFER);
            if (fillHeight > 0) {
                int x = this.leftPos + BAR_X[i];
                int y2 = this.topPos + BAR_Y + BAR_H;
                int y1 = y2 - fillHeight;
                graphics.fill(x, y1, x + BAR_W, y2, BAR_COLORS[i]);
            }
        }
    }

    /**
     * 渲染标题（机器名）
     * <p>
     * 不调用 super.renderLabels：vanilla 会额外绘制"物品栏"标签
     * （inventoryLabelY = imageHeight - 94，本 GUI 高 206 时落在原料区
     * 正上方，与进度条/槽位视觉重叠，用户实测反馈），
     * 故此处只绘制机器名标题，玩家背包区无标签
     *
     * @param graphics 绘制上下文
     * @param mouseX   鼠标 X
     * @param mouseY   鼠标 Y
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);
    }

    /**
     * 键盘输入转发给输入框（未聚焦时维持默认行为）
     *
     * @param codePoint 字符
     * @param modifiers 修饰键位
     * @return 是否消费事件
     */
    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.sequenceBox.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    /**
     * 按键事件转发给输入框（支持退格/方向键等）
     *
     * @param keyCode   键码
     * @param scanCode  扫描码
     * @param modifiers 修饰键位
     * @return 是否消费事件
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.sequenceBox.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 鼠标点击转发给输入框（点击聚焦后才可输入）
     *
     * @param mouseX 鼠标 X
     * @param mouseY 鼠标 Y
     * @param button 鼠标键
     * @return 是否消费事件
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.sequenceBox.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
