package org.Nysxl.CommandManager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A robust CommandRegistry for Bukkit/Spigot/Paper plugins,
 * with onSuccess/onFail logic, subcommands, checks, cooldowns, etc.
 *
 * Additional utility methods included for:
 *  - Spawning custom mobs (with a Consumer<Entity> to set custom meta)
 *  - Spawning/dropping items on success/fail
 *  - Playing sounds
 *  - Placing blocks
 *  - Running commands as console
 *  - Temporarily running logic as OP
 */
public class CommandRegistry {

    private final JavaPlugin plugin;

    public CommandRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Create a CommandBuilder for a command declared in plugin.yml.
     */
    public CommandBuilder createCommand(String commandName) {
        PluginCommand command = plugin.getCommand(commandName);
        if (command == null) {
            throw new IllegalArgumentException(
                    "Command '" + commandName + "' is not registered in plugin.yml."
            );
        }
        return new CommandBuilder(plugin, command);
    }

    // ------------------------------------------------------------------------
    // CommandBuilder
    // ------------------------------------------------------------------------
    public static class CommandBuilder {
        private final PluginCommand command;
        private final JavaPlugin plugin;

        // Requirements
        private boolean requirePlayer = false;
        private String requiredPermission = null;

        // Checks
        private final List<Predicate<CommandSender>> checks = new ArrayList<>();
        private final List<CommandCheckWithArgs> checksWithArgs = new ArrayList<>();

        // Subcommands
        private final Map<String, SubCommandHandler> subCommands = new HashMap<>();
        private final Map<String, String> subCommandDescriptions = new HashMap<>();
        private final Map<String, String> subCommandAliases = new HashMap<>();

        // Logic hooks
        private BiConsumer<CommandSender, String[]> preExecute = (sender, args) -> {};
        private BiConsumer<CommandSender, String[]> postExecute = (sender, args) -> {};

        // Execution logic
        private BiConsumer<CommandSender, List<Object>> defaultLogic = null;
        private BiConsumer<CommandSender, String> fallbackLogic = (sender, unknownSub) ->
                sender.sendMessage(ChatColor.RED + "Unknown subcommand: " + unknownSub);

        // For success/fail
        private final List<BiConsumer<CommandSender, String[]>> postSuccessActions = new ArrayList<>();
        private final List<BiConsumer<CommandSender, String[]>> postFailActions = new ArrayList<>();

        // Cooldown
        private long cooldownTime = 0;
        private final Map<String, Long> cooldowns = new HashMap<>();

        // Messages
        private String noPermissionMessage = ChatColor.RED + "You do not have permission to execute this command.";
        private String playerOnlyMessage = ChatColor.RED + "This command can only be executed by players.";
        private String invalidArgumentsMessage = ChatColor.RED + "Invalid arguments. Please check the command usage.";

        // --------------------------------------------------------------------
        // Constructor
        // --------------------------------------------------------------------
        public CommandBuilder(JavaPlugin plugin, PluginCommand command) {
            this.plugin = plugin;
            this.command = command;
        }

        // --------------------------------------------------------------------
        // Basic Configuration
        // --------------------------------------------------------------------
        public CommandBuilder requirePlayer() {
            this.requirePlayer = true;
            return this;
        }

        public CommandBuilder withPermission(String permission) {
            this.requiredPermission = permission;
            return this;
        }

        public CommandBuilder setNoPermissionMessage(String message) {
            this.noPermissionMessage = message;
            return this;
        }

        public CommandBuilder setPlayerOnlyMessage(String message) {
            this.playerOnlyMessage = message;
            return this;
        }

        public CommandBuilder setInvalidArgumentsMessage(String message) {
            this.invalidArgumentsMessage = message;
            return this;
        }

        public CommandBuilder withCooldown(long seconds) {
            this.cooldownTime = seconds * 1000;
            return this;
        }

        // --------------------------------------------------------------------
        // Hook Methods
        // --------------------------------------------------------------------
        public CommandBuilder onPreExecute(BiConsumer<CommandSender, String[]> hook) {
            this.preExecute = hook;
            return this;
        }

        public CommandBuilder onPostExecute(BiConsumer<CommandSender, String[]> hook) {
            this.postExecute = hook;
            return this;
        }

        // --------------------------------------------------------------------
        // Success/Fail handling
        // --------------------------------------------------------------------
        public CommandBuilder onSuccess(BiConsumer<CommandSender, String[]> action) {
            this.postSuccessActions.add(action);
            return this;
        }

        public CommandBuilder onFail(BiConsumer<CommandSender, String[]> action) {
            this.postFailActions.add(action);
            return this;
        }

        // --------------------------------------------------------------------
        // Default & Fallback Logic
        // --------------------------------------------------------------------
        public CommandBuilder onFallback(BiConsumer<CommandSender, String> logic) {
            this.fallbackLogic = logic;
            return this;
        }

        public CommandBuilder onDefault(BiConsumer<CommandSender, List<Object>> logic) {
            this.defaultLogic = logic;
            return this;
        }

        public CommandBuilder onExecute(BiConsumer<CommandSender, String[]> logic) {
            this.defaultLogic = (sender, parsedArgs) ->
                    logic.accept(sender, parsedArgs.toArray(new String[0]));
            return this;
        }

        // --------------------------------------------------------------------
        // Checking Methods
        // --------------------------------------------------------------------
        public CommandBuilder check(Predicate<CommandSender> condition, String failureMessage) {
            checks.add(sender -> {
                if (!condition.test(sender)) {
                    sender.sendMessage(failureMessage);
                    return false;
                }
                return true;
            });
            return this;
        }

        @FunctionalInterface
        public interface CommandCheckWithArgs {
            boolean check(CommandSender sender, String[] args);
        }

        public CommandBuilder checkWithArgs(Predicate<String[]> condition, String failureMessage) {
            checksWithArgs.add((sender, realArgs) -> {
                if (!condition.test(realArgs)) {
                    sender.sendMessage(failureMessage);
                    return false;
                }
                return true;
            });
            return this;
        }

        public CommandBuilder requireArgs(int minArgs, int maxArgs, String failureMessage) {
            return checkWithArgs(args -> args.length >= minArgs && args.length <= maxArgs, failureMessage);
        }

        public CommandBuilder requireArgs(int numArgs, String failureMessage) {
            return requireArgs(numArgs, numArgs, failureMessage);
        }

        public CommandBuilder requireArgs(int numArgs) {
            return requireArgs(numArgs, "Invalid number of arguments.");
        }

        public CommandBuilder checkWithArgs(BiConsumer<CommandSender, String[]> logic, String failureMessage) {
            checksWithArgs.add((sender, realArgs) -> {
                try {
                    logic.accept(sender, realArgs);
                    return true;
                } catch (Exception e) {
                    sender.sendMessage(failureMessage + ": " + e.getMessage());
                    return false;
                }
            });
            return this;
        }

        public CommandBuilder checks(boolean condition, String failureMessage) {
            checks.add(sender -> {
                if (!condition) {
                    sender.sendMessage(failureMessage);
                    return false;
                }
                return true;
            });
            return this;
        }

        // --------------------------------------------------------------------
        // Subcommand Methods
        // --------------------------------------------------------------------
        public CommandBuilder onSubCommand(String subCommand, SubCommandHandler handler) {
            subCommands.put(subCommand.toLowerCase(), handler);
            if (!handler.getDescription().isEmpty()) {
                subCommandDescriptions.put(subCommand.toLowerCase(), handler.getDescription());
            }
            return this;
        }

        public CommandBuilder describeSubCommand(String subCommand, String description) {
            subCommandDescriptions.put(subCommand.toLowerCase(), description);
            return this;
        }

        public CommandBuilder addAlias(String subCommand, String... aliases) {
            for (String alias : aliases) {
                subCommandAliases.put(alias.toLowerCase(), subCommand.toLowerCase());
            }
            return this;
        }

        public CommandBuilder enableHelp() {
            onSubCommand("help", new SubCommandHandler(
                    Collections.emptyList(),
                    (sender, parsedArgs) -> {
                        sender.sendMessage(ChatColor.GOLD + "Available Commands:");

                        // Collect all command help info including nested subcommands
                        Map<String, String> allCommands = new HashMap<>();
                        subCommands.forEach((name, handler) -> {
                            allCommands.putAll(handler.getHelpInfo("/" + command.getName() + " " + name));
                        });

                        // Display all commands with their descriptions
                        allCommands.forEach((cmd, desc) -> {
                            sender.sendMessage(ChatColor.YELLOW + cmd + ": " + desc);
                        });
                    }
            ).withDescription("Shows this help message"));
            return this;
        }

        // --------------------------------------------------------------------
        // Register the Command
        // --------------------------------------------------------------------
        public void register() {
            command.setExecutor((sender, cmd, label, args) -> {
                boolean success = false;

                // 1) If it must be a player but isn't
                if (requirePlayer && !(sender instanceof Player)) {
                    sender.sendMessage(playerOnlyMessage);
                    runFail(sender, args);
                    runPostExecute(sender, args);
                    return true;
                }

                // 2) Permission check for base command
                if (requiredPermission != null && !sender.hasPermission(requiredPermission)) {
                    sender.sendMessage(noPermissionMessage);
                    runFail(sender, args);
                    runPostExecute(sender, args);
                    return true;
                }

                // 3) Cooldown check (only applies to players)
                if (cooldownTime > 0 && sender instanceof Player player) {
                    long now = System.currentTimeMillis();
                    long lastUsed = cooldowns.getOrDefault(player.getName(), 0L);
                    if (now - lastUsed < cooldownTime) {
                        long remaining = (cooldownTime - (now - lastUsed)) / 1000;
                        player.sendMessage(ChatColor.RED + "Wait " + remaining + " seconds to reuse this command.");
                        runFail(sender, args);
                        runPostExecute(sender, args);
                        return true;
                    }
                    cooldowns.put(player.getName(), now);
                }

                // 4) Checks (no args)
                for (Predicate<CommandSender> check : checks) {
                    if (!check.test(sender)) {
                        runFail(sender, args);
                        runPostExecute(sender, args);
                        return true;
                    }
                }

                // 5) Checks (with args)
                for (CommandCheckWithArgs check : checksWithArgs) {
                    if (!check.check(sender, args)) {
                        runFail(sender, args);
                        runPostExecute(sender, args);
                        return true;
                    }
                }

                // 6) Pre-execute hook
                preExecute.accept(sender, args);

                // 7) Main logic
                try {
                    // If subcommands are defined, interpret args[0] as subcommand
                    if (!subCommands.isEmpty()) {
                        if (args.length > 0) {
                            String sub = args[0].toLowerCase();
                            String primary = subCommandAliases.getOrDefault(sub, sub);
                            SubCommandHandler handler = subCommands.get(primary);

                            if (handler != null) {
                                // recognized subcommand
                                handler.handle(sender, Arrays.copyOfRange(args, 1, args.length));
                                success = true;
                            } else {
                                fallbackLogic.accept(sender, args[0]);
                            }
                        } else {
                            // no arguments => possibly run default logic
                            if (defaultLogic != null) {
                                defaultLogic.accept(sender, Arrays.asList(args));
                                success = true;
                            } else {
                                sender.sendMessage(invalidArgumentsMessage);
                            }
                        }
                    } else {
                        // No subcommands => treat all arguments as part of default logic
                        if (defaultLogic != null) {
                            defaultLogic.accept(sender, Arrays.asList(args));
                            success = true;
                        } else {
                            sender.sendMessage(invalidArgumentsMessage);
                        }
                    }
                } catch (IllegalArgumentException ex) {
                    sender.sendMessage(invalidArgumentsMessage + ": " + ex.getMessage());
                }

                // 8) Success or fail
                if (success) {
                    runSuccess(sender, args);
                } else {
                    runFail(sender, args);
                }

                // 9) Post-execute hook
                runPostExecute(sender, args);

                return true;
            });

            plugin.getLogger().info("Command '" + command.getName() + "' registered successfully.");
        }

        // --------------------------------------------------------------------
        // Private helper methods to unify success/fail calls
        // --------------------------------------------------------------------
        private void runSuccess(CommandSender sender, String[] args) {
            for (BiConsumer<CommandSender, String[]> action : postSuccessActions) {
                action.accept(sender, args);
            }
        }

        private void runFail(CommandSender sender, String[] args) {
            for (BiConsumer<CommandSender, String[]> action : postFailActions) {
                action.accept(sender, args);
            }
        }

        private void runPostExecute(CommandSender sender, String[] args) {
            postExecute.accept(sender, args);
        }

        // --------------------------------------------------------------------
        // Utility Methods
        // --------------------------------------------------------------------
        public CommandBuilder spawnMobOnSuccess(EntityType type, int amount) {
            return onSuccess((sender, args) -> {
                if (sender instanceof Player player) {
                    for (int i = 0; i < amount; i++) {
                        player.getWorld().spawnEntity(player.getLocation(), type);
                    }
                }
            });
        }

        public CommandBuilder spawnMobOnFail(EntityType type, int amount) {
            return

                    onFail((sender, args) -> {
                        if (sender instanceof Player player) {
                            for (int i = 0; i < amount; i++) {
                                player.getWorld().spawnEntity(player.getLocation(), type);
                            }
                        }
                    });
        }

        public CommandBuilder spawnCustomMobOnSuccess(EntityType type, int amount, Consumer<Entity> customizer) {
            return onSuccess((sender, args) -> {
                if (sender instanceof Player player) {
                    for (int i = 0; i < amount; i++) {
                        Entity entity = player.getWorld().spawnEntity(player.getLocation(), type);
                        customizer.accept(entity);
                    }
                }
            });
        }

        public CommandBuilder spawnCustomMobOnFail(EntityType type, int amount, Consumer<Entity> customizer) {
            return onFail((sender, args) -> {
                if (sender instanceof Player player) {
                    for (int i = 0; i < amount; i++) {
                        Entity entity = player.getWorld().spawnEntity(player.getLocation(), type);
                        customizer.accept(entity);
                    }
                }
            });
        }

        public CommandBuilder placeBlockOnSuccess(Material material, int offsetY) {
            return onSuccess((sender, args) -> {
                if (sender instanceof Player player) {
                    player.getLocation().add(0, offsetY, 0).getBlock().setType(material);
                }
            });
        }

        public CommandBuilder placeBlockOnFail(Material material, int offsetY) {
            return onFail((sender, args) -> {
                if (sender instanceof Player player) {
                    player.getLocation().add(0, offsetY, 0).getBlock().setType(material);
                }
            });
        }

        public CommandBuilder playSoundOnSuccess(Sound sound, float volume, float pitch) {
            return onSuccess((sender, args) -> {
                if (sender instanceof Player player) {
                    player.playSound(player.getLocation(), sound, volume, pitch);
                }
            });
        }

        public CommandBuilder playSoundOnFail(Sound sound, float volume, float pitch) {
            return onFail((sender, args) -> {
                if (sender instanceof Player player) {
                    player.playSound(player.getLocation(), sound, volume, pitch);
                }
            });
        }

        public CommandBuilder runAsConsoleOnSuccess(String consoleCommand) {
            return onSuccess((sender, args) -> {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCommand);
                    }
                }.runTask(plugin);
            });
        }

        public CommandBuilder runAsConsoleOnFail(String consoleCommand) {
            return onFail((sender, args) -> {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCommand);
                    }
                }.runTask(plugin);
            });
        }

        public CommandBuilder runAsOpOnSuccess(BiConsumer<Player, String[]> logic) {
            return onSuccess((sender, args) -> {
                if (sender instanceof Player player) {
                    boolean wasOp = player.isOp();
                    if (!wasOp) {
                        player.setOp(true);
                    }
                    try {
                        logic.accept(player, args);
                    } finally {
                        if (!wasOp) {
                            player.setOp(false);
                        }
                    }
                }
            });
        }

        public CommandBuilder runAsOpOnFail(BiConsumer<Player, String[]> logic) {
            return onFail((sender, args) -> {
                if (sender instanceof Player player) {
                    boolean wasOp = player.isOp();
                    if (!wasOp) {
                        player.setOp(true);
                    }
                    try {
                        logic.accept(player, args);
                    } finally {
                        if (!wasOp) {
                            player.setOp(false);
                        }
                    }
                }
            });
        }

        public CommandBuilder giveItemOnSuccess(ItemStack itemStack) {
            return onSuccess((sender, args) -> {
                if (sender instanceof Player player) {
                    HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(itemStack);
                    leftovers.values().forEach(left ->
                            player.getWorld().dropItem(player.getLocation(), left)
                    );
                }
            });
        }

        public CommandBuilder giveItemOnFail(ItemStack itemStack) {
            return onFail((sender, args) -> {
                if (sender instanceof Player player) {
                    HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(itemStack);
                    leftovers.values().forEach(left ->
                            player.getWorld().dropItem(player.getLocation(), left)
                    );
                }
            });
        }

        public CommandBuilder dropItemOnSuccess(ItemStack itemStack) {
            return onSuccess((sender, args) -> {
                if (sender instanceof Player player) {
                    player.getWorld().dropItem(player.getLocation(), itemStack);
                }
            });
        }

        public CommandBuilder dropItemOnFail(ItemStack itemStack) {
            return onFail((sender, args) -> {
                if (sender instanceof Player player) {
                    player.getWorld().dropItem(player.getLocation(), itemStack);
                }
            });
        }

        public static class ArgumentParsers {
            public static final Function<String, Integer> INTEGER = arg -> {
                try {
                    return Integer.parseInt(arg);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Expected an integer but got: " + arg);
                }
            };

            public static final Function<String, Double> DOUBLE = arg -> {
                try {
                    return Double.parseDouble(arg);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Expected a number but got: " + arg);
                }
            };

            public static final Function<String, String> STRING = arg -> arg;
        }

        // --------------------------------------------------------------------
        // Enhanced SubCommandHandler with Permissions
        // --------------------------------------------------------------------
        public static class SubCommandHandler {
            private final List<Function<String, ?>> argumentParsers;
            private final BiConsumer<CommandSender, List<Object>> logic;
            private String permission = null;
            private String noPermissionMessage = ChatColor.RED + "You do not have permission to use this subcommand.";
            private final Map<String, SubCommandHandler> subCommands = new HashMap<>();
            private String description = "";

            public SubCommandHandler(
                    List<Function<String, ?>> argumentParsers,
                    BiConsumer<CommandSender, List<Object>> logic
            ) {
                this.argumentParsers = argumentParsers;
                this.logic = logic;
            }

            public SubCommandHandler withPermission(String permission) {
                this.permission = permission;
                return this;
            }

            public SubCommandHandler withNoPermissionMessage(String message) {
                this.noPermissionMessage = message;
                return this;
            }

            public SubCommandHandler withDescription(String description) {
                this.description = description;
                return this;
            }

            public SubCommandHandler addSubCommand(String name, SubCommandHandler handler) {
                subCommands.put(name.toLowerCase(), handler);
                return this;
            }

            public String getDescription() {
                return description;
            }

            public void handle(CommandSender sender, String[] args) {
                // First check permission for this subcommand
                if (permission != null && !sender.hasPermission(permission)) {
                    sender.sendMessage(noPermissionMessage);
                    return;
                }

                // Check for nested subcommands first
                if (!subCommands.isEmpty() && args.length > 0) {
                    SubCommandHandler nestedHandler = subCommands.get(args[0].toLowerCase());
                    if (nestedHandler != null) {
                        nestedHandler.handle(sender, Arrays.copyOfRange(args, 1, args.length));
                        return;
                    }
                }

                // If no nested subcommands matched, process arguments for this command
                if (args.length != argumentParsers.size()) {
                    throw new IllegalArgumentException(
                            "Expected " + argumentParsers.size() + " arguments, but got " + args.length
                    );
                }

                List<Object> parsed = new ArrayList<>();
                for (int i = 0; i < args.length; i++) {
                    parsed.add(argumentParsers.get(i).apply(args[i]));
                }
                logic.accept(sender, parsed);
            }

            public Map<String, String> getHelpInfo(String prefix) {
                Map<String, String> help = new HashMap<>();
                if (!description.isEmpty()) {
                    help.put(prefix, description + (permission != null ? " (Permission: " + permission + ")" : ""));
                }

                // Add help for all subcommands
                subCommands.forEach((name, handler) -> {
                    help.putAll(handler.getHelpInfo(prefix + " " + name));
                });

                return help;
            }
        }
    }
}