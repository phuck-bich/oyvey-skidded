package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.Comparator;

public class AutoWeb extends Module {
    private enum Mode { Normal, Smart }
    private final Setting<Mode> mode = mode("Mode", Mode.Normal);
    private final Setting<Double> enemyRange = num("Enemy Range", 6.0d, 1.0d, 12.0d);
    private final Setting<Integer> extrapolation = num("Extrapolation", 0, 0, 10);
    private final Setting<Boolean> holeCheck = bool("Hole Check", false);
    private boolean wasInHole;

    public AutoWeb() { super("AutoWeb", "Automatically webs nearby enemies.", Category.COMBAT, false, false, false); }

    @Override public void onUpdate() {
        if (nullCheck() || mc.interactionManager == null) return;
        PlayerEntity target = findTarget();
        if (target == null) return;

        boolean inHole = isInHole(target);
        boolean tryingExit = isTryingToExit(target);
        if (mode.getValue() == Mode.Smart && !tryingExit && (inHole || wasInHole)) { wasInHole = inHole; return; }
        wasInHole = inHole;

        Vec3d predicted = target.getPos().add(target.getVelocity().multiply(extrapolation.getValue()));
        BlockPos pos = BlockPos.ofFloored(predicted);
        if (mode.getValue() == Mode.Smart && inHole) pos = pos.up();
        if (target.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)
                && mc.world.getBlockState(pos).isOf(Blocks.COBWEB)) pos = pos.up();

        if (!mc.world.getBlockState(pos).isReplaceable() || isEntityBlocking(pos, target)) return;

        int slot = findWeb();
        if (slot == -1) return;
        int old = mc.player.getInventory().getSelectedSlot();
        if (old != slot) {
            mc.player.getInventory().setSelectedSlot(slot);
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(slot));
        }

        place(pos);

        if (old != slot) {
            mc.player.getInventory().setSelectedSlot(old);
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(old));
        }
    }

    private PlayerEntity findTarget() {
        return mc.world.getPlayers().stream().filter(p -> p != mc.player && p.isAlive())
                .filter(p -> !OyVey.friendManager.isFriend(p))
                .filter(p -> mc.player.distanceTo(p) <= enemyRange.getValue())
                .filter(p -> !holeCheck.getValue() || isInHole(p))
                .min(Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(p))).orElse(null);
    }

    private boolean isInHole(PlayerEntity p) {
        BlockPos pos=p.getBlockPos();
        if (!mc.world.getBlockState(pos).isAir() || !mc.world.getBlockState(pos.up()).isAir()) return false;
        if (!mc.world.getBlockState(pos.down()).getBlock().getBlastResistance().equals(600.0f)
                && mc.world.getBlockState(pos.down()).getBlock().getBlastResistance() < 600.0f) return false;
        for (net.minecraft.util.math.Direction d : net.minecraft.util.math.Direction.Type.HORIZONTAL)
            if (mc.world.getBlockState(pos.offset(d)).getBlock().getBlastResistance() < 600.0f) return false;
        return true;
    }

    private boolean isTryingToExit(PlayerEntity p) {
        Vec3d v=p.getVelocity(); return v.y > .05 || v.x*v.x+v.z*v.z > .0125;
    }

    private boolean isEntityBlocking(BlockPos pos, PlayerEntity target) {
        return !mc.world.getOtherEntities(null,new Box(pos),e -> e != target && e.isAlive()).isEmpty();
    }

    private int findWeb() {
        for(int i=0;i<36;i++) if(mc.player.getInventory().getStack(i).isOf(Items.COBWEB)) return i;
        return -1;
    }

    private void place(BlockPos pos) {
        for (net.minecraft.util.math.Direction d : net.minecraft.util.math.Direction.values()) {
            BlockPos support=pos.offset(d.getOpposite());
            if(mc.world.getBlockState(support).isReplaceable()) continue;
            var hit=net.minecraft.util.math.Vec3d.ofCenter(support);
            var result=mc.interactionManager.interactBlock(mc.player, net.minecraft.util.Hand.MAIN_HAND,
                    new net.minecraft.util.hit.BlockHitResult(hit,d,support,false));
            if(result.isAccepted()) { mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND); return; }
        }
    }
}
