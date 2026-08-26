package com.github.crafteve.biocraft.item;

import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.central.Codec;
import com.github.crafteve.biocraft.central.Codec;
import com.github.crafteve.biocraft.central.Codec;
import com.github.crafteve.biocraft.init.SequenceData;
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
            case MRNA -> appendMrnaTooltip(data, tooltip);
            case POLYPEPTIDE -> appendPolypeptideTooltip(data, tooltip);
        }
    }

    private static void appendMrnaTooltip(SequenceData data, List<Component> tooltip) {
        String seq = data.seq();
        if (Screen.hasShiftDown()) {
            tooltip.addAll(coloredBases(seq, null));
            return;
        }
        if (Screen.hasControlDown()) {
            // mRNA 的 Ctrl 尝试将 U→T 还原为 DNA 后解码程序
            String dnaEquiv = seq.replace('U', 'T');
            String core = dnaEquiv;
            String prom = com.github.crafteve.biocraft.central.Codec.PROMOTER_CODING;
            String term = com.github.crafteve.biocraft.central.Codec.TERMINATOR_CODING;
            if (core.startsWith(prom) && core.endsWith(term) && core.length() > prom.length() + term.length()) {
                core = core.substring(prom.length(), core.length() - term.length());
            }
            Codec.DecodeResult r = tryDecodeProgram(core);
            if (r.ok()) {
                tooltip.addAll(ProgramHighlight.highlight(r.text()));
            } else {
                tooltip.add(Component.literal("§7非程序 mRNA，无程序可显示"));
            }
            return;
        }
        String state = data.complete() ? "§a完整" : "§7合成中…";
        tooltip.add(Component.literal("§7[mRNA] mRNA §8(" + seq.length() + " nt) " + state));
        tooltip.add(Component.literal("§75'-" + truncate(seq, 10) + "-3'"));
        tooltip.add(Component.literal("§8按住 Shift 彩色序列 / Ctrl 程序"));
    }

    /**
     * DNA tooltip 三态（统一：第一行链型徽章，第二行简写碱基，第三行提示）：
     * <ul>
     *   <li>默认：第一行 [DNA] dsDNA/ssDNA 编码链/模板链 (bp) 完整/合成中；第二行 5'-ATC…-3'；第三行 Shift/Ctrl 提示</li>
     *   <li>Shift：单行完整彩色序列，U 黄，首尾白标</li>
     *   <li>Ctrl：程序高亮</li>
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
            String core = seq;
            String prom = com.github.crafteve.biocraft.central.Codec.PROMOTER_CODING;
            String term = com.github.crafteve.biocraft.central.Codec.TERMINATOR_CODING;
            if (core.startsWith(prom) && core.endsWith(term) && core.length() > prom.length() + term.length()) {
                core = core.substring(prom.length(), core.length() - term.length());
            }
            Codec.DecodeResult r = tryDecodeProgram(core);
            if (r.ok()) {
                tooltip.addAll(ProgramHighlight.highlight(r.text()));
            } else {
                tooltip.add(Component.literal("§7非程序 DNA，无程序可显示"));
            }
            return;
        }
        String state = data.complete() ? "§a完整" : "§7合成中…";
        String strandLabel;
        String unit = data.strand() == SequenceData.Strand.DS ? "bp" : "nt";
        if (data.strand() == SequenceData.Strand.DS) {
            strandLabel = "dsDNA";
        } else if (isTemplate != null) {
            strandLabel = isTemplate ? "ssDNA 编码链" : "ssDNA 模板链";
        } else {
            strandLabel = "ssDNA";
        }
        tooltip.add(Component.literal("§7[DNA] " + strandLabel + " §8(" + seq.length() + " " + unit + ") " + state));
        boolean isTemplateStrand = isTemplate != null && !isTemplate;
        String dirLeft = isTemplateStrand ? "3'-" : "5'-";
        String dirRight = isTemplateStrand ? "-5'" : "-3'";
        String head = seq.length() <= 10 ? seq : seq.substring(0, 10) + "…";
        tooltip.add(Component.literal("§7" + dirLeft + head + dirRight));
        tooltip.add(Component.literal("§8按住 Shift 彩色序列 / Ctrl 程序"));
    }

    /**
     * 多肽 tooltip 三态（与 DNA/mRNA 同构）：
     * <ul>
     *   <li>默认：第一行 [肽链] (N aa) 完整/未完成；第二行 H₂N-Tyr-Gly-…-COOH
     *       三字母残基预览（截断）；第三行 Shift/Ctrl 提示</li>
     *   <li>Shift：单行完整三字母序列——白色 H₂N- 前缀 / -COOH 末端 / "-" 分隔符，
     *       残基按对应 aa-tRNA 物品主题色着色（生化惯例：肽链 N 端 → C 端）</li>
     *   <li>Ctrl：程序反推——规范密码子设计保证 aa1 ↔ 规范密码子双射
     *       （Codec.CANONICAL_AA1/CANONICAL_DNA 同下标），逐残基还原
     *       密码子串后走 SeqCodec 解码 + ProgramHighlight 高亮；
     *       非程序链（天然基因/乱码）解码失败即提示</li>
     * </ul>
     */
    private static void appendPolypeptideTooltip(SequenceData data, List<Component> tooltip) {
        String seq = data.seq();
        if (Screen.hasShiftDown()) {
            Style white = Style.EMPTY.withColor(TextColor.fromRgb(0xFFFFFF));
            MutableComponent line = Component.empty();
            line.append(Component.literal("H₂N-").withStyle(white));
            for (int i = 0; i < seq.length(); i++) {
                if (i > 0) {
                    line.append(Component.literal("-").withStyle(white));
                }
                char aa1 = seq.charAt(i);
                line.append(Component.literal(aa1To3(aa1))
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(aaTint(aa1)))));
            }
            line.append(Component.literal("-COOH").withStyle(white));
            tooltip.add(line);
            return;
        }
        if (Screen.hasControlDown()) {
            StringBuilder codons = new StringBuilder(seq.length() * 3);
            for (int i = 0; i < seq.length(); i++) {
                String codon = aa1ToCanonicalCodon(seq.charAt(i));
                if (codon == null) {
                    tooltip.add(Component.literal("§7含未知氨基酸，无法反推程序"));
                    return;
                }
                codons.append(codon);
            }
            // 首残基为起始密码子 Met 时多肽流以 ATG 开头，tryDecodeProgram 自动兼容
            Codec.DecodeResult r = tryDecodeProgram(codons.toString());
            if (r.ok()) {
                tooltip.addAll(ProgramHighlight.highlight(r.text()));
            } else {
                tooltip.add(Component.literal("§7非程序肽链，无程序可显示"));
            }
            return;
        }
        String state = data.complete() ? "§a完整" : "§c未完成（折叠机拒绝）";
        tooltip.add(Component.literal("§7[肽链] §8(" + seq.length() + " aa) " + state));
        tooltip.add(Component.literal("§7" + peptidePreview(seq, 5)));
        tooltip.add(Component.literal("§8按住 Shift 氨基酸序列 / Ctrl 程序"));
    }

    /** 多肽默认预览：前 maxResidues 个残基的三字母写法（H₂N-Tyr-Gly-…-COOH） */
    private static String peptidePreview(String seq, int maxResidues) {
        StringBuilder sb = new StringBuilder("H₂N-");
        int n = Math.min(seq.length(), maxResidues);
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append('-');
            sb.append(aa1To3(seq.charAt(i)));
        }
        if (seq.length() > maxResidues) {
            sb.append("-…");
        }
        return sb.append("-COOH").toString();
    }

    /**
     * 程序流解码（起始密码子兼容）：先按原样解码；失败且开头为 ATG 时剥掉
     * 起始密码子再试——2026-08-25 起程序 DNA 在正文前固定携带 ATG
     * （Codec.START_CODON_CODING，翻译出的多肽首残基为 Met），
     * DNA/mRNA/多肽三条 Ctrl 反推路径共用
     */
    private static Codec.DecodeResult tryDecodeProgram(String core) {
        Codec.DecodeResult r = Codec.decodeText(core);
        if (!r.ok() && core.startsWith(Codec.START_CODON_CODING)) {
            Codec.DecodeResult r2 = Codec.decodeText(core.substring(3));
            if (r2.ok()) {
                return r2;
            }
        }
        return r;
    }

    /**
     * aa1 → 规范 DNA 密码子（程序反推用）：CANONICAL_AA1 与 CANONICAL_DNA
     * 同下标一一对应；未知字符返回 null（调用方提示不可反推）
     */
    private static String aa1ToCanonicalCodon(char aa1) {
        for (int i = 0; i < Codec.CANONICAL_AA1.length; i++) {
            if (Codec.CANONICAL_AA1[i] == aa1) {
                return Codec.CANONICAL_DNA[i];
            }
        }
        return null;
    }

    /** aa1 → 3 字母缩写（查规范表；未知字符原样返回单字母） */
    private static String aa1To3(char aa1) {
        for (int i = 0; i < Codec.CANONICAL_AA1.length; i++) {
            if (Codec.CANONICAL_AA1[i] == aa1) {
                return Codec.CANONICAL_AA3[i];
            }
        }
        return String.valueOf(aa1);
    }

    /**
     * aa1 → 残基主题色：取对应 aa-tRNA 物品的 substances.json 数据表色
     * （trna_<aa3 小写> 注册名约定，与翻译机 GUI 卡片同源同色）
     */
    private static int aaTint(char aa1) {
        String aa3 = aa1To3(aa1);
        var deferred = ModItems.byId("trna_" + aa3.toLowerCase());
        if (deferred != null && deferred.get() instanceof MoleculeItem molecule) {
            return molecule.getTintColor();
        }
        return 0xCCCCCC;
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
        // 仿 ssDNA 修复：全部单行完整显示，避免 MC 对超宽彩色行的二次折行错乱（原 DS 按屏宽分行仍异常换行）
        int perLine = seq.length();
        boolean dirIsTemplate = isTemplate != null ? isTemplate : true;
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
                    case 'T', 'U' -> dnTpTint("dttp");
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
