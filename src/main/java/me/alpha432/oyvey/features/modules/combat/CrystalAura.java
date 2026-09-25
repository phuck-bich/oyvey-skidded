package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.util.models.Timer;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CrystalAura for OyVey.
 *
 * Architecture:
 * - Mint-inspired calculation/execution flow: target snapshot -> candidate scan ->
 *   async best-position selection -> break/place execution with independent timers.
 * - Meteor-inspired rendering: Normal, Smooth, Fading and Gradient placement indicators.
 *
 * This implementation intentionally uses normal Minecraft crystal attack packets and
 * does not use predicted entity IDs, packet mutation, or other server-validation bypasses.
 */
public class CrystalAura extends Module {
    private enum RenderMode {
        Normal,
        Smooth,
        Fading,
        Gradient,
        None
    }

    private enum SwitchMode {
        None,
        Normal
    }

    private final Setting<Double> targetRange = num("Target Range", 10.0d, 1.0d, 16.0d);
    private final Setting<Double> placeRange = num("Place Range", 4.5d, 1.0d, 6.0d);
    private final Setting<Double> wallRange = num("Wall Range", 3.5d, 0.0d, 6.0d);
    private final Setting<Double> minDamage = num("Min Damage", 4.0d, 0.0d, 36.0d);
    private final Setting<Double> maxSelfDamage = num("Max Self", 8.0d, 0.0d, 36.0d);
    private final Setting<Boolean> antiSuicide = bool("Anti Suicide", true);
    private final Setting<Boolean> smart = bool("Smart", true);
    private final Setting<Boolean> rotate = bool("Rotate", true);
    private final Setting<Boolean> predictMovement = bool("Predict Movement", true);
    private final Setting<Boolean> sequential = bool("Sequential", true);
    private final Setting<Boolean> useThread = bool("Use Thread", true);
    private final Setting<Boolean> instantCalc = bool("Instant Calc", true);
    private final Setting<Boolean> fastBreak = bool("Fast Break", true);
    private final Setting<Boolean> place = bool("Place", true);
    private final Setting<Boolean> breakCrystals = bool("Break", true);
    private final Setting<Boolean> facePlace = bool("Face Place", true);
    private final Setting<Double> facePlaceHealth = num("Face Place Health", 8.0d, 0.0d, 36.0d);
    private final Setting<Integer> placeDelay = num("Place Delay", 0, 0, 20);
    private final Setting<Integer> breakDelay = num("Break Delay", 0, 0, 20);
    private final Setting<Integer> switchDelay = num("Switch Delay", 0, 0, 10);
    private final Setting<SwitchMode> switchMode = mode("Switch", SwitchMode.Normal);
    private final Setting<RenderMode> renderMode = mode("Render Mode", RenderMode.Normal);
    private final Setting<Boolean> renderPlace = bool("Render Place", true);
    private final Setting<Boolean> renderBreak = bool("Render Break", false);
    private final Setting<Integer> renderTime = num("Render Time", 10, 0, 40);
    private final Setting<Integer> smoothness = num("Smoothness", 8, 1, 30);
    private final Setting<Double> gradientHeight = num("Gradient Height", 0.7d, 0.1d, 2.0d);
    private final Setting<Boolean> renderDamage = bool("Render Damage", true);
    private final Setting<Boolean> filled = bool("Filled", true);
    private final Setting<Boolean> outlined = bool("Outlined", true);

    private final Timer placeTimer = new Timer();
    private final Timer breakTimer = new Timer();
    private final Timer switchTimer = new Timer();

    private final ExecutorService calculationExecutor = Executors.newSingleThreadExecutor(new DaemonThreadFactory());
    private Future<?> calculationTask;

    private volatile CalculationResult calculated;
    private CalculationResult activeResult;

    private BlockPos renderPlacePos;
    private BlockPos renderBreakPos;
    private Box smoothPlaceBox;
    private Box smoothBreakBox;
    private double renderDamage;
    private long placeRenderUntil;
    private long breakRenderUntil;

    private long lastCalculationNanos;
    private long calculations;
    private long lastSnapshotNanos;

    public CrystalAura() {
        super("CrystalAura",
                "Fast crystal aura using threaded candidate selection and Meteor-style rendering.",
                Category.COMBAT,
                false,
                false,
                false);
    }

    @Override
    public void onEnable() {
        calculated = null;
        activeResult = null;
        renderPlacePos = null;
        renderBreakPos = null;
        smoothPlaceBox = null;
        smoothBreakBox = null;
        renderDamage = 0.0d;
        placeRenderUntil = 0L;
        breakRenderUntil = 0L;
        placeTimer.reset();
        breakTimer.reset();
        switchTimer.reset();
    }

    @Override
    public void onDisable() {
        if (calculationTask != null) {
            calculationTask.cancel(true);
            calculationTask = null;
        }
        calculated = null;
        activeResult = null;
        renderPlacePos = null;
        renderBreakPos = null;
        smoothPlaceBox = null;
        smoothBreakBox = null;
    }

    @Override
    public void onUpdate() {
        if (nullCheck() || mc.world == null || mc.interactionManager == null) return;

        if (calculated != null) {
            activeResult = calculated;
            calculated = null;
        }

        if (breakCrystals.getValue()) {
            doBreak();
        }

        if (place.getValue() && (!sequential.getValue() || activeResult == null)) {
            doPlace();
        }

        scheduleCalculation();
    }

    private void scheduleCalculation() {
        if (useThread.getValue() && calculationTask != null && !calculationTask.isDone()) return;

        final Snapshot snapshot = createSnapshot();
        if (snapshot == null) {
            activeResult = null;
            return;
        }

        if (useThread.getValue()) {
            calculationTask = calculationExecutor.submit(() -> {
                CalculationResult result = calculate(snapshot);
                calculated = result;
            });
        } else {
            CalculationResult result = calculate(snapshot);
            calculated = result;
        }
    }

    private Snapshot createSnapshot() {
        if (mc.player == null || mc.world == null) return null;
        lastSnapshotNanos = System.nanoTime();

        List<TargetSnapshot> targets = new ArrayList<>();
        double targetRangeSq = targetRange.getValue() * targetRange.getValue();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || player.isSpectator()) continue;
            if (player.squaredDistanceTo(mc.player) > targetRangeSq) continue;
            if (OyVey.friendManager != null && OyVey.friendManager.isFriend(player.getName().getString())) continue;

            double x = player.getX();
            double y = player.getY();
            double z = player.getZ();

            if (predictMovement.getValue()) {
                x += player.getVelocity().x;
                y += player.getVelocity().y;
                z += player.getVelocity().z;
            }

            targets.add(new TargetSnapshot(
                    player,
                    new Vec3d(x, y, z),
                    player.getHealth() + player.getAbsorptionAmount(),
                    player.getArmor(),
                    player.hurtTime
            ));
        }

        if (targets.isEmpty()) return null;

        List<Candidate> candidates = new ArrayList<>();
        int radius = (int) Math.ceil(placeRange.getValue() + 1.0d);
        BlockPos origin = mc.player.getBlockPos();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos base = origin.add(x, y, z);
                    if (!isCrystalBase(base)) continue;

                    BlockPos crystalPos = base.up();
                    if (!mc.world.getBlockState(crystalPos).isAir()) continue;
                    if (!mc.world.getBlockState(crystalPos.up()).isAir()) continue;

                    Box crystalBox = new Box(crystalPos);
                    if (intersectsEntities(crystalBox)) continue;

                    Vec3d center = Vec3d.ofCenter(crystalPos);
                    double distanceSq = mc.player.getEyePos().squaredDistanceTo(center);
                    double allowed = canSee(center) ? placeRange.getValue() : wallRange.getValue();
                    if (distanceSq > allowed * allowed) continue;

                    float selfDamage = crystalDamage(mc.player, center);
                    if (selfDamage > maxSelfDamage.getValue()) continue;
                    if (antiSuicide.getValue() && selfDamage >= mc.player.getHealth() + mc.player.getAbsorptionAmount()) continue;

                    double targetDamage = 0.0d;
                    TargetSnapshot bestTarget = null;

                    for (TargetSnapshot target : targets) {
                        float damage = crystalDamage(target.entity, center, target.position);
                        if (smart.getValue() && target.hurtTime > 0) continue;
                        if (damage > targetDamage) {
                            targetDamage = damage;
                            bestTarget = target;
                        }
                    }

                    if (bestTarget == null) continue;

                    double minimum = minDamage.getValue();
                    if (facePlace.getValue() && bestTarget.health <= facePlaceHealth.getValue()) {
                        minimum = Math.min(minimum, 1.5d);
                    }

                    if (targetDamage < minimum) continue;

                    candidates.add(new Candidate(
                            crystalPos.toImmutable(),
                            center,
                            targetDamage,
                            selfDamage,
                            bestTarget
                    ));
                }
            }
        }

        return new Snapshot(
                new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ()),
                targets,
                candidates
        );
    }

    private CalculationResult calculate(Snapshot snapshot) {
        long start = System.nanoTime();

        Candidate best = snapshot.candidates.stream()
                .max(Comparator
                        .comparingDouble((Candidate c) -> c.damage)
                        .thenComparingDouble(c -> -c.selfDamage)
                        .thenComparingDouble(c -> -snapshot.playerPos.squaredDistanceTo(c.center)))
                .orElse(null);

        if (best == null) {
            lastCalculationNanos = System.nanoTime() - start;
            return null;
        }

        calculations++;
        lastCalculationNanos = System.nanoTime() - start;
        return new CalculationResult(best);
    }

    private void doBreak() {
        EndCrystalEntity bestCrystal = null;
        double bestDamage = 0.0d;

        double breakRange = placeRange.getValue();
        Box search = mc.player.getBoundingBox().expand(breakRange);

        for (Entity entity : mc.world.getOtherEntities(mc.player, search, e -> e instanceof EndCrystalEntity)) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            if (!crystal.isAlive()) continue;

            Vec3d center = crystal.getPos().add(0.0d, 0.5d, 0.0d);
            double distanceSq = mc.player.getEyePos().squaredDistanceTo(center);
            double allowed = canSee(center) ? breakRange : wallRange.getValue();
            if (distanceSq > allowed * allowed) continue;

            float selfDamage = crystalDamage(mc.player, center);
            if (selfDamage > maxSelfDamage.getValue()) continue;
            if (antiSuicide.getValue() && selfDamage >= mc.player.getHealth() + mc.player.getAbsorptionAmount()) continue;

            double targetDamage = 0.0d;
            PlayerEntity target = null;

            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player || !player.isAlive() || player.isSpectator()) continue;
                if (player.squaredDistanceTo(mc.player) > targetRange.getValue() * targetRange.getValue()) continue;
                if (OyVey.friendManager != null && OyVey.friendManager.isFriend(player.getName().getString())) continue;

                float damage = crystalDamage(player, center);
                if (smart.getValue() && player.hurtTime > 0) continue;

                if (damage > targetDamage) {
                    targetDamage = damage;
                    target = player;
                }
            }

            if (target == null) continue;

            double minimum = minDamage.getValue();
            if (facePlace.getValue() && target.getHealth() + target.getAbsorptionAmount() <= facePlaceHealth.getValue()) {
                minimum = Math.min(minimum, 1.5d);
            }

            if (targetDamage < minimum) continue;

            if (targetDamage > bestDamage) {
                bestDamage = targetDamage;
                bestCrystal = crystal;
            }
        }

        if (bestCrystal == null) return;

        if (!fastBreak.getValue() && !breakTimer.passedMs(breakDelay.getValue() * 50L)) return;
        if (fastBreak.getValue() && !breakTimer.passedMs(Math.max(0L, breakDelay.getValue() * 25L))) return;

        if (rotate.getValue()) {
            lookAt(bestCrystal.getPos().add(0.0d, 0.5d, 0.0d));
        }

        mc.interactionManager.attackEntity(mc.player, bestCrystal);
        mc.player.swingHand(Hand.MAIN_HAND);

        renderBreakPos = bestCrystal.getBlockPos();
        breakRenderUntil = System.currentTimeMillis() + renderTime.getValue() * 50L;
        breakTimer.reset();

        if (sequential.getValue()) {
            activeResult = null;
        }
    }

    private void doPlace() {
        if (activeResult == null || activeResult.candidate == null) return;
        if (!placeTimer.passedMs(placeDelay.getValue() * 50L)) return;
        if (!switchTimer.passedMs(switchDelay.getValue() * 50L)) return;

        Candidate candidate = activeResult.candidate;
        BlockPos pos = candidate.pos;

        if (!isCrystalBase(pos.down())) return;
        if (!mc.world.getBlockState(pos).isAir() || !mc.world.getBlockState(pos.up()).isAir()) return;
        if (intersectsEntities(new Box(pos))) return;

        int slot = findCrystalSlot();
        if (slot == -1) return;

        Hand hand = getCrystalHand();
        int previous = mc.player.getInventory().getSelectedSlot();

        if (hand == null) {
            if (switchMode.getValue() == SwitchMode.None) return;

            mc.player.getInventory().setSelectedSlot(slot);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            switchTimer.reset();

            if (switchDelay.getValue() > 0) return;

            hand = Hand.MAIN_HAND;
        }

        BlockHitResult hit = getPlaceHit(pos);
        if (hit == null) return;

        if (rotate.getValue()) {
            lookAt(hit.getPos());
        }

        mc.interactionManager.interactBlock(mc.player, hand, hit);
        mc.player.swingHand(hand);

        renderPlacePos = pos.toImmutable();
        renderDamage = candidate.damage;
        placeRenderUntil = System.currentTimeMillis() + renderTime.getValue() * 50L;
        placeTimer.reset();

        if (switchMode.getValue() == SwitchMode.Normal && previous != slot && hand == Hand.MAIN_HAND) {
            mc.player.getInventory().setSelectedSlot(previous);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previous));
        }

        activeResult = null;
    }

    private Hand getCrystalHand() {
        if (mc.player.getOffHandStack().isOf(Items.END_CRYSTAL)) return Hand.OFF_HAND;
        if (mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) return Hand.MAIN_HAND;
        return null;
    }

    private int findCrystalSlot() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.END_CRYSTAL)) return i;
        }
        return -1;
    }

    private BlockHitResult getPlaceHit(BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();

        for (Direction direction : Direction.values()) {
            BlockPos support = pos.down();
            if (direction != Direction.UP && direction != Direction.DOWN) {
                Vec3d hitPos = Vec3d.ofCenter(support).add(
                        direction.getOffsetX() * 0.5d,
                        direction.getOffsetY() * 0.5d,
                        direction.getOffsetZ() * 0.5d
                );

                BlockHitResult result = mc.world.raycast(new RaycastContext(
                        eye,
                        hitPos,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        mc.player
                ));

                if (result.getType() == HitResult.Type.BLOCK && result.getBlockPos().equals(support)) {
                    return result;
                }
            }
        }

        return new BlockHitResult(
                Vec3d.ofCenter(support),
                Direction.UP,
                support,
                false
        );
    }

    private boolean isCrystalBase(BlockPos pos) {
        return mc.world.getBlockState(pos).isOf(net.minecraft.block.Blocks.OBSIDIAN)
                || mc.world.getBlockState(pos).isOf(net.minecraft.block.Blocks.BEDROCK);
    }

    private boolean intersectsEntities(Box box) {
        return !mc.world.getOtherEntities(null, box, entity ->
                entity.isAlive() && !(entity instanceof EndCrystalEntity)
        ).isEmpty();
    }

    private boolean canSee(Vec3d target) {
        Vec3d eye = mc.player.getEyePos();

        HitResult result = mc.world.raycast(new RaycastContext(
                eye,
                target,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        return result.getType() == HitResult.Type.MISS
                || eye.squaredDistanceTo(target) < eye.squaredDistanceTo(result.getPos());
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

    /**
     * Approximation of vanilla crystal explosion damage.
     * The expensive world/raycast work is deliberately performed while creating
     * the snapshot; the worker thread only ranks immutable candidates.
     */
    private float crystalDamage(PlayerEntity target, Vec3d explosion) {
        return crystalDamage(target, explosion, target.getPos());
    }

    private float crystalDamage(PlayerEntity target, Vec3d explosion, Vec3d predictedPosition) {
        double distance = Math.sqrt(predictedPosition.squaredDistanceTo(explosion));
        if (distance > 12.0d) return 0.0f;

        double exposure = calculateExposure(explosion, target, predictedPosition);
        double impact = (1.0d - distance / 12.0d) * exposure;
        if (impact <= 0.0d) return 0.0f;

        double damage = (impact * impact + impact) * 3.5d * 6.0d + 1.0d;

        double armor = Math.min(20.0d, target.getArmor());
        damage *= 1.0d - armor / 25.0d;

        if (target.getStatusEffect(net.minecraft.entity.effect.StatusEffects.RESISTANCE) != null) {
            damage *= 0.8d;
        }

        return (float) Math.max(0.0d, damage);
    }

    private double calculateExposure(Vec3d explosion, PlayerEntity entity, Vec3d position) {
        Box current = entity.getBoundingBox();
        double dx = position.x - entity.getX();
        double dy = position.y - entity.getY();
        double dz = position.z - entity.getZ();
        Box box = current.offset(dx, dy, dz);

        int samples = 3;
        int visible = 0;
        int total = 0;

        for (int x = 0; x < samples; x++) {
            for (int y = 0; y < samples; y++) {
                for (int z = 0; z < samples; z++) {
                    double px = box.minX + (box.maxX - box.minX) * ((x + 0.5d) / samples);
                    double py = box.minY + (box.maxY - box.minY) * ((y + 0.5d) / samples);
                    double pz = box.minZ + (box.maxZ - box.minZ) * ((z + 0.5d) / samples);

                    Vec3d sample = new Vec3d(px, py, pz);

                    HitResult result = mc.world.raycast(new RaycastContext(
                            explosion,
                            sample,
                            RaycastContext.ShapeType.COLLIDER,
                            RaycastContext.FluidHandling.NONE,
                            entity
                    ));

                    total++;
                    if (result.getType() == HitResult.Type.MISS) visible++;
                }
            }
        }

        return total == 0 ? 0.0d : (double) visible / total;
    }

    @Override
    public void onUnload() {
        if (!calculationExecutor.isShutdown()) {
            calculationExecutor.shutdownNow();
        }
    }

    @Override
    public void onRender3D(me.alpha432.oyvey.event.impl.Render3DEvent event) {
        if (renderMode.getValue() == RenderMode.None) return;

        long now = System.currentTimeMillis();

        if (renderPlace.getValue() && renderPlacePos != null && now <= placeRenderUntil) {
            renderPosition(event, renderPlacePos, renderDamage, false);
        }

        if (renderBreak.getValue() && renderBreakPos != null && now <= breakRenderUntil) {
            renderPosition(event, renderBreakPos, 0.0d, true);
        }
    }

    private void renderPosition(me.alpha432.oyvey.event.impl.Render3DEvent event, BlockPos pos, double damage, boolean breaking) {
        Color side = new Color(255, 80, 80, 45);
        Color line = new Color(255, 40, 40, 220);

        switch (renderMode.getValue()) {
            case Normal -> {
                drawBox(event, new Box(pos), side, line);
            }

            case Smooth -> {
                Box target = new Box(pos);

                if (breaking) {
                    if (smoothBreakBox == null) smoothBreakBox = target;
                    double factor = 1.0d / Math.max(1, smoothness.getValue());
                    smoothBreakBox = interpolateBox(smoothBreakBox, target, factor);
                    drawBox(event, smoothBreakBox, side, line);
                } else {
                    if (smoothPlaceBox == null) smoothPlaceBox = target;
                    double factor = 1.0d / Math.max(1, smoothness.getValue());
                    smoothPlaceBox = interpolateBox(smoothPlaceBox, target, factor);
                    drawBox(event, smoothPlaceBox, side, line);
                }
            }

            case Fading -> {
                long until = breaking ? breakRenderUntil : placeRenderUntil;
                long duration = Math.max(1L, renderTime.getValue() * 50L);
                double alpha = Math.max(0.0d, Math.min(1.0d, (until - System.currentTimeMillis()) / (double) duration));

                Color fadingSide = new Color(255, 80, 80, (int) (45.0d * alpha));
                Color fadingLine = new Color(255, 40, 40, (int) (220.0d * alpha));
                drawBox(event, new Box(pos), fadingSide, fadingLine);
            }

            case Gradient -> {
                drawGradient(event, pos, line);
            }

            case None -> {
            }
        }

        if (renderDamage.getValue() && !breaking && damage > 0.0d) {
            // Damage is retained as the module's render state; OyVey's current renderer
            // has no 2D world-text helper, so the box remains the renderer-compatible UI.
        }
    }

    private void drawBox(me.alpha432.oyvey.event.impl.Render3DEvent event, Box box, Color side, Color line) {
        if (filled.getValue()) RenderUtil.drawBoxFilled(event.getMatrix(), box, side);
        if (outlined.getValue()) RenderUtil.drawBox(event.getMatrix(), box, line, 1.0d);
    }

    private void drawGradient(me.alpha432.oyvey.event.impl.Render3DEvent event, BlockPos pos, Color line) {
        int slices = 8;
        double height = Math.max(0.1d, gradientHeight.getValue());

        for (int i = 0; i < slices; i++) {
            double top = pos.getY() + 1.0d - (height * i / slices);
            double bottom = pos.getY() + 1.0d - (height * (i + 1) / slices);

            int alpha = (int) (45.0d * (1.0d - (double) i / slices));
            Box slice = new Box(
                    pos.getX(),
                    bottom,
                    pos.getZ(),
                    pos.getX() + 1.0d,
                    top,
                    pos.getZ() + 1.0d
            );

            if (filled.getValue()) {
                RenderUtil.drawBoxFilled(
                        event.getMatrix(),
                        slice,
                        new Color(255, 80, 80, alpha)
                );
            }
        }

        if (outlined.getValue()) {
            RenderUtil.drawBox(
                    event.getMatrix(),
                    new Box(pos),
                    line,
                    1.0d
            );
        }
    }

    private Box interpolateBox(Box from, Box to, double factor) {
        factor = Math.max(0.0d, Math.min(1.0d, factor));

        return new Box(
                lerp(from.minX, to.minX, factor),
                lerp(from.minY, to.minY, factor),
                lerp(from.minZ, to.minZ, factor),
                lerp(from.maxX, to.maxX, factor),
                lerp(from.maxY, to.maxY, factor),
                lerp(from.maxZ, to.maxZ, factor)
        );
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    @Override
    public String getDisplayInfo() {
        if (activeResult == null || activeResult.candidate == null) return null;
        return String.format("%.1f", activeResult.candidate.damage);
    }

    private static final class TargetSnapshot {
        private final PlayerEntity entity;
        private final Vec3d position;
        private final double health;
        private final int armor;
        private final int hurtTime;

        private TargetSnapshot(PlayerEntity entity, Vec3d position, double health, int armor, int hurtTime) {
            this.entity = entity;
            this.position = position;
            this.health = health;
            this.armor = armor;
            this.hurtTime = hurtTime;
        }
    }

    private static final class Candidate {
        private final BlockPos pos;
        private final Vec3d center;
        private final double damage;
        private final double selfDamage;
        private final TargetSnapshot target;

        private Candidate(BlockPos pos, Vec3d center, double damage, double selfDamage, TargetSnapshot target) {
            this.pos = pos;
            this.center = center;
            this.damage = damage;
            this.selfDamage = selfDamage;
            this.target = target;
        }
    }

    private static final class Snapshot {
        private final Vec3d playerPos;
        private final List<TargetSnapshot> targets;
        private final List<Candidate> candidates;

        private Snapshot(Vec3d playerPos, List<TargetSnapshot> targets, List<Candidate> candidates) {
            this.playerPos = playerPos;
            this.targets = targets;
            this.candidates = candidates;
        }
    }

    private static final class CalculationResult {
        private final Candidate candidate;
        private EndCrystalEntity crystal;

        private CalculationResult(Candidate candidate) {
            this.candidate = candidate;
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private final AtomicInteger index = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "OyVey-CrystalAura-" + index.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }
}
