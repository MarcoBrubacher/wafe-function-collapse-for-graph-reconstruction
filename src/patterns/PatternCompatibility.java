package patterns;

import helper.Graph;

import java.util.*;


/**
 * Computes and organizes compatibility relationships between ego-network Patterns.
 *
 * <p>This utility class provides methods to determine, for each Pattern extracted
 * from a graph, which other Patterns it can “connect” to based on matching label
 * sequences.  Compatibility is defined by the existence of an outward label-path
 * from one Pattern whose exact sequence of labels—starting at its center and
 * stepping strictly outward to its maximum radius—matches the reverse of an
 * outward path from another Pattern.  By working with label sequences rather than
 * raw adjacency or layer sets, we avoid any confusion between “layers” (sets of
 * nodes at a given distance) and the ordered notion of a path.</p>
 *
 * Internally, for each Pattern it:
 *
 *   Extracts all outward label-paths of length radius+1 (center → depth=radius)
 *       using a depth-first traversal over the Pattern’s adjacency restricted to
 *       strictly increasing depths.
 *   Builds a HashSet of the reversed paths for each Pattern, enabling O(1)
 *       membership tests when comparing sequences.
 *   Performs a pairwise comparison of Patterns (i &lt; j): if any of i’s
 *       outward paths appears in j’s reversed-path set, then i and j are marked
 *       mutually compatible.
 *   Packages the results into PatternCompatibility objects (one per input
 *       Pattern), each recording its own ID and the set of IDs it can connect to.
 *
 *
 *Because we extract paths by following neighbors whose recorded depth equals
 * the previous depth+1, we never mix up different layers or revisit shallower
 * nodes.  Layer information is only used to guide which edges extend outward,
 * ensuring that every path strictly ascends through the Pattern’s distance layers
 * and terminates exactly at the specified radius.
 *
 */
public class PatternCompatibility {
    private final int patternId;
    private final Set<Integer> compatibleIds;

    public PatternCompatibility(int patternId) {
        this.patternId = patternId;
        this.compatibleIds = new LinkedHashSet<>();
    }

    /**
     * Determines which Patterns can connect based on matching outward vs. reversed inward label‐paths.
     *
     * For each pair of distinct Patterns (i, j), if any outward label‐path from i
     * equals a reversed outward path of j, they are marked mutually compatible.
     *
     * @param  patterns  list of ego‐network Patterns (all at the same radius); must not be null
     * @return           a list of PatternCompatibility objects, in the same order as input patterns,
     *                   each containing the ID of its pattern and the IDs of all compatible patterns
     * @throws NullPointerException if patterns or any element is null
     */
    public static List<PatternCompatibility> computeCompatibility(List<Pattern> patterns) {
        Objects.requireNonNull(patterns, "patterns list must not be null");
        int n = patterns.size();

        // 1) Initialize result containers with one entry per input Pattern
        List<PatternCompatibility> result = new ArrayList<>(n);
        for (Pattern p : patterns) {
            Objects.requireNonNull(p, "pattern must not be null");
            result.add(new PatternCompatibility(p.getId()));
        }

        // 2) Precompute each Pattern’s outward label‐paths (length = radius+1)
        List<List<List<Integer>>> outwardPaths = new ArrayList<>(n);
        for (Pattern p : patterns) {
            outwardPaths.add(computeOutwardPaths(p));
        }

        // 3) Precompute each Pattern’s reversed‐paths set for O(1) lookups
        List<Set<List<Integer>>> reversedSets = new ArrayList<>(n);
        for (List<List<Integer>> paths : outwardPaths) {
            Set<List<Integer>> rev = new HashSet<>();
            for (List<Integer> path : paths) {
                List<Integer> r = new ArrayList<>(path);
                Collections.reverse(r);
                rev.add(r);
            }
            reversedSets.add(rev);
        }

        // 4) Pairwise compatibility: for i<j, check if any outward path of i is in j’s reversed set
        for (int i = 0; i < n; i++) {
            List<List<Integer>> pathsI = outwardPaths.get(i);
            for (int j = i + 1; j < n; j++) {
                Set<List<Integer>> revJ = reversedSets.get(j);
                boolean compatible = false;
                for (List<Integer> pi : pathsI) {
                    if (revJ.contains(pi)) {
                        compatible = true;
                        break;
                    }
                }
                if (compatible) {
                    int idI = patterns.get(i).getId();
                    int idJ = patterns.get(j).getId();
                    result.get(i).addCompatible(idJ);
                    result.get(j).addCompatible(idI);
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    /**
     * Computes all outward label paths from the center of the given Pattern.
     *
     * Each path is a sequence of labels starting with the center label and
     * extending outward one hop at a time, following only edges that increase
     * the node’s distance from the center, until reaching the specified radius.
     *
     * @param  pattern  the Pattern whose outward paths are extracted; must not be null
     * @return          an unmodifiable list of label‐sequences, each of length radius+1
     * @throws NullPointerException     if pattern or its internal maps are null
     * @throws IllegalArgumentException if radius &lt; 1
     */
    private static List<List<Integer>> computeOutwardPaths(Pattern pattern) {
        // Validate inputs
        Objects.requireNonNull(pattern,        "pattern must not be null");
        Map<Integer,Integer> depths    = Objects.requireNonNull(pattern.getDepths(),    "depths map must not be null");
        Map<Integer,List<Integer>> adj = Objects.requireNonNull(pattern.getAdjacency(), "adjacency map must not be null");
        Map<Integer,Integer> labels    = Objects.requireNonNull(pattern.getLabels(),    "labels map must not be null");
        int radius = pattern.getRadius();
        if (radius < 1) {
            throw new IllegalArgumentException("radius must be at least 1");
        }

        List<List<Integer>> paths = new ArrayList<>();
        Deque<Integer> nodeStack  = new ArrayDeque<>();
        Deque<List<Integer>> labelStack = new ArrayDeque<>();

        // Initialize DFS from center
        nodeStack.push(pattern.getId());
        labelStack.push(List.of(pattern.getCenterLabel()));

        // Depth-first traversal: only follow edges that increase depth
        while (!nodeStack.isEmpty()) {
            int currentNode = nodeStack.pop();
            List<Integer> currentLabels = labelStack.pop();
            int currentDepth = currentLabels.size() - 1;

            // If we've reached the radius, record the label path
            if (currentDepth == radius) {
                paths.add(currentLabels);
                continue;
            }

            // Otherwise, extend along each neighbor at depth+1
            for (Integer neighbor : adj.getOrDefault(currentNode, Collections.emptyList())) {
                int neighborDepth = depths.getOrDefault(neighbor, -1);
                if (neighborDepth == currentDepth + 1) {
                    List<Integer> newPath = new ArrayList<>(currentLabels);
                    newPath.add(labels.get(neighbor));
                    nodeStack.push(neighbor);
                    labelStack.push(newPath);
                }
            }
        }

        return Collections.unmodifiableList(paths);
    }



    /**
     * For each radius in [1…maxRadius], extracts Patterns from the graph
     * and builds a compatibility table mapping each pattern ID to the set
     * of IDs it can connect to.
     *
     * @param  graph      the input graph; must not be null
     * @param  maxRadius  maximum hop-distance (must be ≥ 1)
     * @return            LinkedHashMap where each key is a radius and its value
     *                    is a LinkedHashMap from patternId → compatible patternIds
     * @throws IllegalArgumentException if graph is null or maxRadius < 1
     */
    public static Map<Integer, Map<Integer, Set<Integer>>> computeCompatibilityByRadius(
            Graph graph,
            int maxRadius
    ) {
        Objects.requireNonNull(graph, "graph must not be null");
        if (maxRadius < 1) {
            throw new IllegalArgumentException("maxRadius must be at least 1");
        }

        Map<Integer, Map<Integer, Set<Integer>>> compatibilityByRadius = new LinkedHashMap<>();

        // For each radius, build compatibility table
        for (int radius = 1; radius <= maxRadius; radius++) {
            // 1) Extract all ego-network patterns at this radius
            List<Pattern> patterns = PatternExtractor.extractPatterns(graph, radius);

            // 2) Compute raw compatibility pairs
            List<PatternCompatibility> rawComps = computeCompatibility(patterns);

            // 3) Index by pattern ID into a LinkedHashMap to preserve insertion order
            Map<Integer, Set<Integer>> table = new LinkedHashMap<>();
            for (PatternCompatibility pc : rawComps) {
                // Use a LinkedHashSet to preserve the order of compatible IDs
                table.put(pc.getPatternId(),
                        new LinkedHashSet<>(pc.getCompatibleIds()));
            }

            compatibilityByRadius.put(radius, table);
        }

        return compatibilityByRadius;
    }


    /**
     * Returns the unique ID of this PatternCompatibility.
     * This is the same as the Pattern ID it represents.
     *
     * @return the pattern ID
     */
    public int getPatternId() {
        return patternId;
    }

    /**
     * Returns an unmodifiable set of compatible Pattern IDs.
     * These are the IDs of Patterns that have outward paths matching this one.
     *
     * @return a set of compatible pattern IDs
     */
    public Set<Integer> getCompatibleIds() {
        return Collections.unmodifiableSet(compatibleIds);
    }

    /**
     * Adds another Pattern ID to the set of compatible IDs.
     * This indicates that this Pattern has an outward path matching
     * the reverse of an outward path from the other Pattern.
     *
     * @param otherId the ID of the compatible Pattern
     */
    void addCompatible(int otherId) {
        this.compatibleIds.add(otherId);
    }

    /**
     * Returns a readable string representation of this PatternCompatibility.
     * It includes the pattern ID and the set of compatible IDs.
     *
     * @return a string describing this compatibility
     */
    @Override
    public String toString() {
        return String.format("Pattern %d compatible with: %s",
                patternId,
                compatibleIds);
    }

    /**
     * Prints compatibility lists for each radius 1..maxRadius to stdout.
     * Now iterates the same maps returned by computeAll.
     */
    public static void PrintCompatibility(Graph graph, int maxRadius) {
        Map<Integer, Map<Integer, Set<Integer>>> all =
                computeCompatibilityByRadius(graph, maxRadius);

        for (var entry : all.entrySet()) {
            System.out.println("Compatibility for radius=" + entry.getKey() + ":");
            for (var inner : entry.getValue().entrySet()) {
                System.out.printf("Pattern %d compatible with: %s%n",
                        inner.getKey(),
                        inner.getValue());
            }
        }
    }


}
