package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.blockentity.SequenceMachineKind;
import com.github.crafteve.biocraft.blockentity.TranscriptionOperation;
import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.seq.SequenceData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 转录仪屏幕（重做：encoder 风格，状态栏模板槽 + 左 NTP/ATP + 右 mRNA/PPi + 启动子检查）
 */
public class TranscriberScreen extends SequenceMachineScreen {

    public TranscriberScreen(SequenceMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (menu.getKind() != SequenceMachineKind.TRANSCRIBER) {
            super.renderBg(graphics, partialTick, mouseX, mouseY);
            return;
        }
        graphics.blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        drawStatusBarWithTemplate(graphics);
        graphics.drawString(font, "INPUT", leftPos + 9, topPos + 30, NAME_COLOR, false);
        graphics.drawString(font, "OUTPUT", leftPos + SequenceMachineMenu.OUTPUT_LABEL_X, topPos + SequenceMachineMenu.OUTPUT_LABEL_Y, NAME_COLOR, false);
        drawInputCards(graphics);
        drawOutputCards(graphics);
        drawTranscriptionPanel(graphics);
    }

    private void drawStatusBarWithTemplate(GuiGraphics graphics) {
        graphics.drawString(font, title, leftPos + 8, topPos + 13, NAME_COLOR, false);
        int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
        int pos = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        String status = switch (stage) {
            case 0 -> "IDLE";
            case 1 -> "TRANSCRIBE " + pos + "/" + total;
            case 2 -> "DONE";
            default -> "IDLE";
        };
        graphics.drawString(font, status, leftPos + imageWidth - 8 - font.width(status), topPos + 13, CONC_TEXT_COLOR, false);
        // 进度条下移至 26-29，避开顶栏槽 7-25 的 3px 重叠（槽 120,8 占 7-25）
        int fill = total > 0 ? (int) ((imageWidth - 16) * pos / (double) total) : 0;
        graphics.fill(leftPos + 8, topPos + 26, leftPos + 8 + imageWidth - 16, topPos + 29, BAR_TRACK);
        if (fill > 0) graphics.fill(leftPos + 8, topPos + 26, leftPos + 8 + fill, topPos + 29, 0xFF7ED6DF);
        // 模板槽背景（顶栏中部 120,8，不挡标题/进度，INPUT 保持 9,30）
        graphics.blit(SLOT_TEX, leftPos + 120 - 1, topPos + 8 - 1, 0, 0, 18, 18, 18, 18);
    }

    private void drawTranscriptionPanel(GuiGraphics graphics) {
        int x = leftPos + SequenceMachineMenu.EDIT_X;
        int y = topPos + SequenceMachineMenu.EDIT_Y;
        int w = SequenceMachineMenu.EDIT_W;
        int h = SequenceMachineMenu.EDIT_H;
        graphics.fill(x, y, x + w, y + h, EDIT_PANEL_COLOR);
        graphics.fill(x, y, x + w, y + 1, 0xFF3A3A3A);
        // 模板序列预览（取状态栏模板槽）
        Slot tmplSlot = menu.getSlot(TranscriptionOperation.SLOT_TEMPLATE);
        ItemStack tmpl = tmplSlot.getItem();
        SequenceData data = tmpl.get(ModDataComponents.SEQUENCE.get());
        String seq = data != null ? data.seq() : "";
        Boolean isTemplate = tmpl.get(ModDataComponents.IS_TEMPLATE.get());
        String label = isTemplate != null && !isTemplate ? "模板 3'→5'" : "模板 5'→3'";
        graphics.drawString(font, label, x + 6, y + 6, 0xFFE0E0E0, false);
        if (!seq.isEmpty()) {
            String head = seq.length() > 30 ? seq.substring(0, 30) + "…" : seq;
            graphics.drawString(font, head, x + 6, y + 18, 0xFFB0BEC5, false);
            if (isTemplate != null && isTemplate) {
                graphics.drawString(font, "编码链不可转录，请放入模板链(3'→5')", x + 6, y + 30, 0xFFE53935, false);
            } else {
                String prom = com.github.crafteve.biocraft.seq.SeqOps.PROMOTER_TEMPLATE;
                int idx = seq.indexOf(prom);
                if (idx >= 0) {
                    graphics.drawString(font, "启动子@" + idx, x + 6, y + 30, 0xFF7ED6DF, false);
                } else {
                    graphics.drawString(font, "未找到启动子 " + prom + "（旧链请重制）", x + 6, y + 30, 0xFFE53935, false);
                }
            }
        } else {
            graphics.drawString(font, "放入模板 dna_single（模板链）", x + 6, y + 18, 0xFF6A9955, false);
        }
        // 当前转录碱基
        int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
        int pos = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        if (stage == 1 && total > 0) {
            String chain = "";
            Slot out = menu.getSlot(TranscriptionOperation.SLOT_OUT_MRNA);
            SequenceData outData = out.getItem().get(ModDataComponents.SEQUENCE.get());
            if (outData != null) chain = outData.seq();
            if (!chain.isEmpty()) {
                char base = chain.charAt(Math.min(pos - 1, chain.length() - 1));
                int color = switch (base) {
                    case 'A' -> BASE_A;
                    case 'U' -> BASE_T;
                    case 'C' -> BASE_C;
                    case 'G' -> BASE_G;
                    default -> 0xFFE0E0E0;
                };
                graphics.drawString(font, "当前 " + base, x + w - 40, y + 6, color, false);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.getKind() == SequenceMachineKind.TRANSCRIBER) {
            // 屏蔽父类 encoder 的 bp 预览，状态栏已 handling
            return;
        }
        super.renderLabels(graphics, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (menu.getKind() == SequenceMachineKind.TRANSCRIBER) {
            Slot tmplSlot = menu.getSlot(TranscriptionOperation.SLOT_TEMPLATE);
            ItemStack tmpl = tmplSlot.getItem();
            SequenceData data = tmpl.get(ModDataComponents.SEQUENCE.get());
            Boolean isTemplate = tmpl.get(ModDataComponents.IS_TEMPLATE.get());
            String err = "";
            if (data != null) {
                if (isTemplate != null && isTemplate) {
                    err = "编码链不可转录，请放入模板链(3'→5')";
                } else if (!data.seq().contains(com.github.crafteve.biocraft.seq.SeqOps.PROMOTER_TEMPLATE)) {
                    err = "未找到启动子 " + com.github.crafteve.biocraft.seq.SeqOps.PROMOTER_TEMPLATE + "（旧链请重制）";
                }
            }
            if (!err.isEmpty()) {
                int x = leftPos + SequenceMachineMenu.EDIT_X + 3;
                int y = topPos + SequenceMachineMenu.EDIT_Y + SequenceMachineMenu.EDIT_H - 9;
                graphics.fill(x, y, x + 1, y + 8, 0xFFE53935);
                graphics.drawString(font, "!", x + 3, y, 0xFFFFFFFF, false);
                if (mouseX >= x && mouseX < x + 8 && mouseY >= y && mouseY < y + 8) {
                    graphics.renderTooltip(font, java.util.List.of(Component.literal("§c" + err)), java.util.Optional.empty(), mouseX, mouseY);
                }
            }
        }
    }
}
