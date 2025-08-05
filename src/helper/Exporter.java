package helper;

import wfc.Cell;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Writes out a generated graph into an edge‐list file and a label file.
 *
 * Provides two export APIs: one for integer‐indexed adjacency lists,
 * and one for direct Cell‐based graphs.
 */
public class Exporter {

    /**
     * Exports a graph when you already have integer‐indexed adjacency.
     *
     * Writes edges and labels to the given paths using zero‐based indices.
     *
     * @param  cells       list of all Cells in the final graph, in index order
     * @param  adjacency   adjacency list where adjacency.get(u) contains neighbors of u
     * @param  edgesPath   filesystem path to write the edge‐list (u v per line)
     * @param  labelsPath  filesystem path to write the label‐list (index label per line)
     * @throws IOException if an I/O error occurs while writing either file
     */
    public static void export(List<Cell> cells,
                              List<Set<Integer>> adjacency,
                              Path edgesPath,
                              Path labelsPath) throws IOException {
        writeEdges(adjacency, edgesPath);
        writeLabels(cells, labelsPath);
    }

    /**
     * Exports a graph directly from Cell objects and their adjacency map.
     *
     * Builds a stable integer ID for each Cell based on iteration order,
     * constructs an integer‐indexed adjacency list, and delegates to the old API.
     *
     * @param  cells         collection of all Cells in the final graph
     * @param  adjacencyMap  map from each Cell to its neighboring Cells
     * @param  edgesPath     filesystem path to write the edge‐list (u v per line)
     * @param  labelsPath    filesystem path to write the label‐list (index label per line)
     * @throws IOException   if an I/O error occurs while writing either file
     */
    public static void export(Collection<Cell> cells,
                              Map<Cell,List<Cell>> adjacencyMap,
                              Path edgesPath,
                              Path labelsPath) throws IOException {
        List<Cell> cellList = new ArrayList<>(cells);
        Map<Cell,Integer> idMap = new IdentityHashMap<>();
        for (int i = 0; i < cellList.size(); i++) {
            idMap.put(cellList.get(i), i);
        }

        List<Set<Integer>> adjList = new ArrayList<>(cellList.size());
        for (Cell c : cellList) {
            Set<Integer> nbrIds = new LinkedHashSet<>();
            for (Cell nbr : adjacencyMap.getOrDefault(c, Collections.emptyList())) {
                Integer nid = idMap.get(nbr);
                if (nid != null && !nid.equals(idMap.get(c))) {
                    nbrIds.add(nid);
                }
            }
            adjList.add(nbrIds);
        }

        export(cellList, adjList, edgesPath, labelsPath);
    }

    // ------------------- internal helpers -------------------

    /**
     * Writes the undirected edges from an integer‐indexed adjacency list.
     *
     * Only writes each edge once (u < v).
     *
     * @param  adjacency   adjacency list where adjacency.get(u) contains neighbors of u
     * @param  edgesPath   filesystem path to write the edge‐list
     * @throws IOException if an I/O error occurs while writing the file
     */
    private static void writeEdges(List<Set<Integer>> adjacency,
                                   Path edgesPath) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(edgesPath)) {
            for (int u = 0; u < adjacency.size(); u++) {
                for (int v : adjacency.get(u)) {
                    if (u < v) {
                        w.write(u + " " + v);
                        w.newLine();
                    }
                }
            }
        }
    }

    /**
     * Writes node labels for each Cell index.
     *
     * Validates that each Cell is collapsed before writing;
     * throws IllegalStateException if any Cell remains uncollapsed.
     *
     * @param  cells       list of Cells in index order
     * @param  labelsPath  filesystem path to write the label‐list
     * @throws IOException              if an I/O error occurs while writing the file
     * @throws IllegalStateException    if any Cell is not fully collapsed
     */
    private static void writeLabels(List<Cell> cells,
                                    Path labelsPath) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(labelsPath)) {
            for (int i = 0; i < cells.size(); i++) {
                Cell c = cells.get(i);
                if (!c.isCollapsed()) {
                    throw new IllegalStateException(
                            "Cannot export: cell " + i + " is still uncollapsed"
                    );
                }
                w.write(i + " " + c.getCenterLabel());
                w.newLine();
            }
        }
    }
}
