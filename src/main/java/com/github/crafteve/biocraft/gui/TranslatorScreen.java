package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.blockentity.SequenceMachineKind;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 翻译机屏幕占位（步2空壳，步4-6填实）
 * <p>
 * 最终用 dnaEncoder 同款 gui_encoder.png + 左 21 卡（GTP置顶）+ 底 4 卡（多肽/tRNA/GDP/Pi）+ 中央核糖体动画
 * </p>
 */
public class TranslatorScreen extends SequenceMachineScreen {

    public TranslatorScreen(SequenceMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        if (menu.getKind() == SequenceMachineKind.TRANSLATOR) containerTick();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        // 步4再覆写槽位坐标为翻译机布局
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (menu.getKind() != SequenceMachineKind.TRANSLATOR) {
            super.renderBg(graphics, partialTick, mouseX, mouseY);
            return;
        }
        // 步2先走父类通用，步4重写为 guiv1
        super.renderBg(graphics, partialTick, mouseX, mouseY);
    }
}
