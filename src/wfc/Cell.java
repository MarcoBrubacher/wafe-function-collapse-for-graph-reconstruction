package wfc;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Collection;

/**
 * Represents a superstate 'cell' holding a set of possible pattern IDs.
 * Can be pruned by external constraints and collapsed to a single pattern,
 * after which it behaves as a fixed node with its centerLabel.
 *
 * This class is a pure domain container; all pruning logic is handled externally.
 */
public class Cell {
    private final Set<Integer> possiblePatterns;
    private Integer collapsedPatternId = null;
    private Integer centerLabel = null;

    /**
     * Initialize a cell with the given candidate pattern IDs.
     * @param initialPatterns collection of all pattern IDs allowed initially
     */
    public Cell(Collection<Integer> initialPatterns) {
        this.possiblePatterns = new LinkedHashSet<>(initialPatterns);
    }

    /**
     * @return unmodifiable view of current possible pattern IDs
     * @throws IllegalStateException if the cell is already collapsed
     */
    public Set<Integer> getPossiblePatterns() {
        if (isCollapsed()) throw new IllegalStateException("Cell is already collapsed");
        return Collections.unmodifiableSet(possiblePatterns);
    }

    /**
     * Prune the cell's possibilities, keeping only those in the provided set.
     * @param allowed set of pattern IDs to retain
     * @throws IllegalStateException if the cell is already collapsed
     */
    public void prune(Set<Integer> allowed) {
        if (isCollapsed()) throw new IllegalStateException("Cell is already collapsed");
        possiblePatterns.retainAll(allowed);
    }

    /**
     * Collapse the cell to a single pattern ID and record its center label.
     * After collapsing, the cell becomes fixed and cannot be changed.
     * @param patternId     the ID to collapse to; must currently be possible
     * @param centerLabel   the center label of the chosen pattern
     * @throws IllegalArgumentException if patternId is not in the current possibilities
     * @throws IllegalStateException if the cell is already collapsed
     */
    public void collapseTo(int patternId, int centerLabel) {
        if (isCollapsed()) throw new IllegalStateException("Cell is already collapsed");
        if (!possiblePatterns.contains(patternId)) {
            throw new IllegalArgumentException("Cannot collapse to non-possible pattern: " + patternId);
        }
        this.collapsedPatternId = patternId;
        this.centerLabel = centerLabel;
        possiblePatterns.clear();
        possiblePatterns.add(patternId);
    }

    /**
     * @return true if the cell has been collapsed to exactly one pattern
     */
    public boolean isCollapsed() {
        return collapsedPatternId != null;
    }

    /**
     * @return the single pattern ID if the cell is collapsed
     * @throws IllegalStateException if the cell is not yet collapsed
     */
    public int getCollapsedPattern() {
        if (!isCollapsed()) {
            throw new IllegalStateException("Cell not yet collapsed");
        }
        return collapsedPatternId;
    }

    /**
     * @return the center label of the collapsed pattern
     * @throws IllegalStateException if the cell is not yet collapsed
     */
    public int getCenterLabel() {
        if (!isCollapsed()) {
            throw new IllegalStateException("Cell not yet collapsed");
        }
        return centerLabel;
    }

    @Override
    public String toString() {
        if (isCollapsed()) {
            return String.format("Cell collapsed to pattern %d with centerLabel=%d",
                    collapsedPatternId, centerLabel);
        } else {
            return "Cell possiblePatterns=" + possiblePatterns;
        }
    }
}
