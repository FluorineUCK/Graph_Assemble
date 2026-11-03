package dev.graphassembly.integration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InputAssignmentTest {
    @Test void reallocatesOverlappingInputsWithoutReusingAUnit() {
        var result = InputAssignment.solve(new boolean[][] {{true, true}, {true, false}},
            new int[] {1, 1}, new int[] {0, 1}, new boolean[] {false, false});
        assertEquals(InputAssignment.Status.UNIQUE, result.status());
        assertArrayEquals(new int[] {1, 0}, result.slots());
        assertEquals(InputAssignment.Status.NONE, InputAssignment.solve(new boolean[][] {{true}, {true}},
            new int[] {1}, new int[] {0}, new boolean[] {true, true}).status());
    }
    @Test void differentProgressIsAmbiguousButEquivalentUnitsAreNot() {
        var matches = new boolean[][] {{true, true}, {true, true}};
        assertEquals(InputAssignment.Status.AMBIGUOUS, InputAssignment.solve(matches,
            new int[] {1, 1}, new int[] {0, 1}, new boolean[] {true, true}).status());
        assertEquals(InputAssignment.Status.UNIQUE, InputAssignment.solve(matches,
            new int[] {1, 1}, new int[] {0, 0}, new boolean[] {true, true}).status());
        assertEquals(InputAssignment.Status.UNIQUE, InputAssignment.solve(new boolean[][] {{true}, {true}},
            new int[] {2}, new int[] {0}, new boolean[] {true, true}).status());
    }
}
