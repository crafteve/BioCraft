package com.github.crafteve.biocraft.client.aui;

import com.sighs.apricityui.init.Element;

/**
 * 酶工厂 GUI 自定义 AUI 元素注册中心
 * <p>
 * 直接调用 {@link Element#register(String, java.util.function.BiFunction)} 静态注册，
 * 不依赖 {@code ApricityUIRegistry.scanPackage} 的包扫描时序（scanPackage 必须在 AUI
 * 自身元素注册前调用，而模组构造顺序不受控，直接注册彻底规避该耦合）。
 * 在客户端装配（BioCraftClient 静态块）调用一次即可，此后 HTML 解析到对应标签时
 * 由 {@code Element.init} 查表实例化
 */
public final class EnzymeGuiElements {
    private EnzymeGuiElements() {
    }

    /**
     * 注册全部酶工厂 GUI 自定义元素（仅客户端，BioCraftClient 静态块调用）
     */
    public static void register() {
        Element.register(BiocraftGaugeElement.TAG_NAME, (document, tag) -> new BiocraftGaugeElement(document));
        Element.register(BiocraftProgressElement.TAG_NAME, (document, tag) -> new BiocraftProgressElement(document));
        Element.register(BiocraftBalanceElement.TAG_NAME, (document, tag) -> new BiocraftBalanceElement(document));
        Element.register(BiocraftVtChartElement.TAG_NAME, (document, tag) -> new BiocraftVtChartElement(document));
    }
}
