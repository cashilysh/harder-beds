package harderbeds.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class ModConfig {
    public static final Logger LOGGER = LoggerFactory.getLogger("harderbeds");

    private static final String SETTINGS_FILE = "harderbeds/settings.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static HarderBedsSettings settings;
    private static final List<String> configErrors = new ArrayList<>();
    public static boolean isInitialized = false;

    // Static initializer to load the config as soon as the class is accessed
    static {
        try {
            loadOrCreateSettingsConfig();
            isInitialized = true;
        } catch (Exception e) {
            LOGGER.error("Failed to initialize HarderBeds config", e);
            configErrors.add("Critical initialization error: " + e.getMessage());
            // Fallback to default settings if loading fails
            if (settings == null) {
                settings = new HarderBedsSettings();
            }
        }
    }

    /**
     * Holds all the configuration settings for the mod.
     */
    public static class HarderBedsSettings {
        private boolean simulateMobPathingOnSleep = true;
        private boolean visualizeMobPath = true;
        private boolean preventBedDropInVillages = true;
        private boolean enableVillageBedPenalty = true;
        private boolean disablePhantomSpawning = true;

        public boolean shouldSimulateMobPathingOnSleep() {
            return simulateMobPathingOnSleep;
        }

        public void setSimulateMobPathingOnSleep(boolean simulateMobPathingOnSleep) {
            this.simulateMobPathingOnSleep = simulateMobPathingOnSleep;
        }

        public boolean isMobPathVisualizationEnabled() {
            return visualizeMobPath;
        }

        public void setVisualizeMobPath(boolean visualizeMobPath) {
            this.visualizeMobPath = visualizeMobPath;
        }

        public boolean shouldPreventBedDropInVillages() {
            return preventBedDropInVillages;
        }

        public void setPreventBedDropInVillages(boolean preventBedDropInVillages) {
            this.preventBedDropInVillages = preventBedDropInVillages;
        }

        public boolean isVillageBedPenaltyEnabled() {
            return enableVillageBedPenalty;
        }

        public void setEnableVillageBedPenalty(boolean enableVillageBedPenalty) {
            this.enableVillageBedPenalty = enableVillageBedPenalty;
        }

        public boolean isPhantomSpawningDisabled() {
            return disablePhantomSpawning;
        }

        public void setPhantomSpawningDisabled(boolean disablePhantomSpawning) {
            this.disablePhantomSpawning = disablePhantomSpawning;
        }
    }

    /**
     * Gets the loaded settings object.
     * @return The current settings.
     */
    public static HarderBedsSettings getSettings() {
        if (settings == null) {
            LOGGER.error("CRITICAL: Settings accessed before initialization or after a critical failure. Using emergency defaults.");
            settings = new HarderBedsSettings(); // Emergency fallback
        }
        return settings;
    }

    /**
     * Public method to trigger a save of the current settings to the file.
     */
    public static void saveSettings() {
        if (!isInitialized) {
            LOGGER.error("Attempted to save settings before initialization.");
            return;
        }
        try {
            Path settingsPath = FabricLoader.getInstance().getConfigDir().resolve(SETTINGS_FILE);
            saveSettingsConfig(settingsPath);
        } catch (IOException e) {
            // Error is already logged by saveSettingsConfig
        }
    }

    private static void loadOrCreateSettingsConfig() {
        Path settingsPath = FabricLoader.getInstance().getConfigDir().resolve(SETTINGS_FILE);
        try {
            Path configDir = settingsPath.getParent();
            if (configDir != null && !Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }

            if (!Files.exists(settingsPath)) {
                LOGGER.info("Creating default settings file for HarderBeds.");
                createDefaultSettingsConfig(settingsPath);
            } else {
                LOGGER.info("Loading HarderBeds settings from file.");
                loadSettingsFromFile(settingsPath);
            }
        } catch (Exception e) {
            String error = "Failed to load or create settings config: " + e.getMessage();
            LOGGER.error(error, e);
            configErrors.add(error);
            settings = new HarderBedsSettings(); // Fallback to defaults
        }
    }

    private static void createDefaultSettingsConfig(Path settingsPath) throws IOException {
        // Create a new settings object with default values and save it
        settings = new HarderBedsSettings();
        saveSettingsConfig(settingsPath);
    }

    private static void loadSettingsFromFile(Path settingsPath) throws IOException {
        try {
            String content = Files.readString(settingsPath, StandardCharsets.UTF_8);
            settings = GSON.fromJson(content, HarderBedsSettings.class);

            if (settings == null) {
                LOGGER.warn("Settings file was empty or corrupted. Reverting to default settings.");
                configErrors.add("Settings file was empty or corrupted. Reverted to defaults.");
                createDefaultSettingsConfig(settingsPath);
            }
        } catch (JsonSyntaxException e) {
            LOGGER.error("Malformed JSON in settings file. Reverting to defaults.", e);
            configErrors.add("Malformed JSON in settings file: " + e.getMessage() + ". Reverted to defaults.");
            createDefaultSettingsConfig(settingsPath);
        }
    }

    private static void saveSettingsConfig(Path settingsPath) throws IOException {
        if (settings == null) {
            LOGGER.warn("Attempted to save null settings, initializing to defaults first.");
            settings = new HarderBedsSettings();
        }
        try {
            Path parentDir = settingsPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            // Create a backup before saving
            if (Files.exists(settingsPath)) {
                Path backupPath = settingsPath.resolveSibling(settingsPath.getFileName() + ".backup");
                Files.copy(settingsPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            }

            String jsonString = GSON.toJson(settings);
            // Use an atomic move for safe saving
            Path tempFile = settingsPath.resolveSibling(settingsPath.getFileName() + ".tmp");
            Files.writeString(tempFile, jsonString, StandardCharsets.UTF_8);
            Files.move(tempFile, settingsPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

        } catch (IOException e) {
            String error = "Failed to save settings config: " + e.getMessage();
            LOGGER.error(error, e);
            configErrors.add(error);
            throw e; // Re-throw to be handled by the public save method
        }
    }
}