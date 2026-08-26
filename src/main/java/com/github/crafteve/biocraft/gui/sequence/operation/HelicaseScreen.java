package com.github.crafteve.biocraft.gui.sequence.operation;

import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.seq.SequenceData;
import com.github.crafteve.biocraft.seq.SeqOps;
import com.github.crafteve.biocraft.gui.sequence.SequenceMachineMenu;
import com.github.crafteve.biocraft.gui.sequence.SequenceLayout;
import com.github.crafteve.biocraft.gui.sequence.SequenceMachineScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 解旋酶屏幕（STAGE族布局）：框架（背景/状态栏/标签/动画区面板骨架/槽位坐标）
 * 全部由基类按 SequenceLayout 绘制，本类只实现：
 * <ul>
 *   <li>专属卡片内容：输入 dsDNA 卡（解旋中显示已解旋前缀）与输出
 *       模板链/编码链卡（nt 数 + 四色碱基窗口）——序列预览型卡片，
 *       与通用库存卡内容不同，故覆写卡片绘制方法</li>
 *   <li>动画内容：待机双螺旋波动 / 解旋中双链分叉 + 当前碱基对 / 完成平行双链</li>
 * </ul>
 */
public class HelicaseScreen extends SequenceMachineScreen {

    public HelicaseScreen(SequenceMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void drawInputCards(GuiGraphics graphics) {
        if (inputCards.isEmpty()) return;
        int areaX = leftPos + SequenceMachineMenu.INPUT_SCROLL_X;
        int areaY = topPos + SequenceMachineMenu.INPUT_SCROLL_Y;
        graphics.enableScissor(areaX, areaY, areaX + SequenceMachineMenu.INPUT_SCROLL_W,
                areaY + SequenceMachineMenu.INPUT_SCROLL_H);
        for (InputCard card : inputCards) {
            Slot slot = menu.getSlot(card.containerSlot());
            // 输入 DNA 与编码链同步滚动：解旋中显示 S[0:pos] 前缀（与编码链一致）
            int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
            int pos = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
            if (stage == 1 && pos > 0) {
                Slot codingSlot = menu.getSlot(2);
                ItemStack codingStack = codingSlot.getItem();
                SequenceData codingData = codingStack.get(ModDataComponents.SEQUENCE.get());
                if (codingData != null && !codingData.seq().isEmpty()) {
                    // 创建临时前缀展示用栈（不改真实 NBT，仅显示）
                    ItemStack display = new ItemStack(codingStack.getItem(), 1);
                    display.set(ModDataComponents.SEQUENCE.get(), new SequenceData(
                            SequenceData.SeqType.DNA, SequenceData.Strand.DS, codingData.kind(), codingData.seq(), true));
                    // 用临时 slot 包装以复用绘制逻辑
                    Slot tmp = new Slot(new net.minecraft.world.SimpleContainer(1), 0, slot.x, slot.y) {
                        @Override public ItemStack getItem() { return display; }
                    };
                    drawHelicaseDnaCard(graphics, areaX, areaY, SequenceMachineMenu.INPUT_SCROLL_W, 28, tmp, "DNA");
                    continue;
                }
            }
            drawHelicaseDnaCard(graphics, areaX, areaY, SequenceMachineMenu.INPUT_SCROLL_W, 28, slot, "DNA");
        }
        graphics.disableScissor();
    }

    @Override
    protected void drawVerticalOutputCards(GuiGraphics graphics) {
        if (outputCards.isEmpty()) return;
        int areaX = leftPos + 193;
        int areaY = topPos + 41;
        graphics.enableScissor(areaX, areaY, areaX + 56, areaY + 112);
        // 垂直双卡：标签改为模板链/编码链（专有名词，编码链与 dsDNA 一致）
        for (int i = 0; i < outputCards.size(); i++) {
            OutputCard card = outputCards.get(i);
            int cardY = areaY + i * SequenceMachineMenu.CARD_STEP;
            Slot slot = menu.getSlot(card.containerSlot());
            String label = i == 0 ? "模板链" : "编码链";
            drawHelicaseDnaCard(graphics, areaX, cardY, 56, 28, slot, label);
        }
        graphics.disableScissor();
    }

    /**
     * 解旋专用 DNA 卡（输入/输出复用）：背景 + slot.png + 序列号 + 四色碱基窗口
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
        graphics.drawString(font, label, textX, pngY, NAME_COLOR, false);
        graphics.drawString(font, seq.length() + " nt", textX + 38, pngY, CONC_TEXT_COLOR, false);
        int baseX = textX;
        int baseY = pngY + 11;
        if (!seq.isEmpty()) {
            int window = (cardW - 34) / 7;
            int from = Math.max(0, seq.length() - window);
            int to = seq.length();
            for (int i = from; i < to && baseX < cardX + cardW - 10; i++) {
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
        int barY = cardY + SequenceMachineMenu.SLOT_PNG_Y + 18 + 1;
        int fill = seq.isEmpty() ? 0 : cardW - 2;
        graphics.fill(cardX + 1, barY, cardX + 1 + cardW - 2, barY + 2, BAR_TRACK);
        if (fill > 0) {
            graphics.fill(cardX + 1, barY, cardX + 1 + fill, barY + 2, 0xFF7ED6DF);
        }
    }

    @Override
    protected void renderMachineAnimation(GuiGraphics graphics, int x, int y, int w, int h) {
        int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
        int pos = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        boolean isUnwinding = stage == 1;
        boolean isDone = stage == 2;

        int cx = x + w / 2;
        int cy = y + h / 2;
        int tick = net.minecraft.client.Minecraft.getInstance().gui.getGuiTicks();
        double wave = Math.sin(tick * 0.22) * 2.5;

        String chain = "";
        char aBase = '?', bBase = '?';
        if (isUnwinding && total > 0) {
            Slot codingSlot = menu.getSlot(2);
            SequenceData codingData = codingSlot.getItem().get(ModDataComponents.SEQUENCE.get());
            if (codingData != null && !codingData.seq().isEmpty()) {
                chain = codingData.seq();
                aBase = chain.charAt(chain.length() - 1);
                bBase = SeqOps.complementDna(aBase);
            }
        } else if (isDone) {
            Slot codingSlot = menu.getSlot(2);
            SequenceData codingData = codingSlot.getItem().get(ModDataComponents.SEQUENCE.get());
            if (codingData != null) chain = codingData.seq();
        }

        if (isUnwinding) {
            // 阴影
            graphics.fill(cx - 31, cy - 15, cx - 29, cy + 29, 0x40000000);
            graphics.fill(cx + 31, cy - 15, cx + 33, cy + 29, 0x40000000);
            // 双链
            graphics.fill(cx - 32, cy - 16, cx - 30, cy + 28, 0xFF4FC3F7);
            graphics.fill(cx + 30, cy - 16, cx + 32, cy + 28, 0xFF81C784);
            for (int i = 0; i < 4; i++) {
                int by = cy - 12 + i * 10;
                int off = (int) wave;
                graphics.fill(cx - 28 + off, by, cx - 22 + off, by + 2, 0xFFEF5350);
                graphics.fill(cx + 22 - off, by, cx + 28 - off, by + 2, 0xFFFFEB3B);
            }
            int flash = (tick / 5) % 2 == 0 ? 0xFFFFFF00 : 0xFFFFE082;
            graphics.fill(cx - 4, cy - 4, cx + 4, cy + 4, 0x40000000);
            graphics.fill(cx - 3, cy - 3, cx + 3, cy + 3, flash);
            if (aBase != '?') {
                int colorA = switch (aBase) { case 'A' -> BASE_A; case 'T' -> BASE_T; case 'C' -> BASE_C; case 'G' -> BASE_G; default -> 0xFFFFF59D; };
                int colorB = switch (bBase) { case 'A' -> BASE_A; case 'T' -> BASE_T; case 'C' -> BASE_C; case 'G' -> BASE_G; default -> 0xFFFFF59D; };
                String aStr = String.valueOf(aBase), bStr = String.valueOf(bBase);
                int pw = font.width(aStr) + font.width("–") + font.width(bStr);
                int px = cx - pw / 2;
                // 发光底
                graphics.fill(px - 2, cy - 30, px + pw + 2, cy - 20, 0x33FFFFFF);
                graphics.drawString(font, aStr, px, cy - 28, colorA, false);
                px += font.width(aStr);
                graphics.drawString(font, "–", px, cy - 28, 0xFFE0E0E0, false);
                px += font.width("–");
                graphics.drawString(font, bStr, px, cy - 28, colorB, false);
                graphics.drawString(font, aStr, cx - 36, cy + 30, colorA, false);
                graphics.drawString(font, bStr, cx + 30, cy + 30, colorB, false);
            }
        } else if (isDone) {
            graphics.fill(cx - 27, cy - 15, cx - 25, cy + 29, 0x40000000);
            graphics.fill(cx + 27, cy - 15, cx + 29, cy + 29, 0x40000000);
            graphics.fill(cx - 28, cy - 16, cx - 26, cy + 28, 0xFF4FC3F7);
            graphics.fill(cx + 26, cy - 16, cx + 28, cy + 28, 0xFF81C784);
            String ss = "ssDNA";
            int pw = font.width(ss);
            graphics.drawString(font, ss, cx - 32 - pw / 2, cy - 32, 0xFFE0E0E0, false);
            graphics.drawString(font, ss, cx + 32 - pw / 2, cy - 32, 0xFFE0E0E0, false);
        } else {
            for (int i = 0; i < 6; i++) {
                int by = cy - 18 + i * 8;
                double t = (tick + i * 7) * 0.14;
                int off = (int) (Math.sin(t) * 10);
                int c1 = i % 2 == 0 ? 0xFF4FC3F7 : 0xFF81C784;
                int c2 = i % 2 == 0 ? 0xFF81C784 : 0xFF4FC3F7;
                // 阴影
                graphics.fill(cx + off, by + 1, cx + off + 2, by + 3, 0x40000000);
                graphics.fill(cx + off - 1, by, cx + off + 1, by + 2, c1);
                graphics.fill(cx - off, by + 1, cx - off + 2, by + 3, 0x40000000);
                graphics.fill(cx - off - 1, by, cx - off + 1, by + 2, c2);
            }
            graphics.drawString(font, "DNA", cx - 10, cy - 34, 0xFF90A4AE, false);
        }
    }
}
