# Wavefunction Collapse Graph Generator

This project applies a **wavefunction collapse (WFC)** algorithm to synthesize novel graphs from an existing training graph. By mining local ego-network patterns at a chosen radius and enforcing compatibility constraints, it incrementally constructs a new graph that mirrors structural features of the input.

## Key Features
- **Pattern Extraction**: Learns local subgraphs (radius-based ego networks).
- **Wavefunction Collapse**: Iteratively collapses cells based on frequency and overlap constraints.
- **Constraint Propagation**: Multi-radius BFS to prune incompatible patterns in neighboring cells.
- **Graph Augmentation**: Supports merges, expansions, and connections to satisfy node-degree targets.


## Output
- The program saves the **augmented** graph in two files (e.g., `augmented_labels.txt`, `augmented_edges.txt`).
- It also computes quality metrics (degree distribution MMD, clustering MMD, etc.) and may display visualizations of patterns or overlaps if enabled.

## Customization
- **Radius**: Change `wfc.wfc.RADIUS` to control the size of mined ego networks.
- **Iterations**: Adjust `MAX_ITERATIONS` for more/less generation steps.
