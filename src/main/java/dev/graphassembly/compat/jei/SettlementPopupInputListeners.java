package dev.graphassembly.compat.jei;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import java.util.function.Consumer;

/** Shared registration seam for modal screen input; tested against the actual NeoForge event bus. */
final class SettlementPopupInputListeners {
    static <T extends Event> void register(IEventBus bus, Class<T> eventType, Consumer<T> listener) {
        // Pinned JEI registers global input at NORMAL before the footer exists.
        bus.addListener(EventPriority.HIGHEST, false, eventType, listener);
    }
}
