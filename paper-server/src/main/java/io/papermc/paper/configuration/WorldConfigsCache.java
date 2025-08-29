// Paper(tmpod) - add world config cache
package io.papermc.paper.configuration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import org.spigotmc.SpigotWorldConfig;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.util.CheckedFunction;

public final class WorldConfigsCache {
    public record Entry(io.papermc.paper.configuration.WorldConfiguration paper, SpigotWorldConfig spigot) {
    }

    private final Map<String, Entry> map = new HashMap<>();

    private Entry getOrSupply(String levelName, Supplier<Entry> supplier) {
        var entry = map.get(levelName);
        if (entry == null) entry = supplier.get();
        return entry;
    }

    public void clear() {
        map.clear();
    }

    public Entry load(
        final String levelName,
        final PaperConfigurations paperConfigs,
        final Function<SpigotWorldConfig, Configurations.ContextMap> paperContextMapProducer,
        final CheckedFunction<ConfigurationNode, WorldConfiguration, SerializationException> creator
    ) {
        var spigotConfig = SpigotWorldConfig.getOrDefault(levelName);
        try {
            var paperConfig = paperConfigs.createWorldConfig(paperContextMapProducer.apply(spigotConfig), creator, false);
            var entry = new Entry(
                paperConfig.config(),
                spigotConfig
            );

            // if there's actual configurations either in spigot or in paper, cache, otherwise just return (defaults)
            if (SpigotWorldConfig.isLevelConfigured(levelName) || !paperConfig.newFile()) {
                map.put(levelName, entry);
            }

            return entry;
        } catch (IOException exception) {
            throw new RuntimeException("Could not create world config for " + levelName, exception);
        }
    }

    public Entry load(
        final ServerLevel level,
        final PaperConfigurations paperConfigs,
        final CheckedFunction<ConfigurationNode, WorldConfiguration, SerializationException> creator
    ) {
        return load(level.serverLevelData.getLevelName(), paperConfigs, c -> PaperConfigurations.createWorldContextMap(level), creator);
    }

    public Entry reload(final ServerLevel level, final PaperConfigurations paperConfigs) {
        return get(level, paperConfigs, PaperConfigurations.reloader(paperConfigs.worldConfigClass, level.paperConfig()));
    }

    public Entry get(
        final ServerLevel level,
        final PaperConfigurations paperConfigs,
        final CheckedFunction<ConfigurationNode, WorldConfiguration, SerializationException> creator
    ) {
        return getOrSupply(level.serverLevelData.getLevelName(), () -> load(level, paperConfigs, creator));
    }

    public Entry get(
        final ServerLevel level,
        final PaperConfigurations paperConfigs
    ) {
        return get(
            level,
            paperConfigs,
            PaperConfigurations.creator(paperConfigs.worldConfigClass, false)
        );
    }

    public Entry get(
        final Path levelDir,
        final String levelName,
        final ResourceLocation worldKey,
        final RegistryAccess regAccess,
        final GameRules gameRules,
        final PaperConfigurations paperConfigs,
        final CheckedFunction<ConfigurationNode, WorldConfiguration, SerializationException> creator
    ) {
        return getOrSupply(
            levelName,
            () -> load(
                levelName,
                paperConfigs,
                spigotConfig -> PaperConfigurations.createWorldContextMap(levelDir, levelName, worldKey, spigotConfig, regAccess, gameRules),
                creator
            )
        );
    }

    public Entry get(
        final Path levelDir,
        final String levelName,
        final ResourceLocation worldKey,
        final RegistryAccess regAccess,
        final GameRules gameRules,
        final PaperConfigurations paperConfigs
    ) {
        return get(levelDir, levelName, worldKey, regAccess, gameRules, paperConfigs, PaperConfigurations.creator(paperConfigs.worldConfigClass, false));
    }
}
