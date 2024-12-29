package org.Nysxl;

import net.milkbowl.vault.economy.Economy;
import org.Nysxl.CommandManager.CommandRegistry;
import org.Nysxl.Components.PermissionChecker.DynamicPermissionCheckCommand;
import org.Nysxl.Components.Profiles.ProfileViewer;
import org.Nysxl.InventoryManager.DynamicConfigManager;
import org.Nysxl.InventoryManager.DynamicInventoryHandler;
import org.Nysxl.Utils.Economy.EconomyManager;
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

    private void registerCommands() {
        registerViewRequestsCommand();
        registerReasonCommand();
        registerViewProfileCommand();
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

    private void registerInventoryEvents() {
        DynamicInventoryHandler.registerGlobal(this);

        DynamicPermissionCheckCommand permissionHandler = new DynamicPermissionCheckCommand(porfileConfigManager);
        permissionHandler.register(this);
    }

    public static DynamicConfigManager getPorfileConfigManager() {
        return porfileConfigManager;
    }

    public static DynamicConfigManager getEconomyConfigManager() {
        return economyConfigManager;
    }

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

    public static Economy getEconomy() {
        return economy;
    }

    public static NysxlServerUtils getInstance() {
        return instance;
    }

    public static EconomyManager getEconomyManager() {
        return economyManager;
    }
}