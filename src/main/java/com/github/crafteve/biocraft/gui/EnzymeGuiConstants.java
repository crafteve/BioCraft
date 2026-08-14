package com.github.crafteve.biocraft.gui;

/**
 * 酶工厂 GUI 的公共常量
 * <p>
 * 逻辑路径被服务端（MachineBlock 打开菜单）与客户端（EnzymeGuiUpdater 查找
 * 文档）共用，集中定义避免两侧硬编码字符串漂移
 */
public final class EnzymeGuiConstants {
    /** 酶工厂 GUI 模板的逻辑路径（AUI 资源空间，不含 assets/apricityui/apricity/ 前缀） */
    public static final String GUI_PATH = "screens/enzyme_factory.html";

    private EnzymeGuiConstants() {
    }
}
