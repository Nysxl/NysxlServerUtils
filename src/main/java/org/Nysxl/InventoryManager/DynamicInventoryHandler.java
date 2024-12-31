package org.Nysxl.InventoryManager;

import org.Nysxl.NysxlServerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A dynamic inventory manager that can handle "free slots" (normal player usage),
 * plugin-driven "dynamic slots" (click actions), and so on.
 */
public class DynamicInventoryHandler {

    private static final Map<Player, DynamicInventoryHandler> activeInventories = new HashMap<>();

    private final Inventory inventory;
    private final Runnable onClose;

    // Old approach: slot -> Consumer<InventoryClickEvent>
    private final Map<Integer, Consumer<InventoryClickEvent>> clickActions = new HashMap<>();

    // New approach: slot -> SlotAction
    private final Map<Integer, SlotAction> slotActions = new HashMap<>();

    /**
     * A set of "free" slots that the plugin won't cancel or run code for.
     *
     * If a slot is in freeSlots:
     *   - The event is NOT cancelled for that slot.
     *   - No old clickActions or new slotActions are invoked.
     *   - "addButton(...)" will skip it (we don't want to overwrite a player's free slot).
     */
    private final Set<Integer> freeSlots = new HashSet<>();

    /**
     * If true, we block *all* shift-click attempts in this inventory.
     * By default, false. (Optional, if you still want to forbid shift-click.)
     */
    private boolean disableAllShiftClick = false;

    /**
     * Creates a dynamic inventory with the specified title, size, and onClose callback.
     *
     * @param title   Inventory title
     * @param size    Inventory size (must be multiple of 9)
     * @param onClose A callback run when the player closes the inventory (can be null)
     */
    public DynamicInventoryHandler(String title, int size, Runnable onClose) {
        this.inventory = Bukkit.createInventory(null, size, title);
        this.onClose = onClose;
    }

    // ------------------------------------------------------------------------
    // Basic open / close
    // ------------------------------------------------------------------------
    public void open(Player player) {
        Bukkit.getScheduler().runTask(NysxlServerUtils.getInstance(), () -> {
            player.openInventory(inventory);
            activeInventories.put(player, this);
        });
    }

    public Inventory getInventory() {
        return this.inventory;
    }

    public static void registerGlobal(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(new InventoryEventListener(), plugin);
    }

    // ------------------------------------------------------------------------
    // SHIFT-click control (optional)
    // ------------------------------------------------------------------------
    public void setDisableAllShiftClick(boolean disable) {
        this.disableAllShiftClick = disable;
    }

    // ------------------------------------------------------------------------
    // FREE SLOTS
    // ------------------------------------------------------------------------

    /**
     * Makes a slot "free," meaning the plugin won't cancel the event or run any click actions.
     * The user can freely put in / take out items (like a normal chest slot).
     *
     * This also clears any existing plugin logic (clickActions or slotActions) from that slot.
     */
    public void makeSlotFree(int slot) {
        clearItem(slot); // remove any plugin logic + item
        freeSlots.add(slot);
    }

    /**
     * Makes multiple slots "free" at once.
     */
    public void makeSlotFree(int... slots) {
        for (int slot : slots) {
            makeSlotFree(slot);
        }
    }

    /**
     * If you want to revert a slot to plugin control, call this to remove it from freeSlots
     * so you can set plugin actions or items again.
     */
    public void makeSlotPluginControlled(int slot) {
        freeSlots.remove(slot);
    }

    /**
     * Checks if a slot is currently in "free" mode.
     */
    public boolean isSlotFree(int slot) {
        return freeSlots.contains(slot);
    }

    // ------------------------------------------------------------------------
    // OLD Approach: setButton(...) or setButton(slot, Consumer)
    // ------------------------------------------------------------------------

    /**
     * Set a DynamicButton in a slot, as the old approach.
     * If the slot is "free," we skip it entirely.
     */
    public void setButton(int slot, DynamicButton button) {
        if (isSlotFree(slot)) {
            return; // user specifically wants this slot for normal items
        }
        if (button != null) {
            inventory.setItem(slot, button.getItemStack());
            clickActions.put(slot, button::handleClick);
        } else {
            clearItem(slot);
        }
        // remove new approach
        slotActions.remove(slot);
    }

    /**
     * Assign a Consumer to a slot (old approach).
     * If the slot is free, we skip.
     */
    public void setButton(int slot, Consumer<InventoryClickEvent> action) {
        if (isSlotFree(slot)) {
            return;
        }
        clickActions.put(slot, action);
        slotActions.remove(slot);
    }

    /**
     * Set a display item in a slot, as the old approach.
     * @param slot The slot to set the display in
     * @param display The display to set
     */
    public void setDisplay(int slot, DynamicDisplay display) {
        if (isSlotFree(slot)) {
            return;
        }
        if (display != null) {
            inventory.setItem(slot, display.getItemStack());
            display.startPeriodicUpdates(1);
        } else {
            clearItem(slot);
        }
        // remove new approach
        slotActions.remove(slot);
    }

    /**
     * Automatically finds the first empty (non-free) slot and places a DynamicButton there.
     */
    public void addButton(DynamicButton button) {
        int emptySlot = findFirstEmptySlot();
        if (emptySlot >= 0) {
            setButton(emptySlot, button);
        } else {
            throw new IllegalStateException("No empty (non-free) slots available in the inventory.");
        }
    }

    /**
     * Automatically finds the first empty (non-free) slot and places a DynamicDisplay there.
     */
    public void addDisplay(DynamicDisplay display){
        int emptySlot = findFirstEmptySlot();
        if (emptySlot >= 0) {
            inventory.setItem(emptySlot, display.getItemStack());
            display.startPeriodicUpdates(1);
        } else {
            throw new IllegalStateException("No empty (non-free) slots available in the inventory.");
        }
    }

    private int findFirstEmptySlot() {
        for (int i = 0; i < inventory.getSize(); i++) {
            // If the slot is free, skip
            if (isSlotFree(i)) {
                continue;
            }
            // If there's no item, we can use it
            if (inventory.getItem(i) == null) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------------
    // NEW Approach: setSlotAction(...) with checks, success/fail, etc.
    // ------------------------------------------------------------------------

    /**
     * Assigns a SlotAction to a specific slot. If that slot is free, we skip.
     */
    public void setSlotAction(int slot, SlotAction slotAction, ItemStack displayItem) {
        if (isSlotFree(slot)) {
            return;
        }
        if (slotAction == null) {
            clearItem(slot);
            return;
        }
        slotActions.put(slot, slotAction);
        // remove old approach
        clickActions.remove(slot);

        // If a display item is provided, place it
        if (displayItem != null) {
            inventory.setItem(slot, displayItem);
        }
    }

    /**
     * removes a slot action from a slot
     */
    public void removeSlotAction(int slot) {
        slotActions.remove(slot);
    }

    // ------------------------------------------------------------------------
    // Clearing items / actions
    // ------------------------------------------------------------------------

    /**
     * Clears plugin logic from a slot (old approach + new approach),
     * and also removes the item in that slot from the inventory.
     *
     * Note: It does NOT remove the slot from the freeSlots set if it was free.
     */
    public void clearItem(int slot) {
        inventory.clear(slot);
        clickActions.remove(slot);
        slotActions.remove(slot);
    }

    /**
     * Clears all items and plugin actions from the inventory,
     * but does not remove "free" status from any slots.
     * (They remain free if they were free.)
     */
    public void clearAll() {
        inventory.clear();
        clickActions.clear();
        slotActions.clear();
    }

    // ------------------------------------------------------------------------
    // Event Handling
    // ------------------------------------------------------------------------
    private void handleClickEvent(InventoryClickEvent event) {
        // If entire SHIFT-click is disabled
        if (disableAllShiftClick && event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        int slot = event.getSlot();
        // If the slot is free, we do NOT cancel or run plugin code
        if (isSlotFree(slot)) {
            // Let them move items in/out
            return;
        }

        // For non-free slots, we typically cancel the movement so plugin logic can apply
        event.setCancelled(true);

        // Check new approach first
        SlotAction newAction = slotActions.get(slot);
        if (newAction != null) {
            newAction.execute(event);
            return;
        }

        // Fallback to old approach
        Consumer<InventoryClickEvent> oldAction = clickActions.get(slot);
        if (oldAction != null) {
            oldAction.accept(event);
        }
    }

    // ------------------------------------------------------------------------
    // InventoryEventListener
    // ------------------------------------------------------------------------
    public static class InventoryEventListener implements Listener {

        @EventHandler
        public void onInventoryClick(InventoryClickEvent event) {
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            DynamicInventoryHandler handler = activeInventories.get(player);
            if (handler == null) {
                return; // Not one of ours
            }

            // Check if they're clicking in the top inventory
            if (!event.getView().getTopInventory().equals(handler.getInventory())) {
                return;
            }

            handler.handleClickEvent(event);
        }

        @EventHandler
        public void onInventoryClose(InventoryCloseEvent event) {
            if (!(event.getPlayer() instanceof Player player)) {
                return;
            }

            DynamicInventoryHandler handler = activeInventories.remove(player);
            if (handler != null && handler.onClose != null) {
                handler.onClose.run();
            }
        }
    }

    // ------------------------------------------------------------------------
    // SlotAction class (unchanged, includes checks, success/fail, etc.)
    // ------------------------------------------------------------------------
    public static class SlotAction {
        private final List<Predicate<InventoryClickEvent>> checks = new ArrayList<>();
        private final List<Consumer<InventoryClickEvent>> successActions = new ArrayList<>();
        private final List<Consumer<InventoryClickEvent>> failActions = new ArrayList<>();

        // (Could also have a 'protectedSlot' if you want, but not mandatory here)
        public SlotAction() {}

        public static SlotAction create() {
            return new SlotAction();
        }

        public void execute(InventoryClickEvent event) {
            for (Predicate<InventoryClickEvent> check : checks) {
                if (!check.test(event)) {
                    runFail(event);
                    return;
                }
            }
            runSuccess(event);
        }

        private void runSuccess(InventoryClickEvent event) {
            for (Consumer<InventoryClickEvent> action : successActions) {
                action.accept(event);
            }
        }

        private void runFail(InventoryClickEvent event) {
            for (Consumer<InventoryClickEvent> action : failActions) {
                action.accept(event);
            }
        }

        public SlotAction check(Predicate<InventoryClickEvent> condition) {
            checks.add(condition);
            return this;
        }

        public SlotAction onSuccess(Consumer<InventoryClickEvent> success) {
            successActions.add(success);
            return this;
        }

        public SlotAction onFail(Consumer<InventoryClickEvent> fail) {
            failActions.add(fail);
            return this;
        }

        // Utility examples
        public SlotAction requirePlayer() {
            return check(e -> e.getWhoClicked() instanceof Player);
        }

        public SlotAction withPermission(String permission) {
            return check(e -> e.getWhoClicked().hasPermission(permission));
        }

        public SlotAction playSoundOnSuccess(Sound sound, float volume, float pitch) {
            return onSuccess(e -> {
                if (e.getWhoClicked() instanceof Player p) {
                    p.playSound(p.getLocation(), sound, volume, pitch);
                }
            });
        }

        public SlotAction playSoundOnFail(Sound sound, float volume, float pitch) {
            return onFail(e -> {
                if (e.getWhoClicked() instanceof Player p) {
                    p.playSound(p.getLocation(), sound, volume, pitch);
                }
            });
        }

        public SlotAction runAsConsoleOnSuccess(String consoleCommand) {
            return onSuccess(e -> {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCommand);
                    }
                }.runTask(Bukkit.getPluginManager().getPlugins()[0]);
            });
        }

        public SlotAction runAsConsoleOnFail(String consoleCommand) {
            return onFail(e -> {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCommand);
                    }
                }.runTask(Bukkit.getPluginManager().getPlugins()[0]);
            });
        }

        public SlotAction runAsOpOnSuccess(Consumer<Player> logic) {
            return onSuccess(e -> {
                if (e.getWhoClicked() instanceof Player p) {
                    boolean wasOp = p.isOp();
                    if (!wasOp) {
                        p.setOp(true);
                    }
                    try {
                        logic.accept(p);
                    } finally {
                        if (!wasOp) {
                            p.setOp(false);
                        }
                    }
                }
            });
        }

        public SlotAction runAsOpOnFail(Consumer<Player> logic) {
            return onFail(e -> {
                if (e.getWhoClicked() instanceof Player p) {
                    boolean wasOp = p.isOp();
                    if (!wasOp) {
                        p.setOp(true);
                    }
                    try {
                        logic.accept(p);
                    } finally {
                        if (!wasOp) {
                            p.setOp(false);
                        }
                    }
                }
            });
        }

        public SlotAction spawnMobOnSuccess(EntityType type, int amount) {
            return onSuccess(e -> {
                if (e.getWhoClicked() instanceof Player p) {
                    for (int i = 0; i < amount; i++) {
                        p.getWorld().spawnEntity(p.getLocation(), type);
                    }
                }
            });
        }

        public SlotAction spawnMobOnFail(EntityType type, int amount) {
            return onFail(e -> {
                if (e.getWhoClicked() instanceof Player p) {
                    for (int i = 0; i < amount; i++) {
                        p.getWorld().spawnEntity(p.getLocation(), type);
                    }
                }
            });
        }
    }
}
