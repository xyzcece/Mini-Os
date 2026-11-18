package modules.paging;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import modules.common.EntryGrid;
import modules.common.FigmaModule;
import shared.Fonts;
import shared.Theme;

/**
 * PAGING LAB  —  OWNER: Member 1
 * Part 1 · Page table size:  number of page table entries = address space ÷ page size
 *   CHECK: 1 GB ÷ 4 KB = 262,144 entries
 * Part 2 · Page-to-frame mapping + logical-to-physical address translation
 *   [x] Page table input: one FRAME per page ("-" = not in memory), boxes up to 7 pages, a table for 8+,
 *       +1 / +5 / −1 / [count] Set / Paste / Bulk - any number of pages
 *   [x] page # = logical ÷ page size, offset = logical mod page size, physical = frame × page size + offset
 *   [x] Traps: page number past the end of the table, page not in memory (page fault)
 *   [x] Validation: blank, letters, negatives, page size 0, the same frame used twice - bad cells turn red
 *   CHECK: page size 4, frames 5 6 1 2, logical 13 -> page 3, offset 1 -> frame 2 -> physical 9
 * Toolbar: Sample data, Not-exact / out-of-range sample, Page fault sample, Large sample, Random,
 *          Invalid input test, History (recent runs), Undo, Clear
 *
 * Layout follows the Figma "Paging Lab" frames (mini-os file, light + dark).
 */
public class PagingModule extends FigmaModule {
    private static final long serialVersionUID = 1L;
    private static final Pattern SIZE = Pattern.compile("([0-9]*\\.?[0-9]+)\\s*([KMG]B?)?");
    /** Practice set from class: {address space, page size}. Solutions are worked out below. */
    static final String[][] PRACTICE = {{"1 GB", "4 KB"}, {"2 GB", "8 KB"}, {"512 MB", "4 KB"}};

    /** Textbook example: page size 4 bytes, page table 5 6 1 2, logical address 13 -> physical 9. */
    static final String[][] SAMPLE_FRAMES = {{"5"}, {"6"}, {"1"}, {"2"}};
    /** Page 1 is not in memory ("-"), so logical address 6 (page 1) causes a page fault. */
    static final String[][] FAULT_FRAMES = {{"5"}, {"-"}, {"1"}, {"2"}};
    /** Exceptional case: a letter, a negative frame, frame 3 used twice. */
    static final String[][] BAD_FRAMES = {{"3"}, {"abc"}, {"3"}, {"-2"}};

    private JTextField addressSpace, pageSize;
    private RoundField tPageField, logicalField;
    private String tPageText = "", logicalText = "";
    /** Page table: one row per page, the only column is its FRAME number. Starts with 4 empty pages. */
    private final transient EntryGrid frames = new EntryGrid("PAGE", new String[]{"FRAME"}, String::valueOf, 56, this::onRunPressed);
    { frames.resize(4); }

    /** Everything the person typed, for Undo and History. */
    private record State(String space, String page, String tPage, String logical, List<String[]> frames) {}
    private final transient InputHistory<State> history = new InputHistory<>();
    private static final String[] UNITS = {"KB", "MB", "GB"};

    @Override public String title()    { return "Paging Lab"; }
    @Override public String subtitle() { return "Page table size, page-to-frame mapping, and logical-to-physical address translation."; }
    @Override protected void onUndoPressed() { history.undo(this, snapshot(), this::restore); }
    @Override protected void onRedoPressed() { history.redo(this, snapshot(), this::restore); }

    @Override protected String crumb() { return "PAGING"; }
    @Override protected String lead()  { return "Size the page table, then map pages to frames and translate a logical address."; }

    // ============================ INPUTS ============================

    @Override protected void buildInputs(JPanel p) {
        saveFields();
        addressSpace = new RoundField(addressSpace == null ? "" : addressSpace.getText());
        pageSize     = new RoundField(pageSize     == null ? "" : pageSize.getText());
        JPanel sizeRow = new JPanel(new GridLayout(1, 2, 14, 0));
        sizeRow.setOpaque(false);
        sizeRow.setAlignmentX(LEFT_ALIGNMENT);
        sizeRow.add(field("Address space", addressSpace, null));
        sizeRow.add(field("Page size", pageSize, "Use KB"));

        tPageField = new RoundField(tPageText);
        logicalField = new RoundField(logicalText);
        tPageField.addActionListener(e -> onRunPressed());
        logicalField.addActionListener(e -> onRunPressed());
        JPanel addrRow = new JPanel(new GridLayout(1, 2, 14, 0));
        addrRow.setOpaque(false);
        addrRow.setAlignmentX(LEFT_ALIGNMENT);
        addrRow.add(field("Page size (bytes)", tPageField, "e.g. 4, 1024, 4096"));
        addrRow.add(field("Logical address", logicalField, "a byte address, 0 or more"));

        JComponent table = frames.box("PAGE TABLE", "page", "frame number per page · - = not in memory",
                "FRAME = where the page sits in physical memory", this::resizePages, this::bulkLoad);
        table.setAlignmentX(LEFT_ALIGNMENT);

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.add(section("1 · PAGE TABLE SIZE"));
        stack.add(Box.createVerticalStrut(8));
        stack.add(sizeRow);
        stack.add(Box.createVerticalStrut(16));
        stack.add(section("2 · PAGE-TO-FRAME MAPPING + ADDRESS TRANSLATION"));
        stack.add(Box.createVerticalStrut(8));
        stack.add(table);
        stack.add(Box.createVerticalStrut(12));
        stack.add(addrRow);
        p.add(stack, BorderLayout.CENTER);
        p.add(toolbar(), BorderLayout.SOUTH);
    }

    private static JComponent section(String s) {
        JLabel l = pixelText(s, 7f, Theme.dark ? Theme.cyan() : Theme.text(), 0f);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    /** Sample / Not-exact / Page fault / Large / Random / Invalid input / History / Undo / Clear pills. */
    private JComponent toolbar() {
        DashedPill hist = new DashedPill("History");
        hist.addActionListener(e -> history.showMenu(hist, this::restore));
        return pillRow(10,
                pill("Sample data (VALID)", () -> load(new State("1 GB", "4 KB", "4", "13", rows(SAMPLE_FRAMES)),
                        "Loaded 1 GB ÷ 4 KB and logical address 13")),
                pill("Not-exact / out-of-range", () -> load(new State("10 MB", "3 KB", "4", "17", rows(SAMPLE_FRAMES)),
                        "Loaded 10 MB ÷ 3 KB and logical 17 (past the last page)")),
                pill("Page fault sample", () -> load(new State("2 GB", "8 KB", "4", "6", rows(FAULT_FRAMES)),
                        "Loaded logical 6 - its page is not in memory")),
                pill("Large sample (64 GB / 20 pages)", () -> loadRandom(20, new Random(7), "64 GB", "4 KB",
                        "Loaded 64 GB ÷ 4 KB and a 20-page table")),
                pill("Random", () -> loadRandom(Math.max(1, frames.size()), new Random(), null, null,
                        "Generated a random page table and address")),
                pill("Invalid input test", () -> load(new State("4 KB", "1 GB", "0", "-5", rows(BAD_FRAMES)),
                        "Loaded bad input - the red cells show what to fix")),
                hist,
                pill("Undo", () -> onUndoPressed()),
                pill("Clear", () -> load(new State("", "", "", "", blank(frames.size())), "Cleared all fields")));
    }

    // ---------- input plumbing ----------

    private static List<String[]> rows(String[][] data) {
        List<String[]> l = new ArrayList<>();
        for (String[] r : data) l.add(r.clone());
        return l;
    }

    private static List<String[]> blank(int n) {
        List<String[]> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(new String[]{""});
        return l;
    }

    private void saveFields() {
        if (tPageField != null) tPageText = tPageField.getText().trim();
        if (logicalField != null) logicalText = logicalField.getText().trim();
        frames.save();
    }

    private State snapshot() {
        saveFields();
        return new State(addressSpace.getText(), pageSize.getText(), tPageText, logicalText, frames.copy());
    }

    /** Puts a snapshot back on screen and runs it. */
    private void restore(State st) {
        addressSpace.setText(st.space());
        pageSize.setText(st.page());
        tPageText = st.tPage();
        logicalText = st.logical();
        tPageField = logicalField = null;   // so buildInputs does not copy the old text back
        frames.setRows(st.frames());
        rebuildInputs();
        markInputsDirty();
    }

    private void load(State st, String toast) {
        history.push(snapshot());
        restore(st);
        showToast(this, toast);
    }

    /**
     * n pages placed in distinct random frames (one in eight left out of memory), page size 1 KB,
     * and a logical address that sometimes falls past the last page. space/page null = random power of two.
     */
    private void loadRandom(int n, Random rng, String space, String page, String toast) {
        List<Integer> free = new ArrayList<>();
        for (int f = 0; f < 2 * n; f++) free.add(f);
        Collections.shuffle(free, rng);
        List<String[]> t = new ArrayList<>();
        for (int i = 0; i < n; i++) t.add(new String[]{rng.nextInt(8) == 0 ? "-" : String.valueOf(free.get(i))});
        int ps = 1024;
        int logical = rng.nextInt(n * ps + ps / 2);
        if (space == null) {
            space = withUnit(1L << (16 + rng.nextInt(11)));   // 64 MB .. 64 GB
            page = withUnit(1L << rng.nextInt(7));             // 1 KB .. 64 KB
        }
        load(new State(space, page, String.valueOf(ps), String.valueOf(logical), t), toast);
    }

    private void resizePages(int n) {
        history.push(snapshot());
        frames.resize(n);
        rebuildInputs();
        showToast(this, frames.size() + (frames.size() == 1 ? " page" : " pages"));
    }

    private void bulkLoad(List<String[]> pasted) {
        history.push(snapshot());
        frames.setRows(pasted);
        rebuildInputs();
        markInputsDirty();
        showToast(this, "Imported " + pasted.size() + " pages — press RUN SIMULATION");
    }

    /** 1,048,576 KB -> "1 GB", 512 -> "512 KB". */
    static String withUnit(long kb) {
        int u = 0;
        while (u < UNITS.length - 1 && kb >= 1024 && kb % 1024 == 0) { kb /= 1024; u++; }
        return kb + " " + UNITS[u];
    }

    private JPanel field(String label, JTextField f, String hint) {
        JPanel c = new JPanel();
        c.setOpaque(false);
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.add(fieldLabel(label));
        c.add(Box.createVerticalStrut(6));
        c.add(f);
        c.add(Box.createVerticalStrut(6));
        c.add(text(hint == null ? " " : hint, 10, Theme.muted()));   // keeps both columns the same height
        return c;
    }

    // ============================ RESULTS ============================

    @Override protected void runSimulation(JPanel r) {
        saveFields();
        pageTableSize(r);
        r.add(Box.createVerticalStrut(22));
        translation(r);
    }

    /** Part 1: number of page table entries = address space ÷ page size. */
    private void pageTableSize(JPanel r) {
        r.add(pixelText("SIMULATION OUTPUT · PAGE TABLE ENTRIES", 7f, Theme.dark ? Theme.cyan() : Theme.text(), 0f));
        r.add(Box.createVerticalStrut(12));
        r.add(new FormulaStrip("Number of page table entries   =   Address space   ÷   Page size"));
        r.add(Box.createVerticalStrut(14));

        if (allBlank(addressSpace, pageSize)) {
            r.add(Notice.prompt("Type the address space (e.g. 1 GB) and the page size (e.g. 4 KB), then press RUN SIMULATION."));
            r.add(Box.createVerticalStrut(14));
            practice(r);
            return;
        }

        long spaceKb, pageKb;
        try {
            spaceKb = toKb(addressSpace.getText());
            pageKb  = toKb(pageSize.getText());
            if (pageKb > spaceKb) throw new IllegalArgumentException("page larger than address space");
        } catch (IllegalArgumentException e) {   // also covers NumberFormatException
            r.add(new Notice(false, "Input check", problem()));
            r.add(Box.createVerticalStrut(6));
            r.add(body("Rule: use a number + KB, MB or GB (e.g. 1 GB, 4 KB); the page size must be at least 1 KB "
                    + "and not bigger than the address space.", 12, Theme.muted()));
            r.add(Box.createVerticalStrut(14));
            practice(r);
            return;
        }
        long entries = spaceKb / pageKb;
        history.record(String.format("%s ÷ %s = %,d entries", addressSpace.getText().trim(), pageSize.getText().trim(), entries),
                snapshot());

        // address space ÷ page size = entries
        Row cards = new Row(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH; gc.weighty = 1;
        gc.weightx = 1; cards.add(new NumberCard("ADDRESS SPACE", String.format("%,d KB", spaceKb), working(addressSpace.getText()), false), gc);
        gc.weightx = 0; cards.add(operator("÷"), gc);
        gc.weightx = 1; cards.add(new NumberCard("PAGE SIZE", String.format("%,d KB", pageKb), working(pageSize.getText()), false), gc);
        gc.weightx = 0; cards.add(operator("="), gc);
        // the pixel font has no superscript digits, so the power of two is a raised <sup> number
        String note = Long.bitCount(entries) == 1 ? "<html>= 2<sup>" + Long.numberOfTrailingZeros(entries) + "</sup> entries</html>"
                : spaceKb % pageKb != 0 ? "rounded down (not an exact fit)" : "entries";
        gc.weightx = 1; cards.add(new NumberCard("PAGE TABLE ENTRIES", String.format("%,d", entries), note, true), gc);
        r.add(cards);
        r.add(Box.createVerticalStrut(14));

        JLabel ans = text(String.format("Ans.   %,d page table entries", entries), 17, Theme.dark ? Theme.green() : Theme.text());
        r.add(pad(ans, 0, 0, false));
        r.add(Box.createVerticalStrut(10));
        practice(r);
    }

    /** Part 2: page # and offset from the logical address, then the frame from the page table. */
    private void translation(JPanel r) {
        frames.clearInvalid();
        tPageField.setInvalid(false);
        logicalField.setInvalid(false);

        r.add(pixelText("SIMULATION OUTPUT · ADDRESS TRANSLATION", 7f, Theme.dark ? Theme.cyan() : Theme.text(), 0f));
        r.add(Box.createVerticalStrut(12));
        r.add(new FormulaStrip("page = logical ÷ page size   ·   offset = logical mod page size   ·   physical = frame × page size + offset"));
        r.add(Box.createVerticalStrut(14));

        List<String[]> rows = frames.rows();
        boolean nothingTyped = tPageText.isEmpty() && logicalText.isEmpty() && rows.stream().allMatch(x -> x[0].isEmpty());
        if (nothingTyped) {
            r.add(Notice.prompt("Fill in the page table (a frame for each page), the page size in bytes and a logical address, "
                    + "then press RUN SIMULATION."));
            return;
        }

        // read everything, collecting every problem and turning the bad cells red
        List<String> problems = new ArrayList<>();
        Integer ps = readWhole(tPageText, "the page size", 1, problems, tPageField);
        Integer la = readWhole(logicalText, "the logical address", 0, problems, logicalField);
        Integer[] frame = new Integer[rows.size()];   // null = not in memory ("-")
        Map<Integer, Integer> usedBy = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            String v = rows.get(i)[0];
            if (v.equals("-")) continue;
            String msg = null;
            if (v.isEmpty()) msg = "page " + i + " FRAME is blank (type a frame number, or - if the page is not in memory)";
            else {
                try {
                    frame[i] = Integer.parseInt(v);
                    if (frame[i] < 0) msg = "page " + i + " FRAME is " + frame[i] + " - frame numbers cannot be negative";
                } catch (NumberFormatException e) {
                    msg = "page " + i + " FRAME (\"" + v + "\") is not a whole number or -";
                }
            }
            if (msg == null && usedBy.containsKey(frame[i]))
                msg = "frame " + frame[i] + " is used by page " + usedBy.get(frame[i]) + " and page " + i + " - a frame holds only one page";
            if (msg != null) { problems.add(msg); frames.markInvalid(i, 0); frame[i] = null; continue; }
            usedBy.put(frame[i], i);
        }
        if (!problems.isEmpty()) {
            r.add(new Notice(false, "Input check", problems.get(0)));
            for (int i = 1; i < Math.min(problems.size(), 4); i++) {
                r.add(Box.createVerticalStrut(6));
                r.add(new Notice(false, "Also", problems.get(i)));
            }
            if (problems.size() > 4) {
                r.add(Box.createVerticalStrut(6));
                r.add(body("... and " + (problems.size() - 4) + " more cell(s) to fix.", 12, Theme.muted()));
            }
            r.add(Box.createVerticalStrut(6));
            r.add(body("Rule: the page size is a whole number more than 0, the logical address is 0 or more, and each FRAME "
                    + "is a different whole number (or - for not in memory).", 12, Theme.muted()));
            return;
        }

        long page = la / ps, offset = la % ps;
        long physical = -1;
        int sel = page < rows.size() ? (int) page : -1;
        String outcome;
        if (page >= rows.size()) {
            outcome = "trap";
            r.add(new Notice(false, "Invalid", String.format(
                    "logical %d  →  page %d ÷ %d = %d, offset %d  →  the table only has pages 0 to %d  →  trap: address out of range",
                    la, la, ps, page, offset, rows.size() - 1)));
        } else if (frame[sel] == null) {
            outcome = "page fault";
            r.add(new Notice(false, "Page fault", String.format(
                    "logical %d  →  page %d, offset %d  →  page %d is not in memory (-)  →  the OS must load it into a free frame first",
                    la, page, offset, page)));
        } else {
            physical = (long) frame[sel] * ps + offset;
            outcome = String.valueOf(physical);
            r.add(new Notice(true, "Valid", String.format(
                    "logical %d  →  page %d, offset %d  →  frame %d  →  physical = %d × %d + %d = %d",
                    la, page, offset, frame[sel], frame[sel], ps, offset, physical)));
        }
        history.record(String.format("%d pages · logical %d → %s", rows.size(), la, outcome), snapshot());
        r.add(Box.createVerticalStrut(12));

        // logical ÷ page size -> page, offset -> frame -> physical
        Row cards = new Row(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.fill = GridBagConstraints.BOTH; gc.weighty = 1;
        gc.weightx = 1; cards.add(new NumberCard("PAGE #", String.valueOf(page), la + " ÷ " + ps + " (whole part)", false), gc);
        gc.weightx = 0; cards.add(operator("·"), gc);
        gc.weightx = 1; cards.add(new NumberCard("OFFSET", String.valueOf(offset), la + " mod " + ps, false), gc);
        gc.weightx = 0; cards.add(operator("→"), gc);
        gc.weightx = 1; cards.add(new NumberCard("FRAME", sel < 0 ? "none" : frame[sel] == null ? "-" : String.valueOf(frame[sel]),
                sel < 0 ? "no such page" : frame[sel] == null ? "not in memory" : "from the page table", false), gc);
        gc.weightx = 0; cards.add(operator("="), gc);
        gc.weightx = 1; cards.add(new NumberCard("PHYSICAL ADDRESS", physical >= 0 ? String.valueOf(physical) : "trap",
                physical >= 0 ? frame[sel] + " × " + ps + " + " + offset : sel < 0 ? "out of range" : "page fault", physical >= 0), gc);
        r.add(cards);
        r.add(Box.createVerticalStrut(16));

        r.add(pixelText("PAGE-TO-FRAME MAPPING", 7f, Theme.muted(), 0f));
        r.add(Box.createVerticalStrut(10));
        r.add(new PageMap(frame, sel, ps));

        // the same steps in words
        List<String> how = new ArrayList<>();
        how.add(String.format("Page size = %d bytes, so page p holds logical addresses p × %d to p × %d + %d.", ps, ps, ps, ps - 1));
        how.add(String.format("page # = %d ÷ %d = %d (whole part, drop the remainder).", la, ps, page));
        how.add(String.format("offset = %d mod %d = %d - %d × %d = %d.", la, ps, la, page, ps, offset));
        if (sel < 0) {
            how.add(String.format("The page table has %d pages (0 to %d). Page %d is not one of them, so the address is "
                    + "outside the program: the hardware traps and the OS stops the process.", rows.size(), rows.size() - 1, page));
        } else if (frame[sel] == null) {
            how.add(String.format("Page %d's entry is - (not in memory): page fault. The OS finds a free frame, loads page %d "
                    + "from disk, writes the frame number into the table, and runs the instruction again.", page, page));
        } else {
            how.add(String.format("Look up page %d in the page table → frame %d.", page, frame[sel]));
            how.add(String.format("physical = frame × page size + offset = %d × %d + %d = %d.", frame[sel], ps, offset, physical));
            how.add(String.format("Check: frame %d covers physical %d to %d, and %d is inside it.",
                    frame[sel], (long) frame[sel] * ps, (long) frame[sel] * ps + ps - 1, physical));
        }
        r.add(explainSection(how));
    }

    /** Whole number >= min, or null with a message and a red field. */
    private static Integer readWhole(String s, String what, int min, List<String> problems, RoundField f) {
        String msg = null;
        Integer v = null;
        if (s.isBlank()) msg = what + " is blank";
        else {
            try {
                v = Integer.parseInt(s.trim());
                if (v < min) msg = what + " is " + v + (min > 0 ? " - it must be more than 0" : " - it cannot be negative");
            } catch (NumberFormatException e) {
                msg = what + " (\"" + s.trim() + "\") is not a whole number";
            }
        }
        if (msg == null) return v;
        problems.add(msg);
        if (f != null) f.setInvalid(true);
        return null;
    }

    /**
     * Logical pages on the left, physical frames on the right, an arrow from each page to its frame.
     * The page being translated is highlighted; pages not in memory get a dashed stub instead of an arrow.
     * With more than 16 rows it zooms in: the 11 pages around the translated one and the frames they use,
     * so every box keeps a readable label (the full mapping is in the page table above).
     */
    static class PageMap extends JComponent {
        private static final long serialVersionUID = 1L;
        static final int BOX_W = 150, GAP = 190, TOP = 22, ROW = 28, FULL_LIMIT = 16, WINDOW = 5;
        private final Integer[] frame; private final int sel, ps;
        private final List<Integer> pageRows = new ArrayList<>(), frameRows = new ArrayList<>();
        private final boolean zoomed;
        PageMap(Integer[] frame, int sel, int ps) {
            this.frame = frame; this.sel = sel; this.ps = ps;
            int max = -1;
            for (Integer f : frame) if (f != null) max = Math.max(max, f);
            int nFrames = Math.max(max + 1, 1);
            zoomed = Math.max(frame.length, nFrames) > FULL_LIMIT;
            if (!zoomed) {
                for (int i = 0; i < frame.length; i++) pageRows.add(i);
                for (int f = 0; f < nFrames; f++) frameRows.add(f);
            } else {
                int centre = sel >= 0 ? sel : 0;
                int from = Math.max(0, Math.min(centre - WINDOW, frame.length - (2 * WINDOW + 1)));
                int to = Math.min(frame.length - 1, from + 2 * WINDOW);
                java.util.TreeSet<Integer> used = new java.util.TreeSet<>();
                for (int i = from; i <= to; i++) { pageRows.add(i); if (frame[i] != null) used.add(frame[i]); }
                frameRows.addAll(used);
            }
            setAlignmentX(LEFT_ALIGNMENT);
        }
        private int rows() { return Math.max(1, Math.max(pageRows.size(), frameRows.size())); }
        @Override public Dimension getPreferredSize() { return new Dimension(BOX_W * 2 + GAP + 4, TOP + ROW * rows() + (zoomed ? 24 : 4)); }
        @Override public Dimension getMaximumSize()   { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int x0 = Math.max(0, (getWidth() - (BOX_W * 2 + GAP)) / 2), x1 = x0 + BOX_W + GAP;

            g2.setFont(Fonts.label(8f)); g2.setColor(Theme.muted());
            centerText(g2, "LOGICAL PAGES", x0 + BOX_W / 2.0, 12);
            centerText(g2, zoomed ? "FRAMES THEY USE" : "PHYSICAL FRAMES", x1 + BOX_W / 2.0, 12);

            Map<Integer, Integer> pageIn = new HashMap<>();
            for (int i = 0; i < frame.length; i++) if (frame[i] != null) pageIn.put(frame[i], i);
            Map<Integer, Integer> frameY = new HashMap<>();
            g2.setFont(Fonts.body(12f));
            for (int k = 0; k < frameRows.size(); k++) {
                int f = frameRows.get(k);
                frameY.put(f, k);
                Integer p = pageIn.get(f);
                RoundRectangle2D box = new RoundRectangle2D.Double(x1, TOP + k * ROW + 1, BOX_W, ROW - 2, 8, 8);
                boolean on = p != null && p == sel;
                g2.setColor(p == null ? Theme.panel() : blockColor(p, on ? 0.95 : 0.5)); g2.fill(box);
                g2.setColor(on ? INK : Theme.line()); g2.setStroke(new BasicStroke(on ? 2f : 1f)); g2.draw(box);
                g2.setColor(p == null ? Theme.muted() : INK);
                centerText(g2, "frame " + f + (p == null ? "  (free)" : "  ·  page " + p), x1 + BOX_W / 2.0, middle(g2, TOP + k * ROW + ROW / 2.0));
            }
            for (int k = 0; k < pageRows.size(); k++) {
                int i = pageRows.get(k);
                double y = TOP + k * ROW;
                boolean on = i == sel;
                RoundRectangle2D box = new RoundRectangle2D.Double(x0, y + 1, BOX_W, ROW - 2, 8, 8);
                g2.setColor(blockColor(i, on ? 0.95 : 0.5)); g2.fill(box);
                g2.setColor(on ? INK : Theme.line()); g2.setStroke(new BasicStroke(on ? 2f : 1f)); g2.draw(box);
                g2.setColor(INK);
                g2.setFont(Fonts.body(12f));
                centerText(g2, "page " + i + "  (" + (long) i * ps + "–" + ((long) (i + 1) * ps - 1) + ")", x0 + BOX_W / 2.0, middle(g2, y + ROW / 2.0));
                double ay = y + ROW / 2.0;
                if (frame[i] == null) {   // not in memory: short dashed stub
                    g2.setColor(Theme.muted());
                    g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[]{3f, 3f}, 0f));
                    g2.draw(new Line2D.Double(x0 + BOX_W + 4, ay, x0 + BOX_W + 40, ay));
                    g2.setFont(Fonts.body(10f));
                    g2.drawString("not in memory", (float) (x0 + BOX_W + 44), (float) (ay + 4));
                    continue;
                }
                double by = TOP + frameY.get(frame[i]) * ROW + ROW / 2.0;
                g2.setColor(on ? Theme.pink() : withAlpha(Theme.muted(), 140));
                g2.setStroke(new BasicStroke(on ? 2.4f : 1f));
                g2.draw(new Line2D.Double(x0 + BOX_W + 2, ay, x1 - 6, by));
                double ang = Math.atan2(by - ay, (x1 - 6) - (x0 + BOX_W + 2)), hx = x1 - 4, hy = by;
                java.awt.geom.Path2D.Double head = new java.awt.geom.Path2D.Double();
                head.moveTo(hx, hy);
                head.lineTo(hx - 7 * Math.cos(ang - 0.45), hy - 7 * Math.sin(ang - 0.45));
                head.lineTo(hx - 7 * Math.cos(ang + 0.45), hy - 7 * Math.sin(ang + 0.45));
                head.closePath();
                g2.fill(head);
            }
            if (zoomed) {
                g2.setFont(Fonts.body(11f)); g2.setColor(Theme.muted());
                centerText(g2, String.format("showing pages %d–%d of %d around the translated page · the page table above has them all",
                        pageRows.get(0), pageRows.get(pageRows.size() - 1), frame.length),
                        x0 + (BOX_W * 2 + GAP) / 2.0, TOP + ROW * rows() + 16);
            }
            g2.dispose();
        }

        private static Color withAlpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }
    }

    /** Names the field that is wrong, for the Input check notice. */
    private String problem() {
        String[][] f = {{"address space", addressSpace.getText()}, {"page size", pageSize.getText()}};
        long[] kb = new long[2];
        for (int i = 0; i < 2; i++) {
            if (f[i][1].isBlank()) return "the " + f[i][0] + " is blank";
            try { kb[i] = toKb(f[i][1]); }
            catch (IllegalArgumentException e) {
                return "the " + f[i][0] + " \"" + f[i][1].trim() + "\" is not a size like 4 KB, 512 MB or 1 GB (and must be at least 1 KB)";
            }
        }
        return String.format("the page size (%,d KB) is bigger than the address space (%,d KB)", kb[1], kb[0]);
    }

    private void practice(JPanel r) {
        r.add(pixelText("PRACTICE SET", 7f, Theme.text(), 0f));
        r.add(Box.createVerticalStrut(10));
        String[][] rows = new String[PRACTICE.length][];
        for (int i = 0; i < PRACTICE.length; i++) {
            long s = toKb(PRACTICE[i][0]), pg = toKb(PRACTICE[i][1]);
            rows[i] = new String[]{String.valueOf(i + 1), PRACTICE[i][0], PRACTICE[i][1],
                    String.format("%,d KB ÷ %,d KB", s, pg), String.format("%,d", s / pg)};
        }
        r.add(new PracticeTable(rows));
    }

    private JComponent operator(String s) {
        JLabel l = text(s, 22, Theme.muted());
        l.setHorizontalAlignment(SwingConstants.CENTER);
        l.setBorder(new EmptyBorder(0, 10, 0, 10));
        return l;
    }

    /** How a size was turned into KB, e.g. "1 GB = 1 × 1024 × 1024 KB"; "given" when it was already KB. */
    static String working(String s) {
        Matcher m = SIZE.matcher(s.trim().toUpperCase());
        if (!m.matches()) return "";
        String n = m.group(1), unit = m.group(2) == null ? "K" : m.group(2).substring(0, 1);
        return switch (unit) {
            case "G" -> n + " GB = " + n + " × 1024 × 1024 KB";
            case "M" -> n + " MB = " + n + " × 1024 KB";
            default  -> "given";
        };
    }

    /** "1 GB" -> 1,048,576 ; "512MB" -> 524,288 ; "4 KB" -> 4. Rejects sizes under 1 KB. */
    static long toKb(String s) {
        Matcher m = SIZE.matcher(s.trim().toUpperCase());
        if (!m.matches()) throw new IllegalArgumentException(s);
        double n = Double.parseDouble(m.group(1));
        String unit = m.group(2) == null ? "K" : m.group(2).substring(0, 1);
        long kb = switch (unit) {
            case "G" -> (long) (n * 1024 * 1024);
            case "M" -> (long) (n * 1024);
            default  -> (long) n;
        };
        if (kb <= 0) throw new IllegalArgumentException(s);
        return kb;
    }

    // ============================ COMPONENTS ============================

    /** The formula in large text with a cyan bar on the left. */
    static class FormulaStrip extends Row {
        private static final long serialVersionUID = 1L;
        FormulaStrip(String formula) {
            super(new BorderLayout());
            setBorder(new EmptyBorder(8, 18, 8, 8));
            add(body(formula, 16, Theme.text()), BorderLayout.CENTER);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(Theme.cyan());
            g2.fill(new RoundRectangle2D.Double(0, 0, 4, getHeight(), 4, 4));
            g2.dispose();
        }
    }

    /** One number card: small caption, big value, working underneath. The answer card is green. */
    static class NumberCard extends JPanel {
        private static final long serialVersionUID = 1L;
        private final boolean answer;
        NumberCard(String caption, String value, String note, boolean answer) {
            this.answer = answer;
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(12, 16, 12, 16));
            JLabel c = new SafeLabel(caption);
            c.setFont(Fonts.label(8f)); c.setForeground(Theme.muted());
            add(c);
            add(Box.createVerticalStrut(8));
            add(body(value, 23, answer ? Theme.green() : Theme.text()));
            add(Box.createVerticalStrut(6));
            add(body(note, 11, Theme.muted()));
        }
        // same small base width for all three cards, so the row's spare space makes them equal (as in the design)
        @Override public Dimension getPreferredSize() { return new Dimension(100, super.getPreferredSize().height); }
        @Override public Dimension getMinimumSize()   { return getPreferredSize(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            RoundRectangle2D box = new RoundRectangle2D.Double(0.75, 0.75, getWidth() - 1.5, getHeight() - 1.5, 18, 18);
            g2.setColor(Theme.panel()); g2.fill(box);
            g2.setColor(answer ? Theme.green() : Theme.inputLine());
            g2.setStroke(new BasicStroke(answer ? 2f : 1.5f)); g2.draw(box);
            g2.dispose();
        }
    }

    /** Practice set table: # / address space / page size / solution / entries. */
    static class PracticeTable extends JComponent {
        private static final long serialVersionUID = 1L;
        static final String[] HEAD = {"#", "ADDRESS SPACE", "PAGE SIZE", "SOLUTION", "ENTRIES"};
        static final double[] WEIGHT = {0.055, 0.175, 0.15, 0.43, 0.19};   // from the design's column widths
        static final int HEAD_H = 27, ROW = 34;
        private final String[][] rows;
        PracticeTable(String[][] rows) { this.rows = rows; setAlignmentX(LEFT_ALIGNMENT); }
        @Override public Dimension getPreferredSize() { return new Dimension(300, HEAD_H + ROW * rows.length + 3); }
        @Override public Dimension getMaximumSize()   { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getPreferredSize().height;
            double[] x = new double[HEAD.length + 1];
            x[0] = 1.5;
            for (int c = 0; c < HEAD.length; c++) x[c + 1] = x[c] + (w - 3) * WEIGHT[c];

            RoundRectangle2D box = new RoundRectangle2D.Double(0.75, 0.75, w - 1.5, h - 1.5, 20, 20);
            Shape oldClip = g2.getClip();
            g2.clip(box);
            g2.setColor(Theme.panel2()); g2.fillRect(0, 0, w, HEAD_H + 2);
            for (int i = 0; i < rows.length; i++) {
                g2.setColor(i % 2 == 0 ? Theme.panel() : Theme.panel2());
                g2.fillRect(0, 2 + HEAD_H + i * ROW, w, ROW + 2);
            }
            g2.setColor(Theme.line()); g2.setStroke(new BasicStroke(1f));
            for (int c = 1; c < HEAD.length; c++) g2.drawLine((int) Math.round(x[c]), 0, (int) Math.round(x[c]), h);
            g2.setClip(oldClip);
            g2.setStroke(new BasicStroke(1.5f)); g2.draw(box);

            g2.setFont(Fonts.label(9f)); g2.setColor(Theme.muted());
            for (int c = 0; c < HEAD.length; c++) cell(g2, HEAD[c], x, c, 2 + HEAD_H / 2.0);
            g2.setFont(Fonts.body(15f));
            for (int i = 0; i < rows.length; i++)
                for (int c = 0; c < HEAD.length; c++) {
                    g2.setColor(c == HEAD.length - 1 && Theme.dark ? Theme.green() : Theme.text());
                    cell(g2, rows[i][c], x, c, 2 + HEAD_H + i * ROW + ROW / 2.0);
                }
            g2.dispose();
        }

        /** The solution column is left-aligned, the others centred. */
        private static void cell(Graphics2D g2, String s, double[] x, int c, double midY) {
            double base = middle(g2, midY);
            if (c == 3) g2.drawString(s, (float) (x[c] + 16), (float) base);
            else centerText(g2, s, (x[c] + x[c + 1]) / 2, base);
        }
    }
}
