package dev.isxander.xso;

import dev.isxander.xso.compat.*;
import dev.isxander.xso.config.XsoConfig;
import dev.isxander.xso.mixins.ConfigMixin;
import dev.isxander.xso.mixins.VideoSettingsScreenAccessor;
import dev.isxander.xso.utils.DonationPrompt;
import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.isxander.yacl3.impl.controller.EnumControllerBuilderImpl;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.NoticeScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import net.caffeinemc.mods.sodium.client.config.structure.BooleanOption;
import net.caffeinemc.mods.sodium.client.config.structure.Config;
import net.caffeinemc.mods.sodium.client.config.structure.EnumOption;
import net.caffeinemc.mods.sodium.client.config.structure.IntegerOption;
import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;
import net.caffeinemc.mods.sodium.client.config.structure.OptionGroup;
import net.caffeinemc.mods.sodium.client.config.structure.OptionPage;
import net.caffeinemc.mods.sodium.client.config.structure.Page;
import net.caffeinemc.mods.sodium.client.config.structure.StatefulOption;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptions;
import net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.data.fingerprint.HashedFingerprint;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class XandersSodiumOptions {
    private static boolean errorOccured = false;
    private static Config capturedConfig;

    public static void setCapturedConfig(Config config) {
        capturedConfig = config;
    }

    public static Screen wrapSodiumScreen(VideoSettingsScreen videoSettingsScreen, Screen prevScreen) {
        try {
            List<ModOptions> allModOptions = capturedConfig != null ? capturedConfig.getModOptions() : null;
            if (allModOptions == null) {
                return videoSettingsScreen;
            }

            YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder()
                    .title(Text.translatable("Sodium Options"));

            for (ModOptions modOptions : allModOptions) {
                for (Page page : modOptions.pages()) {
                    if (page instanceof OptionPage optionPage) {
                        var category = convertCategory(optionPage);
                        if (category != null) {
                            builder.category(category);
                        }
                    }
                }
            }

            builder.category(XsoConfig.getConfigCategory());

            Config finalConfig = capturedConfig;
            builder.save(() -> {
                if (finalConfig != null) {
                    finalConfig.applyAllOptions();
                }
                XsoConfig.INSTANCE.save();
            });

            var options = SodiumClientMod.options();
            if (!options.notifications.hasSeenDonationPrompt) {
                HashedFingerprint fingerprint = null;

                try {
                    fingerprint = HashedFingerprint.loadFromDisk();
                } catch (Throwable var5) {
                    Throwable t = var5;
                    SodiumClientMod.logger().error("Failed to read the fingerprint from disk", t);
                }

                if (fingerprint != null) {
                    Instant now = Instant.now();
                    Instant threshold = Instant.ofEpochSecond(fingerprint.timestamp()).plus(3L, ChronoUnit.DAYS);
                    if (now.isAfter(threshold)) {
                        options.notifications.hasSeenDonationPrompt = true;
                        try {
                            SodiumOptions.writeToDisk(options);
                        } catch (IOException var4) {
                            IOException e = var4;
                            SodiumClientMod.logger().error("Failed to update config file", e);
                        }
                        return new DonationPrompt(builder.build().generateScreen(prevScreen));
                    }
                }
            }

            return builder.build().generateScreen(prevScreen);
        } catch (Exception e) {
            var exception = new IllegalStateException("Failed to convert Sodium option screen to YACL with XSO!", e);

            if (XsoConfig.INSTANCE.getConfig().hardCrash) {
                throw exception;
            } else {
                exception.printStackTrace();

                return new NoticeScreen(() -> {
                    errorOccured = true;
                    MinecraftClient.getInstance().setScreen(videoSettingsScreen);
                    errorOccured = false;
                }, Text.literal("Xander's Sodium Options failed"), Text.literal("Whilst trying to convert Sodium's GUI to YACL with XSO mod, an error occured which prevented the conversion. This is most likely due to a third-party mod adding its own settings to Sodium's screen. XSO will now display the original GUI.\n\nThe error has been logged to latest.log file."), ScreenTexts.PROCEED, true);
            }
        }
    }

    @Nullable
    private static ConfigCategory convertCategory(OptionPage page) {
        try {
            Text pageName = page.name();

            if (Compat.IRIS) {
                Optional<ConfigCategory> shaderPackPage = IrisCompat.replaceShaderPackPage(pageName);
                if (shaderPackPage.isPresent()) {
                    return shaderPackPage.get();
                }
            }

            if (pageName.contains(Text.literal("LambDynamicLights"))) {
                return null;
            }

            ConfigCategory.Builder categoryBuilder = ConfigCategory.createBuilder()
                    .name(pageName);

            for (OptionGroup group : page.groups()) {
                categoryBuilder.option(LabelOption.create(Text.empty()));

                for (net.caffeinemc.mods.sodium.client.config.structure.Option option : group.options()) {
                    if (option instanceof StatefulOption<?> statefulOption) {
                        categoryBuilder.option(convertOption(statefulOption));
                    } else {
                        // Handle non-stateful options (e.g., ExternalButtonOption)
                        categoryBuilder.option(convertNonStatefulOption(option));
                    }
                }
            }

            return categoryBuilder.build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to convert Sodium option page named '" + page.name().getString() + "' to YACL config category.", e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Option<?> convertOption(StatefulOption<?> sodiumOption) {
        try {
            Text name = sodiumOption.getName();
            Text tooltip = sodiumOption.getTooltip();
            MutableText descText = tooltip.copy();

            Class<?> valueClass = getValueClass(sodiumOption);
            if (valueClass == null) {
                SodiumClientMod.logger().warn("[XSO] Unknown option type for '{}': {}", name.getString(), sodiumOption.getClass().getSimpleName());
                throw new IllegalStateException("Unknown option type for: " + name.getString() + " (" + sodiumOption.getClass().getSimpleName() + ")");
            }

            Option.Builder<?> builder = Option.createBuilder(valueClass)
                    .name(name)
                    .flags(convertFlags(sodiumOption))
                    .binding(new SodiumBinding(sodiumOption))
                    .available(sodiumOption.isEnabled());

            if (sodiumOption.getImpact() != null) {
                descText = descText.append("\n").append(Text.translatable("sodium.options.performance_impact_string", sodiumOption.getImpact().getName()).formatted(Formatting.GRAY));
            }

            builder.description(OptionDescription.of(descText));

            addController(builder, sodiumOption);

            return builder.build();
        } catch (Exception e) {
            SodiumClientMod.logger().warn("[XSO] Failed to convert option '{}': {}", sodiumOption.getName().getString(), e.getMessage());
            if (XsoConfig.INSTANCE.getConfig().lenientOptions) {
                return ButtonOption.createBuilder()
                        .name(sodiumOption.getName())
                        .description(OptionDescription.of(sodiumOption.getTooltip(), Text.translatable("xso.incompatible.tooltip").formatted(Formatting.RED)))
                        .available(false)
                        .text(Text.translatable("xso.incompatible.button").formatted(Formatting.RED))
                        .action((screen, opt) -> {})
                        .build();
            } else {
                throw new IllegalStateException("Failed to convert Sodium option named '" + sodiumOption.getName().getString() + "' to a YACL option!", e);
            }
        }
    }

    private static Option<?> convertNonStatefulOption(net.caffeinemc.mods.sodium.client.config.structure.Option sodiumOption) {
        try {
            Text name = sodiumOption.getName();
            Text tooltip = sodiumOption.getTooltip();

            return ButtonOption.createBuilder()
                    .name(name)
                    .description(OptionDescription.of(tooltip))
                    .available(sodiumOption.isEnabled())
                    .text(Text.literal("..."))
                    .action((screen, opt) -> {})
                    .build();
        } catch (Exception e) {
            SodiumClientMod.logger().warn("[XSO] Failed to convert non-stateful option: {}", e.getMessage());
            return ButtonOption.createBuilder()
                    .name(sodiumOption.getName())
                    .description(OptionDescription.of(Text.translatable("xso.incompatible.tooltip").formatted(Formatting.RED)))
                    .available(false)
                    .text(Text.translatable("xso.incompatible.button").formatted(Formatting.RED))
                    .action((screen, opt) -> {})
                    .build();
        }
    }

    @Nullable
    private static Class<?> getValueClass(StatefulOption<?> option) {
        if (option instanceof BooleanOption) {
            return boolean.class;
        } else if (option instanceof IntegerOption) {
            return int.class;
        } else if (option instanceof EnumOption<?> enumOption) {
            return enumOption.enumClass;
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addController(Option.Builder<?> yaclOption, StatefulOption<?> sodiumOption) {
        if (sodiumOption instanceof BooleanOption) {
            yaclOption.controller(opt -> (dev.isxander.yacl3.api.controller.ControllerBuilder) TickBoxControllerBuilder.create((Option<Boolean>) opt));
            return;
        }

        if (sodiumOption instanceof EnumOption<?> enumOption) {
            Class<?> enumClass = enumOption.enumClass;
            yaclOption.controller(opt -> new EnumControllerBuilderImpl<>((Option) opt).formatValue(value -> {
                if (value instanceof Enum<?> enumVal) {
                    return Text.of(enumVal.name());
                }
                return Text.of(String.valueOf(value));
            }).enumClass(enumClass));
            return;
        }

        if (sodiumOption instanceof IntegerOption integerOption) {
            var validatorProvider = integerOption.getValidatorProvider();
            int min = 0, max = 255, step = 1;
            if (validatorProvider != null && capturedConfig != null) {
                var v = validatorProvider.get(capturedConfig);
                if (v != null) {
                    min = v.min();
                    max = v.max();
                    step = v.step();
                }
            }
            var formatter = integerOption.getValueFormatter();
            int fMin = min, fMax = max, fStep = step;
            yaclOption.controller(opt -> (dev.isxander.yacl3.api.controller.ControllerBuilder) IntegerSliderControllerBuilder.create((Option<Integer>) opt).step(fStep).range(fMin, fMax).formatValue(value -> formatter.format(value)));
            return;
        }

        throw new IllegalStateException("Unsupported Sodium Option type: " + sodiumOption.getClass().getName());
    }

    private static List<OptionFlag> convertFlags(StatefulOption<?> sodiumOption) {
        List<OptionFlag> flags = new ArrayList<>();
        Set<net.minecraft.util.Identifier> sodiumFlags = sodiumOption.getFlags();

        if (sodiumFlags == null || sodiumFlags.isEmpty()) {
            return flags;
        }

        if (sodiumFlags.contains(net.caffeinemc.mods.sodium.api.config.option.OptionFlag.REQUIRES_RENDERER_RELOAD.getId())) {
            flags.add(OptionFlag.RELOAD_CHUNKS);
        } else if (sodiumFlags.contains(net.caffeinemc.mods.sodium.api.config.option.OptionFlag.REQUIRES_RENDERER_UPDATE.getId())) {
            flags.add(OptionFlag.WORLD_RENDER_UPDATE);
        }

        if (sodiumFlags.contains(net.caffeinemc.mods.sodium.api.config.option.OptionFlag.REQUIRES_ASSET_RELOAD.getId())) {
            flags.add(OptionFlag.ASSET_RELOAD);
        }

        if (sodiumFlags.contains(net.caffeinemc.mods.sodium.api.config.option.OptionFlag.REQUIRES_GAME_RESTART.getId())) {
            flags.add(OptionFlag.GAME_RESTART);
        }

        return flags;
    }

    public static boolean shouldConvertGui() {
        return XsoConfig.INSTANCE.getConfig().enabled && !errorOccured;
    }
}
