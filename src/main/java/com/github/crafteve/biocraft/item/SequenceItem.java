package com.github.crafteve.biocraft.item;

import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.seq.SeqCodec;
import com.github.crafteve.biocraft.seq.SequenceData;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

/**
 * 序列物品族（DNA/mRNA/多肽等聚合物物品）
 * <p>
 * DataComponent = 唯一事实源（组件缺失时用构造默认的空序列兜底）；
 * tooltip 显示序列预览（超长截断 + 长度标注）；
 * 程序 DNA 额外显示解码出的程序摘要（seq/ 纯核心在客户端同样可用）
 */
public class SequenceItem extends Item implements AbbreviationProvider {

    private final SequenceData.SeqType defaultType;
    private final SequenceData.Strand defaultStrand;
    private final SequenceData.Kind defaultKind;
    private final String abbreviation;

    /**
     * @param properties   物品属性
     * @param type         聚合物类型（dna/mrna/polypeptide）
     * @param strand       链型（非 DNA 传 null）
     * @param kind         序列类型（程序/基因，默认 GENE，编码器产物运行期改 PROGRAM）
     * @param abbreviation 图标缩写标注（DNA/mRNA/肽链）
     */
    public SequenceItem(Properties properties, SequenceData.SeqType type, SequenceData.Strand strand,
                        SequenceData.Kind kind, String abbreviation) {
        super(properties);
        this.defaultType = type;
        this.defaultStrand = strand;
        this.defaultKind = kind;
        this.abbreviation = abbreviation;
    }

    @Override
    public String getAbbreviation() {
        return abbreviation;
    }

    /** 无组件时的默认载荷（空序列） */
    public SequenceData defaultData() {
        return new SequenceData(defaultType, defaultStrand, defaultKind, "", false);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        SequenceData data = stack.getOrDefault(ModDataComponents.SEQUENCE.get(), defaultData());
        String seq = data.seq();
        if (seq.isEmpty()) {
            tooltip.add(Component.literal("§7空序列"));
            return;
        }
        switch (data.type()) {
            case DNA -> appendDnaTooltip(data, tooltip);
            case MRNA -> {
                tooltip.add(Component.literal("§7mRNA 5'-" + truncate(seq) + "-3'  §8(" + seq.length() + " nt)"));
                if (!data.complete()) {
                    tooltip.add(Component.literal("§7合成中…"));
                }
            }
            case POLYPEPTIDE -> {
                tooltip.add(Component.literal("§7肽链 " + truncate(seq) + "  §8(" + seq.length() + " aa)"));
                if (!data.complete()) {
                    tooltip.add(Component.literal("§c未完成（折叠机拒绝）"));
                }
            }
        }
    }

    /**
     * DNA（双链/单链）tooltip 三态：
     * <ul>
     *   <li>默认：5'-前 10 碱基…-3'（简略预览 + 长度标注 + 合成中标记）</li>
     *   <li>Shift：完整碱基序列，逐碱基按 dNTP 主题色着色（A/T/C/G =
     *       dATP/dTTP/dCTP/dGTP 物品色），每行 64 碱基分行显示</li>
     *   <li>Ctrl：解码出的程序全文，语法高亮 + 保留缩进格式
     *       （ProgramHighlight，与编辑器配色一致）</li>
     * </ul>
     */
    private static void appendDnaTooltip(SequenceData data, List<Component> tooltip) {
        String seq = data.seq();
        String kind = data.strand() == SequenceData.Strand.DS ? "dsDNA" : "ssDNA";
        if (Screen.hasShiftDown()) {
            tooltip.add(Component.literal("§7" + kind + " 完整序列（" + seq.length() + " bp，Shift）"));
            tooltip.addAll(coloredBases(seq));
            return;
        }
        if (Screen.hasControlDown()) {
            tooltip.add(Component.literal("§7" + kind + " 程序（Ctrl）"));
            SeqCodec.DecodeResult r = SeqCodec.decodeText(seq);
            if (r.ok()) {
                tooltip.addAll(ProgramHighlight.highlight(r.text()));
            } else {
                tooltip.add(Component.literal("§7非程序 DNA，无程序可显示"));
            }
            return;
        }
        String head = seq.length() <= 10 ? seq : seq.substring(0, 10) + "…";
        tooltip.add(Component.literal("§7" + kind + " 5'-" + head + "-3'  §8(" + seq.length() + " bp)"));
        if (!data.complete()) {
            tooltip.add(Component.literal("§7合成中…"));
        }
    }

    /**
     * 完整碱基序列逐碱基着色（Shift 查看）：A/T/C/G 取对应 dNTP 物品主题色
     * （substances.json 数据表色，与 GUI 卡片同源），每行 64 碱基分行
     */
    private static List<Component> coloredBases(String seq) {
        List<Component> lines = new ArrayList<>();
        for (int start = 0; start < seq.length(); start += 64) {
            String part = seq.substring(start, Math.min(seq.length(), start + 64));
            MutableComponent line = Component.empty();
            for (int i = 0; i < part.length(); i++) {
                char base = part.charAt(i);
                int color = switch (base) {
                    case 'A' -> dnTpTint("datp");
                    case 'T' -> dnTpTint("dttp");
                    case 'C' -> dnTpTint("dctp");
                    case 'G' -> dnTpTint("dgtp");
                    default -> 0xCCCCCC;
                };
                line.append(Component.literal(String.valueOf(base))
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
            }
            lines.add(line);
        }
        return lines;
    }

    /** dNTP 物品主题色（24 位 RGB；解析失败回退浅灰） */
    private static int dnTpTint(String itemId) {
        Item item = ModItems.byId(itemId).get();
        if (item instanceof MoleculeItem molecule) {
            return molecule.getTintColor();
        }
        return 0xCCCCCC;
    }

    private static String truncate(String seq) {
        return truncate(seq, 32);
    }

    private static String truncate(String seq, int max) {
        if (seq.length() <= max) {
            return seq;
        }
        return seq.substring(0, max) + "…";
    }
}
