package me.alpha432.oyvey.features.commands.impl;

import me.alpha432.oyvey.features.commands.Command;
import me.alpha432.oyvey.util.entity.FakePlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.Formatting;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class FakePlayerCommand extends Command {
    private final Map<String, FakePlayerEntity> fakePlayers = new LinkedHashMap<>();

    public FakePlayerCommand() {
        super("fakeplayer", new String[]{"<spawn|remove|list|clear>", "[name]"});
    }

    @Override
    public void execute(String[] args) {
        if (mc.player == null || mc.world == null) {
            sendMessage("Join a world before using this command.");
            return;
        }
        discardStaleEntries();
        if (args.length == 0) {
            spawn("FakePlayer");
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "spawn", "add" -> {
                if (args.length > 2) {
                    usage();
                    return;
                }
                spawn(args.length == 2 ? args[1] : "FakePlayer");
            }
            case "remove", "despawn", "del" -> {
                if (args.length != 2) {
                    sendMessage("Usage: " + getCommandPrefix() + "fakeplayer remove <name>");
                    return;
                }
                remove(args[1]);
            }
            case "list" -> list();
            case "clear" -> clear();
            default -> {
                if (args.length == 1) spawn(args[0]);
                else usage();
            }
        }
    }

    private void spawn(String requestedName) {
        String name = requestedName.trim();
        if (name.isEmpty() || name.length() > 16) {
            sendMessage("Choose a name between 1 and 16 characters.");
            return;
        }
        String key = name.toLowerCase(Locale.ROOT);
        if (fakePlayers.containsKey(key)) {
            sendMessage("A fake player named " + name + " already exists.");
            return;
        }

        FakePlayerEntity fake = new FakePlayerEntity(mc.world, mc.player, name);
        mc.world.addEntity(fake);
        fakePlayers.put(key, fake);
        sendMessage("Spawned fake player " + Formatting.GREEN + name + Formatting.GRAY + ".");
    }

    private void remove(String name) {
        FakePlayerEntity fake = fakePlayers.remove(name.toLowerCase(Locale.ROOT));
        if (fake == null) {
            sendMessage("No fake player named " + name + " was found.");
            return;
        }
        if (!fake.isRemoved()) {
            mc.world.removeEntity(fake.getId(), Entity.RemovalReason.DISCARDED);
            fake.setRemoved(Entity.RemovalReason.DISCARDED);
        }
        sendMessage("Removed fake player " + name + ".");
    }

    private void list() {
        if (fakePlayers.isEmpty()) {
            sendMessage("There are no fake players.");
            return;
        }
        sendMessage("Fake players: " + String.join(", ", fakePlayers.values().stream().map(FakePlayerEntity::getNameForScoreboard).toList()));
    }

    private void clear() {
        int count = fakePlayers.size();
        for (String name : fakePlayers.keySet().toArray(String[]::new)) remove(name);
        if (count == 0) sendMessage("There are no fake players to remove.");
    }

    private void discardStaleEntries() {
        fakePlayers.entrySet().removeIf(entry -> entry.getValue().isRemoved());
    }

    private void usage() {
        sendMessage("Usage: " + getCommandPrefix() + "fakeplayer [spawn <name>|remove <name>|list|clear]");
    }
}
