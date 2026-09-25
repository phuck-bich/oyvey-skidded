package me.alpha432.oyvey.features.modules.misc;

import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.util.entity.FakePlayerEntity;

/** Small Misc toggle that keeps one local fake player beside the user. */
public class FakePlayer extends Module {
    private FakePlayerEntity fakePlayer;

    public FakePlayer() {
        super("FakePlayer", "Spawns a client-side copy of your player", Category.MISC, false, false, false);
    }

    @Override
    public void onEnable() {
        spawnFakePlayer();
    }

    @Override
    public void onUpdate() {
        if (fakePlayer == null || fakePlayer.isRemoved() || mc.world == null || !fakePlayer.belongsTo(mc.world)) {
            spawnFakePlayer();
        }
    }

    @Override
    public void onDisable() {
        removeFakePlayer();
    }

    @Override
    public void onUnload() {
        removeFakePlayer();
    }

    @Override
    public String getDisplayInfo() {
        return fakePlayer != null && !fakePlayer.isRemoved() ? fakePlayer.getGameProfile().name() : null;
    }

    private void spawnFakePlayer() {
        if (mc.player == null || mc.world == null) return;
        fakePlayer = new FakePlayerEntity(mc.world, mc.player, "OyVeyFake");
        fakePlayer.spawn();
    }

    private void removeFakePlayer() {
        if (fakePlayer == null) return;
        fakePlayer.despawn();
        fakePlayer = null;
    }
}
