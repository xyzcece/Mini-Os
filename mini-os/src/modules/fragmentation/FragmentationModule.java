package modules.fragmentation;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import modules.common.FigmaModule;
import shared.Fonts;
import shared.Theme;

/**
 * FRAGMENTATION  —  OWNER: Member 3
 *   [x] A flexible number of memory blocks and processes, plus the policy dropdown
 *   [x] First Fit, Next Fit, Best Fit and Worst Fit (+ a Compare All Policies table)
 *   [x] Memory map drawn to scale, where each process went (or "not allocated"), free space
 *   [x] External fragmentation spelled out: the leftover gaps between blocks
 *   [x] Validation: blank, non-numeric and sizes of 0 or less
 * CHECK: blocks 100 500 200 300 600 / processes 212 417 112 426
 *        First Fit -> P0 and P2 share Block 1, P1 goes to Block 4, P3 does not fit,
 *                     959 KB free (100 + 176 + 200 + 300 + 183)
 *
 * Memory model: variable partitions. Each block is a hole; a process takes exactly its own
 * size out of a hole, so one block can end up holding several processes and whatever is left
 * of that hole stays available. Processes are numbered from P0 and blocks from Block 0.
 *
 * The page design (header, inputs, run button, result card) comes from FigmaModule.
 */
public class FragmentationModule extends FigmaModule {
    private static final long serialVersionUID = 1L;

    static final String[] POLICIES = {"First Fit", "Next Fit", "Best Fit", "Worst Fit"};

    private static final String SAMPLE_BLOCKS = "100 500 200 300 600";
    private static final String SAMPLE_PROCS  = "212 417 112 426";
    private static final String LARGE_BLOCKS =
            "120 480 95 640 220 310 760 150 400 540 280 900 60 350 700";
    private static final String LARGE_PROCS =
            "212 417 112 426 380 95 720 265 140 505 330 880 70 460 190 620 240";

    /** Corner radius of the policy popup card. */
    private static final int RADIUS = 14;

    /** Biggest size a block or process may be, so the totals stay sane. */
    private static final int MAX_SIZE = 1_000_000;

    private RoundField blocks, processes;
    private String policy = "First Fit";
    private boolean hasRun = false;

    private final transient Deque<String[]> undoStack = new ArrayDeque<>();
    private final transient Deque<String[]> redoStack = new ArrayDeque<>();
    private final transient List<HistoryItem> history = new ArrayList<>();
    private boolean restoring = false;

    private record HistoryItem(String label, String blocks, String procs, String policy) {}

    @Override public String title()    { return "Fragmentation"; }
    @Override public String subtitle() { return "Explore first, next, best, and worst fit allocation."; }
    @Override protected void onUndoPressed() {
        if (undoStack.isEmpty()) { showToast(this, "Nothing to undo"); return; }
        if (blocks != null) redoStack.push(new String[]{blocks.getText(), processes.getText()});
        String[] prev = undoStack.pop();
        setFields(prev[0], prev[1]);
        hasRun = false;
        markInputsDirty();
        showToast(this, "Undid · Ctrl+Y to redo");
    }

    @Override protected void onRedoPressed() {
        if (redoStack.isEmpty()) { showToast(this, "Nothing to redo"); return; }
        if (blocks != null) undoStack.push(new String[]{blocks.getText(), processes.getText()});
        String[] next = redoStack.pop();
        setFields(next[0], next[1]);
        hasRun = false;
        markInputsDirty();
        showToast(this, "Redid change");
    }

    @Override protected String crumb() { return "FRAGMENTATION"; }
    @Override protected String lead()  { return "Place each process in a memory block and see how much space is left."; }

    // ============================ INPUTS ============================

    @Override protected void buildInputs(JPanel p) {
        blocks    = new RoundField(blocks    == null ? "" : blocks.getText());
        processes = new RoundField(processes == null ? "" : processes.getText());
        blocks.addActionListener(e -> onRunPressed());
        processes.addActionListener(e -> onRunPressed());
        // The stock Dropdown opens an unthemed popup (its items are not opaque, so the
        // look-and-feel paints them white and ignores the theme). Keep its button face,
        // swap in a popup that follows the theme. Picking a policy keeps the results on
        // screen and re-runs with the new policy instead of clearing back to the prompt.
        Dropdown pick = new Dropdown(policy, POLICIES, a -> { /* replaced below */ });
        for (java.awt.event.ActionListener al : pick.getActionListeners()) pick.removeActionListener(al);
        pick.addActionListener(e -> showPolicyMenu(pick));
        p.add(columns(new double[]{1.6, 1.6, 1.2},
                labeled("Memory blocks (KB)", blocks, "sizes separated by spaces"),
                labeled("Process sizes (KB)", processes, "sizes separated by spaces"),
                labeled("Allocation policy", pick, null)), BorderLayout.CENTER);

        JPanel tools = new JPanel(new WrapLayout(FlowLayout.LEFT, 8, 6));
        tools.setOpaque(false);
        tools.setBorder(new EmptyBorder(10, 0, 0, 0));

        DashedPill sample = new DashedPill("Sample data");
        sample.addActionListener(e -> {
            pushUndo();
            setFields(SAMPLE_BLOCKS, SAMPLE_PROCS);
            hasRun = false;
            rerun();
            showToast(this, "Loaded 5 blocks / 4 processes - press RUN SIMULATION");
        });

        DashedPill large = new DashedPill("Large sample (15/17)");
        large.addActionListener(e -> {
            pushUndo();
            setFields(LARGE_BLOCKS, LARGE_PROCS);
            hasRun = false;
            rerun();
            showToast(this, "Loaded 15 blocks / 17 processes - press RUN SIMULATION");
        });

        DashedPill random = new DashedPill("Random");
        random.addActionListener(e -> {
            pushUndo();
            Random rng = new Random();
            setFields(randomSizes(rng, 5 + rng.nextInt(4), 80, 900),
                      randomSizes(rng, 5 + rng.nextInt(4), 60, 700));
            hasRun = false;
            rerun();
            showToast(this, "Generated random blocks and processes - press RUN SIMULATION");
        });

        DashedPill hist = new DashedPill("History");
        hist.addActionListener(e -> showHistoryMenu(hist));

        DashedPill undo = new DashedPill("Undo");
        undo.addActionListener(e -> {
            if (undoStack.isEmpty()) { showToast(this, "Nothing to undo"); return; }
            String[] prev = undoStack.pop();
            setFields(prev[0], prev[1]);
            hasRun = false;
            rerun();
            showToast(this, "Undid last change");
        });

        DashedPill clear = new DashedPill("Clear");
        clear.addActionListener(e -> {
            pushUndo();
            setFields("", "");
            hasRun = false;
            rerun();
            showToast(this, "Cleared · Ctrl+Z to undo");
        });

        JLabel badge = body(countLabel(), 11, Theme.pink());
        DocumentListener watch = new DocumentListener() {
            private void upd() { badge.setText(countLabel()); }
            @Override public void insertUpdate(DocumentEvent e) { upd(); }
            @Override public void removeUpdate(DocumentEvent e) { upd(); }
            @Override public void changedUpdate(DocumentEvent e) { upd(); }
        };
        blocks.getDocument().addDocumentListener(watch);
        processes.getDocument().addDocumentListener(watch);

        DashedPill bad = new DashedPill("Invalid input test");
        bad.addActionListener(e -> {
            pushUndo();
            setFields("100 abc 0 300", "212 -5");
            hasRun = true;
            rerun();
            showToast(this, "Loaded bad input - the result card shows what to fix");
        });
        tools.add(sample); tools.add(large); tools.add(random); tools.add(bad);
        tools.add(hist); tools.add(undo); tools.add(clear);
        tools.add(Box.createHorizontalStrut(6));
        tools.add(badge);
        p.add(tools, BorderLayout.SOUTH);
    }

    /**
     * Policy list in the Mini OS colours. Every item is opaque so the theme colours
     * actually show (otherwise the look-and-feel paints its own white background), and
     * the popup is nudged left when it would otherwise hang off the right of the window.
     */
    private void showPolicyMenu(JComponent anchor) {
        JPopupMenu menu = buildPolicyMenu();
        int w = Math.max(anchor.getWidth(), 160);
        menu.setPopupSize(w, menu.getPreferredSize().height);

        // keep the popup inside the window instead of letting it spill onto the desktop
        int dx = 0;
        JRootPane root = SwingUtilities.getRootPane(anchor);
        if (root != null) {
            int anchorX = SwingUtilities.convertPoint(anchor, 0, 0, root).x;
            int overflow = anchorX + w - root.getWidth() + 8;
            if (overflow > 0) dx = -overflow;
        }
        if (anchor.isShowing()) menu.show(anchor, dx, anchor.getHeight() - 1);
    }

    JPopupMenu buildPolicyMenu() {
        JPopupMenu menu = new JPopupMenu() {
            private static final long serialVersionUID = 1L;
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = smooth(g);
                int w = getWidth(), h = getHeight();
                g2.setColor(new Color(0, 0, 0, Theme.dark ? 60 : 24));          // soft drop shadow
                g2.fill(new RoundRectangle2D.Double(1, 3, w - 2.0, h - 3.0, RADIUS, RADIUS));
                RoundRectangle2D card = new RoundRectangle2D.Double(0.6, 0.6, w - 1.2, h - 4.0, RADIUS, RADIUS);
                g2.setColor(Theme.input());
                g2.fill(card);
                g2.setColor(Theme.inputLine());
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(card);
                g2.dispose();
            }
        };
        menu.setOpaque(false);                      // the rounded card above is the background
        menu.setBackground(Theme.input());          // only used if Swing has to go heavyweight
        menu.setBorder(new EmptyBorder(9, 0, 13, 0));

        for (String option : POLICIES) {
            // Painted start to finish here: calling super would let the look-and-feel
            // drop its own full-width selection bar over the rounded hover pill.
            JMenuItem item = new JMenuItem(option) {
                private static final long serialVersionUID = 1L;
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = smooth(g);
                    if (getModel().isArmed()) {     // quiet hover, inset so it clears the rounded corners
                        g2.setColor(Theme.panel2());
                        g2.fill(new RoundRectangle2D.Double(6, 1, getWidth() - 12.0, getHeight() - 2.0, 9, 9));
                    }
                    g2.setFont(getFont());
                    g2.setColor(getForeground());
                    g2.drawString(getText(), getInsets().left, (float) middle(g2, getHeight() / 2.0));
                    g2.dispose();
                }
            };
            item.setOpaque(false);
            item.setFont(Fonts.body(15f));
            item.setForeground(Theme.text());       // every option reads the same, as in the design
            item.setBorder(new EmptyBorder(10, 18, 10, 18));
            item.addActionListener(e -> {
                policy = option;
                rebuildInputs();
                rerun();            // keep whatever is on screen, just re-run with the new policy
            });
            menu.add(item);
        }
        return menu;
    }

    private String countLabel() {
        return count(blocks.getText()) + " blocks / " + count(processes.getText()) + " processes";
    }

    private static int count(String raw) {
        int n = 0;
        for (String t : raw.trim().split("[\\s,]+")) if (!t.isEmpty()) n++;
        return n;
    }

    private static String randomSizes(Random rng, int n, int lo, int hi) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) sb.append(' ');
            sb.append((rng.nextInt((hi - lo) / 10 + 1) * 10) + lo);
        }
        return sb.toString();
    }

    private void setFields(String b, String p) {
        restoring = true;
        blocks.setText(b);
        processes.setText(p);
        restoring = false;
    }

    private void pushUndo() {
        if (restoring || blocks == null) return;
        String[] cur = {blocks.getText(), processes.getText()};
        if (!undoStack.isEmpty() && Arrays.equals(undoStack.peek(), cur)) return;
        undoStack.push(cur);
        redoStack.clear();
        if (undoStack.size() > 50) undoStack.removeLast();
    }

    private void recordHistory(String label) {
        HistoryItem item = new HistoryItem(label, blocks.getText().trim(), processes.getText().trim(), policy);
        history.removeIf(h -> h.blocks().equals(item.blocks()) && h.procs().equals(item.procs())
                && h.policy().equals(item.policy()));
        history.add(0, item);
        if (history.size() > 6) history.remove(history.size() - 1);
    }

    private void showHistoryMenu(JComponent invoker) {
        if (history.isEmpty()) { showToast(this, "No recent runs yet"); return; }
        JPopupMenu menu = new JPopupMenu();
        menu.setBorder(BorderFactory.createLineBorder(Theme.inputLine(), 1));
        for (HistoryItem item : history) {
            JMenuItem mi = new JMenuItem(item.label());
            mi.setFont(Fonts.body(13f));
            mi.setBackground(Theme.input());
            mi.setForeground(Theme.text());
            mi.setBorder(new EmptyBorder(6, 12, 6, 14));
            mi.addActionListener(e -> {
                blocks.setText(item.blocks());
                processes.setText(item.procs());
                policy = item.policy();
                hasRun = false;
                rebuildInputs();
                rerun();
                showToast(this, "Restored from history");
            });
            menu.add(mi);
        }
        if (invoker.isShowing()) menu.show(invoker, 0, invoker.getHeight());
    }

    // ============================ RESULTS ============================

    @Override protected void onRunPressed() {
        hasRun = true;
        rerun();
    }

    @Override protected void runSimulation(JPanel r) {
        blocks.setInvalid(false);
        processes.setInvalid(false);

        if (allBlank(blocks, processes)) {
            r.add(outputHeader());
            r.add(Box.createVerticalStrut(12));
            r.add(Notice.prompt("Type the memory block sizes (e.g. 100 500 200 300 600) and the process sizes "
                    + "(e.g. 212 417 112 426), or click Sample data."));
            return;
        }
        if (!hasRun) {
            r.add(outputHeader());
            r.add(Box.createVerticalStrut(12));
            r.add(Notice.prompt("Inputs ready - press RUN SIMULATION (or Enter) to see the memory map."));
            return;
        }

        int[] blockSizes = null, procSizes = null;
        String blockErr = null, procErr = null;
        try {
            blockSizes = parseSizes(blocks.getText(), "memory block");
        } catch (IllegalArgumentException e) { blocks.setInvalid(true); blockErr = e.getMessage(); }
        try {
            procSizes = parseSizes(processes.getText(), "process");
        } catch (IllegalArgumentException e) { processes.setInvalid(true); procErr = e.getMessage(); }

        if (blockErr != null || procErr != null) {
            r.add(outputHeader());
            r.add(Box.createVerticalStrut(12));
            r.add(new Notice(false, "Input check", blockErr != null ? blockErr : procErr));
            if (blockErr != null && procErr != null) {
                r.add(Box.createVerticalStrut(6));
                r.add(new Notice(false, "Also", procErr));
            }
            r.add(Box.createVerticalStrut(6));
            r.add(body(String.format("Rule: every block and process size is a whole number from 1 to %,d KB, "
                    + "separated by spaces or commas.", MAX_SIZE), 12, Theme.muted()));
            return;
        }

        Plan plan = allocate(policy, blockSizes, procSizes);
        recordHistory(String.format("%s / %d blocks / %d processes / %,d KB free",
                policy, blockSizes.length, procSizes.length, plan.freeSpace));

        r.add(outputHeader());
        r.add(Box.createVerticalStrut(12));

        // Left: the memory map drawn to scale.  Right: the numbers and where each process went.
        MemoryMap map = new MemoryMap(plan);
        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setAlignmentX(LEFT_ALIGNMENT);
        left.add(cappedScroll(map, 640));
        left.add(Box.createVerticalStrut(8));
        left.add(pixelText(abbrev(policy) + " · " + policy.toUpperCase(), 8f, Theme.text(), 0.08f));

        r.add(splitRow(left, rightColumn(plan, procSizes.length), 1.0, 1.25, 20, 820));
        r.add(Box.createVerticalStrut(14));

        // Step controls for the map
        JPanel playBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        playBar.setOpaque(false);
        playBar.setAlignmentX(LEFT_ALIGNMENT);
        playBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        DashedPill play = new DashedPill("Play >");
        play.addActionListener(e -> map.play());
        DashedPill stepBtn = new DashedPill("Step >>");
        stepBtn.addActionListener(e -> map.stepOnce());
        DashedPill allBtn = new DashedPill("Show All");
        allBtn.addActionListener(e -> map.showAll());
        playBar.add(play); playBar.add(stepBtn); playBar.add(allBtn);
        playBar.add(body("place the processes one at a time", 11, Theme.muted()));
        r.add(playBar);
        r.add(Box.createVerticalStrut(14));

        // Block by block
        String[][] blockRows = new String[blockSizes.length][];
        for (int b = 0; b < blockSizes.length; b++) {
            List<Integer> holders = plan.holdersOf(b);
            StringBuilder who = new StringBuilder();
            for (int i : holders) who.append(who.length() > 0 ? ", " : "").append("P").append(i);
            blockRows[b] = new String[]{"Block " + b, String.format("%,d KB", blockSizes[b]),
                    holders.isEmpty() ? "-" : who.toString(),
                    String.format("%,d KB", blockSizes[b] - plan.remaining[b]),
                    String.format("%,d KB", plan.remaining[b]),
                    holders.isEmpty() ? "Untouched" : plan.remaining[b] == 0 ? "Full" : "Partly used"};
        }
        r.add(collapsibleSection("BLOCK BY BLOCK",
                cappedScroll(new DataTable(new String[]{"BLOCK", "SIZE", "HOLDS", "USED", "FREE GAP", "STATE"},
                        blockRows, plan.untouchedRows(), Theme.cyan()),
                        DataTable.HEAD_H + 2 + 11 * DataTable.ROW + 1)));   // whole rows only
        r.add(Box.createVerticalStrut(12));

        r.add(new FormulaStrip(String.format(
                "External fragmentation = %,d KB free, split across %d gap(s); the largest single gap is %,d KB.",
                plan.freeSpace, plan.gapCount, plan.largestGap)));
        r.add(Box.createVerticalStrut(6));
        r.add(new FormulaStrip(String.format(
                "Internal fragmentation = %,d KB - each process gets exactly its own size, nothing wasted inside.",
                plan.internal)));
        r.add(Box.createVerticalStrut(8));
        if (plan.unallocated > 0) {
            StringBuilder sb = new StringBuilder();
            int listed = 0;
            for (int i = 0; i < procSizes.length && listed < 4; i++)
                if (plan.placedIn[i] < 0) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append("P").append(i).append(" (").append(procSizes[i]).append(" KB)");
                    listed++;
                }
            if (plan.unallocated > listed) sb.append(" and ").append(plan.unallocated - listed).append(" more");
            r.add(new Notice(false, "Not allocated", sb + " - the largest free gap is only "
                    + String.format("%,d KB.", plan.largestGap)));
            r.add(Box.createVerticalStrut(6));
            r.add(body(String.format("There is %,d KB free in total, but it is split into separate gaps and no single "
                    + "gap is big enough. That is external fragmentation.", plan.freeSpace), 12, Theme.muted()));
        } else {
            r.add(new Notice(true, "All allocated",
                    String.format("Every process found a gap, with %,d KB still free.", plan.freeSpace)));
            r.add(Box.createVerticalStrut(6));
            r.add(body(String.format("Internal fragmentation is %,d KB because each process takes exactly its "
                    + "own size out of a block; the %,d KB of leftover gaps between blocks is external "
                    + "fragmentation.", plan.internal, plan.freeSpace), 12, Theme.muted()));
        }
        r.add(Box.createVerticalStrut(14));

        // Compare all 3 policies
        String[][] cmp = new String[POLICIES.length][];
        int current = -1, bestRow = 0, mostPlaced = -1, bestGap = -1, tiedOnPlaced = 0;
        for (int i = 0; i < POLICIES.length; i++) {
            if (POLICIES[i].equals(policy)) current = i;
            Plan x = allocate(POLICIES[i], blockSizes, procSizes);
            if (x.placed > mostPlaced || (x.placed == mostPlaced && x.largestGap > bestGap)) {
                mostPlaced = x.placed;
                bestGap = x.largestGap;
                bestRow = i;
            }
            cmp[i] = new String[]{POLICIES[i],
                    x.placed + " / " + procSizes.length,
                    String.format("%,d KB", x.freeSpace),
                    String.format("%,d KB", x.largestGap),
                    String.valueOf(x.usedBlocks)};
        }
        for (String p2 : POLICIES)
            if (allocate(p2, blockSizes, procSizes).placed == mostPlaced) tiedOnPlaced++;
        r.add(explainSection(explain(policy, blockSizes, procSizes, plan)));
        r.add(Box.createVerticalStrut(10));
        r.add(collapsibleSection("COMPARE ALL " + POLICIES.length + " POLICIES",
                new DataTable(new String[]{"POLICY", "ALLOCATED", "FREE SPACE", "LARGEST GAP", "BLOCKS USED"},
                        cmp, new int[]{current}, Theme.yellow())));
        r.add(Box.createVerticalStrut(10));
        // Most processes placed wins; the largest remaining gap only breaks a tie.
        r.add(new Notice(true, "Best here", tiedOnPlaced > 1
                ? String.format("%s placed %d of %d processes, like the others, but left the largest usable gap (%,d KB).",
                        POLICIES[bestRow], mostPlaced, procSizes.length, bestGap)
                : String.format("%s placed the most processes: %d of %d.",
                        POLICIES[bestRow], mostPlaced, procSizes.length)));

        r.add(Box.createVerticalStrut(12));
        final int[] bs = blockSizes, ps = procSizes;
        DashedPill copy = new DashedPill("Copy summary");
        copy.addActionListener(e -> {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Fragmentation (%s)%n", policy));
            sb.append("Blocks: ").append(join(bs)).append("\n");
            sb.append("Processes: ").append(join(ps)).append("\n");
            for (int i = 0; i < ps.length; i++)
                sb.append(String.format("P%d (%d KB) -> %s%n", i, ps[i],
                        plan.placedIn[i] < 0 ? "not allocated" : "Block " + plan.placedIn[i]));
            sb.append(String.format("Free space %d KB in %d gap(s), largest %d KB%n",
                    plan.freeSpace, plan.gapCount, plan.largestGap));
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
            showToast(this, "Copied summary to clipboard!");
        });
        r.add(copy);
    }

    /** Stat cards + "where each process went" list. */
    private JComponent rightColumn(Plan plan, int procCount) {
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setAlignmentX(LEFT_ALIGNMENT);

        Row cards = new Row(new GridLayout(1, 3, 10, 0));
        cards.add(new StatCard("FREE SPACE", String.format("%,d KB", plan.freeSpace), true));
        cards.add(new StatCard("UNALLOCATED", String.valueOf(plan.unallocated), false));
        cards.add(new StatCard("USED BLOCKS", String.valueOf(plan.usedBlocks), false));
        right.add(cards);
        right.add(Box.createVerticalStrut(14));

        right.add(pixelText("WHERE EACH PROCESS WENT", 7f, Theme.muted(), 0.06f));
        right.add(Box.createVerticalStrut(8));

        JPanel list = new JPanel();
        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setAlignmentX(LEFT_ALIGNMENT);
        for (int i = 0; i < procCount; i++) {
            if (i > 0) list.add(Box.createVerticalStrut(8));
            list.add(new PlacementRow(i, plan.procSizes[i], plan.placedIn[i]));
        }
        right.add(cappedScroll(list, 9 * (PlacementRow.H + 8) - 8));   // whole rows only
        right.add(Box.createVerticalStrut(10));
        right.add(body("Leftover gaps between blocks = external fragmentation", 11, Theme.muted()));
        return right;
    }

    private JComponent outputHeader() {
        JPanel h = hRow();
        h.add(pixelText("SIMULATION OUTPUT · " + policy.toUpperCase(), 7f,
                Theme.dark ? Theme.cyan() : Theme.text(), 0f));
        return pad(h, 0, 0, false);
    }

    private static String abbrev(String policy) {
        return switch (policy) {
            case "Next Fit" -> "NF";
            case "Best Fit" -> "BF";
            case "Worst Fit" -> "WF";
            default -> "FF";
        };
    }

    private static String join(int[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(v[i]);
        }
        return sb.toString();
    }

    /** Whole numbers above 0, separated by spaces or commas. Throws with a readable message otherwise. */
    static int[] parseSizes(String raw, String what) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty())
            throw new IllegalArgumentException("type at least one " + what + " size");
        String[] parts = trimmed.split("[\\s,]+");
        List<Integer> out = new ArrayList<>();
        int pos = 0;
        for (String p : parts) {
            if (p.isEmpty()) continue;
            pos++;
            long v;
            try {
                v = Long.parseLong(p);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(what + " " + pos + " (\"" + p + "\") is not a whole number");
            }
            if (v <= 0)
                throw new IllegalArgumentException(what + " " + pos + " (\"" + p + "\") must be more than 0");
            if (v > MAX_SIZE)
                throw new IllegalArgumentException(String.format(
                        "%s %d (\"%s\") is too big - keep each %s size at %,d KB or less", what, pos, p, what, MAX_SIZE));
            out.add((int) v);
        }
        if (out.isEmpty())
            throw new IllegalArgumentException("type at least one " + what + " size");
        int[] arr = new int[out.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
        return arr;
    }

    // ============================ THE ALGORITHMS ============================

    /** One slice of the memory map: a process, or the free gap left at the end of a block. */
    static final class Seg {
        final int process;   // -1 means free
        final int size;
        Seg(int process, int size) { this.process = process; this.size = size; }
    }

    /** The finished allocation: where everything went and how the free space is split. */
    static final class Plan {
        final int[] blockSizes, procSizes, remaining, placedIn;
        final List<List<Seg>> segments = new ArrayList<>();   // one list per block, in placement order
        int placed, unallocated, usedBlocks, largestGap, gapCount;
        long internal;          // memory handed out minus memory actually asked for
        long freeSpace, totalMemory;

        Plan(int[] blockSizes, int[] procSizes) {
            this.blockSizes = blockSizes;
            this.procSizes = procSizes;
            this.remaining = blockSizes.clone();
            this.placedIn = new int[procSizes.length];
            Arrays.fill(this.placedIn, -1);
        }

        List<Integer> holdersOf(int block) {
            List<Integer> out = new ArrayList<>();
            for (int i = 0; i < placedIn.length; i++) if (placedIn[i] == block) out.add(i);
            return out;
        }

        /** Row numbers of blocks that no process touched. */
        int[] untouchedRows() {
            List<Integer> rows = new ArrayList<>();
            for (int b = 0; b < blockSizes.length; b++) if (remaining[b] == blockSizes[b]) rows.add(b);
            int[] a = new int[rows.size()];
            for (int i = 0; i < a.length; i++) a[i] = rows.get(i);
            return a;
        }
    }

    /**
     * Variable partitions: processes are taken in order and each one is cut out of a block that
     * still has room, so a block can hold several processes.
     *   First Fit - the first block with enough room left, searching from block 0 each time.
     *   Next Fit  - the same, but the search carries on from the block the last process went
     *               into and wraps round the end, so allocations spread out instead of
     *               crowding into the low-numbered blocks.
     *   Best Fit  - the block whose remaining room is smallest but still enough.
     *   Worst Fit - the block whose remaining room is largest.
     */
    /** One line per process: which gaps were big enough, which one the policy picked, what is left. */
    static List<String> explain(String policy, int[] blockSizes, int[] procSizes, Plan plan) {
        List<String> out = new ArrayList<>();
        out.add(switch (policy) {
            case "Best Fit" -> "Rule (Best Fit): put each process in the smallest gap that is big enough.";
            case "Worst Fit" -> "Rule (Worst Fit): put each process in the largest gap.";
            case "Next Fit" -> "Rule (Next Fit): like First Fit, but start looking from the block used last time.";
            default -> "Rule (First Fit): put each process in the first gap (from Block 0) that is big enough.";
        });
        int[] free = blockSizes.clone();
        for (int i = 0; i < procSizes.length; i++) {
            List<String> fits = new ArrayList<>();
            for (int b = 0; b < free.length; b++) if (free[b] >= procSizes[i]) fits.add("Block " + b + " (" + free[b] + ")");
            int chosen = plan.placedIn[i];
            String cand = fits.size() > 6 ? String.join(", ", fits.subList(0, 6)) + ", ..." : String.join(", ", fits);
            if (chosen < 0) {
                int largest = 0;
                for (int f : free) largest = Math.max(largest, f);
                out.add(String.format("P%d (%,d KB): no gap is big enough (largest is %,d KB) → not allocated.", i, procSizes[i], largest));
                continue;
            }
            free[chosen] -= procSizes[i];
            out.add(String.format("P%d (%,d KB): fits in %s → %s picks Block %d → %,d KB left there.",
                    i, procSizes[i], cand, policy, chosen, free[chosen]));
        }
        out.add(String.format("External fragmentation = %,d KB in %d gap(s), largest %,d KB.", plan.freeSpace, plan.gapCount, plan.largestGap));
        out.add(String.format("Internal fragmentation = %,d KB.", plan.internal));
        if (plan.unallocated > 0 && plan.freeSpace > 0)
            out.add("External fragmentation: there is free memory, but it is split into gaps that are each too small for the waiting process(es).");
        return out;
    }

    static Plan allocate(String policy, int[] blockSizes, int[] procSizes) {
        Plan plan = new Plan(blockSizes, procSizes);

        boolean next = policy.equals("Next Fit");
        boolean stopAtFirst = next || policy.equals("First Fit");
        int n = blockSizes.length;
        int cursor = 0;                 // Next Fit carries on from the last block it used

        for (int i = 0; i < procSizes.length; i++) {
            int size = procSizes[i];
            int chosen = -1;
            for (int step = 0; step < n; step++) {
                int b = next ? (cursor + step) % n : step;      // Next Fit wraps round the end
                if (plan.remaining[b] < size) continue;
                if (chosen < 0) { chosen = b; if (stopAtFirst) break; continue; }
                if (policy.equals("Best Fit")  && plan.remaining[b] < plan.remaining[chosen]) chosen = b;
                if (policy.equals("Worst Fit") && plan.remaining[b] > plan.remaining[chosen]) chosen = b;
            }
            if (chosen >= 0) {
                plan.remaining[chosen] -= size;
                plan.placedIn[i] = chosen;
                plan.placed++;
                cursor = chosen;        // the next search starts here, not back at block 0
            } else {
                plan.unallocated++;
            }
        }

        // Internal fragmentation = what was handed out minus what the processes asked for.
        // Variable partitions cut the exact size out of a hole, so this comes to zero; it is
        // worked out rather than assumed so the figure stays right if the model ever changes.
        long handedOut = 0, requested = 0;
        for (int b = 0; b < n; b++) handedOut += blockSizes[b] - plan.remaining[b];
        for (int i = 0; i < procSizes.length; i++) if (plan.placedIn[i] >= 0) requested += procSizes[i];
        plan.internal = handedOut - requested;

        for (int b = 0; b < blockSizes.length; b++) {
            plan.totalMemory += blockSizes[b];
            plan.freeSpace += plan.remaining[b];
            if (plan.remaining[b] != blockSizes[b]) plan.usedBlocks++;
            if (plan.remaining[b] > 0) {
                plan.gapCount++;
                plan.largestGap = Math.max(plan.largestGap, plan.remaining[b]);
            }
            List<Seg> segs = new ArrayList<>();
            for (int i : plan.holdersOf(b)) segs.add(new Seg(i, procSizes[i]));
            if (plan.remaining[b] > 0) segs.add(new Seg(-1, plan.remaining[b]));
            plan.segments.add(segs);
        }
        return plan;
    }

    // ============================ SMALL LAYOUT HELPERS ============================

    private static JPanel hRow() {
        JPanel r = new JPanel();
        r.setOpaque(false);
        r.setLayout(new BoxLayout(r, BoxLayout.X_AXIS));
        r.setAlignmentX(LEFT_ALIGNMENT);
        return r;
    }

    /** Keeps a tall component inside a scroll pane so the result card never grows without end. */
    private static JComponent cappedScroll(JComponent comp, int maxH) {
        Dimension d = comp.getPreferredSize();
        if (d.height <= maxH) return comp;
        JScrollPane sp = new JScrollPane(comp, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER) {
            private static final long serialVersionUID = 1L;
            @Override public Dimension getPreferredSize() { return new Dimension(d.width, maxH); }
            @Override public Dimension getMaximumSize()   { return new Dimension(Integer.MAX_VALUE, maxH); }
        };
        sp.setAlignmentX(LEFT_ALIGNMENT);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(20);
        styleScrollPane(sp);
        return sp;
    }

    /** Two panels side by side in the given proportion, stacked when the window is narrower than minSide. */
    private static JPanel splitRow(JComponent a, JComponent b, double frA, double frB, int gap, int minSide) {
        JPanel grid = new JPanel(null) {
            private static final long serialVersionUID = 1L;
            private boolean assumedSide = true;
            private boolean side() { return getWidth() == 0 || getWidth() >= minSide; }
            @Override public void doLayout() {
                boolean sbs = side();
                if (sbs) {
                    int avail = Math.max(1, getWidth() - gap);
                    int wa = (int) Math.round(avail * frA / (frA + frB));
                    a.setBounds(0, 0, wa, a.getPreferredSize().height);
                    b.setBounds(wa + gap, 0, avail - wa, b.getPreferredSize().height);
                } else {
                    int ha = a.getPreferredSize().height;
                    a.setBounds(0, 0, getWidth(), ha);
                    b.setBounds(0, ha + gap, getWidth(), b.getPreferredSize().height);
                }
                if (sbs != assumedSide) SwingUtilities.invokeLater(this::revalidate);
            }
            @Override public Dimension getPreferredSize() {
                assumedSide = side();
                int ha = a.getPreferredSize().height, hb = b.getPreferredSize().height;
                return new Dimension(300, assumedSide ? Math.max(ha, hb) : ha + gap + hb);
            }
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        grid.setOpaque(false);
        grid.setAlignmentX(LEFT_ALIGNMENT);
        grid.add(a);
        grid.add(b);
        grid.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { grid.revalidate(); }
        });
        return grid;
    }

    // ============================ RESULT COMPONENTS ============================

    /** Cyan 4px bar with the formula next to it (same look as the other modules). */
    static class FormulaStrip extends Row {
        private static final long serialVersionUID = 1L;
        FormulaStrip(String formula) {
            super(new BorderLayout());
            setBorder(new EmptyBorder(8, 18, 8, 8));
            add(body(formula, 13, Theme.text()), BorderLayout.CENTER);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(Theme.cyan());
            g2.fill(new RoundRectangle2D.Double(0, 0, 4, getHeight(), 4, 4));
            g2.dispose();
        }
    }

    /** Small caption-over-value card. */
    static class StatCard extends JPanel {
        private static final long serialVersionUID = 1L;
        private final boolean accent;
        StatCard(String caption, String value, boolean accent) {
            this.accent = accent;
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(12, 14, 12, 14));
            JLabel c = new SafeLabel(caption);
            c.setFont(Fonts.label(8f));
            c.setForeground(Theme.muted());
            c.setAlignmentX(LEFT_ALIGNMENT);
            add(c);
            add(Box.createVerticalStrut(8));
            add(body(value, 22, accent ? Theme.green() : Theme.text()));
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            RoundRectangle2D box = new RoundRectangle2D.Double(0.75, 0.75, getWidth() - 1.5, getHeight() - 1.5, 16, 16);
            g2.setColor(Theme.panel());
            g2.fill(box);
            g2.setColor(accent ? Theme.green() : Theme.inputLine());
            g2.setStroke(new BasicStroke(accent ? 2f : 1.5f));
            g2.draw(box);
            g2.dispose();
        }
    }

    /** One line of "where each process went": colour swatch, process, arrow, block. */
    static class PlacementRow extends JComponent {
        private static final long serialVersionUID = 1L;
        static final int H = 38, PAD = 14, SWATCH = 15;
        private final int process, size, block;

        PlacementRow(int process, int size, int block) {
            this.process = process; this.size = size; this.block = block;
            setAlignmentX(LEFT_ALIGNMENT);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(260, H); }
        @Override public Dimension getMaximumSize()   { return new Dimension(Integer.MAX_VALUE, H); }
        @Override public Dimension getMinimumSize()   { return new Dimension(180, H); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getHeight();
            boolean ok = block >= 0;
            RoundRectangle2D box = new RoundRectangle2D.Double(0.75, 0.75, w - 1.5, h - 1.5, 13, 13);
            g2.setColor(Theme.panel());
            g2.fill(box);
            if (!ok) {
                Color pink = Theme.pink();
                g2.setColor(new Color(pink.getRed(), pink.getGreen(), pink.getBlue(), Theme.dark ? 34 : 26));
                g2.fill(box);
            }
            g2.setColor(ok ? Theme.inputLine() : Theme.pink());
            g2.setStroke(new BasicStroke(1.4f));
            g2.draw(box);

            double cy = h / 2.0;
            RoundRectangle2D sw = new RoundRectangle2D.Double(PAD, cy - SWATCH / 2.0, SWATCH, SWATCH, 5, 5);
            g2.setColor(ok ? blockColor(process, 1.0) : Theme.pink());
            g2.fill(sw);

            int x = PAD + SWATCH + 12;
            g2.setFont(Fonts.body(13f));
            g2.setColor(ok ? Theme.text() : Theme.pink());
            String who = String.format("P%d · %,d KB", process, size);
            g2.drawString(who, x, (float) middle(g2, cy));
            x += g2.getFontMetrics().stringWidth(who) + 14;

            g2.setColor(Theme.muted());
            g2.drawString("→", x, (float) middle(g2, cy));
            x += g2.getFontMetrics().stringWidth("→") + 12;

            g2.setColor(ok ? Theme.text() : Theme.pink());
            g2.drawString(ok ? "Block " + block : "not allocated (no gap big enough)", x, (float) middle(g2, cy));
            g2.dispose();
        }
    }

    /**
     * The memory map: one tall column, every block stacked in order and drawn to scale.
     * A block that holds several processes shows them one under the other, with the gap
     * that is left over at the bottom of that block.
     */
    static class MemoryMap extends JComponent {
        private static final long serialVersionUID = 1L;
        static final int GUTTER = 84, COL_W = 250, MIN_SEG = 32, TARGET_H = 430, PAD = 4;
        private final transient Plan plan;
        private int shown;
        private int hoveredBlock = -1;
        private transient javax.swing.Timer timer;

        MemoryMap(Plan plan) {
            this.plan = plan;
            this.shown = plan.procSizes.length;
            setAlignmentX(LEFT_ALIGNMENT);
            ToolTipManager.sharedInstance().registerComponent(this);
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int b = blockAt(e.getY());
                    if (b != hoveredBlock) { hoveredBlock = b; repaint(); }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent e) {
                    if (hoveredBlock != -1) { hoveredBlock = -1; repaint(); }
                }
            });
        }

        void play() {
            if (plan.procSizes.length == 0) return;
            if (timer != null && timer.isRunning()) timer.stop();
            shown = 0;
            repaint();
            timer = new javax.swing.Timer(400, e -> {
                shown++;
                if (shown >= plan.procSizes.length) {
                    shown = plan.procSizes.length;
                    ((javax.swing.Timer) e.getSource()).stop();
                }
                repaint();
            });
            timer.start();
        }

        void stepOnce() {
            if (timer != null && timer.isRunning()) timer.stop();
            shown = shown >= plan.procSizes.length ? 1 : shown + 1;
            repaint();
        }

        void showAll() {
            if (timer != null && timer.isRunning()) timer.stop();
            shown = plan.procSizes.length;
            repaint();
        }

        /** Segments of one block as they look after only the first {@code shown} processes were placed. */
        private List<Seg> visibleSegs(int b) {
            List<Seg> out = new ArrayList<>();
            int free = plan.blockSizes[b];
            for (Seg s : plan.segments.get(b)) {
                if (s.process < 0) continue;
                if (s.process >= shown) continue;
                out.add(s);
                free -= s.size;
            }
            if (free > 0) out.add(new Seg(-1, free));
            return out;
        }

        private double scale() {
            long total = Math.max(1, plan.totalMemory);
            return TARGET_H / (double) total;
        }

        private int segHeight(int size) {
            return Math.max(MIN_SEG, (int) Math.round(size * scale()));
        }

        private int blockHeight(int b) {
            int h = 0;
            for (Seg s : visibleSegs(b)) h += segHeight(s.size);
            return Math.max(MIN_SEG, h);
        }

        private int blockAt(int y) {
            int top = PAD;
            for (int b = 0; b < plan.blockSizes.length; b++) {
                int h = blockHeight(b);
                if (y >= top && y < top + h) return b;
                top += h;
            }
            return -1;
        }

        @Override public String getToolTipText(MouseEvent e) {
            int b = blockAt(e.getY());
            if (b < 0) return null;
            List<Integer> holders = plan.holdersOf(b);
            if (holders.isEmpty())
                return String.format("Block %d: %,d KB, untouched - the whole block is one free gap", b, plan.blockSizes[b]);
            StringBuilder who = new StringBuilder();
            for (int i : holders) who.append(who.length() > 0 ? ", " : "").append("P").append(i);
            return String.format("Block %d: %,d KB holds %s - %,d KB gap left",
                    b, plan.blockSizes[b], who, plan.remaining[b]);
        }

        @Override public Dimension getPreferredSize() {
            int h = PAD * 2;
            for (int b = 0; b < plan.blockSizes.length; b++) h += blockHeight(b);
            return new Dimension(GUTTER + COL_W, h);
        }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        @Override public Dimension getMinimumSize() { return new Dimension(GUTTER + 150, getPreferredSize().height); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int colW = Math.max(140, Math.min(COL_W, getWidth() - GUTTER));
            int left = GUTTER;
            int totalH = getPreferredSize().height - PAD * 2;

            RoundRectangle2D column = new RoundRectangle2D.Double(left + 0.75, PAD + 0.75, colW - 1.5, totalH - 1.5, 10, 10);
            Shape oldClip = g2.getClip();
            g2.clip(column);

            int top = PAD;
            for (int b = 0; b < plan.blockSizes.length; b++) {
                List<Seg> segs = visibleSegs(b);
                int bh = blockHeight(b);
                if (b == hoveredBlock) {
                    Color yellow = Theme.yellow();
                    g2.setColor(new Color(yellow.getRed(), yellow.getGreen(), yellow.getBlue(), Theme.dark ? 36 : 56));
                    g2.fillRect(left, top, colW, bh);
                }

                int y = top;
                for (Seg s : segs) {
                    int sh = segHeight(s.size);
                    if (s.process >= 0) {
                        g2.setColor(blockColor(s.process, Theme.dark ? 0.82 : 0.92));
                        g2.fillRect(left, y, colW, sh);
                        g2.setFont(Fonts.body(13f));
                        g2.setColor(glyph());
                        centerText(g2, String.format("P%d · %,d KB", s.process, s.size),
                                left + colW / 2.0, middle(g2, y + sh / 2.0));
                    } else {
                        g2.setColor(Theme.panel());
                        g2.fillRect(left, y, colW, sh);
                        g2.setFont(Fonts.body(12f));
                        g2.setColor(Theme.muted());
                        centerText(g2, String.format("%,d KB free", s.size),
                                left + colW / 2.0, middle(g2, y + sh / 2.0));
                    }
                    y += sh;
                    if (y < top + bh) {   // thin divider between processes inside the same block
                        g2.setColor(Theme.line());
                        g2.setStroke(new BasicStroke(1f));
                        g2.drawLine(left, y, left + colW, y);
                    }
                }

                top += bh;
                if (b < plan.blockSizes.length - 1) {   // stronger divider between blocks
                    g2.setColor(Theme.inputLine());
                    g2.setStroke(new BasicStroke(1.8f));
                    g2.drawLine(left, top, left + colW, top);
                }
            }

            g2.setClip(oldClip);
            g2.setColor(Theme.inputLine());
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(column);

            // left gutter: block number and size, lined up with the top slice of each block
            top = PAD;
            for (int b = 0; b < plan.blockSizes.length; b++) {
                List<Seg> segs = visibleSegs(b);
                int firstH = segs.isEmpty() ? blockHeight(b) : segHeight(segs.get(0).size);
                double cy = top + firstH / 2.0;
                g2.setFont(Fonts.label(8f));
                g2.setColor(Theme.text());
                String id = "BLOCK " + b;
                g2.drawString(id, GUTTER - 10 - g2.getFontMetrics().stringWidth(id), (float) middle(g2, cy - 7));
                g2.setFont(Fonts.body(11f));
                g2.setColor(Theme.muted());
                String sz = String.format("%,d KB", plan.blockSizes[b]);
                g2.drawString(sz, GUTTER - 10 - g2.getFontMetrics().stringWidth(sz), (float) middle(g2, cy + 8));
                top += blockHeight(b);
            }
            g2.dispose();
        }
    }

    /**
     * Painted table in the Mini OS look. Rows listed in {@code marked} get a coloured wash,
     * which is how untouched blocks and the current policy are called out.
     */
    static class DataTable extends JComponent {
        private static final long serialVersionUID = 1L;
        static final int HEAD_H = 27, ROW = 29;
        private final String[] headers;
        private final String[][] rows;
        private final int[] marked;
        private final transient Color markColor;
        private int hovered = -1;

        DataTable(String[] headers, String[][] rows, int[] marked, Color markColor) {
            this.headers = headers;
            this.rows = rows;
            this.marked = marked == null ? new int[0] : marked;
            this.markColor = markColor;
            setAlignmentX(LEFT_ALIGNMENT);
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int idx = (e.getY() - (2 + HEAD_H)) / ROW;
                    int next = (idx >= 0 && idx < rows.length) ? idx : -1;
                    if (next != hovered) { hovered = next; repaint(); }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent e) {
                    if (hovered != -1) { hovered = -1; repaint(); }
                }
            });
        }

        private boolean isMarked(int row) {
            for (int m : marked) if (m == row) return true;
            return false;
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension(420, HEAD_H + ROW * Math.max(1, rows.length) + 3);
        }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getPreferredSize().height;
            double colW = (w - 3) / (double) headers.length;

            RoundRectangle2D box = new RoundRectangle2D.Double(0.75, 0.75, w - 1.5, h - 1.5, 20, 20);
            Shape oldClip = g2.getClip();
            g2.clip(box);
            g2.setColor(Theme.panel2());
            g2.fillRect(0, 0, w, HEAD_H + 2);
            for (int i = 0; i < rows.length; i++) {
                if (isMarked(i)) {
                    g2.setColor(new Color(markColor.getRed(), markColor.getGreen(), markColor.getBlue(),
                            Theme.dark ? 58 : 80));
                } else if (i == hovered) {
                    Color yellow = Theme.yellow();
                    g2.setColor(new Color(yellow.getRed(), yellow.getGreen(), yellow.getBlue(), Theme.dark ? 55 : 95));
                } else {
                    g2.setColor(i % 2 == 0 ? Theme.panel() : Theme.panel2());
                }
                g2.fillRect(0, 2 + HEAD_H + i * ROW, w, ROW + 2);
            }
            g2.setColor(Theme.line());
            g2.setStroke(new BasicStroke(1f));
            for (int c = 1; c < headers.length; c++) {
                int x = (int) Math.round(1.5 + c * colW);
                g2.drawLine(x, 0, x, h);
            }
            g2.setClip(oldClip);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(box);

            g2.setFont(Fonts.label(9f));
            g2.setColor(Theme.muted());
            for (int c = 0; c < headers.length; c++)
                centerText(g2, headers[c], 1.5 + (c + 0.5) * colW, middle(g2, 2 + HEAD_H / 2.0));

            g2.setFont(Fonts.body(13f));
            for (int i = 0; i < rows.length; i++) {
                double midY = middle(g2, 2 + HEAD_H + i * ROW + ROW / 2.0);
                for (int c = 0; c < rows[i].length; c++) {
                    g2.setColor(isMarked(i) && Theme.dark ? markColor : Theme.text());
                    centerText(g2, rows[i][c], 1.5 + (c + 0.5) * colW, midY);
                }
            }
            g2.dispose();
        }
    }
}
