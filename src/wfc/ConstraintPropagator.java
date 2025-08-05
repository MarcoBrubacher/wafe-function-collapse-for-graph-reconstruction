package wfc;

import patterns.PatternCompatibility;

import java.util.*;

/**
 * Propagates pattern compatibility constraints outward from collapsed cells.
 *
 * This class is a core component of the wave function collapse process, responsible
 * for maintaining consistency between cells as the system evolves. It enforces
 * the rule that once a cell is collapsed to a specific pattern, all other cells
 * at distances 1 through maxRadius must only allow patterns that were observed
 * to be compatible with it during training.
 *
 * To accomplish this, the class relies on a compatibility table:
 * a nested mapping from radius → (pattern ID → set of compatible pattern IDs).
 * For each radius r, and for every collapsed pattern, the table specifies which
 * other patterns were seen at that distance during pattern extraction.
 *
 * The propagation logic is implemented using a breadth-first traversal (BFS)
 * starting from one or more collapsed seed cells. At each step, the algorithm:
 *
 * 1. Traverses the cell graph outwards layer by layer using the adjacency structure.
 * 2. At each radius d (from 1 up to maxRadius), checks whether the current neighbor
 *    is uncollapsed and, if so, intersects its possible patterns with the set of
 *    patterns compatible with the collapsed seed's pattern at distance d.
 * 3. Ensures that only uncollapsed neighbors are pruned. Collapsed cells are ignored,
 *    and nodes already visited are not revisited.
 *
 * This process ensures directional and bounded propagation—each iteration pushes
 * the compatibility constraints outward in expanding layers without ever revisiting
 * shallower nodes. The use of explicit depth tracking during BFS (rather than relying
 * on layer metadata) guarantees that propagation respects spatial context correctly.
 *
 * After all propagation is complete, the class returns the list of uncollapsed cells
 * whose domains have now shrunk to exactly one pattern. These "forced" cells can be
 * safely collapsed in the next step of the algorithm.
 *
 * Note: This propagation does not collapse cells or change the collapsed state.
 * It only prunes domains based on constraints and identifies cells that are now
 * fully determined by their surroundings.
 */

public class ConstraintPropagator {

    /** Compatibility table: radius → (patternId → set of compatible patternIds) */
    private final Map<Integer, Map<Integer, Set<Integer>>> compat;

    /** Maximum radius of compatibility propagation (largest radius key in the compat map) */
    private final int maxRadius;

    /**
     * Constructs a new propagator with compatibility data grouped by radius.
     *
     * @param compatByRadius map of radius → (patternId → compatible IDs)
     */
    public ConstraintPropagator(Map<Integer, Map<Integer, Set<Integer>>> compatByRadius) {
        this.compat = Objects.requireNonNull(compatByRadius, "compat table must not be null");
        this.maxRadius = compatByRadius.keySet()
                .stream()
                .max(Integer::compareTo)
                .orElse(0);
    }

    /**
     * Prunes uncollapsed cells using all collapsed seeds.
     * Performs propagation from each collapsed cell outward,
     * and returns any uncollapsed cells that now have exactly one pattern remaining.
     *
     * @param collapsedCells   list of already collapsed cells (used as seeds)
     * @param uncollapsedCells list of cells still containing multiple options
     * @param adjacency        undirected adjacency map (Cell → neighbor Cells)
     * @return list of newly forced cells (now have only one possible pattern)
     */
    public List<Cell> propagate(List<Cell> collapsedCells,
                                List<Cell> uncollapsedCells,
                                Map<Cell, List<Cell>> adjacency) {
        for (Cell seed : collapsedCells) {
            propagateFrom(seed, adjacency);
        }

        // Collect uncollapsed cells that are now fully constrained
        List<Cell> forced = new ArrayList<>();
        for (Cell c : uncollapsedCells) {
            if (c.getPossiblePatterns().size() == 1) {
                forced.add(c);
            }
        }

        return forced;
    }

    /**
     * Performs BFS propagation from a single collapsed cell.
     * Uses the compatibility map to prune each neighbor's domain at radius d.
     *
     * @param seed      collapsed cell (must already be collapsed)
     * @param adjacency cell adjacency map
     * @throws IllegalArgumentException if the seed is not yet collapsed
     */
    private void propagateFrom(Cell seed,
                               Map<Cell, List<Cell>> adjacency) {
        if (!seed.isCollapsed()) {
            throw new IllegalArgumentException("Seed must be collapsed before propagation");
        }

        int seedPattern = seed.getCollapsedPattern();

        Queue<NodeDist> queue = new ArrayDeque<>();
        Set<Cell> visited = new HashSet<>();

        queue.add(new NodeDist(seed, 0));
        visited.add(seed);

        while (!queue.isEmpty()) {
            NodeDist current = queue.remove();
            int dist = current.dist;

            if (dist >= maxRadius) continue;

            for (Cell neighbor : adjacency.getOrDefault(current.cell, Collections.emptyList())) {
                if (!visited.add(neighbor)) continue;

                int nextDist = dist + 1;

                if (!neighbor.isCollapsed()) {
                    Map<Integer, Set<Integer>> table = compat.get(nextDist);
                    Set<Integer> allowed = (table != null) ? table.get(seedPattern) : null;

                    if (allowed != null) {
                        neighbor.prune(allowed);
                    }
                }

                queue.add(new NodeDist(neighbor, nextDist));
            }
        }
    }

    /**
     * Utility class used to store cell + distance during BFS traversal.
     */
    private static class NodeDist {
        final Cell cell;
        final int dist;

        NodeDist(Cell c, int d) {
            this.cell = c;
            this.dist = d;
        }
    }
}
