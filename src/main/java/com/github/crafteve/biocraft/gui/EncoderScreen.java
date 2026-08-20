package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.network.ServerboundSequenceProgramPacket;
import com.github.crafteve.biocraft.seq.SeqCodec;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * DNA 编码器屏幕：编码区 = 自绘代码编辑器（CodeEditorWidget）
 * <p>
 * 差异部分（基类 SequenceMachineScreen 只负责画布/背包/状态栏/输入滚动卡片/
 * 输出卡片/编码区面板）：
 * <ul>
 *   <li>编码区工具栏：模板按钮 + 编码按钮（编辑器面板右上角）</li>
 *   <li>代码编辑器（多行/缩进/语法高亮/光标）接收输入</li>
 *   <li>编码进度 → 编辑器扫描线动画（动画 A）+ 输出 DNA 四色生长（动画 B，基类画）</li>
 *   <li>编码预览：编码后 bp 数（客户端 seq/ 纯核心即时计算）</li>
 * </ul>
 */
public class EncoderScreen extends SequenceMachineScreen {

    private static final String TEMPLATE = "import 酶库 as 酶\nHK = 酶.HK\n修饰(HK, kcat=0.9)";

    private CodeEditorWidget editor;
    private Button encodeButton;

    public EncoderScreen(SequenceMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        // 编辑器：面板内工具栏下方（面板 y24-96，工具栏 y24-35，文本区 y38-92）
        this.editor = new CodeEditorWidget(
                leftPos + SequenceMachineMenu.EDIT_X + 3,
                topPos + SequenceMachineMenu.EDIT_Y + 14,
                SequenceMachineMenu.EDIT_W - 6,
                SequenceMachineMenu.EDIT_H - 16);
        this.editor.setText(TEMPLATE);
        this.editor.setActive(true);

        this.addRenderableWidget(Button.builder(Component.literal("模板"), b -> this.editor.setText(TEMPLATE))
                .bounds(leftPos + SequenceMachineMenu.EDIT_X + SequenceMachineMenu.EDIT_W - 92,
                        topPos + SequenceMachineMenu.EDIT_Y + 2, 42, 11)
                .build());
        this.encodeButton = Button.builder(Component.literal("编码"), b -> submit())
                .bounds(leftPos + SequenceMachineMenu.EDIT_X + SequenceMachineMenu.EDIT_W - 46,
                        topPos + SequenceMachineMenu.EDIT_Y + 2, 42, 11)
                .build();
        this.addRenderableWidget(this.encodeButton);
    }

    private void submit() {
        String text = this.editor.getText();
        if (text != null && !text.isBlank()) {
            PacketDistributor.sendToServer(new ServerboundSequenceProgramPacket(this.menu.getPos(), text));
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.editor.tick();
        // 编码进度 → 扫描线动画（动画 A）；未编码/完成 = 1.0（无扫描线）
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        int position = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        double progress = total > 0 ? position / (double) total : 1.0;
        this.editor.setProgress(progress);
    }

    // ---- 输入路由：编辑器有焦点时优先，否则交还基类（背包快捷键/槽位） ----

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.editor != null && this.editor.isActive()
                && this.editor.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.editor != null && this.editor.isActive()
                && this.editor.charTyped(codePoint)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.editor != null) {
            if (this.editor.isMouseOver(mouseX, mouseY)) {
                this.editor.mouseClicked(mouseX, mouseY);
                this.editor.setActive(true);
                return true;
            }
            this.editor.setActive(false);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        if (this.editor != null) {
            this.editor.render(graphics);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        // 编码预览（客户端 seq/ 纯核心即时计算）
        String text = this.editor != null ? this.editor.getText() : "";
        try {
            int bp = SeqCodec.encodeText(text).length();
            graphics.drawString(this.font, Component.literal("§7编码后 " + bp + " bp / 上限 4096"),
                    SequenceMachineMenu.EDIT_X + 3, SequenceMachineMenu.EDIT_Y + SequenceMachineMenu.EDIT_H - 9,
                    0xFFFFFF, false);
        } catch (IllegalArgumentException e) {
            graphics.drawString(this.font, Component.literal("§c程序过长，超出容量上限"),
                    SequenceMachineMenu.EDIT_X + 3, SequenceMachineMenu.EDIT_Y + SequenceMachineMenu.EDIT_H - 9,
                    0xFFFFFF, false);
        }
    }
}
