package org.Nysxl.InventoryManager;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.UUID;

/**
 * A display-only dynamic icon for inventory GUIs.
 * This class supports periodic updates to its display properties.
 */
public class DynamicDisplay {

    private final ItemStack itemStack;
    private Runnable onUpdate;

    // Constructor
    private DynamicDisplay(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    // Update the display of the item
    public void updateDisplay() {
        if (onUpdate != null) {
            onUpdate.run();
        }
    }

    // Get the current ItemStack
    public ItemStack getItemStack() {
        return itemStack;
    }

    /**
     * Set a runnable that updates the item's display properties dynamically.
     */
    public void setOnUpdate(Runnable onUpdate) {
        this.onUpdate = onUpdate;
    }

    // ------------------------------------------------------------------------
    // Periodic Update
    // ------------------------------------------------------------------------

    /**
     * Start a periodic update task.
     *
     * @param intervalSeconds The interval in seconds between updates.
     */
    public void startPeriodicUpdates(int intervalSeconds) {
        new BukkitRunnable() {
            @Override
            public void run() {
                updateDisplay();
            }
        }.runTaskTimer(Bukkit.getPluginManager().getPlugins()[0], 0, intervalSeconds * 20L);
    }

    // ------------------------------------------------------------------------
    // Builder Pattern for Cleaner Construction
    // ------------------------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Material material = Material.STONE;
        private String displayName;
        private List<String> lore;
        private UUID headOwnerUUID;
        private String headOwnerName;
        private ItemStack itemStack;

        private Builder() {}

        public Builder material(Material material) {
            this.material = material;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder lore(List<String> lore) {
            this.lore = lore;
            return this;
        }

        public Builder headOwner(UUID uuid) {
            this.headOwnerUUID = uuid;
            return this;
        }

        public Builder headOwner(String playerName) {
            this.headOwnerName = playerName;
            return this;
        }

        public Builder withItemStack(ItemStack itemStack) {
            this.itemStack = itemStack;
            return this;
        }

        public DynamicDisplay build() {
            ItemStack stack;
            ItemMeta meta;

            if (itemStack != null) {
                stack = itemStack;
                meta = stack.getItemMeta();
            } else {
                stack = new ItemStack(material);
                meta = stack.getItemMeta();
            }

            if (meta != null) {
                if (displayName != null) meta.setDisplayName(displayName);
                if (lore != null) meta.setLore(lore);

                stack.setItemMeta(meta);
            }

            return new DynamicDisplay(stack);
        }
    }

}
