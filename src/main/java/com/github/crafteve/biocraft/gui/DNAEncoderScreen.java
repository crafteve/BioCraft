package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.BioCraft;
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
 * DNA 编码器屏幕
 * <p>
 * 本机器特有的交互界面（三台原始机器中唯一带文本输入的 GUI）：
 * <ul>
 *   <li>EditBox：DNA 序列输入，客户端实时过滤非法字符（仅 A/T/C/G），长度上限 64</li>
 *   <li>合成按钮：点击后先做客户端预校验（碱基充足/输出槽空），通过则发送网络包</li>
 *   <li>状态文本行：显示最近一次合成结果（碱基不足/输出槽满等停摆原因）</li>
 * </ul>
 * 合成权威判定在服务端（网络包处理器），客户端预校验只为快速反馈，不做安全假设
 */
public class DNAEncoderScreen extends AbstractContainerScreen<DNAEncoderMenu> {
    /** GUI 背景贴图（256×256，实际绘制区域 176×166） */
    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "textures/gui/dna_encoder.png");

    /** 序列输入框（GUI 内相对坐标：x=26, y=17） */
    private EditBox sequenceBox;

    /** 合成按钮（GUI 内相对坐标：x=118, y=16） */
    private Button synthesizeButton;

    /** 本地预校验失败状态（优先于服务端状态显示，点击后由服务端状态覆盖） */
    private SynthesisStatus localStatus = SynthesisStatus.IDLE;

    /**
     * @param menu    菜单实例
     * @param playerInventory 玩家物品栏
     * @param title   窗口标题
     */
    public DNAEncoderScreen(DNAEncoderMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    /**
     * 初始化界面组件：序列输入框与合成按钮
     * <p>
     * 组件坐标基于 guiLeft/guiTop（背景图左上角），与 Menu 槽位坐标同源
     */
    @Override
    protected void init() {
        super.init();

        this.sequenceBox = new EditBox(this.font, this.leftPos + 26, this.topPos + 17, 86, 16,
                Component.translatable("gui.biocraft.sequence_label"));
        // 过滤非法字符：仅允许 DNA 碱基 A/T/C/G
        this.sequenceBox.setFilter(text -> text.chars().allMatch(c -> "ACGT".indexOf(c) >= 0));
        this.sequenceBox.setMaxLength(64);

        this.synthesizeButton = Button.builder(
                        Component.translatable("gui.biocraft.synthesize"),
                        button -> onSynthesizePressed())
                .bounds(this.leftPos + 118, this.topPos + 16, 50, 18)
                .build();

        this.addRenderableWidget(this.sequenceBox);
        this.addRenderableWidget(this.synthesizeButton);
    }

    /**
     * 合成按钮点击处理
     * <p>
     * 先做客户端预校验（快速反馈，不消耗任何资源），通过后发送网络包，
     * 服务端再次权威校验并执行合成；发送成功后清空输入框
     * <p>
     * 客户端读取的是 Menu 槽位内容（服务端每 tick 同步），预校验结果与实际
     * 服务端状态可能存在极短延迟差，因此服务端校验不可省略
     */
    private void onSynthesizePressed() {
        String sequence = this.sequenceBox.getValue().trim();

        // 客户端预校验：序列为空 / 非法字符 / 超长（理论上已被过滤，兜底）
        if (sequence.isEmpty()) {
            this.localStatus = SynthesisStatus.EMPTY_SEQUENCE;
            return;
        }
        if (sequence.length() > 64 || !sequence.chars().allMatch(c -> "ACGT".indexOf(c) >= 0)) {
            this.localStatus = SynthesisStatus.INVALID_SEQUENCE;
            return;
        }

        // 客户端预校验：统计各碱基需求数量，检查对应槽位（菜单槽 0-3）与输出槽（4）
        Map<Character, Integer> needed = new HashMap<>();
        for (int i = 0; i < sequence.length(); i++) {
            needed.merge(sequence.charAt(i), 1, Integer::sum);
        }
        for (Map.Entry<Character, Integer> entry : needed.entrySet()) {
            int slotIndex = switch (entry.getKey()) {
                case 'A' -> 0;
                case 'T' -> 1;
                case 'C' -> 2;
                case 'G' -> 3;
                default -> -1;
            };
            if (this.menu.slots.get(slotIndex).getItem().getCount() < entry.getValue()) {
                this.localStatus = SynthesisStatus.INSUFFICIENT_BASE;
                return;
            }
        }
        if (!this.menu.slots.get(4).getItem().isEmpty()) {
            this.localStatus = SynthesisStatus.OUTPUT_FULL;
            return;
        }

        // 预校验通过：发送合成请求，服务端权威执行
        PacketDistributor.sendToServer(new ServerboundDnaSequencePacket(sequence));
        this.localStatus = SynthesisStatus.IDLE;
        this.sequenceBox.setValue("");
    }

    /**
     * 渲染背景贴图与序列输入提示
     *
     * @param graphics 绘制上下文
     * @param partialTick 部分 tick
     * @param mouseX   鼠标 X
     * @param mouseY   鼠标 Y
     */
    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    }

    /**
     * 渲染标题与状态文本行
     * <p>
     * 状态文本优先显示本地预校验结果，否则显示服务端同步的最近合成状态；
     * 无状态时显示操作提示（灰色）
     *
     * @param graphics 绘制上下文
     * @param mouseX   鼠标 X
     * @param mouseY   鼠标 Y
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);

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
        graphics.drawString(this.font, text, 26, 68, 0xFFFFFF, false);
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
