package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.modules.misc.PacketMine;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AutoMine extends Module {
    private enum Sequence { Surround, Phase, Dynamic }
    private enum BlockerSequence { None, Surround, Above, Smart }

    private final Setting<Double> range = num("Range", 5.0d, 1.0d, 6.0d);
    private final Setting<Sequence> sequence = mode("Sequence", Sequence.Dynamic);
    private final Setting<BlockerSequence> blocker = mode("Blocker", BlockerSequence.None);
    private final Setting<Boolean> face = bool("Face", false);
    private final Setting<Boolean> aboveHead = bool("Head", false);
    private final Setting<Boolean> antiCrawl = bool("AntiCrawl", true);
    private final Setting<Boolean> doubleMine = bool("DoubleMine", false);
    private final Setting<Boolean> cev = bool("Cev", false);
    private final Setting<Boolean> bedrock = bool("Bedrock", false);

    private Target target;
    private BlockPos position;

    private BlockPos cevFor;
    private long cevUntil;

    public AutoMine() {
        super("AutoMine",
                "Sydney-style automatic combat mining.",
                Category.COMBAT,
                false,
                false,
                false);
    }

    @Override
    public void onEnable() {
        target = null;
        position = null;
        resetCev();
    }

    @Override
    public void onDisable() {
        target = null;
        position = null;
        resetCev();
    }

    @Override
    public void onUpdate() {
        if (nullCheck() || mc.interactionManager == null || mc.world == null) return;
        if (mc.player.isCreative()
                || mc.interactionManager.getCurrentGameMode() == GameMode.CREATIVE
                || mc.interactionManager.getCurrentGameMode() == GameMode.SPECTATOR) return;

        PacketMine miner = OyVey.moduleManager.getModuleByClass(PacketMine.class);
        if (miner == null || !miner.isEnabled()) return;

        tickCevPost();

        if (antiCrawl.getValue() && mc.player.getPose() == EntityPose.SWIMMING) {
            BlockPos head = mc.player.getBlockPos().up();
            if (isValid(head) && !isOutOfRange(head)) {
                startMining(miner, head);
                return;
            }
        }

        target = findTarget();

        if (target == null) {
            position = null;
            resetCev();
            return;
        }

        if (packetMine.getValue() && miner != null && position != null && miner.isMining(position)) {
            tickCevWhileMining(position);
            if (!isOutOfRange(position)) {
                if (!mc.world.getBlockState(position).isAir()) return;
            }
        }

        if (antiCrawl.getValue() && target.player.getPose() == EntityPose.SWIMMING) {
            BlockPos anti = getAntiCrawlPos(target.player);
            if (anti != null && isValid(anti) && !isOutOfRange(anti)) {
                startMining(miner, anti);
                return;
            }
        }

        BlockPos best = findHighestPriorityBlock(target.player);
        if (best != null) {
            startMining(miner, best);
        } else {
            handleBlockerMining(miner, target.player);
        }
    }

    private void startMining(PacketMine miner, BlockPos pos) {
        if (pos == null || isInvalid(pos) || isOutOfRange(pos)) return;
        position = pos;
        miner.startMiningPos(pos, nullDirection(pos), doubleMine.getValue());
    }

    private BlockPos findHighestPriorityBlock(PlayerEntity player) {
        List<BlockPos> occupied = getOccupiedPositions(player);

        List<BlockPos> burrows = occupied.stream()
                .filter(p -> !mc.world.getBlockState(p).isOf(Blocks.COBWEB))
                .toList();
        BlockPos best = findBest(burrows);
        if (best != null) return best;

        BlockPos surround = findBest(new ArrayList<>(getFeetPositions(player)));
        if (surround != null) return surround;

        if (aboveHead.getValue()) {
            List<BlockPos> heads = occupied.stream()
                    .map(p -> p.up(2))
                    .filter(p -> !mc.world.getBlockState(p).isReplaceable())
                    .toList();
            best = findBest(heads);
            if (best != null) return best;
        }

        if (face.getValue()) {
            List<BlockPos> faces = getFeetPositions(player).stream()
                    .map(BlockPos::up)
                    .toList();
            best = findBest(faces);
            if (best != null) return best;
        }

        List<BlockPos> path = getTargetMinePath(player);
        return findBest(path);
    }

    private BlockPos findBest(List<BlockPos> blocks) {
        List<BlockPos> candidates = blocks.stream().distinct()
                .filter(p -> !isInvalid(p))
                .filter(p -> !isOutOfRange(p))
                .toList();

        if (candidates.isEmpty()) return null;

        return switch (sequence.getValue()) {
            case Surround -> candidates.stream()
                    .min(Comparator.comparingDouble(this::distanceTo))
                    .orElse(null);
            case Phase -> candidates.stream()
                    .min(Comparator.comparingDouble(this::phaseScore))
                    .orElse(null);
            case Dynamic -> candidates.stream()
                    .min(Comparator.comparingDouble(this::dynamicScore))
                    .orElse(null);
        };
    }

    private double distanceTo(BlockPos pos) {
        return mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos));
    }

    private double phaseScore(BlockPos pos) {
        double score = distanceTo(pos);
        if (target != null && pos.getY() == target.player.getBlockY()) score -= 2.0d;
        if (target != null && pos.getY() > target.player.getBlockY()) score += 1.0d;
        return score;
    }

    private double dynamicScore(BlockPos pos) {
        double score = distanceTo(pos);
        if (target != null) {
            if (getFeetPositions(target.player).contains(pos)) score -= 2.5d;
            if (getOccupiedPositions(target.player).contains(pos)) score -= 4.0d;
            if (face.getValue() && getFeetPositions(target.player).contains(pos.down())) score -= 1.0d;
        }
        return score;
    }

    private void handleBlockerMining(PacketMine miner, PlayerEntity player) {
        if (blocker.getValue() == BlockerSequence.None) return;

        Set<BlockPos> feet = getFeetPositions(player);
        List<BlockPos> blockers = new ArrayList<>();

        if (blocker.getValue() == BlockerSequence.Surround
                || blocker.getValue() == BlockerSequence.Smart) {
            for (BlockPos feetPos : feet) {
                BlockPos above = feetPos.up();
                if (!isObbyOrBedrock(above)) continue;

                for (BlockPos other : feet) {
                    if (!other.equals(feetPos) && !isObbyOrBedrock(other.up())) {
                        blockers.add(other);
                        break;
                    }
                }
            }
        }

        if (blockers.isEmpty()
                && (blocker.getValue() == BlockerSequence.Above
                || blocker.getValue() == BlockerSequence.Smart)) {
            for (BlockPos feetPos : feet) {
                if (mc.world.getBlockState(feetPos).isReplaceable()) continue;
                BlockPos above = feetPos.up();
                if (isObbyOrBedrock(above) && !isOutOfRange(above)) {
                    blockers.add(above);
                    break;
                }
            }
        }

        BlockPos best = findBest(blockers);
        if (best != null) startMining(miner, best);
    }

    private List<BlockPos> getTargetMinePath(PlayerEntity player) {
        // OyVey does not currently have Mint's PlacePathfinding service.
        // Approximate the path candidates using occupied blocks and their immediate
        // horizontal neighbors instead of introducing a second pathfinding system.
        Set<BlockPos> result = new HashSet<>();
        for (BlockPos occupied : getOccupiedPositions(player)) {
            for (Direction direction : Direction.values()) {
                if (direction.getAxis().isHorizontal()) {
                    BlockPos pos = occupied.offset(direction);
                    if (!mc.world.getBlockState(pos).isReplaceable()) result.add(pos);
                }
            }
        }
        return new ArrayList<>(result);
    }

    private Target findTarget() {
        PlayerEntity best = null;
        double closest = Double.MAX_VALUE;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive()) continue;
            if (OyVey.friendManager.isFriend(player)) continue;

            double distance = mc.player.squaredDistanceTo(player);
            if (distance > MathHelper.square(range.getValue())) continue;

            if (distance < closest) {
                closest = distance;
                best = player;
            }
        }

        return best == null ? null : new Target(best);
    }

    private List<BlockPos> getOccupiedPositions(PlayerEntity player) {
        List<BlockPos> result = new ArrayList<>();
        Box box = player.getBoundingBox();

        for (int x = (int) Math.floor(box.minX); x < Math.ceil(box.maxX); x++) {
            for (int y = (int) Math.floor(box.minY); y < Math.ceil(box.maxY); y++) {
                for (int z = (int) Math.floor(box.minZ); z < Math.ceil(box.maxZ); z++) {
                    result.add(new BlockPos(x, y, z));
                }
            }
        }

        return result;
    }

    private Set<BlockPos> getFeetPositions(PlayerEntity player) {
        Set<BlockPos> positions = new HashSet<>();
        BlockPos feet = player.getBlockPos();

        for (Direction direction : Direction.Type.HORIZONTAL) {
            BlockPos pos = feet.offset(direction);
            if (!mc.world.getBlockState(pos).isReplaceable()) positions.add(pos);
        }

        return positions;
    }

    private BlockPos getAntiCrawlPos(PlayerEntity player) {
        for (BlockPos occupied : getOccupiedPositions(player)) {
            BlockPos below = occupied.down();

            if (isObbyOrBedrock(below)
                    && mc.world.getBlockState(below.up()).isReplaceable()) {
                return below;
            }

            for (Direction direction : Direction.Type.HORIZONTAL) {
                BlockPos side = below.offset(direction);
                if (isObbyOrBedrock(side)
                        && mc.world.getBlockState(side.up()).isReplaceable()) {
                    return side;
                }
            }
        }

        return null;
    }

    private boolean isObbyOrBedrock(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (state.isOf(Blocks.BEDROCK)) return bedrock.getValue();
        return state.isOf(Blocks.OBSIDIAN);
    }

    private boolean isValid(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (state.isOf(Blocks.BEDROCK)) return bedrock.getValue();
        return state.isReplaceable() || state.getHardness(mc.world, pos) >= 0.0f;
    }

    private boolean isInvalid(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (state.isOf(Blocks.BEDROCK)) return !bedrock.getValue();
        return state.isReplaceable() || state.getHardness(mc.world, pos) < 0.0f;
    }

    private boolean isOutOfRange(BlockPos pos) {
        return mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos))
                > MathHelper.square(range.getValue());
    }

    private void tickCevWhileMining(BlockPos miningPos) {
        if (!cev.getValue()) return;

        if (!packetMine.getValue()) return;

        CrystalAura ca = OyVey.moduleManager.getModuleByClass(CrystalAura.class);
        if (ca == null || !ca.isEnabled()) return;

        BlockState state = mc.world.getBlockState(miningPos);
        if (!state.isOf(Blocks.OBSIDIAN)) return;

        // Place the crystal immediately before the block finishes.
        // This mirrors Sydney's CEV timing while using vanilla interaction calls.
        PacketMine miner = OyVey.moduleManager.getModuleByClass(PacketMine.class);
        if (miner == null) return;

        BlockPos crystalPos = miningPos.up();
        if (!mc.world.getBlockState(crystalPos).isAir()) return;

        for (EndCrystalEntity crystal : mc.world.getEntitiesByClass(
                EndCrystalEntity.class, new Box(crystalPos).expand(1.0d), e -> true)) {
            return;
        }

        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.END_CRYSTAL)) {
                slot = i;
                break;
            }
        }
        if (slot == -1) return;

        int previous = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);

        mc.interactionManager.interactBlock(
                mc.player,
                Hand.MAIN_HAND,
                new net.minecraft.util.hit.BlockHitResult(
                        Vec3d.ofCenter(miningPos).add(0.0d, 0.5d, 0.0d),
                        Direction.UP,
                        miningPos,
                        false
                )
        );

        mc.player.swingHand(Hand.MAIN_HAND);
        mc.player.getInventory().setSelectedSlot(previous);

        cevFor = miningPos;
        cevUntil = System.currentTimeMillis() + 1200L;
    }

    private void tickCevPost() {
        if (!cev.getValue() || cevFor == null) return;
        if (System.currentTimeMillis() > cevUntil) {
            resetCev();
            return;
        }

        if (!mc.world.getBlockState(cevFor).isAir()) return;

        BlockPos crystalPos = cevFor.up();
        for (EndCrystalEntity crystal : mc.world.getEntitiesByClass(
                EndCrystalEntity.class, new Box(crystalPos).expand(1.0d), e -> true)) {
            mc.interactionManager.attackEntity(mc.player, crystal);
            mc.player.swingHand(Hand.MAIN_HAND);
            resetCev();
            return;
        }
    }

    private void resetCev() {
        cevFor = null;
        cevUntil = 0L;
    }

    @Override
    public String getDisplayInfo() {
        if (target == null) return sequence.getValue().name();
        return target.player.getName().getString() + ", " + sequence.getValue().name();
    }

    private record Target(PlayerEntity player) {}
}
