package org.Nysxl.CommandManager;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class SubCommand implements CommandExecutor, TabCompleter {
    private final String name;
    private final CommandExecutor executor;
    private final List<SubCommand> subCommands;
    private final TabCompleter tabCompleter;
    private final Predicate<CommandContext> requirement;
    private final Consumer<CommandContext> onSucceed;
    private final Consumer<CommandContext> onFail;
    private final List<String> permissions;

    public SubCommand(String name, CommandExecutor executor, List<SubCommand> subCommands, TabCompleter tabCompleter, Predicate<CommandContext> requirement, Consumer<CommandContext> onSucceed, Consumer<CommandContext> onFail, List<String> permissions) {
        this.name = name;
        this.executor = executor;
        this.subCommands = subCommands;
        this.tabCompleter = tabCompleter;
        this.requirement = requirement;
        this.onSucceed = onSucceed;
        this.onFail = onFail;
        this.permissions = permissions;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CommandContext context = new CommandContext(sender, label, List.of(args), null);
        execute(context);
        return true;
    }

    public void execute(CommandContext context) {
        if (requirement.test(context)) {
            try {
                executor.onCommand(context.getSender(), null, context.getLabel(), context.getArgs().toArray(new String[0]));
                onSucceed.accept(context);
            } catch (Exception e) {
                onFail.accept(context);
                e.printStackTrace();
            }
        } else {
            onFail.accept(context);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        CommandContext context = new CommandContext(sender, alias, List.of(args), null);
        return tabComplete(context);
    }

    public List<String> tabComplete(CommandContext context) {
        if (context.getArgs().isEmpty()) {
            return tabCompleter.onTabComplete(context.getSender(), null, context.getLabel(), new String[0]);
        } else {
            String subCommandName = context.getArgs().get(0);
            for (SubCommand subCommand : subCommands) {
                if (subCommand.getName().equalsIgnoreCase(subCommandName)) {
                    return subCommand.tabComplete(new CommandContext(context.getSender(), subCommandName, context.getArgs().subList(1, context.getArgs().size()), null));
                }
            }
            return tabCompleter.onTabComplete(context.getSender(), null, context.getLabel(), new String[0]);
        }
    }

    public List<SubCommand> getSubCommands() {
        return subCommands;
    }

    public String getName() {
        return name;
    }
}