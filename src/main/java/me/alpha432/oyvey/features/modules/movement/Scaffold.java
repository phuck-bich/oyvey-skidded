package me.alpha432.oyvey.features.modules.movement;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class Scaffold extends Module {
    private final Setting<Boolean> autoSwitch = bool("Auto Switch", true);
    private final Setting<Boolean> tower = bool("Tower", false);
    private final Setting<Boolean> rotate = bool("Rotate", false);

    public Scaffold() {
        super("Scaffold", "Places blocks beneath you while bridging.", Category.MOVEMENT, false, false, false);
    }

    @Override
    public void onUpdate() {
        if (nullCheck() || mc.interactionManager == null) return;

        ClientPlayerEntity player = mc.player;
        BlockPos target = BlockPos.ofFloored(player.getX(), player.getY() - 1.0, player.getZ());
        if (!mc.world.getBlockState(target).isReplaceable()) return;

        int previousSlot = player.getInventory().getSelectedSlot();
        if (!(player.getMainHandStack().getItem() instanceof BlockItem) && autoSwitch.getValue()) {
            int slot = findBlockSlot(player);
            if (slot < 0) return;
            player.getInventory().setSelectedSlot(slot);
            player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }

        if (!(player.getMainHandStack().getItem() instanceof BlockItem)) return;

        for (Direction direction : Direction.values()) {
            BlockPos support = target.offset(direction);
            BlockState supportState = mc.world.getBlockState(support);
            if (supportState.isReplaceable() || !supportState.getFluidState().isEmpty()) continue;

            Direction face = direction.getOpposite();
            Vec3d hitPos = Vec3d.ofCenter(support).add(
                    face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            BlockHitResult hit = new BlockHitResult(hitPos, face, support, false);
            var result = mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
            if (result.isAccepted()) {
                player.swingHand(Hand.MAIN_HAND);
                if (rotate.getValue()) player.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, hitPos);
                break;
            }
        }

        if (autoSwitch.getValue() && previousSlot != player.getInventory().getSelectedSlot()) {
            player.getInventory().setSelectedSlot(previousSlot);
            player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
        }

        if (tower.getValue() && player.isOnGround() && mc.options.jumpKey.isPressed()) {
            player.jump();
        }
    }

    private int findBlockSlot(ClientPlayerEntity player) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.getItem() instanceof BlockItem && stack.getCount() > 0) return slot;
        }
        return -1;
    }
}
