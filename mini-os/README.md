# Mini OS — Operating System Simulator (UMak OS Lab)

An interactive, pixel-art **Operating System Simulator** built in **Java Swing** covering all 8 required OS modules with dynamic user input, Excel-style data entry, input validation, algorithm comparisons, and visual charts/diagrams.

---

## Team Members
| Role | Member Name | Assigned Modules |
|---|---|---|
| Member 1 | *(Add Name)* | CPU Scheduling, Paging Lab, Segmentation |
| Member 2 | *(Add Name)* | Page Replacement, Disk Scheduling, Multiprogramming |
| Member 3 | *(Add Name)* | Banker's Algorithm, Fragmentation, Desktop Shell & Theme |

---

## Setup & Run Instructions

### Option 1: VS Code (Recommended for Development)
1. Open the `mini-os` folder in **VS Code** with **Extension Pack for Java**.
2. Run `src/app/Main.java` (always run from the `mini-os` project root so `assets/` and `fonts/` resolve).

### Option 2: Runnable JAR (`MiniOS.jar`)
From inside `mini-os/`:
```powershell
# Build MiniOS.jar (compiles targeting Java 25)
.\build-jar.ps1

# Run the packaged application
java -jar MiniOS.jar
```
*Requirements: Java 25+ (JDK/JRE) with `assets/` and `fonts/` in the working directory.*

---

## Keyboard Shortcuts & QoL Features
* **`Ctrl + 1..8`** — Switch directly between all 8 OS modules
* **`Ctrl + Enter`** — Run simulation in any module
* **`Ctrl + D`** — Toggle Light / Dark arcade theme
* **`Ctrl + +` / `Ctrl + -` / `Ctrl + 0`** — Zoom in / out (`85%`–`130%`) / reset to `100%`
* **`F11`** — Maximize / Restore OS window
* **Excel-Style Input Tables** (CPU, Paging, Segmentation, Multiprogramming):
  * Single-click any cell to type immediately
  * **`Ctrl + V`** directly on a table pastes rows/columns from Excel or Notepad
  * Click **`×`** on any row to remove that specific row, or **`Shift/Ctrl + Click`** multiple rows + **`Remove selected`** (or **`Delete`**)
* **Export & Reporting**: **`SAVE PNG`** exports the result card as an image; **`COPY OUTPUT`** copies a formatted text summary to the clipboard.

---

## Modules, Algorithms & Sample Verification

### 1. CPU Scheduling (`src/modules/cpu/CpuModule.java`)
* **Algorithms**: FIFO (FCFS), SJF, SRT, Priority (Non-preemptive & Preemptive, lower number = higher priority), Round Robin (custom quantum).
* **Formulas**: `TAT = CT - AT`, `WT = TAT - RT`, `AWT = avg(WT)`, `ATAT = avg(TAT)`.
* **Outputs**: Job table (`J#, AT, RT, ST, FT, WT, CT, TAT`), Gantt chart, and **Compare All 6 Algorithms** table.
* **Sample Verification**: 7 jobs (`AT: 0,5,10,15,20,25,30`, `RT: 30,15,25,10,20,5,15`, `q=10`) → FIFO `AWT=46.43, ATAT=63.57`; SJF `33.57 / 50.71`; SRT `27.86 / 45.00`; RR `57.86 / 75.00`.

### 2. Banker's Algorithm (`src/modules/banker/BankerModule.java`)
* **Dynamic Input**: Flexible number of processes and resource types (`Available`, `Allocation`, `Maximum` grids).
* **Formula**: `Need = Maximum - Allocation`.
* **Outputs**: `Need` matrix, step-by-step `AVAILABLE` ladder, `SAFE` / `NOT SAFE` verdict, and Safe Sequence.
* **Sample Verification**: 5 processes × 4 resources → `SAFE`, Safe Sequence: `P3, P4, P5, P1, P2`.

### 3. Paging Lab (`src/modules/paging/PagingModule.java`)
* **Part 1 (Page Table Size)**: `Page Table Entries = Address Space ÷ Page Size` (e.g. `1 GB ÷ 4 KB = 262,144 entries`).
* **Part 2 (Address Translation)**: Dynamic page-to-frame table (`-` = not in memory), `page # = logical ÷ page size`, `offset = logical mod page size`, `physical = frame × page size + offset`, plus page-fault and out-of-range traps.
* **Sample Verification**: `page size = 4`, frames `5 6 1 2`, `logical = 13` → `page 3, offset 1 → frame 2 → physical 9`.

### 4. Page Replacement (`src/modules/replacement/ReplacementModule.java`)
* **Algorithms**: FIFO, LRU, Optimal.
* **Outputs**: Step-by-step frame grid, `#FAULT`, `#HIT`, hit/fault rates, and **Compare All Algorithms** summary.
* **Sample Verification**: FIFO, `3` frames, ref `7 0 1 2 0 3 0 4 2 3 0 3 2` → `10 faults, 3 hits`.

### 5. Segmentation (`src/modules/segmentation/SegmentationModule.java`)
* **Dynamic Input**: Variable-length segment table (`Limit`, `Base`), segment number, and offset.
* **Formula**: Valid if `0 <= Offset < Limit` → `Physical = Base + Offset`; otherwise out-of-range trap.
* **Sample Verification**: `1000,1400; 400,6300; 400,4300; 1100,3200; 1000,4700` → Seg `2`, Offset `53` = `4353`; Seg `1`, Offset `500` = `Invalid`.

### 6. Fragmentation (`src/modules/fragmentation/FragmentationModule.java`)
* **Policies**: First Fit, Next Fit, Best Fit, Worst Fit + **Compare All Policies** table.
* **Outputs**: Scaled memory map, per-process placement (or `not allocated`), total free space, and external fragmentation breakdown.
* **Sample Verification**: Blocks `100 500 200 300 600`, Processes `212 417 112 426` (First Fit) → `959 KB free`, `P3 not allocated`.

### 7. Disk Scheduling (`src/modules/disk/DiskModule.java`)
* **Algorithms**: FCFS, SSTF, SCAN, C-SCAN, LOOK, C-LOOK.
* **Outputs**: Request service order, total head movement (cylinders), average seek, seek-path graph, and **Compare All 6 Algorithms** table.
* **Sample Verification**: Head `53`, Max `199`, Queue `98 183 37 122 14 124 65 67` → SSTF `236 cyl`, FCFS `640 cyl`.

### 8. Multiprogramming (`src/modules/multiprogramming/MultiprogrammingModule.java`)
* **Dynamic Input**: Variable number of jobs (`AT`, `BT` in decimal or `HH:mm`), `% I/O Wait`, and `Start Time`.
* **Formulas**: `%CPU/J = (1 - p^NJ) / NJ`, `HW/J = ET × (%CPU/J)`.
* **Outputs**: Timeline intervals, per-job completion, CPU utilization, and system summary.

---

## Dynamic Input & Validation Coverage
* **No Fixed Limits**: All modules accept variable-length inputs (including large datasets of 15–25+ entries via `+1`, `+5`, `[count] Set`, `Paste / Bulk`, or `Ctrl+V`).
* **Small & Large Dataset Testing**: Each module includes both small and large test cases (plus `Invalid input test` / `Random` / `History` / `Undo` / `Clear`).
* **Graceful Error Handling**: Blank fields, non-numeric text, negative values where disallowed, `Allocation > Maximum`, duplicate frames, and out-of-range offsets highlight invalid cells in pink/red and display a clear `Notice` without crashing.

---

## Suggested 10-Minute Presentation Flow
1. **0:00–1:00 (Intro)**: Project goals, Java Swing desktop shell, 8 OS modules.
2. **1:00–3:00 (GUI & UX)**: Boot splash, desktop/taskbar, Light/Dark (`Ctrl+D`), Zoom (`A-/A+`), Excel-style tables (`Ctrl+V` / `Remove selected`), `SAVE PNG`.
3. **3:00–7:00 (Live Demo)**: Walk through all 8 modules using small + large datasets and trigger 1–2 invalid input checks to show graceful validation.
4. **7:00–9:00 (Algorithm Comparison)**: Expand **Compare All Algorithms** in CPU, Page Replacement, Disk Scheduling, and Fragmentation to justify which algorithm performed best and why.
5. **9:00–10:00 (Q&A)**: Use `?` Quick Help for formula reference if needed.
