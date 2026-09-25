package me.alpha432.oyvey.features.modules.misc;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.event.impl.PacketEvent;
import me.alpha432.oyvey.event.impl.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.block.BlockState;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class PacketMine extends Module {
    private enum AutoSwap {
        None, Normal, Silent, Swap, Pickup
    }

    private enum Shape {
        None, Fill, Outline, Both
    }

    private enum ColorMode {
        Custom, Normal, Gradient
    }

    private enum RenderMode {
        Static, Grow, Shrink, Both
    }

    private final Setting<Double> range = num("Range", 4.0d, 0.1d, 6.0d);
    private final Setting<Double> progressBreak = num("Progress Break", 1.0d, 0.7d, 1.0d);
    private final Setting<AutoSwap> autoSwap = mode("AutoSwap", AutoSwap.Swap);
    private final Setting<Boolean> awaitBreak = bool("AwaitBreak", true);
    private final Setting<Boolean> pauseOnUse = bool("Pause On Use", false);
    private final Setting<Boolean> resetOnSwitch = bool("Reset On Switch", false);
    private final Setting<Boolean> doubleBreak = bool("Double Break", false);
    private final Setting<Boolean> reBreak = bool("Rebreak", true);
    private final Setting<Boolean> instant = bool("Instant", false);
    private final Setting<Integer> instantDelay = num("Instant Delay", 0, 0, 500);

    private final Setting<Shape> shape = mode("Shape", Shape.Both);
    private final Setting<ColorMode> colorMode = mode("Color", ColorMode.Gradient);
    private final Setting<RenderMode> renderMode = mode("Render", RenderMode.Grow);
    private final Setting<Integer> fillAlpha = num("Fill Alpha", 100, 0, 255);
    private final Setting<Integer> outlineAlpha = num("Outline Alpha", 255, 0, 255);
    private final Setting<Boolean> renderAir = bool("Render Air", true);
    private final Setting<Integer> fadeSpeed = num("Fade Speed", 300, 50, 1000);
    private final Setting<Boolean> easing = bool("Easing", true);
    private final Setting<Boolean> rotate = bool("Rotate", true);

    private final LinkedList<MiningData> miningQueue = new LinkedList<>();
    private final List<FadeEntry> fadingBlocks = new ArrayList<>();

    public PacketMine() {
        super("PacketMine",
                "Packet-based mining engine inspired by Mint's PacketMine.",
                Category.MISC,
                true,
                false,
                false);
    }

    @Override
    public void onEnable() {
        miningQueue.clear();
        fadingBlocks.clear();
    }

    @Override
    public void onDisable() {
        for (MiningData data : miningQueue) {
            if (mc.player != null && mc.player.networkHandler != null) {
                mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                        data.pos,
                        data.direction
                ));
            }
        }
        miningQueue.clear();
        fadingBlocks.clear();
    }

    @Override
    public void onUpdate() {
        if (nullCheck() || mc.interactionManager == null || mc.world == null || mc.player.isCreative()) return;
        if (isPaused()) return;

        if (resetOnSwitch.getValue()) {
            int currentSlot = mc.player.getInventory().getSelectedSlot();
            if (lastSelectedSlot != -1 && currentSlot != lastSelectedSlot && !miningQueue.isEmpty()) {
                MiningData first = miningQueue.removeFirst();
                startFade(first);
            }
            lastSelectedSlot = currentSlot;
        }

        if (mc.options.attackKey.isPressed()) {
            BlockHitResult hit = getCrosshairBlock();
            if (hit != null) {
                startMiningPos(hit.getBlockPos(), hit.getSide());
            }
        }

        Iterator<MiningData> iterator = miningQueue.iterator();
        while (iterator.hasNext()) {
            MiningData data = iterator.next();

            if (isOutOfRange(data.pos)) {
                startFade(data);
                iterator.remove();
                continue;
            }

            if (data.process()) {
                iterator.remove();
            }
        }

        updateFades();
    }

    private int lastSelectedSlot = -1;

    private boolean isPaused() {
        return pauseOnUse.getValue() && mc.player.isUsingItem();
    }

    private BlockHitResult getCrosshairBlock() {
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return null;
        return (BlockHitResult) mc.crosshairTarget;
    }

    /**
     * Allows another module, such as AutoMine, to select the packet-mine mode
     * without exposing the setting itself.
     */
    public void setInstantMine(boolean enabled) {
        instant.setValue(enabled);
    }

    public boolean isMining(BlockPos pos) {
        if (pos == null) return false;

        for (MiningData data : miningQueue) {
            if (data.pos.equals(pos)) return true;
        }

        for (FadeEntry entry : fadingBlocks) {
            if (entry.pos.equals(pos)) return true;
        }

        return false;
    }

    public MiningData getMiningData() {
        return miningQueue.isEmpty() ? null : miningQueue.getFirst();
    }

    public MiningData getPrevMiningData() {
        return miningQueue.size() > 1 ? miningQueue.get(1) : null;
    }

    public boolean canMinePos(BlockPos pos) {
        if (nullCheck() || pos == null || isMining(pos)) return false;
        if (mc.interactionManager.getCurrentGameMode() == GameMode.CREATIVE
                || mc.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR) return false;

        BlockState state = mc.world.getBlockState(pos);
        return !state.isAir()
                && !state.isLiquid()
                && state.getHardness(mc.world, pos) >= 0.0f;
    }

    public boolean startMining(BlockPos pos) {
        if (pos == null) return false;
        return startMiningPos(pos, getMiningDirection(pos));
    }

    public boolean startMining(BlockPos pos, Direction direction) {
        return startMiningPos(pos, direction);
    }

    public boolean startMiningPos(BlockPos pos, Direction direction) {
        return startMiningPos(pos, direction, doubleBreak.getValue());
    }

    public boolean startMiningPos(BlockPos pos, Direction direction, boolean forceDoubleBreak) {
        return startMiningPos(pos, direction, forceDoubleBreak, instant.getValue());
    }

    public boolean startMiningPos(BlockPos pos, Direction direction, boolean forceDoubleBreak, boolean forceInstant) {
        if (isPaused() || pos == null || direction == null) return false;
        if (!canMinePos(pos)) return false;
        if (isOutOfRange(pos)) return false;

        MiningData newData = new MiningData(pos.toImmutable(), direction, progressBreak.getValue().floatValue(), forceDoubleBreak, this);

        if (!miningQueue.isEmpty()) {
            if (forceDoubleBreak && miningQueue.size() < 2) {
                miningQueue.addLast(newData);
            } else {
                MiningData old = miningQueue.removeFirst();
                startFade(old);
                miningQueue.addFirst(newData);
            }
        } else {
            miningQueue.add(newData);
        }

        sendStartPacket(newData, forceInstant);
        return true;
    }

    private void sendStartPacket(MiningData data, boolean forceInstant) {
        if (mc.player == null || mc.player.networkHandler == null) return;

        if (data.doubleMode) {
            sendAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, data.pos, data.direction);
            sendAction(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, data.pos, data.direction);
            sendAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, data.pos, data.direction);
        } else {
            sendAction(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, data.pos, data.direction);
        }

        mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }

    private void sendAction(PlayerActionC2SPacket.Action action, BlockPos pos, Direction direction) {
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(action, pos, direction));
    }

    private void attemptMine(MiningData data) {
        if (isPaused() || mc.player == null || mc.player.networkHandler == null) return;

        int toolSlot = data.getToolSlot();
        int previous = mc.player.getInventory().getSelectedSlot();

        if (toolSlot >= 0 && toolSlot < 9 && toolSlot != previous && autoSwap.getValue() != AutoSwap.None) {
            mc.player.getInventory().setSelectedSlot(toolSlot);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(toolSlot));
        }

        if (rotate.getValue()) {
            OyVey.rotationManager.lookAtVec3d(
                    Vec3d.ofCenter(data.pos).add(
                            data.direction.getOffsetX() * 0.5d,
                            data.direction.getOffsetY() * 0.5d,
                            data.direction.getOffsetZ() * 0.5d
                    )
            );
        }

        sendAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, data.pos, data.direction);

        if (data.doubleMode) {
            sendAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, data.pos, data.direction);
        }

        mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        if (awaitBreak.getValue() && canAwaitBreak(data)) {
            mc.interactionManager.breakBlock(data.pos);
        }

        if (toolSlot >= 0 && toolSlot < 9 && toolSlot != previous && autoSwap.getValue() != AutoSwap.None) {
            mc.player.getInventory().setSelectedSlot(previous);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previous));
        }
    }

    private boolean canAwaitBreak(MiningData data) {
        BlockState state = mc.world.getBlockState(data.pos);
        if (state.isAir()) return false;
        return data.getDigSpeed(state) > 0.0f
                && data.damage >= progressBreak.getValue().floatValue() * 0.95f;
    }

    private Direction getMiningDirection(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        Vec3d delta = center.subtract(eye);

        if (Math.abs(delta.x) > Math.abs(delta.y) && Math.abs(delta.x) > Math.abs(delta.z)) {
            return delta.x > 0 ? Direction.WEST : Direction.EAST;
        }

        if (Math.abs(delta.z) > Math.abs(delta.y)) {
            return delta.z > 0 ? Direction.NORTH : Direction.SOUTH;
        }

        return delta.y > 0 ? Direction.DOWN : Direction.UP;
    }

    private boolean isOutOfRange(BlockPos pos) {
        return mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos))
                > range.getValue() * range.getValue();
    }

    private int findFastestTool(BlockState state) {
        int bestSlot = -1;
        float bestSpeed = 0.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            float speed = stack.getMiningSpeedMultiplier(state);
            if (stack.isSuitableFor(state)) speed += 0.01f;

            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private float getDigSpeed(BlockState state) {
        int slot = findFastestTool(state);
        ItemStack stack = slot == -1 ? mc.player.getMainHandStack() : mc.player.getInventory().getStack(slot);

        float speed = stack.getMiningSpeedMultiplier(state);
        if (speed <= 0.0f) speed = 1.0f;

        if (speed > 1.0f) {
            int haste = mc.player.getStatusEffect(StatusEffects.HASTE) == null
                    ? 0
                    : mc.player.getStatusEffect(StatusEffects.HASTE).getAmplifier() + 1;
            speed *= 1.0f + haste * 0.2f;
        }

        if (mc.player.getStatusEffect(StatusEffects.MINING_FATIGUE) != null) {
            int amplifier = mc.player.getStatusEffect(StatusEffects.MINING_FATIGUE).getAmplifier();
            float multiplier = switch (amplifier) {
                case 0 -> 0.3f;
                case 1 -> 0.09f;
                case 2 -> 0.0027f;
                default -> 0.00081f;
            };
            speed *= multiplier;
        }

        if (!mc.player.isOnGround()) speed /= 5.0f;
        return speed;
    }

    @Subscribe
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof BlockUpdateS2CPacket packet)) return;

        BlockPos pos = packet.getPos();

        for (MiningData data : miningQueue) {
            if (!data.pos.equals(pos)) continue;

            if (packet.getState().isAir()) {
                data.onBecameAir();
                startFade(data);
            } else {
                data.onBecameSolid();
            }
        }
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (shape.getValue() == Shape.None) return;

        for (MiningData data : miningQueue) {
            if (!data.state.isAir() || renderAir.getValue()) {
                float progress = data.remine ? 1.0f
                        : MathHelper.clamp(data.damage / progressBreak.getValue().floatValue(), 0.0f, 1.0f);
                renderBox(event, data.pos, progress, 1.0f);
            }
        }

        long now = System.currentTimeMillis();
        for (FadeEntry entry : fadingBlocks) {
            long age = now - entry.startTime;
            if (age > fadeSpeed.getValue()) continue;

            float alpha = 1.0f - age / (float) Math.max(1, fadeSpeed.getValue());
            renderBox(event, entry.pos, entry.progress, alpha);
        }
    }

    private void renderBox(Render3DEvent event, BlockPos pos, float progress, float alpha) {
        double scale = getRenderScale(progress);
        if (scale <= 0.01d) return;

        double offset = (1.0d - scale) * 0.5d;
        Box box = new Box(
                pos.getX() + offset,
                pos.getY() + offset,
                pos.getZ() + offset,
                pos.getX() + 1.0d - offset,
                pos.getY() + 1.0d - offset,
                pos.getZ() + 1.0d - offset
        );

        Color fill = getColor(progress, true, alpha);
        Color outline = getColor(progress, false, alpha);

        if (shape.getValue() == Shape.Fill || shape.getValue() == Shape.Both) {
            RenderUtil.drawBoxFilled(event.getMatrix(), box, fill);
        }

        if (shape.getValue() == Shape.Outline || shape.getValue() == Shape.Both) {
            RenderUtil.drawBox(event.getMatrix(), box, outline, 1.0d);
        }
    }

    private double getRenderScale(float progress) {
        if (renderMode.getValue() == RenderMode.Static) return 1.0d;

        float p = MathHelper.clamp(progress, 0.0f, 1.0f);
        if (easing.getValue()) p = 1.0f - (1.0f - p) * (1.0f - p);

        return switch (renderMode.getValue()) {
            case Grow -> 0.01d + 0.99d * p;
            case Shrink -> 1.0d - 0.99d * p;
            case Both -> {
                float triangle = p < 0.5f ? p * 2.0f : (1.0f - p) * 2.0f;
                yield 0.01d + 0.99d * triangle;
            }
            default -> 1.0d;
        };
    }

    private Color getColor(float progress, boolean fill, float alpha) {
        int baseAlpha = fill ? fillAlpha.getValue() : outlineAlpha.getValue();
        int a = MathHelper.clamp((int) (baseAlpha * alpha), 0, 255);

        return switch (colorMode.getValue()) {
            case Custom -> fill
                    ? new Color(255, 0, 0, a)
                    : new Color(255, 255, 255, a);
            case Normal -> progress >= 0.9f
                    ? new Color(0, 255, 0, a)
                    : new Color(255, 0, 0, a);
            case Gradient -> new Color(
                    (int) (255.0f * (1.0f - progress)),
                    (int) (255.0f * progress),
                    0,
                    a
            );
        };
    }

    private void startFade(MiningData data) {
        if (data == null) return;

        for (FadeEntry existing : fadingBlocks) {
            if (existing.pos.equals(data.pos)) return;
        }

        float progress = data.remine
                ? 1.0f
                : MathHelper.clamp(data.damage / progressBreak.getValue().floatValue(), 0.0f, 1.0f);

        if (!renderAir.getValue() && mc.world.getBlockState(data.pos).isAir()) progress = 0.0f;

        fadingBlocks.add(new FadeEntry(data.pos, System.currentTimeMillis(), progress));
    }

    private void updateFades() {
        long now = System.currentTimeMillis();
        fadingBlocks.removeIf(entry -> now - entry.startTime > fadeSpeed.getValue());
    }

    @Override
    public String getDisplayInfo() {
        MiningData data = getMiningData();
        if (data == null) return "0.0";

        float target = progressBreak.getValue().floatValue();
        return String.format("%.1f", Math.min(data.damage / target, 1.0f));
    }

    public static class MiningData {
        private final BlockPos pos;
        private final Direction direction;
        private final float targetProgress;
        private final boolean doubleMode;
        private final PacketMine parent;

        private BlockState state;
        private float damage;
        private boolean remine;
        private boolean sawAir;
        private long unlockAt;

        private MiningData(BlockPos pos, Direction direction, float targetProgress, boolean doubleMode, PacketMine parent) {
            this.pos = pos;
            this.direction = direction;
            this.targetProgress = targetProgress;
            this.doubleMode = doubleMode;
            this.parent = parent;
            this.state = mc.world.getBlockState(pos);
        }

        private void onBecameAir() {
            sawAir = true;
            damage = 0.0f;
        }

        private void onBecameSolid() {
            if (!parent.reBreak.getValue() || !parent.instant.getValue() || !sawAir) return;

            sawAir = false;
            remine = true;
            damage = 0.0f;
            unlockAt = System.currentTimeMillis() + parent.instantDelay.getValue();
        }

        private boolean process() {
            if (mc.world.getBlockState(pos).isAir()) {
                if (!parent.reBreak.getValue()) return true;
                onBecameAir();
                return false;
            }

            state = mc.world.getBlockState(pos);

            if (remine) {
                if (System.currentTimeMillis() < unlockAt) return false;

                damage = 1.0f;
                parent.attemptMine(this);
                damage = 0.0f;
                remine = true;
                return false;
            }

            float speed = getDigSpeed(state);
            if (speed <= 0.0f) return false;

            damage = MathHelper.clamp(
                    damage + speed / Math.max(state.getHardness(mc.world, pos), 0.1f)
                            / (state.isToolRequired() ? 100.0f : 30.0f),
                    0.0f,
                    1.0f
            );

            if (damage >= targetProgress && !state.isAir()) {
                parent.attemptMine(this);

                if (!parent.reBreak.getValue()) return true;

                damage = 0.0f;
            }

            return false;
        }

        private int getToolSlot() {
            return parent.findFastestTool(state);
        }

        private float getDigSpeed(BlockState state) {
            return parent.getDigSpeed(state);
        }

        public BlockPos getPos() {
            return pos;
        }

        public Direction getDirection() {
            return direction;
        }

        public BlockState getState() {
            return state;
        }

        public float getBlockDamage() {
            return damage;
        }

        public boolean isRemine() {
            return remine;
        }
    }

    private record FadeEntry(BlockPos pos, long startTime, float progress) {
    }
}
