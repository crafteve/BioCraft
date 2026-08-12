package com.github.crafteve.biocraft.blockentity;

import com.github.crafteve.biocraft.BioCraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;

/**
 * 机器类型枚举，区分不同机器的功能与容器规格
 * <p>
 * 所有机器方块共用唯一的 MachineBlock 类（AGENTS.md 1.4 硬性规则），
 * 方块注册时传入本枚举，BlockEntity 工厂按枚举分派创建对应的实体类
 * <p>
 * 机器的特殊行为（容器布局、进度逻辑、GUI 交互）全部由 BlockEntity 承担，
 * 方块类本身只负责通用的放置/交互/掉落行为，因此无需为每种机器建方块类
 *
 * @param id            方块注册名（lower_snake_case）
 * @param containerSize 容器槽位总数
 * @param mapColor      方块地图色，用于在世界地图上区分机器
 */
public enum MachineType {
    /** DNA编码器：手动输入序列，消耗碱基即时产出 DNA模板，无进度 */
    DNA_ENCODER("dna_encoder", 5, MapColor.COLOR_BLUE);

    private final String id;
    private final int containerSize;
    private final MapColor mapColor;

    MachineType(String id, int containerSize, MapColor mapColor) {
        this.id = id;
        this.containerSize = containerSize;
        this.mapColor = mapColor;
    }

    /**
     * 获取方块注册名
     *
     * @return 注册名字符串
     */
    public String getId() {
        return id;
    }

    /**
     * 获取方块注册资源路径（方块状态/模型的资源定位）
     *
     * @return 命名空间资源路径
     */
    public ResourceLocation getBlockLocation() {
        return ResourceLocation.fromNamespaceAndPath(BioCraft.MODID, id);
    }

    /**
     * 获取容器槽位总数
     *
     * @return 槽位数量
     */
    public int getContainerSize() {
        return containerSize;
    }

    /**
     * 获取方块地图色
     *
     * @return 地图色值
     */
    public MapColor getMapColor() {
        return mapColor;
    }
}
