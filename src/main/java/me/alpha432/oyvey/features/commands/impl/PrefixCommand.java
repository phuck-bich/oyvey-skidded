package me.alpha432.oyvey.features.commands.impl;

import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.commands.Command;
import net.minecraft.util.Formatting;

public class PrefixCommand
        extends Command {
    public PrefixCommand() {
        super("prefix", new String[]{"<char>"});
    }

    @Override
    public void execute(String[] commands) {
        if (commands.length == 0) {
            Command.sendMessage(Formatting.GREEN + "Current prefix is " + OyVey.commandManager.getPrefix());
            return;
        }
        if (commands.length != 1 || commands[0].isBlank()) {
            Command.sendMessage("Usage: " + OyVey.commandManager.getPrefix() + "prefix <prefix>");
            return;
        }
        OyVey.commandManager.setPrefix(commands[0]);
        Command.sendMessage("Prefix changed to " + Formatting.GRAY + commands[0]);
    }
}
