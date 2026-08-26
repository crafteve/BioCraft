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
        // 折叠机动画：左侧短肽链滚动（3 tick/残基），右侧程序字符串滚动（仿解旋机双链并行）
        int pos = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        var inStack = menu.getSlot(com.github.crafteve.biocraft.blockentity.sequence.operation.FolderOperation.SLOT_IN_POLYPEPTIDE).getItem();
        var data = inStack.get(com.github.crafteve.biocraft.init.ModDataComponents.SEQUENCE.get());
        String seq = data != null ? data.seq() : "";
        // 左侧：多肽链（肽链三字母，当前位置高亮）
        int leftY = y + 12;
        g.drawString(font, "肽链", x + 6, leftY, 0xFF81C784, false);
        if (!seq.isEmpty()) {
            int window = Math.max(1, (w - 20) / 24);
            int from = Math.max(0, Math.min(pos - window / 2, seq.length() - window));
            int baseX = x + 6;
            int baseY = leftY + 12;
            for (int i = from; i < Math.min(seq.length(), from + window); i++) {
                char aa1 = seq.charAt(i);
                String aa3 = aa1To3(aa1);
                int color = (i == pos && pos < total) ? 0xFFFFFFFF : cardTextColor(aaColor(aa1));
                if (i == pos && pos < total) {
                    g.fill(baseX - 1, baseY - 1, baseX + 19, baseY + 9, 0xFFFFEB3B);
                }
                for (int bi = 0; bi < aa3.length(); bi++) {
                    g.drawString(font, String.valueOf(aa3.charAt(bi)), baseX + bi * 6, baseY, color, false);
                }
                baseX += 24;
            }
        }
        // 右侧：程序字符串滚动（解码预览，当前位置高亮）
        String program = "";
        if (!seq.isEmpty()) {
            var decoded = com.github.crafteve.biocraft.central.Codec.decodeFromPolypeptide(seq);
            if (decoded.ok()) program = decoded.text();
        }
        int rightY = y + h / 2 + 6;
        g.drawString(font, "程序", x + 6, rightY, 0xFF90CAF9, false);
        if (!program.isEmpty()) {
            // 按字符滚动，窗口 18 字符
            int win = Math.max(1, (w - 20) / 6);
            int pFrom = Math.max(0, Math.min(pos - win / 2, program.length() - win));
            int baseX = x + 6;
            int baseY = rightY + 12;
            boolean isError = program.length() > 200; // 简略：超长视为错误
            for (int i = pFrom; i < Math.min(program.length(), pFrom + win); i++) {
                char c = program.charAt(i);
                int color = (i == pos && pos < total) ? 0xFF000000 : 0xFFB0BEC5;
                if (i == pos && pos < total) {
                    g.fill(baseX - 1, baseY - 1, baseX + 7, baseY + 9, 0xFF00E5FF);
                }
                g.drawString(font, String.valueOf(c), baseX, baseY, color, false);
                baseX += 6;
            }
            if (program.length() > win) {
                g.drawString(font, "…", baseX, rightY + 12, 0xFF90A4AE, false);
            }
        } else if (!seq.isEmpty()) {
            g.drawString(font, "…解码中", x + 6, rightY + 12, 0xFF90A4AE, false);
        }
    }
}
