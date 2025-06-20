package helper;

import java.util.*;

public class Graph {
    private final Map<String, Node> nodes = new HashMap<>();
    private final List<Edge> edges = new ArrayList<>();

    /**
     * Adds a new node to the graph.
     * @throws IllegalArgumentException if a node with the given ID already exists.
     */
    public Node addNode(String id, String label) {
        if (nodes.containsKey(id)) {
            throw new IllegalArgumentException("Node ID already exists: " + id);
        }
        Node node = new Node(id, label);
        nodes.put(id, node);
        return node;
    }

    public Node getNodeById(String id) {
        return nodes.get(id);
    }

    public Collection<Node> getAllNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    public List<Edge> getAllEdges() {
        return Collections.unmodifiableList(edges);
    }

    /**
     * Adds an edge between the nodes with the given IDs.
     * If an edge between the two nodes already exists, returns the existing edge.
     * @throws IllegalArgumentException if either node ID is not present in the graph.
     */
    public Edge addEdge(String nodeIdA, String nodeIdB) {
        Node nodeA = getNodeById(nodeIdA);
        Node nodeB = getNodeById(nodeIdB);
        if (nodeA == null || nodeB == null) {
            throw new IllegalArgumentException(
                    "Both nodes must exist in the graph: " + nodeIdA + ", " + nodeIdB
            );
        }
        // Avoid duplicate edges (treat undirected edges as identical regardless of order)
        for (Edge e : edges) {
            if ((e.getNodeA().equals(nodeA) && e.getNodeB().equals(nodeB)) ||
                    (e.getNodeA().equals(nodeB) && e.getNodeB().equals(nodeA))) {
                return e;  // Edge already exists, return it
            }
        }
        Edge edge = new Edge(nodeA, nodeB);
        edges.add(edge);
        nodeA.addNeighbor(nodeB);
        nodeB.addNeighbor(nodeA);
        nodeA.addEdge(edge);
        nodeB.addEdge(edge);
        return edge;
    }

    public Set<Node> getNeighbors(Node node) {
        if (node == null || !nodes.containsKey(node.getId())) {
            throw new IllegalArgumentException("Node is not in this graph.");
        }
        return node.getNeighbors();
    }

    /**
     * Utility: returns a canonical label pair string (labels sorted lexicographically).
     */
    public static String sortedLabelPair(String label1, String label2) {
        return (label1.compareTo(label2) <= 0)
                ? label1 + "|" + label2
                : label2 + "|" + label1;
    }
}
