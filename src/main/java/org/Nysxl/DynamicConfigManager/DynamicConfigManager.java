package org.Nysxl.InventoryManager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A manager for dynamically creating/loading/saving .yml configs.
 * It provides generic methods to save/load any List<T> where T
 * can be converted to/from a Map<String, Object>.
 */
public class DynamicConfigManager {

    private final JavaPlugin plugin;
    private final Map<String, FileConfiguration> cachedConfigs = new HashMap<>();

    public DynamicConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public FileConfiguration loadOrCreateDefaultConfig(String configName) {
        File file = new File(plugin.getDataFolder(), configName + ".yml");
        if (!file.exists()) {
            plugin.saveResource(configName + ".yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        cachedConfigs.put(configName, config); // Cache the configuration
        return config;
    }

    /**
     * Retrieves a cached configuration or loads it if not cached.
     */
    public FileConfiguration getConfig(String configName) {
        return cachedConfigs.computeIfAbsent(configName, name -> {
            File file = new File(plugin.getDataFolder(), name + ".yml");
            if (!file.exists()) {
                try {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return YamlConfiguration.loadConfiguration(file);
        });
    }

    /**
     * Saves a FileConfiguration to (configName.yml).
     */
    public void saveConfig(String configName) {
        FileConfiguration config = cachedConfigs.get(configName);
        if (config == null) return;

        File file = new File(plugin.getDataFolder(), configName + ".yml");
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Deletes a configuration file and removes it from the cache.
     */
    public boolean deleteConfig(String configName) {
        File file = new File(plugin.getDataFolder(), configName + ".yml");
        cachedConfigs.remove(configName);
        return file.exists() && file.delete();
    }

    /**
     * Reloads a configuration file and updates the cache.
     */
    public void reloadConfig(String configName) {
        File file = new File(plugin.getDataFolder(), configName + ".yml");
        if (file.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            cachedConfigs.put(configName, config);
        }
    }

    // ----------------------------------------------------------------
    // Generic Save/Load Methods
    // ----------------------------------------------------------------

    public <T> void saveObjects(String configName,
                                String path,
                                List<T> data,
                                Function<T, Map<String, Object>> serializer) {
        FileConfiguration config = getConfig(configName);
        List<Map<String, Object>> serialized = data.stream()
                .map(serializer)
                .collect(Collectors.toList());
        config.set(path, serialized);
        saveConfig(configName);
    }

    public <T> List<T> loadObjects(String configName,
                                   String path,
                                   Function<Map<String, Object>, T> deserializer) {
        FileConfiguration config = getConfig(configName);
        List<Map<?, ?>> rawList = config.getMapList(path);
        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptyList();
        }

        return rawList.stream()
                .map(raw -> {
                    Map<String, Object> casted = new HashMap<>();
                    raw.forEach((key, value) -> {
                        if (key instanceof String) {
                            casted.put((String) key, value);
                        }
                    });
                    return deserializer.apply(casted);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Checks if a configuration file exists.
     */
    public boolean configExists(String configName) {
        return new File(plugin.getDataFolder(), configName + ".yml").exists();
    }

    /**
     * Clears all cached configurations.
     */
    public void clearCache() {
        cachedConfigs.clear();
    }
}
