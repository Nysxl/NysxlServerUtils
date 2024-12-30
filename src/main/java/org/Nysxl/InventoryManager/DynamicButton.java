package org.Nysxl.InventoryManager;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A dynamic, feature-rich Button for inventory GUIs.
 * Allows different click behaviors for each ClickType, plus checks, success/fail pipelines,
 * and "extra" utility actions (run commands, spawn mobs, play sounds, etc.).
 */
public class DynamicButton {

    // Map to dynamically store actions based on ClickType
    private final EnumMap<ClickType, ClickAction> clickActions = new EnumMap<>(ClickType.class);
    private final ItemStack itemStack;

    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------
    public DynamicButton(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    public DynamicButton(ItemStack itemStack, EnumMap<ClickType, ClickAction> clickActions) {
        this.itemStack = itemStack;
        this.clickActions.putAll(clickActions);
    }

    // ------------------------------------------------------------------------
    // create(...) Helper
    // ------------------------------------------------------------------------
    public static DynamicButton create(Material material, String title, List<String> lore, UUID playerUUID, String playerName) {
        ItemStack itemStack = new ItemStack(material);
        ItemMeta meta = itemStack.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(title);
            meta.setLore(lore);

            if (material == Material.PLAYER_HEAD && meta instanceof SkullMeta skullMeta) {
                if (playerUUID != null) {
                    skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(playerUUID));
                } else if (playerName != null && !playerName.isEmpty()) {
                    skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName));
                }
            }

            itemStack.setItemMeta(meta);
        }

        return new DynamicButton(itemStack);
    }

    // ------------------------------------------------------------------------
    // handleClick(...)
    // ------------------------------------------------------------------------
    /**
     * Main entry point when a button is clicked.
     * Checks if there's a ClickAction for this ClickType; if so, it runs checks
     * and either calls onSuccess or onFail actions.
     */
    public void handleClick(InventoryClickEvent event) {
        ClickAction action = clickActions.get(event.getClick());
        if (action != null) {
            action.execute(event);
        }
    }

    // ------------------------------------------------------------------------
    // Set or update a ClickAction for a given ClickType
    // ------------------------------------------------------------------------
    public void setClickAction(ClickType clickType, ClickAction action) {
        clickActions.put(clickType, action);
    }


    public ItemStack getItemStack() {
        return itemStack;
    }

    // ------------------------------------------------------------------------
    // Builder pattern for cleaner construction
    // ------------------------------------------------------------------------
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Updates the display properties of the button.
     * This method can refresh metadata like the title or lore dynamically.
     */
    public void update() {
        if (itemStack != null) {
            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {
                // Example: Update the display name dynamically
                String updatedName = "Updated: " + UUID.randomUUID().toString().substring(0, 5);
                meta.setDisplayName(updatedName);

                // Example: Update lore dynamically
                List<String> updatedLore = Arrays.asList("Dynamic lore line 1", "Dynamic lore line 2");
                meta.setLore(updatedLore);

                // Apply updated meta to the ItemStack
                itemStack.setItemMeta(meta);
            }
        }
    }


    public static class Builder {
        private Material material = Material.STONE_BUTTON;
        private String displayName;
        private List<String> lore;
        private final EnumMap<ClickType, ClickAction> clickActions = new EnumMap<>(ClickType.class);

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

        public Builder withItemStack(ItemStack itemStack){
            this.itemStack = itemStack;
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

        /**
         * Define actions that happen on a specific ClickType.
         * Example usage:
         *   .onClick(ClickType.LEFT, ClickAction.create()
         *       .check(...)
         *       .onSuccess(...)
         *       .onFail(...)
         *   )
         */
        public Builder onClick(ClickType type, ClickAction action) {
            clickActions.put(type, action);
            return this;
        }

        public DynamicButton build() {
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

                if (material == Material.PLAYER_HEAD && meta instanceof SkullMeta skullMeta) {
                    if (headOwnerUUID != null) {
                        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(headOwnerUUID));
                    } else if (headOwnerName != null && !headOwnerName.isEmpty()) {
                        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(headOwnerName));
                    }
                }
                stack.setItemMeta(meta);
            }

            return new DynamicButton(stack, clickActions);
        }
    }

    // ------------------------------------------------------------------------
    // ClickAction: where we handle checks, success/fail, and utility methods
    // ------------------------------------------------------------------------

    /**
     * Represents the entire pipeline for a single ClickType.
     * It can have zero or more checks, plus onSuccess and onFail consumer lists.
     * Also includes utility methods for playing sounds, running commands, etc.
     */
    public static class ClickAction {
        // A list of checks. If any fail, we consider the action "failed."
        private final List<Predicate<InventoryClickEvent>> checks = new ArrayList<>();

        // For success/fail logic
        private final List<Consumer<InventoryClickEvent>> onSuccessActions = new ArrayList<>();
        private final List<Consumer<InventoryClickEvent>> onFailActions = new ArrayList<>();

        private ClickAction() {}

        // --------------------------------------------------------------------
        // Factory method
        // --------------------------------------------------------------------
        public static ClickAction create() {
            return new ClickAction();
        }

        // --------------------------------------------------------------------
        // Execution
        // --------------------------------------------------------------------
        public void execute(InventoryClickEvent event) {
            // Run checks
            for (Predicate<InventoryClickEvent> check : checks) {
                if (!check.test(event)) {
                    // A check failed => run fail actions, then exit
                    runFail(event);
                    return;
                }
            }
            // If we get here, all checks passed => success
            runSuccess(event);
        }

        private void runSuccess(InventoryClickEvent event) {
            for (Consumer<InventoryClickEvent> consumer : onSuccessActions) {
                consumer.accept(event);
            }
        }

        private void runFail(InventoryClickEvent event) {
            for (Consumer<InventoryClickEvent> consumer : onFailActions) {
                consumer.accept(event);
            }
        }

        // --------------------------------------------------------------------
        // Chainable config
        // --------------------------------------------------------------------
        /**
         * Add a check. If it returns false, the action is considered failed.
         */
        public ClickAction check(Predicate<InventoryClickEvent> condition) {
            checks.add(condition);
            return this;
        }

        /**
         * Action(s) to run if all checks pass.
         */
        public ClickAction onSuccess(Consumer<InventoryClickEvent> action) {
            onSuccessActions.add(action);
            return this;
        }

        /**
         * Action(s) to run if any check fails.
         */
        public ClickAction onFail(Consumer<InventoryClickEvent> action) {
            onFailActions.add(action);
            return this;
        }

        // --------------------------------------------------------------------
        // Utility / Extra Methods
        //
        // These mimic the onSuccess / onFail approach from the CommandRegistry
        // but adapted to an InventoryClickEvent context.
        // --------------------------------------------------------------------

        // 1) Check Helpers
        /**
         * Only allow players (no console). If a console tries, it fails.
         */
        public ClickAction requirePlayer() {
            return check(e -> e.getWhoClicked() instanceof Player);
        }

        /**
         * If the player doesn't have a certain permission, fail.
         */
        public ClickAction withPermission(String permission) {
            return check(e -> e.getWhoClicked().hasPermission(permission));
        }

        // 2) Playing sounds
        public ClickAction playSoundOnSuccess(Sound sound, float volume, float pitch) {
            return onSuccess(e -> {
                if (e.getWhoClicked() instanceof Player player) {
                    player.playSound(player.getLocation(), sound, volume, pitch);
                }
            });
        }
        public ClickAction playSoundOnFail(Sound sound, float volume, float pitch) {
            return onFail(e -> {
                if (e.getWhoClicked() instanceof Player player) {
                    player.playSound(player.getLocation(), sound, volume, pitch);
                }
            });
        }

        // 3) Run console commands
        public ClickAction runAsConsoleOnSuccess(String consoleCommand) {
            return onSuccess(e -> {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCommand);
                    }
                }.runTask(Bukkit.getPluginManager().getPlugins()[0]);
                // or store plugin instance somewhere
            });
        }

        public ClickAction runAsConsoleOnFail(String consoleCommand) {
            return onFail(e -> {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCommand);
                    }
                }.runTask(Bukkit.getPluginManager().getPlugins()[0]);
            });
        }

        // 4) Temporarily run logic as OP
        public ClickAction runAsOpOnSuccess(Consumer<Player> logic) {
            return onSuccess(e -> {
                if (e.getWhoClicked() instanceof Player player) {
                    boolean wasOp = player.isOp();
                    if (!wasOp) {
                        player.setOp(true);
                    }
                    try {
                        logic.accept(player);
                    } finally {
                        if (!wasOp) {
                            player.setOp(false);
                        }
                    }
                }
            });
        }

        public ClickAction runAsOpOnFail(Consumer<Player> logic) {
            return onFail(e -> {
                if (e.getWhoClicked() instanceof Player player) {
                    boolean wasOp = player.isOp();
                    if (!wasOp) {
                        player.setOp(true);
                    }
                    try {
                        logic.accept(player);
                    } finally {
                        if (!wasOp) {
                            player.setOp(false);
                        }
                    }
                }
            });
        }

        // 5) Spawn mobs near the player
        public ClickAction spawnMobOnSuccess(EntityType type, int amount) {
            return onSuccess(e -> {
                if (e.getWhoClicked() instanceof Player player) {
                    for (int i = 0; i < amount; i++) {
                        player.getWorld().spawnEntity(player.getLocation(), type);
                    }
                }
            });
        }

        public ClickAction spawnMobOnFail(EntityType type, int amount) {
            return onFail(e -> {
                if (e.getWhoClicked() instanceof Player player) {
                    for (int i = 0; i < amount; i++) {
                        player.getWorld().spawnEntity(player.getLocation(), type);
                    }
                }
            });
        }

        /**
         * Spawn custom mob with a Consumer<Entity> to modify it (like custom name, etc.).
         */
        public ClickAction spawnCustomMobOnSuccess(EntityType type, int amount, Consumer<Entity> customizer) {
            return onSuccess(e -> {
                if (e.getWhoClicked() instanceof Player player) {
                    for (int i = 0; i < amount; i++) {
                        Entity ent = player.getWorld().spawnEntity(player.getLocation(), type);
                        customizer.accept(ent);
                    }
                }
            });
        }

        public ClickAction spawnCustomMobOnFail(EntityType type, int amount, Consumer<Entity> customizer) {
            return onFail(e -> {
                if (e.getWhoClicked() instanceof Player player) {
                    for (int i = 0; i < amount; i++) {
                        Entity ent = player.getWorld().spawnEntity(player.getLocation(), type);
                        customizer.accept(ent);
                    }
                }
            });
        }

        // 6) Place blocks
        public ClickAction placeBlockOnSuccess(Material material, int offsetY) {
            return onSuccess(e -> {
                if (e.getWhoClicked() instanceof Player player) {
                    player.getLocation().add(0, offsetY, 0).getBlock().setType(material);
                }
            });
        }

        public ClickAction placeBlockOnFail(Material material, int offsetY) {
            return onFail(e -> {
                if (e.getWhoClicked() instanceof Player player) {
                    player.getLocation().add(0, offsetY, 0).getBlock().setType(material);
                }
            });
        }

        // 7) Items: dropping or giving
        public ClickAction giveItemOnSuccess(ItemStack itemStack) {
            return onSuccess(e -> {
                if (e.getWhoClicked() instanceof Player player) {
                    HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(itemStack);
                    leftovers.values().forEach(left ->
                            player.getWorld().dropItem(player.getLocation(), left)
                    );
                }
            });
        }

        public ClickAction giveItemOnFail(ItemStack itemStack) {
            return onFail(e -> {
                if (e.getWhoClicked() instanceof Player player) {
                    HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(itemStack);
                    leftovers.values().forEach(left ->
                            player.getWorld().dropItem(player.getLocation(), left)
                    );
                }
            });
        }

        public ClickAction dropItemOnSuccess(ItemStack itemStack) {
            return onSuccess(e -> {
                if (e.getWhoClicked() instanceof Player player) {
                    player.getWorld().dropItem(player.getLocation(), itemStack);
                }
            });
        }

        public ClickAction dropItemOnFail(ItemStack itemStack) {
            return onFail(e -> {
                if (e.getWhoClicked() instanceof Player player) {
                    player.getWorld().dropItem(player.getLocation(), itemStack);
                }
            });
        }
    }
}
