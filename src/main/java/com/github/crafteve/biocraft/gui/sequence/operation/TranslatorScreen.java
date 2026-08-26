package com.github.crafteve.biocraft.gui.sequence.operation;

import com.github.crafteve.biocraft.blockentity.sequence.SequenceMachineKind;
import com.github.crafteve.biocraft.blockentity.sequence.operation.TranslatorOperation;
import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.item.SequenceData;
import com.github.crafteve.biocraft.gui.sequence.SequenceMachineMenu;
import com.github.crafteve.biocraft.gui.sequence.SequenceLayout;
import com.github.crafteve.biocraft.gui.sequence.SequenceMachineScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 翻译机屏幕（CONSOLE族布局）：框架（背景/状态栏/标签/卡片/动画区面板骨架）
 * 全部由基类按 SequenceLayout 绘制，本类只实现：
 * <ul>
 *   <li>动画内容：上行 mRNA 密码子列 / 下行肽链三字母残基同列居中对齐，
 *       列中线对准密码子正中，当前列黄白脉动 + 白底闪；就绪闪烁光标 +
 *       "点击翻译"提示；完成白色扫光 + 末残基呼吸余晖</li>
 *   <li>右下角"翻译"按钮（手动开工）与左下角红叹号错误提示</li>
 * </ul>
 */
public class TranslatorScreen extends SequenceMachineScreen {

    private boolean working = false;

    // 动画时序追踪：DONE 边沿时刻（驱动完成扫光）+ 阅读头状态
    // readHead = 浮点密码子序号（纸带滑动量）；headStep = 本 tick 步长
    // （供 partialTick 帧内插值）；-1 = 未初始化（首帧直接吸附）
    private boolean animWasDone = false;
    private int animDoneTick = Integer.MIN_VALUE;
    private double readHead = -1;
    private double headStep;
    private int lastHeadTick = Integer.MIN_VALUE;

    public TranslatorScreen(SequenceMachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        if (menu.getKind() == SequenceMachineKind.TRANSLATOR) containerTick();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        if (menu.getKind() != SequenceMachineKind.TRANSLATOR) return;
        // 二态工作判定：与服务端 canStart 同口径（mRNA 合法含 AUG + GTP 在槽 + 输出有空间）
        working = isWorkable();
    }

    private boolean isWorkable() {
        ItemStack mrna = menu.getSlot(TranslatorOperation.SLOT_MRNA).getItem();
        ItemStack gtp = menu.getSlot(TranslatorOperation.SLOT_GTP).getItem();
        if (mrna.isEmpty() || gtp.isEmpty()) return false;
        SequenceData d = mrna.get(ModDataComponents.SEQUENCE.get());
        if (d == null || !d.complete() || d.type() != SequenceData.SeqType.MRNA) return false;
        if (d.seq() == null || !d.seq().contains("AUG")) return false;
        if (!hasRoom(TranslatorOperation.SLOT_OUT_POLYPEPTIDE) || !hasRoom(TranslatorOperation.SLOT_OUT_TRNA) || !hasRoom(TranslatorOperation.SLOT_OUT_GDP) || !hasRoom(TranslatorOperation.SLOT_OUT_PI))
            return false;
        return true;
    }

    private boolean hasRoom(int slot) {
        ItemStack s = menu.getSlot(slot).getItem();
        return s.isEmpty() || s.getCount() < s.getMaxStackSize();
    }

    @Override
    protected void init() {
        super.init();
        // 右下角"翻译"按钮：手动触发开工（禁自动开翻），共用启动工序包
        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.literal("翻译"), b ->
                        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                                new com.github.crafteve.biocraft.network.ServerboundTranscribePacket(this.menu.getPos())))
                .bounds(leftPos + SequenceMachineMenu.EDIT_X + SequenceMachineMenu.EDIT_W - 46,
                        topPos + SequenceMachineMenu.EDIT_Y + SequenceMachineMenu.EDIT_H - 11, 42, 11)
                .build());
    }

    /**
     * 动画内容（转录仪风格上下双行——数据驱动非自由循环）：
     * <ul>
     *   <li>几何与转录仪对齐：上行 mRNA y+42、下行肽链 y+62、配对短线 y+54
     *       （4px 短线，每列一根，当前阅读列黄白脉动）</li>
     *   <li>阅读头：浮点列号每 tick 滑 1/3 密码子（与 3tick 节奏同步），mRNA
     *       辉光与肽行打印框跟头连续滑动——消灭 3tick 瞬移的卡顿抖动；
     *       辉光为转录仪同款"白 @ pulse alpha"灰底高亮，垫在字符下、
     *       打印框盖在占位上，两行同公式同节奏</li>
     *   <li>滚窗：首帧/大跨度跳变直接吸附（消灭开 GUI 入场滚动），小步缓动</li>
     *   <li>就绪模式（插入 mRNA 未点翻译）：mRNA 链 + 空肽链占位行 + AUG@信息行
     *       + 底部提示；完成动画：白色扫光 + 末残基呼吸余晖；
     *       无有效 mRNA 居中灰字提示</li>
     * </ul>
     */
    @Override
    protected void renderMachineAnimation(GuiGraphics g, int x, int y, int w, int h) {
        int stage = menu.getData().get(SequenceMachineMenu.DATA_STAGE);
        int pos = menu.getData().get(SequenceMachineMenu.DATA_POSITION);
        int total = menu.getData().get(SequenceMachineMenu.DATA_TOTAL);
        int guiTick = net.minecraft.client.Minecraft.getInstance().gui.getGuiTicks();
        boolean running = stage == 1 && total > 0;
        boolean done = stage == 2 && total > 0;

        // mRNA 序列来源：0 槽物品（客户端槽位同步可靠）
        ItemStack mrna = menu.getSlot(TranslatorOperation.SLOT_MRNA).getItem();
        SequenceData d = mrna.get(ModDataComponents.SEQUENCE.get());
        String seq = d != null ? d.seq() : "";
        int start = seq.indexOf("AUG");
        if (!seq.isEmpty() && start >= 0) {
            double breath = Math.sin(guiTick * 0.35) * 0.5 + 0.5;
            // 转录仪同款辉光脉动（当前字符灰底高亮的呼吸节奏，两机观感一致）
            double pulse = Math.sin(guiTick * 0.4) * 0.3 + 0.7;

            // 动画时序：DONE 边沿 → 记录扫光起点
            if (done && !animWasDone) {
                animDoneTick = guiTick;
            }
            animWasDone = done;
            int sinceDone = guiTick - animDoneTick;
            boolean sweeping = done && sinceDone <= 26;
            boolean ready = !running && !done;

            // 几何与转录仪对齐：上行 y+42、下行 y+62、配对短线 y+54（4px）。
            // 密码子恒 18px 宽（A/U/C/G 等宽），列宽固定 24px，残基/占位符
            // 居中对齐密码子中心
            int innerX0 = x + 38;
            int innerX1 = x + w - 8;
            int mrnaY = y + 42;
            int pepY = mrnaY + 20;
            int pairY = mrnaY + 12;
            int colW = 24;

            // 密码子总数由序列直接推导——IDLE 时 total=0，用 total 推会得 0 列
            int codonCount = Math.max(0, seq.length() - start) / 3;
            int visibleCols = Math.max(2, (innerX1 - innerX0) / colW);

            // 阅读头推进（tick 级）：基础速度 1/3 密码子/tick（与 3tick 节奏同步），
            // 启动段（头 1 列内）从 35% 平滑加速、结束段（距末列 1 列内）减速到
            // 35%——起步与收尾有加减速曲线；上限为已提交 pos（不越权）
            int dt = lastHeadTick == Integer.MIN_VALUE ? 0 : Math.max(0, guiTick - lastHeadTick);
            lastHeadTick = guiTick;
            if (readHead < 0 || pos < readHead) {
                readHead = pos;
            }
            if (running) {
                double ease = 1.0;
                double distEnd = (codonCount - 1) - readHead;
                if (readHead < 1) {
                    ease = Math.min(ease, 0.35 + 0.65 * Math.max(0, readHead));
                }
                if (distEnd < 1) {
                    ease = Math.min(ease, 0.35 + 0.65 * Math.max(0, distEnd));
                }
                headStep = ease / (double) TranslatorOperation.TICKS_PER_CODON;
                readHead = Math.min(pos, readHead + dt * headStep);
            } else if (done) {
                // 完成：头缓动滑停到末位（指数衰减，越近越慢）
                headStep = 0;
                double target = Math.max(0, codonCount - 1);
                readHead += (target - readHead) * Math.min(1.0, 0.3 * dt);
            } else {
                headStep = 0;
                readHead = 0;
            }

            // 渲染头 = tick 级位置 + partialTick 帧内插值（方案 B）——两次 tick
            // 之间的每一帧都有微移（约 2.7px/帧），彻底消除帧间静止的 20fps 观感
            double renderHead = readHead + animPartialTick * headStep;
            if (running) {
                renderHead = Math.min(renderHead, pos);
            }

            // 固定阅读头屏幕槽位（第 3 列中央）：光标位置恒定不动，碱基链与
            // 肽链作为"纸带"从它下面滑过（列屏幕坐标 = 头屏坐标 + (列-头)×列宽）
            int headSlot = Math.max(1, Math.min(3, visibleCols / 2));
            double headScreenX = innerX0 + headSlot * (double) colW + colW / 2.0;

            // 行底条 + 行首内联标签（两行常驻——就绪模式肽链行为空占位）
            g.fill(innerX0, mrnaY - 2, innerX1, mrnaY + 10, 0xFF2A2A2E);
            g.fill(innerX0, pepY - 2, innerX1, pepY + 9, 0xFF2A2A2E);
            g.drawString(font, "mRNA", x + 6, mrnaY, 0xFFF1C40F, false);
            g.drawString(font, "肽链", x + 6, pepY, 0xFF81C784, false);
            // 起始密码子信息行（转录仪"启动子@n"同位）
            g.drawString(font, "AUG@" + start, x + 6, mrnaY - 11, 0xFF7ED6DF, false);

            // 可视列范围：以渲染头为中心铺满窗口
            int i0 = Math.max(0, (int) Math.floor(renderHead) - headSlot - 1);
            int i1 = Math.min(codonCount - 1, i0 + visibleCols + 2);

            // 相位三段（方案 A）：f = 渲染头列内进度 [0,1)——
            // [0,1/3) 入场（alpha 爬升）/[1/3,2/3) 打印（满强度）/[2/3,1) 出场（渐隐）
            double f = renderHead - Math.floor(renderHead);
            double phase;
            if (ready) {
                phase = 1;
            } else if (f < 1.0 / 3) {
                phase = f * 3;
            } else if (f < 2.0 / 3) {
                phase = 1;
            } else {
                phase = 1 - (f - 2.0 / 3) * 3;
            }
            int headAlpha = (int) (180 * pulse * phase);
            int hxi = (int) Math.round(headScreenX);
            int headCol = Math.max(0, Math.min((int) Math.round(renderHead), codonCount - 1));

            // 阅读头辉光（垫在碱基下面）：光标固定不动，转录仪当前字符灰底
            // 高亮同款（白 @ pulse alpha × 相位包络）
            if (running || ready) {
                g.fill(hxi - 10, mrnaY - 1, hxi + 10, mrnaY + 9, headAlpha << 24 | 0xFFFFFF);
            }

            // 逐列绘制（纸带随渲染头连续滑动）：上格密码子、下格产物残基
            // （或占位）、每列一根 4px 配对短竖线（灰，随带滑动）
            g.enableScissor(innerX0, mrnaY - 4, innerX1, pepY + 10);
            ItemStack pep = menu.getSlot(TranslatorOperation.SLOT_OUT_POLYPEPTIDE).getItem();
            SequenceData pd = pep.get(ModDataComponents.SEQUENCE.get());
            String pSeq = pd != null ? pd.seq() : "";
            int shown = Math.min(pos, pSeq.length());
            for (int ci = i0; ci <= i1; ci++) {
                int cx0 = (int) Math.round(headScreenX - colW / 2.0 + (ci - renderHead) * colW);
                int codonX = cx0 + (colW - 18) / 2;
                String codon = seq.substring(start + ci * 3, start + ci * 3 + 3);
                for (int bi = 0; bi < 3; bi++) {
                    drawBase(g, codon.charAt(bi), codonX + bi * 6, mrnaY);
                }

                // 下格肽链（居中对齐密码子中心）：已翻译 = 彩色残基（完成末残基
                // 呼吸余晖）；未翻译 = 暗色占位
                int resW = ci < pSeq.length() ? font.width(aa1To3(pSeq.charAt(ci))) : font.width("···");
                int resX = cx0 + (colW - resW) / 2;
                if (ci < shown) {
                    char aa1 = pSeq.charAt(ci);
                    String aa3 = aa1To3(aa1);
                    boolean settleGlow = done && ci == shown - 1 && !sweeping;
                    int color = cardTextColor(aaColor(aa1));
                    if (settleGlow) {
                        int halo = (int) (26 + breath * 22) << 24 | 0x00FFFFFF;
                        g.fill(resX - 2, pepY - 1, resX + resW + 2, pepY + 9, halo);
                    }
                    g.drawString(font, aa3, resX, pepY, color, false);
                } else {
                    g.drawString(font, "···", resX, pepY, 0xFF4A4E54, false);
                }

                // 配对短竖线（转录仪同款 4px，随纸带滑动）
                int lineX = codonX + 9;
                g.fill(lineX, pairY, lineX + 1, pairY + 4, 0xFF333338);
            }

            // 固定光标组（画在纸带之上）：肽行打印框（运行中，盖在占位上）+
            // 黄白配对高亮线——位置恒定，相位包络只调 alpha（方案 A 关键帧）
            if (running) {
                g.fill(hxi - 9, pepY - 1, hxi + 9, pepY + 9, headAlpha << 24 | 0xFFFFFF);
                int lineC = (int) (170 + breath * 70) << 24 | 0xFFFFF176;
                g.fill(hxi, pairY, hxi + 1, pairY + 4, lineC);
            }
            g.disableScissor();

            // 完成扫光：白线自左向右掠过两行全区（带渐隐尾迹），扫完落定
            if (sweeping) {
                double pr = sinceDone / 26.0;
                int sx = innerX0 + (int) ((innerX1 - innerX0) * pr);
                g.fill(sx, mrnaY - 3, sx + 1, pepY + 10, 0xA0FFFFFF);
                for (int k = 1; k <= 7; k++) {
                    int tail = Math.max(0, 0x60 - k * 12) << 24 | 0xFFFFFF;
                    g.fill(sx - k, mrnaY - 3, sx - k + 1, pepY + 10, tail);
                }
            }
        } else {
            // 无有效 mRNA：居中提示（灰字，不闪烁）
            String tip = seq.isEmpty() ? "放入 mRNA 并点击翻译" : "mRNA 无起始密码子 AUG";
            g.drawString(font, tip, x + (w - font.width(tip)) / 2, y + h / 2 - 4, 0xFF6A6A72, false);
        }
    }

    /** 单碱基绘制（固定主题色，当前列提亮由背景辉光承担） */
    private void drawBase(GuiGraphics g, char base, int px, int py) {
        int c = switch (base) {
            case 'A' -> BASE_A;
            case 'U' -> BASE_T;
            case 'C' -> BASE_C;
            case 'G' -> BASE_G;
            default -> 0xFF5A5A5A;
        };
        g.drawString(font, String.valueOf(base), px, py, c, false);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        if (menu.getKind() != SequenceMachineKind.TRANSLATOR) return;
        // 左下角红叹号错误提示（悬停 tooltip 列原因）：三态
        ItemStack mrna = menu.getSlot(TranslatorOperation.SLOT_MRNA).getItem();
        SequenceData d = mrna.isEmpty() ? null : mrna.get(ModDataComponents.SEQUENCE.get());
        String err = "";
        boolean isMissing = false;
        if (mrna.isEmpty()) {
            err = "未放mRNA模板";
            isMissing = true;
        } else if (d == null || d.type() != SequenceData.SeqType.MRNA
                || d.seq() == null || !d.seq().matches("[AUCG]*")) {
            err = "非法mRNA（请用转录仪产物）";
        } else if (!d.seq().contains("AUG")) {
            err = "未找到起始密码子 AUG";
        }
        if (!err.isEmpty()) {
            int x = leftPos + SequenceMachineMenu.EDIT_X + 3;
            int y = topPos + SequenceMachineMenu.EDIT_Y + SequenceMachineMenu.EDIT_H - 9;
            int barColor = isMissing ? 0xFF9E9E9E : 0xFFE53935;
            int textColor = isMissing ? 0xFF707070 : 0xFFFFFFFF;
            String prefix = isMissing ? "§7" : "§c";
            g.fill(x, y, x + 1, y + 8, barColor);
            g.drawString(font, "!", x + 3, y, textColor, false);
            if (mx >= x && mx < x + 8 && my >= y && my < y + 8) {
                g.renderTooltip(font, java.util.List.of(Component.literal(prefix + err)), java.util.Optional.empty(), mx, my);
            }
        }
    }
}

