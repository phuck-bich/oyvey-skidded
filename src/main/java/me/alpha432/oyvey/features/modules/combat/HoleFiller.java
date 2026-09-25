package me.alpha432.oyvey.features.modules.combat;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.event.impl.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
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
import java.util.List;

public class HoleFiller extends Module {
    private final Setting<Double> range = num("Range", 4.5d, 0.0d, 6.0d);
    private final Setting<Double> wallsRange = num("Walls Range", 4.5d, 0.0d, 6.0d);
    private final Setting<Integer> searchRadius = num("Search Radius", 5, 0, 6);
    private final Setting<Integer> placeDelay = num("Place Delay", 1, 0, 20);
    private final Setting<Integer> blocksPerTick = num("Blocks Per Tick", 3, 1, 9);
    private final Setting<Boolean> doubles = bool("Doubles", true);
    private final Setting<Boolean> rotate = bool("Rotate", false);
    private final Setting<Boolean> smart = bool("Smart", true);
    private final Setting<Boolean> predictMovement = bool("Predict Movement", true);
    private final Setting<Double> ticksToPredict = num("Ticks To Predict", 10.0d, 1.0d, 30.0d);
    private final Setting<Boolean> ignoreSafe = bool("Ignore Safe", true);
    private final Setting<Boolean> onlyMoving = bool("Only Moving", true);
    private final Setting<Double> targetRange = num("Target Range", 7.0d, 0.0d, 10.0d);
    private final Setting<Double> feetRange = num("Feet Range", 1.5d, 0.0d, 4.0d);
    private final Setting<Boolean> render = bool("Render", true);
    private final Setting<Boolean> filled = bool("Filled", true);
    private final Setting<Boolean> outlined = bool("Outlined", true);

    private final List<PlayerEntity> targets = new ArrayList<>();
    private final List<Hole> holes = new ArrayList<>();
    private int timer;

    public HoleFiller() {
        super("HoleFiller", "Fills nearby holes with obsidian-class blocks.", Category.COMBAT, true, false, false);
    }

    @Override
    public void onEnable() {
        timer = 0;
        targets.clear();
        holes.clear();
    }

    @Override
    public void onDisable() {
        targets.clear();
        holes.clear();
    }

    @Override
    public void onUpdate() {
        if (nullCheck() || mc.world == null || mc.player == null || mc.interactionManager == null) return;

        timer--;
        if (smart.getValue()) updateTargets();
        else targets.clear();

        holes.clear();

        int slot = findBlockSlot();
        if (slot == -1) return;

        int radius = searchRadius.getValue();
        BlockPos origin = mc.player.getBlockPos();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -2; y <= 2; y++) {
                    if (timer > 0 || holes.size() >= 128) continue;

                    BlockPos pos = origin.add(x, y, z);
                    Hole hole = findHole(pos);
                    if (hole != null && validHoleTarget(hole.pos)) holes.add(hole);
                }
            }
        }

        if (timer > 0 || holes.isEmpty()) return;

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        if (oldSlot != slot) {
            mc.player.getInventory().setSelectedSlot(slot);
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(slot));
        }

        int placed = 0;
        for (Hole hole : holes) {
            if (placed >= blocksPerTick.getValue()) break;
            if (place(hole.pos)) placed++;
        }

        if (oldSlot != slot) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(oldSlot));
        }

        if (placed > 0) timer = placeDelay.getValue();
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!render.getValue() || holes.isEmpty()) return;

        for (int i = 0; i < holes.size(); i++) {
            Hole hole = holes.get(i);
            boolean next = i < blocksPerTick.getValue();
            Color fill = next ? new Color(227, 196, 245, 20) : new Color(197, 137, 232, 10);
            Color line = next ? new Color(5, 139, 221, 180) : new Color(197, 137, 232, 120);

            if (filled.getValue()) RenderUtil.drawBoxFilled(event.getMatrix(), new Box(hole.pos), fill);
            if (outlined.getValue()) RenderUtil.drawBox(event.getMatrix(), new Box(hole.pos), line, 1.0d);
        }
    }

    private void updateTargets() {
        targets.clear();

        double max = targetRange.getValue() * targetRange.getValue();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive()) continue;
            if (OyVey.friendManager.isFriend(player)) continue;
            if (player.isCreative()) continue;
            if (mc.player.squaredDistanceTo(player) > max) continue;
            if (onlyMoving.getValue() && !isMoving(player)) continue;
            if (ignoreSafe.getValue() && isSurrounded(player)) continue;
            targets.add(player);
        }
    }

    private boolean validHoleTarget(BlockPos pos) {
        if (!smart.getValue()) return true;

        for (PlayerEntity player : targets) {
            Vec3d targetPos = player.getPos();
            if (predictMovement.getValue()) {
                Vec3d velocity = player.getPos().subtract(player.prevX, player.prevY, player.prevZ);
                targetPos = targetPos.add(velocity.multiply(ticksToPredict.getValue()));
            }

            double dx = targetPos.x - (pos.getX() + 0.5d);
            double dy = targetPos.y - (pos.getY() + 1.0d);
            double dz = targetPos.z - (pos.getZ() + 0.5d);
            if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= feetRange.getValue()
                    && targetPos.y > pos.getY()) return true;
        }

        return false;
    }

    private boolean isMoving(PlayerEntity player) {
        return player.getX() != player.prevX || player.getY() != player.prevY || player.getZ() != player.prevZ;
    }

    private boolean isSurrounded(PlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        for (Direction direction : Direction.Type.HORIZONTAL) {
            if (mc.world.getBlockState(pos.offset(direction)).getBlock().getBlastResistance() < 600.0f) return false;
        }
        return true;
    }

    private Hole findHole(BlockPos pos) {
        if (!mc.world.getBlockState(pos).isAir()) return null;
        if (!mc.world.getBlockState(pos.up()).isAir()) return null;
        if (!hasStrongSurround(pos)) return null;
        if (!isInRange(pos)) return null;

        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos next = pos.offset(direction);
            if (mc.world.getBlockState(next).isAir()) {
                if (!doubles.getValue()) return null;
                if (!mc.world.getBlockState(next.up()).isAir()) return null;
                if (!hasStrongSurround(next)) return null;
            }
        }

        return new Hole(pos.toImmutable());
    }

    private boolean hasStrongSurround(BlockPos pos) {
        if (mc.world.getBlockState(pos.down()).getBlock().getBlastResistance() < 600.0f) return false;
        for (Direction direction : Direction.Type.HORIZONTAL) {
            if (mc.world.getBlockState(pos.offset(direction)).getBlock().getBlastResistance() < 600.0f) return false;
        }
        return true;
    }

    private boolean isInRange(BlockPos pos) {
        Vec3d point = Vec3d.ofBottomCenter(pos).add(0.0d, 0.999d, 0.0d);
        double distance = mc.player.getEyePos().squaredDistanceTo(point);
        if (distance > range.getValue() * range.getValue()) return false;

        Vec3d eye = mc.player.getEyePos();
        HitResult ray = mc.world.raycast(new RaycastContext(
                eye, point,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        return ray.getType() == HitResult.Type.BLOCK && ray.getBlockPos().equals(pos)
                || distance <= wallsRange.getValue() * wallsRange.getValue();
    }

    private boolean place(BlockPos pos) {
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;

        BlockHitResult hit = findPlacementHit(pos);
        if (hit == null) return false;

        if (rotate.getValue()) {
            Vec3d eye = mc.player.getEyePos();
            Vec3d delta = hit.getPos().subtract(eye);
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            OyVey.rotationManager.setPlayerRotations(
                    (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0d),
                    (float) -Math.toDegrees(Math.atan2(delta.y, horizontal))
            );
        }

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

            HitResult ray = mc.world.raycast(new RaycastContext(
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

    private int findBlockSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            Block block = Block.getBlockFromItem(stack.getItem());
            if (block == net.minecraft.block.Blocks.OBSIDIAN
                    || block == net.minecraft.block.Blocks.CRYING_OBSIDIAN
                    || block == net.minecraft.block.Blocks.NETHERITE_BLOCK
                    || block == net.minecraft.block.Blocks.RESPAWN_ANCHOR
                    || block == net.minecraft.block.Blocks.COBWEB) {
                return i;
            }
        }
        return -1;
    }

    private record Hole(BlockPos pos) { }
}
