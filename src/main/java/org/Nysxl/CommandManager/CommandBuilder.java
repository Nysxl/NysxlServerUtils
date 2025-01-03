package org.Nysxl.CommandManager;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;

public class CommandBuilder {
    private String name; // Command name
    private CommandExecutor executor; // Command executor logic
    private TabCompleter tabCompleter = (sender, command, alias, args) -> List.of(); // Optional: Tab completer
    private Predicate<CommandContext> requirement = context -> true; // Optional: Requirement predicate
    private Consumer<CommandContext> onSucceed = context -> {}; // Optional: Action on success
    private Consumer<CommandContext> onFail = context -> {}; // Optional: Action on failure
    private String permissionRequiredMessage = "You do not have permission to execute this command."; // Optional: Permission required message
    private String usageMessage = "Incorrect usage of the command."; // Optional: Usage message
    private List<String> permissions = new ArrayList<>(); // Optional: List of permissions
    private boolean acceptsCmd = false; // Optional: Allow execution from console
    private final List<SubCommandBuilder> subCommands = new ArrayList<>();
    private final List<Usage> usages = new ArrayList<>();
    private JavaPlugin plugin;

    // Inner class to represent expected arguments in a usage
    private static class Usage {
        private final int position;
        private final Class<?> type;
        private final String tabComplete;

        public Usage(int position, Class<?> type, String tabComplete) {
            this.position = position;
            this.type = type;
            this.tabComplete = tabComplete;
        }

        public int getPosition() {
            return position;
        }

        public Class<?> getType() {
            return type;
        }

        public String getTabComplete() {
            return tabComplete;
        }
    }

    // Sets the name of the command
    public CommandBuilder setName(String name) {
        this.name = name;
        return this;
    }

    // Sets the executor logic for the command
    public CommandBuilder setExecutor(CommandExecutor executor) {
        this.executor = executor;
        return this;
    }

    // Adds a subcommand to this command
    public CommandBuilder addSubCommand(SubCommandBuilder subCommand) {
        this.subCommands.add(subCommand);
        return this;
    }

    // Sets a requirement predicate that must be met to execute the command (Optional)
    public CommandBuilder setRequirement(Predicate<CommandContext> requirement) {
        this.requirement = requirement;
        return this;
    }

    // Sets the action to perform when the command succeeds (Optional)
    public CommandBuilder onSucceed(Consumer<CommandContext> onSucceed) {
        this.onSucceed = onSucceed;
        return this;
    }

    // Sets the action to perform when the command fails (Optional)
    public CommandBuilder onFail(Consumer<CommandContext> onFail) {
        this.onFail = onFail;
        return this;
    }

    // Sets the message to display when the user lacks permission (Optional)
    public CommandBuilder setPermissionRequiredMessage(String message) {
        this.permissionRequiredMessage = message;
        return this;
    }

    // Sets the usage message for the command (Optional)
    public CommandBuilder setUsageMessage(String message) {
        this.usageMessage = message;
        return this;
    }

    // Adds a permission required to execute the command (Optional)
    public CommandBuilder addPermission(String permission) {
        this.permissions.add(permission);
        return this;
    }

    // Allows the command to be executed from the console (Optional)
    public CommandBuilder acceptsCmd() {
        this.acceptsCmd = true;
        return this;
    }

    // Sets expected arguments for the command
    public CommandBuilder usage(int position, Class<?> type, String tabComplete) {
        usages.add(new Usage(position, type, tabComplete));
        return this;
    }

    // Sets the tab completer for the command
    public CommandBuilder setTabCompleter(TabCompleter tabCompleter) {
        this.tabCompleter = tabCompleter;
        return this;
    }

    // Builds and returns the command
    public Command build() {
        Command command = new Command(name) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                CommandContext context = new CommandContext(sender, label, Arrays.asList(args), new HashMap<>());
                CommandBuilder.this.execute(context);
                return true;
            }

            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                return CommandBuilder.this.tabComplete(sender, this, alias, args);
            }
        };

        // Register the command with the plugin
        plugin.getCommand(name).setExecutor((CommandExecutor) command);
        plugin.getCommand(name).setTabCompleter((TabCompleter) command);

        // Register the command in the plugin.yml file
        registerCommandInPluginYml();

        return command;
    }

    // Executes the command with the given context
    public void execute(CommandContext context) {
        CommandSender sender = context.getSender();

        // Check if sender is a player unless command accepts console
        if (!acceptsCmd && !(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return;
        }

        // Check permissions
        for (String permission : permissions) {
            if (!sender.hasPermission(permission)) {
                sender.sendMessage(permissionRequiredMessage);
                onFail.accept(context);
                return;
            }
        }

        // Validate and parse arguments based on usages
        for (Usage usage : usages) {
            int position = usage.getPosition();
            if (context.getArgs().size() < position + 1) {
                sender.sendMessage(usageMessage);
                onFail.accept(context);
                return;
            }
            try {
                String arg = context.getArgs().get(position);
                if (usage.getType() == Double.class) {
                    Double.parseDouble(arg);
                } else if (usage.getType() == Integer.class) {
                    Integer.parseInt(arg);
                } else if (usage.getType() == String.class) {
                    // No parsing needed for strings
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(usageMessage);
                onFail.accept(context);
                return;
            }
        }

        // Execute subcommands if any
        if (!context.getArgs().isEmpty()) {
            String subCommandName = context.getArgs().get(0);
            for (SubCommandBuilder subCommand : subCommands) {
                if (subCommand.getName().equalsIgnoreCase(subCommandName)) {
                    subCommand.execute(new CommandContext(
                            sender, subCommandName, context.getArgs().subList(1, context.getArgs().size()), context.getAdditionalData()));
                    return;
                }
            }
        }

        // Execute the main command if no subcommand matches
        if (requirement.test(context)) {
            try {
                executor.onCommand(sender, null, context.getLabel(), context.getArgs().toArray(new String[0]));
                onSucceed.accept(context);
            } catch (Exception e) {
                onFail.accept(context);
                e.printStackTrace();
            }
        } else {
            sender.sendMessage(permissionRequiredMessage);
            onFail.accept(context);
        }
    }

    // Provides tab completion suggestions based on the context
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        CommandContext context = new CommandContext(sender, alias, Arrays.asList(args), new HashMap<>());
        List<String> completions = new ArrayList<>();
        if (context.getArgs().isEmpty()) {
            completions.addAll(tabCompleter.onTabComplete(sender, command, alias, args));
        } else {
            String subCommandName = context.getArgs().get(0);
            for (SubCommandBuilder subCommand : subCommands) {
                if (subCommand.getName().equalsIgnoreCase(subCommandName)) {
                    completions.addAll(subCommand.tabComplete(sender, command, subCommandName, args));
                    return completions;
                }
            }
            // Include argument tab completions
            for (Usage usage : usages) {
                if (context.getArgs().size() == usage.getPosition() + 1) {
                    completions.add(usage.getTabComplete());
                }
            }
        }
        return completions;
    }

    // Automatically configure the command in the plugin
    public void register(JavaPlugin plugin) {
        this.plugin = plugin;
        Command command = build();
        plugin.getCommand(name).setExecutor((CommandExecutor) command);
        plugin.getCommand(name).setTabCompleter((TabCompleter) command);
    }

    // Register the command in the plugin.yml file
    private void registerCommandInPluginYml() {
        File pluginYml = new File(plugin.getDataFolder(), "plugin.yml");
        try (FileWriter writer = new FileWriter(pluginYml, true)) {
            writer.write("\ncommands:\n");
            writer.write("  " + name + ":\n");
            writer.write("    description: " + usageMessage + "\n");
            writer.write("    usage: " + usageMessage + "\n");
            if (!permissions.isEmpty()) {
                writer.write("    permission: " + String.join(",", permissions) + "\n");
                writer.write("    permission-message: " + permissionRequiredMessage + "\n");
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to register command in plugin.yml", e);
        }
    }

    // Getter methods for all fields
    public String getName() {
        return name;
    }

    public CommandExecutor getExecutor() {
        return executor;
    }

    public TabCompleter getTabCompleter() {
        return tabCompleter;
    }

    public Predicate<CommandContext> getRequirement() {
        return requirement;
    }

    public Consumer<CommandContext> getOnSucceed() {
        return onSucceed;
    }

    public Consumer<CommandContext> getOnFail() {
        return onFail;
    }

    public String getPermissionRequiredMessage() {
        return permissionRequiredMessage;
    }

    public String getUsageMessage() {
        return usageMessage;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public boolean isAcceptsCmd() {
        return acceptsCmd;
    }

    public List<SubCommandBuilder> getSubCommands() {
        return subCommands;
    }

    public List<Usage> getUsages() {
        return usages;
    }
}