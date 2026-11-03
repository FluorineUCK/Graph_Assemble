package dev.graphassembly.integration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FluidAllocationTest {
    @Test void reallocatesOverlappingDemandsAndCountsEveryTankOnce() {
        assertArrayEquals(new int[]{250,250},FluidAllocation.solve(new boolean[][]{{true,true},{true,false}},new int[]{250,250},new int[]{250,250}));
        assertNull(FluidAllocation.solve(new boolean[][]{{true},{true}},new int[]{250,250},new int[]{400}));
        assertArrayEquals(new int[]{200,300},FluidAllocation.solve(new boolean[][]{{true,true}},new int[]{500},new int[]{200,300}));
    }
    @Test void hugeQuantitiesDoNotExpandIntoPerUnitObjectsOrOverflow() {
        assertArrayEquals(new int[]{Integer.MAX_VALUE,Integer.MAX_VALUE},FluidAllocation.solve(
            new boolean[][]{{true,false},{false,true}},new int[]{Integer.MAX_VALUE,Integer.MAX_VALUE},new int[]{Integer.MAX_VALUE,Integer.MAX_VALUE}));
    }
}
