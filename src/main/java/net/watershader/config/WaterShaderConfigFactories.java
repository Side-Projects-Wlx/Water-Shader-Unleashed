package net.watershader.config;

import com.google.common.collect.ImmutableMap;
import net.glasslauncher.mods.gcapi3.api.ConfigEntry;
import net.glasslauncher.mods.gcapi3.api.ConfigFactoryProvider;
import net.glasslauncher.mods.gcapi3.impl.SeptFunction;
import net.glasslauncher.mods.gcapi3.impl.object.ConfigEntryHandler;
import net.glasslauncher.mods.gcapi3.impl.object.entry.EnumConfigEntryHandler;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.function.Function;

/**
 * Registers custom enum entry handlers ({@link WaterMode}, {@link ReflectionResolution})
 * with GlassConfigAPI 3 via the {@code gcapi3:factory_provider} entrypoint.
 */
public final class WaterShaderConfigFactories implements ConfigFactoryProvider {
    @Override
    public void provideLoadFactories(
            ImmutableMap.Builder<Type, SeptFunction<String, ConfigEntry, Field, Object, Boolean, Object, Object, ConfigEntryHandler<?>>> immutableBuilder) {
        immutableBuilder.put(
            WaterMode.class,
            (id, configEntry, parentField, parentObject, isMultiplayerSynced, enumOrOrdinal, defaultEnum) -> {
                WaterMode fallback = (defaultEnum instanceof WaterMode m) ? m : WaterMode.REFLECTION_AND_REFRACTION;
                int ordinal = WaterMode.ordinalFromYamlValue(enumOrOrdinal, fallback);
                return new EnumConfigEntryHandler<>(
                    id,
                    configEntry,
                    parentField,
                    parentObject,
                    isMultiplayerSynced,
                    ordinal,
                    fallback.ordinal(),
                    WaterMode.class
                );
            }
        );
        immutableBuilder.put(
            ReflectionResolution.class,
            (id, configEntry, parentField, parentObject, isMultiplayerSynced, enumOrOrdinal, defaultEnum) -> {
                ReflectionResolution fallback = (defaultEnum instanceof ReflectionResolution r) ? r : ReflectionResolution.FULL;
                int ordinal = ReflectionResolution.ordinalFromYamlValue(enumOrOrdinal, fallback);
                return new EnumConfigEntryHandler<>(
                    id,
                    configEntry,
                    parentField,
                    parentObject,
                    isMultiplayerSynced,
                    ordinal,
                    fallback.ordinal(),
                    ReflectionResolution.class
                );
            }
        );
    }

    @Override
    public void provideSaveFactories(ImmutableMap.Builder<Type, Function<Object, Object>> immutableBuilder) {
        immutableBuilder.put(WaterMode.class, o -> o);
        immutableBuilder.put(ReflectionResolution.class, o -> o);
    }
}
