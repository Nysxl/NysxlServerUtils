package org.Nysxl;

import net.milkbowl.vault.economy.Economy;
import org.Nysxl.CommandManager.CommandBuilder;
import org.Nysxl.CommandManager.CommandManager;
import org.Nysxl.CommandManager.SubCommandBuilder;
import org.Nysxl.DynamicConfigManager.DynamicConfigManager;
import org.Nysxl.Utils.Economy.EconomyManager;
import org.bukkit.ChatColor;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

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
    }

    private void registerTaxCommand() {
        CommandBuilder taxCommand = new CommandBuilder(this)
                .setName("tax")
                .setUsageMessage(ChatColor.RED + "Usage: /tax <get|set> [value]")
                .onFail(context -> context.getSender().sendMessage(ChatColor.RED + "Failed to execute tax command."));

        // Subcommand to get the available tax
        SubCommandBuilder getSubCommand = new SubCommandBuilder(taxCommand)
                .setName("get")
                .setPlayerExecutor((player, context) -> {
                    double totalTaxes = economyManager.getAvailableTaxes();
                    player.sendMessage(ChatColor.GREEN + "Total taxes collected so far: " + totalTaxes);
                })
                .setConsoleExecutor((sender, context) -> {
                    double totalTaxes = economyManager.getAvailableTaxes();
                    sender.sendMessage("Total taxes collected so far: " + totalTaxes);
                });

        // Subcommand to set the available tax
        SubCommandBuilder setSubCommand = new SubCommandBuilder(taxCommand)
                .setName("set")
                .usage(0, Double.class, "<amount>")
                .setPlayerExecutor((player, context) -> {
                    try {
                        double newTax = Double.parseDouble(context.getArgs().get(0));
                        if(newTax < 0) {
                            player.sendMessage(ChatColor.RED + "Tax amount must be greater than 0.");
                            return;
                        }
                        economyManager.setAvailableTaxes(newTax);
                        player.sendMessage(ChatColor.GREEN + "Available tax updated to " + newTax + ".");
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Invalid number format.");
                    } catch (IndexOutOfBoundsException e) {
                        player.sendMessage(ChatColor.RED + "Usage: /tax set <amount>");
                    }
                });

        // Nested subcommand to set the tax percentage
        SubCommandBuilder setPercentageSubCommand = new SubCommandBuilder(setSubCommand)
                .setName("percentage")
                .usage(0, Double.class, "<percentage>")
                .setPlayerExecutor((player, context) -> {
                    try {
                        double newTax = Double.parseDouble(context.getArgs().get(0));
                        if(newTax < 0 || newTax > 1) {
                            player.sendMessage(ChatColor.RED + "Tax percentage must be between 0 and 1.");
                            return;
                        }
                        economyManager.setTaxRate(newTax);
                        player.sendMessage(ChatColor.GREEN + "Tax percentage updated to " + (newTax * 100) + "%.");
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Invalid number format.");
                    } catch (IndexOutOfBoundsException e) {
                        player.sendMessage(ChatColor.RED + "Usage: /tax set percentage <percentage>");
                    }
                });

        // Register nested subcommand
        setSubCommand.addSubCommand(setPercentageSubCommand);

        // Register subcommands
        taxCommand.addSubCommand(getSubCommand)
                .addSubCommand(setSubCommand)
                .register();
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