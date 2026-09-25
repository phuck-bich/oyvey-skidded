package me.alpha432.oyvey.features.modules.player;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.models.Timer;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
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
import java.util.Comparator;
import java.util.List;

public class SpeedMine extends Module {
    private enum RotateMode {
        None, Normal, Packet
    }

    private enum Sequence {
        Surround, Phase
    }

    private final Setting<Double> range = num("Range", 6.0, 1.0, 8.0);
    private final Setting<Double> speed = num("Speed", 1.0, 0.7, 1.0);
    private final Setting<RotateMode> rotate = mode("Rotate", RotateMode.Packet);

    private final Setting<Boolean> auto = bool("Auto", false);
    private final Setting<Boolean> cityOnly = bool("City Only", false);
    private final Setting<Boolean> holeCheck = bool("Hole Check", false);

    private final Setting<Boolean> doubleMine = bool("Double", false);
    private final Setting<Sequence> sequence = mode("Sequence", Sequence.Surround);
    private final Setting<Boolean> instant = bool("Instant", false);
    private final Setting<Integer> instantDelay = num("Instant Delay", 0, 0, 20);
    private final Setting<Integer> instantTimeout = num("Instant Timeout", 60, 0, 100);

    private final Setting<Boolean> whileEating = bool("While Eating", true);
    private final Setting<Boolean> render = bool("Render", true);
    private final Setting<Boolean> animation = bool("Expand", true);

    private final Timer instantTimer = new Timer();
    private final Timer mineTimer = new Timer();

    private Action primary;
    private Action secondary;
    private int pendingRestoreSlot = -1;
    private long pendingRestoreTime;

    public SpeedMine() {
        super("SpeedMine",
                "Sydney-style packet mining with auto city/double/instant mining.",
                Category.PLAYER,
                false,
                false,
                false);
    }

    @Override
    public void onEnable() {
        primary = null;
        secondary = null;
        pendingRestoreSlot = -1;
        instantTimer.reset();
        mineTimer.reset();
    }

    @Override
    public void onDisable() {
        if (primary != null) primary.cancel();
        if (secondary != null) secondary.cancel();
        primary = null;
        secondary = null;
        restorePendingSlot();
    }

    @Override
    public void onUpdate() {
        if (nullCheck() || mc.interactionManager == null || mc.world == null) return;

        restorePendingSlot();

        if (secondary != null && secondary.process()) secondary = null;
        if (primary != null && primary.process()) primary = null;

        if (auto.getValue()) {
            updateAuto();
        } else {
            BlockHitResult target = getCrosshairBlock();
            if (target != null) handle(target.getBlockPos(), 0);
        }
    }

    private void updateAuto() {
        if (!doubleMine.getValue() && primary != null) return;
        if (doubleMine.getValue() && primary != null && secondary != null) return;

        if (doubleMine.getValue() && !mineTimer.passedMs(350L)) return;

        PlayerEntity target = findTarget();
        if (target == null) return;

        List<BlockPos> positions = getMiningPositions(target);

        if (sequence.getValue() == Sequence.Phase) {
            positions.sort(Comparator.comparingDouble(pos -> -phaseScore(pos, target)));
        }

        for (BlockPos pos : positions) {
            if (isMining(pos) || isInvalid(pos) || isOutOfRange(pos)) continue;

            handle(pos, 0);

            if (!doubleMine.getValue() || (primary != null && secondary != null)) break;
        }
    }

    private BlockHitResult getCrosshairBlock() {
        if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) return null;
        return (BlockHitResult) mc.crosshairTarget;
    }

    public boolean startMining(BlockPos position) {
        return handle(position, 0);
    }

    public boolean isMining(BlockPos position) {
        return (primary != null && primary.position.equals(position))
                || (secondary != null && secondary.position.equals(position));
    }

    private boolean handle(BlockPos position, int priority) {
        if (position == null) return false;
        if (mc.interactionManager.getCurrentGameMode() == GameMode.CREATIVE
                || mc.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR) return false;

        BlockState state = mc.world.getBlockState(position);
        if (state.isReplaceable() || state.getBlock().getHardness() < 0.0f) return false;
        if (mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(position)) > range.getValue() * range.getValue()) return false;

        if (isMining(position)) return true;

        if (doubleMine.getValue()) {
            if (secondary != null) {
                primary = new Action(position, priority);
            } else if (primary != null) {
                if (!primary.instantMine) secondary = primary;
                primary = new Action(position, priority);
            } else {
                primary = new Action(position, priority);
            }
        } else {
            if (primary != null) primary.cancel();
            primary = new Action(position, priority);
        }

        return true;
    }

    private boolean isInvalid(BlockPos position) {
        if (position == null) return true;
        BlockState state = mc.world.getBlockState(position);
        return state.isReplaceable() || state.getBlock().getHardness() < 0.0f || isMining(position);
    }

    private boolean isOutOfRange(BlockPos position) {
        return position == null
                || mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(position))
                > range.getValue() * range.getValue();
    }

    private PlayerEntity findTarget() {
        return mc.world.getPlayers().stream()
                .filter(player -> player != mc.player)
                .filter(PlayerEntity::isAlive)
                .filter(player -> !OyVey.friendManager.isFriend(player))
                .filter(player -> mc.player.squaredDistanceTo(player)
                        <= (range.getValue() + 2.0) * (range.getValue() + 2.0))
                .min(Comparator.comparingDouble(mc.player::squaredDistanceTo))
                .orElse(null);
    }

    private List<BlockPos> getMiningPositions(PlayerEntity target) {
        List<BlockPos> positions = new ArrayList<>();
        BlockPos base = target.getBlockPos();

        if (!cityOnly.getValue()) {
            for (Direction direction : Direction.values()) {
                if (!direction.getAxis().isHorizontal()) continue;
                BlockPos pos = base.offset(direction);
                if (!mc.world.getBlockState(pos).isReplaceable()) positions.add(pos);
            }
        }

        for (Direction direction : Direction.values()) {
            if (!direction.getAxis().isHorizontal()) continue;

            BlockPos pos = base.offset(direction);
            if (!mc.world.getBlockState(pos).isReplaceable()) {
                positions.add(pos);
            }

            BlockPos head = pos.up();
            if (!cityOnly.getValue() && !mc.world.getBlockState(head).isReplaceable()) {
                positions.add(head);
            }
        }

        if (!cityOnly.getValue()) {
            BlockPos head = base.up(2);
            if (!mc.world.getBlockState(head).isReplaceable()) positions.add(head);
        }

        if (holeCheck.getValue() && !isInHole(target)) return List.of();

        return positions.stream().distinct().toList();
    }

    private boolean isInHole(PlayerEntity player) {
        BlockPos base = player.getBlockPos();

        if (!isSolid(base.down())) return false;

        for (Direction direction : Direction.values()) {
            if (!direction.getAxis().isHorizontal()) continue;
            if (!isSolid(base.offset(direction))) return false;
        }

        return true;
    }

    private boolean isSolid(BlockPos pos) {
        return !mc.world.getBlockState(pos).isReplaceable();
    }

    private double phaseScore(BlockPos position, PlayerEntity target) {
        double score = mc.player.squaredDistanceTo(Vec3d.ofCenter(position));

        if (position.getY() == target.getBlockY()) score -= 2.0;
        if (position.getY() > target.getBlockY()) score += 1.0;

        return -score;
    }

    private int findFastestTool(BlockState state) {
        int bestSlot = -1;
        float bestSpeed = 0.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            float miningSpeed = stack.getMiningSpeedMultiplier(state);
            if (miningSpeed > bestSpeed) {
                bestSpeed = miningSpeed;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    private float getMineDelta(BlockState state, int slot) {
        if (slot < 0 || slot >= 9) return 0.0f;

        ItemStack stack = mc.player.getInventory().getStack(slot);
        float miningSpeed = stack.getMiningSpeedMultiplier(state);

        if (miningSpeed <= 0.0f) return 0.0f;

        return miningSpeed / Math.max(state.getBlock().getHardness(), 0.1f)
                / (state.isToolRequired() && !stack.isSuitableFor(state) ? 100.0f : 30.0f);
    }

    private Direction getMiningDirection(BlockPos position) {
        if (mc.player.getY() >= position.getY()) return Direction.UP;

        BlockHitResult result = mc.world.raycast(new net.minecraft.world.RaycastContext(
                mc.player.getEyePos(),
                Vec3d.ofCenter(position),
                net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        if (result != null && result.getType() == HitResult.Type.BLOCK) {
            return result.getSide();
        }

        Direction closest = Direction.UP;
        double distance = Double.MAX_VALUE;

        for (Direction direction : Direction.values()) {
            Vec3d hit = Vec3d.ofCenter(position).add(
                    direction.getOffsetX() * 0.5,
                    direction.getOffsetY() * 0.5,
                    direction.getOffsetZ() * 0.5
            );

            double current = mc.player.getEyePos().squaredDistanceTo(hit);
            if (current < distance) {
                distance = current;
                closest = direction;
            }
        }

        return closest;
    }

    private void rotateTo(BlockPos position, Direction direction) {
        if (rotate.getValue() == RotateMode.None) return;

        Vec3d hit = Vec3d.ofCenter(position).add(
                direction.getOffsetX() * 0.5,
                direction.getOffsetY() * 0.5,
                direction.getOffsetZ() * 0.5
        );

        OyVey.rotationManager.lookAtVec3d(hit);
    }

    private void startPendingRestore(int slot) {
        if (slot == mc.player.getInventory().getSelectedSlot()) return;

        pendingRestoreSlot = slot;
        pendingRestoreTime = System.currentTimeMillis();
    }

    private void restorePendingSlot() {
        if (pendingRestoreSlot == -1) return;
        if (System.currentTimeMillis() - pendingRestoreTime < 100L) return;

        mc.player.getInventory().setSelectedSlot(pendingRestoreSlot);
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(pendingRestoreSlot));
        pendingRestoreSlot = -1;
    }

    @Override
    public void onRender3D(me.alpha432.oyvey.event.impl.Render3DEvent event) {
        if (!render.getValue() || mc.world == null) return;

        if (primary != null) primary.render(event);
        if (doubleMine.getValue() && secondary != null) secondary.render(event);
    }

    @Override
    public String getDisplayInfo() {
        if (primary == null) return "0%";

        int percent = Math.round(MathHelper.clamp(primary.progress / Math.max(primary.getSpeed(), 0.0001f), 0.0f, 1.0f) * 100.0f);
        if (secondary != null && doubleMine.getValue()) {
            int second = Math.round(MathHelper.clamp(secondary.progress / Math.max(secondary.getSpeed(), 0.0001f), 0.0f, 1.0f) * 100.0f);
            return percent + "%, " + second + "%";
        }

        return percent + "%";
    }

    private final class Action {
        private final BlockPos position;
        private BlockState state;
        private final int priority;

        private float progress;
        private float prevProgress;
        private int attempts;
        private boolean mining;
        private boolean instantMine;

        private Action(BlockPos position, int priority) {
            this.position = position;
            this.state = mc.world.getBlockState(position);
            this.priority = priority;
            start();
        }

        private boolean process() {
            if (isOutOfRange(position)) {
                cancel();
                return true;
            }

            BlockState current = mc.world.getBlockState(position);
            if (current.isReplaceable()) {
                if (instantMine) {
                    instantMine = false;
                    return true;
                }
                cancel();
                return true;
            }

            if (current.getBlock() != state.getBlock()) {
                state = current;
            }

            int slot = findFastestTool(state);
            if (slot == -1) slot = mc.player.getInventory().getSelectedSlot();

            float delta = getMineDelta(state, slot);
            if (delta <= 0.0f) return false;

            prevProgress = progress;
            progress = MathHelper.clamp(progress + delta, 0.0f, getSpeed());

            Direction direction = getMiningDirection(position);

            if (rotate.getValue() == RotateMode.Normal
                    && progress + delta * 2.0f >= getSpeed()) {
                rotateTo(position, direction);
            }

            if (progress >= getSpeed()
                    && !current.isReplaceable()
                    && (whileEating.getValue() || !mc.player.isUsingItem())) {

                if (!instantMine || instantTimer.passedMs(instantDelay.getValue() * 50L)) {
                    if (rotate.getValue() == RotateMode.Packet) {
                        rotateTo(position, direction);
                    }

                    int previousSlot = mc.player.getInventory().getSelectedSlot();

                    if (slot != previousSlot) {
                        mc.player.getInventory().setSelectedSlot(slot);
                        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                    }

                    mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                            PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                            position,
                            direction
                    ));
                    mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

                    if (slot != previousSlot) startPendingRestore(previousSlot);

                    attempts++;

                    if (doubleMine.getValue() && this == secondary) {
                        mineTimer.reset();
                        return true;
                    }

                    if (instant.getValue()) {
                        instantMine = true;
                        instantTimer.reset();
                    } else {
                        start();
                    }

                    return false;
                }
            }

            return false;
        }

        private void start() {
            Direction direction = getMiningDirection(position);

            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.START_DESTROY_BLOCK,
                    position,
                    direction
            ));

            if (doubleMine.getValue()) {
                mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK,
                        position,
                        direction
                ));
            } else {
                mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                        position,
                        direction
                ));
            }

            mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

            progress = 0.0f;
            prevProgress = 0.0f;
            attempts = 0;
            mining = true;
            instantMine = false;
        }

        private void cancel() {
            if (!doubleMine.getValue()) {
                mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                        position,
                        getMiningDirection(position)
                ));
                mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }

            progress = 0.0f;
            prevProgress = 0.0f;
            attempts = 0;
            mining = false;
            instantMine = false;
        }

        private float getSpeed() {
            return secondary != null && this == secondary ? 1.0f : speed.getValue().floatValue();
        }

        private void render(me.alpha432.oyvey.event.impl.Render3DEvent event) {
            if (mc.world.getBlockState(position).isReplaceable() && !instantMine) return;

            double fraction = MathHelper.clamp(progress / Math.max(getSpeed(), 0.0001f), 0.0f, 1.0f);
            if (animation.getValue()) {
                double expand = 0.5d * fraction;
                Box box = new Box(position).contract(0.5d).expand(expand);
                Color fill = new Color(255, 50 + (int) (205 * fraction), 0, 55);
                Color line = new Color(255, 50 + (int) (205 * fraction), 0, 180);
                RenderUtil.drawBoxFilled(event.getMatrix(), box, fill);
                RenderUtil.drawBox(event.getMatrix(), box, line, 1.0d);
            } else {
                Color fill = new Color(255, 80, 0, 55);
                Color line = new Color(255, 80, 0, 180);
                RenderUtil.drawBoxFilled(event.getMatrix(), new Box(position), fill);
                RenderUtil.drawBox(event.getMatrix(), new Box(position), line, 1.0d);
            }
        }
    }
}
