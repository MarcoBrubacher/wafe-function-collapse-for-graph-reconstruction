package wfc;

import patterns.OverlapManager;
import patterns.Pattern;

import java.util.*;

/**
 * Updated Entropy class:
 *  - "calculate(...)" uses pure pattern-based Shannon entropy (in base-2).
 *  - "updateWaveFunction(...)" still applies synergy from collapsed neighbors.
 */
public class Entropy {

    private final Map<Pattern, Integer> frequencies;
    private final PriorityQueue<WaveCell> entropyQueue;
    private final OverlapManager overlapManager;

    public Entropy(Map<Pattern, Integer> frequencies, OverlapManager overlapManager) {
        this.frequencies = frequencies;
        this.overlapManager = overlapManager;

        // The priority queue sorts waveCells by ascending entropy:
        // (lowest-entropy waveCell is polled first).
        this.entropyQueue = new PriorityQueue<>(
                Comparator.comparingDouble(wc -> calculate(wc.getDomain(), frequencies))
        );
    }

    /**
     * Calculates the Shannon entropy (base-2) over the set of patterns in "domain."
     * Each pattern's weight = frequency[pattern], defaulting to 1 if not found.
     *
     * If domain is empty, returns 0.0.
     */
    public static double calculate(Set<Pattern> domain, Map<Pattern, Integer> freqMap) {
        if (domain == null || domain.isEmpty()) {
            return 0.0;
        }

        // 1) Compute total weight
        double totalWeight = 0.0;
        for (Pattern pattern : domain) {
            double w = (freqMap != null)
                    ? freqMap.getOrDefault(pattern, 1)
                    : 1;
            totalWeight += w;
        }

        // 2) Compute the Shannon entropy in base-2:
        // H = - sum( p_i * log2(p_i) ), where p_i = w_i / totalWeight
        double entropy = 0.0;
        for (Pattern pattern : domain) {
            double w = (freqMap != null)
                    ? freqMap.getOrDefault(pattern, 1)
                    : 1;
            double p = w / totalWeight;
            if (p > 0.0) {
                // log base-2 => log(p)/log(2)
                entropy -= p * (Math.log(p) / Math.log(2));
            }
        }

        return entropy;
    }

    /**
     * "updateWaveFunction" computes a probability-like distribution over the domain
     * by combining:
     *   - pattern frequency
     *   - synergy from collapsed neighbors (pairwiseScore)
     * Then normalizes for a final sum=1 distribution.
     */
    public Map<Pattern, Double> updateWaveFunction(Set<Pattern> domain, List<WaveCell> neighbors) {
        Map<Pattern, Double> waveFunction = new HashMap<>();
        double totalWeight = 0.0;

        for (Pattern pattern : domain) {
            // 1) Base weight from training frequency
            double freqWeight = frequencies.getOrDefault(pattern, 1);

            // 2) Add synergy from each collapsed neighbor
            for (WaveCell neighbor : neighbors) {
                if (neighbor.isCollapsed()) {
                    Pattern neighborPattern = neighbor.getCollapsedPattern();
                    double pairScore = overlapManager.getPairwiseScore(pattern, neighborPattern);

                    // Additive synergy
                    freqWeight += pairScore;
                }
            }
            waveFunction.put(pattern, freqWeight);
            totalWeight += freqWeight;
        }

        // 3) Normalize to probability distribution
        if (totalWeight > 0.0) {
            for (Map.Entry<Pattern, Double> e : waveFunction.entrySet()) {
                waveFunction.put(e.getKey(), e.getValue() / totalWeight);
            }
        }

        return waveFunction;
    }

    // ------------------------------
    //   Priority Queue Management
    // ------------------------------

    /** Enqueues a waveCell for consideration (sorting by ascending entropy). */
    public void addToQueue(WaveCell cell) {
        entropyQueue.add(cell);
    }

    /** Removes a waveCell from the entropy queue (e.g., if collapsed). */
    public void removeFromQueue(WaveCell cell) {
        entropyQueue.remove(cell);
    }

    /**
     * Rebuilds the queue by removing and re-adding neighbors
     * of a changed waveCell.
     */
    public void updateNeighborEntropies(WaveCell cell) {
        Set<WaveCell> updated = new HashSet<>();
        for (WaveCell neighbor : cell.getNeighbors()) {
            if (!neighbor.isCollapsed()) {
                updated.add(neighbor);
            }
        }
        rebuildQueue(updated);
    }

    /**
     * Removes a collection of waveCells from the queue,
     * then re-adds them so that their new entropy ordering is correct.
     */
    public void rebuildQueue(Collection<WaveCell> cells) {
        entropyQueue.removeAll(cells);
        entropyQueue.addAll(cells);
    }

    /**
     * Polls the waveCell of lowest entropy from the queue (if any remain),
     * ensuring it's uncollapsed and has a non-empty domain.
     */
    public WaveCell pollLowestEntropyCell() {
        while (!entropyQueue.isEmpty()) {
            WaveCell cell = entropyQueue.poll();
            if (!cell.isCollapsed() && cell.getDomainSize() > 0) {
                return cell;
            }
        }
        return null;
    }

    /** Checks if a waveCell is currently in the queue. */
    public boolean isInQueue(WaveCell cell) {
        return entropyQueue.contains(cell);
    }

    // ------------------------------
    //   (Optional) Additional Util
    // ------------------------------

    /**
     * Example: compute "compatibility" scores for each pattern across all waveCells,
     * if needed for advanced logic or debugging.
     */
    public Map<Pattern, Double> computeCompatibilityWeights(List<WaveCell> waveCells, OverlapManager overlapManager) {
        Map<Pattern, Double> compatibilityWeights = new HashMap<>();
        for (WaveCell cell : waveCells) {
            for (Pattern pattern : cell.getDomain()) {
                compatibilityWeights.put(pattern, overlapManager.calculateCompatibilityScore(pattern));
            }
        }
        return compatibilityWeights;
    }
}
