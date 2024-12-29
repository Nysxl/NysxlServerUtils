package org.Nysxl.Utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class PlaceholderAPI {

    /**
     * Checks if PlaceholderAPI is available on the server.
     *
     * @return true if PlaceholderAPI is detected, false otherwise.
     */
    public static boolean isPlaceholderAPIPresent() {
        return Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    /**
     * Parses placeholders for a player using PlaceholderAPI.
     *
     * @param player The player context for placeholders.
     * @param text   The text containing PlaceholderAPI placeholders.
     * @return The text with placeholders parsed, or the original text if PlaceholderAPI is not available.
     */
    public static String parsePlaceholders(Player player, String text) {
        if (isPlaceholderAPIPresent() && player != null) {
            try {
                return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
            } catch (Exception e) {
                Bukkit.getLogger().warning("Failed to parse placeholders for text: " + text);
                e.printStackTrace();
            }
        }
        return text; // Return unmodified text if PlaceholderAPI is not available
    }

    /**
     * Parses placeholders for a list of strings.
     *
     * @param player The player context for placeholders.
     * @param texts  The list of texts containing PlaceholderAPI placeholders.
     * @return A list of texts with placeholders parsed, or the original texts if PlaceholderAPI is not available.
     */
    public static List<String> parsePlaceholders(Player player, List<String> texts) {
        return texts.stream()
                .map(text -> parsePlaceholders(player, text))
                .collect(Collectors.toList());
    }

    /**
     * Parses placeholders for a player with a fallback text.
     *
     * @param player   The player context for placeholders.
     * @param text     The text containing PlaceholderAPI placeholders.
     * @param fallback The fallback text to use if parsing fails.
     * @return The parsed text or the fallback if parsing fails or PlaceholderAPI is unavailable.
     */
    public static String parsePlaceholdersWithFallback(Player player, String text, String fallback) {
        String parsed = parsePlaceholders(player, text);
        return parsed.equals(text) ? fallback : parsed;
    }
}
