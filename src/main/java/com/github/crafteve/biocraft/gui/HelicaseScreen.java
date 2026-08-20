package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.blockentity.SequenceMachineKind;
import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.seq.SequenceData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 解旋酶屏幕（helicase）：1 dsDNA → 2 ssDNA 双产物卡 + 中央解旋动画
 * <p>
 * 复用 SequenceMachineScreen 的输入/输出滚动区与状态栏，仅覆写：
 * 输入卡按 DNA 序列卡渲染（NMT 不同需显示序列而非 x数量），
 * 输出双卡各显一条单链（反向互补对），中央面板绘解旋分叉动画
 */
public class HelicaseScreen extends SequenceMachineScreen {

    public HelicaseScreen(SequenceMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void drawInputCards(GuiGraphics graphics) {
        if (menu.getKind() != SequenceMachineKind.HELICASE) {
            super.drawInputCards(graphics);
            return;
        }
        // 解旋酶单输入：dsDNA 序列卡（复用输出 DNA 卡的序列显示逻辑，但置于输入区）
        if (inputCards.isEmpty()) {
            return;
        }
        int areaX = leftPos + SequenceMachineMenu.INPUT_SCROLL_X;
        int areaY = topPos + SequenceMachineMenu.INPUT_SCROLL_Y;
        graphics.enableScissor(areaX, areaY, areaX + SequenceMachineMenu.INPUT_SCROLL_W,
                areaY + SequenceMachineMenu.INPUT_SCROLL_H);
        int vOffset = (int) Math.round(inputScrollOffset);
        for (int i = 0; i < inputCards.size(); i++) {
            InputCard card = inputCards.get(i);
            int cardY = areaY + i * SequenceMachineMenu.CARD_STEP - vOffset;
            Slot slot = menu.getSlot(card.containerSlot());
            // 输入 dsDNA 用 DNA 卡渲染（显示序列号 + 四色碱基），复用输出卡逻辑
            drawHelicaseDnaCard(graphics, areaX, cardY, SequenceMachineMenu.CARD_W,
                    SequenceMachineMenu.CARD_H, slot, "dsDNA");
        }
        graphics.disableScissor();
    }

    @Override
    protected void drawOutputCards(GuiGraphics graphics) {
        if (menu.getKind() != SequenceMachineKind.HELICASE) {
            super.drawOutputCards(graphics);
            return;
        }
        if (outputCards.isEmpty()) {
            return;
        }
        int areaX = leftPos + SequenceMachineMenu.OUT_X;
        int areaY = topPos + SequenceMachineMenu.OUT_Y;
        graphics.enableScissor(areaX, areaY, areaX + SequenceMachineMenu.OUT_W,
                areaY + SequenceMachineMenu.OUT_H);
        int hOffset = (int) Math.round(outputScrollOffset);
        int cardX = areaX;
        for (int i = 0; i < outputCards.size(); i++) {
            OutputCard card = outputCards.get(i);
            int thisCardX = cardX - hOffset;
            Slot slot = menu.getSlot(card.containerSlot());
            String label = i == 0 ? "ssDNA-A" : "ssDNA-B";
            drawHelicaseDnaCard(graphics, thisCardX, areaY, card.cardWidth(),
                    SequenceMachineMenu.OUT_CARD_H, slot, label);
            cardX += card.cardWidth() + SequenceMachineMenu.CARD_GAP;
        }
        graphics.disableScissor();
    }

    /**
     * 解旋专用 DNA 卡（输入/输出复用）：背景 + slot.png + 序列号 + 四色碱基窗口
     * <p>
     * 复刻 SequenceMachineScreen.drawDnaCard 的精简版，输入输出共用，避免
     * 基类 drawStockCard 对 dna/dna_single 的 MoleculeItem 误判
     */
    private void drawHelicaseDnaCard(GuiGraphics graphics, int cardX, int cardY, int cardW, int cardH,
                                     Slot slot, String label) {
        graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, CARD_COLOR);
        int pngX = cardX + SequenceMachineMenu.SLOT_PNG_X;
        int pngY = cardY + SequenceMachineMenu.SLOT_PNG_Y;
        graphics.blit(SLOT_TEX, pngX, pngY, 0, 0, 18, 18, 18, 18);
        int textX = pngX + 18 + 4;
        ItemStack stack = slot.getItem();
        SequenceData data = stack.get(ModDataComponents.SEQUENCE.get());
        String seq = data != null ? data.seq() : "";
        // 第一行：标签 + 长度
        graphics.drawString(font, label, textX, pngY, NAME_COLOR, false);
        graphics.drawString(font, seq.length() + " nt", textX + 45, pngY, CONC_TEXT_COLOR, false);
        // 第二行：四色碱基末端窗口（与编码器 DNA 卡同色）
        int baseX = textX;
        int baseY = pngY + 11;
        if (!seq.isEmpty()) {
            int window = (cardW - 34) / 7;
            int from = Math.max(0, seq.length() - window);
            for (int i = from; i < seq.length() && baseX < cardX + cardW - 10; i++) {
                char base = seq.charAt(i);
                int color = switch (base) {
                    case 'A' -> BASE_A;
                    case 'T' -> BASE_T;
                    case 'C' -> BASE_C;
                    case 'G' -> BASE_G;
                    default -> CONC_TEXT_COLOR;
                };
                graphics.drawString(font, String.valueOf(base), baseX, baseY, color, false);
                baseX += 7;
            }
        } else {
            graphics.drawString(font, "空", baseX, baseY, CONC_TEXT_COLOR, false);
        }
        // 进度条：解旋酶原子操作无余量，按有无产物示意（空=0，满=满格）
        int barY = cardY + SequenceMachineMenu.SLOT_PNG_Y + 18 + 1;
        int fill = seq.isEmpty() ? 0 : cardW - 2;
        graphics.fill(cardX + 1, barY, cardX + 1 + cardW - 2, barY + 2, BAR_TRACK);
        if (fill > 0) {
            graphics.fill(cardX + 1, barY, cardX + 1 + fill, barY + 2, 0xFF7ED6DF);
        }
    }

    @Override
    protected void drawEditPanel(GuiGraphics graphics) {
        if (menu.getKind() != SequenceMachineKind.HELICASE) {
            super.drawEditPanel(graphics);
            return;
        }
        // 中央解旋动画区：复用编码区面板坐标 (69,31 178x95)
        int x = leftPos + SequenceMachineMenu.EDIT_X;
        int y = topPos + SequenceMachineMenu.EDIT_Y;
        int w = SequenceMachineMenu.EDIT_W;
        int h = SequenceMachineMenu.EDIT_H;
        graphics.fill(x, y, x + w, y + h, EDIT_PANEL_COLOR);
        graphics.fill(x, y, x + w, y + 1, 0xFF3A3A3A);

        // 状态：IDLE/EXTENDING/DONE
        int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
        int pos = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        boolean isUnwinding = stage == 1; // EXTENDING
        boolean isDone = stage == 2;

        // 标题
        graphics.drawString(font, "解旋", x + 6, y + 6, 0xFFE0E0E0, false);
        String status = isUnwinding ? "解旋中 " + pos + "/" + total : isDone ? "完成" : "待机";
        graphics.drawString(font, status, x + w - 6 - font.width(status), y + 6, 0xFF9E9E9E, false);

        // 动画：双螺旋 → 分叉；用简笔画线条 + 碱基对色点示意
        int cx = x + w / 2;
        int cy = y + h / 2 + 8;
        int tick = net.minecraft.client.Minecraft.getInstance().gui.getGuiTicks();
        double wave = Math.sin(tick * 0.25) * 2;

        if (isUnwinding) {
            // 解旋中：中央分叉，两链分开
            // 左链
            graphics.fill(cx - 28, cy - 12, cx - 26, cy + 16, 0xFF4FC3F7);
            for (int i = 0; i < 4; i++) {
                int by = cy - 8 + i * 8;
                graphics.fill(cx - 24 + (int) wave, by, cx - 20 + (int) wave, by + 2, BASE_A + (i % 2 == 0 ? 0 : 0x33000000));
                graphics.fill(cx - 8 - (int) wave, by, cx - 4 - (int) wave, by + 2, BASE_T + (i % 2 == 0 ? 0 : 0x33000000));
            }
            // 右链
            graphics.fill(cx + 26, cy - 12, cx + 28, cy + 16, 0xFF81C784);
            // 分叉点闪烁
            int flash = (tick / 6) % 2 == 0 ? 0xFFFFFF00 : 0xFFFFE082;
            graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, flash);
            graphics.drawString(font, "⇄", cx - 4, cy - 20, 0xFFE0E0E0, false);
        } else if (isDone) {
            // 完成：两条单链平行
            graphics.fill(cx - 20, cy - 12, cx - 18, cy + 16, 0xFF4FC3F7);
            graphics.fill(cx + 18, cy - 12, cx + 20, cy + 16, 0xFF81C784);
            graphics.drawString(font, "⇉ 2× ssDNA", cx - 22, cy - 20, 0xFFB0BEC5, false);
        } else {
            // 待机：双螺旋示意（交错波浪）
            for (int i = 0; i < 5; i++) {
                int by = cy - 12 + i * 6;
                int off = (int) (Math.sin((tick + i * 8) * 0.15) * 6);
                graphics.fill(cx + off - 1, by, cx + off + 1, by + 2, i % 2 == 0 ? 0xFF4FC3F7 : 0xFF81C784);
                graphics.fill(cx - off - 1, by, cx - off + 1, by + 2, i % 2 == 0 ? 0xFF81C784 : 0xFF4FC3F7);
            }
            graphics.drawString(font, "dsDNA", cx - 12, cy - 20, 0xFF90A4AE, false);
        }

        // 底部提示
        String tip = isDone ? "取出产物后自动复位" : "放入 dsDNA 自动解旋 → 2 ssDNA";
        graphics.drawString(font, tip, x + 6, y + h - 12, 0xFF6A9955, false);
    }
}
