package org.Nysxl.Utils.Economy;

import org.Nysxl.NysxlServerUtils;
import org.bukkit.OfflinePlayer;

public class Economy {

        public double getPlayerBalance(OfflinePlayer player){
            return NysxlServerUtils.getEconomy().getBalance(player);
        }

        public boolean hasBalance(OfflinePlayer player, double balance){
            return getPlayerBalance(player) >= balance;
        }

        public boolean withdrawBalance(OfflinePlayer player, double balance){
            return NysxlServerUtils.getEconomy().withdrawPlayer(player,balance).transactionSuccess();
        }

        public boolean depositBalance(OfflinePlayer player, double balance){
            return NysxlServerUtils.getEconomy().depositPlayer(player,balance).transactionSuccess();
        }
}
