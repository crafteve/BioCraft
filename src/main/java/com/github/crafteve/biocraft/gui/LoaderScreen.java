package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.blockentity.LoaderOperation;
import com.github.crafteve.biocraft.blockentity.SequenceMachineKind;
import com.github.crafteve.biocraft.item.MoleculeItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 装载机屏幕（v1 族布局）：框架（背景/状态栏/标签/左右卡片/动画区面板骨架）
 * 全部由基类按 MachineLayout 绘制，本类只实现：
 * <ul>
 *   <li>动画内容：中央 tRNA 装载口袋（24 点呼吸圆环）+ 原料滑入 + 接触闪光
 *       + AMP/PPi 副产物坠落 + 完成光环（独立 30 tick 循环）</li>
 *   <li>工作状态检测（输入齐+输出有空间+配方可执行 = 绿灯播动画）</li>
 * </ul>
 */
public class LoaderScreen extends SequenceMachineScreen {

    // 动画起点（进入合成时重置，独立 30 tick 循环不绑机器速度）
    private long animStart = -1;
    /**
     * 工作状态（绿灯）：输入槽有货 + 输出槽有空间 + 配方可执行时为 true；
     * 每 tick 检测（containerTick），绿灯即播动画、红灯即停
     */
    private boolean working = false;

    public LoaderScreen(SequenceMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        if (menu.getKind() == SequenceMachineKind.LOADER) containerTick();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (menu.getKind() != SequenceMachineKind.LOADER) return;
        // 工作状态检测（每 tick）：输入槽有货 + 输出槽有空间 + 配方可执行
        // 槽位坐标同步由基类 tickScrolls 按 v1 布局处理，此处不再覆写
        working = checkWorkable();
    }

    /**
     * 工作状态判定：委托 LoaderOperation 同口径静态方法（输入齐全 + 输出有空间即工作）
     */
    private boolean checkWorkable() {
        return LoaderOperation.isWorkable(
                menu.getSlot(LoaderOperation.SLOT_TRNA).getItem(),
                menu.getSlot(LoaderOperation.SLOT_AA).getItem(),
                menu.getSlot(LoaderOperation.SLOT_ATP).getItem(),
                menu.getSlot(LoaderOperation.SLOT_OUT_AATRNA).getItem(),
                menu.getSlot(LoaderOperation.SLOT_OUT_AMP).getItem(),
                menu.getSlot(LoaderOperation.SLOT_OUT_PPI).getItem());
    }

    @Override
    protected void renderMachineAnimation(GuiGraphics g, int x, int y, int w, int h) {
        // 动画活跃 = 工作状态（输入有货 + 输出有空间即播，否则停）
        boolean animActive = working;
        int tick = net.minecraft.client.Minecraft.getInstance().gui.getGuiTicks();
        Slot aaSlot = menu.getSlot(LoaderOperation.SLOT_AA);
        ItemStack aaStack = aaSlot.getItem();
        // aa 缩写：随 AA 槽物品变化（如 Gly/Ala），非固定 "aa"
        int aaTint = 0xFF7CFC00;
        String aaAbbr = "aa";
        if (!aaStack.isEmpty() && aaStack.getItem() instanceof MoleculeItem mi) {
            aaTint = mi.getTintColor() | 0xFF000000;
            aaAbbr = mi.getAbbreviation();
        }
        // 分子主题色从 substances.json 取（勿硬编码）：ATP 红、AMP 橙、PPi 深橙
        int atpTint = moleculeTint("atp", 0xFFE74C3C);
        int ampTint = moleculeTint("amp", 0xFFE67E22);
        int ppiTint = moleculeTint("ppi", 0xFFD35400);
        // 0..1 呼吸曲线（口袋缩放/光环脉动）
        double breath = (Math.sin(tick * 0.35) + 1) * 0.5;

        // 独立 30 tick 循环（绿灯出现时从 0 开始；红灯归零停止）
        int t = 0;
        if (animActive) {
            if (animStart < 0) animStart = tick;
            t = (int) ((tick - animStart) % 30);
        } else {
            animStart = -1;
        }

        // 中央装载口袋：大圆点阵描边（始终 AA 色，无 AA 时灰），呼吸缩放
        int cx = x + w / 2;
        int cy = y + h / 2 + 2;
        int R = 15 + (int) Math.round(breath * 2);
        boolean hasAa = !aaStack.isEmpty();
        int pocket = hasAa ? aaTint : 0xFF7E8EA0;
        for (int i = 0; i < 24; i++) {
            double a = i * (Math.PI * 2 / 24);
            int px = cx + (int) Math.round(Math.cos(a) * R);
            int py = cy + (int) Math.round(Math.sin(a) * R);
            g.fill(px, py, px + 1, py + 1, pocket);
        }
        // 口袋中心：随 AA 槽有无显示 AA 色，无 AA 时 tRNA 灰点（不随动画跳变）
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, hasAa ? aaTint : 0xFFB0C4DE);
        // tRNA 标注：居中口袋上方，固定不变（说明中心圆 = tRNA）
        g.drawString(font, "tRNA", cx - font.width("tRNA") / 2, cy - R - 12, 0xFF90A4AE, false);

        if (!animActive) {
            // 静止：口袋两侧展示原料点（左 aa 右 ATP 三磷），短标注各一个词
            g.fill(x + 20, cy - 1, x + 22, cy + 1, aaTint);
            g.drawString(font, aaAbbr, x + 16, cy + 4, aaTint, false);
            g.fill(x + w - 26, cy - 1, x + w - 24, cy + 1, atpTint);
            g.fill(x + w - 22, cy - 1, x + w - 20, cy + 1, atpTint);
            g.fill(x + w - 18, cy - 1, x + w - 16, cy + 1, atpTint);
            g.drawString(font, "ATP", x + w - 30, cy + 4, atpTint, false);
            return;
        }

        // 原料滑动：aa 从左、ATP 从右沿中轴滑向口袋两侧（0-10）
        double prog = Math.min(1.0, t / 10.0);
        int aaX = (int) (x + 20 + (cx - R - 3 - (x + 20)) * prog);
        int atpX = (int) (x + w - 26 + (cx + R + 3 - (x + w - 26)) * prog);
        if (t < 11) {
            // aa 点（带 aa 缩写标注跟随，与静止态一致）
            g.fill(aaX - 1, cy - 1, aaX + 1, cy + 1, aaTint);
            g.drawString(font, aaAbbr, aaX - 2, cy + 4, aaTint, false);
            // ATP 三磷（主题红，横向排列）+ ATP 标注（随点移动）
            for (int p = 0; p < 3; p++) {
                int px = atpX + p * 4;
                g.fill(px - 1, cy - 1, px + 1, cy + 1, atpTint);
            }
            g.drawString(font, "ATP", atpX - 1, cy - 11, atpTint, false);
        } else {
            // 已接触：aa 点落在口袋核心左侧，闪光扩散
            g.fill(cx - R + 3, cy - 1, cx - R + 5, cy + 1, aaTint);
        }

        // 接触闪光（11-14）：口袋中心白光扩散
        if (t >= 11 && t < 14) {
            int f = (t - 11) * 2;
            g.fill(cx - 3 - f, cy - 3 - f, cx + 4 + f, cy + 4 + f, 0x44FFFFFF);
        }

        // 副产物弹出：PPi 深橙双点从右上坠落（11-16，带 PPi 标注），AMP 橙点从左下坠落（15-20）
        if (t >= 11 && t < 16) {
            int fall = (t - 11) * 2;
            g.fill(cx + R + 4 + fall, cy - 6 + fall, cx + R + 6 + fall, cy - 4 + fall, ppiTint);
            g.fill(cx + R + 8 + fall, cy - 4 + fall, cx + R + 10 + fall, cy - 2 + fall, ppiTint);
            g.drawString(font, "PPi", cx + R + 8 + fall - font.width("PPi") / 2, cy - 12 + fall, ppiTint, false);
        }
        if (t >= 15 && t < 21) {
            int fall = (t - 15) * 2;
            // AMP 用 AMP 主题橙（substances.json amp color），标注一个词
            g.fill(cx - R - 8 - fall, cy + 4 + fall, cx - R - 6 - fall, cy + 6 + fall, ampTint);
            g.drawString(font, "AMP", cx - R - 12 - fall, cy + 9 + fall, ampTint, false);
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

    /** 取分子物品主题色（substances.json color，带 alpha），不存在时回退默认 */
    private static int moleculeTint(String id, int fallback) {
        var deferred = com.github.crafteve.biocraft.init.ModItems.byId(id);
        if (deferred != null && deferred.get() instanceof MoleculeItem mi) {
            return mi.getTintColor() | 0xFF000000;
        }
        return fallback;
    }
}
