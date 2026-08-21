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
        g.drawString(font, abbr, x + 6, y + 6, aaTint, false);
        String st = running ? "LOAD" : stage == 2 ? "DONE" : "IDLE";
        g.drawString(font, st, x + w - 6 - font.width(st), y + 6, 0xFF9E9E9E, false);
        int cx = x + w / 2;
        int cy = y + h / 2 + 6;
        // 网格
        for (int gx = x + 10; gx < x + w; gx += 16) g.fill(gx, y + 14, gx + 1, y + h - 10, 0x08FFFFFF);
        // 三叶草点阵（左 tRNA 空）
        int lx = cx - 32;
        int rx = cx + 12;
        // 左 tRNA 点阵（灰点围成 Y）
        for (int i = 0; i < 6; i++) {
            int py = cy - 10 + i * 4;
            int off = i < 3 ? i : 5 - i;
            g.fill(lx + 10 - off, py, lx + 11 - off, py + 1, 0xFFB0BEC5);
            g.fill(lx + 10 + off, py, lx + 11 + off, py + 1, 0xFFB0BEC5);
        }
        g.fill(lx + 9, cy + 2, lx + 11, cy + 4, 0xFFB0BEC5);
        // 右 aa-tRNA 点阵（AA 色）
        for (int i = 0; i < 6; i++) {
            int py = cy - 10 + i * 4;
            int off = i < 3 ? i : 5 - i;
            g.fill(rx + 10 - off, py, rx + 11 - off, py + 1, aaTint);
            g.fill(rx + 10 + off, py, rx + 11 + off, py + 1, aaTint);
        }
        g.fill(rx + 9, cy + 2, rx + 11, cy + 4, aaTint);
        if (!running) return;
        // 独立 20 tick 循环，不与机器速度绑定，停机立刻停
        int t = tick % 20;
        // 第一步 0-9 tick：ATP+aa → aa-AMP + PPi（点合成 + PPi 弹出）
        if (t < 10) {
            int prog = t * 3;
            int ax = lx + 18 + prog;
            int ay = cy - 8;
            if (ax < rx - 6) {
                // aa 点
                g.fill(ax, ay, ax + 2, ay + 2, aaTint);
                // ATP 三磷点（黄）
                g.fill(ax + 2, ay + 4, ax + 4, ay + 6, 0xFFF1C40F);
                g.fill(ax + 4, ay + 4, ax + 6, ay + 6, 0xFFF1C40F);
                g.fill(ax + 6, ay + 4, ax + 8, ay + 6, 0xFFF1C40F);
            } else {
                // 合成 aa-AMP 点
                g.fill(rx - 6, ay, rx - 4, ay + 2, aaTint);
                g.fill(rx - 4, ay + 1, rx - 2, ay + 3, 0xFFF1C40F);
                // PPi 双磷弹出
                int py = ay + 8 + (t - 7) * 2;
                g.fill(rx - 2, py, rx, py + 2, 0xFF9E9E9E);
                g.fill(rx + 2, py, rx + 4, py + 2, 0xFF9E9E9E);
            }
        } else {
            // 第二步 10-19 tick：aa-AMP + tRNA → aa-tRNA + AMP
            int prog = (t - 10) * 3;
            int ax = lx + 18 + prog;
            int ay = cy;
            if (ax < lx + 10) {
                // aa-AMP 点群
                g.fill(ax, ay, ax + 2, ay + 2, aaTint);
                g.fill(ax + 2, ay + 1, ax + 4, ay + 3, 0xFFF1C40F);
            } else {
                // 到缺口填色
                g.fill(lx + 9, cy + 2, lx + 11, cy + 4, aaTint);
                // AMP 弹出
                int my = ay + 10 + (t - 15) * 2;
                g.fill(rx + 6, my, rx + 8, my + 2, 0xFF9E9E9E);
                g.fill(rx + 8, my, rx + 10, my + 2, 0xFF9E9E9E);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.getKind() == SequenceMachineKind.LOADER) return;
        super.renderLabels(graphics, mouseX, mouseY);
    }
}
