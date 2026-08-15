package com.github.crafteve.biocraft.blockentity;

import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * 酶工厂能量存储适配器（IEnergyStorage 实现，能量管道接入用）
 * <p>
 * 方向由 fe 净化学计量自动判定（构造参数 output）：
 * <ul>
 *   <li>output=true（fe 在产物侧，发电机）：canExtract、不可充入——
 *       能量管道把存量抽给其他 mod 的用电设备</li>
 *   <li>output=false（fe 在反应物侧，合成器）：canReceive、不可抽出——
 *       外部充入存量供反应消耗（本批数据暂无 input 模式酶）</li>
 * </ul>
 * 存量直接读写宿主 BE 的 energyStored（权威单一），
 * 外部变更经 setChanged 链标记存档（槽位不变时 syncFromSlots 幂等）
 */
public class MachineEnergyStorage implements IEnergyStorage {
    /** 宿主方块实体（存量读写与存档标记） */
    private final EnzymeFactoryBlockEntity blockEntity;

    /** 容量（FE，构造时固化） */
    private final int capacity;

    /** 方向：true = 发电机（只可抽）、false = 合成器（只可充） */
    private final boolean output;

    /**
     * @param blockEntity 宿主酶工厂方块实体
     * @param capacity    能量容量（FE）
     * @param output      方向：产物侧 fe（发电机）为 true，反应物侧 fe 为 false
     */
    public MachineEnergyStorage(EnzymeFactoryBlockEntity blockEntity, int capacity, boolean output) {
        this.blockEntity = blockEntity;
        this.capacity = capacity;
        this.output = output;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (output || maxReceive <= 0) {
            return 0;
        }
        int stored = blockEntity.getEnergyStored();
        int accepted = Math.min(maxReceive, capacity - stored);
        if (!simulate && accepted > 0) {
            blockEntity.addEnergy(accepted);
        }
        return accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        if (!output || maxExtract <= 0) {
            return 0;
        }
        int stored = blockEntity.getEnergyStored();
        int extracted = Math.min(maxExtract, stored);
        if (!simulate && extracted > 0) {
            blockEntity.consumeEnergy(extracted);
        }
        return extracted;
    }

    @Override
    public int getEnergyStored() {
        return blockEntity.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored() {
        return capacity;
    }

    @Override
    public boolean canExtract() {
        return output;
    }

    @Override
    public boolean canReceive() {
        return !output;
    }
}
