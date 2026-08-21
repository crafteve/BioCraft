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

    // 动画起点（进入 running 时重置，独立 20 tick 循环不绑机器速度）
    private long animStart = -1;

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
        if (!aaStack.isEmpty() && aaStack.getItem() instanceof MoleculeItem mi) {
            aaTint = mi.getTintColor() | 0xFF000000;
        }
        double pulse = Math.sin(tick * 0.4) * 0.3 + 0.7;

        // 顶标题同 helicase 转录：左转化右状态
        g.drawString(font, "tRNA→aa-tRNA", x + 6, y + 6, 0xFFE0E0E0, false);
        String st = running ? "LOAD" : stage == 2 ? "DONE" : "IDLE";
        g.drawString(font, st, x + w - 6 - font.width(st), y + 6, 0xFF9E9E9E, false);

        // 双步标题（各自轨道上方，不与轨道重叠）
        g.drawString(font, "ATP+aa→aa-AMP+PPi", x + 6, y + 16, 0xFFB0BEC5, false);
        g.drawString(font, "aa-AMP+tRNA→aa-tRNA+AMP", x + 6, y + 56, 0xFFB0BEC5, false);

        // 双轨（高 16，点行在轨内中线）
        int yU = y + 28;
        int yL = y + 68;
        g.fill(x + 6, yU, x + w - 6, yU + 16, 0xFF2A2A2E);
        g.fill(x + 6, yL, x + w - 6, yL + 16, 0xFF2A2A2E);
        int pyU = yU + 7;
        int pyL = yL + 7;

        // 下轨两侧三叶草：左 tRNA 灰（缺口空），右 aa-tRNA 完成态 AA 色
        int lx = x + 24;
        int rx = x + 94;
        drawClover(g, lx, pyL, 0xFFB0BEC5, false, 0);
        boolean done = !running && stage == 2;
        drawClover(g, rx, pyL, aaTint, done, aaTint);

        if (!running) {
            // 静止：上轨展示原料点（aa + ATP 三磷），下轨三叶草已画
            g.fill(x + 16, pyU - 1, x + 18, pyU + 1, aaTint);
            g.fill(x + 24, pyU - 1, x + 26, pyU + 1, 0xFFF1C40F);
            g.fill(x + 28, pyU - 1, x + 30, pyU + 1, 0xFFF1C40F);
            g.fill(x + 32, pyU - 1, x + 34, pyU + 1, 0xFFF1C40F);
            return;
        }

        // 独立 20 tick 循环（进入 running 时从 0 开始），停机立刻停
        if (animStart < 0) animStart = tick;
        int t = (int) ((tick - animStart) % 20);
        int glow = (int) (180 * pulse) << 24 | 0x00FFFFFF;

        if (t < 10) {
            // 上游 0-9：aa 点 + ATP 三磷点同步右移合成 aa-AMP
            int prog = t * 5;
            int ax = x + 16 + prog;
            // aa 点带 glow
            g.fill(ax - 1, pyU - 2, ax + 3, pyU + 4, glow);
            g.fill(ax, pyU - 1, ax + 2, pyU + 1, aaTint);
            // ATP 三磷（黄）
            int bx = ax + 10;
            g.fill(bx, pyU - 1, bx + 2, pyU + 1, 0xFFF1C40F);
            g.fill(bx + 4, pyU - 1, bx + 6, pyU + 1, 0xFFF1C40F);
            g.fill(bx + 8, pyU - 1, bx + 10, pyU + 1, 0xFFF1C40F);
            // 轨迹虚线
            for (int dx = 4; dx < prog; dx += 5) g.fill(x + 16 + dx, pyU + 3, x + 17 + dx, pyU + 4, 0x33FFFFFF);
            if (t >= 8) {
                // 合成：aa 与 ATP 之间黄键线
                g.fill(ax + 2, pyU, bx, pyU + 1, 0xFFF1C40F);
                // PPi 双磷下落
                int fall = (t - 8) * 3;
                g.fill(bx + 12, pyU + 2 + fall, bx + 14, pyU + 4 + fall, 0xFF9E9E9E);
                g.fill(bx + 16, pyU + 2 + fall, bx + 18, pyU + 4 + fall, 0xFF9E9E9E);
            }
        } else {
            // 下游 10-19：aa-AMP 点群左移飘向 tRNA 缺口，填色完成
            int pt = t - 10;
            int prog = pt * 4;
            int ax = x + 60 - prog;
            g.fill(ax - 1, pyL - 2, ax + 5, pyL + 4, glow);
            g.fill(ax, pyL - 1, ax + 2, pyL + 1, aaTint);
            g.fill(ax + 3, pyL, ax + 5, pyL + 2, 0xFFF1C40F);
            // 轨迹虚线（自起点向左延伸）
            for (int dx = 4; dx < prog; dx += 5) g.fill(x + 60 - dx, pyL + 3, x + 61 - dx, pyL + 4, 0x33FFFFFF);
            if (pt >= 8) {
                // 到位：缺口填 AA 色 + 右 aa-tRNA 亮起 + AMP 弹出
                g.fill(lx - 1, pyL + 4, lx + 1, pyL + 6, aaTint);
                drawClover(g, rx, pyL, aaTint, true, aaTint);
                int fall = (pt - 8) * 2;
                g.fill(lx + 6, pyL + 4 + fall, lx + 8, pyL + 6 + fall, 0xFF9E9E9E);
                g.fill(lx + 10, pyL + 4 + fall, lx + 12, pyL + 6 + fall, 0xFF9E9E9E);
            }
        }
    }

    private void drawClover(GuiGraphics g, int cx, int cy, int color, boolean filled, int aaTint) {
        // 三叶点阵 Y：6 点围成丫，缺口在下方（filled 时缺口填 AA 色）
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
