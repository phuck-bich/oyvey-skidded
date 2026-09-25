package me.alpha432.oyvey.features.modules.combat;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.event.impl.PacketEvent;
import me.alpha432.oyvey.event.impl.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.render.RenderUtil;
import me.alpha432.oyvey.util.models.Timer;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.RedstoneBlock;
import net.minecraft.block.RedstoneTorchBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class PistonCrystal extends Module {
    private enum BreakType { Swing, Packet }
    private enum PlaceMode { Torch, Block, Both }
    private enum SwitchMode { None, Normal, Silent, Swap, Pickup }

    private final Setting<BreakType> breakType = mode("Type", BreakType.Swing);
    private final Setting<PlaceMode> placeMode = mode("Place", PlaceMode.Torch);
    private final Setting<SwitchMode> switching = mode("Switch", SwitchMode.Silent);

    private final Setting<Double> enemyRange = num("Range", 4.9d, 0.0d, 6.0d);
    private final Setting<Integer> startDelay = num("Start Delay", 4, 0, 20);
    private final Setting<Integer> pistonDelay = num("Piston Delay", 2, 0, 20);
    private final Setting<Integer> crystalDelay = num("Crystal Delay", 2, 0, 20);
    private final Setting<Integer> hitDelay = num("Hit Delay", 2, 0, 20);
    private final Setting<Integer> maxYIncrease = num("Max Y", 3, 0, 5);
    private final Setting<Integer> stuckThreshold = num("Stuck Limit", 10, 5, 40);

    private final Setting<Boolean> rotate = bool("Rotate", true);
    private final Setting<Boolean> confirmBreak = bool("No Glitch Break", true);
    private final Setting<Boolean> confirmPlace = bool("No Glitch Place", true);
    private final Setting<Boolean> render = bool("Render", true);

    private final Timer timer = new Timer();
    private final Timer hitTimer = new Timer();

    private boolean noMaterials;
    private boolean isHole;
    private boolean enoughSpace;
    private boolean redstoneBlockMode;
    private boolean broken;

    private int stage;
    private int[] delayTable;
    private int stuckTicks;

    private BlockPos enemyPos;
    private Vec3d enemyVec;
    private Structure structure;
    private PlayerEntity target;

    public PistonCrystal() {
        super("PistonCrystal",
                "Mint-style piston crystal combat module.",
                Category.COMBAT,
                true,
                false,
                false);
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            disable();
            return;
        }

        delayTable = new int[]{
                startDelay.getValue(),
                0,
                pistonDelay.getValue(),
                crystalDelay.getValue(),
                hitDelay.getValue()
        };
        reset();
        findTarget();
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        structure = null;
        target = null;
        enemyPos = null;
        enemyVec = null;
        isHole = true;
        enoughSpace = false;
        broken = false;
        stage = 0;
        stuckTicks = 0;
        timer.reset();
        hitTimer.reset();
    }

    @Override
    public void onUpdate() {
        if (nullCheck() || mc.interactionManager == null || mc.world == null) return;

        if (delayTable == null) {
            delayTable = new int[]{
                    startDelay.getValue(),
                    0,
                    pistonDelay.getValue(),
                    crystalDelay.getValue(),
                    hitDelay.getValue()
            };
        }

        int currentDelay = delayTable[Math.min(stage, delayTable.length - 1)];
        if (!timer.passedMs(currentDelay * 50L)) return;

        if (target == null || !target.isAlive()
                || mc.player.squaredDistanceTo(target) > enemyRange.getValue() * enemyRange.getValue()) {
            if (!findTarget()) {
                disable();
                return;
            }
        }

        if (stage == 0) {
            playerChecks();

            if (noMaterials || !isHole || !enoughSpace) return;

            if (structure != null && !structure.positions().isEmpty()) {
                stage = 1;
                stuckTicks = 0;
                timer.reset();
            }
            return;
        }

        stuckTicks++;
        if (stuckTicks > stuckThreshold.getValue() * 20) {
            reset();
            return;
        }

        switch (stage) {
            case 1 -> {
                if (confirmBreak.getValue()
                        && (checkCrystalPlaced() || checkNearbyCrystal() != null)) {
                    stage = 4;
                    timer.reset();
                    return;
                }

                if (checkPistonPlaced()) {
                    stage = 2;
                    stuckTicks = 0;
                    timer.reset();
                    return;
                }

                placeStructureStep(1);
            }

            case 2 -> {
                if (checkCrystalPlaced()) {
                    stage = 3;
                    stuckTicks = 0;
                    timer.reset();
                    return;
                }

                placeStructureStep(0);
            }

            case 3 -> {
                if (checkRedstonePlaced()) {
                    stage = 4;
                    stuckTicks = 0;
                    timer.reset();
                    return;
                }

                placeStructureStep(2);
            }

            case 4 -> {
                destroyCrystal();
                if (broken) {
                    stage = 0;
                    structure = null;
                    timer.reset();
                }
            }

            default -> reset();
        }
    }

    @Subscribe
    public void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;

        if (event.getPacket() instanceof PlaySoundS2CPacket packet
                && packet.getCategory() == SoundCategory.BLOCKS
                && packet.getSound() == SoundEvents.ENTITY_GENERIC_EXPLODE
                && enemyPos != null
                && (int) packet.getX() == enemyPos.getX()
                && (int) packet.getZ() == enemyPos.getZ()) {
            stage = 0;
            structure = null;
            timer.reset();
        }
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (!render.getValue() || structure == null || enemyVec == null) return;

        Color fill = new Color(255, 0, 255, 30);
        Color outline = new Color(255, 0, 255, 180);

        for (Vec3d relative : structure.positions()) {
            BlockPos pos = new BlockPos(
                    (int) Math.floor(enemyVec.x + relative.x),
                    (int) Math.floor(enemyVec.y + relative.y),
                    (int) Math.floor(enemyVec.z + relative.z)
            );

            RenderUtil.drawBoxFilled(event.getMatrix(), new Box(pos), fill);
            RenderUtil.drawBox(event.getMatrix(), new Box(pos), outline, 1.0d);
        }
    }

    private boolean findTarget() {
        PlayerEntity best = null;
        double bestDistance = enemyRange.getValue() * enemyRange.getValue();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive()) continue;
            if (OyVey.friendManager.isFriend(player)) continue;

            double distance = mc.player.squaredDistanceTo(player);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }

        target = best;
        return target != null;
    }

    private void playerChecks() {
        noMaterials = !hasMaterials();

        if (target == null) {
            isHole = false;
            enoughSpace = false;
            return;
        }

        isHole = isHole(target.getBlockPos());
        if (!isHole) {
            enoughSpace = false;
            return;
        }

        enemyPos = target.getBlockPos();
        enemyVec = target.getPos();
        enoughSpace = createStructure();
    }

    private boolean isHole(BlockPos pos) {
        if (!mc.world.getBlockState(pos.down()).isSolidBlock(mc.world, pos.down())) return false;

        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos side = pos.offset(direction);
            if (mc.world.getBlockState(side).isReplaceable()) return false;
        }

        return true;
    }

    private boolean createStructure() {
        if (enemyPos == null) return false;

        int maxY = Math.min(maxYIncrease.getValue(), 5);

        for (Direction facing : Direction.Type.HORIZONTAL) {
            for (int y = 1; y <= maxY; y++) {
                BlockPos crystalPos = enemyPos.offset(facing).up(y);
                BlockPos pistonPos = crystalPos.offset(facing);
                BlockPos redstonePos = pistonPos.offset(facing);

                if (!mc.world.getBlockState(crystalPos).isReplaceable()) continue;
                if (!mc.world.getBlockState(pistonPos).isReplaceable()) continue;
                if (!mc.world.getBlockState(redstonePos).isReplaceable()) continue;

                List<Vec3d> positions = new ArrayList<>();
                positions.add(new Vec3d(facing.getOffsetX(), y, facing.getOffsetZ()));
                positions.add(new Vec3d(facing.getOffsetX() * 2, y, facing.getOffsetZ() * 2));
                positions.add(new Vec3d(facing.getOffsetX() * 3, y, facing.getOffsetZ() * 3));

                structure = new Structure(facing, positions);
                return true;
            }
        }

        structure = null;
        return false;
    }

    private void placeStructureStep(int step) {
        if (structure == null || step < 0 || step >= structure.positions().size()) return;

        BlockPos pos = structurePos(step);
        Item item = itemForStep(step);
        if (item == Items.AIR) return;

        int slot = findHotbar(item);
        if (slot == -1) return;

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        switchTo(slot, switching.getValue());

        BlockPos support = pos.down();
        if (step == 1) {
            support = pos.down();
        }

        BlockHitResult hit = new BlockHitResult(
                Vec3d.ofCenter(support),
                Direction.UP,
                support,
                false
        );

        if (rotate.getValue()) {
            lookAt(pos.toCenterPos());
        }

        if (step == 1) {
            spoofPistonFacing(structure.facing().getOpposite());
        }

        var result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);

        if (result.isAccepted()) {
            if (confirmPlace.getValue()) {
                timer.reset();
            } else {
                timer.reset();
            }

            mc.player.swingHand(Hand.MAIN_HAND);
        }

        restoreSlot(oldSlot);
    }

    private void spoofPistonFacing(Direction facing) {
        float yaw = switch (facing) {
            case SOUTH -> 0.0f;
            case WEST -> 90.0f;
            case NORTH -> 180.0f;
            case EAST -> -90.0f;
            default -> mc.player.getYaw();
        };

        OyVey.rotationManager.setPlayerRotations(yaw, 0.0f);
    }

    private Item itemForStep(int step) {
        return switch (step) {
            case 0 -> Items.END_CRYSTAL;
            case 1 -> findHotbar(Items.PISTON) != -1 ? Items.PISTON
                    : (findHotbar(Items.STICKY_PISTON) != -1 ? Items.STICKY_PISTON : Items.AIR);
            case 2 -> {
                boolean blockAvailable = findHotbar(Items.REDSTONE_BLOCK) != -1;
                redstoneBlockMode = switch (placeMode.getValue()) {
                    case Block -> blockAvailable;
                    case Both -> blockAvailable;
                    case Torch -> false;
                };

                if (redstoneBlockMode) yield Items.REDSTONE_BLOCK;
                yield findHotbar(Items.REDSTONE_TORCH) != -1 ? Items.REDSTONE_TORCH : Items.AIR;
            }
            default -> Items.AIR;
        };
    }

    private boolean checkPistonPlaced() {
        if (structure == null) return false;
        Block block = mc.world.getBlockState(structurePos(1)).getBlock();
        return block instanceof PistonBlock;
    }

    private boolean checkRedstonePlaced() {
        if (structure == null) return false;

        Block block = mc.world.getBlockState(structurePos(2)).getBlock();
        return block instanceof RedstoneBlock || block instanceof RedstoneTorchBlock;
    }

    private boolean checkCrystalPlaced() {
        if (structure == null) return false;

        BlockPos pos = structurePos(0);
        return !mc.world.getEntitiesByClass(
                EndCrystalEntity.class,
                new Box(pos).expand(0.15d),
                Entity::isAlive
        ).isEmpty();
    }

    private EndCrystalEntity checkNearbyCrystal() {
        if (structure == null) return null;

        BlockPos crystalPos = structurePos(0);

        for (EndCrystalEntity crystal : mc.world.getEntitiesByClass(
                EndCrystalEntity.class,
                new Box(crystalPos).expand(1.25d),
                Entity::isAlive
        )) {
            return crystal;
        }

        return null;
    }

    private void destroyCrystal() {
        EndCrystalEntity crystal = checkNearbyCrystal();
        if (crystal == null || !hitTimer.passedMs(hitDelay.getValue() * 50L)) {
            broken = false;
            return;
        }

        hitTimer.reset();

        if (rotate.getValue()) {
            lookAt(crystal.getPos());
        }

        if (breakType.getValue() == BreakType.Packet) {
            mc.player.networkHandler.sendPacket(
                    PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking())
            );
        } else {
            mc.interactionManager.attackEntity(mc.player, crystal);
        }

        mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        broken = true;
    }

    private boolean hasMaterials() {
        boolean piston = findHotbar(Items.PISTON) != -1 || findHotbar(Items.STICKY_PISTON) != -1;
        boolean crystal = findHotbar(Items.END_CRYSTAL) != -1;

        boolean block = findHotbar(Items.REDSTONE_BLOCK) != -1;
        boolean torch = findHotbar(Items.REDSTONE_TORCH) != -1;

        return piston && crystal && switch (placeMode.getValue()) {
            case Block -> block;
            case Torch -> torch;
            case Both -> block || torch;
        };
    }

    private int findHotbar(Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(item)) return i;
        }

        return -1;
    }

    private void switchTo(int slot, SwitchMode mode) {
        if (slot < 0 || slot > 8) return;

        switch (mode) {
            case Normal -> {
                mc.player.getInventory().setSelectedSlot(slot);
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }

            case Silent, Swap, Pickup -> {
                mc.player.getInventory().setSelectedSlot(slot);
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            }

            case None -> {
                // Do not switch. Placement will fail safely if the requested item
                // isn't already held.
            }
        }
    }

    private void restoreSlot(int oldSlot) {
        if (switching.getValue() == SwitchMode.Normal
                || switching.getValue() == SwitchMode.Silent
                || switching.getValue() == SwitchMode.Swap
                || switching.getValue() == SwitchMode.Pickup) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(oldSlot));
        }
    }

    private BlockPos structurePos(int step) {
        Vec3d relative = structure.positions().get(step);
        return new BlockPos(
                (int) Math.floor(enemyVec.x + relative.x),
                (int) Math.floor(enemyVec.y + relative.y),
                (int) Math.floor(enemyVec.z + relative.z)
        );
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
        if (target == null) return null;
        return target.getName().getString();
    }

    private record Structure(Direction facing, List<Vec3d> positions) {
    }
}
