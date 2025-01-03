package org.Nysxl.CommandManager;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CommandManager {
    private final Map<String, CommandExecutor> commands = new HashMap<>();
    private final Map<String, TabCompleter> tabCompleters = new HashMap<>();

    // Registers a new command with the plugin
    public void registerCommand(JavaPlugin plugin, String commandName, CommandExecutor executor) {
        commands.put(commandName, executor);
        PluginCommand command = plugin.getCommand(commandName);
        if (command != null) {
            command.setExecutor(executor);
        }
    }

    // Registers a new command with a tab completer
    public void registerCommand(JavaPlugin plugin, String commandName, CommandExecutor executor, TabCompleter completer) {
        commands.put(commandName, executor);
        tabCompleters.put(commandName, completer);
        PluginCommand command = plugin.getCommand(commandName);
        if (command != null) {
            command.setExecutor(executor);
            command.setTabCompleter(completer);
        }
    }

    // Unregisters a command
    public void unregisterCommand(String commandName) {
        commands.remove(commandName);
        tabCompleters.remove(commandName);
    }

    // Gets the command executor for a specific command
    public CommandExecutor getCommandExecutor(String commandName) {
        return commands.get(commandName);
    }

    // Gets the tab completer for a specific command
    public TabCompleter getTabCompleter(String commandName) {
        return tabCompleters.get(commandName);
    }
}