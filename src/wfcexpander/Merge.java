package wfcexpander;

import display.GraphAugmentationVisualizer;
import patterns.OverlapManager;
import patterns.Pattern;
import wfc.Entropy;
import wfc.WaveCell;

import java.util.*;
import java.util.stream.Collectors;

import static wfc.wfc.RADIUS;

public class Merge {

    public static void merge(
            WaveCell referenceCell,
            List<WaveCell> waveCells,
            GraphAugmentationVisualizer graphViz,
            OverlapManager overlapManager,
            Entropy entropy     // <-- added
    ) {
        // 1) Must be uncollapsed & domain not empty
        if (!shouldMerge(referenceCell)) {
            return;
        }

        // 2) BFS up to RADIUS, skip distance < 3
        Set<WaveCell> localSet = bfsWaveCells(referenceCell, RADIUS);
        Map<WaveCell, Integer> distMap = buildDistanceMap(referenceCell, RADIUS);
        localSet.removeIf(c -> distMap.get(c) < 3);

        // 3) find merge candidates
        List<WaveCell> candidates = filterMergeCandidates(referenceCell, localSet);

        // 4) unify
        for (WaveCell candidate : candidates) {
            unifyCells(referenceCell, candidate, waveCells, graphViz, entropy);
        }
    }

    // -------------------------------------------------------------------
    //   Helpers
    // -------------------------------------------------------------------

    private static boolean shouldMerge(WaveCell refCell) {
        if (refCell == null) return false;
        if (refCell.isCollapsed()) return false;
        return !refCell.getDomain().isEmpty();
    }

    private static Set<WaveCell> bfsWaveCells(WaveCell start, int radius) {
        Set<WaveCell> visited = new HashSet<>();
        Queue<WaveCell> queue = new LinkedList<>();
        Map<WaveCell, Integer> dist = new HashMap<>();

        visited.add(start);
        queue.offer(start);
        dist.put(start, 0);

        while (!queue.isEmpty()) {
            WaveCell curr = queue.poll();
            int d = dist.get(curr);
            if (d >= radius) continue;
            for (WaveCell nbr : curr.getNeighbors()) {
                if (!visited.contains(nbr)) {
                    visited.add(nbr);
                    dist.put(nbr, d + 1);
                    queue.offer(nbr);
                }
            }
        }
        return visited;
    }

    private static Map<WaveCell, Integer> buildDistanceMap(WaveCell start, int radius) {
        Map<WaveCell, Integer> dist = new HashMap<>();
        Queue<WaveCell> queue = new LinkedList<>();
        dist.put(start, 0);
        queue.offer(start);

        while (!queue.isEmpty()) {
            WaveCell curr = queue.poll();
            int d = dist.get(curr);
            if (d >= radius) continue;
            for (WaveCell nbr : curr.getNeighbors()) {
                if (!dist.containsKey(nbr)) {
                    dist.put(nbr, d + 1);
                    queue.offer(nbr);
                }
            }
        }
        return dist;
    }

    private static List<WaveCell> filterMergeCandidates(WaveCell refCell, Set<WaveCell> localSet) {
        Set<Pattern> refDom = refCell.getDomain();
        return localSet.stream()
                .filter(c -> c != refCell)
                .filter(c -> !c.isCollapsed())
                .filter(c -> c.getDomain().equals(refDom))
                .filter(c -> haveSameNeighbors(refCell, c))
                .collect(Collectors.toList());
    }

    private static boolean haveSameNeighbors(WaveCell a, WaveCell b) {
        return a.getNeighbors().equals(b.getNeighbors());
    }

    private static void unifyCells(
            WaveCell referenceCell,
            WaveCell candidate,
            List<WaveCell> waveCells,
            GraphAugmentationVisualizer graphViz,
            Entropy entropy
    ) {
        // 1) remove candidate from neighbors
        for (WaveCell nbr : new ArrayList<>(candidate.getNeighbors())) {
            if (nbr != referenceCell) {
                nbr.removeNeighbor(candidate);
                nbr.addNeighbor(referenceCell);
                referenceCell.addNeighbor(nbr);
            }
        }

        // 2) Remove candidate from waveCells
        waveCells.remove(candidate);

        // 3) Also remove from Entropy queue if present
        entropy.removeFromQueue(candidate);

        // 4) Clear candidate
        candidate.clearDomain();
        candidate.getNeighbors().clear();

        System.out.println("Merged " + candidate.getNode().getId() + " into " + referenceCell.getNode().getId());

        // If referenceCell is uncollapsed, we can re-check its place in the queue
        if (!referenceCell.isCollapsed()) {
            // e.g. rebuild it
            if (!entropy.isInQueue(referenceCell)) {
                entropy.addToQueue(referenceCell);
            } else {
                entropy.rebuildQueue(Collections.singleton(referenceCell));
            }
        }
    }
}
