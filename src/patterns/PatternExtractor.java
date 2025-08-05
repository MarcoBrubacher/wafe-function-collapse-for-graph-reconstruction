package patterns;

import helper.Graph;
import helper.Node;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds ego-network Patterns for each node in a graph up to a given radius.
 * For each center node, performs a BFS to depth ≤ radius, captures node labels,
 * induced adjacency, layering by distance, and depths, then deduplicates
 * identical Patterns by canonical form—incrementing frequency for duplicates.
 */
public final class PatternExtractor {
    // Prevent instantiation
    private PatternExtractor() { }

    /**
     * Extracts ego-network patterns for all nodes in the graph at the specified radius,
     * deduplicates identical patterns, and aggregates their frequencies.
     *
     * @param  graph   the input graph; must not be null
     * @param  radius  maximum hop-distance (must be ≥ 1)
     * @return         list of unique Patterns, each with its aggregated frequency
     * @throws IllegalArgumentException if graph is null or radius < 1
     */
    public static List<Pattern> extractPatterns(Graph graph, int radius) {
        Objects.requireNonNull(graph, "graph must not be null");
        if (radius < 1) {
            throw new IllegalArgumentException("radius must be ≥ 1");
        }

        // Preserve insertion order of first-seen patterns
        Map<Pattern, Pattern> unique = new LinkedHashMap<>();
        for (Node center : graph.getAllNodes()) {
            Pattern p = buildPattern(center, radius);
            Pattern existing = unique.get(p);
            if (existing == null) {
                unique.put(p, p);
            } else {
                existing.updateFrequency();
            }
        }
        return new ArrayList<>(unique.values());
    }

    /**
     * Builds a single Pattern for the given center node with initial frequency = 1.
     *
     * @param  center  the center node; must not be null
     * @param  radius  maximum hop-distance (≥ 1)
     * @return         a new Pattern capturing the center’s ego-network
     */
    private static Pattern buildPattern(Node center, int radius) {
        // 1) Compute BFS depths from center
        Map<Node,Integer> nodeDepths = computeDepths(center, radius);

        // 2) Map node IDs to labels and depths
        Map<Integer,Integer> labels = new LinkedHashMap<>();
        Map<Integer,Integer> depths = new LinkedHashMap<>();
        for (Map.Entry<Node,Integer> e : nodeDepths.entrySet()) {
            Node n = e.getKey();
            labels.put(n.getId(), n.getLabel());
            depths.put(n.getId(), e.getValue());
        }

        // 3) Build per-distance layers
        List<Set<Integer>> layers = computeLayers(depths, radius);

        // 4) Build induced adjacency among nodes within radius
        Set<Integer> subIds = labels.keySet();
        Map<Integer,List<Integer>> adjacency = new LinkedHashMap<>();
        for (Node n : nodeDepths.keySet()) {
            List<Integer> nbrs = n.getNeighbors().stream()
                    .map(Node::getId)
                    .filter(subIds::contains)
                    .collect(Collectors.toList());
            adjacency.put(n.getId(), nbrs);
        }

        // 5) Center node degree = number of neighbors at distance 1
        int centerNodeDegree = layers.get(0).size();

        // 6) Construct Pattern with frequency = 1
        return new Pattern(
                center.getId(),
                center.getLabel(),
                radius,
                labels,
                adjacency,
                layers,
                depths,
                1,
                centerNodeDegree
        );
    }

    /**
     * Performs a breadth-first search from the center up to the given radius.
     *
     * @param  center  start node for BFS; must not be null
     * @param  radius  maximum hop-distance (≥ 1)
     * @return         map of each reached Node to its distance from center
     */
    private static Map<Node,Integer> computeDepths(Node center, int radius) {
        Map<Node,Integer> depthMap = new LinkedHashMap<>();
        Queue<Node> queue = new ArrayDeque<>();

        depthMap.put(center, 0);
        queue.add(center);

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            int dist = depthMap.get(current);
            if (dist >= radius) continue;

            for (Node nbr : current.getNeighbors()) {
                if (!depthMap.containsKey(nbr)) {
                    depthMap.put(nbr, dist + 1);
                    queue.add(nbr);
                }
            }
        }
        return depthMap;
    }

    /**
     * Constructs layers of node IDs by exact distance from the center.
     *
     * @param  depths  map of nodeID → distance (must contain 0…radius)
     * @param  radius  maximum distance
     * @return         list of size = radius, where index k-1 holds nodes at distance k
     */
    private static List<Set<Integer>> computeLayers(Map<Integer,Integer> depths, int radius) {
        List<Set<Integer>> layers = new ArrayList<>();
        for (int k = 1; k <= radius; k++) {
            layers.add(new LinkedHashSet<>());
        }
        for (Map.Entry<Integer,Integer> e : depths.entrySet()) {
            int id = e.getKey(), d = e.getValue();
            if (d >= 1 && d <= radius) {
                layers.get(d - 1).add(id);
            }
        }
        return Collections.unmodifiableList(layers);
    }

    /**
     * Prints each extracted Pattern in a human-readable format.
     *
     * @param  graph   the input graph
     * @param  radius  maximum hop-distance
     */
    public static void printPatterns(Graph graph, int radius) {
        extractPatterns(graph, radius).forEach(p -> {
            System.out.println(p);
            System.out.println();
        });
    }
}
