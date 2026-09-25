package me.alpha432.oyvey.features.commands.impl;

import com.google.gson.JsonParser;
import me.alpha432.oyvey.OyVey;
import me.alpha432.oyvey.features.commands.Command;
import me.alpha432.oyvey.features.modules.Module;
import me.alpha432.oyvey.features.settings.Setting;
import me.alpha432.oyvey.manager.ConfigManager;
import net.minecraft.util.Formatting;

public class ModuleCommand
        extends Command {
    public ModuleCommand() {
        super("module", new String[]{"<module>", "<set/reset>", "<setting>", "<value>"});
    }

    @Override
    public void execute(String[] commands) {
        if (commands.length == 0) {
            ModuleCommand.sendMessage("Modules: ");
            for (Module.Category category : OyVey.moduleManager.getCategories()) {
                String modules = category.getName() + ": ";
                for (Module module1 : OyVey.moduleManager.getModulesByCategory(category)) {
                    modules = modules + (module1.isEnabled() ? Formatting.GREEN : Formatting.RED) + module1.getName() + Formatting.WHITE + ", ";
                }
                ModuleCommand.sendMessage(modules);
            }
            return;
        }
        Module module = OyVey.moduleManager.getModuleByDisplayName(commands[0]);
        if (module == null) {
            module = OyVey.moduleManager.getModuleByName(commands[0]);
            if (module == null) {
                ModuleCommand.sendMessage("This module doesnt exist.");
                return;
            }
        }
        if (commands.length == 1) {
            ModuleCommand.sendMessage(module.getDisplayName() + " : " + module.getDescription());
            for (Setting setting2 : module.getSettings()) {
                ModuleCommand.sendMessage(setting2.getName() + " : " + setting2.getValue() + ", " + setting2.getDescription());
            }
            return;
        }
        if (commands.length == 2 && commands[1].equalsIgnoreCase("reset")) {
            for (Setting<?> setting : module.getSettings()) {
                if (setting.getName().equalsIgnoreCase("Enabled")) {
                    module.setEnabled((Boolean) setting.getDefaultValue());
                } else {
                    ((Setting) setting).setValue(setting.getDefaultValue());
                }
            }
            ModuleCommand.sendMessage(module.getDisplayName() + " settings reset.");
            return;
        }
        if (commands.length < 4 || !commands[1].equalsIgnoreCase("set")) {
            ModuleCommand.sendMessage("Usage: " + OyVey.commandManager.getPrefix() + "module <module> set <setting> <value> (or reset)");
            return;
        }
        if (commands.length != 4) {
            ModuleCommand.sendMessage("Values containing spaces must be quoted.");
            return;
        }
        Setting<?> setting = module.getSettingByName(commands[2]);
        if (setting == null) {
            ModuleCommand.sendMessage("Unknown setting '" + commands[2] + "'.");
            return;
        }
        String value = commands[3];
        try {
            if (setting.getName().equalsIgnoreCase("Enabled")) {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) throw new IllegalArgumentException();
                module.setEnabled(Boolean.parseBoolean(value));
            } else if (setting.getType().equalsIgnoreCase("String")) {
                ((Setting) setting).setValue(value);
            } else {
                ConfigManager.setValueFromJson(module, setting, JsonParser.parseString(value));
            }
        } catch (RuntimeException exception) {
            ModuleCommand.sendMessage("Bad value. This setting requires a " + setting.getType() + " value.");
            return;
        }
        ModuleCommand.sendMessage(module.getName() + " " + setting.getName() + " set to " + value + ".");
    }
}
