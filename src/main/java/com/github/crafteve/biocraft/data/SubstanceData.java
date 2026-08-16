package com.github.crafteve.biocraft.data;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 物质数据表读取工具（classpath 读取 substances.json）
 * <p>
 * 供运行时注册（ModItems）与 datagen 各 Provider 共用同一份读取逻辑，
 * 保证 datagen 生成的资源与游戏内注册的物品始终一致
 * <p>
 * 本工具只读不写，只负责把 JSON 解析为内存对象，具体生成/注册逻辑在各调用方
 */
public final class SubstanceData {

    private SubstanceData() {
    }

    /**
     * 从 classpath 读取物质数据表 JSON 根对象
     * <p>
     * datagen 运行时 src/main/resources 位于 classpath 中，
     * 因此可以像运行时一样通过 getResourceAsStream 读取，无文件系统路径依赖
     *
     * @return 物质表 JSON 根对象
     */
    public static JsonObject loadRoot() {
        String path = "/data/biocraft/molecule/substances.json";
        InputStream stream = SubstanceData.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("未找到物质数据表: " + path);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return GsonHelper.parse(reader);
        } catch (Exception e) {
            throw new IllegalStateException("解析物质数据表失败: " + path, e);
        }
    }

    /**
     * 解析 #色号 字符串为 ARGB int（substances.json 与 enzymes.json 的 color 字段共用）
     * <p>
     * 数据表颜色采用 #RRGGBB 十六进制色号（与网页/MC 惯例一致，肉眼可读可调），
     * 引擎与渲染层内部统一用 ARGB int（alpha 恒 0xFF）——本方法在注册期做换算，
     * 输入非法（非 # 开头/位数不对/含非十六进制字符）直接抛异常快速失败，
     * 与数据防火墙同风格：颜色数据错误应在启动期暴露而非静默产出异常色
     *
     * @param hex #RRGGBB（6 位，自动补 alpha 0xFF）或 #AARRGGBB（8 位完整）
     * @return ARGB int
     */
    public static int parseColor(String hex) {
        if (hex == null || hex.charAt(0) != '#') {
            throw new IllegalArgumentException("颜色必须为 # 开头色号: " + hex);
        }
        int len = hex.length();
        if (len != 7 && len != 9) {
            throw new IllegalArgumentException("颜色色号位数非法（#RRGGBB 6 位或 #AARRGGBB 8 位）: " + hex);
        }
        try {
            int value = Integer.parseUnsignedInt(hex.substring(1), 16);
            return len == 7 ? value | 0xFF000000 : value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("颜色色号含非法字符: " + hex, e);
        }
    }
}
