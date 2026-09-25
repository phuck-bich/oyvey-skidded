package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.RenderUtil;
import me.alpha432.oyvey.util.models.Timer;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class AutoTrap extends Module {
    private enum TopMode { Full, Top, Face, None }
    private enum BottomMode { Single, Platform, Full, None }

    private final Setting<Double> placeRange = num("Place Range", 4.0d, 0.0d, 6.0d);
    private final Setting<Double> wallsRange = num("Walls Range", 4.0d, 0.0d, 6.0d);
    private final Setting<Double> targetRange = num("Target Range", 3.0d, 0.0d, 10.0d);
    private final Setting<Integer> delay = num("Place Delay", 1, 0, 20);
    private final Setting<Integer> blocksPerTick = num("Blocks Per Tick", 1, 1, 6);
    private final Setting<TopMode> topBlocks = mode("Top Blocks", TopMode.Full);
    private final Setting<BottomMode> bottomBlocks = mode("Bottom Blocks", BottomMode.Platform);
    private final Setting<Boolean> selfToggle = bool("Self Toggle", true);
    private final Setting<Boolean> rotate = bool("Rotate", true);
    private final Setting<Boolean> render = bool("Render", true);
    private final Setting<Boolean> filled = bool("Filled", true);
    private final Setting<Boolean> outlined = bool("Outlined", true);

    private final Timer timer = new Timer();
    private final List<BlockPos> placePositions = new ArrayList<>();

    private PlayerEntity target;
    private boolean placed;
    private int ticks;

    public AutoTrap() {
        super("AutoTrap", "Traps a nearby player in a block enclosure.", Category.COMBAT, false, false, false);
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            disable();
            return;
        }
        target = null;
        placed = false;
        ticks = 0;
        placePositions.clear();
        timer.reset();
    }

    @Override
    public void onDisable() {
        target = null;
        placePositions.clear();
    }

    @Override
    public void onUpdate() {
        if (nullCheck() || mc.interactionManager == null || mc.world == null) return;

        BlockSlot block = findBlock();
        if (block.slot == -1) return;

        if (target == null || !isValidTarget(target)) {
            target = findTarget();
            if (target == null) {
                placePositions.clear();
                return;
            }
        }

        fillPlaceArray(target);

        if (ticks < delay.getValue()) {
            ticks++;
            return;
        }

        if (placePositions.isEmpty()) {
            if (selfToggle.getValue() && placed) disable();
            return;
        }

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        if (oldSlot != block.slot) {
            mc.player.getInventory().setSelectedSlot(block.slot);
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(block.slot));
        }

        int count = 0;
        for (BlockPos pos : placePositions) {
            if (count >= blocksPerTick.getValue()) break;
            if (place(pos)) {
                placed = true;
                count++;
            }
        }

        if (oldSlot != block.slot) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(oldSlot));
        }

        if (count > 0) {
            ticks = 0;
            timer.reset();
        } else {
            ticks++;
        }
    }

    @Override
    public void onRender3D(me.alpha432.oyvey.event.impl.Render3DEvent event) {
        if (!render.getValue() || placePositions.isEmpty()) return;

        for (int i = 0; i < placePositions.size(); i++) {
            BlockPos pos = placePositions.get(i);
            Color fill = i < blocksPerTick.getValue()
                    ? new Color(227, 196, 245, 20)
                    : new Color(197, 137, 232, 10);
            Color line = i < blocksPerTick.getValue()
                    ? new Color(5, 139, 221, 180)
                    : new Color(197, 137, 232, 120);

            if (filled.getValue()) RenderUtil.drawBoxFilled(event.getMatrix(), new Box(pos), fill);
            if (outlined.getValue()) RenderUtil.drawBox(event.getMatrix(), new Box(pos), line, 1.0d);
        }
    }

    private PlayerEntity findTarget() {
        PlayerEntity best = null;
        double bestDistance = targetRange.getValue() * targetRange.getValue();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive()) continue;
            if (OyVey.friendManager.isFriend(player)) continue;

            double distance = mc.player.squaredDistanceTo(player);
            if (distance > bestDistance) continue;

            if (best == null || player.getHealth() < best.getHealth()
                    || (player.getHealth() == best.getHealth() && distance < bestDistance)) {
                best = player;
                bestDistance = distance;
            }
        }

        return best;
    }

    private boolean isValidTarget(PlayerEntity player) {
        return player.isAlive()
                && !OyVey.friendManager.isFriend(player)
                && mc.player.squaredDistanceTo(player) <= targetRange.getValue() * targetRange.getValue();
    }

    private void fillPlaceArray(PlayerEntity player) {
        placePositions.clear();

        double epsilon = 1.0e-5d;
        Box box = player.getBoundingBox();

        Set<BlockPos> corners = new LinkedHashSet<>();
        corners.add(BlockPos.ofFloored(box.minX, box.minY, box.minZ));
        corners.add(BlockPos.ofFloored(box.minX, box.minY, box.maxZ - epsilon));
        corners.add(BlockPos.ofFloored(box.maxX - epsilon, box.minY, box.minZ));
        corners.add(BlockPos.ofFloored(box.maxX - epsilon, box.minY, box.maxZ - epsilon));

        for (BlockPos base : corners) {
            switch (topBlocks.getValue()) {
                case Full -> {
                    add(base.up(2));
                    add(base.offset(1, 1, 0));
                    add(base.offset(-1, 1, 0));
                    add(base.offset(0, 1, 1));
                    add(base.offset(0, 1, -1));
                }
                case Top -> add(base.up(2));
                case Face -> {
                    add(base.offset(1, 1, 0));
                    add(base.offset(-1, 1, 0));
                    add(base.offset(0, 1, 1));
                    add(base.offset(0, 1, -1));
                }
                case None -> { }
            }

            switch (bottomBlocks.getValue()) {
                case Platform -> {
                    add(base.down());
                    add(base.offset(1, -1, 0));
                    add(base.offset(-1, -1, 0));
                    add(base.offset(0, -1, 1));
                    add(base.offset(0, -1, -1));
                }
                case Full -> {
                    add(base.down());
                    add(base.offset(1, 0, 0));
                    add(base.offset(-1, 0, 0));
                    add(base.offset(0, 0, 1));
                    add(base.offset(0, 0, -1));
                }
                case Single -> add(base.down());
                case None -> { }
            }
        }

        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        placePositions.sort(Comparator.comparingDouble(
                pos -> -squaredDistance(x, y, z, pos.getX() + 0.5d, pos.getY() + 0.5d, pos.getZ() + 0.5d)
        ));
    }

    private void add(BlockPos pos) {
        if (placePositions.contains(pos)) return;
        if (!mc.world.getBlockState(pos).isReplaceable()) return;
        if (intersectsEntity(new Box(pos))) return;
        if (isOutOfRange(pos)) return;
        placePositions.add(pos.toImmutable());
    }

    private boolean place(BlockPos pos) {
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;

        BlockHitResult hit = findPlacementHit(pos);
        if (hit == null) return false;

        if (rotate.getValue()) lookAt(hit.getPos());

        var result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);
        return result.isAccepted();
    }

    private BlockHitResult findPlacementHit(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();

        for (Direction direction : Direction.values()) {
            BlockPos support = pos.offset(direction.getOpposite());
            if (mc.world.getBlockState(support).isReplaceable()) continue;

            Vec3d hit = Vec3d.ofCenter(support).add(
                    direction.getOffsetX() * 0.5d,
                    direction.getOffsetY() * 0.5d,
                    direction.getOffsetZ() * 0.5d
            );

            var ray = mc.world.raycast(new RaycastContext(
                    eye, hit,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    mc.player
            ));

            if (ray.getType() == HitResult.Type.BLOCK && ray.getBlockPos().equals(support)) {
                return new BlockHitResult(hit, direction, support, false);
            }
        }

        return null;
    }

    private boolean isOutOfRange(BlockPos pos) {
        Vec3d center = Vec3d.ofCenter(pos);
        if (mc.player.squaredDistanceTo(center) > placeRange.getValue() * placeRange.getValue()) return true;

        Vec3d eye = mc.player.getEyePos();
        var ray = mc.world.raycast(new RaycastContext(
                eye, center,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        if (ray.getType() == HitResult.Type.BLOCK && ray.getBlockPos().equals(pos)) return false;
        return mc.player.squaredDistanceTo(center) > wallsRange.getValue() * wallsRange.getValue();
    }

    private boolean intersectsEntity(Box box) {
        return !mc.world.getOtherEntities(mc.player, box, entity -> entity.isAlive()).isEmpty();
    }

    private BlockSlot findBlock() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            Block block = Block.getBlockFromItem(stack.getItem());
            if (block == net.minecraft.block.Blocks.OBSIDIAN
                    || block == net.minecraft.block.Blocks.CRYING_OBSIDIAN
                    || block == net.minecraft.block.Blocks.NETHERITE_BLOCK) {
                return new BlockSlot(i);
            }
        }
        return new BlockSlot(-1);
    }

    private double squaredDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2, dy = y1 - y2, dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }

    private void lookAt(Vec3d target) {
        Vec3d eye = mc.player.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0d);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        OyVey.rotationManager.setPlayerRotations(yaw, pitch);
    }

    @Override
    public String getDisplayInfo() {
        return target == null ? null : target.getName().getString();
    }

    private record BlockSlot(int slot) { }
}
