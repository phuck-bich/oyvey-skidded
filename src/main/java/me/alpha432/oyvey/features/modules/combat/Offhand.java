package me.alpha432.oyvey.features.modules.combat;

import me.alpha432.oyvey.OyVey;
import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.PacketEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

public class Offhand extends Module {
    private enum Mode { Totem, Crystal, Gapple, Sword }

    private final Setting<Mode> mode = mode("Mode", Mode.Totem);
    private final Setting<Double> health = num("Health", 16.0d, 1.0d, 20.0d);
    private final Setting<Boolean> swordGap = bool("SwordGap", true);
    private final Setting<Boolean> totemGap = bool("TotemGap", true);
    private final Setting<Boolean> smart = bool("Smart", true);

    private boolean forceTotem;

    public Offhand() {
        super("Offhand", "Automatically manages the offhand item.", Category.COMBAT, true, false, false);
    }

    @Override public void onEnable() { forceTotem = false; }
    @Override public void onDisable() { forceTotem = false; }

    @Override
    public void onUpdate() {
        if (nullCheck() || mc.player.currentScreenHandler == null) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof HandledScreen<?>)) return;

        Item desired = getDesired();
        if (forceTotem) {
            desired = Items.TOTEM_OF_UNDYING;
            forceTotem = false;
        }

        if (mc.player.getOffHandStack().isOf(desired)) return;

        int slot = findItem(desired);
        if (slot == -1 && desired != Items.TOTEM_OF_UNDYING) slot = findItem(Items.TOTEM_OF_UNDYING);
        if (slot == -1) return;

        int old = mc.player.getInventory().getSelectedSlot();
        if (slot < 9) {
            mc.player.getInventory().setSelectedSlot(slot);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }

        // Vanilla-compatible offhand swap through the inventory screen handler.
        int inventorySlot = slot < 9 ? 36 + slot : slot;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, inventorySlot,
                40, net.minecraft.screen.slot.SlotActionType.SWAP, mc.player);

        if (slot < 9) {
            mc.player.getInventory().setSelectedSlot(old);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(old));
        }
    }

    @Override
    @Subscribe\n    public void onPacketReceive(PacketEvent.Receive event) {\n        if (!(event.getPacket() instanceof EntityStatusS2CPacket packet)) return;
        if (nullCheck()) return;
        if (packet.getEntity(mc.world) == mc.player && packet.getStatus() == 35
                && getTotalHealth() <= health.getValue()) {
            forceTotem = true;
        }
    }

    private Item getDesired() {
        float hp = getTotalHealth();
        if (hp <= health.getValue() || getFallDamage() >= hp) return Items.TOTEM_OF_UNDYING;

        if (smart.getValue() && !mc.world.getEntitiesByClass(
                EndCrystalEntity.class, mc.player.getBoundingBox().expand(6.0), e -> true).isEmpty()) {
            return Items.TOTEM_OF_UNDYING;
        }

        boolean using = mc.options.useKey.isPressed();
        Item main = mc.player.getMainHandStack().getItem();
        if (using && totemGap.getValue() && main == Items.TOTEM_OF_UNDYING) return bestGapple();
        if (using && swordGap.getValue() && isSword(main)) return bestGapple();

        return switch (mode.getValue()) {
            case Crystal -> Items.END_CRYSTAL;
            case Gapple -> bestGapple();
            case Sword -> bestSword();
            default -> Items.TOTEM_OF_UNDYING;
        };
    }

    private int findItem(Item item) {
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    private Item bestGapple() {
        return findItem(Items.ENCHANTED_GOLDEN_APPLE) != -1
                ? Items.ENCHANTED_GOLDEN_APPLE : Items.GOLDEN_APPLE;
    }

    private Item bestSword() {
        Item[] swords = {Items.NETHERITE_SWORD, Items.DIAMOND_SWORD, Items.IRON_SWORD, Items.STONE_SWORD, Items.WOODEN_SWORD};
        for (Item item : swords) if (findItem(item) != -1) return item;
        return Items.NETHERITE_SWORD;
    }

    private boolean isSword(Item item) {
        return item == Items.NETHERITE_SWORD || item == Items.DIAMOND_SWORD
                || item == Items.IRON_SWORD || item == Items.STONE_SWORD || item == Items.WOODEN_SWORD;
    }

    private float getTotalHealth() { return mc.player.getHealth() + mc.player.getAbsorptionAmount(); }

    private float getFallDamage() {
        if (mc.player.hasStatusEffect(StatusEffects.SLOW_FALLING) || mc.player.isGliding()) return 0;
        var jump = mc.player.getStatusEffect(StatusEffects.JUMP_BOOST);
        float offset = jump != null ? jump.getAmplifier() + 1 : 0;
        return Math.max((float) Math.ceil(mc.player.fallDistance - 3.0f - offset), 0);
    }

    @Override public String getDisplayInfo() { return Integer.toString(countItem(Items.TOTEM_OF_UNDYING)); }

    private int countItem(Item item) {
        int count=0; for (int i=0;i<36;i++) count += mc.player.getInventory().getStack(i).isOf(item) ? mc.player.getInventory().getStack(i).getCount() : 0; return count;
    }
}
