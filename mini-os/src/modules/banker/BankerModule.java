package modules.banker;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import modules.common.FigmaModule;
import shared.Fonts;
import shared.Theme;

/**
 * BANKER'S ALGORITHM  —  OWNER: Member 3
 *   [x] Available, Allocation and Maximum grids, flexible process AND resource-type counts
 *   [x] Need = Maximum - Allocation
 *   [x] AVAILABLE ladder: start, then the Available vector after each process is released
 *   [x] SAFE / NOT SAFE verdict chips and the safe sequence
 *   [x] Validation: blank, letters, negatives, Allocation > Maximum - never crashes
 * CHECK: sample data (5 processes x 4 resource types) -> SAFE, sequence P3, P4, P5, P1, P2
 *        AVAILABLE ladder: start 3 2 2 1 -> +P3 4 5 4 4 -> +P4 5 8 6 4 -> +P5 7 8 7 7
 *                          -> +P1 8 8 7 9 -> +P2 10 8 8 11
 *
 * Words used on this page:
 *   Allocation = what each process already holds      Maximum = the most it will ever ask for
 *   Need       = Maximum - Allocation                 Available = free units, updated after each release
 *
 * The page design (header, inputs, run button, result card) comes from FigmaModule.
 */
public class BankerModule extends FigmaModule {
    private static final long serialVersionUID = 1L;

    /** CHECK sample: Available 3 2 2 1 -> SAFE with sequence P3, P4, P5, P1, P2. */
    private static final String[] SAMPLE_AVAIL = {"3", "2", "2", "1"};
    private static final String[][] SAMPLE_ALLOC = {
            {"1", "0", "0", "2"}, {"2", "0", "1", "2"}, {"1", "3", "2", "3"},
            {"1", "3", "2", "0"}, {"2", "0", "1", "3"}};
    private static final String[][] SAMPLE_MAX = {
            {"1", "2", "0", "7"}, {"2", "2", "1", "6"}, {"2", "3", "4", "3"},
            {"4", "3", "4", "4"}, {"5", "2", "5", "4"}};

    /** Deadlock sample: nothing can finish, so the system is NOT safe. */
    private static final String[] UNSAFE_AVAIL = {"0", "0", "1"};
    private static final String[][] UNSAFE_ALLOC = {{"1", "0", "0"}, {"0", "1", "0"}, {"0", "0", "1"}};
    private static final String[][] UNSAFE_MAX = {{"1", "2", "2"}, {"2", "1", "2"}, {"2", "2", "1"}};

    private int procs = 5, resources = 4;
    private String[] availText = blankRow(4);
    private String[][] allocText = blankGrid(5, 4);
    private String[][] maxText = blankGrid(5, 4);

    private transient RoundField[] availF = new RoundField[0];
    private transient RoundField[][] allocF = new RoundField[0][0];
    private transient RoundField[][] maxF = new RoundField[0][0];

    private boolean hasRun = false;
    private final transient Deque<Snapshot> undoStack = new ArrayDeque<>();
    private final transient List<HistoryItem> history = new ArrayList<>();

    private record Snapshot(int procs, int resources, String[] avail, String[][] alloc, String[][] max) {}
    private record HistoryItem(String label, Snapshot state) {}

    @Override public String title()    { return "Banker's Algorithm"; }
    @Override public String subtitle() { return "Calculate Need and discover a safe sequence."; }
    @Override protected String crumb() { return "BANKER"; }
    @Override protected String lead()  { return "Test whether every process can finish without deadlock."; }
    @Override protected int inputsTop() { return 4; }

    // ============================ INPUTS ============================

    @Override protected void buildInputs(JPanel p) {
        saveFields();

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setAlignmentX(LEFT_ALIGNMENT);
        stack.add(wrapRow(16,
                availableBox(),
                matrixBox("ALLOCATION", allocText, true),
                matrixBox("MAXIMUM", maxText, false)));
        stack.add(Box.createVerticalStrut(10));
        stack.add(structureRow());
        p.add(stack, BorderLayout.CENTER);
        p.add(toolbar(), BorderLayout.SOUTH);
    }

    /** "AVAILABLE" box: one small field per resource type. */
    private JComponent availableBox() {
        Box12 b = new Box12(null);
        b.setLayout(new BoxLayout(b, BoxLayout.Y_AXIS));
        b.add(pixelText("AVAILABLE", 7f, Theme.green(), 0f));
        b.add(Box.createVerticalStrut(8));

        JPanel ids = hRow(), vals = hRow();
        availF = new RoundField[resources];
        for (int j = 0; j < resources; j++) {
            availF[j] = new RoundField(availText[j], 6, 50, 32);
            availF[j].addActionListener(e -> onRunPressed());
            if (j > 0) { ids.add(Box.createHorizontalStrut(6)); vals.add(Box.createHorizontalStrut(6)); }
            ids.add(cell("R" + (j + 1), 50, 22, Theme.muted()));
            vals.add(availF[j]);
        }
        b.add(ids); b.add(Box.createVerticalStrut(6));
        b.add(vals); b.add(Box.createVerticalStrut(8));
        b.add(body("One value per resource", 11, Theme.muted()));
        return b;
    }

    /** An n x m grid of small fields with P# row labels and R# column headers. */
    private JComponent matrixBox(String caption, String[][] data, boolean isAlloc) {
        Box12 b = new Box12(null);
        b.setLayout(new BoxLayout(b, BoxLayout.Y_AXIS));
        b.add(pixelText(caption, 7f, Theme.text(), 0f));
        b.add(Box.createVerticalStrut(8));

        JPanel ids = hRow();
        ids.add(cell("", 34, 22, Theme.muted()));
        for (int j = 0; j < resources; j++) {
            ids.add(Box.createHorizontalStrut(6));
            ids.add(cell("R" + (j + 1), 50, 22, Theme.muted()));
        }
        b.add(ids);

        RoundField[][] fields = new RoundField[procs][resources];
        for (int i = 0; i < procs; i++) {
            b.add(Box.createVerticalStrut(6));
            JPanel row = hRow();
            row.add(cell("P" + (i + 1), 34, 32, Theme.muted()));
            for (int j = 0; j < resources; j++) {
                fields[i][j] = new RoundField(data[i][j], 6, 50, 32);
                fields[i][j].addActionListener(e -> onRunPressed());
                row.add(Box.createHorizontalStrut(6));
                row.add(fields[i][j]);
            }
            b.add(row);
        }
        if (isAlloc) allocF = fields; else maxF = fields;
        return b;
    }

    /** Grid-size controls: how many processes and how many resource types. */
    private JComponent structureRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        DashedPill addP = new DashedPill("+ Add process");
        addP.addActionListener(e -> resize(procs + 1, resources, "Added process P" + (procs + 1)));
        DashedPill delP = new DashedPill("- Remove process");
        delP.addActionListener(e -> resize(procs - 1, resources, "Removed process P" + procs));
        delP.setEnabled(procs > 1);
        DashedPill addR = new DashedPill("+ Resource");
        addR.addActionListener(e -> resize(procs, resources + 1, "Added resource R" + (resources + 1)));
        DashedPill delR = new DashedPill("- Resource");
        delR.addActionListener(e -> resize(procs, resources - 1, "Removed resource R" + resources));
        delR.setEnabled(resources > 1);

        row.add(addP); row.add(delP); row.add(addR); row.add(delR);
        row.add(body(procs + (procs == 1 ? " process / " : " processes / ")
                + resources + (resources == 1 ? " resource type" : " resource types"), 11, Theme.pink()));
        return row;
    }

    /** Sample / Large / Random / History / Undo / Clear pills. */
    private JComponent toolbar() {
        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        tools.setOpaque(false);
        tools.setBorder(new EmptyBorder(6, 0, 0, 0));

        DashedPill sample = new DashedPill("Sample data (SAFE)");
        sample.addActionListener(e -> {
            pushUndo();
            load(5, 4, SAMPLE_AVAIL, SAMPLE_ALLOC, SAMPLE_MAX);
            showToast(this, "Loaded the 5 x 4 sample - press RUN SIMULATION");
        });

        DashedPill unsafe = new DashedPill("Deadlock sample");
        unsafe.addActionListener(e -> {
            pushUndo();
            load(3, 3, UNSAFE_AVAIL, UNSAFE_ALLOC, UNSAFE_MAX);
            showToast(this, "Loaded a NOT SAFE case - press RUN SIMULATION");
        });

        DashedPill large = new DashedPill("Large sample (10 x 5)");
        large.addActionListener(e -> {
            pushUndo();
            loadRandom(10, 5, new Random(7));
            showToast(this, "Loaded 10 processes x 5 resources - press RUN SIMULATION");
        });

        DashedPill random = new DashedPill("Random");
        random.addActionListener(e -> {
            pushUndo();
            loadRandom(procs, resources, new Random());
            showToast(this, "Generated random data - press RUN SIMULATION");
        });

        DashedPill hist = new DashedPill("History");
        hist.addActionListener(e -> showHistoryMenu(hist));

        DashedPill undo = new DashedPill("Undo");
        undo.addActionListener(e -> {
            if (undoStack.isEmpty()) { showToast(this, "Nothing to undo"); return; }
            restore(undoStack.pop());
            showToast(this, "Undid last change");
        });

        DashedPill clear = new DashedPill("Clear");
        clear.addActionListener(e -> {
            pushUndo();
            availText = blankRow(resources);
            allocText = blankGrid(procs, resources);
            maxText = blankGrid(procs, resources);
            discardFields();
            hasRun = false;
            rebuildInputs();
            rerun();
            showToast(this, "Cleared all cells");
        });

        tools.add(sample); tools.add(unsafe); tools.add(large);
        tools.add(random); tools.add(hist); tools.add(undo); tools.add(clear);
        return tools;
    }

    // ---------- input plumbing ----------

    private static String[] blankRow(int m) {
        String[] r = new String[m];
        java.util.Arrays.fill(r, "");
        return r;
    }

    private static String[][] blankGrid(int n, int m) {
        String[][] g = new String[n][];
        for (int i = 0; i < n; i++) g[i] = blankRow(m);
        return g;
    }

    /**
     * Forgets the on-screen fields so the next rebuild takes the text arrays as they are.
     * Call this straight after replacing the text arrays, otherwise the rebuild would call
     * saveFields() first and copy the old (empty) cells back over the new data.
     */
    private void discardFields() {
        availF = new RoundField[0];
        allocF = new RoundField[0][0];
        maxF = new RoundField[0][0];
    }

    /** Copies whatever is typed in the fields back into the text arrays. */
    private void saveFields() {
        if (availF.length == resources) {
            for (int j = 0; j < resources; j++) availText[j] = availF[j].getText().trim();
        }
        if (allocF.length == procs && maxF.length == procs) {
            for (int i = 0; i < procs; i++) {
                if (allocF[i].length != resources || maxF[i].length != resources) return;
                for (int j = 0; j < resources; j++) {
                    allocText[i][j] = allocF[i][j].getText().trim();
                    maxText[i][j] = maxF[i][j].getText().trim();
                }
            }
        }
    }

    /** Grows or shrinks the grids, keeping everything the person already typed. */
    private void resize(int newProcs, int newRes, String toastMsg) {
        if (newProcs < 1 || newRes < 1) return;
        saveFields();
        pushUndo();
        String[] av = blankRow(newRes);
        String[][] al = blankGrid(newProcs, newRes), mx = blankGrid(newProcs, newRes);
        for (int j = 0; j < Math.min(resources, newRes); j++) av[j] = availText[j];
        for (int i = 0; i < Math.min(procs, newProcs); i++)
            for (int j = 0; j < Math.min(resources, newRes); j++) {
                al[i][j] = allocText[i][j];
                mx[i][j] = maxText[i][j];
            }
        procs = newProcs; resources = newRes;
        availText = av; allocText = al; maxText = mx;
        discardFields();
        hasRun = false;
        rebuildInputs();
        rerun();
        showToast(this, toastMsg);
    }

    private void load(int n, int m, String[] av, String[][] al, String[][] mx) {
        procs = n; resources = m;
        availText = av.clone();
        allocText = copy(al); maxText = copy(mx);
        discardFields();
        hasRun = false;
        rebuildInputs();
        rerun();
    }

    private void loadRandom(int n, int m, Random rng) {
        String[] av = new String[m];
        String[][] al = new String[n][m], mx = new String[n][m];
        for (int j = 0; j < m; j++) av[j] = String.valueOf(rng.nextInt(4) + 1);
        for (int i = 0; i < n; i++)
            for (int j = 0; j < m; j++) {
                int a = rng.nextInt(4);
                al[i][j] = String.valueOf(a);
                mx[i][j] = String.valueOf(a + rng.nextInt(4));   // Maximum is never below Allocation
            }
        load(n, m, av, al, mx);
    }

    private static String[][] copy(String[][] g) {
        String[][] out = new String[g.length][];
        for (int i = 0; i < g.length; i++) out[i] = g[i].clone();
        return out;
    }

    private Snapshot snapshot() {
        return new Snapshot(procs, resources, availText.clone(), copy(allocText), copy(maxText));
    }

    private void pushUndo() {
        saveFields();
        undoStack.push(snapshot());
        if (undoStack.size() > 15) undoStack.removeLast();
    }

    private void restore(Snapshot s) {
        procs = s.procs(); resources = s.resources();
        availText = s.avail().clone();
        allocText = copy(s.alloc()); maxText = copy(s.max());
        discardFields();
        hasRun = false;
        rebuildInputs();
        rerun();
    }

    private void recordHistory(String label) {
        Snapshot s = snapshot();
        history.removeIf(h -> h.label().equals(label));
        history.add(0, new HistoryItem(label, s));
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
            mi.addActionListener(e -> { restore(item.state()); showToast(this, "Restored from history"); });
            menu.add(mi);
        }
        menu.show(invoker, 0, invoker.getHeight());
    }

    // ============================ RESULTS ============================

    @Override protected void onRunPressed() {
        saveFields();
        hasRun = true;
        rerun();
    }

    @Override protected void runSimulation(JPanel r) {
        saveFields();
        clearInvalidMarks();

        if (everythingBlank()) {
            r.add(outputHeader(null));
            r.add(Box.createVerticalStrut(12));
            r.add(Notice.prompt("Fill in Available, Allocation and Maximum - "
                    + "or click Sample data for the 5 x 4 example, then press RUN SIMULATION."));
            return;
        }
        if (!hasRun) {
            r.add(outputHeader(null));
            r.add(Box.createVerticalStrut(12));
            r.add(Notice.prompt("Inputs ready - press RUN SIMULATION (or Enter) to see the Need table and the safety check."));
            return;
        }

        List<String> problems = new ArrayList<>();
        int[] available = readRow(availF, availText, "Available", problems);
        int[][] alloc = readGrid(allocF, allocText, "Allocation", problems);
        int[][] max = readGrid(maxF, maxText, "Maximum", problems);

        if (problems.isEmpty()) {
            for (int i = 0; i < procs; i++)
                for (int j = 0; j < resources; j++)
                    if (alloc[i][j] > max[i][j]) {
                        allocF[i][j].setInvalid(true);
                        maxF[i][j].setInvalid(true);
                        problems.add(String.format("P%d holds %d of R%d but its Maximum is only %d - "
                                + "Allocation can never be more than Maximum.", i + 1, alloc[i][j], j + 1, max[i][j]));
                    }
        }
        if (!problems.isEmpty()) {
            r.add(outputHeader(null));
            r.add(Box.createVerticalStrut(12));
            r.add(new Notice(false, "Input check", problems.get(0)));
            for (int i = 1; i < Math.min(problems.size(), 4); i++) {
                r.add(Box.createVerticalStrut(6));
                r.add(new Notice(false, "Also", problems.get(i)));
            }
            if (problems.size() > 4) {
                r.add(Box.createVerticalStrut(6));
                r.add(body("... and " + (problems.size() - 4) + " more cell(s) to fix.", 12, Theme.muted()));
            }
            return;
        }

        int[][] need = new int[procs][resources];
        for (int i = 0; i < procs; i++)
            for (int j = 0; j < resources; j++) need[i][j] = max[i][j] - alloc[i][j];

        Safety safety = checkSafety(available, alloc, need);
        recordHistory(String.format("%d x %d / %s", procs, resources,
                safety.safe ? "SAFE (" + sequenceText(safety.order) + ")" : "NOT SAFE"));

        // Header: title + the formula and the Available vector that was given
        r.add(outputHeader("Need = Maximum - Allocation  ·  AVAILABLE given: " + plain(available)));
        r.add(Box.createVerticalStrut(12));

        // Maximum | Allocation | Need  beside  the AVAILABLE ladder
        MatrixTable matrix = new MatrixTable(resources, max, alloc, need);
        AvailableTable ladder = new AvailableTable(available, safety);
        r.add(splitRow(matrix, ladder, 1 + 3 * resources, 1.9 + resources, 14, 760));
        r.add(Box.createVerticalStrut(14));

        // Verdict: a ticked box for whichever outcome is true, an empty box for the other
        JPanel verdict = new JPanel(new FlowLayout(FlowLayout.LEFT, 26, 0));
        verdict.setOpaque(false);
        verdict.setAlignmentX(LEFT_ALIGNMENT);
        verdict.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        verdict.add(new VerdictCheck(safety.safe,
                "SAFE SEQUENCE < " + sequenceText(safety.order) + " >", Theme.green()));
        verdict.add(new VerdictCheck(!safety.safe, "NOT SAFE", Theme.pink()));
        r.add(verdict);

        if (!safety.safe) {
            r.add(Box.createVerticalStrut(10));
            r.add(body("Available stopped at " + plain(safety.finalWork)
                    + " and no remaining process has a Need row that fits inside it.", 13, Theme.text()));
            r.add(Box.createVerticalStrut(6));
            StringBuilder why = new StringBuilder("Still waiting: ");
            int listed = Math.min(safety.blocked.size(), 4);
            for (int k = 0; k < listed; k++) {
                if (k > 0) why.append(", ");
                int i = safety.blocked.get(k);
                why.append("P").append(i + 1).append(" needs ").append(plain(need[i]));
            }
            if (safety.blocked.size() > listed) why.append(" and ").append(safety.blocked.size() - listed).append(" more");
            why.append(".");
            r.add(body(why.toString(), 12, Theme.muted()));
        }

        r.add(Box.createVerticalStrut(12));
        DashedPill copy = new DashedPill("Copy summary");
        copy.addActionListener(e -> {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Banker's Algorithm (%d processes x %d resource types)%n", procs, resources));
            sb.append("Available: ").append(plain(available)).append("\n");
            for (int i = 0; i < procs; i++)
                sb.append(String.format("P%d  Max %s  Alloc %s  Need %s%n",
                        i + 1, plain(max[i]), plain(alloc[i]), plain(need[i])));
            sb.append("Available after each step: start ").append(plain(available));
            for (SafeStep s : safety.steps)
                sb.append(" -> +P").append(s.process + 1).append(" ").append(plain(s.workAfter));
            sb.append("\n");
            sb.append(safety.safe ? "SAFE - sequence: " + sequenceText(safety.order) : "NOT SAFE - no safe sequence");
            sb.append("\n");
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
            showToast(this, "Copied summary to clipboard!");
        });
        r.add(copy);
    }

    /** "SIMULATION OUTPUT · SAFETY CHECK" with an optional note after it. */
    private JComponent outputHeader(String note) {
        JPanel h = hRow();
        h.add(pixelText("SIMULATION OUTPUT · SAFETY CHECK", 7f, Theme.dark ? Theme.cyan() : Theme.text(), 0f));
        if (note != null) {
            h.add(Box.createHorizontalStrut(16));
            h.add(body(note, 12, Theme.muted()));
        }
        return pad(h, 0, 0, false);
    }

    // ---------- reading + validating the grids ----------

    private void clearInvalidMarks() {
        for (RoundField f : availF) if (f != null) f.setInvalid(false);
        for (RoundField[] row : allocF) for (RoundField f : row) if (f != null) f.setInvalid(false);
        for (RoundField[] row : maxF) for (RoundField f : row) if (f != null) f.setInvalid(false);
    }

    private boolean everythingBlank() {
        for (String s : availText) if (!s.isEmpty()) return false;
        for (String[] row : allocText) for (String s : row) if (!s.isEmpty()) return false;
        for (String[] row : maxText) for (String s : row) if (!s.isEmpty()) return false;
        return true;
    }

    private int[] readRow(RoundField[] fields, String[] text, String what, List<String> problems) {
        int[] out = new int[resources];
        for (int j = 0; j < resources; j++) {
            Integer v = whole(text[j]);
            if (v == null) {
                if (j < fields.length && fields[j] != null) fields[j].setInvalid(true);
                problems.add(what + " R" + (j + 1) + " " + why(text[j]));
                continue;
            }
            out[j] = v;
        }
        return out;
    }

    private int[][] readGrid(RoundField[][] fields, String[][] text, String what, List<String> problems) {
        int[][] out = new int[procs][resources];
        for (int i = 0; i < procs; i++)
            for (int j = 0; j < resources; j++) {
                Integer v = whole(text[i][j]);
                if (v == null) {
                    if (i < fields.length && j < fields[i].length && fields[i][j] != null) fields[i][j].setInvalid(true);
                    problems.add(what + " P" + (i + 1) + " R" + (j + 1) + " " + why(text[i][j]));
                    continue;
                }
                out[i][j] = v;
            }
        return out;
    }

    /** Biggest value a cell may hold, so adding up Available can never overflow. */
    private static final int MAX_UNITS = 1_000_000;

    /** The number when the text is a whole number from 0 to MAX_UNITS, otherwise null. */
    private static Integer whole(String s) {
        String t = s == null ? "" : s.trim();
        if (t.isEmpty()) return null;
        for (int i = 0; i < t.length(); i++) if (!Character.isDigit(t.charAt(i))) return null;
        try {
            long v = Long.parseLong(t);
            return v > MAX_UNITS ? null : (int) v;
        } catch (NumberFormatException e) { return null; }   // too many digits for a long
    }

    /** Says, in plain words, why a cell was not accepted. */
    private static String why(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty()) return "is blank - type a whole number of 0 or more.";
        if (t.startsWith("-")) return "cannot be negative - type a whole number of 0 or more.";
        for (int i = 0; i < t.length(); i++)
            if (!Character.isDigit(t.charAt(i)))
                return "must be a whole number - \"" + t + "\" is not.";
        return String.format("is too big - keep each value at %,d or less.", MAX_UNITS);
    }

    // ============================ THE ALGORITHM ============================

    /** One release: the process that was able to run, and Available before and after it. */
    static final class SafeStep {
        final int process;
        final int[] need, workBefore, allocation, workAfter;
        SafeStep(int process, int[] need, int[] workBefore, int[] allocation, int[] workAfter) {
            this.process = process; this.need = need;
            this.workBefore = workBefore; this.allocation = allocation; this.workAfter = workAfter;
        }
    }

    static final class Safety {
        boolean safe;
        int[] finalWork;
        final List<Integer> order = new ArrayList<>();      // process indexes, in the order they finish
        final List<Integer> blocked = new ArrayList<>();    // processes that could never finish
        final List<SafeStep> steps = new ArrayList<>();
    }

    /**
     * Classic safety algorithm: Work = Available; repeatedly look for an unfinished process whose
     * Need fits inside Work, pretend it runs and releases its Allocation (Work += Allocation), repeat.
     * The state is safe when every process can be finished this way.
     */
    static Safety checkSafety(int[] available, int[][] alloc, int[][] need) {
        int n = need.length, m = available.length;
        Safety s = new Safety();
        int[] work = available.clone();
        boolean[] finished = new boolean[n];
        int done = 0;
        boolean progress = true;
        while (progress && done < n) {
            progress = false;
            for (int i = 0; i < n; i++) {
                if (finished[i] || !fits(need[i], work)) continue;
                int[] before = work.clone();
                for (int j = 0; j < m; j++) work[j] += alloc[i][j];
                s.steps.add(new SafeStep(i, need[i].clone(), before, alloc[i].clone(), work.clone()));
                s.order.add(i);
                finished[i] = true;
                done++;
                progress = true;
            }
        }
        s.safe = done == n;
        s.finalWork = work;
        for (int i = 0; i < n; i++) if (!finished[i]) s.blocked.add(i);
        return s;
    }

    private static boolean fits(int[] need, int[] work) {
        for (int j = 0; j < need.length; j++) if (need[j] > work[j]) return false;
        return true;
    }

    /** "3 2 2 1" */
    static String plain(int[] v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(v[i]);
        }
        return sb.toString();
    }

    private static String sequenceText(List<Integer> order) {
        if (order.isEmpty()) return "none";
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < order.size(); k++) {
            if (k > 0) sb.append(", ");
            sb.append("P").append(order.get(k) + 1);
        }
        return sb.toString();
    }

    // ============================ SMALL LAYOUT HELPERS ============================

    private static JPanel hRow() {
        JPanel r = new JPanel();
        r.setOpaque(false);
        r.setLayout(new BoxLayout(r, BoxLayout.X_AXIS));
        r.setAlignmentX(LEFT_ALIGNMENT);
        return r;
    }

    /** Fixed-size centred Silkscreen label (the P# and R# headings of a grid). */
    private static JLabel cell(String s, int w, int h, Color c) {
        JLabel l = new JLabel(s, SwingConstants.CENTER);
        l.setFont(Fonts.label(9f));
        l.setForeground(c);
        Dimension d = new Dimension(w, h);
        l.setPreferredSize(d); l.setMinimumSize(d); l.setMaximumSize(d);
        return l;
    }

    /** Lays boxes out left to right at their own width, wrapping to a new line when there is no room. */
    private static JPanel wrapRow(int gap, JComponent... items) {
        JPanel grid = new JPanel(null) {
            private static final long serialVersionUID = 1L;
            private int measure(int width, boolean apply) {
                int x = 0, y = 0, rowH = 0;
                for (JComponent c : items) {
                    Dimension d = c.getPreferredSize();
                    if (x > 0 && x + d.width > width) { x = 0; y += rowH + gap; rowH = 0; }
                    if (apply) c.setBounds(x, y, d.width, d.height);
                    x += d.width + gap;
                    rowH = Math.max(rowH, d.height);
                }
                return y + rowH;
            }
            @Override public void doLayout() { measure(Math.max(1, getWidth()), true); }
            @Override public Dimension getPreferredSize() {
                return new Dimension(300, measure(getWidth() > 0 ? getWidth() : 1100, false));
            }
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        grid.setOpaque(false);
        grid.setAlignmentX(LEFT_ALIGNMENT);
        for (JComponent c : items) grid.add(c);
        grid.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { grid.revalidate(); }
        });
        return grid;
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

    /** Shared paint for the two result tables: rounded card, header band, zebra rows. */
    private static void paintCard(Graphics2D g2, int w, int h, int headTop, int rowTop, int rowH, int rows,
                                  int hovered, Runnable dividers) {
        RoundRectangle2D box = new RoundRectangle2D.Double(0.75, 0.75, w - 1.5, h - 1.5, 18, 18);
        Shape oldClip = g2.getClip();
        g2.clip(box);
        g2.setColor(Theme.panel2());
        g2.fillRect(0, 0, w, headTop + rowTop);
        for (int i = 0; i < rows; i++) {
            Color yellow = Theme.yellow();
            g2.setColor(i == hovered
                    ? new Color(yellow.getRed(), yellow.getGreen(), yellow.getBlue(), Theme.dark ? 55 : 95)
                    : (i % 2 == 0 ? Theme.panel() : Theme.panel2()));
            g2.fillRect(0, headTop + rowTop + i * rowH, w, rowH);
        }
        dividers.run();
        g2.setClip(oldClip);
        g2.setColor(Theme.line());
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(box);
    }

    // ============================ RESULT COMPONENTS ============================

    /** P# + Maximum + Allocation + Need, with a grouped two-row header. Need is drawn in pink. */
    static class MatrixTable extends JComponent {
        private static final long serialVersionUID = 1L;
        static final int GROUP_H = 24, HEAD_H = 22, ROW = 29, ID_W = 44;
        private final int m;
        private final transient int[][] max, alloc, need;
        private int hovered = -1;

        MatrixTable(int m, int[][] max, int[][] alloc, int[][] need) {
            this.m = m; this.max = max; this.alloc = alloc; this.need = need;
            setAlignmentX(LEFT_ALIGNMENT);
            ToolTipManager.sharedInstance().registerComponent(this);
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int idx = (e.getY() - (GROUP_H + HEAD_H)) / ROW;
                    int next = (idx >= 0 && idx < alloc.length) ? idx : -1;
                    if (next != hovered) { hovered = next; repaint(); }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent e) {
                    if (hovered != -1) { hovered = -1; repaint(); }
                }
            });
        }

        @Override public String getToolTipText(MouseEvent e) {
            int i = (e.getY() - (GROUP_H + HEAD_H)) / ROW;
            if (i < 0 || i >= alloc.length) return null;
            return String.format("P%d:  Need %s = Max %s - Alloc %s",
                    i + 1, plain(need[i]), plain(max[i]), plain(alloc[i]));
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension(Math.max(320, ID_W + m * 3 * 40), GROUP_H + HEAD_H + ROW * alloc.length + 2);
        }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getPreferredSize().height;
            double colW = (w - 3 - ID_W) / (double) (m * 3);
            double groupW = colW * m;
            double[] groupX = {1.5 + ID_W, 1.5 + ID_W + groupW, 1.5 + ID_W + 2 * groupW};
            String[] groups = {"MAXIMUM", "ALLOCATION", "NEED"};

            paintCard(g2, w, h, GROUP_H, HEAD_H, ROW, alloc.length, hovered, () -> {
                // NEED group gets a pink wash so it reads as the computed column
                Color pink = Theme.pink();
                g2.setColor(new Color(pink.getRed(), pink.getGreen(), pink.getBlue(), Theme.dark ? 30 : 26));
                g2.fillRect((int) Math.round(groupX[2]), 0, (int) Math.round(groupW), h);
                g2.setColor(Theme.line());
                g2.setStroke(new BasicStroke(1f));
                g2.drawLine((int) Math.round(1.5 + ID_W), 0, (int) Math.round(1.5 + ID_W), h);
                for (int c = 1; c < m * 3; c++) {
                    int x = (int) Math.round(1.5 + ID_W + c * colW);
                    boolean groupEdge = c % m == 0;
                    g2.setStroke(new BasicStroke(groupEdge ? 1.6f : 1f));
                    g2.setColor(groupEdge ? Theme.inputLine() : Theme.line());
                    g2.drawLine(x, groupEdge ? 0 : GROUP_H, x, h);
                }
                g2.setColor(Theme.line());
                g2.setStroke(new BasicStroke(1f));
                g2.drawLine(0, GROUP_H, w, GROUP_H);
            });

            g2.setFont(Fonts.label(9f));
            for (int gi = 0; gi < 3; gi++) {
                g2.setColor(gi == 2 ? Theme.pink() : Theme.text());
                centerText(g2, groups[gi], groupX[gi] + groupW / 2, middle(g2, GROUP_H / 2.0));
            }
            g2.setFont(Fonts.label(8f));
            for (int c = 0; c < m * 3; c++) {
                g2.setColor(c / m == 2 ? Theme.pink() : Theme.muted());
                centerText(g2, "R" + (c % m + 1), 1.5 + ID_W + (c + 0.5) * colW, middle(g2, GROUP_H + HEAD_H / 2.0));
            }

            for (int i = 0; i < alloc.length; i++) {
                double midY = middle(g2, GROUP_H + HEAD_H + i * ROW + ROW / 2.0);
                g2.setFont(Fonts.label(9f));
                g2.setColor(Theme.muted());
                centerText(g2, "P" + (i + 1), 1.5 + ID_W / 2.0, midY);
                g2.setFont(Fonts.body(13f));
                for (int c = 0; c < m * 3; c++) {
                    int grp = c / m, j = c % m;
                    int v = grp == 0 ? max[i][j] : grp == 1 ? alloc[i][j] : need[i][j];
                    g2.setColor(grp == 2 ? Theme.pink() : Theme.text());
                    centerText(g2, String.valueOf(v), 1.5 + ID_W + (c + 0.5) * colW, midY);
                }
            }
            g2.dispose();
        }
    }

    /** The AVAILABLE ladder: "start", then the Available vector after each process is released. */
    static class AvailableTable extends JComponent {
        private static final long serialVersionUID = 1L;
        static final int GROUP_H = 24, HEAD_H = 22, ROW = 29, ID_W = 56;
        private final transient int[] start;
        private final transient Safety safety;
        private int hovered = -1;

        AvailableTable(int[] start, Safety safety) {
            this.start = start;
            this.safety = safety;
            setAlignmentX(LEFT_ALIGNMENT);
            ToolTipManager.sharedInstance().registerComponent(this);
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int idx = (e.getY() - (GROUP_H + HEAD_H)) / ROW;
                    int next = (idx >= 0 && idx < rows()) ? idx : -1;
                    if (next != hovered) { hovered = next; repaint(); }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent e) {
                    if (hovered != -1) { hovered = -1; repaint(); }
                }
            });
        }

        private int rows() { return safety.steps.size() + 1; }

        @Override public String getToolTipText(MouseEvent e) {
            int i = (e.getY() - (GROUP_H + HEAD_H)) / ROW;
            if (i <= 0 || i > safety.steps.size()) return i == 0 ? "The Available vector that was typed in" : null;
            SafeStep s = safety.steps.get(i - 1);
            return String.format("P%d finished: Available %s + its Allocation %s = %s",
                    s.process + 1, plain(s.workBefore), plain(s.allocation), plain(s.workAfter));
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension(Math.max(210, ID_W + start.length * 42), GROUP_H + HEAD_H + ROW * rows() + 2);
        }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getPreferredSize().height;
            int m = start.length;
            double colW = (w - 3 - ID_W) / (double) m;

            paintCard(g2, w, h, GROUP_H, HEAD_H, ROW, rows(), hovered, () -> {
                g2.setColor(Theme.line());
                g2.setStroke(new BasicStroke(1f));
                g2.drawLine((int) Math.round(1.5 + ID_W), 0, (int) Math.round(1.5 + ID_W), h);
                for (int c = 1; c < m; c++) {
                    int x = (int) Math.round(1.5 + ID_W + c * colW);
                    g2.drawLine(x, GROUP_H, x, h);
                }
                g2.drawLine(0, GROUP_H, w, GROUP_H);
            });

            g2.setFont(Fonts.label(9f));
            g2.setColor(Theme.green());
            centerText(g2, "AVAILABLE", 1.5 + ID_W + (w - 3 - ID_W) / 2.0, middle(g2, GROUP_H / 2.0));
            g2.setFont(Fonts.label(8f));
            g2.setColor(Theme.muted());
            for (int c = 0; c < m; c++)
                centerText(g2, "R" + (c + 1), 1.5 + ID_W + (c + 0.5) * colW, middle(g2, GROUP_H + HEAD_H / 2.0));

            boolean lastIsFinal = safety.safe;
            for (int i = 0; i < rows(); i++) {
                double midY = middle(g2, GROUP_H + HEAD_H + i * ROW + ROW / 2.0);
                int[] vec = i == 0 ? start : safety.steps.get(i - 1).workAfter;
                String label = i == 0 ? "start" : "+P" + (safety.steps.get(i - 1).process + 1);

                g2.setFont(Fonts.body(13f));
                g2.setColor(i == 0 ? Theme.muted() : Theme.green());
                centerText(g2, label, 1.5 + ID_W / 2.0, midY);

                boolean finalRow = lastIsFinal && i == rows() - 1;
                g2.setColor(finalRow ? Theme.green() : Theme.text());
                for (int c = 0; c < m; c++)
                    centerText(g2, String.valueOf(vec[c]), 1.5 + ID_W + (c + 0.5) * colW, midY);
            }
            g2.dispose();
        }
    }

    /**
     * A ticked box when this outcome is the verdict, an empty box when it is not.
     * No pill behind it - just the box and the words, as in the reference design.
     */
    static class VerdictCheck extends JComponent {
        private static final long serialVersionUID = 1L;
        static final int BOX = 19, GAP = 11, H = 30;
        private final boolean on;
        private final String label;
        private final transient Color accent;

        VerdictCheck(boolean on, String label, Color accent) {
            this.on = on; this.label = label; this.accent = accent;
            setAlignmentX(LEFT_ALIGNMENT);
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension(BOX + GAP + getFontMetrics(Fonts.body(14f)).stringWidth(label) + 4, H);
        }
        @Override public Dimension getMaximumSize() { return getPreferredSize(); }
        @Override public Dimension getMinimumSize() { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            double cy = getHeight() / 2.0;
            RoundRectangle2D box = new RoundRectangle2D.Double(0.75, cy - BOX / 2.0, BOX - 1.5, BOX - 1.5, 5, 5);
            if (on) {
                g2.setColor(accent);
                g2.fill(box);
                g2.setColor(Theme.dark ? Theme.bg() : Color.WHITE);
                g2.setStroke(new BasicStroke(2.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Line2D.Double(5.0, cy, 8.0, cy + 3.5));
                g2.draw(new Line2D.Double(8.0, cy + 3.5, 14.0, cy - 4.0));
            } else {
                g2.setColor(Theme.inputLine());
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(box);
            }
            g2.setFont(Fonts.body(14f));
            g2.setColor(on ? Theme.text() : Theme.muted());
            g2.drawString(label, BOX + GAP, (float) middle(g2, cy));
            g2.dispose();
        }
    }
}
