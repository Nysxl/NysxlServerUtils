package org.Nysxl.CommandManager;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;

public class SubCommandBuilder extends CommandBuilder {
    private final CommandBuilder parentBuilder;

    public SubCommandBuilder(CommandBuilder parentBuilder) {
        super(parentBuilder.getPlugin());
        this.parentBuilder = parentBuilder;
    }

    @Override
    public SubCommandBuilder setName(String name) {
        super.setName(name);
        return this;
    }

    @Override
    public SubCommandBuilder setPlayerExecutor(PlayerCommandExecutor playerExecutor) {
        super.setPlayerExecutor(playerExecutor);
        return this;
    }

    @Override
    public SubCommandBuilder setConsoleExecutor(ConsoleCommandExecutor consoleExecutor) {
        super.setConsoleExecutor(consoleExecutor);
        return this;
    }

    @Override
    public SubCommandBuilder setTabCompleter(TabCompleter tabCompleter) {
        super.setTabCompleter(tabCompleter);
        return this;
    }

    @Override
    public SubCommandBuilder setRequirement(Predicate<CommandContext> requirement) {
        super.setRequirement(requirement);
        return this;
    }

    @Override
    public SubCommandBuilder onSucceed(Consumer<CommandContext> onSucceed) {
        super.onSucceed(onSucceed);
        return this;
    }

    @Override
    public SubCommandBuilder onFail(Consumer<CommandContext> onFail) {
        super.onFail(onFail);
        return this;
    }

    @Override
    public SubCommandBuilder setPermissionRequiredMessage(String message) {
        super.setPermissionRequiredMessage(message);
        return this;
    }

    @Override
    public SubCommandBuilder setUsageMessage(String message) {
        super.setUsageMessage(message);
        return this;
    }

    @Override
    public SubCommandBuilder addPermission(String permission) {
        super.addPermission(permission);
        return this;
    }

    @Override
    public SubCommandBuilder usage(int position, Class<?> type, String tabComplete) {
        super.usage(position, type, tabComplete);
        return this;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            // Provide subcommands as suggestions at the first level within the subcommand
            for (SubCommandBuilder subCommand : getSubCommands()) {
                if (subCommand.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(subCommand.getName());
                }
            }
        } else {
            String subCommandName = args[0];
            for (SubCommandBuilder subCommand : getSubCommands()) {
                if (subCommand.getName().equalsIgnoreCase(subCommandName)) {
                    // Handle tab completion for nested subcommands recursively
                    List<String> subCompletions = subCommand.tabComplete(sender, command, alias, Arrays.copyOfRange(args, 1, args.length));
                    completions.addAll(subCompletions);
                    return completions;
                }
            }
        }

        // Handle usage completions based on the current argument level
        for (Usage usage : getUsages()) {
            if (args.length == usage.getPosition() + 1) {
                completions.add(usage.getTabComplete());
            }
        }

        return completions;
    }

    @Override
    public void execute(CommandContext context) {
        CommandSender sender = context.getSender();
        List<String> args = new ArrayList<>(context.getArgs());

        if (sender instanceof Player) {
            if (getPlayerExecutor() != null) {
                if (true) {
                    getPlugin().getLogger().log(Level.INFO, "Executing player command in subcommand: " + getName());
                }
                getPlayerExecutor().execute((Player) sender, context);
                getOnSucceed().accept(context);
            } else {
                sender.sendMessage(getPlayerErrorMessage());
                getOnFail().accept(context);
            }
        } else {
            if (getConsoleExecutor() != null) {
                if (true) {
                    getPlugin().getLogger().log(Level.INFO, "Executing console command in subcommand: " + getName());
                }
                getConsoleExecutor().execute(sender, context);
                getOnSucceed().accept(context);
            } else {
                sender.sendMessage(getConsoleErrorMessage());
                getOnFail().accept(context);
            }
        }
    }

    public CommandBuilder endSubCommand() {
        parentBuilder.addSubCommand(this);
        return parentBuilder;
    }

    public String getName() {
        return super.getName();
    }
}