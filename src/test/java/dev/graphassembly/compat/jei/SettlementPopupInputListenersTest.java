package dev.graphassembly.compat.jei;

import net.neoforged.bus.api.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SettlementPopupInputListenersTest {
    enum Action { OUTSIDE_DOWN, UP, ITEM_DOWN, SCROLL, ESCAPE, QUERY_KEY, PAGE_DOWN }
    public static final class Input extends Event implements ICancellableEvent {
        final Action action;
        Input(Action action) { this.action = action; }
    }
    @Test void pageDownPrecedesAnEarlierRegisteredFocusedJeiSearchConsumer() {
        var bus = BusBuilder.builder().build();
        var nativeSeen = new ArrayList<Action>();
        bus.addListener(EventPriority.NORMAL, false, Input.class, e -> {
            nativeSeen.add(e.action);
            if (e.action == Action.PAGE_DOWN) e.setCanceled(true);
        });
        var popup = new SettlementPopupState();
        popup.open(SettlementPopupState.Kind.CANDIDATES, 136, 77, 0);
        SettlementPopupInputListeners.register(bus, Input.class, e -> {
            if (popup.open() && e.action == Action.PAGE_DOWN) { popup.scroll(1); e.setCanceled(true); }
        });
        bus.post(new Input(Action.PAGE_DOWN));
        assertEquals(77, popup.offset, "Popup changes to page 2 even when JEI search would consume the key");
        assertTrue(nativeSeen.isEmpty(), "Focused JEI search must not see the handled popup navigation key");
    }
    @Test void outsideDownAndUpAreSwallowedBeforeEarlierRegisteredJeiHandlers() {
        var bus = BusBuilder.builder().build();
        var nativeSeen = new ArrayList<Action>();
        // Pinned JEI EventSubscription registers NORMAL with receiveCanceled=false before the footer exists.
        bus.addListener(EventPriority.NORMAL, false, Input.class, e -> nativeSeen.add(e.action));
        var popup = new SettlementPopupState();
        popup.open(SettlementPopupState.Kind.SELECTOR, 4, 4, 0);
        boolean[] pendingRelease = {false};
        SettlementPopupInputListeners.register(bus, Input.class, e -> {
            if (e.action == Action.OUTSIDE_DOWN && popup.open()) {
                popup.close(); pendingRelease[0] = true; e.setCanceled(true);
            } else if (e.action == Action.UP && pendingRelease[0]) {
                pendingRelease[0] = false; e.setCanceled(true);
            }
        });
        bus.post(new Input(Action.OUTSIDE_DOWN));
        bus.post(new Input(Action.UP));
        assertFalse(popup.open()); assertFalse(pendingRelease[0]);
        assertTrue(nativeSeen.isEmpty(), "JEI must neither arm a pending press nor navigate on its release");
        bus.post(new Input(Action.ITEM_DOWN));
        assertEquals(List.of(Action.ITEM_DOWN), nativeSeen, "Normal item input resumes after close");
    }
    @Test void modalScrollAndEscapeRunFirstButNativeIngredientAndQueryInputStillPassThroughOnce() {
        var bus = BusBuilder.builder().build();
        var nativeSeen = new ArrayList<Action>();
        bus.addListener(EventPriority.NORMAL, false, Input.class, e -> nativeSeen.add(e.action));
        var popup = new SettlementPopupState();
        popup.open(SettlementPopupState.Kind.SELECTOR, 9, 4, 0);
        SettlementPopupInputListeners.register(bus, Input.class, e -> {
            if (!popup.open()) return;
            if (e.action == Action.SCROLL) { popup.scroll(1); e.setCanceled(true); }
            else if (e.action == Action.ESCAPE) { popup.close(); e.setCanceled(true); }
        });
        bus.post(new Input(Action.SCROLL));
        assertEquals(1, popup.offset);
        assertTrue(nativeSeen.isEmpty(), "Scroll must not also reach JEI page/graph handlers");
        bus.post(new Input(Action.ITEM_DOWN)); bus.post(new Input(Action.QUERY_KEY));
        assertEquals(List.of(Action.ITEM_DOWN, Action.QUERY_KEY), nativeSeen, "Uncancelled native slot/query events pass once");
        bus.post(new Input(Action.ESCAPE));
        assertFalse(popup.open());
        assertEquals(List.of(Action.ITEM_DOWN, Action.QUERY_KEY), nativeSeen, "Escape closes popup before native handling");
        bus.post(new Input(Action.SCROLL));
        assertEquals(Action.SCROLL, nativeSeen.getLast(), "No popup means no modal interception");
    }
}
