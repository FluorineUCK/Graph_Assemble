package dev.graphassembly.client.view;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.graphassembly.client.view.NodePresentation.Bounds;

class SlotFrameTest {
    @Test void onlyDrawnMemberIdentityCanBeHitInsideItsEffectiveClip() {
        var slot = new String("same name");
        var hidden = new String("hidden");
        var builder = new SlotFrame.Builder<String, String>(List.of(slot, hidden));
        builder.add(new String("same name"), "foreign", new Bounds(0, 0, 16, 16), null);
        builder.add(slot, "displayed", new Bounds(20, 30, 36, 46), new Bounds(24, 30, 36, 40));
        var frame = builder.build();
        assertEquals(1, frame.entries().size());
        assertNull(frame.hit(21, 31));
        assertNull(frame.hit(30, 42));
        assertNull(frame.hit(36, 35));
        assertEquals("displayed", frame.hit(25, 35).value());
        assertSame(slot, frame.hit(25, 35).key());
        assertNull(frame.hit(1, 1));
    }
}
