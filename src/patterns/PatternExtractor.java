package patterns;

import helper.Graph;
import helper.Node;
import helper.Edge;

import java.util.*;

public class PatternExtractor {

    /**
     * Extracts all unique ego-network patterns (of given radius) from the graph,
     * returning a map from each Pattern to its occurrence count.
     */
    public static Map<Pattern, Integer> extractPatterns(Graph graph, int radius) {
        Map<Pattern, Integer> counts = new HashMap<>();
        for (Node center : graph.getAllNodes()) {
            // 1) BFS to collect distances up to the specified radius
            Map<Node, Integer> dist = bfsDistances(graph, center, radius);

            // 2) Collect candidate nodes at distance 1..radius from center
            Set<Node> egoCandidates = new HashSet<>();
            for (Map.Entry<Node, Integer> entry : dist.entrySet()) {
                int d = entry.getValue();
                if (d >= 1 && d <= radius) {
                    egoCandidates.add(entry.getKey());
                }
            }

            // 3) Build induced subgraph set: {center} ∪ egoCandidates
            Set<Node> inducedSet = new HashSet<>(egoCandidates);
            inducedSet.add(center);

            // 4) Collect edges within induced set and their label pairs
            Set<Edge> edgeObjects = new HashSet<>();
            Set<String> edgeLabels = new TreeSet<>();
            for (Edge edge : graph.getAllEdges()) {
                Node a = edge.getNodeA();
                Node b = edge.getNodeB();
                if (inducedSet.contains(a) && inducedSet.contains(b)) {
                    edgeObjects.add(edge);
                    edgeLabels.add(Graph.sortedLabelPair(a.getLabel(), b.getLabel()));
                }
            }

            // 5) Derive neighbor labels from collected edge label pairs
            LinkedHashSet<String> neighborLabels = new LinkedHashSet<>();
            for (String pair : edgeLabels) {
                String[] parts = pair.split("\\|");
                neighborLabels.add(parts[0]);
                neighborLabels.add(parts[1]);
            }

            // 6) Identify direct neighbors of the center (distance = 1)
            List<Node> directNeighbors = new ArrayList<>();
            for (Map.Entry<Node, Integer> entry : dist.entrySet()) {
                if (entry.getValue() == 1) {
                    directNeighbors.add(entry.getKey());
                }
            }
            // Collect direct neighbor labels (duplicates allowed)
            List<String> directLabelsList = gatherLabelsWithDuplicates(directNeighbors);

            // Prepare neighborNodes list for Pattern (all induced nodes except center)
            List<Node> neighborNodes = new ArrayList<>(inducedSet);
            neighborNodes.remove(center);

            // Create the Pattern for this center and count its occurrences
            Pattern pattern = new Pattern(
                    center.getLabel(),
                    new ArrayList<>(neighborLabels),
                    edgeLabels,
                    directLabelsList,
                    radius
            );
            // Set transient node references for potential later use
            pattern.setCenterNode(center);
            pattern.setNeighborNodes(neighborNodes);
            pattern.setDirectNeighborNodes(directNeighbors);
            pattern.setNeighborEdges(edgeObjects);

            // *** Store the BFS distance map in the pattern,
            //     so we can visualize the exact same layering.
            pattern.setDistanceMap(dist);

            counts.merge(pattern, 1, Integer::sum);
        }

        return counts;
    }

    /**
     * Collects node labels from a list of nodes, preserving duplicates.
     */
    private static List<String> gatherLabelsWithDuplicates(List<Node> nodes) {
        List<String> labels = new ArrayList<>();
        for (Node node : nodes) {
            labels.add(node.getLabel());
        }
        return labels;
    }

    /**
     * BFS from the start node up to a given radius. Returns a map of each reachable node to its distance from the start.
     */
    private static Map<Node, Integer> bfsDistances(Graph graph, Node start, int radius) {
        Map<Node, Integer> dist = new HashMap<>();
        Queue<Node> queue = new LinkedList<>();
        dist.put(start, 0);
        queue.add(start);

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            int d = dist.get(current);
            if (d >= radius) continue;
            for (Node neighbor : graph.getNeighbors(current)) {
                if (!dist.containsKey(neighbor)) {
                    dist.put(neighbor, d + 1);
                    queue.add(neighbor);
                }
            }
        }
        return dist;
    }
}
