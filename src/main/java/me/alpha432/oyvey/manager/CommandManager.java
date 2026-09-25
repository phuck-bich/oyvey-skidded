package me.alpha432.oyvey.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.alpha432.oyvey.features.Feature;
import me.alpha432.oyvey.features.commands.Command;
import me.alpha432.oyvey.features.commands.impl.*;
import me.alpha432.oyvey.util.traits.Jsonable;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandManager
        extends Feature implements Jsonable {
    private final List<Command> commands = new ArrayList<>();
    private String clientMessage = "<OyVey>";
    private String prefix = ".";

    public CommandManager() {
        super("Command");
        commands.add(new ToggleCommand());
        commands.add(new BindCommand());
        commands.add(new FriendCommand());
        commands.add(new ModuleCommand());
        commands.add(new PrefixCommand());
        commands.add(new FakePlayerCommand());

        commands.add(new HelpCommand());
    }

    private static final Pattern ARGUMENT_PATTERN = Pattern.compile("\"([^\"]*)\"|(\\S+)");

    public void executeCommand(String command) {
        Matcher matcher = ARGUMENT_PATTERN.matcher(command);
        List<String> parts = new ArrayList<>();
        while (matcher.find()) parts.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
        if (parts.isEmpty()) return;

        String name = parts.get(0);
        String[] args = parts.subList(1, parts.size()).toArray(String[]::new);
        for (Command c : this.commands) {
            boolean fakePlayerAlias = c.getName().equalsIgnoreCase("fake-player") && name.equalsIgnoreCase("fakeplayer");
            if (!c.getName().equalsIgnoreCase(name) && !fakePlayerAlias) continue;
            c.execute(args);
            return;
        }
        Command.sendMessage(Formatting.GRAY + "Command not found, type 'help' for the commands list.");
    }

    public Command getCommandByName(String name) {
        for (Command command : this.commands) {
            if (!command.getName().equalsIgnoreCase(name)) continue;
            return command;
        }
        return null;
    }

    public List<Command> getCommands() {
        return this.commands;
    }

    public String getClientMessage() {
        return this.clientMessage;
    }

    public void setClientMessage(String clientMessage) {
        this.clientMessage = clientMessage;
    }

    public String getPrefix() {
        return this.prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override public JsonElement toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("prefix", prefix);
        return object;
    }

    @Override public void fromJson(JsonElement element) {
        setPrefix(element.getAsJsonObject().get("prefix").getAsString());
    }

    @Override public String getFileName() {
        return "commands.json";
    }
}
