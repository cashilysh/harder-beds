package harderbeds.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import harderbeds.config.ModConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class ModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory getModConfigScreenFactory() {
        return parent -> {
            ConfigBuilder builder = ConfigBuilder.create()
                    .setParentScreen(parent)
                    .setTitle(Text.literal("Harder Beds Configuration"));

            // Set the action to perform when the user saves the configuration
            builder.setSavingRunnable(() -> {
                ModConfig.saveSettings();
            });

            // Get the Main category
            ConfigCategory mainCategory = builder.getOrCreateCategory(Text.literal("Main"));
            ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            // Get current and default settings for comparison
            ModConfig.HarderBedsSettings settings = ModConfig.getSettings();
            ModConfig.HarderBedsSettings defaults = new ModConfig.HarderBedsSettings();

            mainCategory.addEntry(
                    entryBuilder.startBooleanToggle(
                                    Text.literal("Simulate Mob Pathing Before Sleeping"),
                                    settings.shouldSimulateMobPathingOnSleep()
                            )
                            .setDefaultValue(defaults.shouldSimulateMobPathingOnSleep())
                            .setTooltip(Text.literal("If enabled, the game will check if hostile mobs can reach the bed before allowing you to sleep."))
                            .setSaveConsumer(settings::setSimulateMobPathingOnSleep)
                            .build()
            );
           
            mainCategory.addEntry(
                    entryBuilder.startBooleanToggle(
                                    Text.literal("Visualize Mob Path"),
                                    settings.isMobPathVisualizationEnabled()
                            )
                            .setDefaultValue(defaults.isMobPathVisualizationEnabled())
                            .setTooltip(Text.literal("Shows the simulated mob path to the bed (if a path is found) using particle effects. Requires path simulation to be enabled."))
                            .setSaveConsumer(settings::setVisualizeMobPath)
                            .build()
            );

            mainCategory.addEntry(
                    entryBuilder.startBooleanToggle(
                                    Text.literal("Prevent Bed Drop in Villages"),
                                    settings.shouldPreventBedDropInVillages()
                            )
                            .setDefaultValue(defaults.shouldPreventBedDropInVillages())
                            .setTooltip(Text.literal("If enabled, beds located within a village structure will not drop as an item when mined."))
                            .setSaveConsumer(settings::setPreventBedDropInVillages)
                            .build()
            );

            mainCategory.addEntry(
                    entryBuilder.startBooleanToggle(
                                    Text.literal("Apply Penalty for Using Village Beds"),
                                    settings.isVillageBedPenaltyEnabled()
                            )
                            .setDefaultValue(defaults.isVillageBedPenaltyEnabled())
                            .setTooltip(Text.literal("If enabled, sleeping in a bed located within a village will apply negative potion effects to the player."))
                            .setSaveConsumer(settings::setEnableVillageBedPenalty)
                            .build()
            );

            mainCategory.addEntry(
                    entryBuilder.startBooleanToggle(
                                    Text.literal("Disable Phantom Spawning"),
                                    settings.isPhantomSpawningDisabled()
                            )
                            .setDefaultValue(defaults.isPhantomSpawningDisabled())
                            .setTooltip(Text.literal("If enabled, no phantoms will spawn even if the player doesnt sleep."))
                            .setSaveConsumer(settings::setPhantomSpawningDisabled)
                            .build()
            );

            return builder.build();
        };
    }
}