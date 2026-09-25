package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

public class SelfTrap extends Module {
    private enum Mode { Partial, Full }

    private final Setting<Mode> trapMode = mode("Mode", Mode.Full);
    private final Setting<Boolean> head = bool("Head", true);
    private final Setting<Boolean> antiStep = bool("AntiStep", false);
    private final Setting<Boolean> antiBomb = bool("AntiBomb", false);
    private final Setting<Boolean> holeCheck = bool("Hole Check", false);
    private final Setting<Boolean> rotate = bool("Rotate", true);
    private final Setting<Integer> blocksPerTick = num("Blocks Per Tick", 4, 1, 8);

    public SelfTrap() {
        super("SelfTrap", "Places blocks around yourself to prevent movement and attacks.", Category.COMBAT, false, false, false);
    }

    @Override
    public void onUpdate() {
        if (nullCheck() || mc.interactionManager == null) return;
        if (holeCheck.getValue() && !isInHole(mc.player)) return;

        List<BlockPos> positions = getTrapPositions();
        if (positions.isEmpty()) return;

        int slot = findBlockSlot();
        if (slot == -1) return;

        int old = mc.player.getInventory().getSelectedSlot();
        if (old != slot) {
            mc.player.getInventory().setSelectedSlot(slot);
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(slot));
        }

        int placed = 0;
        for (BlockPos pos : positions) {
            if (placed >= blocksPerTick.getValue()) break;
            if (place(pos)) placed++;
        }

        if (old != slot) {
            mc.player.getInventory().setSelectedSlot(old);
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(old));
        }
    }

    private List<BlockPos> getTrapPositions() {
        List<BlockPos> out = new ArrayList<>();
        BlockPos feet = mc.player.getBlockPos();

        for (Direction d : Direction.Type.HORIZONTAL) {
            BlockPos side = feet.offset(d);
            if (trapMode.getValue() == Mode.Full || d.getAxis() != Direction.Axis.X) add(out, side.up(2));
            add(out, side.up(1));
        }

        if (head.getValue() && trapMode.getValue() == Mode.Full) add(out, feet.up(2));
        if (antiStep.getValue()) {
            for (Direction d : Direction.Type.HORIZONTAL) add(out, feet.offset(d).up(2));
        }
        if (antiBomb.getValue()) add(out, feet.up(3));

        return out;
    }

    private void add(List<BlockPos> list, BlockPos pos) {
        if (mc.world.getBlockState(pos).isReplaceable() && !list.contains(pos)) list.add(pos.toImmutable());
    }

    private boolean place(BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockPos support = pos.offset(d.getOpposite());
            if (mc.world.getBlockState(support).isReplaceable()) continue;

            Vec3d hit = Vec3d.ofCenter(support);
            HitResult ray = mc.world.raycast(new RaycastContext(
                    mc.player.getEyePos(), hit,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE, mc.player));

            if (ray.getType() != HitResult.Type.BLOCK || !ray.getBlockPos().equals(support)) continue;

            if (rotate.getValue()) {
                Vec3d delta = hit.subtract(mc.player.getEyePos());
                double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
                OyVey.rotationManager.setPlayerRotations(
                        (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0),
                        (float) -Math.toDegrees(Math.atan2(delta.y, horizontal)));
            }

            var result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND,
                    new BlockHitResult(hit, d, support, false));
            if (result.isAccepted()) {
                mc.player.swingHand(Hand.MAIN_HAND);
                return true;
            }
        }
        return false;
    }

    private int findBlockSlot() {
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.OBSIDIAN) || stack.isOf(Items.CRYING_OBSIDIAN)
                    || stack.isOf(Items.NETHERITE_BLOCK)) return i;
        }
        return -1;
    }

    private boolean isInHole(PlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        if (!mc.world.getBlockState(pos).isAir() || !mc.world.getBlockState(pos.up()).isAir()) return false;
        if (!strong(pos.down())) return false;
        for (Direction d : Direction.Type.HORIZONTAL) if (!strong(pos.offset(d))) return false;
        return true;
    }

    private boolean strong(BlockPos pos) {
        return mc.world.getBlockState(pos).getBlock().getBlastResistance() >= 600.0f;
    }
}