package me.alpha432.oyvey.util.entity;

import com.mojang.authlib.GameProfile;
import me.alpha432.oyvey.util.traits.Util;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;

/** Client-only player clone that uses the local player's skin and equipment. */
public class FakePlayerEntity extends OtherClientPlayerEntity implements Util {
    public FakePlayerEntity(ClientWorld world, PlayerEntity source, String name) {
        super(world, new GameProfile(UUID.randomUUID(), name));
        copyPositionAndRotation(source);
        setHeadYaw(source.getHeadYaw());
        setBodyYaw(source.getBodyYaw());
        setHealth(source.getHealth());
        setPose(source.getPose());
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
                EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
            equipStack(slot, source.getEquippedStack(slot).copy());
        }
    }

    @Override
    protected PlayerListEntry getPlayerListEntry() {
        if (mc.getNetworkHandler() == null || mc.player == null) return super.getPlayerListEntry();
        return mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
    }
}
