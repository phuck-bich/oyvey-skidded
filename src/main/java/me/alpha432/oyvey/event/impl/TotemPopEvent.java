package me.alpha432.oyvey.event.impl;

import me.alpha432.oyvey.event.Event;
import net.minecraft.entity.player.PlayerEntity;

public class TotemPopEvent extends Event {
    private final PlayerEntity player;

    public TotemPopEvent(PlayerEntity player) {
        this.player = player;
    }

    public PlayerEntity getPlayer() {
        return player;
    }
}
