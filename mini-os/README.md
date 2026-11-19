# Hyperpuff – Mini Operating System

**Hyperpuff – Mini Operating System** is an interactive, pixel-art **Operating System Simulator** built in **Java Swing** that implements all 8 core Operating System modules with dynamic user input (no fixed item limits), Excel-style data entry, real-time input validation, algorithm comparison tables, and visual charts/diagrams.

---

## Team Members
| Role | Member Name | Assigned Modules |
|---|---|---|
| Member 1 | **Chloe Bautista** | CPU Scheduling, Paging, Segmentation |
| Member 2 | **Ysamarie Siblero** | Page Replacement, Disk Scheduling, Multiprogramming |
| Member 3 | **Charmee Cantones** | Banker's Algorithm, Fragmentation, Desktop Shell & Theme |

---

## Setup & Run Instructions

### Option 1: VS Code (Development)
1. Open the `mini-os` folder in **VS Code** with **Extension Pack for Java**.
2. Run `src/app/Main.java` (always run from the `mini-os` directory so `assets/` and `fonts/` load properly).

### Option 2: Command Line
From inside `mini-os/`:
```powershell
# Compile (targets Java 21+ for compatibility)
$files = Get-ChildItem -Recurse -Filter *.java src | Select-Object -ExpandProperty FullName
javac --release 21 -d bin $files

# Run Hyperpuff
java -cp bin app.Main
```

### Option 3: Runnable JAR (`Hyperpuff.jar`)
From inside `mini-os/`:
```powershell
java -jar Hyperpuff.jar
```
*Requirements: Java 21+ (JDK/JRE) with `assets/` and `fonts/` in the working directory.*

---

## Key Features & QoL Shortcuts
* **`Ctrl + 1..8`** — Jump directly between all 8 OS modules
* **`Ctrl + Enter`** — Run simulation from anywhere in the active module
* **`Ctrl + Z` / `Ctrl + Y`** — Undo / Redo input changes
* **`Ctrl + D`** — Toggle Light / Dark arcade theme
* **`Ctrl + +` / `Ctrl + -` / `Ctrl + 0`** — Zoom in / out (`85%`–`130%`) / reset to `100%`
* **`F11`** — Maximize / Restore window
* **Fast Data Entry:**
  * **Excel-Style Tables** (CPU, Paging, Segmentation, Multiprogramming): single-click to edit, **`Ctrl + V`** to paste rows/columns from Excel/Notepad, **`Delete`** to clear selected cells, and **`×`** or **`Remove selected`** to delete rows.
  * **Banker's Algorithm Grids**: arrow-key navigation (`↑` `↓` `←` `→`), **`Delete`** to clear a cell, **`Shift + Delete`** to clear an entire process row, and real-time red border highlights when `Allocation > Maximum` or non-numeric text is entered.
* **Presentation & Export Tools:**
  * **`SAVE PNG`** — Exports the simulation result card as a crisp PNG image via the native Windows Save dialog.
  * **`COPY OUTPUT`** — Copies a formatted text summary straight to the clipboard.
  * **Smooth Auto-Scroll** — Automatically scrolls smoothly to `SIMULATION OUTPUT` on Run and back to top via `BACK TO TOP`.
  * **On-Theme Exit Guard** — Prompts with a themed confirmation dialog if unsaved inputs are present.

---

## Modules, Algorithms & Sample Verification

### 1. CPU Scheduling (`src/modules/cpu/CpuModule.java`)
* **Algorithms**: FIFO (FCFS), SJF, SRT, Priority (Non-preemptive & Preemptive; lower number = higher priority), Round Robin (custom quantum).
* **Formulas**: `TAT = CT - AT`, `WT = TAT - RT`, `AWT = avg(WT)`, `ATAT = avg(TAT)`.
* **Outputs**: Full job table (`J#, AT, RT, ST, FT, WT, CT, TAT`), Gantt chart, and **Compare All 6 Algorithms** table.
* **Sample Verification**: 7 jobs (`AT: 0,5,10,15,20,25,30`, `RT: 30,15,25,10,20,5,15`, `q=10`) → FIFO `AWT=46.43, ATAT=63.57`; SJF `33.57 / 50.71`; SRT `27.86 / 45.00`; RR `57.86 / 75.00`.

### 2. Banker's Algorithm (`src/modules/banker/BankerModule.java`)
* **Dynamic Input**: Flexible process and resource-type counts (`Available`, `Allocation`, `Maximum` grids).
* **Formula**: `Need = Maximum - Allocation`.
* **Outputs**: `Need` matrix, step-by-step `AVAILABLE` ladder, `SAFE` / `NOT SAFE` verdict, and Safe Sequence.
* **Sample Verification**: 5 processes × 4 resources (`Available: 3 2 2 1`) → `SAFE`, Safe Sequence: `P3, P4, P5, P1, P2`.

### 3. Paging (`src/modules/paging/PagingModule.java`)
* **Part 1 (Page Table Size)**: `Page Table Entries = Address Space ÷ Page Size` (e.g. `1 GB ÷ 4 KB = 262,144 entries`).
* **Part 2 (Address Translation)**: Dynamic page-to-frame table (`-` = not in memory), `page # = logical ÷ page size`, `offset = logical mod page size`, `physical = frame × page size + offset`, with page-fault and out-of-range detection.
* **Sample Verification**: `page size = 4`, frames `5 6 1 2`, `logical = 13` → `page 3, offset 1 → frame 2 → physical 9`.

### 4. Page Replacement (`src/modules/replacement/ReplacementModule.java`)
* **Algorithms**: FIFO, LRU, Optimal.
* **Outputs**: Step-by-step frame grid, `#FAULT`, `#HIT`, hit/fault percentages, and **Compare All Algorithms** summary.
* **Sample Verification**: FIFO, `3` frames, ref `7 0 1 2 0 3 0 4 2 3 0 3 2` → `10 faults, 3 hits`.

### 5. Segmentation (`src/modules/segmentation/SegmentationModule.java`)
* **Dynamic Input**: Variable-length segment table (`Limit`, `Base`), segment number, and offset.
* **Formula**: Valid if `0 <= Offset < Limit` → `Physical = Base + Offset`; otherwise out-of-range trap.
* **Sample Verification**: `1000,1400; 400,6300; 400,4300; 1100,3200; 1000,4700` → Seg `2`, Offset `53` = `4353`; Seg `1`, Offset `500` = `Invalid`.

### 6. Fragmentation (`src/modules/fragmentation/FragmentationModule.java`)
* **Policies**: First Fit, Next Fit, Best Fit, Worst Fit + **Compare All Policies** table.
* **Outputs**: Scaled memory map, per-process allocation (or `not allocated`), total free space, and internal/external fragmentation breakdown.
* **Sample Verification**: Blocks `100 500 200 300 600`, Processes `212 417 112 426` (First Fit) → `959 KB free`, `P3 not allocated`.

### 7. Disk Scheduling (`src/modules/disk/DiskModule.java`)
* **Algorithms**: FCFS, SSTF, SCAN, C-SCAN, LOOK, C-LOOK.
* **Outputs**: Service order, total head movement (cylinders), average seek, seek-path graph, and **Compare All 6 Algorithms** table.
* **Sample Verification**: Head `53`, Max `199`, Queue `98 183 37 122 14 124 65 67` → SSTF `236 cyl`, FCFS `640 cyl`.

### 8. Multiprogramming (`src/modules/multiprogramming/MultiprogrammingModule.java`)
* **Dynamic Input**: Variable number of jobs (`AT`, `BT` in decimal or `HH:mm`), `% I/O Wait`, and `Start Time`.
* **Formulas**: `%CPU/J = (1 - p^NJ) / NJ`, `HW/J = ET × (%CPU/J)`.
* **Outputs**: Timeline intervals, per-job completion times, CPU utilization, and system summary.

---

## Dynamic Input & Validation Coverage
* **No Fixed Item Limits**: Every module accepts dynamic user-defined input sizes (small datasets and large datasets of 15–25+ entries via `+1`, `+5`, `[count] Set`, `Paste / Bulk`, or `Ctrl + V`).
* **Graceful Error Handling**: Blank cells, non-numeric entries, disallowed negative values, `Allocation > Maximum`, duplicate frames, and out-of-range offsets highlight invalid fields without crashing and explain how to fix them.

---

## Suggested 10-Minute Presentation Flow
1. **0:00–1:00 — Intro**: Introduce **Hyperpuff**, objectives, Java Swing architecture, and all 8 OS modules.
2. **1:00–3:00 — GUI & UX**: Show the boot splash, desktop/taskbar, Light/Dark mode (`Ctrl + D`), Zoom (`A-`/`A+`), Excel-style tables (`Ctrl + V` / `Delete`), and `SAVE PNG`.
3. **3:00–7:00 — Live Demo**: Walk through all 8 modules using both small and large datasets, plus 1–2 `Invalid input test` cases to demonstrate graceful error handling.
4. **7:00–9:00 — Algorithm Comparison**: Open **Compare All Algorithms** in CPU Scheduling, Page Replacement, Disk Scheduling, and Fragmentation to justify which algorithm performed best and why.
5. **9:00–10:00 — Q&A**: Use the `?` Quick Help button for instant formula reference if needed.
