package org.Nysxl.CommandManager;

import org.bukkit.command.Command;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

public class SubCommandBuilder extends CommandBuilder {
    private final CommandBuilder parentBuilder;

    public SubCommandBuilder(CommandBuilder parentBuilder) {
        super(parentBuilder.getPlugin()); // Pass only the plugin to the super constructor
        this.parentBuilder = parentBuilder;
        this.hierarchyLevel = parentBuilder.getHierarchyLevel() + 1; // Subcommand level is parent level + 1
    }

    public SubCommandBuilder setName(String name) {
        super.setName(name);
        return this;
    }

    public SubCommandBuilder setPlayerExecutor(PlayerCommandExecutor playerExecutor) {
        super.setPlayerExecutor(playerExecutor);
        return this;
    }

    public SubCommandBuilder setConsoleExecutor(ConsoleCommandExecutor consoleExecutor) {
        super.setConsoleExecutor(consoleExecutor);
        return this;
    }

    public SubCommandBuilder setTabCompleter(TabCompleter tabCompleter) {
        super.setTabCompleter(tabCompleter);
        return this;
    }

    public SubCommandBuilder setRequirement(Predicate<CommandContext> requirement) {
        super.setRequirement(requirement);
        return this;
    }

    public SubCommandBuilder onSucceed(Consumer<CommandContext> onSucceed) {
        super.onSucceed(onSucceed);
        return this;
    }

    public SubCommandBuilder onFail(Consumer<CommandContext> onFail) {
        super.onFail(onFail);
        return this;
    }

    public SubCommandBuilder setPermissionRequiredMessage(String message) {
        super.setPermissionRequiredMessage(message);
        return this;
    }

    public SubCommandBuilder setUsageMessage(String message) {
        super.setUsageMessage(message);
        return this;
    }

    public SubCommandBuilder addPermission(String permission) {
        super.addPermission(permission);
        return this;
    }

    public SubCommandBuilder usage(int position, Class<?> type, String tabComplete) {
        Usage usage = new Usage(position, type, () -> List.of(tabComplete));
        usage.setHierarchyLevel(this.hierarchyLevel + position + 1); // Set the hierarchy level for the usage
        usages.add(usage);
        return this;
    }

    public SubCommandBuilder usage(int position, Class<?> type, List<String> tabComplete) {
        Usage usage = new Usage(position, type, () -> tabComplete);
        usage.setHierarchyLevel(this.hierarchyLevel + position + 1); // Set the hierarchy level for the usage
        usages.add(usage);
        return this;
    }

    public SubCommandBuilder usage(int position, Class<?> type, Supplier<List<String>> tabComplete) {
        Usage usage = new Usage(position, type, tabComplete);
        usage.setHierarchyLevel(this.hierarchyLevel + position + 1); // Set the hierarchy level for the usage
        usages.add(usage);
        return this;
    }

    public CommandBuilder endSubCommand() {
        parentBuilder.addSubCommand(this);
        return parentBuilder;
    }

    public void register(JavaPlugin plugin, int level) {
        this.hierarchyLevel = level;
        Command command = build();
        if (plugin.getCommand(name) != null) {
            plugin.getCommand(name).setExecutor(this);
            plugin.getCommand(name).setTabCompleter(this);
        } else {
            plugin.getLogger().log(Level.SEVERE, "Command not found in plugin.yml: " + name);
        }
        // Register subcommands recursively
        for (SubCommandBuilder subCommand : subCommands) {
            subCommand.register(plugin, level + 1); // Increment level for nested subcommands
        }
    }

    public List<CommandBuilder> getCommandsAtHierarchyLevel(int hierarchyLevel) {
        List<CommandBuilder> commands = new ArrayList<>();
        if (this.hierarchyLevel == hierarchyLevel) {
            commands.add(this);
        }
        for (SubCommandBuilder subCommand : subCommands) {
            commands.addAll(subCommand.getCommandsAtHierarchyLevel(hierarchyLevel));
        }
        return commands;
    }

    public List<Usage> getUsageAtHierarchyLevel(int hierarchyLevel) {
        List<Usage> usagesAtLevel = new ArrayList<>();
        if (this.hierarchyLevel == hierarchyLevel) {
            usagesAtLevel.addAll(this.usages);
        }
        for (SubCommandBuilder subCommand : subCommands) {
            usagesAtLevel.addAll(subCommand.getUsageAtHierarchyLevel(hierarchyLevel));
        }
        return usagesAtLevel;
    }
}