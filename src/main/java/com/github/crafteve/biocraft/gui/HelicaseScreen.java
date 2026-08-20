package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.SequenceMachineKind;
import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.seq.SequenceData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 解旋酶屏幕（helicase）：guiv1 酶工厂布局
 * <p>
 * 左侧输入（7,41）1 张 dsDNA 模板卡，右侧输出（193,41）垂直双卡 ssDNA-A/B，
 * 中央（68,38 122×126）为大动画区：逐碱基对解旋时显示对应碱基对，待机双螺旋/解旋中分叉/完成平行
 */
public class HelicaseScreen extends SequenceMachineScreen {

    private static final ResourceLocation GUI_V1 =
            ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, "textures/gui/gui_v1.png");

    // guiv1 布局常量（复用 MachineScreen/MachineMenu 同款，SequenceMachineMenu 另有 OUT_X=70 不适用）
    private static final int LEFT_X = 7;
    private static final int LEFT_Y = 41;
    private static final int LEFT_W = 56;
    private static final int LEFT_H = 112;
    private static final int RIGHT_X = 193;
    private static final int RIGHT_Y = 41;
    private static final int RIGHT_W = 56;
    // 中央动画区（复用酶工厂反应区：x 67~188, y 38~164，取大区 122×126）
    private static final int ANIM_X = 68;
    private static final int ANIM_Y = 38;
    private static final int ANIM_W = 122;
    private static final int ANIM_H = 126;

    public HelicaseScreen(SequenceMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (menu.getKind() != SequenceMachineKind.HELICASE) {
            return;
        }
        // 覆写槽位坐标为 guiv1 布局（左侧单卡，右侧垂直双卡），避免父类按 SequenceMachineMenu.OUT_X=70 定位
        if (!inputCards.isEmpty()) {
            Slot s0 = menu.getSlot(inputCards.get(0).containerSlot());
            s0.x = LEFT_X + SequenceMachineMenu.SLOT_X;
            s0.y = LEFT_Y + SequenceMachineMenu.SLOT_Y;
        }
        if (outputCards.size() >= 2) {
            Slot s1 = menu.getSlot(outputCards.get(0).containerSlot());
            Slot s2 = menu.getSlot(outputCards.get(1).containerSlot());
            s1.x = RIGHT_X + SequenceMachineMenu.SLOT_X;
            s1.y = RIGHT_Y + SequenceMachineMenu.SLOT_Y;
            s2.x = RIGHT_X + SequenceMachineMenu.SLOT_X;
            s2.y = RIGHT_Y + SequenceMachineMenu.SLOT_Y + SequenceMachineMenu.CARD_STEP;
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (menu.getKind() != SequenceMachineKind.HELICASE) {
            super.renderBg(graphics, partialTick, mouseX, mouseY);
            return;
        }
        // guiv1 基底
        graphics.blit(GUI_V1, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        drawStatusBar(graphics);
        drawHelicaseLabels(graphics);
        drawHelicaseInputCards(graphics);
        drawHelicaseOutputCards(graphics);
        drawHelicaseAnimation(graphics);
    }

    /** 状态栏复用父类逻辑但保持 guiv1 标题区 */
    private void drawStatusBar(GuiGraphics graphics) {
        // 复用父类的 drawStatusBar 逻辑：标题 + 状态 + 进度条（y 13/22）
        // 父类为 private， here reimplement简版
        graphics.drawString(font, title, leftPos + 8, topPos + 13, NAME_COLOR, false);
        int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
        int position = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        String status = switch (stage) {
            case 0 -> "IDLE";
            case 1 -> "UNWIND " + position + "/" + total;
            case 2 -> "DONE";
            default -> "IDLE";
        };
        graphics.drawString(font, status, leftPos + imageWidth - 8 - font.width(status), topPos + 13, CONC_TEXT_COLOR, false);
        int fill = total > 0 ? (int) ((imageWidth - 16) * position / (double) total) : 0;
        graphics.fill(leftPos + 8, topPos + 22, leftPos + 8 + imageWidth - 16, topPos + 25, BAR_TRACK);
        if (fill > 0) {
            graphics.fill(leftPos + 8, topPos + 22, leftPos + 8 + fill, topPos + 25, 0xFF7ED6DF);
        }
    }

    private void drawHelicaseLabels(GuiGraphics graphics) {
        graphics.drawString(font, "INPUT", leftPos + 9, topPos + 30, NAME_COLOR, false);
        graphics.drawString(font, "OUTPUT", leftPos + 195, topPos + 30, NAME_COLOR, false);
        graphics.drawString(font, "UNWIND", leftPos + 109, topPos + 30, NAME_COLOR, false);
    }

    private void drawHelicaseInputCards(GuiGraphics graphics) {
        if (inputCards.isEmpty()) return;
        int areaX = leftPos + LEFT_X;
        int areaY = topPos + LEFT_Y;
        graphics.enableScissor(areaX, areaY, areaX + LEFT_W, areaY + LEFT_H);
        for (InputCard card : inputCards) {
            Slot slot = menu.getSlot(card.containerSlot());
            // 输入 DNA 用 DNA 卡渲染（标签简化为 DNA，三卡均滚动）
            drawHelicaseDnaCard(graphics, areaX, areaY, LEFT_W, 28, slot, "DNA");
        }
        graphics.disableScissor();
    }

    private void drawHelicaseOutputCards(GuiGraphics graphics) {
        if (outputCards.isEmpty()) return;
        int areaX = leftPos + RIGHT_X;
        int areaY = topPos + RIGHT_Y;
        graphics.enableScissor(areaX, areaY, areaX + RIGHT_W, areaY + LEFT_H);
        // 垂直双卡：y 0 与 y 29，标签改为模板链/非模板链（专有名词）
        for (int i = 0; i < outputCards.size(); i++) {
            OutputCard card = outputCards.get(i);
            int cardY = areaY + i * SequenceMachineMenu.CARD_STEP;
            Slot slot = menu.getSlot(card.containerSlot());
            String label = i == 0 ? "模板链" : "非模板链";
            drawHelicaseDnaCard(graphics, areaX, cardY, RIGHT_W, 28, slot, label);
        }
        graphics.disableScissor();
    }

    /** 覆写父类输入卡绘制：helicase 时走 guiv1 单卡，否则委派 */
    @Override
    protected void drawInputCards(GuiGraphics graphics) {
        if (menu.getKind() == SequenceMachineKind.HELICASE) {
            drawHelicaseInputCards(graphics);
            return;
        }
        super.drawInputCards(graphics);
    }

    @Override
    protected void drawOutputCards(GuiGraphics graphics) {
        if (menu.getKind() == SequenceMachineKind.HELICASE) {
            drawHelicaseOutputCards(graphics);
            return;
        }
        super.drawOutputCards(graphics);
    }

    @Override
    protected void drawEditPanel(GuiGraphics graphics) {
        if (menu.getKind() == SequenceMachineKind.HELICASE) {
            drawHelicaseAnimation(graphics);
            return;
        }
        super.drawEditPanel(graphics);
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
        int barY = cardY + SequenceMachineMenu.SLOT_PNG_Y + 18 + 1;
        int fill = seq.isEmpty() ? 0 : cardW - 2;
        graphics.fill(cardX + 1, barY, cardX + 1 + cardW - 2, barY + 2, BAR_TRACK);
        if (fill > 0) {
            graphics.fill(cardX + 1, barY, cardX + 1 + fill, barY + 2, 0xFF7ED6DF);
        }
    }

    /** 中央大动画区：待机双螺旋/解旋中分叉+逐碱基对显示/完成平行 */
    private void drawHelicaseAnimation(GuiGraphics graphics) {
        int x = leftPos + ANIM_X;
        int y = topPos + ANIM_Y;
        int w = ANIM_W;
        int h = ANIM_H;
        // 背景
        graphics.fill(x, y, x + w, y + h, EDIT_PANEL_COLOR);
        graphics.fill(x, y, x + w, y + 1, 0xFF3A3A3A);

        int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
        int pos = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        boolean isUnwinding = stage == 1;
        boolean isDone = stage == 2;
        // 标题
        graphics.drawString(font, "解旋", x + 6, y + 6, 0xFFE0E0E0, false);
        String status = isUnwinding ? "解旋中 " + pos + "/" + total : isDone ? "完成" : "待机";
        graphics.drawString(font, status, x + w - 6 - font.width(status), y + 6, 0xFF9E9E9E, false);

        int cx = x + w / 2;
        int cy = y + h / 2 + 10;
        int tick = net.minecraft.client.Minecraft.getInstance().gui.getGuiTicks();
        double wave = Math.sin(tick * 0.25) * 2;

        // 获取当前解旋序列与当前碱基对（用于动态显示）
        String chain = "";
        char aBase = '?', bBase = '?';
        if (isUnwinding && total > 0) {
            // 从 BE 的 chain 取当前碱基对：需读取输入序列的第 pos 个碱基
            // menu 无法直接取 chain，改从输入槽或输出槽的序列反推：输出 A 的 seq 长度 = pos，取末位
            Slot outA = menu.getSlot(1);
            SequenceData outData = outA.getItem().get(ModDataComponents.SEQUENCE.get());
            if (outData != null && !outData.seq().isEmpty()) {
                chain = outData.seq();
                aBase = chain.charAt(chain.length() - 1);
                bBase = com.github.crafteve.biocraft.seq.SeqOps.complementDna(aBase);
            }
        } else if (isDone) {
            Slot outA = menu.getSlot(1);
            SequenceData outData = outA.getItem().get(ModDataComponents.SEQUENCE.get());
            if (outData != null) chain = outData.seq();
        }

        if (isUnwinding) {
            // 解旋中：中央分叉，两链分开 + 当前碱基对高亮
            graphics.fill(cx - 32, cy - 16, cx - 30, cy + 28, 0xFF4FC3F7);
            for (int i = 0; i < 4; i++) {
                int by = cy - 12 + i * 10;
                graphics.fill(cx - 28 + (int) wave, by, cx - 22 + (int) wave, by + 2, BASE_A);
                graphics.fill(cx + 22 - (int) wave, by, cx + 28 - (int) wave, by + 2, BASE_T);
            }
            graphics.fill(cx + 30, cy - 16, cx + 32, cy + 28, 0xFF81C784);
            // 分叉点闪烁 + 当前碱基对
            int flash = (tick / 6) % 2 == 0 ? 0xFFFFFF00 : 0xFFFFE082;
            graphics.fill(cx - 3, cy - 3, cx + 3, cy + 3, flash);
            if (aBase != '?') {
                String pair = "" + aBase + "–" + bBase;
                int pw = font.width(pair);
                // 放大显示当前碱基对
                graphics.drawString(font, pair, cx - pw / 2, cy - 28, 0xFFFFF59D, false);
                // 两链末端碱基小标签
                graphics.drawString(font, String.valueOf(aBase), cx - 36, cy + 30, BASE_A, false);
                graphics.drawString(font, String.valueOf(bBase), cx + 30, cy + 30, BASE_T, false);
            }
            graphics.drawString(font, "⇄", cx - 4, cy - 36, 0xFFE0E0E0, false);
        } else if (isDone) {
            graphics.fill(cx - 28, cy - 16, cx - 26, cy + 28, 0xFF4FC3F7);
            graphics.fill(cx + 26, cy - 16, cx + 28, cy + 28, 0xFF81C784);
            // 完成态：两链平行，无文字（已移除 ⇉ 2× ssDNA，避免遮挡）
        } else {
            // 待机：双螺旋波浪
            for (int i = 0; i < 6; i++) {
                int by = cy - 18 + i * 8;
                int off = (int) (Math.sin((tick + i * 8) * 0.15) * 10);
                graphics.fill(cx + off - 1, by, cx + off + 1, by + 2, i % 2 == 0 ? 0xFF4FC3F7 : 0xFF81C784);
                graphics.fill(cx - off - 1, by, cx - off + 1, by + 2, i % 2 == 0 ? 0xFF81C784 : 0xFF4FC3F7);
            }
            graphics.drawString(font, "DNA", cx - 10, cy - 34, 0xFF90A4AE, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // guiv1 标题已在 drawStatusBar 绘制，屏蔽父类的 encoder 专用 bp 预览
        if (menu.getKind() != SequenceMachineKind.HELICASE) {
            super.renderLabels(graphics, mouseX, mouseY);
        }
    }
}
