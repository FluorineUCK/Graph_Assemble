package dev.graphassembly.client.gui;

import org.junit.jupiter.api.Test;
import java.util.function.ToIntFunction;
import static org.junit.jupiter.api.Assertions.*;

class ButtonLabelTest {
    private static final ToIntFunction<String> FONT = text -> text.codePointCount(0, text.length()) * 6;

    @Test void overflowCountsAreCompleteOrAnUnambiguousEllipsisNeverAnotherNumber() {
        assertEquals("+2", ButtonLabel.fit("+2", 12, FONT));
        assertEquals("…", ButtonLabel.fit("+12", 12, FONT));
        assertEquals("…", ButtonLabel.fit("+135", 12, FONT));
        assertEquals("+135", ButtonLabel.fit("+135", 24, FONT));
    }
    @Test void policyUsesActualMeasuredWidthsAndDoesNotDrawOutsideATinyButton() {
        ToIntFunction<String> narrowDigits = text -> text.equals("+12") ? 11 : FONT.applyAsInt(text);
        assertEquals("+12", ButtonLabel.fit("+12", 12, narrowDigits));
        assertEquals("…", ButtonLabel.fit("+135", 6, FONT));
        assertEquals("", ButtonLabel.fit("+135", 5, FONT));
        assertEquals("", ButtonLabel.fit("+135", 0, FONT));
    }
}
