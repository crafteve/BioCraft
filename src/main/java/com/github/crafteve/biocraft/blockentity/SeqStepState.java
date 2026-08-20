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

    private Stage stage = Stage.IDLE;
    private int position = 0;
    private int total = 0;
    private String chain = "";
    private String pendingProgram = "";

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

    public void setPendingProgram(String pendingProgram) {
        this.pendingProgram = pendingProgram;
    }

    /** 开始延伸：设定链并归零位置（stage → EXTENDING） */
    public void beginExtending(String chain) {
        this.chain = chain;
        this.total = chain.length();
        this.position = 0;
        this.stage = Stage.EXTENDING;
    }

    /** 完全复位（换模板/换程序） */
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
        return tag;
    }

    public void load(CompoundTag tag) {
        this.stage = tag.contains("stage", Tag.TAG_STRING)
                ? Stage.valueOf(tag.getString("stage")) : Stage.IDLE;
        this.position = tag.getInt("position");
        this.total = tag.getInt("total");
        this.chain = tag.contains("chain", Tag.TAG_STRING) ? tag.getString("chain") : "";
        this.pendingProgram = tag.contains("pendingProgram", Tag.TAG_STRING) ? tag.getString("pendingProgram") : "";
    }
}
