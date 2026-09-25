package me.alpha432.oyvey.features.modules.combat;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.PacketEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

public class AutoTotem extends Module {
    public enum Mode { Smart, Strict }

    private final Setting<Mode> mode = mode("Mode", Mode.Smart);
    private final Setting<Integer> delay = num("Delay", 0, 0, 100);
    private final Setting<Integer> health = num("Health", 10, 0, 36);
    private final Setting<Boolean> elytra = bool("Elytra", true);
    private final Setting<Boolean> fall = bool("Fall", true);
    private final Setting<Boolean> explosion = bool("Explosion", true);

    public boolean locked;
    private int totems;
    private int ticks;

    public AutoTotem() {
        super("AutoTotem", "Automatically equips a totem in your offhand.", Category.COMBAT, true, false, false);
    }

    @Override
    public void onEnable() {
        locked = false;
        ticks = 0;
        totems = countTotems();
    }

    @Override
    public void onUpdate() {
        if (nullCheck() || mc.player == null || mc.interactionManager == null) return;

        totems = countTotems();
        if (totems <= 0) {
            locked = false;
            return;
        }

        if (ticks < delay.getValue()) {
            ticks++;
            return;
        }

        float totalHealth = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        boolean low = totalHealth - possibleHealthReductions() <= health.getValue();
        boolean flying = elytra.getValue()
                && mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)
                && mc.player.isGliding();

        locked = mode.getValue() == Mode.Strict
                || (mode.getValue() == Mode.Smart && (low || flying));

        if (locked && !mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) {
            moveToOffhand();
        }

        ticks = 0;
    }

    @Subscribe
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof EntityStatusS2CPacket packet)) return;
        if (packet.getStatus() != 35 || nullCheck()) return;
        if (packet.getEntity(mc.world) != mc.player) return;
        ticks = 0;
    }

    public boolean isLocked() {
        return isEnabled() && locked;
    }

    @Override
    public String getDisplayInfo() {
        return Integer.toString(totems);
    }

    private void moveToOffhand() {
        int slot = findTotem();
        if (slot < 0) return;

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        if (slot < 9) {
            mc.player.getInventory().setSelectedSlot(slot);
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(slot));
        }

        int inventorySlot = slot < 9 ? 36 + slot : slot;
        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                inventorySlot,
                40,
                net.minecraft.screen.slot.SlotActionType.SWAP,
                mc.player
        );

        if (slot < 9) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
            mc.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(oldSlot));
        }
    }

    private int findTotem() {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }

    private int countTotems() {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) {
                count += mc.player.getInventory().getStack(i).getCount();
            }
        }
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) count += mc.player.getOffHandStack().getCount();
        return count;
    }

    private float possibleHealthReductions() {
        float damage = 0.0f;

        if (explosion.getValue()) {
            damage = Math.max(damage, estimateExplosionDamage());
        }

        if (fall.getValue()) {
            damage = Math.max(damage, estimateFallDamage());
        }

        return damage;
    }

    private float estimateFallDamage() {
        if (mc.player.hasNoGravity() || mc.player.isGliding()) return 0.0f;
        float fallDistance = mc.player.fallDistance;
        if (fallDistance <= 3.0f) return 0.0f;
        return Math.max(0.0f, fallDistance - 3.0f);
    }

    private float estimateExplosionDamage() {
        float max = 0.0f;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive()) continue;
            if (mc.player.squaredDistanceTo(player) > 64.0) continue;

            double distance = Math.sqrt(mc.player.squaredDistanceTo(player));
            float estimated = (float) Math.max(0.0, 12.0 - distance * 2.0);
            max = Math.max(max, estimated);
        }
        return max;
    }
}
