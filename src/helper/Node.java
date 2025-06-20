package helper;

import java.util.*;

public class Node {
    private final String id;
    private String label;
    private Set<Node> neighbors = new HashSet<>();

    // Track which edges connect this node to neighbors
    private final Set<Edge> edges = new HashSet<>();

    public Node(String id, String label) {
        if (id == null) {
            throw new IllegalArgumentException("Node ID cannot be null");
        }
        this.id = id;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Set<Node> getNeighbors() {
        return Collections.unmodifiableSet(neighbors);
    }

    public void addNeighbor(Node neighbor) {
        if (neighbor == null) {
            throw new IllegalArgumentException("Neighbor cannot be null");
        }
        neighbors.add(neighbor);
    }

    public void addEdge(Edge edge) {
        if (edge == null) {
            throw new IllegalArgumentException("Cannot add a null edge");
        }
        edges.add(edge);
    }

    @Override
    public String toString() {
        return "Node{id='" + id + "', label='" + label + "'}";
    }
}
