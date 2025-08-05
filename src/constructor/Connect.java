package constructor;

import wfc.Cell;

import java.util.*;

/**
 * Connects collapsed cells by adding edges in a way that exactly mirrors
 * the compatibility relationships observed during training, while ensuring
 * that no longer-distance compatibility constraints are violated.
 *
 * This class fills each cell’s remaining “stubs” (unconnected edge slots)
 * by pairing it with other cells whose assigned patterns were recorded as
 * valid neighbors in the training graph. In addition to direct (radius=1)
 * compatibility, it verifies that every implied multi-hop connection up to
 * the learned maxRadius is also supported by training data.
 *
 * In detail, the connection algorithm proceeds as follows:
 *
 * 1. Stub counting
 *    • For each collapsed cell, compute how many more edges it needs by
 *      subtracting its current adjacency size from its target degree.
 *
 * 2. Candidate generation
 *    • Examine each unordered pair of cells that both still have stubs.
 *    • First, check direct‐neighbor compatibility: look up the two patterns
 *      in the radius=1 compatibility table to confirm they were seen side by side.
 *
 * 3. Path validation
 *    • For each tentative pairing (u, v), perform a breadth‐first traversal
 *      starting from u and separately from v, up to a depth of maxRadius−1.
 *    • At BFS layer k (1 ≤ k < maxRadius), every newly discovered collapsed
 *      neighbor w at that distance must have appeared next to v’s pattern in
 *      the radius=(k+1) compatibility table. If any check fails, the pair is rejected.
 *    • By driving the BFS strictly outward—only enqueueing neighbors whose
 *      recorded depth increments by exactly one—we never revisit shallower layers
 *      or confuse adjacency order with layer membership.
 *
 * 4. Scoring with Resource‐Allocation (RA)
 *    • For each surviving candidate pair, compute an RA score:
 *        Σ_{m ∈ N₁(pA) ∩ N₁(pB)} 1 / |N₁(m)|
 *      where N₁(p) is the set of patterns adjacent to p at radius=1.
 *    • This metric favors pairs that share high‐centrality common neighbors.
 *
 * 5. Greedy edge addition
 *    • Sort all candidate pairs by descending RA score.
 *    • Iterate in that order, and for each pair with both endpoints still
 *      having available stubs, add the mutual adjacency and decrement their stubs.
 *    • Stop when no stubs remain or all candidates are exhausted.
 *
 */

public class Connect {
    private final Map<Cell, List<Cell>> adj;
    private final Map<Integer, Map<Integer, Set<Integer>>> compatByRadius;
    private final int maxRadius;

    /**
     * @param adjacency       current adjacency of collapsed cells (will be updated)
     * @param compatByRadius  compatibility data: radius → (patternID → compatible patternIDs)
     */
    public Connect(Map<Cell, List<Cell>> adjacency,
                   Map<Integer, Map<Integer, Set<Integer>>> compatByRadius) {
        this.adj = Objects.requireNonNull(adjacency, "adjacency must not be null");
        this.compatByRadius = Objects.requireNonNull(compatByRadius, "compatByRadius must not be null");
        this.maxRadius = compatByRadius.keySet().stream()
                .max(Integer::compareTo)
                .orElse(1);
    }

    /**
     * Fills each cell’s remaining edge slots by linking valid pairs.
     *
     * @param collapsed     list of collapsed cells whose patterns are fixed
     * @param targetDegree  desired degree (edge count) for each collapsed cell
     * @return              the total number of edges successfully added
     */
    public int connect(List<Cell> collapsed,
                       Map<Cell, Integer> targetDegree) {
        // 1) Determine how many stubs (remaining edges) each cell needs
        Map<Cell, Integer> stubs = countStubs(collapsed, targetDegree);

        // 2) Immediate compatibility map at radius=1
        Map<Integer, Set<Integer>> radiusOne = compatByRadius.getOrDefault(1, Collections.emptyMap());

        // 3) Score eligible pairs using Resource-Allocation
        Map<CellPair, Double> candidateScores = new HashMap<>();
        List<Cell> cells = new ArrayList<>(stubs.keySet());
        for (int i = 0; i < cells.size(); i++) {
            Cell u = cells.get(i);
            for (int j = i + 1; j < cells.size(); j++) {
                Cell v = cells.get(j);
                if (!canConsiderPair(u, v, stubs, radiusOne)) continue;
                int pA = u.getCollapsedPattern();
                int pB = v.getCollapsedPattern();
                if (!validateAllPaths(u, pB) || !validateAllPaths(v, pA)) continue;
                double score = computeRA(pA, pB);
                candidateScores.put(new CellPair(u, v), score);
            }
        }

        // 4) Sort candidate pairs by score descending
        List<Map.Entry<CellPair, Double>> entries = new ArrayList<>(candidateScores.entrySet());
        entries.sort((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()));

        // 5) Greedily add edges until stubs are exhausted
        int added = 0;
        for (var entry : entries) {
            CellPair pair = entry.getKey();
            Cell u = pair.u, v = pair.v;
            if (stubs.get(u) > 0 && stubs.get(v) > 0) {
                adj.computeIfAbsent(u, k -> new ArrayList<>()).add(v);
                adj.computeIfAbsent(v, k -> new ArrayList<>()).add(u);
                stubs.put(u, stubs.get(u) - 1);
                stubs.put(v, stubs.get(v) - 1);
                added++;
            }
        }
        return added;
    }

    /**
     * Checks if two cells are eligible for a new edge:
     *   Distinct cells with remaining stubs
     *   Not already adjacent
     *   Their patterns were direct neighbors in training (radius=1)
     *
     * @param u           first cell
     * @param v           second cell
     * @param stubs       map of cell → remaining stubs
     * @param radiusOne   radius=1 compatibility (patternID → compatible patternIDs)
     * @return            true if u and v can be considered for linking
     */
    private boolean canConsiderPair(Cell u,
                                    Cell v,
                                    Map<Cell, Integer> stubs,
                                    Map<Integer, Set<Integer>> radiusOne) {
        if (u == v) return false;
        if (stubs.getOrDefault(u, 0) <= 0 || stubs.getOrDefault(v, 0) <= 0) return false;
        if (adj.getOrDefault(u, Collections.emptyList()).contains(v)) return false;
        int pA = u.getCollapsedPattern();
        int pB = v.getCollapsedPattern();
        return radiusOne.getOrDefault(pA, Collections.emptySet()).contains(pB);
    }

    /**
     * Computes how many more edges (stubs) each cell still needs
     * to reach its target degree.
     *
     * @param collapsed     list of collapsed cells
     * @param targetDegree  desired degree map for each cell
     * @return              map of cell → number of remaining stubs
     */
    private Map<Cell, Integer> countStubs(List<Cell> collapsed,
                                          Map<Cell, Integer> targetDegree) {
        Map<Cell, Integer> stubs = new HashMap<>();
        for (Cell cell : collapsed) {
            int want = targetDegree.getOrDefault(cell, 0);
            int have = adj.getOrDefault(cell, Collections.emptyList()).size();
            int rem  = Math.max(0, want - have);
            if (rem > 0) {
                stubs.put(cell, rem);
            }
        }
        return stubs;
    }

    /**
     * Validates that linking 'start' to a cell with pattern pEnd
     * does not break compatibility at larger radii.
     *
     * Performs a BFS from 'start' up to depth maxRadius-1. At each layer k,
     * it checks that every encountered collapsed neighbor’s pattern is compatible
     * with pEnd at radius k+1. Returns false immediately on any violation.
     *
     * @param start   the starting collapsed cell
     * @param pEnd    the pattern ID of the prospective neighbor
     * @return        true if all implied paths up to maxRadius are valid
     */
    private boolean validateAllPaths(Cell start, int pEnd) {
        Deque<Cell> queue = new ArrayDeque<>();
        Set<Cell> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);
        int depth = 0;

        while (!queue.isEmpty() && depth < maxRadius - 1) {
            depth++;
            int levelSize = queue.size();
            Map<Integer, Set<Integer>> table = compatByRadius.getOrDefault(depth + 1, Collections.emptyMap());

            for (int i = 0; i < levelSize; i++) {
                Cell cur = queue.poll();
                for (Cell nbr : adj.getOrDefault(cur, Collections.emptyList())) {
                    if (!visited.add(nbr) || !nbr.isCollapsed()) continue;
                    int pX = nbr.getCollapsedPattern();
                    if (!table.getOrDefault(pX, Collections.emptySet()).contains(pEnd)) {
                        return false;
                    }
                    queue.add(nbr);
                }
            }
        }
        return true;
    }

    /**
     * Computes the Resource-Allocation (RA) score for two pattern IDs.
     *
     * RA = sum_{m in N1(pA) ∩ N1(pB)} 1 / |N1(m)|
     * where N1(p) is the set of patterns adjacent to p at radius=1.
     *
     * @param pA  first pattern ID
     * @param pB  second pattern ID
     * @return    RA score reflecting weighted shared 1-hop neighborhood
     */
    private double computeRA(int pA, int pB) {
        Map<Integer, Set<Integer>> r1 = compatByRadius.getOrDefault(1, Collections.emptyMap());
        Set<Integer> neighA = r1.getOrDefault(pA, Collections.emptySet());
        Set<Integer> neighB = r1.getOrDefault(pB, Collections.emptySet());

        double score = 0.0;
        for (int m : neighA) {
            if (neighB.contains(m)) {
                int deg = r1.getOrDefault(m, Collections.emptySet()).size();
                if (deg > 0) {
                    score += 1.0 / deg;
                }
            }
        }
        return score;
    }

    /**
     * Key for an unordered pair of cells, so (u,v) == (v,u).
     */
    private static class CellPair {
        final Cell u, v;

        CellPair(Cell a, Cell b) {
            if (System.identityHashCode(a) <= System.identityHashCode(b)) {
                u = a; v = b;
            } else {
                u = b; v = a;
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CellPair)) return false;
            CellPair p = (CellPair) o;
            return u == p.u && v == p.v;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(u) * 31 + System.identityHashCode(v);
        }
    }
}
