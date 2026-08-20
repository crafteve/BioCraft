package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.blockentity.SeqStepState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 序列机通用屏幕：机器槽 + 进度条 + 阶段文本（信息层 GUI，非酶腔的 v-t 图）
 * <p>
 * MVP 用纯色面板背景（正式基底贴图待 texturegen 轮次）；
 * 编码器子类叠加文本编辑器（EncoderScreen）
 */
public class SequenceMachineScreen extends AbstractContainerScreen<SequenceMachineMenu> {

    /** 进度条区域（imageHeight 192 布局） */
    private static final int PROGRESS_X = 36, PROGRESS_Y = 78, PROGRESS_W = 104, PROGRESS_H = 6;

    public SequenceMachineScreen(SequenceMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 192;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 面板底色 + 边框
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF101016);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + 1, 0xFF3A3A3A);
        graphics.fill(leftPos, topPos + imageHeight - 1, leftPos + imageWidth, topPos + imageHeight, 0xFF3A3A3A);
        graphics.fill(leftPos, topPos, leftPos + 1, topPos + imageHeight, 0xFF3A3A3A);
        graphics.fill(leftPos + imageWidth - 1, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF3A3A3A);

        // 机器槽位底（深色凹槽）
        for (int i = 0; i < menu.machineSlotCount; i++) {
            Slot slot = menu.getSlot(i);
            graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1,
                    leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF202028);
        }

        // 进度条
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        int position = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int fill = total > 0 ? (int) (PROGRESS_W * position / (double) total) : 0;
        graphics.fill(leftPos + PROGRESS_X, topPos + PROGRESS_Y,
                leftPos + PROGRESS_X + PROGRESS_W, topPos + PROGRESS_Y + PROGRESS_H, 0xFF2A2A32);
        if (fill > 0) {
            graphics.fill(leftPos + PROGRESS_X, topPos + PROGRESS_Y,
                    leftPos + PROGRESS_X + fill, topPos + PROGRESS_Y + PROGRESS_H, 0xFF4CAF50);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        // 阶段文本 + 进度读数
        int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
        int position = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        String stageText = switch (SeqStepState.Stage.values()[Math.min(stage, SeqStepState.Stage.values().length - 1)]) {
            case IDLE -> "空闲 · 等待输入";
            case EXTENDING -> "延伸中 " + position + " / " + total;
            case DONE -> "完成";
        };
        graphics.drawString(this.font, Component.literal("§7" + stageText), 8, 90, 0xFFFFFF);
    }
}
