package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.models.Timer;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.awt.Color;
import java.util.List;

public class Surround extends Module {
    private enum Center {
        Never, OnActivate, Incomplete, Always
    }

    private enum BlockType {
        Safe, Normal, Unsafe
    }

    private final Setting<Center> center = mode("Center", Center.Incomplete);
    private final Setting<Integer> delay = num("Delay", 0, 0, 20);
    private final Setting<Integer> blocksPerTick = num("Blocks Per Tick", 1, 1, 6);
    private final Setting<Boolean> doubleHeight = bool("Double Height", false);
    private final Setting<Boolean> onlyOnGround = bool("Only On Ground", true);
    private final Setting<Boolean> airPlace = bool("Air Place", true);
    private final Setting<Boolean> rotate = bool("Rotate", true);
    private final Setting<Boolean> protect = bool("Protect", true);

    private final Setting<Boolean> toggleOnYChange = bool("Toggle On Y Change", true);
    private final Setting<Boolean> toggleOnComplete = bool("Toggle On Complete", false);
    private final Setting<Boolean> toggleOnDeath = bool("Toggle On Death", true);

    private final Setting<Boolean> swing = bool("Swing", true);
    private final Setting<Boolean> render = bool("Render", true);
    private final Setting<Boolean> renderBelow = bool("Render Below", false);
    private final Setting<Boolean> filled = bool("Filled", true);
    private final Setting<Boolean> outlined = bool("Outlined", true);

    private final Setting<Boolean> useObsidian = bool("Obsidian", true);
    private final Setting<Boolean> useCryingObsidian = bool("Crying Obsidian", true);
    private final Setting<Boolean> useNetherite = bool("Netherite", true);

    private final Timer delayTimer = new Timer();
    private int startY = Integer.MIN_VALUE;
    private boolean died;

    public Surround() {
        super("Surround",
                "Meteor-style surround using vanilla Fabric placement and rendering.",
                Category.COMBAT,
                false,
                false,
                false);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) return;

        startY = mc.player.getBlockY();
        died = false;
        delayTimer.reset();

        if (center.getValue() == Center.OnActivate) centerPlayer();
    }

    @Override
    public void onDisable() {
        startY = Integer.MIN_VALUE;
    }

    @Override
    public void onUpdate() {
        if (nullCheck() || mc.world == null || mc.interactionManager == null) return;

        if (toggleOnDeath.getValue() && (mc.player.isDead() || mc.player.getHealth() <= 0.0f)) {
            if (!died) {
                died = true;
                disable();
            }
            return;
        }

        if (toggleOnYChange.getValue() && startY != Integer.MIN_VALUE
                && mc.player.getBlockY() != startY) {
            disable();
            return;
        }

        if (onlyOnGround.getValue() && !mc.player.isOnGround()) return;

        if (center.getValue() == Center.Always) centerPlayer();

        if (!delayTimer.passedMs(delay.getValue() * 50L)) return;

        int slot = findBlockSlot();
        if (slot == -1) return;

        int previous = mc.player.getInventory().getSelectedSlot();
        if (!mc.player.getMainHandStack().isOf(Items.OBSIDIAN)
                && !mc.player.getMainHandStack().isOf(Items.CRYING_OBSIDIAN)
                && !mc.player.getMainHandStack().isOf(Items.NETHERITE_BLOCK)) {
            mc.player.getInventory().setSelectedSlot(slot);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }

        BlockPos playerPos = mc.player.getBlockPos();
        int placed = 0;
        boolean complete = true;

        for (Direction direction : Direction.values()) {
            if (!direction.getAxis().isHorizontal()) continue;

            BlockPos pos = playerPos.offset(direction);

            if (mc.world.getBlockState(pos).isReplaceable()) {
                complete = false;

                if (place(pos)) {
                    placed++;
                    if (placed >= blocksPerTick.getValue()) break;
                } else if (protect.getValue()) {
                    protectPosition(pos);
                }
            }
        }

        if (placed < blocksPerTick.getValue() && doubleHeight.getValue() && complete) {
            for (Direction direction : Direction.values()) {
                if (!direction.getAxis().isHorizontal()) continue;

                BlockPos pos = playerPos.offset(direction).up();
                if (!mc.world.getBlockState(pos).isReplaceable()) continue;

                complete = false;
                if (place(pos)) {
                    placed++;
                    if (placed >= blocksPerTick.getValue()) break;
                } else if (protect.getValue()) {
                    protectPosition(pos);
                }
            }
        }

        if (placed > 0) delayTimer.reset();

        if (center.getValue() == Center.Incomplete && !complete) centerPlayer();

        if (complete && doubleHeight.getValue()) {
            complete = isComplete(playerPos, true);
        } else if (complete) {
            complete = isComplete(playerPos, false);
        }

        if (complete && toggleOnComplete.getValue()) disable();

        if (previous != mc.player.getInventory().getSelectedSlot()) {
            mc.player.getInventory().setSelectedSlot(previous);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previous));
        }
    }

    private boolean place(BlockPos pos) {
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        if (intersectsBlockingEntity(new Box(pos))) return false;

        BlockHitResult hit = findPlacementHit(pos);

        if (hit == null) {
            if (!airPlace.getValue()) return false;

            // Air-place fallback. This mirrors Meteor's intent: attempt placement even
            // when a conventional neighboring support face cannot be found.
            hit = new BlockHitResult(
                    Vec3d.ofCenter(pos),
                    Direction.DOWN,
                    pos,
                    false
            );
        }

        if (rotate.getValue()) lookAt(hit.getPos());

        Hand hand = Hand.MAIN_HAND;
        if (!isAllowedBlock(mc.player.getMainHandStack())) return false;

        var result = mc.interactionManager.interactBlock(mc.player, hand, hit);
        if (result.isAccepted() && swing.getValue()) {
            mc.player.swingHand(hand);
        }

        return result.isAccepted();
    }

    private BlockHitResult findPlacementHit(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();

        for (Direction direction : Direction.values()) {
            BlockPos support = pos.offset(direction.getOpposite());
            if (mc.world.getBlockState(support).isReplaceable()) continue;

            Vec3d hitPos = Vec3d.ofCenter(support).add(
                    direction.getOffsetX() * 0.5d,
                    direction.getOffsetY() * 0.5d,
                    direction.getOffsetZ() * 0.5d
            );

            BlockHitResult ray = mc.world.raycast(new RaycastContext(
                    eye,
                    hitPos,
                    RaycastContext.ShapeType.COLLIDER,
                    RaycastContext.FluidHandling.NONE,
                    mc.player
            ));

            if (ray.getType() == HitResult.Type.BLOCK
                    && ray.getBlockPos().equals(support)) {
                return new BlockHitResult(hitPos, direction, support, false);
            }
        }

        return null;
    }

    private void protectPosition(BlockPos pos) {
        Box box = new Box(
                pos.getX() - 1.0d, pos.getY() - 1.0d, pos.getZ() - 1.0d,
                pos.getX() + 2.0d, pos.getY() + 2.0d, pos.getZ() + 2.0d
        );

        for (Entity entity : mc.world.getOtherEntities(mc.player, box,
                e -> e instanceof EndCrystalEntity && e.isAlive())) {
            EndCrystalEntity crystal = (EndCrystalEntity) entity;

            if (crystal.squaredDistanceTo(mc.player) > 36.0d) continue;

            if (rotate.getValue()) lookAt(crystal.getPos().add(0.0d, 0.5d, 0.0d));

            mc.interactionManager.attackEntity(mc.player, crystal);
            if (swing.getValue()) mc.player.swingHand(Hand.MAIN_HAND);
        }
    }

    private boolean isComplete(BlockPos playerPos, boolean height) {
        for (Direction direction : Direction.values()) {
            if (!direction.getAxis().isHorizontal()) continue;

            BlockPos pos = playerPos.offset(direction);
            if (mc.world.getBlockState(pos).isReplaceable()) return false;

            if (height && mc.world.getBlockState(pos.up()).isReplaceable()) return false;
        }

        return true;
    }

    private boolean intersectsBlockingEntity(Box box) {
        List<Entity> entities = mc.world.getOtherEntities(null, box,
                entity -> entity.isAlive() && !(entity instanceof EndCrystalEntity));
        return !entities.isEmpty();
    }

    private int findBlockSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isAllowedBlock(stack)) return i;
        }
        return -1;
    }

    private boolean isAllowedBlock(ItemStack stack) {
        if (useObsidian.getValue() && stack.isOf(Items.OBSIDIAN)) return true;
        if (useCryingObsidian.getValue() && stack.isOf(Items.CRYING_OBSIDIAN)) return true;
        return useNetherite.getValue() && stack.isOf(Items.NETHERITE_BLOCK);
    }

    private void centerPlayer() {
        double x = Math.floor(mc.player.getX()) + 0.5d;
        double z = Math.floor(mc.player.getZ()) + 0.5d;

        mc.player.setPosition(x, mc.player.getY(), z);
        mc.player.setVelocity(mc.player.getVelocity().x, mc.player.getVelocity().y, mc.player.getVelocity().z);
    }

    private void lookAt(Vec3d target) {
        Vec3d eye = mc.player.getEyePos();

        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0d);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horizontal)));

        OyVey.rotationManager.setPlayerRotations(yaw, pitch);
    }

    @Override
    public void onRender3D(me.alpha432.oyvey.event.impl.Render3DEvent event) {
        if (!render.getValue() || mc.player == null || mc.world == null) return;

        BlockPos playerPos = mc.player.getBlockPos();

        if (renderBelow.getValue()) {
            draw(playerPos.down(), event, BlockType.Normal);
        }

        for (Direction direction : Direction.values()) {
            if (!direction.getAxis().isHorizontal()) continue;

            BlockPos pos = playerPos.offset(direction);
            draw(pos, event, getBlockType(pos));

            if (doubleHeight.getValue()) {
                draw(pos.up(), event, getBlockType(pos.up()));
            }
        }
    }

    private void draw(BlockPos pos, me.alpha432.oyvey.event.impl.Render3DEvent event, BlockType type) {
        Color side = switch (type) {
            case Safe -> new Color(13, 255, 0, 0);
            case Normal -> new Color(0, 255, 238, 12);
            case Unsafe -> new Color(204, 0, 0, 12);
        };

        Color line = switch (type) {
            case Safe -> new Color(13, 255, 0, 0);
            case Normal -> new Color(0, 255, 238, 100);
            case Unsafe -> new Color(204, 0, 0, 100);
        };

        if (filled.getValue()) RenderUtil.drawBoxFilled(event.getMatrix(), new Box(pos), side);
        if (outlined.getValue()) RenderUtil.drawBox(event.getMatrix(), new Box(pos), line, 1.0d);
    }

    private BlockType getBlockType(BlockPos pos) {
        var state = mc.world.getBlockState(pos);
        Block block = state.getBlock();

        if (block == Blocks.BEDROCK || block == Blocks.REINFORCED_DEEPSLATE) return BlockType.Safe;
        if (state.getBlock().getBlastResistance() >= 600.0f) return BlockType.Normal;
        return BlockType.Unsafe;
    }

    @Override
    public String getDisplayInfo() {
        if (mc.player == null || mc.world == null) return null;

        int complete = 0;
        BlockPos pos = mc.player.getBlockPos();

        for (Direction direction : Direction.values()) {
            if (!direction.getAxis().isHorizontal()) continue;
            if (!mc.world.getBlockState(pos.offset(direction)).isReplaceable()) complete++;
        }

        return complete + "/4";
    }
}
