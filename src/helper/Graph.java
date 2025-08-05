package helper;

import java.util.*;

/**
 * Represents an undirected graph with lazy node creation, edge management, and labeling.
 *
 * This class maintains a mapping from integer IDs to Node instances and provides
 * methods to add edges, assign labels, and retrieve nodes.
 */
public class Graph {
    /** Maps each node ID to its corresponding Node instance. */
    private final Map<Integer, Node> nodes = new HashMap<>();

    /**
     * Retrieves or creates a Node with the specified ID.
     *
     * @param  id    unique identifier of the node
     * @return       the existing or newly created Node
     */
    public Node getOrCreateNode(int id) {
        return nodes.computeIfAbsent(id, Node::new);
    }

    /**
     * Adds an undirected edge between two nodes identified by their IDs.
     * If either node does not exist, it is created.
     *
     * @param  uId    ID of the first node endpoint
     * @param  vId    ID of the second node endpoint
     */
    public void addEdge(int uId, int vId) {
        Node u = getOrCreateNode(uId);
        Node v = getOrCreateNode(vId);
        u.addNeighbor(v);
        v.addNeighbor(u);
    }

    /**
     * Assigns or updates the integer label of the node with the given ID.
     * If the node does not exist yet, it is created.
     *
     * @param  id      unique identifier of the node
     * @param  label   integer label used for marking or categorization
     */
    public void setLabel(int id, int label) {
        getOrCreateNode(id).setLabel(label);
    }

    /**
     * Returns the Node corresponding to the specified ID, or null if none exists.
     *
     * @param  id    unique identifier to look up
     * @return       the Node with that ID, or null if not found
     */
    public Node getNode(int id) {
        return nodes.get(id);
    }

    /**
     * Provides a read-only collection of all nodes currently in the graph.
     *
     * @return       an unmodifiable view of all registered Nodes
     */
    public Collection<Node> getAllNodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }
}
