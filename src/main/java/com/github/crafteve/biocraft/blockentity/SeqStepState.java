package com.github.crafteve.biocraft.blockentity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * 序列机步进状态（链源模型的 BE 侧工作副本）
 * <p>
 * stage/position/chain = 唯一真相；产物槽物品只是物化（每步同步刷新 NBT）；
 * 取走产物自动重建、原料不够停止（本状态保留，补料即续）、
 * 换模板/换程序归零 + 旧产物弹出（见设计稿 §5）
 */
public final class SeqStepState {

    public enum Stage { IDLE, EXTENDING, DONE }

    /** 余量槽位数（扩至 32，覆盖翻译机 26 槽；编码器 8/转录仪不使用恒 0） */
    private static final int REMAINDER_SLOTS = 32;

    private Stage stage = Stage.IDLE;
    private int position = 0;
    private int total = 0;
    private String chain = "";
    private String pendingProgram = "";

    /**
     * 分子余量（0~1，每槽一个，酶工厂同款模式）：
     * 1 分子 = 10 碱基时，每碱基余量 +0.1，满 1.0 才真正从槽位消耗/向槽位产出——
     * 槽位物品是整数，小数余量存这里（BE 权威），GUI 显示 count + 余量。
     * 余量是槽位分子的连续消耗状态（64 个 dATP 用了 0.9 个 = 63.1 个），
     * 跨编码批次/换程序**保留**（化学计量：清零等于白送分子）；
     * 转录仪 1:1 消耗恒为 0
     */
    private final double[] remainders = new double[REMAINDER_SLOTS];

    public Stage stage() {
        return stage;
    }

    public int position() {
        return position;
    }

    public int total() {
        return total;
    }

    public String chain() {
        return chain;
    }

    public String pendingProgram() {
        return pendingProgram;
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    /** 写入总步数（默认 beginExtending 用 chain.length()，装载机 1:1 需覆写为 1） */
    public void setTotal(int total) {
        this.total = total;
    }

    public void setPendingProgram(String pendingProgram) {
        this.pendingProgram = pendingProgram;
    }

    /** 读取槽位分子余量（越界槽恒 0） */
    public double remainder(int slot) {
        return slot >= 0 && slot < remainders.length ? remainders[slot] : 0.0;
    }

    /** 写入槽位分子余量（越界槽忽略） */
    public void setRemainder(int slot, double value) {
        if (slot >= 0 && slot < remainders.length) {
            remainders[slot] = value;
        }
    }

    /** 开始延伸：设定链并归零位置（stage → EXTENDING） */
    public void beginExtending(String chain) {
        this.chain = chain;
        this.total = chain.length();
        this.position = 0;
        this.stage = Stage.EXTENDING;
    }

    /**
     * 链源复位（换模板/换程序）：stage/position/total/chain/pendingProgram
     * 归零；**分子余量保留**——余量是槽位分子的连续消耗状态（如 64 个
     * dATP 用了 0.9 = 63.1），跨程序接着用，清零等于白送分子
     */
    public void reset() {
        this.stage = Stage.IDLE;
        this.position = 0;
        this.total = 0;
        this.chain = "";
        this.pendingProgram = "";
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putString("stage", stage.name());
        tag.putInt("position", position);
        tag.putInt("total", total);
        tag.putString("chain", chain);
        tag.putString("pendingProgram", pendingProgram);
        for (int i = 0; i < remainders.length; i++) {
            tag.putDouble("rem" + i, remainders[i]);
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        this.stage = tag.contains("stage", Tag.TAG_STRING)
                ? Stage.valueOf(tag.getString("stage")) : Stage.IDLE;
        this.position = tag.getInt("position");
        this.total = tag.getInt("total");
        this.chain = tag.contains("chain", Tag.TAG_STRING) ? tag.getString("chain") : "";
        this.pendingProgram = tag.contains("pendingProgram", Tag.TAG_STRING) ? tag.getString("pendingProgram") : "";
        for (int i = 0; i < remainders.length; i++) {
            remainders[i] = tag.contains("rem" + i, Tag.TAG_DOUBLE) ? tag.getDouble("rem" + i) : 0.0;
        }
    }
}
