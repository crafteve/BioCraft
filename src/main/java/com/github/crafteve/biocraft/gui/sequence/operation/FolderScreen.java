package com.github.crafteve.biocraft.gui.sequence.operation;

import com.github.crafteve.biocraft.gui.sequence.SequenceMachineMenu;
import com.github.crafteve.biocraft.gui.sequence.SequenceMachineScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 折叠机屏幕（STAGE 族，占位）
 * <p>
 * 首版不做动画，仅复用基类框架（背景/状态栏/左右卡片/面板骨架），
 * 动画区留空，待后续补折叠可视化
 * </p>
 */
public class FolderScreen extends SequenceMachineScreen {

    public FolderScreen(SequenceMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderMachineAnimation(GuiGraphics g, int x, int y, int w, int h) {
        // 占位：中央显示 FOLD 文字
        String text = "FOLD";
        int tx = x + w / 2 - font.width(text) / 2;
        int ty = y + h / 2 - 4;
        g.drawString(font, text, tx, ty, 0xFF90A4AE, false);
    }
}
