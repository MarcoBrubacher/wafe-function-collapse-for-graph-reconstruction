package wfc;

import patterns.Pattern;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Computes entropy-based decisions for collapsing WFC cells.
 *
 * This class tracks a list of uncollapsed {@link Cell} objects, along with global
 * frequency statistics derived from training patterns. It uses Shannon entropy
 * to quantify uncertainty in each cell and selects the one with the lowest entropy
 * to collapse next.
 *
 * Entropy is calculated as a weighted sum over possible pattern IDs,
 * where each weight is proportional to that pattern's observed frequency.
 *
 * The class supports:
 * - Computing entropy for all uncollapsed cells
 * - Selecting the most "decided" (least uncertain) cell
 * - Collapsing that cell randomly, weighted by pattern frequency
 *
 * Note: This class does not mutate the list of uncollapsed cells—it assumes
 * external control of removal, addition, and adjacency propagation.
 */
public class Entropy {

    /** List of uncollapsed cells (not yet fixed to one pattern). */
    private final List<Cell> uncollapsedCells;

    /** Frequency map: pattern ID → frequency count (used to weight entropy and sampling). */
    private final Map<Integer, Integer> freq;

    /** Center label map: pattern ID → label (used when collapsing a cell). */
    private final Map<Integer, Integer> centerLabel;

    /** Random generator used to sample patterns during collapse (seeded for reproducibility). */
    private final Random rand = new Random(42);

    /**
     * Initializes the entropy controller using the current cell list and all extracted patterns.
     *
     * @param uncollapsedCells the current list of uncollapsed cells
     * @param patterns         the list of all available patterns (with frequency and label)
     */
    public Entropy(List<Cell> uncollapsedCells, List<Pattern> patterns) {
        this.uncollapsedCells = Objects.requireNonNull(uncollapsedCells, "uncollapsedCells cannot be null");
        Objects.requireNonNull(patterns, "patterns cannot be null");

        // Build frequency and label lookup maps
        this.freq = patterns.stream().collect(Collectors.toMap(
                Pattern::getId, Pattern::getFrequency));
        this.centerLabel = patterns.stream().collect(Collectors.toMap(
                Pattern::getId, Pattern::getCenterLabel));
    }

    /**
     * Computes the Shannon entropy H = –∑ pᵢ log(pᵢ) for the given cell,
     * where each pᵢ is proportional to the pattern's frequency.
     * Returns 0.0 if the cell is already forced or contradictory.
     *
     * @param cell the cell to analyze
     * @return entropy value (0.0 if size ≤ 1 or all frequencies are 0)
     */
    private double computeEntropy(Cell cell) {
        Set<Integer> domain = cell.getPossiblePatterns();

        double total = domain.stream()
                .mapToDouble(pid -> freq.getOrDefault(pid, 0))
                .sum();

        if (total <= 0 || domain.size() <= 1) {
            return 0.0;
        }

        double H = 0.0;
        for (int pid : domain) {
            double f = freq.getOrDefault(pid, 0);
            if (f <= 0) continue;
            double p = f / total;
            H -= p * Math.log(p); // natural log
        }

        return H;
    }

    /**
     * Computes the current entropy of all uncollapsed cells.
     *
     * @return a map from each cell to its computed entropy
     */
    public Map<Cell, Double> computeEntropies() {
        return uncollapsedCells.stream()
                .collect(Collectors.toMap(Function.identity(), this::computeEntropy));
    }

    /**
     * Finds the index of the uncollapsed cell with the lowest entropy > 0.
     * Returns -1 if no such cell exists (i.e., all are forced or blocked).
     *
     * @return index in the uncollapsed list, or -1 if none remain
     */
    public int selectCellIndex() {
        double bestH = Double.POSITIVE_INFINITY;
        int bestIdx = -1;

        for (int i = 0; i < uncollapsedCells.size(); i++) {
            Cell c = uncollapsedCells.get(i);
            double H = computeEntropy(c);
            if (H > 0 && H < bestH) {
                bestH = H;
                bestIdx = i;
            }
        }

        return bestIdx;
    }

    /**
     * Collapses the cell with the lowest entropy by randomly choosing one of
     * its possible patterns, weighted by global pattern frequency.
     *
     * After collapsing, the cell is still present in the list, but is now fixed
     * to a single pattern and cannot be modified.
     *
     * @return index of the collapsed cell in the uncollapsed list, or -1 if none could be collapsed
     */
    public int collapseNextCell() {
        int idx = selectCellIndex();
        if (idx < 0) return -1;

        Cell cell = uncollapsedCells.get(idx);
        List<Integer> domain = new ArrayList<>(cell.getPossiblePatterns());

        // Build cumulative frequency array for weighted sampling
        double total = 0;
        List<Double> cumulative = new ArrayList<>(domain.size());
        for (int pid : domain) {
            total += freq.getOrDefault(pid, 0);
            cumulative.add(total);
        }

        // Randomly select a pattern based on cumulative weights
        double r = rand.nextDouble() * total;
        int chosen = domain.get(0); // fallback
        for (int i = 0; i < cumulative.size(); i++) {
            if (r <= cumulative.get(i)) {
                chosen = domain.get(i);
                break;
            }
        }

        // Collapse the cell to that pattern and assign its center label
        cell.collapseTo(chosen, centerLabel.get(chosen));
        return idx;
    }
}
