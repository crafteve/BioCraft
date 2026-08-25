package com.github.crafteve.biocraft.gui;

import com.github.crafteve.biocraft.BioCraft;
import com.github.crafteve.biocraft.blockentity.SequenceMachineKind;
import net.minecraft.resources.ResourceLocation;

/**
 * 序列机屏幕布局描述（框架差异全部数据化，动画内容仍由各 Screen 子类实现）
 * <p>
 * 每台序列机一份布局常量，基类 SequenceMachineScreen 的 renderBg 据此一次画完
 * 全部家常逻辑（背景贴图/状态栏/标签/输入竖滚卡/输出卡方向/动画区面板骨架/
 * 右上角状态文字与催化剂图标），子类只覆写 renderMachineAnimation 画自己的动画。
 * 新增序列机 = 这里加一行布局 + 子类实现一个动画方法。
 * <ul>
 *   <li>encoder 族（gui_encoder 底）：输出底横滚，中央 178×95 面板——编码器
 *       （plainPanel：面板即代码编辑器，无网格/标题/图标骨架）、转录仪、翻译机</li>
 *   <li>v1 族（gui_v1 底）：输出右竖滚，中央 122×126 大动画区 + 顶栏 LOAD/UNWIND
 *       标签——装载机、解旋酶</li>
 * </ul>
 */
public enum MachineLayout {

    // 编码器：plainPanel = 面板是代码编辑器，基类只铺底色不画网格/标题/图标
    ENCODER(false, true, "", ' ', 0, 0, "", 69, 31, 178, 95, true, "EXT"),
    TRANSCRIBER(false, false, "转录", 'P', 0xFF4FC3F7, 0xFF0288D1, "", 69, 31, 178, 95, true, "TRANS"),
    TRANSLATOR(false, false, "翻译", 'R', 0xFF4FC3F7, 0xFF0288D1, "", 69, 31, 178, 95, true, "TRANS"),
    // v1 族：无面板顶部文字标识（panelTitle 空 + 无图标），动画内容填满整个面板——
    // 中央 LOAD/UNWIND 标签取消（centerLabel 空），动画区上边界向上延长 9px
    // （标签行高）填满中心区域
    LOADER(true, false, "", ' ', 0, 0, "", 68, 29, 122, 135, true, "RUN"),
    HELICASE(true, false, "", ' ', 0, 0, "", 68, 29, 122, 135, true, "UNWIND");

    private final boolean guiV1;
    private final boolean plainPanel;
    private final String panelTitle;
    private final char iconChar;
    private final int iconOuter;
    private final int iconInner;
    private final String centerLabel;
    private final int ax;
    private final int ay;
    private final int aw;
    private final int ah;
    private final boolean hasProgressBar;
    private final String verb;

    MachineLayout(boolean guiV1, boolean plainPanel, String panelTitle, char iconChar,
                  int iconOuter, int iconInner, String centerLabel,
                  int ax, int ay, int aw, int ah, boolean hasProgressBar, String verb) {
        this.guiV1 = guiV1;
        this.plainPanel = plainPanel;
        this.panelTitle = panelTitle;
        this.iconChar = iconChar;
        this.iconOuter = iconOuter;
        this.iconInner = iconInner;
        this.centerLabel = centerLabel;
        this.ax = ax;
        this.ay = ay;
        this.aw = aw;
        this.ah = ah;
        this.hasProgressBar = hasProgressBar;
        this.verb = verb;
    }

    /** 机器类型 → 布局常量 */
    public static MachineLayout of(SequenceMachineKind kind) {
        return switch (kind) {
            case DNA_ENCODER -> ENCODER;
            case TRANSCRIBER -> TRANSCRIBER;
            case TRANSLATOR -> TRANSLATOR;
            case LOADER -> LOADER;
            case HELICASE -> HELICASE;
        };
    }

    /** 背景贴图：v1 族用 gui_v1，encoder 族用 gui_encoder */
    public ResourceLocation bg() {
        return ResourceLocation.fromNamespaceAndPath(BioCraft.MODID,
                guiV1 ? "textures/gui/gui_v1.png" : "textures/gui/gui_encoder.png");
    }

    /** 输出卡方向：v1 族 = 右竖滚（193,41），encoder 族 = 底横滚（70,140） */
    public boolean outputVertical() {
        return guiV1;
    }

    /** 面板是否为素面板（编码器编辑器：只铺底色，无网格/标题/图标骨架） */
    public boolean plainPanel() {
        return plainPanel;
    }

    /** 动画区面板内标题（左上角汉字） */
    public String panelTitle() {
        return panelTitle;
    }

    /** 右上角催化剂图标字母（' ' = 不画图标） */
    public char iconChar() {
        return iconChar;
    }

    public int iconOuter() {
        return iconOuter;
    }

    public int iconInner() {
        return iconInner;
    }

    /** v1 族中央区顶栏标签（LOAD/UNWIND；空串 = 无） */
    public String centerLabel() {
        return centerLabel;
    }

    /** 动画区矩形（GUI 相对坐标） */
    public int ax() {
        return ax;
    }

    public int ay() {
        return ay;
    }

    public int aw() {
        return aw;
    }

    public int ah() {
        return ah;
    }

    /** 状态栏是否画细进度条 */
    public boolean hasProgressBar() {
        return hasProgressBar;
    }

    /** 状态栏进行中动词（EXT/TRANS/RUN/UNWIND） */
    public String verb() {
        return verb;
    }
}
