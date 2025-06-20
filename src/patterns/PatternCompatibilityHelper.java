package patterns;

public class PatternCompatibilityHelper {
    private final OverlapManager overlapManager;

    public PatternCompatibilityHelper(OverlapManager overlapManager) {
        this.overlapManager = overlapManager;
    }

    public boolean areCompatible(Pattern patternA, Pattern patternB) {
        if (patternA == null || patternB == null) {
            return false;
        }
        // 1. Ask OverlapManager for the numeric overlap score between A and B
        double pairwiseScore = overlapManager.getPairwiseScore(patternA, patternB);

        // 2. Consider them “compatible” if that score is strictly positive
        return (pairwiseScore > 0.0);
    }
}
