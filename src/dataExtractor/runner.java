package dataExtractor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class runner {

    // A simple Edge class to store undirected edges.
    public static class Edge {
        public int u;
        public int v;
        Edge(int u, int v) {
            this.u = u;
            this.v = v;
        }
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Edge)) return false;
            Edge edge = (Edge) o;
            // For an undirected edge, (u,v) is equivalent to (v,u).
            return (u == edge.u && v == edge.v) || (u == edge.v && v == edge.u);
        }
        @Override
        public int hashCode() {
            int min = Math.min(u, v);
            int max = Math.max(u, v);
            return Objects.hash(min, max);
        }
    }

    // A simple Graph class holding nodes, edges, and labels.
    public static class Graph {
        int numNodes;
        public List<Edge> edges;
        public Map<Integer, Integer> labels; // Every node is labeled.

        Graph(int numNodes, List<Edge> edges, Map<Integer, Integer> labels) {
            this.numNodes = numNodes;
            this.edges = edges;
            this.labels = labels;
        }
    }

    // Creates the output directory if it doesn't exist.
    public static void createDirectory(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Generates a synthetic graph.
     * - First, it creates a spanning tree to guarantee connectivity.
     * - Then, for every unordered pair of nodes not in the spanning tree,
     *   it adds an edge with probability extraEdgeProbability.
     * - Every node is assigned a label randomly (from 0 to numLabelTypes - 1).
     *
     * @param numNodes           Total number of nodes.
     * @param extraEdgeProbability Probability to add an extra edge between any pair of nodes.
     * @param numLabelTypes      Number of distinct label types.
     * @return A synthetic Graph.
     */
    public static Graph generateGraph(int numNodes, double extraEdgeProbability, int numLabelTypes) {
        List<Integer> nodes = new ArrayList<>();
        for (int i = 0; i < numNodes; i++) {
            nodes.add(i);
        }
        Random rand = new Random();
        // Use a Set to avoid duplicate edges.
        Set<Edge> edgeSet = new HashSet<>();

        // Create a spanning tree for connectivity.
        Collections.shuffle(nodes, rand);
        for (int i = 1; i < numNodes; i++) {
            int u = nodes.get(i - 1);
            int v = nodes.get(i);
            edgeSet.add(new Edge(u, v));
        }

        // Iterate over all unordered pairs and add extra edges based on probability.
        for (int i = 0; i < numNodes; i++) {
            for (int j = i + 1; j < numNodes; j++) {
                Edge edge = new Edge(i, j);
                if (edgeSet.contains(edge)) continue;
                if (rand.nextDouble() < extraEdgeProbability) {
                    edgeSet.add(edge);
                }
            }
        }

        List<Edge> edges = new ArrayList<>(edgeSet);

        // Assign a label to every node.
        Map<Integer, Integer> labels = new HashMap<>();
        for (int i = 0; i < numNodes; i++) {
            labels.put(i, rand.nextInt(numLabelTypes));
        }

        return new Graph(numNodes, edges, labels);
    }

    // Writes edges to a file (each line: u TAB v).
    public static void writeEdges(List<Edge> edges, String filePath) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));
        for (Edge edge : edges) {
            writer.write(edge.u + "\t" + edge.v);
            writer.newLine();
        }
        writer.close();
    }

    // Writes labels to a file (each line: node TAB label).
    public static void writeLabels(Map<Integer, Integer> labels, String filePath) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));
        for (Map.Entry<Integer, Integer> entry : labels.entrySet()) {
            writer.write(entry.getKey() + "\t" + entry.getValue());
            writer.newLine();
        }
        writer.close();
    }

    /**
     * Generates the synthetic graph with the provided parameters and writes its edges and labels to files.
     *
     * @param numNodes            Total number of nodes in the graph.
     * @param extraEdgeProbability Probability to add an extra edge between any two nodes.
     * @param numLabelTypes       Number of distinct label types.
     */
    public static void exportGraph(int numNodes, double extraEdgeProbability, int numLabelTypes) {
        // Set up directory.
        String baseDir = System.getProperty("user.dir") + File.separator + "res" + File.separator + "syntheticdata/SyntheticGraph";
        createDirectory(baseDir);

        // Generate the synthetic graph.
        Graph graph = generateGraph(numNodes, extraEdgeProbability, numLabelTypes);

        // Write graph data to files.
        String edgeFile = baseDir + File.separator + "graph_edges.txt";
        String labelFile = baseDir + File.separator + "graph_labels.txt";
        try {
            writeEdges(graph.edges, edgeFile);
            writeLabels(graph.labels, labelFile);
            System.out.println("Exported graph with " + graph.numNodes + " nodes and " + graph.edges.size() + " edges.");
            System.out.println("Edges written to: " + edgeFile);
            System.out.println("Labels written to: " + labelFile);
        } catch (IOException e) {
            System.err.println("Error writing files: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        // Example configuration:
        // Total nodes: 50, extra edge probability: 0.1, number of label types: 3.
        exportGraph(180, 0.015, 4);    }
}
