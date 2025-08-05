package wfc;

import java.util.*;
import patterns.Pattern;

/**
 * Whenever you collapse one cell, call Expand.expand(...) to grow
 * the graph out by attaching ⌈degree/2⌉ new cells to it.
 */
public class Expand {

    /**
     * @param collapsedCell    the cell you just collapsed
     * @param centerNodeDegree the full degree (e.g. via
     *                         patterns.get(collapsedCell.getCollapsedPattern()).getCenterNodeDegree())
     * @param allPatternIds    the “full super-state” you seed every new cell with
     * @param uncollapsedCells your driver’s list of cells not yet collapsed
     * @param collapsedCells   your driver’s list of cells already collapsed
     * @param cellAdjacency    map from every Cell → its neighbors
     */
    public static void expand(Cell              collapsedCell,
                              int               centerNodeDegree,
                              Set<Integer>      allPatternIds,
                              List<Cell>        uncollapsedCells,
                              List<Cell>        collapsedCells,
                              Map<Cell, List<Cell>> cellAdjacency)
    {
        if (!collapsedCell.isCollapsed()) {
            throw new IllegalArgumentException("Can only expand a collapsed cell");
        }

        // compute how many new spokes to add = ceil(degree/2.0)
        int toAdd = (int) Math.ceil(centerNodeDegree / 2.0);
        List<Cell> neighbors = cellAdjacency.get(collapsedCell);
        if (neighbors == null) {
            // if for some reason no entry exists yet
            neighbors = new ArrayList<>();
            cellAdjacency.put(collapsedCell, neighbors);
        }

        for (int i = 0; i < toAdd; i++) {
            // 1) make a fresh super-state cell
            Cell child = new Cell(allPatternIds);

            // 2) register it in your pools
            uncollapsedCells.add(child);
            // (you probably don’t add to collapsedCells—you only add there once it collapses)

            // 3) init its adjacency list
            cellAdjacency.put(child, new ArrayList<>());

            // 4) link it with the parent
            neighbors.add(child);
            cellAdjacency.get(child).add(collapsedCell);
        }
    }
}
