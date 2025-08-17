# Wave Function Collapse for Synthetic Graph Generation

## Work in Progress: This project is under active development and features are subject to change. The README may not reflect the final implementation.


Synthetic graph generation via a Wave Function Collapse–inspired pipeline. Given one or more training graphs, this tool learns local ego-network patterns and uses them to construct new graphs that replicate the input’s structural statistics.

## Overview

1. **Pattern Extraction**

    * For each node in the training graph, extract its induced subgraph (ego‑network) out to a fixed radius (`RADIUS`).
    * Record node labels, adjacency, distance layers, and compute a canonical representation via two rounds of Weisfeiler–Lehman refinement.

2. **Compatibility Mapping**

    * Build multi‑radius compatibility tables: for each pattern at each hop distance, record the set of patterns it was observed adjacent to in training.
    * These tables drive both local constraint propagation and global edge wiring.

3. **Generation Pipeline**

    * **Initialization:**

        * Start with a single cell whose domain contains all training patterns.
    * **Growth Phase:**

        1. Collapse the frontier cell with lowest Shannon entropy.
        2. Expand a bounded number of new neighbor cells proportional to the collapsed-cell degree.
        3. Prune neighbor domains via compatibility (`ConstraintPropagator`), forcing any singleton domains to collapse immediately.
        4. Wire remaining “stubs” (desired edges) among settled cells according to compatibility (`Connect`).
        5. Repeat until settled cells reach `lowerCap × targetSize` or the frontier is empty.
    * **Cleanup Phase:**

        * Continue collapsing any remaining frontier cells and wiring all possible stubs.
        * Expansion allowance decays linearly from full cap at 100% progress down to zero at `upperCap × targetSize`.
        * Guarantees every cell is collapsed; leaves only minimal open stubs if size constraints apply.

4. **Export**

    * Write out final edge and label files for each generated graph.


## Repository Structure

```
helper/
  Graph.java
  Node.java
  Reader.java
  Exporter.java
  ExpansionCap.java

patterns/
  Pattern.java
  PatternExtractor.java
  PatternCompatibility.java

constructor/
  Expand.java
  Connect.java

wfc/
  Cell.java
  Entropy.java
  ConstraintPropagator.java
  wfc.java

```

## Configuration

Edit constants in `wfc.wfc`:

* `RADIUS`      — radius for ego-network extraction
* `lowerCap`    — fraction of target size to switch from growth to cleanup (e.g. 0.9)
* `upperCap`    — hard cap fraction beyond which no expansion occurs (e.g. 1.1)
* `sizeFactor`  — multiplier: `targetSize = sizeFactor × |trainingNodes|`

## Usage

1. **Prepare training data**
   Place pairs of files in `res/trainingGraphs/` named:
   `graphedges<index>`
   `graphlabels<index>`
   for each integer `<index>` (e.g. 1, 2, ...).

2. **Run the generator**

    * Auto-detects all `<index>` in `res/trainingGraphs/`
    * Resumes from last processed index (stored in `res/last_iter.txt`)
    * Outputs synthetic graphs to `res/generatedGraphs/`

3. **Inspect results**

    * Edge files: `res/generatedGraphs/graphedges<index>`
    * Label files: `res/generatedGraphs/graphlabels<index>`

## Requirements

* Java 11 or later
* No external dependencies




