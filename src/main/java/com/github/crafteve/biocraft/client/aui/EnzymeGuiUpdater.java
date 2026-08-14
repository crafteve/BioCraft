package com.github.crafteve.biocraft.client.aui;

import com.github.crafteve.biocraft.gui.EnzymeGuiConstants;
import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.item.MoleculeItem;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import com.sighs.apricityui.ApricityUI;
import com.sighs.apricityui.element.Item;
import com.sighs.apricityui.element.Slot;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 酶工厂 GUI 文档填充器（纯 Java push 的唯一入口）
 * <p>
 * 分两层职责：
 * <ul>
 *   <li>{@link #buildStatic(Document)}：一次性构建标题/方程式/物种卡片（按酶数据，
 *       惰性 + 幂等，由已构建文档 UUID 集合守卫），数据全部来自 enzymes.json 解析
 *       出的 {@link EnzymeFactoryData}，新增酶无需改任何代码</li>
 *   <li>{@link #pushDynamicText(Document)}：每 tick 刷新速率/方向/温度/进度百分比等
 *       动态文本（直接写 textContent，量小、频率低）</li>
 * </ul>
 * 动态进度条/平衡条/v-t 图不走本类，由各自定义元素在 drawPhase 逐帧读取
 * {@link EnzymeGuiContext} 绘制
 */
public final class EnzymeGuiUpdater {
    /** 已完成静态构建的文档 UUID（幂等守卫，避免每 tick 重复建 DOM） */
    private static final Set<UUID> BUILT = new HashSet<>();

    private EnzymeGuiUpdater() {
    }

    /**
     * 向全部打开的酶工厂文档推送（客户端数据包处理调用）
     * <p>
     * 每 tick 调用：静态构建首次执行后短路，动态文本持续刷新
     */
    public static void pushAll() {
        if (!EnzymeGuiContext.isReady()) {
            return;
        }
        for (Document document : ApricityUI.getDocument(EnzymeGuiConstants.GUI_PATH)) {
            buildStatic(document);
            pushDynamicText(document);
        }
    }

    /**
     * 一次性构建静态 DOM（标题/方程式/物种卡片/主题色）
     * <p>
     * 物种槽位为 AUI 真实槽位（slot-index = 物种下标），其最近的 container 祖先
     * 是 {@code block_entity} 容器，由 SlotDataBinder 在检测到槽位数量变化后自动
     * 重新绑定到方块实体 IItemHandler，无需手动触发绑定
     *
     * @param document 酶工厂文档
     */
    private static void buildStatic(Document document) {
        if (document == null || BUILT.contains(document.getUuid())) {
            return;
        }
        BUILT.add(document.getUuid());
        EnzymeFactoryData data = EnzymeGuiContext.data();

        // 标题：缩写徽章 / 中文名 / 环境参数
        setText(document, "abbr", data.abbreviation());
        setText(document, "name", data.nameZn());

        // 主题色注入 CSS 变量 --accent（body 内联，覆盖 :root 默认值）
        Element body = document.body;
        if (body != null) {
            String hex = String.format(Locale.ROOT, "#%06X", EnzymeGuiContext.accentColor() & 0xFFFFFF);
            body.setAttribute("style", "--accent:" + hex + ";");
        }

        // 反应方程式（居中显示）
        setText(document, "equation", buildEquation(data));

        // 物种卡片：输入列（反应物）+ 输出列（产物），槽位下标 = 物种下标
        Element reactants = document.getElementById("reactants");
        Element products = document.getElementById("products");
        int slot = 0;
        if (reactants != null) {
            for (EnzymeFactoryData.SpeciesSpec spec : data.reactants()) {
                reactants.appendChild(buildSpeciesCard(document, slot++, spec.item()));
            }
        }
        if (products != null) {
            for (EnzymeFactoryData.SpeciesSpec spec : data.products()) {
                products.appendChild(buildSpeciesCard(document, slot++, spec.item()));
            }
        }
    }

    /**
     * 每 tick 刷新动态文本（速率/方向/温度/进度百分比）
     *
     * @param document 酶工厂文档
     */
    private static void pushDynamicText(Document document) {
        double flux = EnzymeGuiContext.flux();
        setText(document, "rate-value", String.format(Locale.ROOT, "%.2f", Math.abs(flux)));
        setText(document, "rate-dir", flux > 0.001 ? "→ 正向" : flux < -0.001 ? "← 逆向" : "≈ 平衡");
        setText(document, "env", String.format(Locale.ROOT, "T %.2fK · pH 7.00", EnzymeGuiContext.temperature()));

        int n = EnzymeGuiContext.speciesCount();
        double progress = EnzymeGuiContext.concentration(n - 1);
        setText(document, "progress-pct", String.format(Locale.ROOT, "%.0f%%", progress * 100.0));
    }

    /**
     * 构建单张物种卡片：槽位 + 物品名 + 缩写 + 浓度条
     * <p>
     * 槽位为 AUI 真实槽位（带 minecraft:air 的 Item 子节点，供 SlotDataBinder 识别为
     * 可绑定内容）；浓度条为 BiocraftGaugeElement，填充色 = 物品染色（高饱和度纯色）。
     * 动态创建的 DOM 元素全部使用内联样式（字面量颜色），不依赖类选择器匹配，
     * 规避动态元素 CSS 匹配时序问题
     *
     * @param document 文档
     * @param slot     物种下标（= 槽位下标）
     * @param itemId   物种物品注册名
     * @return 物种卡片元素
     */
    private static Element buildSpeciesCard(Document document, int slot, String itemId) {
        MoleculeItem molecule = ModItems.byId(itemId).get();
        String tint = String.format(Locale.ROOT, "#%06X", molecule.getTintColor() & 0xFFFFFF);

        Element card = document.createElement("div");
        card.setAttribute("style",
                "position:relative;height:34px;margin-bottom:4px;background:#FFFFFF;" +
                        "border:1px solid #D8DEE6;border-radius:8px;");

        Slot slotElement = new Slot(document);
        slotElement.setAttribute("slot-index", String.valueOf(slot));
        slotElement.setAttribute("style",
                "position:absolute;left:5px;top:6px;width:22px;height:22px;" +
                        "background:#FFFFFF;border:1px solid #D8DEE6;border-radius:6px;");
        Item itemChild = new Item(document);
        itemChild.setTextContent("minecraft:air");
        slotElement.appendChild(itemChild);
        card.appendChild(slotElement);

        Element name = document.createElement("div");
        name.setAttribute("style",
                "position:absolute;left:32px;top:3px;font-size:10px;font-weight:600;" +
                        "color:#1F2937;white-space:nowrap;overflow:hidden;max-width:80px;");
        name.setTextContent(molecule.getDescription().getString());
        card.appendChild(name);

        Element abbr = document.createElement("div");
        abbr.setAttribute("style",
                "position:absolute;left:32px;top:17px;font-size:9px;font-weight:700;" +
                        "letter-spacing:0.5px;color:" + tint + ";white-space:nowrap;");
        abbr.setTextContent(molecule.getAbbreviation());
        card.appendChild(abbr);

        BiocraftGaugeElement gauge = new BiocraftGaugeElement(document);
        gauge.setAttribute("data-slot", String.valueOf(slot));
        gauge.setAttribute("data-color", String.format(Locale.ROOT, "%08X", molecule.getTintColor()));
        gauge.setAttribute("style", "position:absolute;left:32px;top:27px;width:80px;height:5px;");
        card.appendChild(gauge);

        return card;
    }

    /**
     * 拼装反应方程式：化学计量系数（>1 时前缀）+ 缩写，'+' 连接，中间 ⇌/→
     *
     * @param data 酶数据档案
     * @return 方程式字符串
     */
    private static String buildEquation(EnzymeFactoryData data) {
        StringBuilder sb = new StringBuilder();
        appendSide(sb, data.reactants());
        sb.append(' ').append(data.reversible() ? '⇌' : '→').append(' ');
        appendSide(sb, data.products());
        return sb.toString();
    }

    private static void appendSide(StringBuilder sb, java.util.List<EnzymeFactoryData.SpeciesSpec> specs) {
        for (int i = 0; i < specs.size(); i++) {
            if (i > 0) {
                sb.append('+');
            }
            EnzymeFactoryData.SpeciesSpec spec = specs.get(i);
            if (spec.count() > 1) {
                sb.append(spec.count());
            }
            MoleculeItem molecule = ModItems.byId(spec.item()).get();
            sb.append(molecule.getAbbreviation());
        }
    }

    private static void setText(Document document, String id, String text) {
        Element element = document.getElementById(id);
        if (element != null) {
            element.setTextContent(text);
        }
    }
}
