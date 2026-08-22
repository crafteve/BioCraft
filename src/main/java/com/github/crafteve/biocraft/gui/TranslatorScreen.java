package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.SequenceMachineKind;
import com.github.crafteve.biocraft.blockentity.TranslatorOperation;
import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.item.MoleculeItem;
import com.github.crafteve.biocraft.seq.SequenceData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 翻译机屏幕：mRNA(9,8) + 左21卡（GTP置顶+20 aa-tRNA）+ 右上178×95核糖体动画 + 底4卡（多肽/tRNA/GDP/Pi）
 * <p>
 * 布局复 dnaEncoder 的 gui_encoder.png 256×256，同 Transcriber 的 EDIT 动画区，
 * 输入竖直滚动借 Loader 输出竖直卡，输出横向4卡借 dnaEncoder 底栏
 * </p>
 */
public class TranslatorScreen extends SequenceMachineScreen {

    private static final ResourceLocation GUI_V1 = ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "textures/gui/gui_encoder.png");

    private long animStart = -1;
    private boolean working = false;

    public TranslatorScreen(SequenceMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        if (menu.getKind() == SequenceMachineKind.TRANSLATOR) containerTick();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (menu.getKind() != SequenceMachineKind.TRANSLATOR) return;
        // 二态工作判定：与 TranslatorOperation.isWorkable 同口径（模板+ GTP + 输出空间），不依赖 stage
        working = isWorkable();
        // 基类 tickScrolls 已处理左21卡纵向与底4卡横向的坐标，无需再覆写
    }

    private boolean isWorkable() {
        ItemStack mrna = menu.getSlot(TranslatorOperation.SLOT_MRNA).getItem();
        ItemStack gtp = menu.getSlot(TranslatorOperation.SLOT_GTP).getItem();
        // 简化：容器整包判定，真正缺特定 aa-tRNA 由 BE step STALLED 细化，GUI 绿灯只要任一 aa-tRNA 存在即亮
        // 为精确，尝试用整容器 isWorkable
        // 构造临时 SimpleContainer 视图复用逻辑？直接判 mrna/gtp/输出空间
        if (mrna.isEmpty() || gtp.isEmpty()) return false;
        SequenceData d = mrna.get(ModDataComponents.SEQUENCE.get());
        if (d == null || !d.complete() || d.type() != SequenceData.SeqType.MRNA) return false;
        if (!hasRoom(TranslatorOperation.SLOT_OUT_POLYPEPTIDE) || !hasRoom(TranslatorOperation.SLOT_OUT_TRNA) || !hasRoom(TranslatorOperation.SLOT_OUT_GDP) || !hasRoom(TranslatorOperation.SLOT_OUT_PI))
            return false;
        return true;
    }

    private boolean hasRoom(int slot) {
        ItemStack s = menu.getSlot(slot).getItem();
        return s.isEmpty() || s.getCount() < s.getMaxStackSize();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (menu.getKind() != SequenceMachineKind.TRANSLATOR) {
            super.renderBg(graphics, partialTick, mouseX, mouseY);
            return;
        }
        graphics.blit(GUI_V1, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        drawTranslatorStatusBar(graphics);
        graphics.drawString(font, "INPUT", leftPos + 9, topPos + 30, NAME_COLOR, false);
        graphics.drawString(font, "OUTPUT", leftPos + SequenceMachineMenu.OUTPUT_LABEL_X, topPos + SequenceMachineMenu.OUTPUT_LABEL_Y, NAME_COLOR, false);
        drawTranslatorInputCards(graphics);
        drawTranslatorOutputCards(graphics);
        drawTranslationAnimation(graphics);
        // 模板槽底纹（仿转录仪 9,8 slot.png）
        graphics.blit(SLOT_TEX, leftPos + 9 - 1, topPos + 8 - 1, 0, 0, 18, 18, 18, 18);
    }

    private void drawTranslatorStatusBar(GuiGraphics g) {
        g.drawString(font, title, leftPos + 28, topPos + 13, NAME_COLOR, false);
        int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
        int pos = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        String status = switch (stage) {
            case 0 -> working ? "RUN" : "IDLE";
            case 1 -> "TRANS " + pos + "/" + total;
            case 2 -> "DONE";
            default -> "IDLE";
        };
        // 工作态细化提示
        if (stage == 0 && !working) {
            ItemStack mrna = menu.getSlot(TranslatorOperation.SLOT_MRNA).getItem();
            if (mrna.isEmpty()) status = "WAIT mRNA";
            else if (!hasRoom(TranslatorOperation.SLOT_OUT_POLYPEPTIDE)) status = "FULL";
            else status = "IDLE";
        }
        g.drawString(font, status, leftPos + imageWidth - 8 - font.width(status), topPos + 13, CONC_TEXT_COLOR, false);
        int fill = total > 0 ? (int) ((imageWidth - 16) * pos / (double) total) : 0;
        g.fill(leftPos + 8, topPos + 22, leftPos + 8 + imageWidth - 16, topPos + 25, BAR_TRACK);
        if (fill > 0) g.fill(leftPos + 8, topPos + 22, leftPos + 8 + fill, topPos + 25, 0xFF7ED6DF);
        g.blit(SLOT_TEX, leftPos + 9 - 1, topPos + 8 - 1, 0, 0, 18, 18, 18, 18);
    }

    private void drawTranslatorInputCards(GuiGraphics g) {
        // 复用父类纵向滚动，但需确保 GTP 置顶视觉——第一卡 GTP 用绿调高亮
        int areaX = leftPos + SequenceMachineMenu.INPUT_SCROLL_X;
        int areaY = topPos + SequenceMachineMenu.INPUT_SCROLL_Y;
        g.enableScissor(areaX, areaY, areaX + SequenceMachineMenu.INPUT_SCROLL_W, areaY + SequenceMachineMenu.INPUT_SCROLL_H);
        int vOff = (int) Math.round(inputScrollOffset);
        for (int i = 0; i < inputCards.size(); i++) {
            var card = inputCards.get(i);
            int cardY = areaY + i * SequenceMachineMenu.CARD_STEP - vOff;
            Slot slot = menu.getSlot(card.containerSlot());
            // GTP 置顶卡特殊：颜色取 gtp 分子色，若空则灰
            drawTranslatorInputCard(g, areaX, cardY, SequenceMachineMenu.CARD_W, SequenceMachineMenu.CARD_H, card.itemId(), slot, card.containerSlot() == TranslatorOperation.SLOT_GTP);
        }
        g.disableScissor();
    }

    private void drawTranslatorInputCard(GuiGraphics g, int cardX, int cardY, int cardW, int cardH, String itemId, Slot slot, boolean isGtp) {
        g.fill(cardX, cardY, cardX + cardW, cardY + cardH, CARD_COLOR);
        int pngX = cardX + SequenceMachineMenu.SLOT_PNG_X;
        int pngY = cardY + SequenceMachineMenu.SLOT_PNG_Y;
        g.blit(SLOT_TEX, pngX, pngY, 0, 0, 18, 18, 18, 18);
        ItemStack stack = slot.getItem();
        String abbr; int tint;
        var di = ModItems.byId(itemId);
        if (di != null && di.get() instanceof MoleculeItem mi) { abbr = mi.getAbbreviation(); tint = mi.getTintColor(); }
        else if (isGtp) { abbr = "GTP"; tint = 0x27AE60; }
        else if ("trna".equals(itemId)) { abbr = "tRNA"; tint = 0xB0C4DE; }
        else { abbr = itemId; tint = 0xCCCCCC; }
        // GTP 置顶卡：若为空，abbl 灰；若有货，绿高亮
        int color = stack.isEmpty() ? CONC_TEXT_COLOR : cardTextColor(tint);
        if (isGtp && !stack.isEmpty()) g.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0x1027AE60);
        g.drawString(font, abbr, pngX + 18 + 4, pngY, color, false);
        double rem = menu.getRemainder(slot.index);
        double total = Math.max(0, stack.getCount() - rem);
        // 输入卡：消耗型，total = count - rem
        int barY = cardY + SequenceMachineMenu.SLOT_PNG_Y + 18 + (8 - 3) / 2;
        int fill = (int) Math.min((cardW - 2) * total / 64.0, cardW - 2);
        g.fill(cardX + 1, barY, cardX + 1 + cardW - 2, barY + 3, BAR_TRACK);
        if (fill > 0) g.fill(cardX + 1, barY, cardX + 1 + fill, barY + 3, color);
        String countText = total >= 100 ? String.format("%.1f", total) : String.format("%.2f", total);
        g.drawString(font, "x" + countText, pngX + 18 + 4, pngY + 18 + 1 - 8, CONC_TEXT_COLOR, false);
    }

    private void drawTranslatorOutputCards(GuiGraphics g) {
        int areaX = leftPos + SequenceMachineMenu.OUT_X;
        int areaY = topPos + SequenceMachineMenu.OUT_Y;
        g.enableScissor(areaX, areaY, areaX + SequenceMachineMenu.OUT_W, areaY + SequenceMachineMenu.OUT_H);
        int hOff = (int) Math.round(outputScrollOffset);
        int cardX = areaX;
        for (int i = 0; i < outputCards.size(); i++) {
            var card = outputCards.get(i);
            int thisX = cardX - hOff;
            Slot slot = menu.getSlot(card.containerSlot());
            if (card.containerSlot() == TranslatorOperation.SLOT_OUT_POLYPEPTIDE) {
                drawPolypeptideCard(g, thisX, areaY, card.cardWidth(), SequenceMachineMenu.OUT_CARD_H, slot);
            } else {
                drawStockCard(g, thisX, areaY, card.cardWidth(), SequenceMachineMenu.OUT_CARD_H, card.itemId(), slot, false);
            }
            cardX += card.cardWidth() + SequenceMachineMenu.CARD_GAP;
        }
        g.disableScissor();
    }

    private void drawPolypeptideCard(GuiGraphics g, int cardX, int cardY, int cardW, int cardH, Slot slot) {
        g.fill(cardX, cardY, cardX + cardW, cardY + cardH, CARD_COLOR);
        int pngX = cardX + SequenceMachineMenu.SLOT_PNG_X;
        int pngY = cardY + SequenceMachineMenu.SLOT_PNG_Y;
        g.blit(SLOT_TEX, pngX, pngY, 0, 0, 18, 18, 18, 18);
        int textX = pngX + 18 + 4;
        int pos = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        g.drawString(font, "PEP", textX, pngY, NAME_COLOR, false);
        g.drawString(font, pos + "/" + total, textX + 30, pngY, CONC_TEXT_COLOR, false);
        ItemStack stack = slot.getItem();
        SequenceData data = stack.get(ModDataComponents.SEQUENCE.get());
        String seq = data != null ? data.seq() : "";
        int baseX = textX;
        int baseY = pngY + 11;
        boolean translating = total > 0 && pos < total;
        if (!seq.isEmpty()) {
            int window = (cardW - 34) / 7;
            int from = Math.max(0, seq.length() - window);
            for (int i = from; i < seq.length() && baseX < cardX + cardW - 10; i++) {
                char aa1 = seq.charAt(i);
                // 1字母→3字母→分子色
                String aa3 = aa1To3(aa1);
                int tint = aaColor(aa1);
                int color = tint;
                if (translating && i == seq.length() - 1) {
                    g.fill(baseX - 1, baseY - 1, baseX + 7, baseY + 9, 0xFFFFFFFF);
                    color = 0xFF000000;
                } else {
                    color = cardTextColor(tint);
                }
                // 显示单字母着色（窄卡）或三字母首字母
                g.drawString(font, String.valueOf(aa1), baseX, baseY, color, false);
                baseX += 7;
            }
        }
        if (translating && baseX < cardX + cardW - 4) g.fill(baseX, baseY + 4, baseX + 3, baseY + 7, 0xFF00E5FF);
        int barY = cardY + SequenceMachineMenu.SLOT_PNG_Y + 18 + 1;
        int fill = total > 0 ? (int) Math.min((cardW - 2) * pos / (double) total, cardW - 2) : 0;
        g.fill(cardX + 1, barY, cardX + 1 + cardW - 2, barY + 2, BAR_TRACK);
        if (fill > 0) g.fill(cardX + 1, barY, cardX + 1 + fill, barY + 2, 0xFF4CAF50);
    }

    private String aa1To3(char aa1) {
        return switch (aa1) {
            case 'A' -> "Ala"; case 'R' -> "Arg"; case 'N' -> "Asn"; case 'D' -> "Asp"; case 'C' -> "Cys";
            case 'Q' -> "Gln"; case 'E' -> "Glu"; case 'G' -> "Gly"; case 'H' -> "His"; case 'I' -> "Ile";
            case 'L' -> "Leu"; case 'K' -> "Lys"; case 'M' -> "Met"; case 'F' -> "Phe"; case 'P' -> "Pro";
            case 'S' -> "Ser"; case 'T' -> "Thr"; case 'W' -> "Trp"; case 'Y' -> "Tyr"; case 'V' -> "Val";
            default -> String.valueOf(aa1);
        };
    }

    private int aaColor(char aa1) {
        // 复用 Loader aa 颜色 via TranslatorOperation 的 trna 映射
        int slot = TranslatorOperation.slotForAa1(aa1);
        if (slot < 0) return 0xCCCCCC;
        String trna = TranslatorOperation.trnaForSlot(slot);
        if (trna == null) return 0xCCCCCC;
        var di = ModItems.byId(trna);
        if (di != null && di.get() instanceof MoleculeItem mi) return mi.getTintColor();
        return 0xCCCCCC;
    }

    // 动画区
    private void drawTranslationAnimation(GuiGraphics g) {
        int x = leftPos + SequenceMachineMenu.EDIT_X;
        int y = topPos + SequenceMachineMenu.EDIT_Y;
        int w = SequenceMachineMenu.EDIT_W;
        int h = SequenceMachineMenu.EDIT_H;
        g.fill(x, y, x + w, y + h, EDIT_PANEL_COLOR);
        g.fill(x, y, x + w, y + 1, 0xFF3A3A3A);
        for (int gx = x + 12; gx < x + w; gx += 14) g.fill(gx, y + 12, gx + 1, y + h - 6, 0x08FFFFFF);
        for (int gy = y + 18; gy < y + h; gy += 14) g.fill(x + 6, gy, x + w - 6, gy + 1, 0x08FFFFFF);

        int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
        int pos = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        boolean run = stage == 1;
        boolean done = stage == 2;
        g.drawString(font, "翻译", x + 6, y + 6, 0xFFE0E0E0, false);
        String st = run ? pos + "/" + total : done ? "完成" : working ? "就绪" : "待机";
        g.drawString(font, st, x + w - 6 - font.width(st), y + 6, 0xFF9E9E9E, false);
        int tick = net.minecraft.client.Minecraft.getInstance().gui.getGuiTicks();
        boolean animActive = working && run;
        int t = 0;
        if (animActive) { if (animStart < 0) animStart = tick; t = (int) ((tick - animStart) % 30); } else animStart = -1;

        // 核糖体
        int cx = x + w / 2;
        int cy = y + h / 2 + 8;
        // 小亚基 30S / 大亚基 50S
        g.fill(cx - 28, cy - 10, cx + 28, cy + 6, 0xFF3A3A42);
        g.fill(cx - 26, cy - 8, cx + 26, cy + 4, 0xFF5A5A64);
        g.fill(cx - 20, cy + 4, cx + 20, cy + 8, 0xFF7ED6DF);
        // mRNA 轨道
        g.fill(x + 10, cy, x + w - 10, cy + 2, 0xFF8BC34A);
        // 密码子窗口
        ItemStack mrna = menu.getSlot(TranslatorOperation.SLOT_MRNA).getItem();
        SequenceData d = mrna.get(ModDataComponents.SEQUENCE.get());
        String seq = d != null ? d.seq() : "";
        int start = seq.indexOf("AUG");
        if (start >= 0 && total > 0) {
            int curCodonIdx = Math.min(pos, total - 1);
            // 显示 3 密码子窗口
            for (int i = -1; i <= 1; i++) {
                int idx = curCodonIdx + i;
                if (idx < 0 || idx >= total) continue;
                // 从 codons pendingProgram 取密码子？用 seq 估
                int base = start + idx * 3;
                if (base + 3 > seq.length()) continue;
                String cod = seq.substring(base, base + 3);
                int bx = cx + i * 22 - 9;
                int col = i == 0 ? 0xFFFFFF00 : 0xFFB0BEC5;
                if (i == 0) g.fill(bx - 2, cy - 8, bx + 20, cy + 10, 0x33FFEB3B);
                g.drawString(font, cod, bx, cy - 6, col, false);
                // 下方氨基酸缩写
                char aa1 = '?'; try { aa1 = com.github.crafteve.biocraft.seq.CodonTable.codonToAa(cod); } catch (Exception e) {}
                if (aa1 != '*') {
                    String aa3 = aa1To3(aa1);
                    int ac = aaColor(aa1);
                    g.drawString(font, aa3, bx + 2, cy + 6, ac | 0xFF000000, false);
                }
            }
        }
        // 多肽链上长（头顶）
        ItemStack pep = menu.getSlot(TranslatorOperation.SLOT_OUT_POLYPEPTIDE).getItem();
        SequenceData pd = pep.get(ModDataComponents.SEQUENCE.get());
        String pseq = pd != null ? pd.seq() : "";
        if (!pseq.isEmpty()) {
            int window = Math.min(6, pseq.length());
            String tail = pseq.substring(Math.max(0, pseq.length() - window));
            // 竖向肽链
            int px = cx;
            int py = cy - 18;
            for (int i = 0; i < tail.length(); i++) {
                char aa1 = tail.charAt(i);
                int ac = aaColor(aa1) | 0xFF000000;
                g.drawString(font, String.valueOf(aa1), px + i * 7 - window * 3, py, ac, false);
            }
            g.drawString(font, "N-", cx - 18, py - 8, 0xFF90A4AE, false);
            g.drawString(font, "-C", cx + tail.length() * 7 - window * 3 + 4, py, 0xFF90A4AE, false);
        }
        if (!animActive) {
            if (working) g.drawString(font, "缺 GTP 或对应 aa-tRNA", x + 6, y + h - 10, 0xFFE67E22, false);
            return;
        }
        // 进入动画：aa-tRNA 从右上进 A 位，GTP 双闪
        double prog = Math.min(1.0, t / 10.0);
        int aX = (int) (x + w - 20 + (cx + 14 - (x + w - 20)) * prog);
        // 当前密码子对应 aa 颜色
        char curAa = '?'; String curCod = ""; if (start >= 0 && pos < total) {
            int b = start + pos * 3; if (b + 3 <= seq.length()) curCod = seq.substring(b, b + 3);
            try { curAa = com.github.crafteve.biocraft.seq.CodonTable.codonToAa(curCod); } catch (Exception e) {}
        }
        int curTint = aaColor(curAa) | 0xFF000000;
        if (t < 11) {
            g.fill(aX - 1, cy - 10, aX + 6, cy - 4, curTint);
            g.drawString(font, curAa == '?' ? "?" : String.valueOf(curAa), aX, cy - 18, curTint, false);
            for (int p = 0; p < 2; p++) g.fill(aX + 8 + p * 4, cy - 10, aX + 10 + p * 4, cy - 8, 0xFF27AE60);
            g.drawString(font, "GTP×2", aX + 6, cy - 22, 0xFF27AE60, false);
        } else g.fill(cx + 14, cy - 10, cx + 18, cy - 6, curTint);
        if (t >= 11 && t < 14) g.fill(cx - 4, cy - 4, cx + 4, cy + 4, 0x44FFFFFF);
        if (t >= 11 && t < 16) {
            int f = (t - 11) * 2;
            g.fill(cx + 18 + f, cy - 14 + f, cx + 24 + f, cy - 10 + f, 0xFFA0D995);
            g.drawString(font, "GDP", cx + 20 + f, cy - 22 + f, 0xFFA0D995, false);
            g.fill(cx + 24 + f, cy - 12 + f, cx + 28 + f, cy - 8 + f, 0xFFF39C12);
            g.drawString(font, "Pi", cx + 28 + f, cy - 18 + f, 0xFFF39C12, false);
        }
        if (t >= 15 && t < 21) {
            int f = (t - 15) * 2;
            g.fill(cx - 24 - f, cy - 2 + f, cx - 18 - f, cy + 2 + f, 0xFFB0C4DE);
            g.drawString(font, "tRNA", cx - 28 - f, cy + 6 + f, 0xFFB0C4DE, false);
        }
        if (t >= 14) {
            double breath = (Math.sin(tick * 0.35) + 1) * 0.5;
            int halo = 12 + (int) Math.round(breath * 2);
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI * 2 / 16;
                int px = cx + (int) Math.round(Math.cos(a) * halo);
                int py = cy - 4 + (int) Math.round(Math.sin(a) * halo);
                if (i % 2 == 0) g.fill(px, py, px + 1, py + 1, 0x44FFFFFF);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        if (menu.getKind() == SequenceMachineKind.TRANSLATOR) return;
        super.renderLabels(g, mx, my);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (menu.getKind() == SequenceMachineKind.TRANSLATOR && isHoveringMrnaError(mx, my)) {
            ItemStack mrna = menu.getSlot(TranslatorOperation.SLOT_MRNA).getItem();
            SequenceData d = mrna.get(ModDataComponents.SEQUENCE.get());
            String err = "";
            if (d == null || d.type() != SequenceData.SeqType.MRNA) err = "未放mRNA模板";
            else if (!d.seq().contains("AUG")) err = "未找到起始密码子 AUG";
            if (!err.isEmpty()) g.renderTooltip(font, java.util.List.of(Component.literal("§c" + err)), java.util.Optional.empty(), mx, my);
        }
    }

    private boolean isHoveringMrnaError(double mx, double my) {
        int x = leftPos + 69 + 3, y = topPos + 31 + 95 - 9;
        return mx >= x && mx < x + 8 && my >= y && my < y + 8;
    }
}
