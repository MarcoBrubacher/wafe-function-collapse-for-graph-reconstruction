package patterns;

import helper.Edge;
import helper.Node;

import java.util.*;

/**
 * Refactored OverlapManager that:
 *  1) Gathers all simple paths (no repeated nodes) up to 'radius' for each Pattern.
 *  2) Compares each pair of Patterns (A,B) exactly once in a double loop
 *     and stores reversed-path matches in overlapPaths[A][B] and overlapPaths[B][A].
 *  3) Uses a basic overlap ratio for getPairwiseScore(...), then normalizes it.
 *  4) Respects the patternFrequencyMap for usage/frequency counts.
 */
public class OverlapManager {

    // Maps patternA -> (patternB -> list of node-paths in A that match reversed paths in B)
    private final Map<Pattern, Map<Pattern, List<List<Node>>>> overlapPaths = new HashMap<>();

    // For each Pattern, how many outward (simple) paths it has (up to BFS radius).
    private final Map<Pattern, Integer> outwardPathCount = new HashMap<>();

    // Frequency of each pattern in the training data
    private final Map<Pattern, Integer> patternFrequency = new HashMap<>();

    // Size of each pattern's subgraph (# of nodes in its ego-network).
    private final Map<Pattern, Integer> subgraphSize = new HashMap<>();

    // Min/Max used for normalizing overlap ratio
    private double overlapRatioMin = Double.POSITIVE_INFINITY;
    private double overlapRatioMax = Double.NEGATIVE_INFINITY;

    private double freqValMin = Double.POSITIVE_INFINITY;
    private double freqValMax = Double.NEGATIVE_INFINITY;

    private double sizeValMin = Double.POSITIVE_INFINITY;
    private double sizeValMax = Double.NEGATIVE_INFINITY;

    /**
     * Builds an OverlapManager by:
     *   1) Collecting all simple paths for each Pattern up to 'radius'.
     *   2) Comparing each unique pair (i,j) for reversed path matches.
     *   3) Initializing various min/max stats for normalization.
     *
     * @param patterns            All patterns from the training data
     * @param radius              BFS radius used
     * @param patternFrequencyMap Map of each Pattern -> frequency in training
     */
    public OverlapManager(List<Pattern> patterns,
                          int radius,
                          Map<Pattern, Integer> patternFrequencyMap) {

        // 1) Store frequency from the outside map
        for (Pattern p : patternFrequencyMap.keySet()) {
            this.patternFrequency.put(p, patternFrequencyMap.get(p));
        }

        // 2) For each pattern, gather all simple paths + subgraph size
        Map<Pattern, List<List<Node>>> patternToPaths = new HashMap<>();
        for (Pattern pat : patterns) {
            // All simple paths of length <= radius
            List<List<Node>> allPaths = computeAllPaths(pat, radius);
            patternToPaths.put(pat, allPaths);
            outwardPathCount.put(pat, allPaths.size());

            // subgraph size (# nodes in BFS subgraph)
            int subgraphNodeCount = pat.getNeighborNodes().size() + 1; // +1 for center
            subgraphSize.put(pat, subgraphNodeCount);
        }

        // 3) For each pair of patterns (A,B) with i<j, record reversed matches
        int n = patterns.size();
        for (int i = 0; i < n; i++) {
            Pattern A = patterns.get(i);
            List<List<Node>> pathsA = patternToPaths.getOrDefault(A, Collections.emptyList());

            for (int j = i + 1; j < n; j++) {
                Pattern B = patterns.get(j);
                List<List<Node>> pathsB = patternToPaths.getOrDefault(B, Collections.emptyList());

                recordOverlapPaths(A, B, pathsA, pathsB);
            }
        }

        // 4) With overlapPaths filled, do min/max stats for normalization
        initializeStatistics(patterns);
    }

    // --------------------------------------------------------------------
    // Step 1: Collect all simple paths up to radius for each pattern
    // --------------------------------------------------------------------

    /**
     * Gathers all simple paths (no repeated nodes) of length up to 'radius'
     * in the BFS subgraph around pattern.getCenterNode().
     */
    private List<List<Node>> computeAllPaths(Pattern pattern, int radius) {
        List<List<Node>> results = new ArrayList<>();
        Node center = pattern.getCenterNode();
        if (center == null) {
            return results;
        }

        // 1) BFS to find which nodes are within distance <= radius
        Map<Node,Integer> distMap = buildDistanceMap(pattern, center, radius);

        // 2) Build adjacency for only those nodes
        Map<Node, Set<Node>> subgraphAdj = buildSubgraphAdjacency(pattern, distMap, radius);

        // 3) DFS collecting all simple paths up to length radius
        Set<Node> visited = new HashSet<>();
        List<Node> pathSoFar = new ArrayList<>();

        visited.add(center);
        pathSoFar.add(center);

        collectAllPaths(center, radius, pathSoFar, visited, results, subgraphAdj);

        return results;
    }

    private Map<Node,Integer> buildDistanceMap(Pattern pattern, Node center, int radius) {
        // Induced set = center + neighborNodes
        Set<Node> nodes = new HashSet<>(pattern.getNeighborNodes());
        nodes.add(center);

        // Build adjacency
        Map<Node, Set<Node>> adjacency = new HashMap<>();
        for (Node nd : nodes) {
            adjacency.put(nd, new HashSet<>());
        }
        for (Edge e : pattern.getNeighborEdges()) {
            Node a = e.getNodeA();
            Node b = e.getNodeB();
            if (adjacency.containsKey(a) && adjacency.containsKey(b)) {
                adjacency.get(a).add(b);
                adjacency.get(b).add(a);
            }
        }

        // BFS
        Map<Node,Integer> dist = new HashMap<>();
        dist.put(center, 0);
        Queue<Node> queue = new LinkedList<>();
        queue.offer(center);

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            int d = dist.get(current);
            if (d >= radius) {
                continue;
            }
            for (Node nbr : adjacency.get(current)) {
                if (!dist.containsKey(nbr)) {
                    dist.put(nbr, d + 1);
                    queue.offer(nbr);
                }
            }
        }
        return dist;
    }

    private Map<Node, Set<Node>> buildSubgraphAdjacency(
            Pattern pattern,
            Map<Node,Integer> distMap,
            int radius
    ) {
        Map<Node, Set<Node>> subgraph = new HashMap<>();
        // Only keep nodes with distance <= radius
        for (Node nd : distMap.keySet()) {
            if (distMap.get(nd) <= radius) {
                subgraph.put(nd, new HashSet<>());
            }
        }

        // Among those nodes, keep edges that connect them
        for (Edge e : pattern.getNeighborEdges()) {
            Node a = e.getNodeA();
            Node b = e.getNodeB();
            if (subgraph.containsKey(a) && subgraph.containsKey(b)) {
                subgraph.get(a).add(b);
                subgraph.get(b).add(a);
            }
        }
        return subgraph;
    }

    /**
     * DFS collecting all simple paths (no repeated nodes) up to 'stepsRemaining'.
     */
    private void collectAllPaths(
            Node current,
            int stepsRemaining,
            List<Node> currPath,
            Set<Node> visited,
            List<List<Node>> results,
            Map<Node, Set<Node>> subgraphAdj
    ) {
        if (stepsRemaining == 0) {
            return;
        }

        // Explore neighbors
        for (Node nbr : subgraphAdj.getOrDefault(current, Collections.emptySet())) {
            if (visited.contains(nbr)) {
                continue;
            }
            visited.add(nbr);
            currPath.add(nbr);

            // We record the newly extended path as one possible path
            results.add(new ArrayList<>(currPath));

            collectAllPaths(nbr, stepsRemaining - 1, currPath, visited, results, subgraphAdj);

            // backtrack
            currPath.remove(currPath.size() - 1);
            visited.remove(nbr);
        }
    }

    // --------------------------------------------------------------------
    // Step 2: Compare pairs (A,B) for reversed label matches
    // --------------------------------------------------------------------

    private void recordOverlapPaths(
            Pattern patternA,
            Pattern patternB,
            List<List<Node>> pathsA,
            List<List<Node>> pathsB
    ) {
        for (List<Node> pathA : pathsA) {
            for (List<Node> pathB : pathsB) {
                if (pathA.size() == pathB.size() && isReverse(pathA, pathB)) {
                    // Store pathA in overlapPaths[A][B]
                    overlapPaths
                            .computeIfAbsent(patternA, x -> new HashMap<>())
                            .computeIfAbsent(patternB, x -> new ArrayList<>())
                            .add(new ArrayList<>(pathA));

                    // Also store pathB in overlapPaths[B][A]
                    overlapPaths
                            .computeIfAbsent(patternB, x -> new HashMap<>())
                            .computeIfAbsent(patternA, x -> new ArrayList<>())
                            .add(new ArrayList<>(pathB));
                }
            }
        }
    }

    private boolean isReverse(List<Node> pathA, List<Node> pathB) {
        int len = pathA.size();
        for (int i = 0; i < len; i++) {
            String labelA = pathA.get(i).getLabel();
            String labelB = pathB.get(len - 1 - i).getLabel();
            if (!Objects.equals(labelA, labelB)) {
                return false;
            }
        }
        return true;
    }

    // --------------------------------------------------------------------
    // Step 3: Initialize Min/Max stats for Overlap Ratio, Frequency, Size
    // --------------------------------------------------------------------

    private void initializeStatistics(List<Pattern> patterns) {
        for (Pattern A : patterns) {
            int pA  = outwardPathCount.getOrDefault(A, 1);
            int fA  = patternFrequency.getOrDefault(A, 1);
            int szA = subgraphSize.getOrDefault(A, 1);

            Map<Pattern, List<List<Node>>> overlapMap
                    = overlapPaths.getOrDefault(A, Collections.emptyMap());

            for (Map.Entry<Pattern, List<List<Node>>> e : overlapMap.entrySet()) {
                Pattern B = e.getKey();
                List<List<Node>> overlapAB = e.getValue();
                int x = overlapAB.size();

                int pB  = outwardPathCount.getOrDefault(B, 1);
                int fB  = patternFrequency.getOrDefault(B, 1);
                int szB = subgraphSize.getOrDefault(B, 1);

                // Overlap ratio
                double ratio = (double) x / Math.min(pA, pB);
                overlapRatioMin = Math.min(overlapRatioMin, ratio);
                overlapRatioMax = Math.max(overlapRatioMax, ratio);

                // Average freq
                double avgFreq = (fA + fB) / 2.0;
                freqValMin = Math.min(freqValMin, avgFreq);
                freqValMax = Math.max(freqValMax, avgFreq);

                // Average size
                double avgSize = (szA + szB) / 2.0;
                sizeValMin = Math.min(sizeValMin, avgSize);
                sizeValMax = Math.max(sizeValMax, avgSize);
            }
        }

        // If none found any overlap, clamp to 0
        if (overlapRatioMin == Double.POSITIVE_INFINITY) {
            overlapRatioMin = 0.0;
            overlapRatioMax = 0.0;
            freqValMin      = 0.0;
            freqValMax      = 0.0;
            sizeValMin      = 0.0;
            sizeValMax      = 0.0;
        }
    }

    // --------------------------------------------------------------------
    // Step 4: Accessors for Overlap Scores
    // --------------------------------------------------------------------

    /**
     * getPairwiseScore(A,B): how strongly A and B overlap based on reversed paths.
     * If >0 => they are "compatible."
     */
    public double getPairwiseScore(Pattern A, Pattern B) {
        if (A == null || B == null) return 0.0;

        Map<Pattern, List<List<Node>>> overlapFromA
                = overlapPaths.getOrDefault(A, Collections.emptyMap());
        List<List<Node>> listAB
                = overlapFromA.getOrDefault(B, Collections.emptyList());
        int overlapCount = listAB.size();

        // precomputed stats
        int pA  = outwardPathCount.getOrDefault(A, 1);
        int fA  = patternFrequency.getOrDefault(A, 1);
        int sA  = subgraphSize.getOrDefault(A, 1);

        int pB  = outwardPathCount.getOrDefault(B, 1);
        int fB  = patternFrequency.getOrDefault(B, 1);
        int sB  = subgraphSize.getOrDefault(B, 1);

        return computeOverlapScore(pA, fA, sA, pB, fB, sB, overlapCount);
    }

    /**
     * Summation of overlap for patternA across all pairs (A,B).
     * This can be used as an aggregate measure of how "compatible"
     * patternA is with the rest.
     */
    public double calculateCompatibilityScore(Pattern A) {
        Map<Pattern, List<List<Node>>> overlapMap
                = overlapPaths.getOrDefault(A, Collections.emptyMap());

        int pA  = outwardPathCount.getOrDefault(A, 1);
        int fA  = patternFrequency.getOrDefault(A, 1);
        int sA  = subgraphSize.getOrDefault(A, 1);

        double total = 0.0;
        for (Map.Entry<Pattern, List<List<Node>>> e : overlapMap.entrySet()) {
            Pattern B = e.getKey();
            List<List<Node>> overlapAB = e.getValue();
            int x = overlapAB.size();

            total += computeOverlapScore(pA, fA, sA,
                    outwardPathCount.getOrDefault(B, 1),
                    patternFrequency.getOrDefault(B, 1),
                    subgraphSize.getOrDefault(B, 1),
                    x);
        }
        return total;
    }

    /**
     * Overlap ratio => normalization => combine with freq & size
     * (currently only normalizes the ratio, ignoring freq+size in the final product).
     */
    private double computeOverlapScore(int pA, int freqA, int szA,
                                       int pB, int freqB, int szB,
                                       int overlapCount) {

        // Overlap ratio = overlapCount / min(pA, pB)
        double rawOverlapRatio = (pA == 0 || pB == 0)
                ? 0.0
                : (overlapCount / (double)Math.min(pA, pB));

        double ratioNorm = normalize(rawOverlapRatio, overlapRatioMin, overlapRatioMax);
        // Potentially incorporate freq or size if desired:
        // e.g. ratioNorm *= frequencyFactor(...) or ratioNorm *= sizeFactor(...)

        return ratioNorm;
    }

    /**
     * Normalizes a raw value to [0..1] given a known (minVal..maxVal) range.
     */
    private double normalize(double raw, double minVal, double maxVal) {
        if (maxVal <= minVal) {
            return 0.0; // degenerate
        }
        if (raw < minVal) raw = minVal;
        if (raw > maxVal) raw = maxVal;
        return (raw - minVal) / (maxVal - minVal);
    }

    // --------------------------------------------------------------------
    // Overlap Path Pair Visualization
    // --------------------------------------------------------------------

    /**
     * Return pairs of reversed paths for (A,B).
     */
    public List<PathPair> getOverlapPathPairs(Pattern A, Pattern B) {
        Map<Pattern, List<List<Node>>> mapFromA = overlapPaths.get(A);
        if (mapFromA == null) return Collections.emptyList();

        List<List<Node>> listA = mapFromA.get(B);
        if (listA == null) return Collections.emptyList();

        Map<Pattern, List<List<Node>>> mapFromB = overlapPaths.get(B);
        if (mapFromB == null) return Collections.emptyList();

        List<List<Node>> listB = mapFromB.get(A);
        if (listB == null) return Collections.emptyList();

        // Pair them up in order; they should have the same length
        int count = Math.min(listA.size(), listB.size());
        List<PathPair> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(new PathPair(listA.get(i), listB.get(i)));
        }
        return result;
    }

    public static class PathPair {
        public final List<Node> pathInA;
        public final List<Node> pathInB;

        public PathPair(List<Node> pathInA, List<Node> pathInB) {
            this.pathInA = pathInA;
            this.pathInB = pathInB;
        }
    }
}
