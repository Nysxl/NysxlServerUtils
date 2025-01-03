package org.Nysxl.CommandManager;

import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

public class CommandBuilder implements CommandExecutor, TabCompleter {
    private JavaPlugin plugin;
    private CommandManager commandManager;
    protected String name;
    private PlayerCommandExecutor playerExecutor;
    private ConsoleCommandExecutor consoleExecutor;
    private TabCompleter tabCompleter = (sender, command, alias, args) -> List.of();
    private Predicate<CommandContext> requirement = context -> true;
    private Consumer<CommandContext> onSucceed = context -> {};
    private Consumer<CommandContext> onFail = context -> {};
    private String permissionRequiredMessage = "You do not have permission to execute this command.";
    private String usageMessage = "Incorrect usage of the command.";
    private List<String> permissions = new ArrayList<>();
    private String playerErrorMessage = "Only players can use this command.";
    private String consoleErrorMessage = "This command cannot be executed from the console.";
    protected final List<SubCommandBuilder> subCommands = new ArrayList<>();
    public final List<Usage> usages = new ArrayList<>();
    private boolean debugEnabled = true; // Set debugEnabled to true for debugging
    private Sound successSound = null;
    private Sound failSound = null;
    protected int hierarchyLevel = 0; // Added hierarchyLevel

    static class Usage {
        private final int position;
        private final Class<?> type;
        private final Supplier<List<String>> tabComplete;
        private int hierarchyLevel = 0;
        private CommandBuilder relatedCommand;

        public Usage(int position, Class<?> type, Supplier<List<String>> tabComplete) {
            this.position = position;
            this.type = type;
            this.tabComplete = tabComplete;
        }

        public void setHierarchyLevel(int hierarchyLevel) {
            this.hierarchyLevel = hierarchyLevel;
        }

        public int getHierarchyLevel() {
            return hierarchyLevel;
        }

        public int getPosition() {
            return position;
        }

        public Class<?> getType() {
            return type;
        }

        public List<String> getTabComplete() {
            return tabComplete.get();
        }

        public void setRelatedCommand(CommandBuilder commandBuilder) {
            this.relatedCommand = commandBuilder;
        }

        public CommandBuilder getRelatedCommand() {
            return relatedCommand;
        }
    }

    public CommandBuilder(JavaPlugin plugin) {
        this.plugin = plugin;
        this.commandManager = new CommandManager(); // Instantiate CommandManager
    }

    public CommandBuilder setName(String name) {
        this.name = name;
        return this;
    }

    public CommandBuilder setPlayerExecutor(PlayerCommandExecutor playerExecutor) {
        this.playerExecutor = playerExecutor;
        return this;
    }

    public CommandBuilder setConsoleExecutor(ConsoleCommandExecutor consoleExecutor) {
        this.consoleExecutor = consoleExecutor;
        return this;
    }

    public CommandBuilder addSubCommand(SubCommandBuilder subCommand) {
        subCommand.setHierarchyLevel(this.hierarchyLevel + 1); // Set subcommand hierarchy level
        this.subCommands.add(subCommand);
        return this;
    }

    public CommandBuilder setRequirement(Predicate<CommandContext> requirement) {
        this.requirement = requirement;
        return this;
    }

    public CommandBuilder onSucceed(Consumer<CommandContext> onSucceed) {
        this.onSucceed = onSucceed;
        return this;
    }

    public CommandBuilder onFail(Consumer<CommandContext> onFail) {
        this.onFail = onFail;
        return this;
    }

    public CommandBuilder setPermissionRequiredMessage(String message) {
        this.permissionRequiredMessage = message;
        return this;
    }

    public CommandBuilder setUsageMessage(String message) {
        this.usageMessage = message;
        return this;
    }

    public CommandBuilder addPermission(String permission) {
        this.permissions.add(permission);
        return this;
    }

    public CommandBuilder usage(int position, Class<?> type, String tabComplete) {
        Usage usage = new Usage(position, type, () -> List.of(tabComplete));
        usage.setRelatedCommand(this);
        usage.setHierarchyLevel(this.hierarchyLevel + position + 1); // Set the hierarchy level for the usage
        usages.add(usage);
        return this;
    }

    public CommandBuilder usage(int position, Class<?> type, List<String> tabComplete) {
        Usage usage = new Usage(position, type, () -> tabComplete);
        usage.setHierarchyLevel(this.hierarchyLevel + position + 1); // Set the hierarchy level for the usage
        usages.add(usage);
        return this;
    }

    public CommandBuilder usage(int position, Class<?> type, Supplier<List<String>> tabComplete) {
        Usage usage = new Usage(position, type, tabComplete);
        usage.setHierarchyLevel(this.hierarchyLevel + position + 1); // Set the hierarchy level for the usage
        usages.add(usage);
        return this;
    }

    public CommandBuilder setTabCompleter(TabCompleter tabCompleter) {
        this.tabCompleter = tabCompleter;
        return this;
    }

    public CommandBuilder setPlayerErrorMessage(String message) {
        this.playerErrorMessage = message;
        return this;
    }

    public CommandBuilder setConsoleErrorMessage(String message) {
        this.consoleErrorMessage = message;
        return this;
    }

    public CommandBuilder setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
        return this;
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

    //getLastRecognizedCommand/SubCommand
    public CommandBuilder getLastRecognizedCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return this;
        }
        String subCommandName = args[0];
        for (SubCommandBuilder subCommand : subCommands) {
            if (subCommand.getName().equalsIgnoreCase(subCommandName)) {
                return subCommand.getLastRecognizedCommand(sender, Arrays.copyOfRange(args, 1, args.length));
            }
        }
        return this;
    }

    public Command build() {
        Command command = new Command(name) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (debugEnabled) {
                    plugin.getLogger().log(Level.INFO, "Executing command with context: " + Arrays.toString(args) + ", args length: " + args.length);
                }
                CommandContext context = new CommandContext(sender, label, Arrays.asList(args), new HashMap<>());
                CommandBuilder.this.execute(context);
                return true;
            }

            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                if (debugEnabled) {
                    plugin.getLogger().log(Level.INFO, "Tab completing with context: " + Arrays.toString(args) + ", args length: " + args.length);
                }
                return CommandBuilder.this.tabComplete(sender, this, alias, args);
            }
        };

        if (plugin.getCommand(name) != null) {
            plugin.getCommand(name).setExecutor(this);
            plugin.getCommand(name).setTabCompleter(this);
        } else {
            plugin.getLogger().log(Level.SEVERE, "Command not found in plugin.yml: " + name);
        }

        return command;
    }

    public void register() {
        // Automatically register the command when building
        commandManager.registerCommand(plugin, name, this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (debugEnabled) {
            plugin.getLogger().log(Level.INFO, "onCommand executed with context: " + Arrays.toString(args) + ", args length: " + args.length);
        }
        CommandContext context = new CommandContext(sender, label, Arrays.asList(args), new HashMap<>());
        execute(context);
        return true;
    }

    public void execute(CommandContext context) {
        CommandSender sender = context.getSender();

        if (debugEnabled) {
            plugin.getLogger().log(Level.INFO, "Executing command with context: " + context.toString());
        }

        List<String> args = new ArrayList<>(context.getArgs());

        if (args.isEmpty()) {
            sender.sendMessage(usageMessage);
            onFail.accept(context);
            if (debugEnabled) {
                plugin.getLogger().log(Level.INFO, "No arguments provided, showing usage message.");
            }
            return;
        }

        // Recursively execute subcommands
        executeSubCommand(sender, context, args, this);
    }

    private void executeSubCommand(CommandSender sender, CommandContext context, List<String> args, CommandBuilder commandBuilder) {
        if (debugEnabled) {
            plugin.getLogger().log(Level.INFO, "Executing subcommand with context: " + context.toString() + ", args: " + args.toString() + ", args length: " + args.size() + ", commandBuilder: " + commandBuilder.getName());
        }

        if (args.isEmpty()) {
            // Handle case where no subcommand is found and no more arguments are provided
            if (sender instanceof Player && commandBuilder.playerExecutor != null) {
                commandBuilder.playerExecutor.execute((Player) sender, context);
                commandBuilder.onSucceed.accept(context);
            } else if (commandBuilder.consoleExecutor != null) {
                commandBuilder.consoleExecutor.execute(sender, context);
                commandBuilder.onSucceed.accept(context);
            } else {
                sender.sendMessage(commandBuilder.usageMessage);
                commandBuilder.onFail.accept(context);
            }
            return;
        }

        String subCommandName = args.get(0);
        plugin.getLogger().log(Level.INFO, "Checking for subcommand: " + subCommandName + ", current args: " + args);
        for (SubCommandBuilder subCommand : commandBuilder.subCommands) {
            if (subCommand.getName().equalsIgnoreCase(subCommandName)) {
                if (commandBuilder.debugEnabled) {
                    plugin.getLogger().log(Level.INFO, "Found subcommand: " + subCommandName);
                }
                args.remove(0);
                CommandContext subCommandContext = new CommandContext(sender, context.getLabel(), args, context.getAdditionalData());
                executeSubCommand(sender, subCommandContext, args, subCommand);
                return;
            }
        }

        // If no subcommand is found, execute the main command logic
        if (sender instanceof Player) {
            if (commandBuilder.playerExecutor != null) {
                if (commandBuilder.debugEnabled) {
                    plugin.getLogger().log(Level.INFO, "Executing player command");
                }
                commandBuilder.playerExecutor.execute((Player) sender, context);
                commandBuilder.onSucceed.accept(context);
            } else {
                sender.sendMessage(commandBuilder.playerErrorMessage);
                commandBuilder.onFail.accept(context);
            }
        } else {
            if (commandBuilder.consoleExecutor != null) {
                if (commandBuilder.debugEnabled) {
                    plugin.getLogger().log(Level.INFO, "Executing console command");
                }
                commandBuilder.consoleExecutor.execute(sender, context);
                commandBuilder.onSucceed.accept(context);
            } else {
                sender.sendMessage(commandBuilder.consoleErrorMessage);
                commandBuilder.onFail.accept(context);
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (debugEnabled) {
            plugin.getLogger().log(Level.INFO, "onTabComplete executed with context: " + Arrays.toString(args) + ", args length: " + args.length);
        }
        return tabComplete(sender, command, alias, args);
    }

    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        CommandBuilder lastRecognizedCommand = getLastRecognizedCommand(sender, args);
        int currentLevel = lastRecognizedCommand.getHierarchyLevel() + 1;

        if (debugEnabled) {
            plugin.getLogger().log(Level.INFO, "Current hierarchy level: " + currentLevel + ", args length: " + args.length);
        }

        // Retrieve subcommands at the last recognized command
        for (SubCommandBuilder subCommand : lastRecognizedCommand.getSubCommands()) {
            completions.add(subCommand.getName());
        }

        // Retrieve usages at the last recognized command hierarchy level if the last recognized command matches the usage related command
        List<Usage> usagesAtLevel = lastRecognizedCommand.getUsageAtHierarchyLevel(currentLevel - 1);
        for (Usage usage : usagesAtLevel) {
            // Only add tab completions for usages whose position matches the current argument length
            if (currentLevel+usage.getPosition() == args.length) {
                completions.addAll(usage.getTabComplete());
            }
        }

        if (debugEnabled) {
            plugin.getLogger().log(Level.INFO, "Tab completions: " + completions);
        }
        return completions;
    }

    public void setHierarchyLevel(int hierarchyLevel) {
        this.hierarchyLevel = hierarchyLevel;
    }

    // Getter methods for private fields
    public JavaPlugin getPlugin() {
        return plugin;
    }

    public String getName() {
        return name;
    }

    public List<SubCommandBuilder> getSubCommands() {
        return subCommands;
    }

    public int getHierarchyLevel() {
        return hierarchyLevel;
    }
}