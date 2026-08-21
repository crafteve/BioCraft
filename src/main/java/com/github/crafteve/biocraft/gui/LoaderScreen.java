package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.blockentity.LoaderOperation;
import com.github.crafteve.biocraft.blockentity.SequenceMachineKind;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.item.MoleculeItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 装载机屏幕：左 INPUT 3 槽（tRNA/AA/ATP）中动画区 122x126 显示当前装载 AA
 */
public class LoaderScreen extends SequenceMachineScreen {

    private static final net.minecraft.resources.ResourceLocation GUI_V1 =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.github.crafteve.biocraft.BioCraft.MODID, "textures/gui/gui_v1.png");

    public LoaderScreen(SequenceMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        if (menu.getKind() == SequenceMachineKind.LOADER) containerTick();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (menu.getKind() != SequenceMachineKind.LOADER) return;
        // guiv1 布局：左 3 INPUT 右 3 OUTPUT，覆盖父类 70,140 横向定位
        for (int i = 0; i < inputCards.size(); i++) {
            var slot = menu.getSlot(inputCards.get(i).containerSlot());
            slot.x = 7 + SequenceMachineMenu.SLOT_X;
            slot.y = 41 + i * SequenceMachineMenu.CARD_STEP + SequenceMachineMenu.SLOT_Y;
        }
        for (int i = 0; i < outputCards.size(); i++) {
            var slot = menu.getSlot(outputCards.get(i).containerSlot());
            slot.x = 193 + SequenceMachineMenu.SLOT_X;
            slot.y = 41 + i * SequenceMachineMenu.CARD_STEP + SequenceMachineMenu.SLOT_Y;
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (menu.getKind() != SequenceMachineKind.LOADER) {
            super.renderBg(graphics, partialTick, mouseX, mouseY);
            return;
        }
        graphics.blit(GUI_V1, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        drawLoaderStatusBar(graphics);
        graphics.drawString(font, "INPUT", leftPos + 9, topPos + 30, NAME_COLOR, false);
        graphics.drawString(font, "OUTPUT", leftPos + 195, topPos + 30, NAME_COLOR, false);
        graphics.drawString(font, "LOAD", leftPos + 109, topPos + 30, NAME_COLOR, false);
        drawLoaderInputCards(graphics);
        drawLoaderOutputCards(graphics);
        drawLoaderAnimation(graphics);
    }

    private void drawLoaderStatusBar(GuiGraphics graphics) {
        graphics.drawString(font, title, leftPos + 8, topPos + 13, NAME_COLOR, false);
        int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
        int pos = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        String status = switch (stage) {
            case 0 -> "IDLE";
            case 1 -> "LOAD";
            case 2 -> "DONE";
            default -> "IDLE";
        };
        graphics.drawString(font, status, leftPos + imageWidth - 8 - font.width(status), topPos + 13, CONC_TEXT_COLOR, false);
    }

    private void drawLoaderInputCards(GuiGraphics g) {
        int areaX = leftPos + 7; int areaY = topPos + 41;
        g.enableScissor(areaX, areaY, areaX + 56, areaY + 112);
        for (int i = 0; i < inputCards.size(); i++) {
            var card = inputCards.get(i); var slot = menu.getSlot(card.containerSlot());
            int cardY = areaY + i * SequenceMachineMenu.CARD_STEP;
            drawLoaderCard(g, areaX, cardY, 56, 28, card.itemId(), slot, true);
        }
        g.disableScissor();
    }

    private void drawLoaderOutputCards(GuiGraphics g) {
        int areaX = leftPos + 193; int areaY = topPos + 41;
        g.enableScissor(areaX, areaY, areaX + 56, areaY + 112);
        for (int i = 0; i < outputCards.size(); i++) {
            var card = outputCards.get(i); var slot = menu.getSlot(card.containerSlot());
            int cardY = areaY + i * SequenceMachineMenu.CARD_STEP;
            drawLoaderCard(g, areaX, cardY, 56, 28, card.itemId(), slot, false);
        }
        g.disableScissor();
    }

    private void drawLoaderCard(GuiGraphics g, int cardX, int cardY, int cardW, int cardH, String itemId, Slot slot, boolean isInput) {
        g.fill(cardX, cardY, cardX + cardW, cardY + cardH, CARD_COLOR);
        int pngX = cardX + SequenceMachineMenu.SLOT_PNG_X; int pngY = cardY + SequenceMachineMenu.SLOT_PNG_Y;
        g.blit(SLOT_TEX, pngX, pngY, 0, 0, 18, 18, 18, 18);
        ItemStack stack = slot.getItem();
        String abbr;
        int tint;
        if (slot.index == LoaderOperation.SLOT_TRNA) {
            abbr = "tRNA"; tint = 0xB0C4DE;
        } else if (slot.index == LoaderOperation.SLOT_OUT_AATRNA) {
            if (!stack.isEmpty() && stack.getItem() instanceof com.github.crafteve.biocraft.item.MoleculeItem mi) {
                abbr = "tRNA"; tint = mi.getTintColor();
            } else {
                abbr = "tRNA"; tint = 0xCCCCCC;
            }
        } else if (!stack.isEmpty()) {
            if (stack.getItem() instanceof com.github.crafteve.biocraft.item.MoleculeItem mi) {
                abbr = mi.getAbbreviation(); tint = mi.getTintColor();
            } else {
                abbr = stack.getHoverName().getString(); tint = 0xCCCCCC;
            }
        } else {
            if (slot.index == LoaderOperation.SLOT_AA) { abbr = "aa"; tint = 0xCCCCCC; }
            else {
                var di = com.github.crafteve.biocraft.init.ModItems.byId(itemId);
                if (di != null) { var mi = di.get(); abbr = mi.getAbbreviation(); tint = mi.getTintColor(); }
                else if ("trna".equals(itemId)) { abbr = "tRNA"; tint = 0xB0C4DE; }
                else { abbr = itemId; tint = 0xCCCCCC; }
            }
        }
        int color = stack.isEmpty() ? CONC_TEXT_COLOR : cardTextColor(tint);
        g.drawString(font, abbr, pngX + 18 + 4, pngY, color, false);
        double rem = menu.getRemainder(slot.index);
        double total = isInput ? Math.max(0, stack.getCount() - rem) : stack.getCount() + rem;
        int barY = cardY + SequenceMachineMenu.SLOT_PNG_Y + 18 + (8 - 3) / 2;
        int fill = (int) Math.min((cardW - 2) * total / 64.0, cardW - 2);
        g.fill(cardX + 1, barY, cardX + 1 + cardW - 2, barY + 3, BAR_TRACK);
        if (fill > 0) g.fill(cardX + 1, barY, cardX + 1 + fill, barY + 3, color);
        String countText = total >= 100 ? String.format("%.1f", total) : String.format("%.2f", total);
        g.drawString(font, "x" + countText, pngX + 18 + 4, pngY + 18 + 1 - 8, CONC_TEXT_COLOR, false);
    }

    // guiv1 动画区常量（复用 helicase）
    private static final int ANIM_X = 68;
    private static final int ANIM_Y = 38;
    private static final int ANIM_W = 122;
    private static final int ANIM_H = 126;

    private void drawLoaderAnimation(GuiGraphics g) {
        int x = leftPos + ANIM_X;
        int y = topPos + ANIM_Y;
        int w = ANIM_W;
        int h = ANIM_H;
        g.fill(x, y, x + w, y + h, EDIT_PANEL_COLOR);
        g.fill(x, y, x + w, y + 1, 0xFF3A3A3A);
        for (int gx = x + 12; gx < x + w; gx += 14) g.fill(gx, y + 12, gx + 1, y + h - 6, 0x08FFFFFF);
        for (int gy = y + 18; gy < y + h; gy += 14) g.fill(x + 6, gy, x + w - 6, gy + 1, 0x08FFFFFF);

        int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
        boolean running = stage == 1;
        int tick = net.minecraft.client.Minecraft.getInstance().gui.getGuiTicks();
        Slot aaSlot = menu.getSlot(LoaderOperation.SLOT_AA);
        ItemStack aaStack = aaSlot.getItem();
        int aaTint = 0xFF7CFC00;
        String abbr = "aa";
        if (!aaStack.isEmpty() && aaStack.getItem() instanceof MoleculeItem mi) {
            aaTint = mi.getTintColor() | 0xFF000000;
            abbr = mi.getAbbreviation();
        }
        double pulse = Math.sin(tick * 0.4) * 0.3 + 0.7;

        // 顶标题同 helicase 转录：左转化右状态
        g.drawString(font, "tRNA→aa-tRNA", x + 6, y + 6, 0xFFE0E0E0, false);
        String st = running ? "LOAD" : stage == 2 ? "DONE" : "IDLE";
        g.drawString(font, st, x + w - 6 - font.width(st), y + 6, 0xFF9E9E9E, false);

        // 双步标题
        g.drawString(font, "ATP+aa→aa-AMP+PPi", x + 6, y + 18, 0xFFB0BEC5, false);
        g.drawString(font, "aa-AMP+tRNA→aa-tRNA+AMP", x + 6, y + 64, 0xFFB0BEC5, false);

        // 轨道底
        int yU = y + 36;
        int yL = y + 82;
        g.fill(x + 6, yU - 2, x + w - 6, yU + 10, 0xFF2A2A2E);
        g.fill(x + 6, yL - 2, x + w - 6, yL + 10, 0xFF2A2A2E);
        // 轨道标签
        g.drawString(font, "上游", x + 6, yU - 11, 0xFFF1C40F, false);
        g.drawString(font, "下游", x + 6, yL - 11, 0xFF81C784, false);

        int cx = x + w / 2;
        // 静止态：点阵三叶草与原料点
        // 左 tRNA 点阵
        int lx = x + 14;
        drawClover(g, lx + 10, yL, 0xFFB0BEC5, false, 0);
        // 右 aa-tRNA 点阵
        int rx = x + w - 28;
        drawClover(g, rx + 10, yL, aaTint, !running && stage == 2 ? true : false, aaTint);

        if (!running) {
            // 静止只展示原料点，不播放
            g.fill(x + 14, yU + 3, x + 16, yU + 5, aaTint);
            g.fill(x + 22, yU + 3, x + 24, yU + 5, 0xFFF1C40F);
            g.fill(x + 26, yU + 3, x + 28, yU + 5, 0xFFF1C40F);
            g.fill(x + 30, yU + 3, x + 32, yU + 5, 0xFFF1C40F);
            g.drawString(font, abbr, x + 12, yU - 12, aaTint, false);
            return;
        }

        // 独立 20 tick 循环，停机立刻停（与转录 helicase 同 tick 源，不绑机器速度）
        int t = tick % 20;
        int glow = (int) (180 * pulse) << 24 | 0x00FFFFFF;

        if (t < 10) {
            // 上游 0-9：aa 点与 ATP 三磷点平移合成，动效代替箭头
            int prog = t * 6;
            int ax = x + 14 + prog;
            int bx = x + 22 + prog;
            // aa 点带 glow
            g.fill(ax - 1, yU - 1, ax + 3, yU + 6, glow);
            g.fill(ax, yU + 3, ax + 2, yU + 5, aaTint);
            // ATP 三磷
            g.fill(bx, yU + 3, bx + 2, yU + 5, 0xFFF1C40F);
            g.fill(bx + 4, yU + 3, bx + 6, yU + 5, 0xFFF1C40F);
            g.fill(bx + 8, yU + 3, bx + 10, yU + 5, 0xFFF1C40F);
            // 虚线轨迹
            for (int dx = 0; dx < prog; dx += 4) g.fill(x + 14 + dx, yU + 6, x + 15 + dx, yU + 7, 0x33FFFFFF);
            if (t >= 7) {
                // 合成瞬间 aa-AMP 键闪
                int mx = cx - 6;
                g.fill(mx, yU + 4, mx + 8, yU + 5, 0xFFF1C40F);
                g.fill(mx + 2, yU + 2, mx + 4, yU + 6, aaTint);
            }
            if (t >= 8) {
                // PPi 弹出
                int py = yU + 8 + (t - 8) * 2;
                g.fill(cx + 10, py, cx + 12, py + 2, 0xFF9E9E9E);
                g.fill(cx + 14, py, cx + 16, py + 2, 0xFF9E9E9E);
            }
        } else {
            // 下游 10-19：aa-AMP 点群飘向 tRNA 缺口，填色完成
            int pt = t - 10;
            int prog = pt * 6;
            int ax = x + 54 + prog;
            // aa-AMP 点群
            g.fill(ax - 1, yL - 1, ax + 5, yL + 6, glow);
            g.fill(ax, yL + 3, ax + 2, yL + 5, aaTint);
            g.fill(ax + 3, yL + 4, ax + 5, yL + 6, 0xFFF1C40F);
            for (int dx = 0; dx < prog; dx += 4) g.fill(x + 54 + dx, yL + 6, x + 55 + dx, yL + 7, 0x33FFFFFF);
            if (pt >= 6) {
                // 到缺口填色
                g.fill(lx + 9, yL + 4, lx + 11, yL + 6, aaTint);
                // 右 aa-tRNA 亮起
                drawClover(g, rx + 10, yL, aaTint, true, aaTint);
                // AMP 弹出
                int my = yL + 8 + (pt - 6) * 2;
                g.fill(cx + 18, my, cx + 20, my + 2, 0xFF9E9E9E);
                g.fill(cx + 22, my, cx + 24, my + 2, 0xFF9E9E9E);
            }
        }
    }

    private void drawClover(GuiGraphics g, int cx, int cy, int color, boolean filled, int aaTint) {
        // 三叶点阵 Y：6 点围成丫，缺口在下方
        for (int i = 0; i < 6; i++) {
            int py = cy - 8 + i * 3;
            int off = i < 3 ? i : 5 - i;
            int c = filled ? color : 0xFFB0BEC5;
            g.fill(cx - off, py, cx - off + 1, py + 1, c);
            g.fill(cx + off, py, cx + off + 1, py + 1, c);
        }
        if (filled) g.fill(cx - 1, cy + 4, cx + 1, cy + 6, aaTint);
        else g.fill(cx - 1, cy + 4, cx + 1, cy + 6, 0xFF555555);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.getKind() == SequenceMachineKind.LOADER) return;
        super.renderLabels(graphics, mouseX, mouseY);
    }
}
