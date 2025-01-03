package org.Nysxl;

import net.milkbowl.vault.economy.Economy;
import org.Nysxl.CommandManager.CommandBuilder;
import org.Nysxl.CommandManager.CommandManager;
import org.Nysxl.DynamicConfigManager.DynamicConfigManager;
import org.Nysxl.Utils.Economy.EconomyManager;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.*;

import java.util.List;

public class NysxlServerUtils extends JavaPlugin {

    private static DynamicConfigManager profileConfigManager;
    private static DynamicConfigManager economyConfigManager;
    private static Economy economy;
    private static EconomyManager economyManager;
    private static NysxlServerUtils instance;
    private CommandManager commandManager;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize ConfigManager
        registerConfigManager();

        // Initialize CommandRegistry
        commandManager = new CommandManager();

        // Setup Vault
        if (!setupEconomy()) {
            getLogger().severe("Vault dependency not found! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register commands
        registerCommands();

        // Register event handlers
        registerInventoryEvents();
    }

    private void registerConfigManager() {
        profileConfigManager = new DynamicConfigManager(this);
        profileConfigManager.loadOrCreateDefaultConfig("ProfileViewerConfig"); // Explicitly load the profile config

        economyConfigManager = new DynamicConfigManager(this);
        economyConfigManager.loadOrCreateDefaultConfig("economy"); // Load the economy config

        economyManager = new EconomyManager(economyConfigManager);
    }

    private void registerCommands() {
        registerTaxCommand();
        registerTaxViewerCommand();
        registerSetAvailableTaxCommand();
    }

    private void registerTaxCommand() {
        CommandExecutor executor = new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Only players can use this command.");
                    return true;
                }
                Player player = (Player) sender;
                try {
                    double newTax = Double.parseDouble(args[0]);
                    if (newTax < 0 || newTax > 1) {
                        player.sendMessage(ChatColor.RED + "Tax percentage must be between 0 and 1.");
                        return true;
                    }
                    economyManager.setTaxRate(newTax);
                    player.sendMessage(ChatColor.GREEN + "Tax percentage updated to " + (newTax * 100) + "%.");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid number format.");
                } catch (ArrayIndexOutOfBoundsException e) {
                    player.sendMessage(ChatColor.RED + "Usage: /setTax <percentage>");
                }
                return true;
            }
        };

        Command setTaxCommand = new CommandBuilder()
                .setName("setTax")
                .usage(0, Double.class, "<percentage>")
                .setExecutor(executor)
                .setTabCompleter((sender, command, alias, args) -> List.of("<percentage>"))
                .setUsageMessage(ChatColor.RED + "Usage: /setTax <percentage>") // Optional
                .onFail(context -> context.getSender().sendMessage(ChatColor.RED + "Failed to execute tax command.")) // Optional
                .build();

        commandManager.registerCommand(this, setTaxCommand.getName(), executor);
    }

    private void registerTaxViewerCommand() {
        CommandExecutor executor = new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
                double totalTaxes = economyManager.getAvailableTaxes();
                if (sender instanceof Player) {
                    ((Player) sender).sendMessage(ChatColor.GREEN + "Total taxes collected so far: " + totalTaxes);
                } else {
                    sender.sendMessage("Total taxes collected so far: " + totalTaxes);
                }
                return true;
            }
        };

        Command getTaxesCommand = new CommandBuilder()
                .setName("getTaxes")
                .acceptsCmd() // Allow this command to be executed from the console
                .setExecutor(executor)
                .setTabCompleter((sender, command, alias, args) -> List.of())
                .build();

        commandManager.registerCommand(this, getTaxesCommand.getName(), executor);
    }

    private void registerSetAvailableTaxCommand() {
        CommandExecutor executor = new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, org.bukkit.command.Command command, String label, String[] args) {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("Only players can use this command.");
                    return true;
                }
                Player player = (Player) sender;
                try {
                    double newTax = Double.parseDouble(args[0]);
                    if (newTax < 0) {
                        player.sendMessage(ChatColor.RED + "Tax amount must be greater than 0.");
                        return true;
                    }
                    economyManager.setAvailableTaxes(newTax);
                    player.sendMessage(ChatColor.GREEN + "Available tax updated to " + newTax + ".");
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Invalid number format.");
                } catch (ArrayIndexOutOfBoundsException e) {
                    player.sendMessage(ChatColor.RED + "Usage: /setAvailableTax <amount>");
                }
                return true;
            }
        };

        org.bukkit.command.Command setAvailableTaxCommand = new CommandBuilder()
                .setName("setAvailableTax")
                .usage(0, Double.class, "<amount>")
                .setExecutor(executor)
                .setTabCompleter((sender, command, alias, args) -> List.of("<amount>"))
                .setUsageMessage(ChatColor.RED + "Usage: /setAvailableTax <amount>") // Optional
                .onFail(context -> context.getSender().sendMessage(ChatColor.RED + "Failed to execute setAvailableTax command.")) // Optional
                .build();

        commandManager.registerCommand(this, setAvailableTaxCommand.getName(), executor);
    }

    // Return the profile config manager
    public static DynamicConfigManager getProfileConfigManager() {
        return profileConfigManager;
    }

    // Return the economy config manager
    public static DynamicConfigManager getEconomyConfigManager() {
        return economyConfigManager;
    }

    // Set up the economy
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    // Returns Vault economy
    public static Economy getEconomy() {
        return economy;
    }

    // Returns instance of plugin
    public static NysxlServerUtils getInstance() {
        return instance;
    }

    // Returns EconomyManager for easy economy management
    public static EconomyManager getEconomyManager() {
        return economyManager;
    }

    // Register inventory events
    private void registerInventoryEvents() {
        // Implementation for registering inventory events
    }
}