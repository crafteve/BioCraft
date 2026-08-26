package com.github.crafteve.biocraft.blockentity.sequence;

import com.github.crafteve.biocraft.blockentity.base.MachineBlockEntity;
import com.github.crafteve.biocraft.gui.sequence.SequenceMachineMenu;
import com.github.crafteve.biocraft.init.ModDataComponents;
import com.github.crafteve.biocraft.blockentity.sequence.operation.DnaSynthesisOperation;
import com.github.crafteve.biocraft.blockentity.sequence.operation.HelicaseOperation;
import com.github.crafteve.biocraft.blockentity.sequence.operation.LoaderOperation;
import com.github.crafteve.biocraft.blockentity.sequence.operation.TranscriptionOperation;
import com.github.crafteve.biocraft.blockentity.sequence.operation.TranslatorOperation;
import com.github.crafteve.biocraft.seq.SeqCodec;
import com.github.crafteve.biocraft.seq.SequenceConstants;
import com.github.crafteve.biocraft.seq.SequenceData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 序列机 BE 基类：只做编排（tick 步进/存档/停摆/物化），不懂具体操作
 * <p>
 * 链源模型（设计稿 §5）：SeqStepState（stage/position/chain）= 唯一真相，
 * 产物槽物品 = 物化（每步同步刷新）；取走产物自动重建新物品继续、
 * 原料不够停止（state 保留，补料即续）、换模板/换程序归零 + 旧产物弹出；
 * 步进频率 K = STEP_TICKS（配置常量，Phase 3/4 工程读速从这挂入）
 */
public class SequenceMachineBlockEntity extends MachineBlockEntity {

    private final SequenceOperation operation;
    private final SeqStepState stepState = new SeqStepState();
    private int stepCooldown = 0;
    private String lastTemplateSeq = "";

    /** 编辑器草稿（未提交的程序文本，跨 GUI 打开保留；NBT 存档） */
    private String programDraft = "";

    public SequenceMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, resolveContainerSize(state));
        this.operation = resolveOperation(state);
    }

    /** 方块实体工厂构造（BlockEntityType.Builder.of 需要 (BlockPos, BlockState) 签名） */
    public SequenceMachineBlockEntity(BlockPos pos, BlockState state) {
        this(com.github.crafteve.biocraft.init.ModBlocks.SEQUENCE_BE.get(), pos, state);
    }

    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    private static int resolveContainerSize(BlockState state) {
        SequenceMachineKind kind = SequenceMachineKind.fromBlockState(state);
        return kind != null ? kind.containerSize() : 2;
    }

    private static SequenceOperation resolveOperation(BlockState state) {
        SequenceMachineKind kind = SequenceMachineKind.fromBlockState(state);
        return kind != null ? kind.createOperation() : new DnaSynthesisOperation();
    }

    public SequenceMachineKind kind() {
        return SequenceMachineKind.fromBlockState(getBlockState());
    }

    public SequenceOperation operation() {
        return operation;
    }

    public SeqStepState stepState() {
        return stepState;
    }

    /** 读取编辑器草稿（打开 GUI 时客户端恢复用） */
    public String lastTemplateSeq() {
        return lastTemplateSeq;
    }

    public void setLastTemplateSeq(String seq) {
        this.lastTemplateSeq = seq != null ? seq : "";
        setChanged();
    }

    public String programDraft() {
        return programDraft;
    }

    /** 写入编辑器草稿（客户端文本变化经网络包保存） */
    public void setProgramDraft(String draft) {
        if (draft != null && !draft.equals(this.programDraft)) {
            this.programDraft = draft;
            setChanged();
        }
    }

    /** 打开数据包追加编辑器草稿（编码器；BioCraftMachineBlock 写入顺序：pos → 本钩子） */
    @Override
    public void writeMenuOpeningData(net.minecraft.network.FriendlyByteBuf buf) {
        if (kind() == SequenceMachineKind.DNA_ENCODER) {
            buf.writeUtf(programDraft);
        }
    }

    /** 服务端每 tick 调度（SequenceMachineBlock.getTicker 挂载） */
    public static void serverTick(Level level, BlockPos pos, BlockState blockState, SequenceMachineBlockEntity be) {
        be.tickServer();
    }

    private void tickServer() {
        if (level == null || level.isClientSide) {
            return;
        }
        // 工序中模板指纹追踪（转录仪/翻译机通用）：模板链被拿走或换成别的链时，
        // 弹出旧产物 + 状态归零——防止"幽灵翻译/幽灵转录"（密码子串存在内存里，
        // step 不查槽位，不追踪则抽走 mRNA 后机器照翻不误、塞新链也被无视）
        Integer templateSlot = switch (kind()) {
            case TRANSCRIBER -> TranscriptionOperation.SLOT_TEMPLATE;
            case TRANSLATOR -> TranslatorOperation.SLOT_MRNA;
            default -> null;
        };
        if (templateSlot != null && !lastTemplateSeq.isEmpty()) {
            ItemStack tmpl = inventory.getItem(templateSlot);
            SequenceData tmplData = tmpl.get(ModDataComponents.SEQUENCE.get());
            String curSeq = tmplData != null ? tmplData.seq() : "";
            boolean tmplEmpty = tmpl.isEmpty();
            boolean tmplChanged = !curSeq.equals(lastTemplateSeq);
            if (tmplEmpty || tmplChanged) {
                ItemStack oldOut = inventory.getItem(operation.outputSlot());
                if (!oldOut.isEmpty() && level != null) {
                    Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, oldOut);
                    inventory.setItem(operation.outputSlot(), ItemStack.EMPTY);
                }
                stepState.reset();
                lastTemplateSeq = "";
                stepCooldown = 0;
                setChanged();
                if (tmplEmpty) return;
            }
        }
        switch (stepState.stage()) {
            case IDLE -> {
                // 编码器/转录机/翻译机均改为点击按钮才触发（fix：禁自动开工，
                // 不自动创建空产物）——三机共用 ServerboundTranscribePacket 启动
                if (kind() == SequenceMachineKind.DNA_ENCODER
                        || kind() == SequenceMachineKind.TRANSCRIBER
                        || kind() == SequenceMachineKind.TRANSLATOR) return;
                if (operation.canStart(inventory, stepState) && operation.init(inventory, stepState)) {
                    materialize();
                    setChanged();
                }
            }
            case EXTENDING -> {
                // 转录机/翻译机：工序中取走产物则重置（与转录机同理，防止半成品被取后继续）
                if ((kind() == SequenceMachineKind.TRANSCRIBER || kind() == SequenceMachineKind.TRANSLATOR)
                        && inventory.getItem(operation.outputSlot()).isEmpty()
                        && stepState.position() > 0) {
                    stepState.reset();
                    lastTemplateSeq = "";
                    stepCooldown = 0;
                    setChanged();
                    return;
                }
                if (--stepCooldown > 0) {
                    return;
                }
                // 翻译机节奏 = 每密码子 3 tick（1 tick 1 碱基的逐碱基读移意象），
                // 其余序列机维持全局 STEP_TICKS 节奏
                stepCooldown = kind() == SequenceMachineKind.TRANSLATOR
                        ? TranslatorOperation.TICKS_PER_CODON
                        : SequenceConstants.STEP_TICKS;
                SequenceOperation.StepResult result = operation.step(inventory, stepState);
                if (result == SequenceOperation.StepResult.DONE) {
                    operation.finish(inventory, stepState);
                    stepState.setStage(SeqStepState.Stage.DONE);
                }
                materialize();
                setChanged();
            }
            case DONE -> {
                if (kind() == SequenceMachineKind.LOADER) {
                    // 每 tick 二态检测：输入齐全+输出有空间+类型匹配=可工作（RUNNING），否则停止（IDLE）
                    // 与 GUI working 同口径（LoaderOperation.isWorkable），废弃 stage 三态显示
                    boolean workable = LoaderOperation.isWorkable(inventory);
                    if (workable) {
                        // 可工作则连续作业自动回 IDLE 接下一轮（1 tick 一轮）
                        stepState.setStage(SeqStepState.Stage.IDLE);
                        setChanged();
                    } else {
                        // 不可工作（缺料/产满/错种）需取走产物才回 IDLE
                        boolean doneEmpty = inventory.getItem(operation.outputSlot()).isEmpty();
                        if (doneEmpty) {
                            stepState.setStage(SeqStepState.Stage.IDLE);
                            setChanged();
                        }
                    }
                    break;
                }
                if (kind() == SequenceMachineKind.TRANSLATOR) {
                    boolean workable = TranslatorOperation.isWorkable(inventory);
                    if (workable) {
                        stepState.setStage(SeqStepState.Stage.IDLE);
                        setChanged();
                    } else {
                        boolean doneEmpty = inventory.getItem(operation.outputSlot()).isEmpty();
                        if (doneEmpty) {
                            stepState.setStage(SeqStepState.Stage.IDLE);
                            setChanged();
                        }
                    }
                    break;
                }
                boolean doneEmpty;
                if (kind() == SequenceMachineKind.HELICASE) {
                    doneEmpty = inventory.getItem(HelicaseOperation.SLOT_OUT_A).isEmpty()
                            && inventory.getItem(HelicaseOperation.SLOT_OUT_B).isEmpty();
                } else {
                    doneEmpty = inventory.getItem(operation.outputSlot()).isEmpty();
                }
                if (doneEmpty) {
                    stepState.setStage(SeqStepState.Stage.IDLE);
                    setChanged();
                }
            }
        }
    }

    /** 物化链前缀到产物槽（产物被取走后自动重建新物品） */
    private void materialize() {
        operation.materialize(inventory, stepState);
    }

    /**
     * 编码器提交程序文本（网络包到达）：换文本 = 换模板语义
     * <p>先编码验证（超上限直接拒绝）；旧产物弹出；链源状态归零 + 写入新程序；
     * 分子余量保留（槽位连续消耗状态，跨程序接着用）</p>
     */
    public void submitProgram(String program) {
        if (program == null || program.isEmpty()) {
            return;
        }
        try {
            SeqCodec.encodeText(program);
        } catch (IllegalArgumentException e) {
            return; // 超上限：拒绝
        }
        ItemStack old = inventory.getItem(DnaSynthesisOperation.SLOT_OUT_DNA);
        if (!old.isEmpty()) {
            inventory.setItem(DnaSynthesisOperation.SLOT_OUT_DNA, ItemStack.EMPTY);
            if (level != null) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5, old);
            }
        }
        stepState.reset();
        stepState.setPendingProgram(program);
        setChanged();
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SequenceMachineMenu(kind(), containerId, playerInventory, this);
    }

    @Override
    public void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("seqState", stepState.save(new CompoundTag()));
        tag.putString("draft", programDraft);
        tag.putString("lastTemplateSeq", lastTemplateSeq);
    }

    @Override
    public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("seqState", Tag.TAG_COMPOUND)) {
            stepState.load(tag.getCompound("seqState"));
        }
        if (tag.contains("draft", Tag.TAG_STRING)) {
            programDraft = tag.getString("draft");
        }
        if (tag.contains("lastTemplateSeq", Tag.TAG_STRING)) {
            lastTemplateSeq = tag.getString("lastTemplateSeq");
        }
        if (kind() == SequenceMachineKind.DNA_ENCODER) {
            restoreOutputSlots();
        }
    }

    /**
     * 容器序列化覆写（根因修复，2026-08-19）：
     * <p>
     * vanilla 的 SimpleContainer.createTag/fromTag **不写 Slot index**——
     * 保存只存非空物品紧凑列表、读档用 addItem 顺序填充空槽（源码实证
     * SimpleContainer L223-234/L215-221）。序列机有空槽（如编码器取出
     * DNA 后槽 5 为空）时，读档会把后续物品整体前移：实测"ADP 进 DNA 槽、
     * PPi 进 ADP 槽"（槽 5 空 → adp 落槽 5、ppi 落槽 6）。
     * <p>
     * 本覆写保存带 Slot index（ItemStack.save 全量，保留 SEQUENCE 组件），
     * 读档按 Slot 恢复；旧格式（无 Slot 字段）走 addItem 兼容（保持原行为，
     * 错位由 restoreOutputSlots 自愈）
     */
    @Override
    protected net.minecraft.nbt.Tag saveContainerData(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            net.minecraft.nbt.CompoundTag entry = new net.minecraft.nbt.CompoundTag();
            entry.putInt("Slot", slot);
            list.add(stack.save(registries, entry));
        }
        return list;
    }

    @Override
    protected void loadContainerData(net.minecraft.nbt.ListTag list, net.minecraft.core.HolderLookup.Provider registries) {
        inventory.clearContent();
        boolean hasSlot = !list.isEmpty() && list.getCompound(0).contains("Slot", Tag.TAG_INT);
        for (net.minecraft.nbt.Tag element : list) {
            net.minecraft.nbt.CompoundTag entry = (net.minecraft.nbt.CompoundTag) element;
            ItemStack stack = ItemStack.parse(registries, entry).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                continue;
            }
            if (hasSlot) {
                int slot = entry.getInt("Slot");
                if (slot >= 0 && slot < inventory.getContainerSize()) {
                    inventory.setItem(slot, stack);
                }
            } else {
                // 旧紧凑格式（无 Slot 字段）：addItem 顺序填充（保持原行为）
                for (int s = 0; s < inventory.getContainerSize(); s++) {
                    if (inventory.getItem(s).isEmpty()) {
                        inventory.setItem(s, stack);
                        break;
                    }
                }
            }
        }
    }

    /**
     * 读档后校正编码器输出槽（自愈旧存档的 addItem 错位）：
     * <p>
     * 槽 5/6/7 按机器语义归位——DNA 槽（5）只存 DNA（SEQUENCE 组件）、
     * ADP 槽（6）只存 adp、PPi 槽（7）只存 ppi（GUI mayPlace=false +
     * 漏斗 canPlaceItem=false 堵死外部塞入，异类物品必为存档错位）。
     * 从 5/6/7 中按物品 id 识别 adp/ppi 放回正确槽位；槽 5 只保留 DNA；
     * stage != IDLE 时按链源重新物化槽 5
     */
    private void restoreOutputSlots() {
        int a = DnaSynthesisOperation.SLOT_OUT_ADP;
        int p = DnaSynthesisOperation.SLOT_OUT_PPI;
        ItemStack s5 = inventory.getItem(DnaSynthesisOperation.SLOT_OUT_DNA);
        ItemStack s6 = inventory.getItem(a);
        ItemStack s7 = inventory.getItem(p);
        boolean fiveDna = !s5.isEmpty() && s5.get(ModDataComponents.SEQUENCE.get()) != null;
        boolean sixAdp = !s6.isEmpty() && SequenceContainerUtil.matchesId(s6, "adp");
        boolean sevenPpi = !s7.isEmpty() && SequenceContainerUtil.matchesId(s7, "ppi");
        boolean wrong = (!fiveDna && !s5.isEmpty())       // 槽5 非 DNA（如错位的 adp）
                || (!sixAdp && !s6.isEmpty())             // 槽6 非 adp（如错位的 ppi）
                || (!sevenPpi && !s7.isEmpty());          // 槽7 非 ppi
        if (wrong) {
            com.github.crafteve.biocraft.BioCraft.LOGGER.warn(
                    "编码器读档输出槽错位校正: slot5={} slot6={} slot7={}", s5, s6, s7);
            ItemStack adpStack = SequenceContainerUtil.matchesId(s5, "adp") ? s5
                    : (sixAdp ? s6 : (SequenceContainerUtil.matchesId(s7, "adp") ? s7 : ItemStack.EMPTY));
            ItemStack ppiStack = SequenceContainerUtil.matchesId(s5, "ppi") ? s5
                    : (SequenceContainerUtil.matchesId(s6, "ppi") ? s6
                    : (sevenPpi ? s7 : ItemStack.EMPTY));
            inventory.setItem(DnaSynthesisOperation.SLOT_OUT_DNA, fiveDna ? s5 : ItemStack.EMPTY);
            inventory.setItem(a, adpStack);
            inventory.setItem(p, ppiStack);
        }
        // DNA 槽重新物化（覆盖污染/缺失；IDLE 不物化——无链可物化）
        if (stepState.stage() != SeqStepState.Stage.IDLE) {
            operation.materialize(inventory, stepState);
        }
    }

    @Override
    protected int slotStackLimit() {
        if (kind() == SequenceMachineKind.HELICASE) {
            return 1;
        }
        return 64;
    }

    @Override
    protected boolean canPlaceItemInternal(int slot, ItemStack stack) {
        return operation.isItemValidForSlot(slot, stack);
    }

    /**
     * 抽取门控（原版漏斗 removeItem / 管道 extractItem 同规则，三路统一）：
     * 编码器输入槽只进不出（防漏斗抽走单体破坏产线）、DNA 槽仅完全编码
     * （complete）可抽（半成品锁在槽内）、ADP/PPi 可抽；转录仪保持全可抽
     * （重做时定）。插入侧已由 canPlaceItemInternal = 操作层过滤兜底
     * （输出槽恒拒绝，防漏斗塞入被物化覆盖吞掉）
     */
    @Override
    protected boolean canTakeItemInternal(int slot) {
        if (kind() == SequenceMachineKind.DNA_ENCODER) {
            if (slot < DnaSynthesisOperation.SLOT_OUT_DNA) {
                return false;
            }
            if (slot == DnaSynthesisOperation.SLOT_OUT_DNA) {
                SequenceData data = inventory.getItem(slot).get(ModDataComponents.SEQUENCE.get());
                return data != null && data.complete();
            }
            return true;
        }
        if (kind() == SequenceMachineKind.HELICASE) {
            if (slot == HelicaseOperation.SLOT_IN_DNA) {
                return false;
            }
            if (slot == HelicaseOperation.SLOT_OUT_A || slot == HelicaseOperation.SLOT_OUT_B) {
                SequenceData data = inventory.getItem(slot).get(ModDataComponents.SEQUENCE.get());
                return data != null && data.complete();
            }
            return true;
        }
        if (kind() == SequenceMachineKind.TRANSCRIBER) {
            if (slot == TranscriptionOperation.SLOT_TEMPLATE) {
                return false;
            }
            if (slot >= TranscriptionOperation.SLOT_ATP && slot <= TranscriptionOperation.SLOT_GTP) {
                return false;
            }
            if (slot == TranscriptionOperation.SLOT_OUT_MRNA) {
                SequenceData data = inventory.getItem(slot).get(ModDataComponents.SEQUENCE.get());
                return data != null && data.complete();
            }
            return true;
        }
        if (kind() == SequenceMachineKind.LOADER) {
            if (slot == LoaderOperation.SLOT_TRNA || slot == LoaderOperation.SLOT_AA || slot == LoaderOperation.SLOT_ATP) {
                return false;
            }
            return true;
        }
        if (kind() == SequenceMachineKind.TRANSLATOR) {
            if (slot == TranslatorOperation.SLOT_MRNA || slot == TranslatorOperation.SLOT_GTP) return false;
            if (slot >= TranslatorOperation.SLOT_AATRNA_START && slot <= TranslatorOperation.SLOT_AATRNA_END) return false;
            if (slot == TranslatorOperation.SLOT_OUT_POLYPEPTIDE) {
                SequenceData d = inventory.getItem(slot).get(ModDataComponents.SEQUENCE.get());
                return d != null && d.complete();
            }
            return true;
        }
        return true;
    }
}


