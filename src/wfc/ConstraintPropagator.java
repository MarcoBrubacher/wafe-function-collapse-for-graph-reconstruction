package wfc;

import display.GraphAugmentationVisualizer;
import patterns.Pattern;
import patterns.PatternCompatibilityHelper;

import java.util.*;

/**
 * Multi-radius constraint propagation:
 * Each iteration:
 *  - partition waveCells into collapsed vs. uncollapsed
 *  - for each uncollapsed waveCell:
 *    BFS up to waveCell distance <= RADIUS
 *    keep only patterns that are compatible with all collapsed patterns found in that BFS region
 *  - repeat until stable or contradiction
 *
 * Now refactored to accept an Entropy object so we can re-queue waveCells whose
 * domain changes.
 */
public final class ConstraintPropagator {

    public static void enforceConsistency(
            Collection<WaveCell> waveCells,
            GraphAugmentationVisualizer graphViz,
            PatternCompatibilityHelper compHelper,
            Entropy entropy  // <-- added
    ) {
        boolean changed = true;
        while (changed) {
            changed = false;

            // gather uncollapsed
            List<WaveCell> uncollapsed = new ArrayList<>();
            for (WaveCell wc : waveCells) {
                if (!wc.isCollapsed()) {
                    uncollapsed.add(wc);
                }
            }
            if (uncollapsed.isEmpty()) {
                // everything collapsed => done
                break;
            }

            // prune each uncollapsed
            for (WaveCell u : uncollapsed) {
                boolean domainChanged = pruneDomainMultiRadius(u, waveCells, compHelper);
                if (domainChanged) {
                    changed = true;

                    // if domain empty => contradiction
                    if (u.getDomain().isEmpty()) {
                        throw new IllegalStateException(
                                "Contradiction: WaveCell " + u.getNode().getId()
                                        + " domain is empty after multi-radius pruning."
                        );
                    }

                    // re-queue or rebuild the queue entry for 'u' so that
                    // its newly changed entropy is reflected
                    if (!entropy.isInQueue(u)) {
                        entropy.addToQueue(u);
                    } else {
                        entropy.rebuildQueue(Collections.singleton(u));
                    }
                }
            }
        }
    }

    private static boolean pruneDomainMultiRadius(
            WaveCell u,
            Collection<WaveCell> waveCells,
            PatternCompatibilityHelper compHelper
    ) {
        if (u.isCollapsed() || u.getDomain().size() <= 1) {
            return false;
        }
        // BFS waveCells up to wfc.wfc.RADIUS
        Set<WaveCell> inRange = bfsWaveCells(u, wfc.RADIUS);

        // gather collapsed
        List<WaveCell> collapsedInRange = new ArrayList<>();
        for (WaveCell c : inRange) {
            if (c.isCollapsed()) {
                collapsedInRange.add(c);
            }
        }

        Set<Pattern> oldDomain = u.getDomain();
        Set<Pattern> newDomain = new HashSet<>();

        domainLoop:
        for (Pattern p : oldDomain) {
            // p must be compatible with all collapsed waveCells c in inRange
            for (WaveCell c : collapsedInRange) {
                Pattern cPat = c.getCollapsedPattern();
                if (cPat != null && !compHelper.areCompatible(cPat, p)) {
                    // skip p
                    continue domainLoop;
                }
            }
            newDomain.add(p);
        }

        if (newDomain.size() < oldDomain.size()) {
            oldDomain.retainAll(newDomain);
            return true;
        }
        return false;
    }

    private static Set<WaveCell> bfsWaveCells(WaveCell start, int radius) {
        Set<WaveCell> visited = new HashSet<>();
        Queue<WaveCell> queue = new LinkedList<>();
        Map<WaveCell,Integer> dist = new HashMap<>();

        visited.add(start);
        queue.offer(start);
        dist.put(start, 0);

        while (!queue.isEmpty()) {
            WaveCell current = queue.poll();
            int d = dist.get(current);
            if (d >= radius) {
                continue;
            }
            for (WaveCell nbr : current.getNeighbors()) {
                if (!visited.contains(nbr)) {
                    visited.add(nbr);
                    dist.put(nbr, d + 1);
                    queue.offer(nbr);
                }
            }
        }
        return visited;
    }
}
