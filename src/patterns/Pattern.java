package patterns;

import java.util.*;
import java.util.stream.Collectors;


/**
 * Represents a pattern in a graph centered on a specific node within a given radius.
 *
 * Captures the following structural information of the induced ego-network:
 * - node labels
 * - adjacency among included nodes
 * - layering of nodes by distance from the center
 * - depths (exact distances) of each node
 * - occurrence frequency
 * - original degree of the center node
 *
 * Also computes a canonical form string to enable structural equivalence checks
 * (via equals() and hashCode()).
 */

public class Pattern {
    private final int id;
    private final int centerLabel;
    private final int radius;
    private final Map<Integer, Integer> labels;          // nodeID -> label
    private final Map<Integer, List<Integer>> adjacency; // nodeID -> neighbor IDs in subgraph
    private final List<Set<Integer>> layers;             // index k-1 = set of nodeIDs at distance k
    private final Map<Integer, Integer> depths;          // nodeID -> distance from center
    private int frequency;
    private final int centerNodeDegree;
    private final String canonicalForm;

    /**
     * Constructs a Pattern encapsulating all details of an ego-network around a center node.
     *
     * @param  id                center node ID
     * @param  centerLabel       label of the center node
     * @param  radius            maximum hop-distance to include
     * @param  labels            map of each node ID to its label
     * @param  adjacency         induced adjacency among subgraph nodes (nodeID → neighbor IDs)
     * @param  layers            per-distance sets of node IDs (index k-1 = distance k)
     * @param  depths            map of each node ID to its distance from the center
     * @param  frequency         initial occurrence count for this pattern
     * @param  centerNodeDegree  degree of the center node in the original graph
     */
    public Pattern(int id,
                   int centerLabel,
                   int radius,
                   Map<Integer,Integer> labels,
                   Map<Integer,List<Integer>> adjacency,
                   List<Set<Integer>> layers,
                   Map<Integer,Integer> depths,
                   int frequency, int centerNodeDegree) {
        this.id = id;
        this.centerLabel = centerLabel;
        this.radius = radius;
        // store unmodifiable copies
        this.labels = Collections.unmodifiableMap(new LinkedHashMap<>(labels));
        this.centerNodeDegree = centerNodeDegree;
        Map<Integer, List<Integer>> adjCopy = new LinkedHashMap<>();
        for (Map.Entry<Integer,List<Integer>> e : adjacency.entrySet()) {
            adjCopy.put(e.getKey(), List.copyOf(e.getValue()));
        }
        this.adjacency = Collections.unmodifiableMap(adjCopy);
        List<Set<Integer>> layersCopy = new ArrayList<>();
        for (Set<Integer> layer : layers) {
            layersCopy.add(Collections.unmodifiableSet(new LinkedHashSet<>(layer)));
        }
        this.layers = Collections.unmodifiableList(layersCopy);
        this.depths = Collections.unmodifiableMap(new LinkedHashMap<>(depths));
        this.frequency = frequency;
        this.canonicalForm = computeCanonicalForm();

    }

    /**
     * Computes the canonical form of this pattern using two rounds of
     * Weisfeiler–Lehman color refinement followed by a deterministic
     * node ordering and adjacency normalization.
     *
     * Preconditions:
     * - depths, labels, and adjacency must be non-null.
     * - depths must not be empty.
     * - labels.keySet() must equal depths.keySet().
     * - adjacency.keySet() must contain all of depths.keySet().
     *
     * @return a string that uniquely represents this pattern’s structure
     * @throws IllegalArgumentException if any precondition is violated
     */
    private String computeCanonicalForm() {
        // 1) Validate inputs to fail fast on malformed state
        Objects.requireNonNull(depths,   "depth map must not be null");
        Objects.requireNonNull(labels,   "label map must not be null");
        Objects.requireNonNull(adjacency,"adjacency map must not be null");
        if (depths.isEmpty()) {
            throw new IllegalArgumentException("Pattern has no nodes");
        }
        if (!labels.keySet().equals(depths.keySet())
                || !adjacency.keySet().containsAll(depths.keySet())) {
            throw new IllegalArgumentException(
                    "Mismatch among depths, labels, and adjacency keys");
        }

        // 2) Initial coloring: each node’s color = hash(depth, label)
        Map<Integer,Integer> color = new LinkedHashMap<>();
        for (Integer v : depths.keySet()) {
            int d = depths.get(v);
            int lbl = labels.get(v);
            color.put(v, Objects.hash(d, lbl));
        }

        // 3) Two rounds of WL refinement:
        //    For each node, collect neighbor colors, sort them, then hash
        for (int round = 0; round < 2; round++) {
            Map<Integer,Integer> next = new LinkedHashMap<>();
            for (Integer v : color.keySet()) {
                // gather colors of adjacent nodes (only those already colored)
                List<Integer> neighborColors = new ArrayList<>();
                for (Integer u : adjacency.getOrDefault(v, Collections.emptyList())) {
                    Integer c = color.get(u);
                    if (c != null) {
                        neighborColors.add(c);
                    }
                }
                // sort to form a multiset representation
                Collections.sort(neighborColors);
                // new color = hash(previous color, multiset of neighbor colors)
                next.put(v, Objects.hash(color.get(v), neighborColors));
            }
            color = next;
        }

        // 4) Freeze final colors for use in sorting and lambdas
        Map<Integer,Integer> finalColor = color;

        // 5) Determine a stable node ordering by (color, depth, label)
        List<Integer> nodes = new ArrayList<>(finalColor.keySet());
        nodes.sort(
                Comparator.comparing(finalColor::get)
                        .thenComparing(depths::get)
                        .thenComparing(labels::get)
        );

        // 6) Build an index map: original node ID → normalized index
        Map<Integer,Integer> indexMap = new LinkedHashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            indexMap.put(nodes.get(i), i);
        }

        // 7) Assemble the canonical string representation
        StringJoiner sj = new StringJoiner(";");
        for (int i = 0; i < nodes.size(); i++) {
            Integer orig = nodes.get(i);
            // map each neighbor to its new index, filter out any missing
            List<Integer> nbrIdx = adjacency.getOrDefault(orig, Collections.emptyList())
                    .stream()
                    .map(indexMap::get)
                    .filter(Objects::nonNull)
                    .sorted()
                    .collect(Collectors.toList());
            // format: (index:c=<color>,d=<depth>,l=<label>→[neighborIdxs])
            sj.add(String.format("(%d:c=%d,d=%d,l=%d→%s)",
                    i,
                    finalColor.get(orig),
                    depths.get(orig),
                    labels.get(orig),
                    nbrIdx
            ));
        }

        return sj.toString();
    }




    /**
     * Two patterns are considered equal if their canonical forms match.
     * This is used to ensure that patterns with the same structure but different IDs
     * are treated as equivalent.
     *
     * @param o the object to compare with
     * @return true if this pattern is equal to the given object
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pattern)) return false;
        Pattern other = (Pattern) o;
        return this.canonicalForm.equals(other.canonicalForm);
    }

    /**
     * Two patterns are considered equal if their canonical forms match.
     * This is used to ensure that patterns with the same structure but different IDs
     * are treated as equivalent.
     *
     * @return true if this pattern is equal to the given object
     */
    @Override
    public int hashCode() {
        return canonicalForm.hashCode();
    }


    /**
     * Returns a readable summary of this pattern’s key properties and layers.
     *
     * This includes the pattern ID, center label, radius, frequency, and center node degree,
     * followed by each layer’s nodes (with their labels) and, for each node, the list of
     * same-layer and next-layer neighbors. Note that this output is intended for logging
     * and debugging; it does not expose the internal canonical form used for equality checks.
     *
     * @return a multi-line string summarizing the pattern’s structure and layers
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(
                String.format("Pattern ID: %d(label=%d), Radius: %d, Frequency: %d, CenterDegree: %d",
                        id, centerLabel, radius, frequency, centerNodeDegree)
        );
        for (int k = 1; k <= layers.size(); k++) {
            Set<Integer> layerSet = layers.get(k - 1);
            List<String> layerDisplay = layerSet.stream()
                    .map(nodeId -> String.format("%d(%d)", nodeId, labels.get(nodeId)))
                    .collect(Collectors.toList());
            sb.append(String.format("\n%d-hop nodes: %s", k, layerDisplay));
            for (Integer nodeId : layerSet) {
                int d = depths.get(nodeId);
                List<String> sameLayer = adjacency.get(nodeId).stream()
                        .filter(n -> depths.get(n) == d)
                        .map(n -> String.format("%d(%d)", n, labels.get(n)))
                        .collect(Collectors.toList());
                List<String> outLayer = adjacency.get(nodeId).stream()
                        .filter(n -> depths.get(n) == d + 1)
                        .map(n -> String.format("%d(%d)", n, labels.get(n)))
                        .collect(Collectors.toList());
                sb.append(String.format(
                        "\n  %d(%d): same-layer → %s, next-layer → %s",
                        nodeId,
                        labels.get(nodeId),
                        sameLayer,
                        outLayer
                ));
            }
        }
        return sb.toString();
    }


    /**
     * @return the canonical form of this pattern, used for equality checks
     */

    public int getCenterNodeDegree() {
        return centerNodeDegree;
    }
    /**
     * @return unique ID of this pattern
     */
    public int getId() {
        return id;
    }

    /**
     * @return the label of the center node
     */
    public int getCenterLabel() {
        return centerLabel;
    }

    /**
     * @return the maximum radius of this pattern
     */
    public int getRadius() {
        return radius;
    }

    /**
     * @return how many times this pattern has been observed
     */
    public int getFrequency() {
        return frequency;
    }

    /**
     * Increment the occurrence count by one.
     */
    public void updateFrequency() {
        this.frequency++;
    }

    /**
     * @return unmodifiable map of nodeID to label
     */
    public Map<Integer,Integer> getLabels() {
        return labels;
    }

    /**
     * @return unmodifiable adjacency lists within the induced subgraph
     */
    public Map<Integer,List<Integer>> getAdjacency() {
        return adjacency;
    }

    /**
     * @return unmodifiable list of sets of node IDs by exact distance
     */
    public List<Set<Integer>> getLayers() {
        return layers;
    }

    /**
     * @return unmodifiable map of nodeID to its distance from center
     */
    public Map<Integer,Integer> getDepths() {
        return depths;
    }
}
