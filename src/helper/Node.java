package helper;

import java.util.*;

/**
 * Represents a Node in an undirected graph.
 * Each Node has a unique identifier, an integer label, and a list of adjacent nodes.
 * Neighbor relationships (undirected edges) are maintained by the Graph class.
 */
public class Node {
    /** Immutable unique identifier for this node. */
    private final int id;

    /** Mutable label for marking, coloring, or categorization. */
    private int label;

    /** Adjacency list of directly connected neighbor nodes. */
    private final List<Node> neighbors = new ArrayList<>();

    /**
     * Constructs a new Node with the given unique identifier.
     *
     * @param id the unique ID of this node
     */
    public Node(int id) {
        this.id = id;
    }

    /**
     * Returns this node’s unique identifier.
     *
     * @return the node ID
     */
    public int getId() {
        return id;
    }

    /**
     * Returns the current label of this node.
     *
     * @return the node’s label
     */
    public int getLabel() {
        return label;
    }

    /**
     * Updates the label of this node.
     *
     * @param label an integer value used for marking or categorization
     */
    public void setLabel(int label) {
        this.label = label;
    }

    /**
     * Adds a neighbor to this node’s adjacency list.
     * Package-private to prevent external misuse; Graph ensures undirected symmetry.
     *
     * @param neighbor the node to connect as a neighbor
     */
    void addNeighbor(Node neighbor) {
        neighbors.add(neighbor);
    }

    /**
     * Returns an unmodifiable view of this node’s neighbors.
     *
     * @return an unmodifiable list of adjacent nodes
     */
    public List<Node> getNeighbors() {
        return Collections.unmodifiableList(neighbors);
    }
}
