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
    protected void init() {
        super.init();
        // 仿 dnaEncoder 右下角按钮：转录区右下角“转录”按钮，点击检测条件并启动
        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("转录"), b -> {
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                            new com.github.crafteve.biocraft.network.ServerboundTranscribePacket(this.menu.getPos()));
                })
                .bounds(leftPos + SequenceMachineMenu.EDIT_X + SequenceMachineMenu.EDIT_W - 46,
                        topPos + SequenceMachineMenu.EDIT_Y + SequenceMachineMenu.EDIT_H - 11, 42, 11)
                .build());
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
        // 标题仿酶工厂 slot0 右侧 28 起，避免 9,8 槽覆盖
        graphics.drawString(font, title, leftPos + 28, topPos + 13, NAME_COLOR, false);
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
        // 模板槽背景仿酶工厂 slot0 (9,8)；进度条保持 22-25，标题 8,13 避开槽左侧 9-26 区
        int fill = total > 0 ? (int) ((imageWidth - 16) * pos / (double) total) : 0;
        graphics.fill(leftPos + 8, topPos + 22, leftPos + 8 + imageWidth - 16, topPos + 25, BAR_TRACK);
        if (fill > 0) graphics.fill(leftPos + 8, topPos + 22, leftPos + 8 + fill, topPos + 25, 0xFF7ED6DF);
        graphics.blit(SLOT_TEX, leftPos + 9 - 1, topPos + 8 - 1, 0, 0, 18, 18, 18, 18);
    }

    private void drawTranscriptionPanel(GuiGraphics graphics) {
        int x = leftPos + SequenceMachineMenu.EDIT_X;
        int y = topPos + SequenceMachineMenu.EDIT_Y;
        int w = SequenceMachineMenu.EDIT_W;
        int h = SequenceMachineMenu.EDIT_H;
        graphics.fill(x, y, x + w, y + h, EDIT_PANEL_COLOR);
        graphics.fill(x, y, x + w, y + 1, 0xFF3A3A3A);
        // 顶部状态：标题 + 聚合酶图标
        graphics.drawString(font, "转录", x + 6, y + 6, 0xFFE0E0E0, false);
        int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
        int pos = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        boolean running = stage == 1 && total > 0;
        int tick = net.minecraft.client.Minecraft.getInstance().gui.getGuiTicks();
        int px = x + w - 18;
        int py = y + 6;
        graphics.fill(px, py, px + 10, py + 10, 0xFF4FC3F7);
        graphics.fill(px + 1, py + 1, px + 9, py + 9, 0xFF0288D1);
        graphics.drawString(font, "P", px + 3, py + 1, 0xFFFFFFFF, false);
        String status = running ? pos + "/" + total : stage == 2 ? "完成" : "待机";
        graphics.drawString(font, status, px - 6 - font.width(status), py + 1, 0xFF9E9E9E, false);

        Slot tmplSlot = menu.getSlot(TranscriptionOperation.SLOT_TEMPLATE);
        ItemStack tmpl = tmplSlot.getItem();
        SequenceData data = tmpl.get(ModDataComponents.SEQUENCE.get());
        String seq = data != null ? data.seq() : "";
        Boolean isTemplate = tmpl.get(ModDataComponents.IS_TEMPLATE.get());
        if (seq.isEmpty()) {
            return;
        }
        if (isTemplate != null && isTemplate) {
            graphics.drawString(font, "编码链不可转录，请放入模板链(3'→5')", x + 6, y + 22, 0xFFE53935, false);
            return;
        }
        String prom = com.github.crafteve.biocraft.seq.SeqOps.PROMOTER_TEMPLATE;
        int idx = seq.indexOf(prom);
        if (idx < 0) {
            graphics.drawString(font, "未找到启动子 " + prom + "（旧链请重制）", x + 6, y + 22, 0xFFE53935, false);
            String head = seq.length() > 28 ? seq.substring(0, 28) + "…" : seq;
            graphics.drawString(font, head, x + 6, y + 34, 0xFFB0BEC5, false);
            return;
        }
        graphics.drawString(font, "启动子@" + idx, x + 6, y + 21, 0xFF7ED6DF, false);
        double pulse = (Math.sin(tick * 0.4) * 0.3 + 0.7);
        int window = Math.min(18, seq.length() - (idx + prom.length()));
        int fromBase = idx + prom.length();
        // 窗口随转录进度自动右移，当前碱基保持在窗口内右侧 1/3
        int cur = running ? Math.max(0, pos - idx - prom.length() - 1) : 0;
        int from = fromBase + Math.max(0, cur - window + 6);
        from = Math.min(from, Math.max(fromBase, seq.length() - window));
        int to = Math.min(seq.length(), from + window);
        String templateSeg = seq.substring(from, to);
        String mrnaSeg = "";
        Slot out = menu.getSlot(TranscriptionOperation.SLOT_OUT_MRNA);
        SequenceData outData = out.getItem().get(ModDataComponents.SEQUENCE.get());
        if (outData != null) {
            mrnaSeg = outData.seq();
            // mRNA 窗口与模板同步偏移，保持配对对齐
            int mrnaFrom = Math.max(0, cur - window + 6);
            mrnaFrom = Math.min(mrnaFrom, Math.max(0, mrnaSeg.length() - window));
            if (mrnaSeg.length() > window) mrnaSeg = mrnaSeg.substring(mrnaFrom, Math.min(mrnaSeg.length(), mrnaFrom + window));
        }
        int autoScroll = running ? (pos * 2) % 8 : 0;
        int baseX0 = x + 16 - autoScroll;
        int templY = y + 42;
        int mrnaY = y + 62;
        int pairY = y + 54;
        graphics.fill(x + 6, templY - 2, x + w - 6, templY + 10, 0xFF2A2A2E);
        graphics.fill(x + 6, mrnaY - 2, x + w - 6, mrnaY + 10, 0xFF2A2A2E);
        graphics.drawString(font, "模板", x + 6, templY - 11, 0xFF81C784, false);
        graphics.drawString(font, "mRNA", x + 6, mrnaY + 11, 0xFFF1C40F, false);
        int curInWindow = cur - (from - fromBase);
        for (int i = 0; i < templateSeg.length() && baseX0 + i * 8 < x + w - 10; i++) {
            char tBase = templateSeg.charAt(i);
            char mBase = i < mrnaSeg.length() ? mrnaSeg.charAt(i) : '?';
            boolean isCurrent = running && i == curInWindow && i < mrnaSeg.length();
            int tColor = switch (tBase) {
                case 'A' -> BASE_A; case 'T' -> BASE_T; case 'C' -> BASE_C; case 'G' -> BASE_G; default -> 0xFF9E9E9E;
            };
            int mColor = switch (mBase) {
                case 'A' -> BASE_A; case 'U' -> BASE_T; case 'C' -> BASE_C; case 'G' -> BASE_G; default -> 0xFF5A5A5A;
            };
            int bx = baseX0 + i * 8;
            if (isCurrent) {
                int glow = (int) (180 * pulse) << 24 | 0x00FFFFFF;
                graphics.fill(bx - 1, templY - 1, bx + 7, templY + 9, glow);
                graphics.fill(bx - 1, mrnaY - 1, bx + 7, mrnaY + 9, glow);
            }
            graphics.drawString(font, String.valueOf(tBase), bx, templY, tColor, false);
            graphics.drawString(font, String.valueOf(mBase == '?' ? "·" : mBase), bx, mrnaY, isCurrent ? 0xFFFFFFFF : mColor, false);
            int lineColor = isCurrent ? 0xFFFFFF00 : 0xFF555555;
            graphics.fill(bx + 3, pairY, bx + 4, pairY + 4, lineColor);
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
            boolean isMissing = false;
            if (data == null || data.seq().isEmpty()) {
                err = "未放ssDNA模板链";
                isMissing = true;
            } else if (isTemplate != null && isTemplate) {
                err = "编码链不可转录，请放入模板链(3'→5')";
            } else if (!data.seq().contains(com.github.crafteve.biocraft.seq.SeqOps.PROMOTER_TEMPLATE)) {
                err = "未找到启动子 " + com.github.crafteve.biocraft.seq.SeqOps.PROMOTER_TEMPLATE + "（旧链请重制）";
            }
            if (!err.isEmpty()) {
                int x = leftPos + SequenceMachineMenu.EDIT_X + 3;
                int y = topPos + SequenceMachineMenu.EDIT_Y + SequenceMachineMenu.EDIT_H - 9;
                int barColor = isMissing ? 0xFF9E9E9E : 0xFFE53935;
                int textColor = isMissing ? 0xFF707070 : 0xFFFFFFFF;
                String prefix = isMissing ? "§7" : "§c";
                graphics.fill(x, y, x + 1, y + 8, barColor);
                graphics.drawString(font, "!", x + 3, y, textColor, false);
                if (mouseX >= x && mouseX < x + 8 && mouseY >= y && mouseY < y + 8) {
                    graphics.renderTooltip(font, java.util.List.of(Component.literal(prefix + err)), java.util.Optional.empty(), mouseX, mouseY);
                }
            }
        }
    }
}
