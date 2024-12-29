package org.Nysxl.Components.PermissionChecker;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.Nysxl.InventoryManager.DynamicButton;
import org.Nysxl.InventoryManager.DynamicConfigManager;
import org.Nysxl.InventoryManager.DynamicPagedInventoryHandler;
import org.Nysxl.NysxlServerUtils;
import org.Nysxl.Utils.TextComponentBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class DynamicPermissionCheckCommand implements Listener {

    private static final List<Requests> activeRequests = new ArrayList<>();
    private static final Map<UUID, String> pendingRequests = new HashMap<>();
    private final DynamicConfigManager configManager;

    public DynamicPermissionCheckCommand(DynamicConfigManager configManager) {
        this.configManager = configManager;

        // Load requests from configuration
        List<Requests> loadedRequests = configManager.loadObjects(
                "ActiveCommandRequests",
                "activeRequests",
                map -> {
                    try {
                        UUID uuid = UUID.fromString((String) map.get("uuid"));
                        String command = (String) map.get("command");
                        String reason = (String) map.get("reason");
                        return new Requests(uuid, command, reason);
                    } catch (Exception e) {
                        Bukkit.getLogger().warning("Failed to load a request from configuration: " + e.getMessage());
                        return null;
                    }
                }
        );

        if (loadedRequests != null) {
            activeRequests.addAll(loadedRequests);
        }

        Bukkit.getLogger().info("Loaded " + activeRequests.size() + " requests from config.");
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();
        String command = message.split(" ")[0].substring(1).toLowerCase();

        PluginCommand pluginCommand = Bukkit.getPluginCommand(command);
        if (pluginCommand != null && pluginCommand.getPermission() != null && !player.hasPermission(pluginCommand.getPermission())) {
            event.setCancelled(true);

            TextComponent messageComponent = TextComponentBuilder.create()
                    .addStyledText("&5You don't have permission for this command. \n", ChatColor.GRAY)
                    .addStyledText("[Click here]", ChatColor.GOLD)
                    .setStyle(true, false, true)
                    .setClickAction(ClickEvent.Action.RUN_COMMAND, "/reason " + command)
                    .setHoverAction("Request Permission for: " + command)
                    .addStyledText(" to explain why you should have access", ChatColor.GRAY)
                    .build();

            player.spigot().sendMessage(messageComponent);
        }
    }

    public static void handleReason(Player player, String commandName) {
        if (commandName == null || commandName.isEmpty()) {
            player.sendMessage(ChatColor.RED + "You must specify a valid command name.");
            return;
        }

        pendingRequests.put(player.getUniqueId(), commandName);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Please type in chat why you believe you should have access to the " +
                ChatColor.AQUA + "/" + commandName + ChatColor.LIGHT_PURPLE + " command.");
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (pendingRequests.containsKey(uuid)) {
            event.setCancelled(true);
            String commandName = pendingRequests.remove(uuid);
            String reason = event.getMessage();

            activeRequests.add(new Requests(uuid, commandName, reason));
            player.sendMessage(ChatColor.GREEN + "Your request to access the " + ChatColor.AQUA + "/" + commandName +
                    ChatColor.GREEN + " command has been submitted.");

            saveActiveRequests(NysxlServerUtils.getPorfileConfigManager());
        }
    }

    public void register(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static void displayActiveRequests(Player player) {
        DynamicPagedInventoryHandler gui = new DynamicPagedInventoryHandler(
                "Active Requests",
                54,
                null,
                NysxlServerUtils.getInstance()
        );

        for (Requests request : activeRequests) {
            createAndAddButton(gui, request, player);
        }

        gui.open(player);
    }


    private static void createAndAddButton(DynamicPagedInventoryHandler gui, Requests request, Player player) {
        List<String> lore = new ArrayList<>();
        String playerName = Optional.ofNullable(Bukkit.getOfflinePlayer(request.uuid()).getName()).orElse("Unknown");

        lore.add("§ePlayer: " + playerName);
        lore.add("§7Reason: " + request.reason());
        lore.add("§cRight-click to remove this request.");

        String permission = Optional.ofNullable(Bukkit.getPluginCommand(request.command()))
                .map(PluginCommand::getPermission)
                .orElse("N/A");

        DynamicButton button = DynamicButton.builder()
                .material(Material.PAPER)
                .displayName("§6Command: " + request.command() + " : " + permission)
                .lore(lore)
                .build();

        button.setClickAction(ClickType.RIGHT, DynamicButton.ClickAction.create().onSuccess(event -> {
            activeRequests.remove(request);
            gui.removeButton(button, (Player) event.getWhoClicked());
            ((Player) event.getWhoClicked()).sendMessage(ChatColor.GREEN + "Request removed.");
            saveActiveRequests(NysxlServerUtils.getPorfileConfigManager());
        }));


        gui.addButton(button, player);
    }

    public static void saveActiveRequests(DynamicConfigManager configManager) {
        configManager.saveObjects(
                "ActiveCommandRequests",
                "activeRequests",
                activeRequests,
                req -> Map.of(
                        "uuid", req.uuid().toString(),
                        "command", req.command(),
                        "reason", req.reason()
                )
        );
    }

    public record Requests(UUID uuid, String command, String reason) {}
}
