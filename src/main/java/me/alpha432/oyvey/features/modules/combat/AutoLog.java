package me.alpha432.oyvey.features.modules.combat;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.event.impl.PacketEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.features.modules.misc.AutoReconnect;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.text.Text;
import java.util.HashMap;
import java.util.Map;

public class AutoLog extends Module {
    private final Setting<Integer> health = num("Health", 6, 0, 19);
    private final Setting<Boolean> smart = bool("Predict Damage", true);
    private final Setting<Integer> totemPops = num("Totem Pops", 0, 0, 50);
    private final Setting<Boolean> onlyTrusted = bool("Only Trusted", false);
    private final Setting<Boolean> instantDeath = bool("32K", false);
    private final Setting<Boolean> smartToggle = bool("Smart Toggle", false);
    private final Setting<Boolean> toggleOff = bool("Toggle Off", true);
    private final Setting<Boolean> toggleAutoReconnect = bool("Toggle AutoReconnect", true);
    private final Setting<Boolean> useTotalCount = bool("Total Entity Count", true);
    private final Setting<Integer> combinedEntityThreshold = num("Entity Threshold", 10, 1, 32);
    private final Setting<Integer> individualEntityThreshold = num("Individual Threshold", 2, 1, 16);
    private final Setting<Integer> range = num("Entity Range", 5, 1, 16);

    private int pops;
    private final Map<EntityType<?>, Integer> entityCounts = new HashMap<>();

    public AutoLog() {
        super("AutoLog", "Automatically disconnects when configured danger conditions are met.", Category.COMBAT, true, false, false);
    }

    @Override public void onEnable() { pops = 0; }

    @Override
    public void onUpdate() {
        if (nullCheck() || mc.player.networkHandler == null) return;
        float hp = mc.player.getHealth();
        if (hp <= 0) { disable(); return; }

        if (health.getValue() > 0 && hp <= health.getValue()) {
            disconnect("Health was lower than " + health.getValue() + ".");
            afterLogout();
            return;
        }

        if (smart.getValue() && health.getValue() > 0
                && hp + mc.player.getAbsorptionAmount() - possibleDamage() < health.getValue()) {
            disconnect("Predicted incoming damage would lower health below the limit.");
            afterLogout();
            return;
        }

        if (onlyTrusted.getValue()) {
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p != mc.player && p.isAlive() && !OyVey.friendManager.isFriend(p)) {
                    disconnect("Non-trusted player appeared in render distance.");
                    afterLogout();
                    return;
                }
            }
        }

        if (instantDeath.getValue()) {
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p != mc.player && p.isAlive() && !OyVey.friendManager.isFriend(p)
                        && mc.player.squaredDistanceTo(p) <= 64.0
                        && p.getAttackDamage(mc.player) > hp + mc.player.getAbsorptionAmount()) {
                    disconnect("Anti-32K measures.");
                    afterLogout();
                    return;
                }
            }
        }

        entityCounts.clear();
        int total = 0;
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || mc.player.distanceTo(e) > range.getValue()) continue;
            total++;
            entityCounts.merge(e.getType(), 1, Integer::sum);
        }

        if (useTotalCount.getValue() && total >= combinedEntityThreshold.getValue()) {
            disconnect("Total entity count exceeded the limit.");
            afterLogout();
        } else if (!useTotalCount.getValue()) {
            for (Map.Entry<EntityType<?>, Integer> e : entityCounts.entrySet()) {
                if (e.getValue() >= individualEntityThreshold.getValue()) {
                    disconnect("Entity count exceeded the individual limit.");
                    afterLogout();
                    return;
                }
            }
        }
    }

    @Subscribe
    public void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.getPacket() instanceof EntityStatusS2CPacket packet) || nullCheck()) return;
        if (packet.getStatus() != 35 || packet.getEntity(mc.world) != mc.player) return;
        pops++;
        if (totemPops.getValue() > 0 && pops >= totemPops.getValue()) {
            disconnect("Popped " + pops + " totems.");
            afterLogout();
        }
    }

    private double possibleDamage() {
        double damage = 0.0;
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof PlayerEntity p && p != mc.player
                    && p.squaredDistanceTo(mc.player) <= 64.0
                    && !OyVey.friendManager.isFriend(p)) {
                damage = Math.max(damage, p.getAttackDamage(mc.player));
            }
        }
        return damage;
    }

    private void disconnect(String reason) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        mc.player.networkHandler.getConnection().disconnect(Text.literal("[AutoLog] " + reason));
    }

    private void afterLogout() {
        if (toggleAutoReconnect.getValue()) {
            AutoReconnect ar = OyVey.moduleManager.getModuleByClass(AutoReconnect.class);
            if (ar != null && ar.isEnabled()) ar.disable();
        }
        if (toggleOff.getValue()) disable();
    }

    @Override public String getDisplayInfo() { return Integer.toString(pops); }
}
