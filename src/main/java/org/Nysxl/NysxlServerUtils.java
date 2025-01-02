package org.Nysxl;

import net.milkbowl.vault.economy.Economy;
import org.Nysxl.CommandManager.CommandRegistry;
import org.Nysxl.Components.PermissionChecker.DynamicPermissionCheckCommand;
import org.Nysxl.Components.Profiles.ProfileViewer;
import org.Nysxl.DynamicConfigManager.DynamicConfigManager;
import org.Nysxl.InventoryManager.DynamicInventoryHandler;
import org.Nysxl.Utils.Economy.EconomyManager;
import org.Nysxl.Utils.StringBuildHandler;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class NysxlServerUtils extends JavaPlugin {

    private CommandRegistry commandRegistry;
    private static DynamicConfigManager porfileConfigManager;
    private static DynamicConfigManager economyConfigManager;
    private static Economy economy;
    private static EconomyManager economyManager;
    private static NysxlServerUtils instance;

    @Override
    public void onEnable() {
        instance = this;

        // Initialize ConfigManager
        registerConfigManager();

        // Initialize CommandRegistry
        commandRegistry = new CommandRegistry(this);

        new ProfileViewer();

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
        porfileConfigManager = new DynamicConfigManager(this);
        porfileConfigManager.loadOrCreateDefaultConfig("ProfileViewerConfig"); // Explicitly load the profile config

        economyConfigManager = new DynamicConfigManager(this);
        economyConfigManager.loadOrCreateDefaultConfig("economy"); // Load the economy config

        economyManager = new EconomyManager(economyConfigManager);
    }

    /*
        * Register all commands for the plugin
     */
    private void registerCommands() {
        registerViewRequestsCommand();
        registerReasonCommand();
        registerViewProfileCommand();
        registerTaxCommand();
        registerTaxViewerCommand();
        registerSetAvailableTaxCommand();
    }

    private void registerTaxCommand() {
        commandRegistry.createCommand("setTax")
                .requirePlayer()
                .check(sender -> sender instanceof Player, "Only players can use this command.")
                .checkWithArgs((sender, args) -> {
                    if (args == null || args.length < 1) {
                        throw new IllegalArgumentException("Usage: /setTax <percentage>");
                    }

                    double newTax = Double.parseDouble(args[0]);
                    if (newTax < 0 || newTax > 1) {
                        throw new IllegalArgumentException("Tax percentage must be between 0 and 1.");
                    }
                }, "Invalid arguments.")
                .onFallback((sender, partial) -> {
                    sender.sendMessage(ChatColor.RED + "Usage: /setTax <percentage>");
                })
                .onExecute((sender, args) -> {
                    try {
                        double newTax = Double.parseDouble(args[0]);
                        economyManager.setTaxRate(newTax);
                        sender.sendMessage(ChatColor.GREEN + "Tax percentage updated to " + (newTax * 100) + "%.");
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "Invalid number format.");
                    }
                })
                .register();
    }

    private void registerTaxViewerCommand() {
        commandRegistry.createCommand("getTaxes")
                .requirePlayer()
                .withPermission("bounty.gettaxes")
                .onExecute((sender, args) -> {
                    double totalTaxes = economyManager.getAvailableTaxes();
                    sender.sendMessage(ChatColor.GREEN + "Total taxes collected so far: " + totalTaxes);
                })
                .register();
    }

    private void registerViewRequestsCommand() {
        commandRegistry.createCommand("viewrequests")
                .requirePlayer()
                .withPermission("admin.viewrequests")
                .onExecute((sender, args) -> {
                    Player player = (Player) sender; // Safe cast due to requirePlayer
                    DynamicPermissionCheckCommand.displayActiveRequests(player);
                })
                .register();
    }

    private void registerReasonCommand() {
        commandRegistry.createCommand("reason")
                .requirePlayer()
                .onExecute((sender, args) -> {
                    if (args.length < 1) {
                        sender.sendMessage("Usage: /reason <command>");
                        return;
                    }
                    Player player = (Player) sender; // Safe cast due to requirePlayer
                    DynamicPermissionCheckCommand.handleReason(player, args[0]);
                })
                .register();
    }

    private void registerViewProfileCommand() {
        commandRegistry.createCommand("viewprofile")
                .requirePlayer()
                .onExecute((sender, args) -> {
                    if (args.length < 1) {
                        sender.sendMessage("Usage: /viewprofile <player>");
                        return;
                    }
                    Player player = (Player) sender; // Safe cast due to requirePlayer
                    Player target = getServer().getPlayer(args[0]);
                    if (target == null) {
                        player.sendMessage("Player not found.");
                        return;
                    }
                    ProfileViewer.openProfile(player, target);
                })
                .register();
    }

    private void registerSetAvailableTaxCommand() {
        commandRegistry.createCommand("setAvailableTax")
                .requirePlayer()
                .withPermission("admin.economy.setavailabletaxes")
                .check(sender -> sender instanceof Player, "Only players can use this command.")
                .checkWithArgs((sender, args) -> {
                    if(args == null || args.length < 1) {
                        throw new IllegalArgumentException("Usage: /setAvailableTax <amount>");
                    }

                    double newTax = Double.parseDouble(args[0]);
                    if(newTax < 0) {
                        throw new IllegalArgumentException("Tax amount must be greater than 0.");
                    }
                }, "Invalid arguments.")
                .onFallback((sender, partial) -> {
                    sender.sendMessage(ChatColor.RED + "Usage: /setAvailableTax <amount>");
                })
                .onExecute((sender, args) -> {
                    try {
                        double newTax = Double.parseDouble(args[0]);
                        economyManager.setAvailableTaxes(newTax);
                        sender.sendMessage(ChatColor.GREEN + "Available tax updated to " + newTax + ".");
                    } catch (NumberFormatException e) {
                        sender.sendMessage(ChatColor.RED + "Invalid number format.");
                    }
                })
                .register();
    }

    //register inventory events
    private void registerInventoryEvents() {
        DynamicInventoryHandler.registerGlobal(this);

        DynamicPermissionCheckCommand permissionHandler = new DynamicPermissionCheckCommand(porfileConfigManager);
        permissionHandler.register(this);
    }

    //return the profile config manager
    public static DynamicConfigManager getPorfileConfigManager() {
        return porfileConfigManager;
    }

    //return the economy config manager
    public static DynamicConfigManager getEconomyConfigManager() {
        return economyConfigManager;
    }

    //set up the economy
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

    //returns Vault economy
    public static Economy getEconomy() {
        return economy;
    }

    //returns instance of plugin
    public static NysxlServerUtils getInstance() {
        return instance;
    }

    //returns EconomyManager for easy economy management.
    public static EconomyManager getEconomyManager() {
        return economyManager;
    }

}