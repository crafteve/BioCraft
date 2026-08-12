package com.github.crafteve.biocraft.blockentity;

/**
 * 合成结果状态枚举
 * <p>
 * 服务端合成逻辑的返回值与 GUI 状态文本的桥梁：
 * 状态码经 Menu 的 ContainerData 同步到客户端，Screen 按码查询翻译 key 显示提示
 */
public enum SynthesisStatus {
    /** 空闲：尚未执行合成或已成功 */
    IDLE,
    /** 合成成功 */
    SUCCESS,
    /** 序列为空：玩家点击合成但未输入任何碱基 */
    EMPTY_SEQUENCE,
    /** 序列含非法字符或超长（客户端已过滤，服务端兜底校验） */
    INVALID_SEQUENCE,
    /** 碱基不足：输入槽中的碱基数量少于序列需求 */
    INSUFFICIENT_BASE,
    /** 输出槽已满：DNA模板尚未取出 */
    OUTPUT_FULL
}
