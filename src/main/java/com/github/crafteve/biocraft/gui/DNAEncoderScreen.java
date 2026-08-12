package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.DNAEncoderBlockEntity;
import com.github.crafteve.biocraft.blockentity.SynthesisStatus;
import com.github.crafteve.biocraft.network.ServerboundDnaSequencePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

/**
 * DNA 编码器屏幕（缓冲池版）
 * <p>
 * 自下而上布局分区（背景贴图坐标，相对 GUI 左上角）：
 * <ul>
 *   <li>按钮区 y=16：启动子/终止子按钮 + 右侧序列文本框（EditBox）</li>
 *   <li>按钮区 y=38：ATCG 碱基按钮（点击向文本框光标处插入字符）+ 合成按钮</li>
 *   <li>原料区 y=62：四根竖向缓冲进度条（对应四种碱基，悬停显示 库存/4096）</li>
 *   <li>原料区 y=106：四个碱基吸收槽（空槽时上方显示 A/T/C/G 小字标注）</li>
 *   <li>输出槽 (134,62)：合成产物 DNA模板</li>
 * </ul>
 * 进度条数据来自 Menu 的 ContainerData（每 tick 服务端同步），
 * 悬停提示与状态文本均为客户端本地渲染，无额外网络包
 */
public class DNAEncoderScreen extends AbstractContainerScreen<DNAEncoderMenu> {
    /** GUI 背景贴图（256×256，实际绘制区域 176×206） */
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "textures/gui/dna_encoder.png");

    /** 碱基字母序列（与缓冲池索引一致：0=A 1=T 2=C 3=G） */
    private static final String BASE_CHARS = "ATCG";

    /** 四条进度条的 X 坐标（与碱基槽同列，宽 12 居中于 18 宽槽位上方） */
    private static final int[] BAR_X = {29, 47, 65, 83};
    /** 进度条区域 Y 与宽高 */
    private static final int BAR_Y = 62;
    private static final int BAR_W = 12;
    private static final int BAR_H = 34;

    /** 碱基槽 X 坐标（用于空槽标注居中） */
    private static final int[] BASE_SLOT_X = {26, 44, 62, 80};
    /** 槽位上方标注 Y */
    private static final int LABEL_Y = 100;

    /** 进度条填充颜色：A=蓝 T=红 C=绿 G=黄 */
    private static final int[] BAR_COLORS = {0xFF4FC3F7, 0xFFFF5252, 0xFF69F0AE, 0xFFFFD740};

    /** 序列输入框 */
    private EditBox sequenceBox;

    /** 合成按钮 */
    private Button synthesizeButton;

    /** 本地预校验失败状态（优先于服务端状态显示，点击后由服务端状态覆盖） */
    private SynthesisStatus localStatus = SynthesisStatus.IDLE;

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

        this.synthesizeButton = Button.builder(
                        Component.translatable("gui.biocraft.synthesize"),
                        button -> onSynthesizePressed())
                .bounds(this.leftPos + 116, this.topPos + 38, 52, 16)
                .build();
        this.addRenderableWidget(this.synthesizeButton);
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
     * 先做客户端预校验（对照缓冲池数据，快速反馈不消耗资源），通过后发送网络包，
     * 服务端再次权威校验并执行合成；发送成功后清空输入框
     * <p>
     * 预校验读取的是 Menu 的 ContainerData（服务端每 tick 同步），
     * 与实际服务端缓冲可能存在极短延迟差，因此服务端校验不可省略
     */
    private void onSynthesizePressed() {
        String sequence = this.sequenceBox.getValue().trim();

        // 客户端预校验：序列为空 / 非法字符 / 超长（理论上已被过滤，兜底）
        if (sequence.isEmpty()) {
            this.localStatus = SynthesisStatus.EMPTY_SEQUENCE;
            return;
        }
        if (sequence.length() > DNAEncoderBlockEntity.MAX_SEQUENCE_LENGTH
                || !sequence.chars().allMatch(c -> "ACGT".indexOf(c) >= 0)) {
            this.localStatus = SynthesisStatus.INVALID_SEQUENCE;
            return;
        }

        // 客户端预校验：统计需求并对照缓冲池（Menu data 槽 0-3）
        Map<Character, Integer> needed = new HashMap<>();
        for (int i = 0; i < sequence.length(); i++) {
            needed.merge(sequence.charAt(i), 1, Integer::sum);
        }
        for (Map.Entry<Character, Integer> entry : needed.entrySet()) {
            int bufferIndex = BASE_CHARS.indexOf(entry.getKey());
            if (this.menu.getBuffer(bufferIndex) < entry.getValue()) {
                this.localStatus = SynthesisStatus.INSUFFICIENT_BASE;
                return;
            }
        }
        if (!this.menu.getSlot(DNAEncoderBlockEntity.SLOT_OUTPUT).getItem().isEmpty()) {
            this.localStatus = SynthesisStatus.OUTPUT_FULL;
            return;
        }

        // 预校验通过：发送合成请求，服务端权威执行
        PacketDistributor.sendToServer(new ServerboundDnaSequencePacket(sequence));
        this.localStatus = SynthesisStatus.IDLE;
        this.sequenceBox.setValue("");
    }

    /**
     * 渲染背景贴图与缓冲进度条填充
     * <p>
     * 进度条轨道画在背景贴图上（静态），填充按缓冲/上限比例动态绘制：
     * 从轨道底部向上填充对应颜色，缓冲区变化后数据经 ContainerData
     * 每 tick 同步，填充高度随之更新
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
     * 渲染标题、状态文本与碱基槽空槽标注
     * <p>
     * 状态文本优先显示本地预校验结果，否则显示服务端同步的最近合成状态；
     * 空槽标注仅当槽位为空时绘制（物品放入后由图标缩写装饰器标识，避免遮挡）
     *
     * @param graphics 绘制上下文
     * @param mouseX   鼠标 X
     * @param mouseY   鼠标 Y
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);

        // 空槽标注：字母画在槽位上方（进度条下方间隙）
        for (int i = 0; i < 4; i++) {
            if (this.menu.getSlot(i).getItem().isEmpty()) {
                graphics.drawString(this.font, Character.toString(BASE_CHARS.charAt(i)),
                        BASE_SLOT_X[i] + 6, LABEL_Y, 0xFF9E9E9E, false);
            }
        }

        // 状态文本
        SynthesisStatus status = this.localStatus != SynthesisStatus.IDLE
                ? this.localStatus
                : this.menu.getStatus();
        Component text = switch (status) {
            case IDLE -> Component.translatable("gui.biocraft.status.idle")
                    .withStyle(style -> style.withColor(0x9E9E9E));
            case SUCCESS -> Component.translatable("gui.biocraft.status.success")
                    .withStyle(style -> style.withColor(0x55FF55));
            case EMPTY_SEQUENCE -> Component.translatable("gui.biocraft.status.empty_sequence")
                    .withStyle(style -> style.withColor(0xFF5555));
            case INVALID_SEQUENCE -> Component.translatable("gui.biocraft.status.invalid_sequence")
                    .withStyle(style -> style.withColor(0xFF5555));
            case INSUFFICIENT_BASE -> Component.translatable("gui.biocraft.status.insufficient_base")
                    .withStyle(style -> style.withColor(0xFF5555));
            case OUTPUT_FULL -> Component.translatable("gui.biocraft.status.output_full")
                    .withStyle(style -> style.withColor(0xFF5555));
        };
        graphics.drawString(this.font, text, 8, 124, 0xFFFFFF, false);
    }

    /**
     * 悬停提示：鼠标位于进度条区域内时显示缓冲库存
     * <p>
     * 在渲染 tooltip 前检测鼠标位置与四条进度条的碰撞，
     * 命中则直接渲染"碱基: 库存/上限"提示并短路（不渲染其他 tooltip）
     *
     * @param graphics 绘制上下文
     * @param mouseX   鼠标 X
     * @param mouseY   鼠标 Y
     */
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < 4; i++) {
            int x1 = this.leftPos + BAR_X[i];
            int y1 = this.topPos + BAR_Y;
            if (mouseX >= x1 && mouseX < x1 + BAR_W && mouseY >= y1 && mouseY < y1 + BAR_H) {
                graphics.renderTooltip(this.font,
                        Component.literal(BASE_CHARS.charAt(i) + ": "
                                + this.menu.getBuffer(i) + "/" + DNAEncoderBlockEntity.MAX_BUFFER),
                        mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(graphics, mouseX, mouseY);
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
