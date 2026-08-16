package com.github.crafteve.biocraft.item;

/**
 * 图标缩写标注提供者（IItemDecorator 的数据源接口）
 * <p>
 * 分子物品与酶蛋白物品的图标左上角都标注缩写（MoleculeItemDecorator 绘制），
 * 原实现直接 instanceof MoleculeItem 判断，酶物品无法复用；
 * 抽为本接口后装饰器只依赖"能提供缩写"这一契约，
 * MoleculeItem 与 EnzymeItem 各自实现，注册时逐个挂到物品上
 */
public interface AbbreviationProvider {
    /**
     * 获取图标缩写标注文本
     *
     * @return 缩写字符串（如 G6P/HK/NAD⁺，空串则不绘制标注）
     */
    String getAbbreviation();
}
