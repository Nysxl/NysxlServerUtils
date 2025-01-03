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
import java.util.logging.Level;

public class CommandBuilder implements CommandExecutor, TabCompleter {
    private JavaPlugin plugin;
    private String name;
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
    private final List<SubCommandBuilder> subCommands = new ArrayList<>();
    private final List<Usage> usages = new ArrayList<>();
    private boolean debugEnabled = false;
    private Sound successSound = null;
    private Sound failSound = null;

    static class Usage {
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

    public CommandBuilder(JavaPlugin plugin) {
        this.plugin = plugin;
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
        usages.add(new Usage(position, type, tabComplete));
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

    public CommandBuilder setSuccessSound(Sound successSound) {
        this.successSound = successSound;
        return this;
    }

    public CommandBuilder setFailSound(Sound failSound) {
        this.failSound = failSound;
        return this;
    }

    public Command build() {
        Command command = new Command(name) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (debugEnabled) {
                    plugin.getLogger().log(Level.INFO, "Executing command with context: " + Arrays.toString(args));
                }
                CommandContext context = new CommandContext(sender, label, Arrays.asList(args), new HashMap<>());
                CommandBuilder.this.execute(context);
                return true;
            }

            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                if (debugEnabled) {
                    plugin.getLogger().log(Level.INFO, "Tab completing with context: " + Arrays.toString(args));
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

    public void execute(CommandContext context) {
        CommandSender sender = context.getSender();

        if (debugEnabled) {
            plugin.getLogger().log(Level.INFO, "Executing command with context: " + context.toString());
        }

        List<String> args = new ArrayList<>(context.getArgs());

        if (args.isEmpty()) {
            sender.sendMessage(usageMessage);
            onFail.accept(context);
            playFailSound(sender);
            if (debugEnabled) {
                plugin.getLogger().log(Level.INFO, "No arguments provided, showing usage message.");
            }
            return;
        }

        // Recursively execute subcommands
        executeSubCommand(sender, context, args, this);
    }

    private void executeSubCommand(CommandSender sender, CommandContext context, List<String> args, CommandBuilder commandBuilder) {
        if (args.isEmpty()) {
            // Handle case where no subcommand is found and no more arguments are provided
            if (sender instanceof Player && commandBuilder.playerExecutor != null) {
                commandBuilder.playerExecutor.execute((Player) sender, context);
                commandBuilder.onSucceed.accept(context);
                playSuccessSound(sender);
            } else if (commandBuilder.consoleExecutor != null) {
                commandBuilder.consoleExecutor.execute(sender, context);
                commandBuilder.onSucceed.accept(context);
            } else {
                sender.sendMessage(commandBuilder.usageMessage);
                commandBuilder.onFail.accept(context);
                playFailSound(sender);
            }
            return;
        }

        String subCommandName = args.get(0);
        for (SubCommandBuilder subCommand : commandBuilder.subCommands) {
            if (subCommand.getName().equalsIgnoreCase(subCommandName)) {
                if (commandBuilder.debugEnabled) {
                    plugin.getLogger().log(Level.INFO, "Executing subcommand: " + subCommandName);
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
                playSuccessSound(sender);
            } else {
                sender.sendMessage(commandBuilder.playerErrorMessage);
                commandBuilder.onFail.accept(context);
                playFailSound(sender);
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
                playFailSound(sender);
            }
        }
    }

    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        int currentLevel = args.length; // Determine current level based on argument length

        if (currentLevel == 1) {
            for (SubCommandBuilder subCommand : subCommands) {
                if (subCommand.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand.getName());
                }
            }
            if (completions.isEmpty()) {
                for (Usage usage : usages) {
                    completions.add(usage.getTabComplete());
                }
            }
        } else {
            String subCommandName = args[0];
            for (SubCommandBuilder subCommand : subCommands) {
                if (subCommand.getName().equalsIgnoreCase(subCommandName)) {
                    List<String> subCompletions = subCommand.tabComplete(sender, command, alias, Arrays.copyOfRange(args, 1, args.length));
                    if (!subCompletions.isEmpty()) {
                        completions.addAll(subCompletions);
                    } else {
                        for (Usage usage : usages) {
                            if (args.length == usage.getPosition() + 1) {
                                completions.add(usage.getTabComplete());
                            }
                        }
                    }
                    return completions;
                }
            }
        }
        if (debugEnabled) {
            plugin.getLogger().log(Level.INFO, "Tab completions: " + completions);
        }
        return completions;
    }

    public void register(JavaPlugin plugin) {
        this.plugin = plugin;
        Command command = build();
        if (plugin.getCommand(name) != null) {
            plugin.getCommand(name).setExecutor(this);
            plugin.getCommand(name).setTabCompleter(this);
        } else {
            plugin.getLogger().log(Level.SEVERE, "Command not found in plugin.yml: " + name);
        }
    }

    public String getName() {
        return name;
    }

    public PlayerCommandExecutor getPlayerExecutor() {
        return playerExecutor;
    }

    public ConsoleCommandExecutor getConsoleExecutor() {
        return consoleExecutor;
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

    public String getPlayerErrorMessage() {
        return playerErrorMessage;
    }

    public String getConsoleErrorMessage() {
        return consoleErrorMessage;
    }

    public List<SubCommandBuilder> getSubCommands() {
        return subCommands;
    }

    public List<Usage> getUsages() {
        return usages;
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CommandContext context = new CommandContext(sender, label, Arrays.asList(args), new HashMap<>());
        if (debugEnabled) {
            plugin.getLogger().log(Level.INFO, "onCommand called with args: " + Arrays.toString(args));
        }
        execute(context);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (debugEnabled) {
            plugin.getLogger().log(Level.INFO, "onTabComplete called with args: " + Arrays.toString(args));
        }
        return tabComplete(sender, command, alias, args);
    }

    private void playSuccessSound(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            player.playSound(player.getLocation(), successSound, 1.0f, 1.0f);
        }
    }

    private void playFailSound(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            player.playSound(player.getLocation(), failSound, 1.0f, 1.0f);
        }
    }
}