package patterns;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

/**
 * Represents which Patterns can appear at each exact hop-distance
 * from a given center pattern, based on matching centerLabels.
 * Duplicate pattern IDs in each layer are removed.
 */
public class Compatibility {
    private final int patternId;
    private final List<List<Integer>> degreeCompatibleIds;

    /**
     * @param patternId             the center pattern's ID
     * @param degreeCompatibleIds   for each k=1..radius, list of compatible pattern IDs
     *                              whose centerLabel matches a label at distance k
     */
    public Compatibility(int patternId, List<List<Integer>> degreeCompatibleIds) {
        this.patternId = patternId;
        this.degreeCompatibleIds = degreeCompatibleIds;
    }

    /**
     * @return per-degree lists of compatible pattern IDs (index 0 -> distance=1)
     *         with duplicates removed
     */
    public List<List<Integer>> getDegreeCompatibleIds() {
        List<List<Integer>> copy = new ArrayList<>(degreeCompatibleIds.size());
        for (List<Integer> layer : degreeCompatibleIds) {
            copy.add(new ArrayList<>(layer));
        }
        return copy;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(
                String.format("Pattern ID: %d", patternId)
        );
        for (int i = 0; i < degreeCompatibleIds.size(); i++) {
            sb.append(String.format(", %d.degreePatterns' IDs: %s", i + 1, degreeCompatibleIds.get(i)));
        }
        return sb.toString();
    }

    /**
     * Computes compatibility lists for all Patterns: for each Pattern,
     * at each distance k, gathers unique IDs of all Patterns whose centerLabel
     * equals one of the labels at distance k in the original Pattern.
     *
     * @param patterns list of extracted Patterns (all same radius)
     * @return a Compatibility object per Pattern, in the same order
     */
    public static List<Compatibility> computeCompatibility(List<Pattern> patterns) {
        // Map label -> all pattern IDs with that centerLabel
        Map<Integer, List<Integer>> labelToIds = new HashMap<>();
        for (Pattern p : patterns) {
            labelToIds.computeIfAbsent(p.getCenterLabel(), lbl -> new ArrayList<>())
                    .add(p.getId());
        }

        List<Compatibility> result = new ArrayList<>();
        for (Pattern p : patterns) {
            int radius = p.getRadius();
            List<List<Integer>> compIds = new ArrayList<>();
            for (int d = 1; d <= radius; d++) {
                List<Integer> labelsAtD = p.getDegreeLayers().get(d - 1);
                Set<Integer> idsSet = new LinkedHashSet<>();
                for (Integer lbl : labelsAtD) {
                    List<Integer> matches = labelToIds.getOrDefault(lbl, Collections.emptyList());
                    idsSet.addAll(matches);
                }
                compIds.add(new ArrayList<>(idsSet));
            }
            result.add(new Compatibility(p.getId(), compIds));
        }
        return result;
    }
}
