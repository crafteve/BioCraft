package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.blockentity.SequenceMachineKind;
import com.github.crafteve.biocraft.blockentity.TranslatorOperation;
import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.seq.SequenceData;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 翻译机屏幕（encoder 族布局）：框架（背景/状态栏/标签/卡片/动画区面板骨架）
 * 全部由基类按 MachineLayout 绘制，本类只实现：
 * <ul>
 *   <li>动画内容：上行 mRNA 密码子列 / 下行肽链三字母残基同列居中对齐，
 *       列中线对准密码子正中，当前列黄白脉动 + 白底闪；就绪闪烁光标 +
 *       "点击翻译"提示；完成白色扫光 + 末残基呼吸余晖</li>
 *   <li>右下角"翻译"按钮（手动开工）与左下角红叹号错误提示</li>
 * </ul>
 */
public class TranslatorScreen extends SequenceMachineScreen {

    private boolean working = false;

    // 动画时序追踪：DONE 边沿时刻（驱动完成扫光）+ 滚窗浮点列号（平滑缓动）
    private boolean animWasDone = false;
    private int animDoneTick = Integer.MIN_VALUE;
    private double animWinPos;

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
     *   <li>节奏：每密码子 3 tick（BE 步进同速）；动画无入场过渡——当前列整串
     *       直接出现 + 背景呼吸闪白，残基直接彩色</li>
     *   <li>对齐：列宽固定 24px（密码子恒 18px 等宽），残基/占位符按实测宽度
     *       居中对齐密码子中心；每列一条中线对准密码子正中（当前列黄白脉动、
     *       其余暗灰）；滚窗左缘浮点列号逐帧缓动（像素级平滑滑动）</li>
     *   <li>就绪态：起始列闪烁光标 + "点击翻译"提示；完成动画：DONE 边沿白色
     *       扫光掠过全链（26 tick），随后末残基柔和呼吸余晖；
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

            // 动画时序：DONE 边沿 → 记录扫光起点
            if (done && !animWasDone) {
                animDoneTick = guiTick;
            }
            animWasDone = done;
            int sinceDone = guiTick - animDoneTick;
            boolean sweeping = done && sinceDone <= 26;

            // 几何：标签内联行首，内容统一从 x+38 起；上行 mRNA、下行肽链。
            // 密码子恒 18px 宽（A/U/C/G 均为 5px 等宽字符 + 1px 字距），
            // 列宽固定 24px——残基/占位符按实测宽度**居中对齐密码子中心**，
            // 列中线对准密码子正中
            int innerX0 = x + 38;
            int innerX1 = x + w - 8;
            int mrnaY = y + 28;
            int pepY = mrnaY + 27;
            int colW = 24;

            // 滚窗：当前列保持右侧 1/3，到末端停住（转录仪同款语义）
            int codonCount = Math.min(total, Math.max(0, seq.length() - start) / 3);
            int cur = running ? Math.min(pos, Math.max(0, codonCount - 1))
                    : done ? Math.max(0, codonCount - 1) : 0;
            int visibleCols = Math.max(2, (innerX1 - innerX0) / colW);
            int from = Math.max(0, Math.min(cur - visibleCols * 2 / 3, codonCount - visibleCols));
            boolean ready = !running && !done;

            // 行底条（转录仪同款深灰衬条）+ 行首内联标签
            g.fill(innerX0, mrnaY - 2, innerX1, mrnaY + 10, 0xFF2A2A2E);
            g.drawString(font, "mRNA", x + 6, mrnaY, 0xFFF1C40F, false);

            // 就绪模式（插入 mRNA 未点翻译）：只显示 mRNA 链，类似转录仪待机界面——
            // 从起始密码子起铺开整链窗口，AUG 呼吸辉光 + 信息行 + 底部提示，
            // 不画肽链行（翻译开始后肽链行才出现）
            if (ready) {
                int win = Math.min(codonCount, visibleCols);
                for (int ci = 0; ci < win; ci++) {
                    int cx0 = innerX0 + ci * colW;
                    int codonX = cx0 + (colW - 18) / 2;
                    if (ci == 0) {
                        int glow = (int) (70 + breath * 90) << 24 | 0x00FFFFFF;
                        g.fill(codonX - 2, mrnaY - 1, codonX + 20, mrnaY + 9, glow);
                    }
                    String codon = seq.substring(start + ci * 3, start + ci * 3 + 3);
                    for (int bi = 0; bi < 3; bi++) {
                        drawBase(g, codon.charAt(bi), codonX + bi * 6, mrnaY);
                    }
                }
                g.drawString(font, "AUG@" + start, x + 6, mrnaY - 11, 0xFF7ED6DF, false);
                String tip = "点击「翻译」开始";
                g.drawString(font, tip, x + (w - font.width(tip)) / 2, y + h - 12, 0xFF6A6A72, false);
                return;
            }

            g.fill(innerX0, pepY - 2, innerX1, pepY + 9, 0xFF2A2A2E);
            g.drawString(font, "肽链", x + 6, pepY, 0xFF81C784, false);

            ItemStack pep = menu.getSlot(TranslatorOperation.SLOT_OUT_POLYPEPTIDE).getItem();
            SequenceData pd = pep.get(ModDataComponents.SEQUENCE.get());
            String pSeq = pd != null ? pd.seq() : "";
            int shown = Math.min(pos, pSeq.length());

            // 滚窗平滑缓动：窗口左缘是浮点列号，每帧向目标 from 收敛——
            // 3 tick 一密码子的整列跳变是"卡顿感"根因，像素级缓动补齐连贯性
            animWinPos += (from - animWinPos) * 0.35;
            if (Math.abs(from - animWinPos) < 0.02) {
                animWinPos = from;
            }
            int i0 = Math.max(0, (int) Math.floor(animWinPos));
            int i1 = Math.min(codonCount - 1, i0 + visibleCols + 1);

            // 逐列绘制：上格密码子、下格产物残基（或占位/打印特效）、配对短线
            g.enableScissor(innerX0, mrnaY - 4, innerX1, pepY + 10);
            for (int ci = i0; ci <= i1; ci++) {
                int cx0 = (int) Math.round(innerX0 + (ci - animWinPos) * colW);
                int codonX = cx0 + (colW - 18) / 2;
                boolean isCurCol = ci == cur && running;
                String codon = seq.substring(start + ci * 3, start + ci * 3 + 3);

                // 上格 mRNA：当前列整串直接出现 + 白底呼吸闪（无入场动画），
                // 其余列全彩常显（mRNA 是输入，本来就完整存在）
                if (isCurCol) {
                    int glow = (int) (110 + breath * 130) << 24 | 0x00FFFFFF;
                    g.fill(codonX - 2, mrnaY - 1, codonX + 20, mrnaY + 9, glow);
                }
                for (int bi = 0; bi < 3; bi++) {
                    drawBase(g, codon.charAt(bi), codonX + bi * 6, mrnaY);
                }

                // 下格肽链（居中对齐密码子中心）：打印中 = 白底呼吸闪占位；
                // 已翻译 = 直接彩色残基（无入场动画）；未翻译 = 暗色占位
                int resW = ci < pSeq.length() ? font.width(aa1To3(pSeq.charAt(ci))) : font.width("···");
                int resX = cx0 + (colW - resW) / 2;
                boolean printing = isCurCol;
                if (printing) {
                    int fa = (int) (60 + breath * 150);
                    g.fill(resX - 2, pepY - 1, resX + resW + 2, pepY + 9, fa << 24 | 0xFFFFFF);
                } else if (ci < shown) {
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

                // 配对短线（转录仪同款语言）：每个碱基下方一条贯穿两行之间的竖线，
                // 当前密码子的三根黄白脉动、其余暗灰——密码子↔残基逐位对应意象
                int lineC = isCurCol ? (int) (170 + breath * 70) << 24 | 0xFFFFF176 : 0xFF333338;
                for (int bi = 0; bi < 3; bi++) {
                    int lineX = codonX + bi * 6 + 3;
                    g.fill(lineX, mrnaY + 11, lineX + 1, pepY - 3, lineC);
                }
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
