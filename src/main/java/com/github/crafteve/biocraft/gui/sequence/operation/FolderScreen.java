package com.github.crafteve.biocraft.gui.sequence.operation;

import com.github.crafteve.biocraft.blockentity.sequence.SeqStepState;
import com.github.crafteve.biocraft.blockentity.sequence.operation.FolderOperation;
import com.github.crafteve.biocraft.gui.sequence.SequenceMachineMenu;
import com.github.crafteve.biocraft.gui.sequence.SequenceMachineScreen;
import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.item.EnzymeItem;
import com.github.crafteve.biocraft.item.SequenceData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 折叠机屏幕（STAGE 族）—— 四态重做
 * <p>
 * 自动启动，无等待按钮：
 * idle 待机、working 工作、done 待取、interrupted 中断
 * 中央 122×135 舞台：折叠漏斗可视化 + 烧杯结果
 * 卡片联动：基类卡片已覆写为 UNKN 悬念 + 绿进度 + 滚动
 * </p>
 */
public class FolderScreen extends SequenceMachineScreen {

    public FolderScreen(SequenceMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderMachineAnimation(GuiGraphics g, int x, int y, int w, int h) {
        int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
        int pos = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        boolean isExtending = stage == SeqStepState.Stage.EXTENDING.ordinal() && total > 0;
        boolean isDone = stage == SeqStepState.Stage.DONE.ordinal();

        ItemStack inStack = menu.getSlot(FolderOperation.SLOT_IN_POLYPEPTIDE).getItem();
        var inData = inStack.get(ModDataComponents.SEQUENCE.get());
        String seq = inData != null ? inData.seq() : "";
        boolean hasValidInput = !inStack.isEmpty() && inData != null && inData.complete() && !seq.isEmpty();

        ItemStack outStack = menu.getSlot(FolderOperation.SLOT_OUT).getItem();
        boolean outFull = !outStack.isEmpty() && outStack.getCount() >= outStack.getMaxStackSize();
        boolean interrupted = isExtending && (!hasValidInput || outFull);

        int tick = net.minecraft.client.Minecraft.getInstance().gui.getGuiTicks();
        double breath = (Math.sin(tick * 0.35) + 1) * 0.5;

        int cx = x + w / 2;
        int cy = y + h / 2;

        if (isDone) {
            renderDone(g, x, y, w, h, cx, cy, tick, breath, outStack);
            return;
        }
        if (interrupted) {
            renderInterrupted(g, x, y, w, h, cx, cy, tick, hasValidInput, outFull);
            return;
        }
        if (isExtending) {
            renderWorking(g, x, y, w, h, cx, cy, tick, breath, seq, pos, total);
            return;
        }
        renderIdle(g, x, y, w, h, cx, cy, breath, hasValidInput);
    }

    /** 待机：空口袋呼吸 + 提示 */
    private void renderIdle(GuiGraphics g, int x, int y, int w, int h, int cx, int cy, double breath, boolean hasValidInput) {
        int R = 15 + (int) Math.round(breath * 2);
        int pocketColor = hasValidInput ? 0xFF7E8EA0 : 0xFF5A6A7A;
        for (int i = 0; i < 24; i++) {
            double a = i * (Math.PI * 2 / 24);
            int px = cx + (int) Math.round(Math.cos(a) * R);
            int py = cy + (int) Math.round(Math.sin(a) * R);
            g.fill(px, py, px + 1, py + 1, pocketColor);
        }
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFF9E9E9E);
        String tip = hasValidInput ? "自动折叠就绪" : "放入多肽链自动折叠";
        int tw = font.width(tip);
        g.drawString(font, tip, cx - tw / 2, cy + R + 10, 0xFF90A4AE, false);
        g.drawString(font, "肽链 → 酶", cx - font.width("肽链 → 酶") / 2, y + 10, 0xFF81C784, false);
    }

    /** 工作：折叠漏斗收束 + 当前残基坠落 */
    private void renderWorking(GuiGraphics g, int x, int y, int w, int h, int cx, int cy, int tick, double breath, String seq, int pos, int total) {
        double progress = total > 0 ? pos / (double) total : 0;
        int R = (int) (18 - progress * 10 + breath * 1.5);
        // 外环
        int ring = 0xFF7E8EA0;
        for (int i = 0; i < 24; i++) {
            double a = i * (Math.PI * 2 / 24) + tick * 0.03;
            int px = cx + (int) Math.round(Math.cos(a) * R);
            int py = cy + (int) Math.round(Math.sin(a) * R);
            g.fill(px, py, px + 1, py + 1, ring);
        }
        // 中心
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFE0E0E0);
        // 当前残基坠落点
        if (!seq.isEmpty() && pos < seq.length()) {
            char aa1 = seq.charAt(pos);
            int aaTint = aaColor(aa1) | 0xFF000000;
            double drop = (tick % 10) / 10.0;
            int sx = cx;
            int sy = y + 18 + (int) (drop * (cy - (y + 18)));
            g.fill(sx - 1, sy - 1, sx + 1, sy + 1, aaTint);
            String aa3 = aa1To3(aa1);
            g.drawString(font, aa3, sx + 4, sy - 3, cardTextColor(aaTint & 0xFFFFFF), false);
            // 连接线
            g.fill(cx, sy + 2, cx + 1, cy - 2, 0x44FFFFFF);
        }
        // 顶部肽链微缩进度
        g.drawString(font, "折叠 " + pos + "/" + total, x + 6, y + 8, 0xFFE0E0E0, false);
        // 底部程序片段淡显
        String program = "";
        if (!seq.isEmpty()) {
            var decoded = com.github.crafteve.biocraft.central.Codec.decodeFromPolypeptide(seq);
            if (decoded.ok()) program = sanitizeProgram(decoded.text());
        }
        if (!program.isEmpty()) {
            String snippet = program.length() > 18 ? program.substring(0, 18) + "…" : program;
            g.drawString(font, snippet, x + 6, y + h - 12, 0xFF5A6A72, false);
        }
        // 进度光点环脉动
        int pulse = (int) (80 + breath * 80);
        g.fill(cx - R - 2, cy, cx - R, cy + 2, (pulse << 24) | 0xFFFFFF);
    }

    /** 结束待取：烧杯+缩写+信息罗列 */
    private void renderDone(GuiGraphics g, int x, int y, int w, int h, int cx, int cy, int tick, double breath, ItemStack outStack) {
        boolean isEnzyme = outStack.getItem() instanceof EnzymeItem;
        int tint;
        String abbr;
        String name;
        if (isEnzyme) {
            EnzymeItem ei = (EnzymeItem) outStack.getItem();
            tint = ei.getTintColor() | 0xFF000000;
            abbr = ei.getAbbreviation();
            name = outStack.getHoverName().getString();
        } else {
            // 错折
            tint = 0xFF9A9A9A;
            abbr = "MFLD";
            name = "错误折叠蛋白";
        }
        // 烧杯背景
        int bx = cx - 9;
        int by = cy - 16;
        g.blit(SLOT_TEX, bx, by, 0, 0, 18, 18, 18, 18);
        // 用纯色块模拟烧杯内容物染色（避免在此 renderItem 引入 z 冲突）
        g.fill(bx + 1, by + 1, bx + 17, by + 17, tint);
        g.drawString(font, abbr, cx - font.width(abbr) / 2, by + 4, 0xFFFFFFFF, true);
        // 光晕
        int halo = (int) (26 + breath * 18) << 24 | 0x00FFFFFF;
        g.fill(cx - 14, cy - 14, cx + 14, cy + 14, halo);
        // 白扫光 26tick
        int sinceDone = tick % 40;
        if (sinceDone < 26) {
            double pr = sinceDone / 26.0;
            int sx = x + 6 + (int) ((w - 12) * pr);
            g.fill(sx, y + 8, sx + 1, y + h - 8, 0xA0FFFFFF);
            for (int k = 1; k <= 5; k++) {
                int tail = Math.max(0, 0x60 - k * 12) << 24 | 0xFFFFFF;
                g.fill(sx - k, y + 8, sx - k + 1, y + h - 8, tail);
            }
        }
        // 文字信息罗列
        g.drawString(font, name, cx - font.width(name) / 2, cy + 18, 0xFFE0E0E0, false);
        String sub = isEnzyme ? abbr + " · 已折叠完成" : "MFLD · 校验失败";
        g.drawString(font, sub, cx - font.width(sub) / 2, cy + 28, isEnzyme ? 0xFF81C784 : 0xFFE57373, false);
        g.drawString(font, "取出产物以继续", cx - font.width("取出产物以继续") / 2, y + h - 10, 0xFF90A4AE, false);
    }

    /** 中断：红闪 + 原因 */
    private void renderInterrupted(GuiGraphics g, int x, int y, int w, int h, int cx, int cy, int tick, boolean hasValidInput, boolean outFull) {
        // 红闪背景
        if ((tick / 6) % 2 == 0) {
            g.fill(x + 4, y + 4, x + w - 4, y + h - 4, 0x22FF5252);
        }
        int R = 14;
        for (int i = 0; i < 24; i++) {
            double a = i * (Math.PI * 2 / 24);
            int px = cx + (int) Math.round(Math.cos(a) * R) + (int) (Math.sin(tick * 0.8) * 1.5);
            int py = cy + (int) Math.round(Math.sin(a) * R);
            g.fill(px, py, px + 1, py + 1, 0xFFFF5252);
        }
        g.fill(cx - 2, cy - 2, cx + 2, cy + 2, 0xFFFF5252);
        String reason = !hasValidInput ? "多肽被取走" : outFull ? "输出已满" : "已中断";
        g.drawString(font, reason, cx - font.width(reason) / 2, cy + R + 10, 0xFFFF5252, false);
        g.drawString(font, "补料/腾空自动恢复", cx - font.width("补料/腾空自动恢复") / 2, cy + R + 20, 0xFF90A4AE, false);
    }
}
