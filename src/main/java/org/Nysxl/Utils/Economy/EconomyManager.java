package org.Nysxl.Utils.Economy;


import org.Nysxl.DynamicConfigManager.DynamicConfigManager;
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

    private void saveTotalTaxesPaid() {
        NysxlServerUtils.getEconomyConfigManager()
                .getConfig("economy")
                .set("taxes.availableTaxes", availableTaxes);
        NysxlServerUtils.getEconomyConfigManager().saveConfig("economy");
    }

    public void loadTotalTaxesPaid() {
        availableTaxes = NysxlServerUtils.getEconomyConfigManager()
                .getConfig("economy")
                .getDouble("taxes.availableTaxes");
    }

    private void saveTaxRate() {
        NysxlServerUtils.getEconomyConfigManager()
                .getConfig("economy")
                .set("taxes.taxRate", taxRate);
        NysxlServerUtils.getEconomyConfigManager().saveConfig("economy");
    }

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

    public void setTaxRate(double taxRate) {
        this.taxRate = taxRate;
        saveTaxRate();
    }

    public void setAvailableTaxes(double availableTaxes) {
        this.availableTaxes = availableTaxes;
        saveTotalTaxesPaid();
    }

    public void addToAvailableTaxes(double amount) {
        availableTaxes += amount;
        saveTotalTaxesPaid();
    }
}
