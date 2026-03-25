package dev.isxander.xso;

import dev.isxander.yacl3.api.Binding;

import net.caffeinemc.mods.sodium.client.config.structure.StatefulOption;
import net.caffeinemc.mods.sodium.api.config.option.OptionBinding;

public class SodiumBinding<V> implements Binding<V> {
    private final StatefulOption<V> option;

    public SodiumBinding(StatefulOption<V> option) {
        this.option = option;
    }

    @Override
    public void setValue(V value) {
        option.modifyValue(value);
    }

    @Override
    public V getValue() {
        return option.getValidatedValue();
    }

    @Override
    public V defaultValue() {
        return option.getBinding().load();
    }
}
