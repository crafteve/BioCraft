package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.network.ServerboundSequenceProgramPacket;
import com.github.crafteve.biocraft.seq.SeqCodec;
import com.github.crafteve.biocraft.seq.SequenceConstants;
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

    /** 编码预览缓存（脏检测：文本变化才重算，避免每帧 SeqCodec.encodeText） */
    private int cachedBp;
    private boolean bpOverLimit;
    private String lastEditorText = "";

    public EncoderScreen(SequenceMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        // 编辑器：顶到编码区面板顶部（y = 面板顶 + 1px 边距），
        // 底部让出 2px 到按钮行（y117）；第一行文本 y34 起（贴近面板顶）
        this.editor = new CodeEditorWidget(
                leftPos + SequenceMachineMenu.EDIT_X + 3,
                topPos + SequenceMachineMenu.EDIT_Y + 1,
                SequenceMachineMenu.EDIT_W - 6,
                SequenceMachineMenu.EDIT_H - 12);
        this.editor.setText(TEMPLATE);
        this.editor.setActive(true);

        // 模板/编码按钮：面板底部行（y = 面板底 - 11，高 11，与 bp 预览同行）
        this.addRenderableWidget(Button.builder(Component.literal("模板"), b -> this.editor.setText(TEMPLATE))
                .bounds(leftPos + SequenceMachineMenu.EDIT_X + SequenceMachineMenu.EDIT_W - 92,
                        topPos + SequenceMachineMenu.EDIT_Y + SequenceMachineMenu.EDIT_H - 11, 42, 11)
                .build());
        this.encodeButton = Button.builder(Component.literal("编码"), b -> submit())
                .bounds(leftPos + SequenceMachineMenu.EDIT_X + SequenceMachineMenu.EDIT_W - 46,
                        topPos + SequenceMachineMenu.EDIT_Y + SequenceMachineMenu.EDIT_H - 11, 42, 11)
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

    /**
     * 滚轮：悬停在编辑区内时优先滚动编辑器（纵向翻行），
     * 否则交还基类（输入/输出滚动卡片区）
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        if (this.editor != null && this.editor.isMouseOver(mouseX, mouseY)) {
            return this.editor.mouseScrolled(verticalAmount);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
        // 编码预览（客户端 seq/ 纯核心即时计算，脏检测缓存）：
        // 文本未变化时直接读缓存，不每帧执行 encodeText（最长 538 字节的
        // UTF-8→BigInteger→base-20 转换，60fps 下是纯浪费）
        String text = this.editor != null ? this.editor.getText() : "";
        if (!text.equals(this.lastEditorText)) {
            this.lastEditorText = text;
            try {
                this.cachedBp = SeqCodec.encodeText(text).length();
                this.bpOverLimit = false;
            } catch (IllegalArgumentException e) {
                this.bpOverLimit = true;
            }
        }
        // 简略形式：309bp/3000bp（上限 3000 = 1000 个整密码子，能被 3 整除）
        int maxBp = SequenceConstants.MAX_DNA_BP;
        String msg = this.bpOverLimit
                ? "§c程序过长，超出" + maxBp + "bp 上限"
                : "§7" + this.cachedBp + "bp/" + maxBp + "bp";
        graphics.drawString(this.font, Component.literal(msg),
                SequenceMachineMenu.EDIT_X + 3, SequenceMachineMenu.EDIT_Y + SequenceMachineMenu.EDIT_H - 9,
                0xFFFFFF, false);
    }
}
