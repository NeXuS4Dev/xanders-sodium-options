package dev.isxander.xso.mixins;

import dev.isxander.xso.XandersSodiumOptions;
import net.caffeinemc.mods.sodium.client.config.structure.Config;
import net.caffeinemc.mods.sodium.client.config.structure.ModOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = Config.class, remap = false)
public class ConfigMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void captureConfig(List<ModOptions> modOptions, CallbackInfo ci) {
        XandersSodiumOptions.setCapturedConfig((Config) (Object) this);
    }
}
