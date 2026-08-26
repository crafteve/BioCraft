package com.github.crafteve.biocraft.gui.sequence.operation;

import com.github.crafteve.biocraft.data.EnzymeProgramChecker;
import com.github.crafteve.biocraft.network.ServerboundProgramDraftPacket;
import com.github.crafteve.biocraft.network.ServerboundSequenceProgramPacket;
import com.github.crafteve.biocraft.program.EnzymeProgramParser;
import com.github.crafteve.biocraft.program.ProgramError;
import com.github.crafteve.biocraft.seq.SeqCodec;
import com.github.crafteve.biocraft.seq.SequenceConstants;
import com.github.crafteve.biocraft.gui.sequence.SequenceMachineMenu;
import com.github.crafteve.biocraft.gui.sequence.CodeEditorWidget;
import com.github.crafteve.biocraft.gui.sequence.SequenceMachineScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

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

    /**
     * 默认模板（酶设计单 DSL）：id 锚定基酶（正式 id，如 hexokinase；
     * 缩写 HK 也能匹配）+ name 显示名；kcat 等字段需后续解锁
     * （TNT 诱变/翻译成就）
     */
    private static final String TEMPLATE = "id: hexokinase\nname: 己糖激酶";

    private CodeEditorWidget editor;
    private Button encodeButton;

    /** 编码预览缓存（脏检测：文本变化才重算，避免每帧 SeqCodec.encodeText） */
    private int cachedBp;
    private boolean bpOverLimit;
    private String lastEditorText = "";

    /** 程序校验错误缓存（脏检测：文本变化才跑解析 + 完整校验） */
    private List<ProgramError> programErrors = List.of();

    /** 已发送服务端的草稿快照（脏检测：编辑器文本变化才发包保存） */
    private String lastSentDraft = null;

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
        // 恢复服务端存档的编辑器草稿（无草稿用默认模板）
        String draft = this.menu.getProgramDraft();
        this.editor.setText(draft == null || draft.isEmpty() ? TEMPLATE : draft);
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

    /**
     * 编码按钮：先提交程序文本（服务端归零旧链 + 存 pendingProgram），
     * 再发启动工序包手动开工——两包按发送顺序在服务端排队执行，
     * 对齐转录仪/翻译机的"点按钮才开工"语义
     */
    private void submit() {
        String text = this.editor.getText();
        if (text != null && !text.isBlank()) {
            PacketDistributor.sendToServer(new ServerboundSequenceProgramPacket(this.menu.getPos(), text));
            PacketDistributor.sendToServer(new com.github.crafteve.biocraft.network.ServerboundTranscribePacket(this.menu.getPos()));
        }
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.editor.tick();
        // 编码进度 → 逐字符动画（动画 A：编码到哪个字符哪个变色+变底色）
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        int position = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        double progress = total > 0 ? position / (double) total : 1.0;
        this.editor.setProgress(progress);
        // 编辑器草稿保存（脏检测：文本变化才发包，跨 GUI 打开保留）
        String text = this.editor.getText();
        if (!text.equals(this.lastSentDraft)) {
            this.lastSentDraft = text;
            PacketDistributor.sendToServer(new ServerboundProgramDraftPacket(this.menu.getPos(), text));
        }
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
            // 酶设计单校验（零依赖解析 + MC 侧装配校验，与折叠机同口径）
            if (text.isBlank()) {
                this.programErrors = List.of();
            } else {
                this.programErrors = EnzymeProgramChecker.check(
                        EnzymeProgramParser.parse(text));
            }
        }
        int maxBp = SequenceConstants.MAX_DNA_BP;
        boolean hasIssue = this.bpOverLimit || !this.programErrors.isEmpty();
        // 报错提示：左下角红色感叹号（悬停 tooltip 显示详情，见 render 覆写）——
        // 不直接在编码区打印错误文本（会与按钮/编辑器重叠）
        String bpText = "§7" + this.cachedBp + "bp/" + maxBp + "bp";
        if (hasIssue) {
            graphics.drawString(this.font, Component.literal("§c!"),
                    SequenceMachineMenu.EDIT_X + 3, SequenceMachineMenu.EDIT_Y + SequenceMachineMenu.EDIT_H - 9,
                    0xFFFFFF, false);
            graphics.drawString(this.font, Component.literal(bpText),
                    SequenceMachineMenu.EDIT_X + 3 + 10, SequenceMachineMenu.EDIT_Y + SequenceMachineMenu.EDIT_H - 9,
                    0xFFFFFF, false);
        } else {
            graphics.drawString(this.font, Component.literal(bpText),
                    SequenceMachineMenu.EDIT_X + 3, SequenceMachineMenu.EDIT_Y + SequenceMachineMenu.EDIT_H - 9,
                    0xFFFFFF, false);
        }
    }

    /**
     * render 覆写：super 之后补画报错感叹号的悬停 tooltip——
     * 有错误且鼠标悬停在左下角感叹号上时，显示全部错误详情（每行一条）
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (isHoveringWarning(mouseX, mouseY)) {
            java.util.List<Component> lines = new java.util.ArrayList<>();
            if (this.bpOverLimit) {
                lines.add(Component.literal("§c程序过长，超出 " + SequenceConstants.MAX_DNA_BP + "bp 上限"));
            }
            for (ProgramError e : this.programErrors) {
                lines.add(Component.literal("§c" + e.describe()));
            }
            if (!lines.isEmpty()) {
                graphics.renderTooltip(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
            }
        }
    }

    /** 悬停判定：左下角感叹号矩形（8×8，与 bp 预览同行） */
    private boolean isHoveringWarning(double mouseX, double mouseY) {
        int x = this.leftPos + SequenceMachineMenu.EDIT_X + 3;
        int y = this.topPos + SequenceMachineMenu.EDIT_Y + SequenceMachineMenu.EDIT_H - 9;
        return mouseX >= x && mouseX < x + 8 && mouseY >= y && mouseY < y + 8;
    }
}
