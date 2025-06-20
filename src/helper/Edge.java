package helper;

public class Edge {
    private final Node nodeA;
    private final Node nodeB;

    public Edge(Node nodeA, Node nodeB) {
        if (nodeA == null || nodeB == null) {
            throw new IllegalArgumentException("Edge nodes cannot be null");
        }
        if (nodeA == nodeB) {
            throw new IllegalArgumentException("An edge must connect two distinct nodes");
        }
        this.nodeA = nodeA;
        this.nodeB = nodeB;
    }

    public Node getNodeA() {
        return nodeA;
    }

    public Node getNodeB() {
        return nodeB;
    }

    /**
     * Given one endpoint of this edge, returns the other endpoint.
     */
    public Node getOtherNode(Node node) {
        if (node == null) {
            throw new IllegalArgumentException("Node cannot be null");
        }
        if (node.equals(nodeA)) {
            return nodeB;
        }
        if (node.equals(nodeB)) {
            return nodeA;
        }
        throw new IllegalArgumentException("The given node is not an endpoint of this edge");
    }

    @Override
    public String toString() {
        return "Edge{" + nodeA.getId() + " <-> " + nodeB.getId() + "}";
    }
}
