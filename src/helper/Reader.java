package helper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;

/**
 * Loads graph structure and node labels from text files into a Graph instance.
 *
 * This class provides static methods to read edge lists and label lists,
 * stripping comments and ignoring empty or malformed lines.
 */
public class Reader {

    /**
     * Reads edges and labels from the given file paths and returns the populated Graph.
     *
     * If edgesPath is non-null, loads edges first. If labelsPath is non-null, loads labels next.
     *
     * @param  edgesPath   filesystem path to the edge-list file (u v per line), or null to skip
     * @param  labelsPath  filesystem path to the label-list file (id label per line), or null to skip
     * @return             a Graph containing all nodes, edges, and labels read
     * @throws IOException if an I/O error occurs reading either file
     */
    public static Graph load(Path edgesPath, Path labelsPath) throws IOException {
        Graph g = new Graph();
        if (edgesPath != null) {
            readEdges(edgesPath, g);
        }
        if (labelsPath != null) {
            readLabels(labelsPath, g);
        }
        return g;
    }

    /**
     * Reads undirected edges from the specified file and adds them to the graph.
     *
     * Expects each non-comment, non-empty line to contain two integer IDs separated by whitespace.
     * Lines with fewer than two tokens are ignored.
     *
     * @param  path  filesystem path to the edge-list file
     * @param  g     the Graph to populate with edges
     * @throws IOException if an I/O error occurs while reading the file
     */
    public static void readEdges(Path path, Graph g) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = stripComments(line).trim();
                if (line.isEmpty()) continue;

                String[] p = line.split("\\s+");
                if (p.length < 2) continue;  // skip malformed lines

                int u = Integer.parseInt(p[0]);
                int v = Integer.parseInt(p[1]);
                g.addEdge(u, v);
            }
        }
    }

    /**
     * Reads node labels from the specified file and assigns them in the graph.
     *
     * Expects each non-comment, non-empty line to contain two integers: node ID and label.
     * Lines with fewer than two tokens are ignored.
     *
     * @param  path  filesystem path to the label-list file
     * @param  g     the Graph to populate with labels
     * @throws IOException if an I/O error occurs while reading the file
     */
    public static void readLabels(Path path, Graph g) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(path)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = stripComments(line).trim();
                if (line.isEmpty()) continue;

                String[] p = line.split("\\s+");
                if (p.length < 2) continue;  // skip malformed lines

                int id = Integer.parseInt(p[0]);
                int label = Integer.parseInt(p[1]);
                g.setLabel(id, label);
            }
        }
    }

    /**
     * Removes any text following a '#' or '//' comment marker.
     *
     * Finds the earliest occurrence of either marker and returns the substring
     * before it; otherwise returns the original string.
     *
     * @param  s  the input line potentially containing comments
     * @return    the line content up to (but not including) any comment marker
     */
    private static String stripComments(String s) {
        int iHash = s.indexOf('#');
        int iSl   = s.indexOf("//");
        int i;
        if (iHash >= 0 && iSl >= 0) {
            i = Math.min(iHash, iSl);
        } else if (iHash >= 0) {
            i = iHash;
        } else {
            i = iSl;
        }
        return (i >= 0) ? s.substring(0, i) : s;
    }
}
