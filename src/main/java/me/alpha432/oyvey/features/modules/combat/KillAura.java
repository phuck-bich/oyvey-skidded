package me.alpha432.oyvey.features.modules.combat;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.event.impl.Render3DEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.util.render.RenderUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.awt.Color;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class KillAura extends Module {

    public String targetMode = "Single";
    public String limbMode = "Auto";
    public String swapMode = "Switch";

    public boolean targetPlayers = true;
    public boolean targetMonsters = false;
    public boolean targetAnimals = false;

    public double range = 5.0d;
    public double wallRange = 3.5d;

    public boolean rotate = true;
    public boolean invis = true;
    public boolean filled = true;

    private long lastAttackTime = 0L;
    private final long attackCooldownMillis = 600L;

    private final long hitFadeMillis = 550L;
    private Entity target;
    private long hitTime = 0L;

    public KillAura() {
        super("KillAura", "Attacks nearby entities automatically.", Category.COMBAT, true, false, false);
    }

    @Override
    public void onEnable() {
        target = null;
        hitTime = 0L;
        lastAttackTime = 0L;
    }

    @Override
    public void onDisable() {
        target = null;
        hitTime = 0L;
    }

    @Override
    public void onUpdate() {
        if (nullCheck()) {
            target = null;
            return;
        }

        if (!handleSwordSwap()) {
            target = null;
            return;
        }

        target = findTarget();

        if (target == null) {
            return;
        }

        long now = System.currentTimeMillis();

        if (now - lastAttackTime < attackCooldownMillis) {
            return;
        }

        if (mc.player.getAttackCooldownProgress(0.0f) < 1.0f) {
            return;
        }

        if (rotate) {
            Vec3d aimPos = getAimPosition(target);

            mc.player.lookAt(net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES, aimPos);
        }

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        lastAttackTime = now;
        hitTime = now;
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (target == null || nullCheck()) {
            return;
        }

        long now = System.currentTimeMillis();
        long elapsed = now - hitTime;

        if (elapsed >= hitFadeMillis) {
            return;
        }

        double alpha = 1.0d - ((double) elapsed / hitFadeMillis);

        int outlineAlpha = (int) (255.0d * alpha);
        int filledAlpha = (int) (70.0d * alpha);

        double distanceSquared = mc.player.squaredDistanceTo(target);

        double maximumRange = canSee(target) ? range : wallRange;

        if (distanceSquared > maximumRange * maximumRange) {
            return;
        }

        float tickDelta = mc.getRenderTickCounter().getTickProgress(true);

        Vec3d interpolatedPosition = target.getLerpedPos(tickDelta);

        Box box = target.getBoundingBox().offset(
                interpolatedPosition.x - target.getX(),
                interpolatedPosition.y - target.getY(),
                interpolatedPosition.z - target.getZ()
        );

        if (filled) {
            Color fillColor = new Color(
                    255,
                    0,
                    0,
                    filledAlpha
            );

            RenderUtil.drawBox(
                    event.getMatrix(),
                    box,
                    fillColor,
                    1.0f
            );
        }

        Color outlineColor = new Color(
                255,
                0,
                0,
                outlineAlpha
        );

        RenderUtil.drawBox(
                event.getMatrix(),
                box,
                outlineColor,
                1.0f
        );
    }

    private boolean handleSwordSwap() {
        if (swapMode.equals("None")) {
            return true;
        }

        int swordSlot = findSwordSlot();

        if (swordSlot == -1) {
            return swapMode.equals("Switch");
        }

        int selectedSlot = mc.player.getInventory().getSelectedSlot();

        if (swapMode.equals("Require")) {
            return selectedSlot == swordSlot;
        }

        if (swapMode.equals("Switch") && selectedSlot != swordSlot) {
            mc.player.getInventory().setSelectedSlot(swordSlot);

            mc.player.networkHandler.sendPacket(
                    new UpdateSelectedSlotC2SPacket(swordSlot)
            );
        }

        return true;
    }

    private int findSwordSlot() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);

            if (stack.isIn(ItemTags.SWORDS)) {
                return slot;
            }
        }

        return -1;
    }

    private Entity findTarget() {
        List<Entity> valid = mc.world.getEntitiesByClass(
                        Entity.class,
                        mc.player.getBoundingBox().expand(range),
                        entity ->
                                entity != mc.player
                                        && entity.isAlive()
                                        && !entity.isRemoved()
                                        && (invis || !entity.isInvisible())
                                        && isValidTarget(entity)
                                        && isNotFriend(entity)
                )
                .stream()
                .filter(entity -> {
                    double distanceSquared =
                            mc.player.squaredDistanceTo(entity);

                    double rangeSquared =
                            range * range;

                    double wallRangeSquared =
                            wallRange * wallRange;

                    return distanceSquared <= rangeSquared
                            && (canSee(entity)
                            || distanceSquared <= wallRangeSquared);
                })
                .collect(Collectors.toList());

        if (valid.isEmpty()) {
            return null;
        }

        if (targetMode.equals("Single")) {
            return valid.stream()
                    .min(
                            Comparator.comparingDouble(
                                    entity ->
                                            mc.player.squaredDistanceTo(entity)
                            )
                    )
                    .orElse(null);
        }

        return valid.stream()
                .filter(entity -> entity instanceof LivingEntity)
                .map(entity -> (LivingEntity) entity)
                .min(
                        Comparator.comparingDouble(
                                this::calculatePriorityScore
                        )
                )
                .map(entity -> (Entity) entity)
                .orElse(valid.get(0));
    }

    private boolean isNotFriend(Entity entity) {
        if (!(entity instanceof PlayerEntity player)) {
            return true;
        }

        return !OyVey.friendManager.isFriend(
                player.getName().getString()
        );
    }

    private double calculatePriorityScore(LivingEntity entity) {
        double health = entity.getHealth();
        double distance = mc.player.distanceTo(entity);
        double armor = entity.getArmor();

        return (health * 0.4d)
                + (distance * 0.5d)
                - (armor * 0.1d);
    }

    private boolean isValidTarget(Entity entity) {
        if (entity instanceof PlayerEntity) {
            return targetPlayers;
        }

        if (entity instanceof HostileEntity) {
            return targetMonsters;
        }

        if (entity instanceof AnimalEntity) {
            return targetAnimals;
        }

        return false;
    }

    private Vec3d getAimPosition(Entity entity) {
        double yOffset = switch (limbMode) {
            case "Head" -> entity.getHeight() * 0.9d;
            case "Chest" -> entity.getHeight() * 0.65d;
            case "Feet" -> entity.getHeight() * 0.1d;
            default -> entity.getHeight() * 0.75d;
        };

        if (limbMode.equals("Auto")) {
            Vec3d bestHit = null;
            double bestDistanceSquared = Double.MAX_VALUE;

            double[] offsets = {
                    entity.getHeight() * 0.9d,
                    entity.getHeight() * 0.65d,
                    entity.getHeight() * 0.1d
            };

            for (double offset : offsets) {
                Vec3d checkPosition = entity.getEntityPos().add(
                        0.0d,
                        offset,
                        0.0d
                );

                if (!canSee(checkPosition)) {
                    continue;
                }

                double distanceSquared =
                        mc.player.getEyePos()
                                .squaredDistanceTo(checkPosition);

                if (distanceSquared < bestDistanceSquared) {
                    bestDistanceSquared = distanceSquared;
                    bestHit = checkPosition;
                }
            }

            if (bestHit != null) {
                return bestHit;
            }
        }

        return entity.getEntityPos().add(
                0.0d,
                yOffset,
                0.0d
        );
    }

    private boolean canSee(Entity entity) {
        return canSee(
                entity.getEntityPos().add(
                        0.0d,
                        entity.getHeight() * 0.5d,
                        0.0d
                )
        );
    }

    private boolean canSee(Vec3d targetPosition) {
        Vec3d eyePosition = mc.player.getEyePos();

        HitResult hitResult = mc.world.raycast(
                new RaycastContext(
                        eyePosition,
                        targetPosition,
                        RaycastContext.ShapeType.COLLIDER,
                        RaycastContext.FluidHandling.NONE,
                        mc.player
                )
        );

        return hitResult.getType() == HitResult.Type.MISS
                || eyePosition.squaredDistanceTo(targetPosition)
                < eyePosition.squaredDistanceTo(hitResult.getPos());
    }

    @Override
    public String getDisplayInfo() {
        return targetMode;
    }
}

