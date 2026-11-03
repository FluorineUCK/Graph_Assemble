package dev.graphassembly.integration;

import java.util.Arrays;
import java.util.HashSet;

/** Quantity-aware role assignment; independent of Minecraft and inventory mutation. */
public final class InputAssignment {
    private InputAssignment() {}
    public enum Status { NONE, UNIQUE, AMBIGUOUS }
    public record Result(Status status, int[] slots) {
        public Result { slots = slots.clone(); }
        @Override public int[] slots() { return slots.clone(); }
    }
    public static Result solve(boolean[][] matches, int[] capacities, int[] identities, boolean[] significant) {
        if (capacities.length != identities.length || significant.length != matches.length)
            throw new IllegalArgumentException("Assignment dimensions differ");
        for (var row : matches) if (row.length != capacities.length)
            throw new IllegalArgumentException("Assignment row dimensions differ");
        for (int capacity : capacities) if (capacity < 0) throw new IllegalArgumentException("Negative capacity");
        int[] first = assign(matches, capacities, identities, -1, 0);
        if (first == null) return new Result(Status.NONE, new int[0]);
        // A distinct semantic solution must differ at some significant role. Checking each
        // alternative identity with augmenting paths avoids enumerating factorial slot permutations.
        for (int role = 0; role < matches.length; role++) {
            if (!significant[role]) continue;
            var checked = new HashSet<Integer>();
            checked.add(identities[first[role]]);
            for (int slot = 0; slot < capacities.length; slot++) {
                if (capacities[slot] == 0 || !matches[role][slot] || !checked.add(identities[slot])) continue;
                if (assign(matches, capacities, identities, role, identities[slot]) != null)
                    // A witness is useful for shared native-condition/capacity checks, never for commit.
                    return new Result(Status.AMBIGUOUS, first);
            }
        }
        return new Result(Status.UNIQUE, first);
    }

    private static int[] assign(boolean[][] matches, int[] capacities, int[] identities, int forcedRole, int identity) {
        int units = 0;
        for (int capacity : capacities) units = Math.addExact(units, Math.min(matches.length, capacity));
        int[] slotOf = new int[units], owners = new int[units];
        Arrays.fill(owners, -1);
        int unit = 0;
        for (int slot = 0; slot < capacities.length; slot++)
            for (int i = 0; i < Math.min(matches.length, capacities[slot]); i++) slotOf[unit++] = slot;
        for (int role = 0; role < matches.length; role++)
            if (!augment(role, matches, identities, forcedRole, identity, slotOf, owners, new boolean[units])) return null;
        int[] result = new int[matches.length];
        for (int i = 0; i < units; i++) if (owners[i] >= 0) result[owners[i]] = slotOf[i];
        return result;
    }

    private static boolean augment(int role, boolean[][] matches, int[] identities, int forcedRole, int identity,
                                   int[] slotOf, int[] owners, boolean[] visited) {
        for (int unit = 0; unit < slotOf.length; unit++) {
            int slot = slotOf[unit];
            if (visited[unit] || !matches[role][slot] || (role == forcedRole && identities[slot] != identity)) continue;
            visited[unit] = true;
            if (owners[unit] < 0 || augment(owners[unit], matches, identities, forcedRole, identity, slotOf, owners, visited)) {
                owners[unit] = role;
                return true;
            }
        }
        return false;
    }
}
