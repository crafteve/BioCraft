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

    private boolean working = false;

    // 动画时序追踪：pos 变化时刻（驱动逐碱基揭示）/ DONE 边沿时刻（驱动完成扫光）
    private int animLastPos = -1;
    private int animPosChangeTick = Integer.MIN_VALUE;
    private boolean animWasDone = false;
    private int animDoneTick = Integer.MIN_VALUE;

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
        // 与服务端 canStart 同口径：mRNA 合法且含起始密码子 AUG、GTP 在槽、输出有空间；
        // 特定 aa-tRNA 缺料由 BE step STALLED 细化，GUI 绿灯粗判即可
        if (mrna.isEmpty() || gtp.isEmpty()) return false;
        SequenceData d = mrna.get(ModDataComponents.SEQUENCE.get());
        if (d == null || !d.complete() || d.type() != SequenceData.SeqType.MRNA) return false;
        if (d.seq() == null || !d.seq().contains("AUG")) return false;
        if (!hasRoom(TranslatorOperation.SLOT_OUT_POLYPEPTIDE) || !hasRoom(TranslatorOperation.SLOT_OUT_TRNA) || !hasRoom(TranslatorOperation.SLOT_OUT_GDP) || !hasRoom(TranslatorOperation.SLOT_OUT_PI))
            return false;
        return true;
    }

    @Override
    protected void init() {
        super.init();
        // 对齐转录仪：右下角"翻译"按钮手动触发开工（禁自动开翻），共用启动工序包
        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("翻译"), b ->
                        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                                new com.github.crafteve.biocraft.network.ServerboundTranscribePacket(this.menu.getPos())))
                .bounds(leftPos + SequenceMachineMenu.EDIT_X + SequenceMachineMenu.EDIT_W - 46,
                        topPos + SequenceMachineMenu.EDIT_Y + SequenceMachineMenu.EDIT_H - 11, 42, 11)
                .build());
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
            case 0 -> working ? "READY" : "IDLE";
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
            // 三字母残基滚动（与 tooltip 同款写法）：每残基 18px（3 字母）+ 6px 分隔符，
            // 末端窗口滚动，当前残基白底反色高亮；分隔符用中灰（卡片浅底上纯白不可见）
            int residueW = 24;
            int window = Math.max(1, (cardW - 34) / residueW);
            int count = seq.length();
            int from = Math.max(0, count - window);
            for (int i = from; i < count && baseX + residueW <= cardX + cardW - 6; i++) {
                char aa1 = seq.charAt(i);
                String aa3 = aa1To3(aa1);
                boolean current = translating && i == count - 1;
                int color;
                if (current) {
                    g.fill(baseX - 1, baseY - 1, baseX + 19, baseY + 9, 0xFFFFFFFF);
                    color = 0xFF000000;
                } else {
                    color = cardTextColor(aaColor(aa1));
                }
                g.drawString(font, aa3, baseX, baseY, color, false);
                baseX += 18;
                if (i < count - 1) {
                    g.drawString(font, "-", baseX, baseY, 0xFF666666, false);
                    baseX += 6;
                }
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

    /**
     * 动画区（核糖体翻译，转录仪逐碱基配对范式——数据驱动非自由循环）：
     * <ul>
     *   <li>节奏：每密码子 3 tick（与 BE 步进同速，共享 TICKS_PER_CODON），
     *       1 tick 揭示 1 个碱基；第 3 个碱基点亮后服务端提交 pos++，残基在同一列弹出</li>
     *   <li>对齐：密码子 ci 与其产物残基**同列**，且残基三个字母逐字画在三个
     *       碱基的正下方（同一 x 坐标序列）——像素级一一对应，消除"亮列无物、
     *       残基错位"的观感错位</li>
     *   <li>就绪态：mRNA 全彩铺开 + 起始列白色闪烁光标 + 底部"点击翻译"提示；
     *   <li>完成动画：DONE 边沿触发白色扫光从左掠过全链（26 tick），随后末残基
     *       柔和呼吸余晖；运行中最新残基白底反色短闪（提交瞬间）</li>
     *   <li>滚窗：当前列保持右侧 1/3（转录仪同款），到末端停住；
     *       无有效 mRNA 居中灰字提示</li>
     * </ul>
     */
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
        int guiTick = net.minecraft.client.Minecraft.getInstance().gui.getGuiTicks();
        boolean running = stage == 1 && total > 0;
        boolean done = stage == 2 && total > 0;

        // 顶栏：标题 + 状态灯（工作/完成绿，等待与停摆红——loader 同口径）
        g.drawString(font, "翻译", x + 6, y + 6, 0xFFE0E0E0, false);
        int lampColor = running || done ? 0xFF2ECC71 : 0xFFE74C3C;
        g.fill(x + w - 22, y + 7, x + w - 14, y + 15, lampColor);

        // mRNA 序列来源：0 槽物品（客户端槽位同步可靠）
        ItemStack mrna = menu.getSlot(TranslatorOperation.SLOT_MRNA).getItem();
        SequenceData d = mrna.get(ModDataComponents.SEQUENCE.get());
        String seq = d != null ? d.seq() : "";
        int start = seq.indexOf("AUG");
        if (!seq.isEmpty() && start >= 0) {
            double breath = Math.sin(guiTick * 0.35) * 0.5 + 0.5;

            // 动画时序：pos 变化沿 → 重置揭示时钟；DONE 边沿 → 记录扫光起点
            if (pos != animLastPos) {
                animLastPos = pos;
                animPosChangeTick = guiTick;
            }
            if (done && !animWasDone) {
                animDoneTick = guiTick;
            }
            animWasDone = done;
            int sincePos = guiTick - animPosChangeTick;
            // 本密码子已揭示的碱基数（0..3）：运行中按距上次提交的 tick 数推进，
            // 非运行态恒满格（就绪/完成都完整展示链）
            int reveal = running
                    ? Math.min(Math.max(sincePos, 0), TranslatorOperation.TICKS_PER_CODON)
                    : TranslatorOperation.TICKS_PER_CODON;
            int sinceDone = guiTick - animDoneTick;
            boolean sweeping = done && sinceDone <= 26;

            // 几何：标签内联行首，内容统一从 x+38 起；上行 mRNA、下行肽链
            int innerX0 = x + 38;
            int innerX1 = x + w - 8;
            int mrnaY = y + 28;
            int pepY = mrnaY + 27;

            // 滚窗：当前列保持右侧 1/3，到末端停住（转录仪同款语义）
            // 列宽估算用保守下限 24（实际列宽逐列实测，见下方 colX 布局）
            int codonCount = Math.min(total, Math.max(0, seq.length() - start) / 3);
            int cur = running ? Math.min(pos, Math.max(0, codonCount - 1))
                    : done ? Math.max(0, codonCount - 1) : 0;
            int visibleCols = Math.max(2, (innerX1 - innerX0) / 24);
            int from = Math.max(0, Math.min(cur - visibleCols * 2 / 3, codonCount - visibleCols));

            // 行底条（转录仪同款深灰衬条）+ 行首内联标签
            g.fill(innerX0, mrnaY - 2, innerX1, mrnaY + 10, 0xFF2A2A2E);
            g.fill(innerX0, pepY - 2, innerX1, pepY + 9, 0xFF2A2A2E);
            g.drawString(font, "mRNA", x + 6, mrnaY, 0xFFF1C40F, false);
            g.drawString(font, "肽链", x + 6, pepY, 0xFF81C784, false);

            ItemStack pep = menu.getSlot(TranslatorOperation.SLOT_OUT_POLYPEPTIDE).getItem();
            SequenceData pd = pep.get(ModDataComponents.SEQUENCE.get());
            String pSeq = pd != null ? pd.seq() : "";
            int shown = Math.min(pos, pSeq.length());

            // 列布局：MC 字体非等宽（M/W 宽、·/i 窄），固定 6px 步进会让宽字母
            // 溢出列边界——灰线/占位点/残基全部跟着漂移（实测截图根因）。
            // 改为逐列实测内容宽（密码子整串宽 与 残基/占位串宽 取大）+ 边距，
            // 边界累计——分隔线画在真实列边界上，必然对齐
            int visCount = Math.max(0, Math.min(visibleCols, codonCount - from));
            int[] colX = new int[visCount + 1];
            colX[0] = innerX0;
            for (int k = 0; k < visCount; k++) {
                int ci = from + k;
                String codon = seq.substring(start + ci * 3, start + ci * 3 + 3);
                String resText = ci < pSeq.length() ? aa1To3(pSeq.charAt(ci)) : "···";
                int contentW = Math.max(font.width(codon), font.width(resText));
                colX[k + 1] = colX[k] + Math.max(contentW + 10, 28);
            }

            // 逐列绘制：上格密码子、下格产物残基（或占位/打印特效）、列间分隔线
            for (int k = 0; k < visCount; k++) {
                int ci = from + k;
                int cx0 = colX[k];
                int colW = colX[k + 1] - colX[k];
                boolean isCurCol = ci == cur && running;
                String codon = seq.substring(start + ci * 3, start + ci * 3 + 3);

                // 上格 mRNA：运行中当前列做逐碱基揭示（碱基按实测宽度累计排布，
                // 未揭示位画暗色占位点），其余列全彩常显（mRNA 是输入，本来就完整）
                if (isCurCol && reveal < TranslatorOperation.TICKS_PER_CODON) {
                    int bx = cx0;
                    for (int bi = 0; bi < 3; bi++) {
                        char b = codon.charAt(bi);
                        int bw = font.width(String.valueOf(b));
                        if (bi < reveal) {
                            drawBase(g, b, bx, mrnaY, true, breath);
                        } else {
                            g.drawString(font, "·", bx, mrnaY, 0xFF44484E, false);
                        }
                        bx += bw;
                    }
                } else {
                    if (isCurCol) {
                        int glow = (int) (150 * breath) << 24 | 0x00FFFFFF;
                        g.fill(cx0 - 1, mrnaY - 1, cx0 + colW - 2, mrnaY + 9, glow);
                    }
                    int bx = cx0;
                    for (int bi = 0; bi < 3; bi++) {
                        char b = codon.charAt(bi);
                        drawBase(g, b, bx, mrnaY, isCurCol, breath);
                        bx += font.width(String.valueOf(b));
                    }
                }

                // 下格肽链（打印三段时序，全部左对齐列起点）：
                //   打印中（当前列）= 白框闪烁占位（空心框，呼吸明灭）
                //   刚提交 ≤5 tick   = 黑白残基（灰度，"墨水未干"感）
                //   更早 / 完成态     = aa 主题色彩色残基
                boolean printing = isCurCol;
                if (printing) {
                    int fa = (int) (100 + breath * 130);
                    int fc = fa << 24 | 0xFFFFFF;
                    g.fill(cx0 - 1, pepY - 1, cx0 + colW - 2, pepY, fc);
                    g.fill(cx0 - 1, pepY + 8, cx0 + colW - 2, pepY + 9, fc);
                    g.fill(cx0 - 1, pepY, cx0, pepY + 8, fc);
                    g.fill(cx0 + colW - 3, pepY, cx0 + colW - 2, pepY + 8, fc);
                } else if (ci < shown) {
                    char aa1 = pSeq.charAt(ci);
                    String aa3 = aa1To3(aa1);
                    boolean justPrinted = running && ci == shown - 1 && sincePos <= 5;
                    boolean settleGlow = done && ci == shown - 1 && !sweeping;
                    int color = justPrinted ? 0xFF9A9A9A : cardTextColor(aaColor(aa1));
                    if (settleGlow) {
                        int halo = (int) (26 + breath * 22) << 24 | 0x00FFFFFF;
                        g.fill(cx0 - 1, pepY - 1, cx0 + colW - 2, pepY + 9, halo);
                    }
                    g.drawString(font, aa3, cx0, pepY, color, false);
                } else {
                    g.drawString(font, "···", cx0, pepY, 0xFF4A4E54, false);
                }

                // 列间分隔线：画在实测列边界上（贯穿两行之间的暗灰细线）
                if (k > 0) {
                    g.fill(cx0 - 2, mrnaY + 11, cx0 - 1, pepY - 3, 0xFF333338);
                }
            }

            // 当前列活动连接线：高度随逐碱基揭示进度增长（读移意象），
            // 就绪态改为起始列闪烁光标（提示点击翻译即从此处开始）
            int gapTop = mrnaY + 11;
            int gapBot = pepY - 3;
            if (running && visCount > 0) {
                int k = cur - from;
                int actX = colX[k] + (colX[k + 1] - colX[k]) / 2;
                double f = Math.max(0.2, reveal / (double) TranslatorOperation.TICKS_PER_CODON);
                int len = (int) ((gapBot - gapTop) * f);
                int lineC = (int) (170 + breath * 70) << 24 | 0xFFFFF176;
                g.fill(actX, gapTop, actX + 1, gapTop + len, lineC);
            } else if (!done && visCount > 0) {
                if ((guiTick / 8) % 2 == 0) {
                    int blinkX = colX[0] + (colX[1] - colX[0]) / 2;
                    g.fill(blinkX, gapTop, blinkX + 1, gapBot, 0x90FFFFFF);
                }
                String tip = "点击「翻译」开始";
                g.drawString(font, tip, x + (w - font.width(tip)) / 2, y + h - 12, 0xFF6A6A72, false);
            }

            // 完成扫光：白线自左向右掠过两行全区（带渐隐尾迹），扫完落定
            if (sweeping) {
                double pr = sinceDone / 26.0;
                int sx = innerX0 + (int) ((innerX1 - innerX0) * pr);
                g.fill(sx, mrnaY - 3, sx + 1, pepY + 10, 0xA0FFFFFF);
                for (int k = 1; k <= 7; k++) {
                    int tail = Math.max(0, 0x60 - k * 12) << 24 | 0xFFFFFF;
                    g.fill(sx - k, mrnaY - 3, sx - k + 1, pepY + 10, tail);
                }
            }
        } else {
            // 无有效 mRNA：居中提示（灰字，不闪烁）
            String tip = seq.isEmpty() ? "放入 mRNA 并点击翻译" : "mRNA 无起始密码子 AUG";
            g.drawString(font, tip, x + (w - font.width(tip)) / 2, y + h / 2 - 4, 0xFF6A6A72, false);
        }
    }

    /** 单碱基绘制：主题色着色；lit=true 时叠加呼吸提亮（运行中当前列辉光用） */
    private void drawBase(GuiGraphics g, char base, int px, int py, boolean lit, double breath) {
        int bColor = switch (base) {
            case 'A' -> BASE_A;
            case 'U' -> BASE_T;
            case 'C' -> BASE_C;
            case 'G' -> BASE_G;
            default -> 0xFF5A5A5A;
        };
        int c = lit ? blend(bColor, 0xFFFFFFFF, 0.35 + breath * 0.25) : bColor;
        g.drawString(font, String.valueOf(base), px, py, c, false);
    }

    /** 两色线性插值（t=0 取 c0，t=1 取 c1；ARGB 各通道独立插值） */
    private static int blend(int c0, int c1, double t) {
        t = Math.max(0, Math.min(1, t));
        int a = (int) (((c0 >>> 24) & 0xFF) * (1 - t) + ((c1 >>> 24) & 0xFF) * t);
        int r = (int) (((c0 >> 16) & 0xFF) * (1 - t) + ((c1 >> 16) & 0xFF) * t);
        int gg = (int) (((c0 >> 8) & 0xFF) * (1 - t) + ((c1 >> 8) & 0xFF) * t);
        int b = (int) ((c0 & 0xFF) * (1 - t) + (c1 & 0xFF) * t);
        return (Math.min(255, a) << 24) | (r << 16) | (gg << 8) | b;
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        if (menu.getKind() == SequenceMachineKind.TRANSLATOR) return;
        super.renderLabels(g, mx, my);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (menu.getKind() != SequenceMachineKind.TRANSLATOR) return;
        // 错误提示对齐转录仪：左下角红叹号（悬停 tooltip 列原因）——
        // 三态：未放 mRNA / 非 mRNA 物品或序列非法 / 无起始密码子 AUG
        ItemStack mrna = menu.getSlot(TranslatorOperation.SLOT_MRNA).getItem();
        SequenceData d = mrna.isEmpty() ? null : mrna.get(ModDataComponents.SEQUENCE.get());
        String err = "";
        boolean isMissing = false;
        if (mrna.isEmpty()) {
            err = "未放mRNA模板";
            isMissing = true;
        } else if (d == null || d.type() != SequenceData.SeqType.MRNA
                || d.seq() == null || !d.seq().matches("[AUCG]*")) {
            err = "非法mRNA（请用转录仪产物）";
        } else if (!d.seq().contains("AUG")) {
            err = "未找到起始密码子 AUG";
        }
        if (!err.isEmpty()) {
            int x = leftPos + SequenceMachineMenu.EDIT_X + 3;
            int y = topPos + SequenceMachineMenu.EDIT_Y + SequenceMachineMenu.EDIT_H - 9;
            int barColor = isMissing ? 0xFF9E9E9E : 0xFFE53935;
            int textColor = isMissing ? 0xFF707070 : 0xFFFFFFFF;
            String prefix = isMissing ? "§7" : "§c";
            g.fill(x, y, x + 1, y + 8, barColor);
            g.drawString(font, "!", x + 3, y, textColor, false);
            if (mx >= x && mx < x + 8 && my >= y && my < y + 8) {
                g.renderTooltip(font, java.util.List.of(Component.literal(prefix + err)), java.util.Optional.empty(), mx, my);
            }
        }
    }
}
