package constructor;

import wfc.Cell;

import java.util.*;

/**
 * Expands the current graph by allocating and attaching new uncollapsed cells
 * to already-collapsed cells, based on each center node’s degree.
 *
 * This expansion module determines how many new neighbor cells to create around
 * each collapsed cell, proportional to its original degree in the training graph.
 * Expansion is capped per cell to at most ⌈degree / 2⌉ to prevent over-attachment.
 *
 * Expansion proceeds in four steps:
 * 1. Computes total demand (sum of degrees) to derive relative share.
 * 2. Assigns each cell a base number of expansion slots using proportional allocation,
 *    with per-cell caps and a minimum of 1.
 * 3. Distributes any leftover budget using the largest remainder method.
 * 4. Creates the new uncollapsed child cells and updates the adjacency map.
 *
 * The resulting uncollapsed cells are appended to the active WFC frontier.
 */
public class Expand {

    /**
     * Allocates expansion slots for collapsed cells and creates new neighbors accordingly.
     *
     * @param collapsedCells         list of collapsed cells that will receive new neighbors
     * @param allowedExpansionCount  total number of new uncollapsed cells allowed
     * @param centerDegrees          map from each collapsed cell → its center node degree
     * @param allPatternIds          set of all pattern IDs (used to initialize new cells)
     * @param uncollapsedCells       output list where created cells will be added
     * @param cellAdjacency          bidirectional adjacency map (updated in-place)
     */
    public static void expand(List<Cell> collapsedCells,
                              int allowedExpansionCount,
                              Map<Cell, Integer> centerDegrees,
                              Set<Integer> allPatternIds,
                              List<Cell> uncollapsedCells,
                              Map<Cell, List<Cell>> cellAdjacency) {

        // 1) Compute total expansion demand: sum of degrees of all collapsed cells
        int totalDemand = collapsedCells.stream()
                .mapToInt(c -> centerDegrees.getOrDefault(c, 0))
                .sum();
        // If there's no demand or no budget, nothing to do
        if (totalDemand == 0 || allowedExpansionCount <= 0) {
            return;
        }

        // 2) Proportional base allocation and cap enforcement
        //    For each cell, compute its share of the budget,
        //    floor to an integer (min 1), then cap to ⌈degree/2⌉
        Map<Cell, Integer> baseAlloc = new HashMap<>();
        Map<Cell, Double> remainders = new HashMap<>();
        for (Cell cell : collapsedCells) {
            int degree = centerDegrees.getOrDefault(cell, 0);
            // Floating-point share relative to total demand
            double share = allowedExpansionCount * (degree / (double) totalDemand);
            // Base allocation: at least 1, at most floor(share)
            int base = Math.max(1, (int) Math.floor(share));
            // Cap per cell to ⌈degree/2⌉
            int cap = (degree + 1) / 2;
            base = Math.min(base, cap);
            baseAlloc.put(cell, base);
            // Store fractional part for largest-remainder step
            remainders.put(cell, share - base);
        }

        // 3) Largest-remainder distribution of any leftover slots
        //    Calculate how many slots remain after base allocation
        int usedSlots = baseAlloc.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        int surplus = Math.max(0, allowedExpansionCount - usedSlots);

        if (surplus > 0) {
            // Sort cells by descending fractional remainder
            List<Cell> sortedByRemainder = new ArrayList<>(collapsedCells);
            sortedByRemainder.sort(
                    Comparator.comparingDouble(remainders::get).reversed()
            );

            // Distribute one extra slot to each, up to the cap, until surplus is exhausted
            for (Cell cell : sortedByRemainder) {
                if (surplus == 0) break;
                int current = baseAlloc.get(cell);
                int cap = (centerDegrees.getOrDefault(cell, 0) + 1) / 2;
                if (current < cap) {
                    baseAlloc.put(cell, current + 1);
                    surplus--;
                }
            }
        }

        // 4) Expansion: create uncollapsed child cells and wire them to parents
        for (Cell parent : collapsedCells) {
            int slots = baseAlloc.getOrDefault(parent, 0);
            // Ensure parent has an adjacency list
            List<Cell> parentNeighbors =
                    cellAdjacency.computeIfAbsent(parent, k -> new ArrayList<>());

            // For each allocated slot, create a new cell and connect it bidirectionally
            for (int i = 0; i < slots; i++) {
                // Initialize child with all possible patterns
                Cell child = new Cell(allPatternIds);
                // Add to uncollapsed frontier
                uncollapsedCells.add(child);
                // Connect parent -> child
                parentNeighbors.add(child);
                // Connect child -> parent
                cellAdjacency.computeIfAbsent(child, k -> new ArrayList<>())
                        .add(parent);
            }
        }
    }
}
