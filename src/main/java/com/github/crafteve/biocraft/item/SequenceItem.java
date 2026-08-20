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
        Boolean isTemplate = stack.get(ModDataComponents.IS_TEMPLATE.get());
        switch (data.type()) {
            case DNA -> appendDnaTooltip(data, tooltip, stack, isTemplate);
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
     *   <li>默认：第一行灰白 5'-前 10 碱基…-3'（含 bp 数，合成中并入行尾）；
     *       第二行"按住 Shift 显示碱基序列"；第三行"按住 Ctrl 显示程序"</li>
     *   <li>Shift：完整碱基序列，逐碱基按 dNTP 主题色着色（A/T/C/G =
     *       dATP/dTTP/dCTP/dGTP 物品色），首行 5' 白标、末行 3' 白标，
     *       非模板链显示 3'→5'（方向相反），每行碱基数按 tooltip 可用宽度自适应</li>
     *   <li>Ctrl：解码出的程序全文，语法高亮 + 保留缩进格式
     *       （ProgramHighlight，与编辑器配色一致）</li>
     * </ul>
     */
    private static void appendDnaTooltip(SequenceData data, List<Component> tooltip, ItemStack stack, Boolean isTemplate) {
        String seq = data.seq();
        if (Screen.hasShiftDown()) {
            tooltip.addAll(coloredBases(seq, isTemplate));
            if (isTemplate != null) {
                tooltip.add(Component.literal(isTemplate ? "§7编码链 (5'→3')" : "§7模板链 (3'→5')"));
            }
            return;
        }
        if (Screen.hasControlDown()) {
            SeqCodec.DecodeResult r = SeqCodec.decodeText(seq);
            if (r.ok()) {
                tooltip.addAll(ProgramHighlight.highlight(r.text()));
            } else {
                tooltip.add(Component.literal("§7非程序 DNA，无程序可显示"));
            }
            return;
        }
        boolean isNonTemplate = isTemplate != null && !isTemplate;
        String dirLeft = isNonTemplate ? "3'-" : "5'-";
        String dirRight = isNonTemplate ? "-5'" : "-3'";
        String head = seq.length() <= 10 ? seq : seq.substring(0, 10) + "…";
        MutableComponent line1 = Component.literal("§7" + dirLeft + head + dirRight + "  §8(" + seq.length() + " bp)");
        if (!data.complete()) {
            line1.append(Component.literal(" §7合成中"));
        }
        tooltip.add(line1);
        if (isTemplate != null) {
            tooltip.add(Component.literal(isTemplate ? "§7编码链" : "§7模板链"));
        }
        tooltip.add(Component.literal("§8按住 Shift 显示碱基序列"));
        tooltip.add(Component.literal("§8按住 Ctrl 显示程序"));
    }

    /**
     * 完整碱基序列逐碱基着色（Shift 查看）：A/T/C/G 取对应 dNTP 物品主题色
     * （substances.json 数据表色，与 GUI 卡片同源）。
     * <p>
     * 分行规则：每行碱基数 = (屏幕可用宽度 / 6px 每字符)——MC 会对超宽的
     * tooltip 行自动折行（折行位置在字符中间、打乱彩色换行 = "换行错乱"），
     * 按屏宽分行保证单行不超可用宽度、永不被 MC 二次折行；
     * 首行 5' 白标、末行 3' 白标（非模板链则 3'/5' 对调，显示 3'→5'）
     */
    private static List<Component> coloredBases(String seq, Boolean isTemplate) {
        // helicase 单链（带 IS_TEMPLATE）一行完整显示，避免莫名换行；其余按屏宽自适应
        int perLine;
        boolean dirIsTemplate;
        if (isTemplate == null) {
            int avail = Math.max(120, net.minecraft.client.Minecraft.getInstance()
                    .getWindow().getGuiScaledWidth() - 40);
            perLine = Math.max(20, avail / 6);
            dirIsTemplate = true;
        } else {
            perLine = seq.length();
            dirIsTemplate = isTemplate;
        }
        Style white = Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF));
        List<Component> lines = new ArrayList<>();
        String leftMark = dirIsTemplate ? "5'" : "3'";
        String rightMark = dirIsTemplate ? "3'" : "5'";
        for (int start = 0; start < seq.length(); start += perLine) {
            String part = seq.substring(start, Math.min(seq.length(), start + perLine));
            MutableComponent line = Component.empty();
            if (start == 0) {
                line.append(Component.literal(leftMark).withStyle(white));
            }
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
            if (start + perLine >= seq.length()) {
                line.append(Component.literal(rightMark).withStyle(white));
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
