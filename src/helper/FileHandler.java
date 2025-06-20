package helper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class FileHandler {

    public static Graph loadGraphFromFiles(String labelFile, String edgeFile) throws IOException {
        Graph graph = new Graph();
        loadNodesFromFile(graph, labelFile);
        loadEdgesFromFile(graph, edgeFile);
        return graph;
    }

    private static void loadNodesFromFile(Graph graph, String labelFile) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(labelFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                String id = parts[0];
                String label = (parts.length > 1 ? parts[1] : "");
                Node node = graph.getNodeById(id);
                if (node == null) {
                    graph.addNode(id, label);
                } else {
                    // If node exists with no label, update the label if available
                    if ((node.getLabel() == null || node.getLabel().isEmpty()) && !label.isEmpty()) {
                        node.setLabel(label);
                    }
                }
            }
        }
    }

    private static void loadEdgesFromFile(Graph graph, String edgeFile) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(edgeFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                if (parts.length < 2) continue;
                String u = parts[0], v = parts[1];
                if (u.equals(v)) continue;  // skip self-loops
                if (graph.getNodeById(u) == null) {
                    graph.addNode(u, "");
                }
                if (graph.getNodeById(v) == null) {
                    graph.addNode(v, "");
                }
                graph.addEdge(u, v);
            }
        }
    }

    public static void saveGraphToFiles(Graph graph, String labelFile, String edgeFile) throws IOException {
        try (FileWriter fw = new FileWriter(labelFile)) {
            for (Node node : graph.getAllNodes()) {
                fw.write(node.getId() + " " + node.getLabel() + "\n");
            }
        }
        try (FileWriter fw = new FileWriter(edgeFile)) {
            for (Edge edge : graph.getAllEdges()) {
                fw.write(edge.getNodeA().getId() + " " + edge.getNodeB().getId() + "\n");
            }
        }
    }
}
