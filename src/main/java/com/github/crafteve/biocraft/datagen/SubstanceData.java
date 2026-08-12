package com.github.crafteve.biocraft.datagen;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 物质数据表读取工具，供 datagen 各 Provider 使用
 * <p>
 * 与运行时 ModItems 共用同一份 substances.json（classpath 读取），
 * 保证 datagen 生成的资源与游戏内注册的物品始终一致
 * <p>
 * 本工具只读不写，只负责把 JSON 解析为内存对象，具体生成逻辑在各 Provider 中
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
}
