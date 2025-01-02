package org.Nysxl.DynamicConfigManager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A manager for dynamically creating/loading/saving .yml configs.
 * It provides generic methods to save/load any List<T> where T
 * can be converted to/from a Map<String, Object>.
 */
public class DynamicConfigManager {

    private final JavaPlugin plugin;
    private final Map<String, FileConfiguration> cachedConfigs = new ConcurrentHashMap<>();

    public DynamicConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Load or create the default configuration file.
     * @param configName The name of the configuration file.
     * @return The loaded or created FileConfiguration.
     */
    public FileConfiguration loadOrCreateDefaultConfig(String configName) {
        return loadOrCreateConfig(configName, plugin.getDataFolder().getPath());
    }

    /**
     * Get the configuration file from cache or load it if not cached.
     * @param configName The name of the configuration file.
     * @return The FileConfiguration.
     */
    public FileConfiguration getConfig(String configName) {
        return getConfig(configName, plugin.getDataFolder().getPath());
    }

    /**
     * Get the configuration file from cache or load it if not cached, with custom file path.
     * @param configName The name of the configuration file.
     * @param customFilePath The custom file path.
     * @return The FileConfiguration.
     */
    public FileConfiguration getConfig(String configName, String customFilePath) {
        return cachedConfigs.computeIfAbsent(configName, name -> loadOrCreateConfig(name, customFilePath));
    }

    /**
     * Save the configuration file to disk.
     * @param configName The name of the configuration file.
     */
    public void saveConfig(String configName) {
        saveConfig(configName, plugin.getDataFolder().getPath());
    }

    /**
     * Save the configuration file to disk, with custom file path.
     * @param configName The name of the configuration file.
     * @param customFilePath The custom file path.
     */
    public void saveConfig(String configName, String customFilePath) {
        FileConfiguration config = cachedConfigs.get(configName);
        if (config == null) return;

        File file = new File(customFilePath, configName + ".yml");
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Load or create a configuration file.
     * @param configName The name of the configuration file.
     * @param filePath The file path.
     * @return The loaded or created FileConfiguration.
     */
    private FileConfiguration loadOrCreateConfig(String configName, String filePath) {
        File file = new File(filePath, configName + ".yml");
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    /**
     * Save objects to configuration.
     * @param configName The name of the configuration file.
     * @param path The path within the configuration file.
     * @param data The list of objects to save.
     * @param serializer The function to serialize the objects.
     * @param <T> The type of the objects.
     */
    public <T> void saveObjects(String configName, String path, List<T> data, Function<T, Map<String, Object>> serializer) {
        FileConfiguration config = getConfig(configName);
        List<Map<String, Object>> serialized = data.stream()
                .map(serializer)
                .collect(Collectors.toList());
        config.set(path, serialized);
        saveConfig(configName);
    }

    /**
     * Load objects from configuration.
     * @param configName The name of the configuration file.
     * @param path The path within the configuration file.
     * @param deserializer The function to deserialize the objects.
     * @param <T> The type of the objects.
     * @return The list of loaded objects.
     */
    public <T> List<T> loadObjects(String configName, String path, Function<Map<String, Object>, T> deserializer) {
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
     * Serialize an object to a Base64 string.
     * @param obj The object to serialize.
     * @return The Base64 string representation of the object.
     * @throws IOException If an I/O error occurs.
     */
    public static String serializeToBase64(Object obj) throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(obj);
            return Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray());
        }
    }

    /**
     * Deserialize an object from a Base64 string.
     * @param base64 The Base64 string representation of the object.
     * @return The deserialized object.
     * @throws IOException If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    public static Object deserializeFromBase64(String base64) throws IOException, ClassNotFoundException {
        byte[] data = Base64.getDecoder().decode(base64);
        try (ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data);
             ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream)) {
            return objectInputStream.readObject();
        }
    }

    /**
     * Save a serialized object to configuration.
     * @param configName The name of the configuration file.
     * @param path The path within the configuration file.
     * @param obj The object to save.
     * @throws IOException If an I/O error occurs.
     */
    public void saveSerializedObject(String configName, String path, Object obj) throws IOException {
        String serialized = serializeToBase64(obj);
        FileConfiguration config = getConfig(configName);
        config.set(path, serialized);
        saveConfig(configName);
    }

    /**
     * Load a serialized object from configuration.
     * @param configName The name of the configuration file.
     * @param path The path within the configuration file.
     * @return The deserialized object.
     * @throws IOException If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized object cannot be found.
     */
    public Object loadSerializedObject(String configName, String path) throws IOException, ClassNotFoundException {
        FileConfiguration config = getConfig(configName);
        String serialized = config.getString(path);
        if (serialized == null) {
            return null;
        }
        return deserializeFromBase64(serialized);
    }

    /**
     * Check if a configuration file exists.
     * @param configName The name of the configuration file.
     * @return True if the configuration file exists, false otherwise.
     */
    public boolean configExists(String configName) {
        return new File(plugin.getDataFolder(), configName + ".yml").exists();
    }

    /**
     * Clear all cached configurations.
     */
    public void clearCache() {
        cachedConfigs.clear();
    }

    /**
     * Delete a configuration file and remove it from cache.
     * @param configName The name of the configuration file.
     * @return True if the file was deleted, false otherwise.
     */
    public boolean deleteConfig(String configName) {
        File file = new File(plugin.getDataFolder(), configName + ".yml");
        cachedConfigs.remove(configName);
        return file.exists() && file.delete();
    }

    /**
     * Reload a configuration file and update the cache.
     * @param configName The name of the configuration file.
     */
    public void reloadConfig(String configName) {
        File file = new File(plugin.getDataFolder(), configName + ".yml");
        if (file.exists()) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            cachedConfigs.put(configName, config);
        }
    }

    /**
     * Save an array of data to configuration as a Base64-encoded string.
     * @param configName The name of the configuration file.
     * @param path The path within the configuration file.
     * @param data The array of objects to save.
     * @throws IOException If an I/O error occurs.
     */
    public void saveDataArray(String configName, String path, Object[] data) {
        // Serialize the array to a Base64 string
        String serialized = null;
        try {
            serialized = serializeToBase64(data);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Get the configuration file and set the serialized string at the path
        FileConfiguration config = getConfig(configName);
        config.set(path, serialized);

        // Save the configuration file
        saveConfig(configName);
    }

    /**
     * Load an array of data from configuration that was saved as a Base64-encoded string.
     * @param configName The name of the configuration file.
     * @param path The path within the configuration file.
     * @return The deserialized array of objects, or null if not found.
     * @throws IOException If an I/O error occurs.
     * @throws ClassNotFoundException If the class of the serialized objects cannot be found.
     */
    public Object[] loadDataArray(String configName, String path) {
        // Get the configuration file and retrieve the serialized string at the path
        FileConfiguration config = getConfig(configName);
        String serialized = config.getString(path);

        // Return null if no data was found
        if (serialized == null) {
            return null;
        }

        // Deserialize the Base64 string back into an array of objects
        try {
            return (Object[]) deserializeFromBase64(serialized);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}