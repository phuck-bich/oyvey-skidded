package me.alpha432.oyvey.features.modules.misc;

import com.google.common.eventbus.Subscribe;
import me.alpha432.oyvey.event.impl.ChatEvent;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;

/** Small chat quality-of-life helpers inspired by Meteor and Mint. */
public class BetterChat extends Module {
    private final Setting<Boolean> coordinateProtection = register(bool("Coordinate Protection", true));
    private final Setting<Boolean> blockSpam = register(bool("Block Repeated Messages", false));
    private final Setting<Integer> repeatWindow = register(num("Repeat Window (seconds)", 3, 1, 30));
    private String lastMessage = "";
    private long lastSentAt;

    public BetterChat() { super("BetterChat", "Adds chat safety and spam controls", Category.MISC, true, false, false); }

    @Subscribe private void onChat(ChatEvent event) {
        String message = event.getMessage();
        if (coordinateProtection.getValue() && message.matches("(?is).*(?:x\\s*[:=]?\\s*-?\\d{2,}.*y\\s*[:=]?\\s*-?\\d{2,}|y\\s*[:=]?\\s*-?\\d{2,}.*z\\s*[:=]?\\s*-?\\d{2,}|-?\\d{2,}\\s*[, ]+~-?\\d{2,}\\s*[, ]+~-?\\d{2,}).*")) {
            event.cancel();
            return;
        }
        long now = System.currentTimeMillis();
        if (blockSpam.getValue() && message.equals(lastMessage) && now - lastSentAt < repeatWindow.getValue() * 1000L) {
            event.cancel();
            return;
        }
        lastMessage = message;
        lastSentAt = now;
    }

    @Override public void onDisable() { lastMessage = ""; lastSentAt = 0; }
}
