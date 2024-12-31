package org.Nysxl.InventoryManager;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class DynamicPagedInventoryHandler extends DynamicInventoryHandler {

    // Core storage
    private final Set<DynamicButton> allButtons = Collections.synchronizedSet(new LinkedHashSet<>());
    private final Set<DynamicDisplay> allDisplays = Collections.synchronizedSet(new LinkedHashSet<>());

    // Navigation state
    private volatile int currentPage = 0;
    private volatile int nextPageSlot = -1;
    private volatile int prevPageSlot = -1;
    private volatile int searchSlot = -1;
    private volatile boolean searchEnabled = false;

    // Layout management
    private final Set<Integer> reservedSlots = ConcurrentHashMap.newKeySet();
    private final Set<Integer> validSlots = ConcurrentHashMap.newKeySet();
    private volatile int pageCapacity;
    private volatile int rowsPerPage;
    private int inventorySize;

    // Search functionality
    private static final Map<UUID, DynamicPagedInventoryHandler> awaitingSearchMap = new ConcurrentHashMap<>();
    private volatile String lastSearchQuery = null;
    private Predicate<DynamicButton> searchPredicate;
    private final Map<String, List<DynamicButton>> searchCache = new ConcurrentHashMap<>();

    // Dynamic content systems
    private final Map<Integer, RefreshableSlot> refreshableSlots = new ConcurrentHashMap<>();
    private final Map<Integer, ItemAnimation> animations = new ConcurrentHashMap<>();
    private final Map<String, InventorySection> sections = new ConcurrentHashMap<>();

    // Refresh task
    private BukkitRunnable refreshTask;
    private final Plugin plugin;
    private final Runnable onClose;

    // Configuration
    private boolean soundEnabled = true;
    private Sound clickSound = Sound.UI_BUTTON_CLICK;

    // Inner classes
    class InventorySection {
        private final String name;
        private final List<Integer> slots = new ArrayList<>();

        public InventorySection(String name) {
            this.name = name;
        }

        public void addSlot(int slot) {
            validateSlot(slot);
            slots.add(slot);
        }

        public List<Integer> getSlots() {
            return Collections.unmodifiableList(slots);
        }

        public String getName() {
            return name;
        }
    }

    class RefreshableSlot {
        private final DynamicButton button;
        private final int refreshInterval;
        private int tickCounter;

        public RefreshableSlot(DynamicButton button, int refreshInterval) {
            this.button = button;
            this.refreshInterval = refreshInterval;
            this.tickCounter = 0;
        }

        public boolean shouldRefresh() {
            return tickCounter >= refreshInterval;
        }

        public void tick(Player player) {
            if (shouldRefresh()) {
                tickCounter = 0;
                if (button != null) {
                    button.update();
                }
                setButton(refreshableSlots.entrySet().stream()
                        .filter(entry -> entry.getValue() == this)
                        .findFirst()
                        .map(Map.Entry::getKey)
                        .orElse(-1), button);
            } else {
                tickCounter++;
            }
        }

        public DynamicButton getButton() {
            return button;
        }
    }

    class ItemAnimation {
        private final List<ItemStack> frames;
        private int currentFrame;
        private final List<Integer> frameIntervals;
        private int tickCounter;

        public ItemAnimation(List<ItemStack> frames, List<Integer> frameIntervals) {
            if (frames == null || frames.isEmpty()) {
                throw new IllegalArgumentException("Frames cannot be null or empty");
            }
            if (frameIntervals.size() != frames.size()) {
                throw new IllegalArgumentException("Frame intervals must match frame count");
            }
            this.frames = new ArrayList<>(frames);
            this.frameIntervals = new ArrayList<>(frameIntervals);
            this.currentFrame = 0;
            this.tickCounter = 0;
        }

        public ItemStack getCurrentFrame() {
            return frames.get(currentFrame);
        }

        public void tick() {
            if (++tickCounter >= frameIntervals.get(currentFrame)) {
                tickCounter = 0;
                currentFrame = (currentFrame + 1) % frames.size();
            }
        }
    }

    public DynamicPagedInventoryHandler(String title, int size, Runnable onClose, Plugin plugin) {
        super(title, size, onClose);
        this.plugin = plugin;
        this.onClose = onClose;
        this.inventorySize = size;
        this.searchPredicate = this::defaultSearchPredicate;
        initializeSlots();
        startRefreshTask();
    }

    public void setInventorySize(int size) {
        this.inventorySize = size;
        initializeSlots();
    }

    private void initializeSlots() {
        validSlots.clear();
        IntStream.range(0, inventorySize).forEach(validSlots::add);
        recalcPageCapacity();
    }

    private void startRefreshTask() {
        refreshTask = new BukkitRunnable() {
            @Override
            public void run() {
                Player player = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
                if (player == null || !player.isOnline()) {
                    cancel();
                    return;
                }
                refreshableSlots.forEach((slot, refreshableSlot) -> refreshableSlot.tick(player));
                animations.values().forEach(ItemAnimation::tick);
            }
        };
        refreshTask.runTaskTimer(plugin, 0L, 1L); // Run every tick
    }

    public void addRefreshableSlot(int slot, DynamicButton button, int refreshInterval) {
        validateSlot(slot);
        refreshableSlots.put(slot, new RefreshableSlot(button, refreshInterval));
    }

    public void addItemAnimation(int slot, ItemAnimation animation) {
        validateSlot(slot);
        animations.put(slot, animation);
    }

    public void setNextPageSlot(int slot) {
        validateSlot(slot);
        if (slot >= 0) {
            reservedSlots.add(slot);
        } else {
            reservedSlots.remove(this.nextPageSlot);
        }
        this.nextPageSlot = slot;
        recalcPageCapacity();
    }

    public void addReserverdSlot(int slot) {
        validateSlot(slot);
        reservedSlots.add(slot);
    }

    public void removeReservedSlot(int slot) {
        reservedSlots.remove(slot);
    }

    public void addReservedSlots(int... slots) {
        for (int slot : slots) {
            addReserverdSlot(slot);
        }
    }

    public void removeReservedSlots(int... slots) {
        for (int slot : slots) {
            removeReservedSlot(slot);
        }
    }

    public void setPrevPageSlot(int slot) {
        validateSlot(slot);
        if (slot >= 0) {
            reservedSlots.add(slot);
        } else {
            reservedSlots.remove(this.prevPageSlot);
        }
        this.prevPageSlot = slot;
        recalcPageCapacity();
    }

    public void setSearchSlot(int slot) {
        validateSlot(slot);
        if (slot >= 0) {
            searchEnabled = true;
            reservedSlots.add(slot);
        } else {
            searchEnabled = false;
            reservedSlots.remove(this.searchSlot);
        }
        this.searchSlot = slot;
        recalcPageCapacity();
    }

    public void setNavButtonSlots(int nextButton, int prevButton){
        setNextPageSlot(nextButton);
        setPrevPageSlot(prevButton);
    }

    public void setNavButtonSlots(int nextButton, int prevButton, int searchButton){
        setNextPageSlot(nextButton);
        setPrevPageSlot(prevButton);
        setSearchSlot(searchButton);
    }

    public void openPage(Player player, int page) {
        currentPage = Math.max(0, page);
        clearAll();
        renderPage(player, currentPage);
    }

    private void renderPage(Player player, int pageIndex) {
        List<DynamicButton> source = getFilteredButtons();
        List<Integer> availableSlots = findContentSlots();

        int startIndex = pageIndex * pageCapacity;
        int endIndex = Math.min(startIndex + pageCapacity, source.size());

        availableSlots.forEach(slot -> getInventory().clear(slot));

        int itemCursor = 0;
        for (int i = startIndex; i < endIndex && itemCursor < availableSlots.size(); i++) {
            setButton(availableSlots.get(itemCursor++), source.get(i));
        }

        updateNavigationButtons(player, pageIndex, source.size(), availableSlots.size());
    }

    private void updateNavigationButtons(Player player, int pageIndex, int totalItems, int availableSlots) {
        if (nextPageSlot >= 0 && pageIndex * pageCapacity + availableSlots < totalItems) {
            setButton(nextPageSlot, createNavButton(Material.ARROW, "§aNext Page",
                    e -> openPage(player, pageIndex + 1)));
        }

        if (prevPageSlot >= 0 && pageIndex > 0) {
            setButton(prevPageSlot, createNavButton(Material.ARROW, "§cPrevious Page",
                    e -> openPage(player, pageIndex - 1)));
        }

        if (searchEnabled && searchSlot >= 0) {
            setButton(searchSlot, createNavButton(Material.NAME_TAG, "§eSearch",
                    e -> promptPlayerForSearch(player)));
        }
    }

    private DynamicButton createNavButton(Material material, String title,
                                          Consumer<InventoryClickEvent> eventConsumer) {
        return DynamicButton.builder()
                .material(material)
                .displayName(title)
                .onClick(ClickType.LEFT, DynamicButton.ClickAction.create().onSuccess(eventConsumer))
                .build();
    }
    private List<Integer> findContentSlots() {
        return IntStream.range(0, inventorySize)
                .filter(i -> !reservedSlots.contains(i))
                .boxed()
                .collect(Collectors.toList());
    }

    private void recalcPageCapacity() {
        Set<Integer> availableSlots = new HashSet<>(validSlots);
        availableSlots.removeAll(reservedSlots);
        pageCapacity = availableSlots.size();
        rowsPerPage = pageCapacity / 9;
    }

    private void validateSlot(int slot) {
        if (slot < -1 || slot >= inventorySize) {
            throw new IllegalArgumentException("Invalid slot number: " + slot);
        }
    }

    private List<DynamicButton> getFilteredButtons() {
        if (!searchEnabled || lastSearchQuery == null) {
            return new ArrayList<>(allButtons);
        }
        return searchCache.computeIfAbsent(lastSearchQuery, key ->
                allButtons.stream()
                        .filter(searchPredicate)
                        .collect(Collectors.toList())
        );
    }

    private boolean defaultSearchPredicate(DynamicButton button) {
        if (lastSearchQuery == null) return true;
        var itemStack = button.getItemStack();
        if (itemStack == null) return false;
        var itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) return false;
        String displayName = itemMeta.getDisplayName();
        if (displayName == null) return false;

        return displayName.toLowerCase().contains(lastSearchQuery.toLowerCase());
    }

    private void invalidateSearchCache() {
        searchCache.clear();
    }

    private void promptPlayerForSearch(Player player) {
        player.closeInventory();
        awaitingSearchMap.put(player.getUniqueId(), this);
        player.sendMessage("§ePlease type your search query in chat. Type 'cancel' to abort.");
    }

    public void addButton(DynamicButton button, Player player) {
        allButtons.add(button);
        invalidateSearchCache();
        openPage(player, currentPage);
    }

    public void addDisplay(DynamicDisplay display, Player player) {
        allDisplays.add(display);
        openPage(player, currentPage);
    }

    public void removeButton(DynamicButton button, Player player) {
        allButtons.remove(button);
        invalidateSearchCache();
        if (currentPage * pageCapacity >= allButtons.size() && currentPage > 0) {
            currentPage--;
        }
        openPage(player, currentPage);
    }

    public void removeDisplay(DynamicDisplay display, Player player){
        allDisplays.remove(display);
        if (currentPage * pageCapacity >= allButtons.size() && currentPage > 0) {
            currentPage--;
        }
        openPage(player, currentPage);
    }

    public void clearAllButtons(Player player) {
        allButtons.clear();
        searchCache.clear();
        lastSearchQuery = null;
        currentPage = 0;
        openPage(player, currentPage);
    }

    public void logInventoryState() {
        System.out.println("Current Page: " + currentPage);
        System.out.println("Total Buttons: " + allButtons.size());
        System.out.println("Refreshable Slots: " + refreshableSlots.size());
        System.out.println("Animations: " + animations.size());
    }

    public void cleanup(Player player) {
        awaitingSearchMap.remove(player.getUniqueId());
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        clearAllButtons(player);
        if (onClose != null) {
            onClose.run();
        }
    }

    protected Runnable getOnClose() {
        return this.onClose;
    }
}
