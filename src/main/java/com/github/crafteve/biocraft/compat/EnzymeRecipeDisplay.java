package com.github.crafteve.biocraft.compat;

import com.github.crafteve.biocraft.init.ModItems;
import com.github.crafteve.biocraft.reaction.EnzymeFactoryData;
import com.github.crafteve.biocraft.reaction.KineticConstants;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 酶工厂配方的展示模型（JEI/EMI 共享的只读 DTO）
 * <p>
 * 由 {@link EnzymeFactoryData} 在注册期转换构建：物品 id 解析为实际 ItemStack、
 * 固定活性物种标记、ΔG°′ 换算，此后全程只读。JEI 与 EMI 的显示层都只依赖
 * 本模型，保证两侧配方卡内容与数据完全一致，新增酶（改 enzymes.json）自动生效
 * <p>
 * 本类只依赖 Minecraft 与 reaction 包，不 import 任何 JEI/EMI 类——
 * 两套显示层插件在各自框架存在时才被加载，本模型始终可安全加载
 *
 * @param enzymeId    酶注册名（与方块注册名一致，调试定位用）
 * @param displayName 显示名（酶数据表中文名，与 GUI 标题一致）
 * @param abbreviation 酶缩写（如 PGI/HK/GAPDH，信息条黄字徽标）
 * @param ecCategory  EC 类别（EC1~EC6，信息条类别色文本）
 * @param kinetic     动力学变体（LIMITING/ISOMERASE/OXIDO_LYASE，决定变体文案）
 * @param reversible  反应是否可逆（决定箭头 ⇌/→）
 * @param keq         平衡常数（Keq 文本）
 * @param deltaG      ΔG°′（kJ/mol，由 Keq 换算：−RT·ln(Keq)）
 * @param kcat        正向周转数（s⁻¹，信息条）
 * @param tempOptimum 最适温度（K，信息条）
 * @param activators  激活剂物品列表（信息条行 2）
 * @param inputs      反应物条目（左侧槽位，按酶数据表顺序）
 * @param outputs     产物条目（右侧槽位，按酶数据表顺序）
 */
public record EnzymeRecipeDisplay(
        String enzymeId,
        String displayName,
        String abbreviation,
        String ecCategory,
        String kinetic,
        boolean reversible,
        double keq,
        double deltaG,
        double kcat,
        double tempOptimum,
        List<ItemStack> activators,
        List<Entry> inputs,
        List<Entry> outputs) {

    /**
     * 单个物种在配方卡中的展示条目
     *
     * @param stack         物品堆（数量 1，实际系数在 count 字段，槽内堆叠数不表示化学计量）
     * @param count         化学计量系数（tooltip 显示 ×N，系数 1 不显示）
     * @param km            米氏常数（mM，Km 文本；固定活性物种为 0）
     * @param fixedActivity 固定活性标记（H₂O/H⁺ 不参与速率计算，灰色槽 + tooltip 说明）
     */
    public record Entry(ItemStack stack, int count, double km, boolean fixedActivity) {
    }

    /** 酶 id -> 展示模型缓存（注册期构建一次，运行期只读） */
    private static final Map<String, EnzymeRecipeDisplay> CACHE = new LinkedHashMap<>();

    /**
     * 由酶数据档案构建展示模型（缓存）
     *
     * @param data 酶数据档案
     * @return 展示模型（同一酶返回同一实例）
     */
    public static EnzymeRecipeDisplay from(EnzymeFactoryData data) {
        return CACHE.computeIfAbsent(data.id(), id -> build(data));
    }

    /**
     * 构建展示模型：物种条目转换 + ΔG°′ 换算
     * <p>
     * ΔG°′ = −R·T₀·ln(Keq)，R 与 T₀ 直接复用引擎常量（KineticConstants），
     * 保证显示值与引擎热力学数据同一基准（R 为 J 单位，换算 kJ 除以 1000）
     *
     * @param data 酶数据档案
     * @return 新展示模型
     */
    private static EnzymeRecipeDisplay build(EnzymeFactoryData data) {
        List<Entry> inputs = data.reactants().stream().map(EnzymeRecipeDisplay::toEntry).toList();
        List<Entry> outputs = data.products().stream().map(EnzymeRecipeDisplay::toEntry).toList();
        List<ItemStack> activators = data.activators().stream()
                .map(id -> new ItemStack(ModItems.byId(id).get()))
                .toList();
        double deltaG = CompatRenderUtil.deltaGFromKeq(data.keq());
        return new EnzymeRecipeDisplay(data.id(), data.nameZn(), data.abbreviation(),
                data.category(), data.kinetic(), data.reversible(), data.keq(), deltaG,
                data.kcat(), data.tempOptimum(), activators, inputs, outputs);
    }

    /**
     * 物种条目转展示条目：物品 id 解析 + 固定活性标记
     *
     * @param spec 酶数据表中的物种条目
     * @return 展示条目
     */
    private static Entry toEntry(EnzymeFactoryData.SpeciesSpec spec) {
        ItemStack stack = new ItemStack(ModItems.byId(spec.item()).get());
        boolean fixed = CompatRenderUtil.isFixedActivity(spec.item());
        return new Entry(stack, spec.count(), spec.kmMillimolar(), fixed);
    }
}
