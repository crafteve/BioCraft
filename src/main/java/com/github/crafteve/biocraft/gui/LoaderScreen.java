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

    public LoaderScreen(SequenceMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (menu.getKind() != SequenceMachineKind.LOADER) {
            super.renderBg(graphics, partialTick, mouseX, mouseY);
            return;
        }
        graphics.blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        // 状态栏
        graphics.drawString(font, title, leftPos + 8, topPos + 13, NAME_COLOR, false);
        int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
        int pos = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        String status = switch (stage) {
            case 0 -> "IDLE";
            case 1 -> "LOAD " + pos + "/" + total;
            case 2 -> "DONE";
            default -> "IDLE";
        };
        graphics.drawString(font, status, leftPos + imageWidth - 8 - font.width(status), topPos + 13, CONC_TEXT_COLOR, false);
        int fill = total > 0 ? (int) ((imageWidth - 16) * pos / (double) total) : 0;
        graphics.fill(leftPos + 8, topPos + 22, leftPos + 8 + imageWidth - 16, topPos + 25, BAR_TRACK);
        if (fill > 0) graphics.fill(leftPos + 8, topPos + 22, leftPos + 8 + fill, topPos + 25, 0xFF7ED6DF);
        graphics.drawString(font, "INPUT", leftPos + 9, topPos + 30, NAME_COLOR, false);
        graphics.drawString(font, "OUTPUT", leftPos + SequenceMachineMenu.OUTPUT_LABEL_X, topPos + SequenceMachineMenu.OUTPUT_LABEL_Y, NAME_COLOR, false);
        drawInputCards(graphics);
        drawOutputCards(graphics);
        drawLoaderAnimation(graphics);
    }

    private void drawLoaderAnimation(GuiGraphics graphics) {
        int x = leftPos + SequenceMachineMenu.EDIT_X;
        int y = topPos + SequenceMachineMenu.EDIT_Y;
        int w = SequenceMachineMenu.EDIT_W;
        int h = SequenceMachineMenu.EDIT_H;
        graphics.fill(x, y, x + w, y + h, EDIT_PANEL_COLOR);
        graphics.fill(x, y, x + w, y + 1, 0xFF3A3A3A);
        int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
        boolean running = stage == 1;
        int tick = net.minecraft.client.Minecraft.getInstance().gui.getGuiTicks();
        // 当前 AA
        Slot aaSlot = menu.getSlot(LoaderOperation.SLOT_AA);
        ItemStack aaStack = aaSlot.getItem();
        String aaName = aaStack.isEmpty() ? "等待 AA" : aaStack.getHoverName().getString();
        int color = 0xFFB0BEC5;
        if (!aaStack.isEmpty() && aaStack.getItem() instanceof MoleculeItem mi) {
            color = mi.getTintColor() | 0xFF000000;
        }
        graphics.drawString(font, aaName, x + 6, y + 6, color, false);
        // 中央 tRNA 图标（染色为 AA 颜色）
        int cx = x + w / 2;
        int cy = y + h / 2;
        int pulse = running ? (int) (Math.sin(tick * 0.3) * 2) : 0;
        graphics.fill(cx - 20 + pulse, cy - 12, cx + 20 + pulse, cy + 12, color & 0x44FFFFFF);
        graphics.drawString(font, "tRNA", cx - 12 + pulse, cy - 4, color, false);
        graphics.drawString(font, running ? "装载中..." : "待机", x + 6, y + h - 12, 0xFF9E9E9E, false);
        // 速度：简单显示 tick 脉冲
        if (running) {
            int bar = (tick % 20) * (w - 12) / 20;
            graphics.fill(x + 6, y + h - 6, x + 6 + bar, y + h - 4, color);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.getKind() == SequenceMachineKind.LOADER) return;
        super.renderLabels(graphics, mouseX, mouseY);
    }
}
