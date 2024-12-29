package org.Nysxl.Components.Profiles;

import net.md_5.bungee.api.ChatColor;
import org.Nysxl.InventoryManager.DynamicButton;
import org.Nysxl.InventoryManager.DynamicInventoryHandler;
import org.Nysxl.NysxlServerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.Nysxl.Utils.PlaceholderAPI.parsePlaceholders;

public class ProfileViewer {

    private static FileConfiguration profileConfig;

    public ProfileViewer() {
        profileConfig = NysxlServerUtils.getConfigManager().getConfig("ProfileViewerConfig");
        if (profileConfig == null) {
            throw new IllegalStateException("ProfileViewerConfig.yml is not loaded!");
        }
    }

    public static void openProfile(Player player, Player target) {
        if (profileConfig == null) {
            player.sendMessage(ChatColor.RED + "Profile configuration is not available.");
            return;
        }

        List<Map<String, Object>> items = getConfigItems("profile.items");
        if (items.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No profile items configured.");
            return;
        }

        // Create a dynamic inventory of size 54 for the target's profile
        DynamicInventoryHandler profile = new DynamicInventoryHandler(
                target.getDisplayName() + "'s Profile",
                54,
                null
        );

        // For each item in the config, create a DynamicButton and place it in the correct slot
        items.forEach(itemConfig -> {
            int slot = getIntOrDefault(itemConfig, "slot", -1);
            if (slot < 0 || slot >= profile.getInventory().getSize()) return;

            // Build a DynamicButton with all item (including skull) logic
            DynamicButton button = createDynamicButton(itemConfig, player, target);
            if (button == null) return;

            profile.setButton(slot, button);
        });

        profile.open(player);
    }

    /**
     * Reads all items from the specified path in the config.
     */
    private static List<Map<String, Object>> getConfigItems(String path) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) profileConfig.getList(path);
        return (items != null) ? items : List.of();
    }

    /**
     * Creates a DynamicButton (and underlying ItemStack) from the config,
     * including player-head logic when needed.
     */
    private static DynamicButton createDynamicButton(Map<String, Object> itemConfig, Player player, Player target) {
        // Grab the basic item info
        Material material = getMaterialOrDefault(itemConfig, "material", Material.STONE);
        String title = (String) itemConfig.getOrDefault("title", "Untitled");
        List<String> lore = (List<String>) itemConfig.getOrDefault("lore", List.of());

        // Parse placeholders / colorize the title
        title = colorize(parsePlaceholders(player, title.replace("%target%", target.getName())));
        // Parse placeholders / colorize each line of lore
        List<String> colorizedLore = lore.stream()
                .map(line -> colorize(parsePlaceholders(player, line.replace("%target%", target.getName()))))
                .collect(Collectors.toList());

        // Start building the DynamicButton
        DynamicButton.Builder buttonBuilder = DynamicButton.builder()
                .material(material)
                .displayName(title)
                .lore(colorizedLore);

        // If this is a player head, set the owner via the builder
        if (material == Material.PLAYER_HEAD) {
            String uuidString = (String) itemConfig.get("uuid");
            String playerName = (String) itemConfig.get("playerName");

            if (uuidString != null) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                    // Only set the headOwner if the server recognizes the player
                    if (offlinePlayer.hasPlayedBefore() || offlinePlayer.isOnline()) {
                        buttonBuilder.headOwner(uuid);
                    } else {
                        Bukkit.getLogger().warning("UUID not associated with a known player: " + uuidString);
                    }
                } catch (IllegalArgumentException e) {
                    Bukkit.getLogger().warning("Invalid UUID: " + uuidString);
                }
            } else if (playerName != null && !playerName.isEmpty()) {
                buttonBuilder.headOwner(playerName);
            } else {
                Bukkit.getLogger().warning("PLAYER_HEAD requires either a 'uuid' or 'playerName'.");
            }
        }

        // Bind any commands to click actions (left/right/shift/etc.)
        bindClickActions(buttonBuilder, itemConfig, player, target);

        // Build and return the final DynamicButton
        return buttonBuilder.build();
    }

    /**
     * Reads click commands from the config and binds them to the button builder.
     */
    private static void bindClickActions(DynamicButton.Builder buttonBuilder,
                                         Map<String, Object> itemConfig,
                                         Player player,
                                         Player target) {
        Map<ClickType, String> actions = Map.of(
                ClickType.LEFT, "leftClickCommand",
                ClickType.RIGHT, "rightClickCommand",
                ClickType.SHIFT_LEFT, "shiftLeftClickCommand",
                ClickType.SHIFT_RIGHT, "shiftRightClickCommand",
                ClickType.MIDDLE, "middleClickCommand"
        );

        actions.forEach((clickType, configKey) -> {
            String command = (String) itemConfig.getOrDefault(configKey, "");
            if (!command.isEmpty()) {
                buttonBuilder.onClick(clickType, DynamicButton.ClickAction.create().onSuccess(event -> executeCommand(player, target, command)));
            }
        });
    }

    /**
     * Convenience method to parse placeholders and run a command as the player.
     */
    private static void executeCommand(Player player, Player target, String command) {
        // Replace %target% in the command, then parse the rest with PlaceholderAPI
        String parsedCommand = parsePlaceholders(player, command.replace("%target%", target.getName()));
        player.performCommand(parsedCommand);
    }

    /**
     * Tries to parse a material from the config; defaults if unknown.
     */
    private static Material getMaterialOrDefault(Map<String, Object> itemConfig,
                                                 String key,
                                                 Material defaultMaterial) {
        String materialName = (String) itemConfig.getOrDefault(key, defaultMaterial.name());
        try {
            return Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultMaterial;
        }
    }

    /**
     * Safely gets an int from the config map or returns the default value.
     */
    private static int getIntOrDefault(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        return (value instanceof Integer) ? (int) value : defaultValue;
    }

    /**
     * Handles color codes including hex (&#rrggbb) and '&'-based codes.
     */
    private static String colorize(String text) {
        // Replace our hex codes first
        Pattern hexPattern = Pattern.compile("(?i)&#([0-9A-Fa-f]{6})");
        Matcher matcher = hexPattern.matcher(text);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : hex.toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(buffer, replacement.toString());
        }
        matcher.appendTail(buffer);

        // Then translate the remaining '&' color codes
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
