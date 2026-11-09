package dev.graphassembly.compat.kubejs;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

/** Loaded exclusively by KubeJS's plugin discovery, never from the common mod entrypoint. */
public final class GraphKubePlugin implements KubeJSPlugin {
    @Override public void registerBindings(BindingRegistry bindings) { bindings.add("GraphAssembly", new Bindings()); }
    public static final class Bindings {
        public KubeGraphBuilder create(String transitionalItem) { return new KubeGraphBuilder(transitionalItem); }
        public KubeSequenceBuilder sequence(dev.latvian.mods.rhino.Context cx, Object transitionalItem) { return new KubeSequenceBuilder(cx, transitionalItem); }
    }
}
