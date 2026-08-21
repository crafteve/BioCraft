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

    // 动画起点（进入合成时重置，独立 30 tick 循环不绑机器速度）
    private long animStart = -1;
    /** 连续非活跃（IDLE）tick 计数：合成动作出现即清零，累计超 6 tick 动画停止 */
    private int animIdleTicks = 0;

    public LoaderScreen(SequenceMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        if (menu.getKind() == SequenceMachineKind.LOADER) containerTick();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (menu.getKind() != SequenceMachineKind.LOADER) return;
        // 动画活跃度追踪：合成动作（stage 非 IDLE）出现即清零 idle 计数，
        // 累计超过 6 tick 无合成才停止动画（开始即播、停 6tick 后结束）
        int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
        if (stage == 1 || stage == 2) {
            animIdleTicks = 0;
        } else {
            animIdleTicks++;
        }
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
        boolean done = stage == 2;
        // 动画活跃：有合成动作（stage 1/2）即播；无合成动作累计超 6 tick 停止
        boolean animActive = animIdleTicks <= 6;
        int tick = net.minecraft.client.Minecraft.getInstance().gui.getGuiTicks();
        Slot aaSlot = menu.getSlot(LoaderOperation.SLOT_AA);
        ItemStack aaStack = aaSlot.getItem();
        int aaTint = 0xFF7CFC00;
        if (!aaStack.isEmpty() && aaStack.getItem() instanceof MoleculeItem mi) {
            aaTint = mi.getTintColor() | 0xFF000000;
        }
        // 0..1 呼吸曲线（口袋缩放/光环脉动）
        double breath = (Math.sin(tick * 0.35) + 1) * 0.5;

        // 顶标题 + 状态（同 helicase/转录风格）
        g.drawString(font, "装载", x + 6, y + 6, 0xFFE0E0E0, false);
        String st = running ? "LOAD" : done ? "DONE" : "IDLE";
        g.drawString(font, st, x + w - 6 - font.width(st), y + 6, 0xFF9E9E9E, false);

        // 独立 30 tick 循环（合成动作出现时从 0 开始；停超 6 tick 归零停止）
        int t = 0;
        if (animActive) {
            if (animStart < 0) animStart = tick;
            t = (int) ((tick - animStart) % 30);
        } else {
            animStart = -1;
        }

        // 中央装载口袋：大圆点阵描边（空闲灰 / 完成 AA 色），呼吸缩放
        int cx = x + w / 2;
        int cy = y + h / 2 + 2;
        int R = 15 + (int) Math.round(breath * 2);
        boolean loaded = done || (animActive && t >= 14);
        int pocket = loaded ? aaTint : 0xFF7E8EA0;
        for (int i = 0; i < 24; i++) {
            double a = i * (Math.PI * 2 / 24);
            int px = cx + (int) Math.round(Math.cos(a) * R);
            int py = cy + (int) Math.round(Math.sin(a) * R);
            g.fill(px, py, px + 1, py + 1, pocket);
        }
        // 口袋中心：装载的核心点（空 = tRNA 灰点，完成 = AA 色亮点）
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, loaded ? 0xFFFFFFFF : 0xFFB0C4DE);

        if (!animActive) {
            // 静止：口袋两侧展示原料点（左 aa 右 ATP 三磷），短标注各一个词
            g.fill(x + 20, cy - 1, x + 22, cy + 1, aaTint);
            g.drawString(font, "aa", x + 16, cy + 4, aaTint, false);
            g.fill(x + w - 26, cy - 1, x + w - 24, cy + 1, 0xFFF1C40F);
            g.fill(x + w - 22, cy - 1, x + w - 20, cy + 1, 0xFFF1C40F);
            g.fill(x + w - 18, cy - 1, x + w - 16, cy + 1, 0xFFF1C40F);
            g.drawString(font, "ATP", x + w - 30, cy + 4, 0xFFF1C40F, false);
            return;
        }

        // 原料滑动：aa 从左、ATP 从右沿中轴滑向口袋两侧（0-10）
        double prog = Math.min(1.0, t / 10.0);
        int aaX = (int) (x + 20 + (cx - R - 3 - (x + 20)) * prog);
        int atpX = (int) (x + w - 26 + (cx + R + 3 - (x + w - 26)) * prog);
        if (t < 11) {
            // aa 点
            g.fill(aaX - 1, cy - 1, aaX + 1, cy + 1, aaTint);
            // ATP 三磷（黄，横向排列）+ ATP 标注（随点移动）
            for (int p = 0; p < 3; p++) {
                int px = atpX + p * 4;
                g.fill(px - 1, cy - 1, px + 1, cy + 1, 0xFFF1C40F);
            }
            g.drawString(font, "ATP", atpX - 1, cy - 11, 0xFFF1C40F, false);
        } else {
            // 已接触：aa 点落在口袋核心左侧，闪光扩散
            g.fill(cx - R + 3, cy - 1, cx - R + 5, cy + 1, aaTint);
        }

        // 接触闪光（11-14）：口袋中心白光扩散
        if (t >= 11 && t < 14) {
            int f = (t - 11) * 2;
            g.fill(cx - 3 - f, cy - 3 - f, cx + 4 + f, cy + 4 + f, 0x44FFFFFF);
        }

        // 副产物弹出：PPi 灰双点从右上坠落（11-16），AMP 橙点从左下坠落（15-20）
        if (t >= 11 && t < 16) {
            int fall = (t - 11) * 2;
            g.fill(cx + R + 4 + fall, cy - 6 + fall, cx + R + 6 + fall, cy - 4 + fall, 0xFF9E9E9E);
            g.fill(cx + R + 8 + fall, cy - 4 + fall, cx + R + 10 + fall, cy - 2 + fall, 0xFF9E9E9E);
        }
        if (t >= 15 && t < 21) {
            int fall = (t - 15) * 2;
            // AMP 用 AMP 主题橙（substances.json amp color #E67E22），标注一个词
            g.fill(cx - R - 8 - fall, cy + 4 + fall, cx - R - 6 - fall, cy + 6 + fall, 0xFFE67E22);
            g.drawString(font, "AMP", cx - R - 12 - fall, cy + 9 + fall, 0xFFE67E22, false);
        }

        // 完成态光环（14-30）：AA 色外圈脉动
        if (t >= 14) {
            int halo = R + 4 + (int) Math.round(breath * 3);
            for (int i = 0; i < 20; i++) {
                double a = i * (Math.PI * 2 / 20);
                int px = cx + (int) Math.round(Math.cos(a) * halo);
                int py = cy + (int) Math.round(Math.sin(a) * halo);
                g.fill(px, py, px + 1, py + 1, 0x44FFFFFF);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.getKind() == SequenceMachineKind.LOADER) return;
        super.renderLabels(graphics, mouseX, mouseY);
    }
}
