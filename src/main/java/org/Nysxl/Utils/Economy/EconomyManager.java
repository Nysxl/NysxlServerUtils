package org.Nysxl.Utils.Economy;

import org.Nysxl.InventoryManager.DynamicConfigManager;
import org.Nysxl.NysxlServerUtils;
import org.bukkit.OfflinePlayer;

public class EconomyManager {

    private double taxRate;
    private double availableTaxes;
    private final DynamicConfigManager economyConfigManager;


    public EconomyManager(DynamicConfigManager economyConfigManager) {
        this.economyConfigManager = economyConfigManager;
        loadTaxRate();
        loadTotalTaxesPaid();
    }

    /*
        * Get the tax rate from the economy config
     */
    private void saveTotalTaxesPaid() {
        NysxlServerUtils.getEconomyConfigManager()
                .getConfig("economy")
                .set("taxes.availableTaxes", availableTaxes);
        NysxlServerUtils.getEconomyConfigManager().saveConfig("economy");
    }

    /*
        * Load the total taxes paid from the economy config
     */
    public void loadTotalTaxesPaid() {
        availableTaxes = NysxlServerUtils.getEconomyConfigManager()
                .getConfig("economy")
                .getDouble("taxes.availableTaxes");
    }

    /*
        * Save the tax rate to the economy config
     */
    private void saveTaxRate() {
        NysxlServerUtils.getEconomyConfigManager()
                .getConfig("economy")
                .set("taxes.taxRate", taxRate);
        NysxlServerUtils.getEconomyConfigManager().saveConfig("economy");
    }

    /*
        * Load the tax rate from the economy config
     */
    public void loadTaxRate() {
        taxRate = NysxlServerUtils.getEconomyConfigManager()
                .getConfig("economy")
                .getDouble("taxes.taxRate");
    }

    public double getPlayerBalance(OfflinePlayer player) {
        return NysxlServerUtils.getEconomy().getBalance(player);
    }

    public boolean hasBalance(OfflinePlayer player, double balance) {
        return getPlayerBalance(player) >= balance;
    }

    public boolean withdrawBalance(OfflinePlayer player, double balance) {
        return NysxlServerUtils.getEconomy().withdrawPlayer(player, balance).transactionSuccess();
    }

    public boolean depositBalance(OfflinePlayer player, double balance) {
        return NysxlServerUtils.getEconomy().depositPlayer(player, balance).transactionSuccess();
    }

    public double getTaxRate() {
        return taxRate;
    }

    public double getAvailableTaxes() {
        return availableTaxes;
    }
}
