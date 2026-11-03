package dev.graphassembly.integration;

import java.util.ArrayDeque;
import java.util.Arrays;

/** Integral max-flow for overlapping fluid predicates; no per-millibucket expansion. */
public final class FluidAllocation {
    private FluidAllocation() {}
    public static int[] solve(boolean[][] matches, int[] required, int[] available) {
        if (matches.length != required.length) throw new IllegalArgumentException("Fluid dimensions differ");
        int roles=required.length, tanks=available.length, sink=1+roles+tanks, size=sink+1;
        long[][] remaining=new long[size][size]; long total=0;
        for (int i=0;i<roles;i++) {
            if(required[i]<0 || matches[i].length!=tanks) throw new IllegalArgumentException("Invalid fluid requirement");
            remaining[0][1+i]=required[i]; total+=required[i];
            for(int j=0;j<tanks;j++) if(matches[i][j]) remaining[1+i][1+roles+j]=required[i];
        }
        for(int j=0;j<tanks;j++) {
            if(available[j]<0) throw new IllegalArgumentException("Negative fluid amount");
            remaining[1+roles+j][sink]=available[j];
        }
        long flow=0;
        while(flow<total) {
            int[] previous=new int[size]; Arrays.fill(previous,-1); previous[0]=0;
            var queue=new ArrayDeque<Integer>(); queue.add(0);
            while(!queue.isEmpty() && previous[sink]<0) {
                int from=queue.remove();
                for(int to=1;to<size;to++) if(previous[to]<0 && remaining[from][to]>0) {
                    previous[to]=from; queue.add(to);
                }
            }
            if(previous[sink]<0) return null;
            long amount=Long.MAX_VALUE;
            for(int v=sink;v!=0;v=previous[v]) amount=Math.min(amount,remaining[previous[v]][v]);
            for(int v=sink;v!=0;v=previous[v]) {remaining[previous[v]][v]-=amount; remaining[v][previous[v]]+=amount;}
            flow+=amount;
        }
        int[] result=new int[tanks];
        for(int j=0;j<tanks;j++) result[j]=Math.toIntExact(available[j]-remaining[1+roles+j][sink]);
        return result;
    }
}
