package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.network.ServerboundSequenceProgramPacket;
import com.github.crafteve.biocraft.seq.SeqCodec;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * DNA 编码器屏幕：文本编辑器 + 模板按钮 + 编码按钮 + 客户端编码预览
 * <p>
 * 程序文本经 ServerboundSequenceProgramPacket 提交服务端 BE（submitProgram），
 * 编码预览用 seq/ 纯核心在客户端即时计算（"编码后 X bp / 上限 4096"）
 */
public class EncoderScreen extends SequenceMachineScreen {

    private EditBox programBox;

    public EncoderScreen(SequenceMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        this.programBox = new EditBox(this.font, this.leftPos + 8, this.topPos + 18, 116, 14,
                Component.literal("程序"));
        this.programBox.setMaxLength(1500);
        this.programBox.setValue("import 酶库; HK = 酶.HK; 修饰(kcat=0.9)");
        this.addRenderableWidget(this.programBox);

        this.addRenderableWidget(Button.builder(Component.literal("编码"), b -> submit())
                .bounds(this.leftPos + 128, this.topPos + 18, 40, 14)
                .build());
        this.addRenderableWidget(Button.builder(Component.literal("模板"), b ->
                        this.programBox.setValue("import 酶库; HK = 酶.HK; 修饰(kcat=0.9)"))
                .bounds(this.leftPos + 8, this.topPos + 34, 40, 12)
                .build());
    }

    private void submit() {
        String text = this.programBox.getValue();
        if (text != null && !text.isBlank()) {
            PacketDistributor.sendToServer(new ServerboundSequenceProgramPacket(this.menu.getPos(), text));
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.programBox.canConsumeInput()) {
            return this.programBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.programBox.canConsumeInput()) {
            return this.programBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        // 客户端编码预览（seq/ 纯核心）
        String text = this.programBox.getValue();
        try {
            int bp = SeqCodec.encodeText(text).length();
            graphics.drawString(this.font, Component.literal("§7编码后 " + bp + " bp / 上限 4096"),
                    8, 48, 0xFFFFFF);
        } catch (IllegalArgumentException e) {
            graphics.drawString(this.font, Component.literal("§c程序过长，超出容量上限"),
                    8, 48, 0xFFFFFF);
        }
    }
}
