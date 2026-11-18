package modules.segmentation;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import javax.swing.*;
import modules.common.EntryGrid;
import modules.common.FigmaModule;
import shared.Fonts;
import shared.Theme;

/**
 * SEGMENTATION  —  OWNER: Member 1
 *   [x] Segment table input: a scrolling table (SEG / LIMIT / BASE, one row per segment),
 *       with +1 / +5 / −1 / [count] Set / Paste / Bulk - any number of segments
 *   [x] Segment number + offset to translate
 *   [x] Logical space + segment table + physical memory map
 *   [x] Translation: offset < limit ? base + offset : invalid
 *   [x] Toolbar:
 *       Sample data, Out-of-range sample, Large sample (12 segments), Random, Invalid input test,
 *       History (recent runs), Undo, Clear
 *   [x] Validation: blank, letters, limit <= 0, negative base/offset - the bad cell turns red, never crashes
 * CHECK: Segment 2, offset 53 -> physical 4353
 *        Segment 1, offset 500 -> invalid (500 is not < limit 400)
 *
 * Layout follows the Figma frame "Segmentation — Light" (mini-os file, node 20:740).
 */
public class SegmentationModule extends FigmaModule {
    private static final long serialVersionUID = 1L;

    /** Names from the class example (logical address space). Extra segments get "segment N". */
    static final String[] NAMES = {"subroutine", "sqrt", "main program", "stack", "symbol table"};
    /** Class example as {limit, base}: segment 2, offset 53 -> 4353. Fields start empty. */
    static final String[][] EXAMPLE = {{"1000", "1400"}, {"400", "6300"}, {"400", "4300"}, {"1100", "3200"}, {"1000", "4700"}};
    /** Exceptional case: a letter, a negative limit and a missing base. */
    static final String[][] BAD = {{"1000", "1400"}, {"abc", "6300"}, {"-5", "4300"}, {"700", ""}};

    /** Segments as {limit, base}; segment # is the position (0..n-1). Starts with 5 empty segments. */
    private final transient EntryGrid grid = new EntryGrid("SEG", new String[]{"LIMIT", "BASE"}, String::valueOf, 62, this::onRunPressed)
            .alwaysTable();
    { grid.resize(5); }
    private String segText = "", offsetText = "";
    private RoundField segField, offsetField;

    /** Everything the person typed, for Undo and History. */
    private record State(List<String[]> segs, String seg, String offset) {}
    private final transient InputHistory<State> history = new InputHistory<>();

    @Override public String title()    { return "Segmentation"; }
    @Override public String subtitle() { return "Validate offsets against base and limit pairs."; }
    @Override protected void onUndoPressed() { history.undo(this, snapshot(), this::restore); }
    @Override protected void onRedoPressed() { history.redo(this, snapshot(), this::restore); }

    @Override protected String crumb() { return "SEGMENTS"; }
    @Override protected String lead()  { return "Validate a segment:offset pair and find its physical address."; }

    // ============================ INPUTS ============================

    @Override protected void buildInputs(JPanel p) {
        saveFields();   // keep what was typed (theme change, add/remove segment)

        segField = new RoundField(segText);
        offsetField = new RoundField(offsetText);
        segField.addActionListener(e -> onRunPressed());
        offsetField.addActionListener(e -> onRunPressed());

        JPanel address = new JPanel(new GridLayout(1, 2, 14, 0));
        address.setOpaque(false);
        address.setAlignmentX(LEFT_ALIGNMENT);
        address.add(field("Segment number", segField, "0 to " + Math.max(0, grid.size() - 1)));
        address.add(field("Offset", offsetField, "must be less than the segment's limit"));

        JPanel stack = new JPanel();
        stack.setOpaque(false);
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        JComponent table = tableBox();
        table.setAlignmentX(LEFT_ALIGNMENT);
        stack.add(table);
        stack.add(Box.createVerticalStrut(14));
        stack.add(address);
        p.add(stack, BorderLayout.CENTER);
        p.add(toolbar(), BorderLayout.SOUTH);
    }

    /** "SEGMENT TABLE" box: a scrolling table (one row per segment) and the +1 / +5 / −1 / Set / Paste row. */
    private JComponent tableBox() {
        return grid.box("SEGMENT TABLE", "segment", "one per segment", "physical address = base + offset",
                this::resizeSegments, this::bulkLoad);
    }

    /** +1 / +5 / −1 / Set: changes the number of segments, keeping what was typed (with Undo). */
    private void resizeSegments(int n) {
        history.push(snapshot());
        grid.resize(n);
        rebuildInputs();
        showToast(this, grid.size() + (grid.size() == 1 ? " segment" : " segments"));
    }

    /** Paste / Bulk: replaces the table with the pasted "limit, base" lines (with Undo) and runs it. */
    private void bulkLoad(List<String[]> pasted) {
        history.push(snapshot());
        grid.setRows(pasted);
        rebuildInputs();
        markInputsDirty();
        showToast(this, "Imported " + pasted.size() + " segments — press RUN SIMULATION");
    }

    /** Sample / Out-of-range / Large / Random / Invalid input / History / Undo / Clear pills. */
    private JComponent toolbar() {
        DashedPill hist = new DashedPill("History");
        hist.addActionListener(e -> history.showMenu(hist, this::restore));
        return pillRow(10,
                pill("Sample data (VALID)", () -> load(rows(EXAMPLE), "2", "53",
                        "Loaded the class example: segment 2, offset 53")),
                pill("Out-of-range sample", () -> load(rows(EXAMPLE), "1", "500",
                        "Loaded segment 1, offset 500 (limit is 400)")),
                pill("Large sample (12 segments)", () -> loadRandom(12, new Random(7), "Loaded 12 segments")),
                pill("Random", () -> loadRandom(grid.size(), new Random(), "Generated " + grid.size() + " random segments")),
                pill("Invalid input test", () -> load(rows(BAD), "x", "-1",
                        "Loaded bad input - the red cells show what to fix")),
                hist,
                pill("Undo", () -> onUndoPressed()),
                pill("Clear", () -> load(blank(grid.size()), "", "", "Cleared all fields")));
    }

    // ---------- input plumbing ----------

    private static List<String[]> blank(int n) {
        List<String[]> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(new String[]{"", ""});
        return l;
    }

    private static List<String[]> rows(String[][] data) {
        List<String[]> l = new ArrayList<>();
        for (String[] r : data) l.add(r.clone());
        return l;
    }

    /** Copies whatever is typed in the fields back into the grid / segText / offsetText. */
    private void saveFields() {
        if (segField != null) segText = segField.getText().trim();
        if (offsetField != null) offsetText = offsetField.getText().trim();
        grid.save();
    }

    private State snapshot() {
        saveFields();
        return new State(grid.copy(), segText, offsetText);
    }

    /** Puts a snapshot back on screen and runs it. */
    private void restore(State st) {
        grid.setRows(st.segs());   // drops the old cells so they are not copied back over the new data
        segText = st.seg();
        offsetText = st.offset();
        segField = offsetField = null;
        rebuildInputs();
        markInputsDirty();
    }

    private void load(List<String[]> table, String seg, String offset, String toast) {
        history.push(snapshot());
        restore(new State(table, seg, offset));
        showToast(this, toast);
    }

    /** n segments packed one after another (with small random gaps) from address 1000. */
    private void loadRandom(int n, Random rng, String toast) {
        List<String[]> t = new ArrayList<>();
        int base = 1000;
        for (int i = 0; i < n; i++) {
            int limit = 100 * (1 + rng.nextInt(15));   // 100 .. 1500
            t.add(new String[]{String.valueOf(limit), String.valueOf(base)});
            base += limit + 100 * rng.nextInt(4);      // gap of 0 .. 300 before the next segment
        }
        int seg = rng.nextInt(n);
        int offset = rng.nextInt(Integer.parseInt(t.get(seg)[0]) + 200);   // sometimes past the limit, to show the trap
        load(t, String.valueOf(seg), String.valueOf(offset), toast);
    }

    private JPanel field(String label, JTextField f, String hint) {
        JPanel c = new JPanel();
        c.setOpaque(false);
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        c.add(fieldLabel(label));
        c.add(Box.createVerticalStrut(6));
        c.add(f);
        if (hint != null) {
            c.add(Box.createVerticalStrut(6));
            c.add(text(hint, 10, Theme.muted()));
        }
        return c;
    }

    // ============================ RESULTS ============================

    @Override protected void runSimulation(JPanel r) {
        saveFields();
        grid.clearInvalid();
        if (segField != null) { segField.setInvalid(false); offsetField.setInvalid(false); }

        r.add(pixelText("SIMULATION OUTPUT · EXAMPLE OF SEGMENTATION", 7f, Theme.text(), 0f));
        r.add(Box.createVerticalStrut(12));

        boolean nothingTyped = segText.isEmpty() && offsetText.isEmpty()
                && grid.rows().stream().allMatch(s -> s[0].isEmpty() && s[1].isEmpty());
        if (nothingTyped) {
            r.add(Notice.prompt("Fill in each segment's LIMIT and BASE, a segment number and an offset, then press RUN SIMULATION."));
            return;
        }

        // read everything, collecting every problem (and turning the bad cells red)
        List<String> problems = new ArrayList<>();
        List<int[]> table = new ArrayList<>();   // each entry: {limit, base}
        List<String[]> segs = grid.rows();
        for (int i = 0; i < segs.size(); i++) {
            Integer limit = read(segs.get(i)[0], "segment " + i + " LIMIT", 1, i, 0, problems);
            Integer base = read(segs.get(i)[1], "segment " + i + " BASE", 0, i, 1, problems);
            table.add(new int[]{limit == null ? 0 : limit, base == null ? 0 : base});
        }
        Integer segN = read(segText, "the segment number", Integer.MIN_VALUE, -1, 0, problems);
        Integer offN = read(offsetText, "the offset", 0, -1, 1, problems);

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
            r.add(body("Rule: every LIMIT is a whole number more than 0; every BASE and the offset are 0 or more.", 12, Theme.muted()));
            return;
        }
        int seg = segN, offset = offN;

        // 1) the answer
        int physical = -1;
        if (seg < 0 || seg >= table.size()) {
            r.add(new Notice(false, "Invalid",
                    "there is no segment " + seg + " (segments 0 to " + (table.size() - 1) + ")  →  trap: addressing error"));
        } else {
            int limit = table.get(seg)[0], base = table.get(seg)[1];
            if (offset >= 0 && offset < limit) {
                physical = base + offset;
                r.add(new Notice(true, "Valid", String.format(
                        "segment %d, offset %d  →  %d < limit %d  →  physical = base %d + %d = %d",
                        seg, offset, offset, limit, base, offset, physical)));
            } else {
                r.add(new Notice(false, "Invalid", String.format(
                        "segment %d, offset %d  →  %d is not < limit %d  →  trap: addressing error",
                        seg, offset, offset, limit)));
            }
        }
        r.add(Box.createVerticalStrut(12));

        List<String> how = new ArrayList<>();
        how.add("A logical address is (segment number, offset). The segment table gives each segment's base "
                + "(where it starts in memory) and limit (its length).");
        if (seg < 0 || seg >= table.size()) {
            how.add(String.format("The table has segments 0 to %d. Segment %d does not exist, so the hardware traps "
                    + "(addressing error) and the OS stops the process.", table.size() - 1, seg));
        } else {
            int limit = table.get(seg)[0], base = table.get(seg)[1];
            how.add(String.format("Segment %d: base %d, limit %d, so its legal offsets are 0 to %d.", seg, base, limit, limit - 1));
            if (offset < limit) {
                how.add(String.format("Offset %d < limit %d → the address is legal.", offset, limit));
                how.add(String.format("physical = base + offset = %d + %d = %d.", base, offset, base + offset));
                how.add(String.format("Check: segment %d occupies physical %d to %d, and %d is inside it.",
                        seg, base, base + limit - 1, base + offset));
            } else {
                how.add(String.format("Offset %d is not < limit %d → it would reach past the end of the segment, "
                        + "so the hardware traps (addressing error) and no physical address is made.", offset, limit));
            }
        }

        history.record(String.format("%d segments · seg %d, offset %d → %s", table.size(), seg, offset,
                physical >= 0 ? String.valueOf(physical) : "trap"), snapshot());

        // 2) logical space, segment table, physical memory (spread across the row)
        Row row = new Row(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.anchor = GridBagConstraints.CENTER;
        row.add(column(new LogicalSpace(table, seg), "logical address space"), gc);
        gc.weightx = 1; row.add(Box.createHorizontalStrut(8), gc); gc.weightx = 0;
        row.add(column(new SegmentTable(table, seg), "segment table"), gc);
        gc.weightx = 1; row.add(Box.createHorizontalStrut(8), gc); gc.weightx = 0;
        row.add(column(new MemoryMap(table, seg, physical), "physical memory"), gc);
        r.add(row);
        r.add(explainSection(how));
    }

    /**
     * Reads one whole number. If it is blank, not a number or below min, adds a message to problems,
     * turns its field red and returns null. segIdx >= 0 means a table cell (col 0 = LIMIT, 1 = BASE);
     * segIdx -1 means the segment number (col 0) or the offset (col 1).
     */
    private Integer read(String s, String what, int min, int segIdx, int col, List<String> problems) {
        RoundField f = segIdx >= 0 ? null : (col == 0 ? segField : offsetField);
        String msg = null;
        Integer v = null;
        if (s.isBlank()) msg = what + " is blank";
        else {
            try {
                v = Integer.parseInt(s.trim());
                if (v < min) msg = what + " is " + v + (min == 1 ? " - a limit must be more than 0" : " - it cannot be negative");
            } catch (NumberFormatException e) {
                msg = what + " (\"" + s.trim() + "\") is not a whole number";
            }
        }
        if (msg == null) return v;
        problems.add(msg);
        if (segIdx >= 0) grid.markInvalid(segIdx, col);
        else if (f != null) f.setInvalid(true);
        return null;
    }

    static String name(int i) { return i < NAMES.length ? NAMES[i] : "segment " + i; }

    /** Segment colour; a bit stronger in dark mode so the dark ink stays readable. */
    static Color segColor(int i, double alpha) {
        return blockColor(i, Theme.dark ? Math.min(1, alpha + 0.3) : alpha);
    }

    private JPanel column(JComponent body, String caption) {
        JPanel c = new JPanel();
        c.setOpaque(false);
        c.setLayout(new BoxLayout(c, BoxLayout.Y_AXIS));
        body.setAlignmentX(CENTER_ALIGNMENT);
        c.add(body);
        c.add(Box.createVerticalStrut(8));
        JLabel l = text(caption, 12, Theme.muted());
        l.setAlignmentX(CENTER_ALIGNMENT);
        c.add(l);
        return c;
    }

    // ============================ DIAGRAMS ============================

    /** Logical address space: rounded "bubble" with one coloured block per segment. */
    static class LogicalSpace extends JComponent {
        private static final long serialVersionUID = 1L;
        /** Block positions (x, y, w, h) from the design for the 5 class-example segments. */
        static final double[][] SPOTS = {
                {38.5, 68.5, 86, 66}, {50.5, 148.5, 64, 86}, {140.5, 182.5, 92, 54},
                {148.5, 64.5, 80, 40}, {144.5, 116.5, 92, 52}};
        private final List<int[]> table; private final int seg;
        LogicalSpace(List<int[]> table, int seg) { this.table = table; this.seg = seg; }
        @Override public Dimension getPreferredSize() { return new Dimension(280, 320); }
        @Override public Dimension getMaximumSize()   { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            RoundRectangle2D space = new RoundRectangle2D.Double(0.75, 0.75, 278.5, 318.5, 280, 280);
            g2.setColor(Theme.panel()); g2.fill(space);
            g2.setColor(Theme.line()); g2.setStroke(new BasicStroke(1.5f)); g2.draw(space);

            int n = table.size();
            for (int i = 0; i < n; i++) {
                double[] b;
                if (n <= SPOTS.length) b = SPOTS[i];
                else {                                   // more than 5 segments: 2-column grid inside the bubble
                    int rows = (n + 1) / 2;
                    double cellH = Math.min(52, 210.0 / rows);
                    b = new double[]{i % 2 == 0 ? 44 : 144, 55 + (i / 2) * cellH, 92, cellH - 6};
                }
                boolean sel = i == seg;
                RoundRectangle2D block = new RoundRectangle2D.Double(b[0], b[1], b[2], b[3], 12, 12);
                g2.setColor(segColor(i, sel ? 0.95 : 0.55)); g2.fill(block);
                g2.setColor(sel ? INK : Theme.line()); g2.setStroke(new BasicStroke(sel ? 2f : 1f)); g2.draw(block);

                Font nameF = Fonts.body(12f), tagF = Fonts.label(8f);
                FontMetrics a = g2.getFontMetrics(nameF), t = g2.getFontMetrics(tagF);
                double top = b[1] + (b[3] - (a.getHeight() + t.getHeight())) / 2;
                g2.setColor(INK);
                g2.setFont(nameF); centerText(g2, name(i), b[0] + b[2] / 2, top + a.getAscent());
                if (b[3] >= 30) { g2.setFont(tagF); centerText(g2, "seg " + i, b[0] + b[2] / 2, top + a.getHeight() + t.getAscent()); }
            }
            g2.dispose();
        }
    }

    /** Segment table: # / limit / base, the chosen segment's row highlighted. */
    static class SegmentTable extends JComponent {
        private static final long serialVersionUID = 1L;
        static final int[] COLS = {34, 70, 70};
        static final int HEAD = 28, ROW = 34;
        private final List<int[]> table; private final int seg;
        SegmentTable(List<int[]> table, int seg) { this.table = table; this.seg = seg; }
        @Override public Dimension getPreferredSize() { return new Dimension(174 + 3, HEAD + ROW * table.size() + 3); }
        @Override public Dimension getMaximumSize()   { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getPreferredSize().height;
            RoundRectangle2D box = new RoundRectangle2D.Double(0.75, 0.75, w - 1.5, h - 1.5, 16, 16);
            Shape oldClip = g2.getClip();
            g2.clip(box);
            g2.setColor(Theme.panel2()); g2.fillRect(0, 0, w, HEAD + 2);
            for (int i = 0; i < table.size(); i++) {
                int y = 2 + HEAD + i * ROW;
                g2.setColor(i == seg ? segColor(i, 0.6) : (i % 2 == 0 ? Theme.panel() : Theme.panel2()));
                g2.fillRect(0, y, w, ROW + 2);
            }
            g2.setColor(Theme.line()); g2.setStroke(new BasicStroke(1f));
            int x = 2;
            for (int c = 0; c < COLS.length - 1; c++) { x += COLS[c]; g2.drawLine(x, 0, x, h); }
            g2.setClip(oldClip);
            g2.setStroke(new BasicStroke(1.5f)); g2.draw(box);

            String[] head = {"#", "limit", "base"};
            g2.setFont(Fonts.label(9f)); g2.setColor(Theme.muted());
            drawCells(g2, head, 2 + HEAD / 2.0);
            g2.setFont(Fonts.body(14f));
            for (int i = 0; i < table.size(); i++) {
                g2.setColor(i == seg ? INK : Theme.text());
                drawCells(g2, new String[]{String.valueOf(i), String.valueOf(table.get(i)[0]), String.valueOf(table.get(i)[1])},
                        2 + HEAD + i * ROW + ROW / 2.0);
            }
            g2.dispose();
        }

        private void drawCells(Graphics2D g2, String[] cells, double midY) {
            double x = 1.5, base = middle(g2, midY);
            for (int c = 0; c < cells.length; c++) { centerText(g2, cells[c], x + COLS[c] / 2.0, base); x += COLS[c]; }
        }
    }

    /** Physical memory: segments placed by base address, address marks on the left, arrow at the answer. */
    static class MemoryMap extends JComponent {
        private static final long serialVersionUID = 1L;
        static final int BOX_X = 42, BOX_Y = 4, BOX_W = 150, BOX_H = 331, TOP_GAP = 20;
        private final List<int[]> table; private final int seg, physical;
        MemoryMap(List<int[]> table, int seg, int physical) { this.table = table; this.seg = seg; this.physical = physical; }
        @Override public Dimension getPreferredSize() { return new Dimension(252, 339); }
        @Override public Dimension getMaximumSize()   { return getPreferredSize(); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            long lo = Long.MAX_VALUE, hi = Long.MIN_VALUE;
            for (int[] t : table) { lo = Math.min(lo, t[1]); hi = Math.max(hi, (long) t[1] + t[0]); }
            double top = BOX_Y + TOP_GAP, scale = (BOX_H - TOP_GAP - 2) / (double) (hi - lo);
            final long low = lo;
            java.util.function.LongToDoubleFunction yOf = addr -> top + (addr - low) * scale;

            Rectangle box = new Rectangle(BOX_X, BOX_Y, BOX_W, BOX_H);
            Color m = Theme.muted();
            g2.setColor(new Color(m.getRed(), m.getGreen(), m.getBlue(), 51)); g2.fill(box);

            g2.setFont(Fonts.body(12f));
            FontMetrics fm = g2.getFontMetrics();
            for (int i = 0; i < table.size(); i++) {
                double y1 = yOf.applyAsDouble(table.get(i)[1]), y2 = yOf.applyAsDouble((long) table.get(i)[1] + table.get(i)[0]);
                Rectangle.Double r = new Rectangle.Double(BOX_X, y1, BOX_W, y2 - y1);
                g2.setColor(segColor(i, i == seg ? 0.95 : 0.55)); g2.fill(r);
                g2.setColor(Theme.line()); g2.setStroke(new BasicStroke(1f)); g2.draw(r);
                if (r.height >= fm.getAscent()) {
                    g2.setColor(INK);
                    centerText(g2, "segment " + i, BOX_X + BOX_W / 2.0, y1 + (r.height + fm.getAscent() - fm.getDescent()) / 2);
                }
            }
            g2.setColor(Theme.line()); g2.setStroke(new BasicStroke(1.5f));
            g2.draw(new Rectangle.Double(BOX_X + 0.75, BOX_Y + 0.75, BOX_W - 1.5, BOX_H - 1.5));

            // address marks: every segment start and end, skipping ones that would overlap
            TreeSet<Long> marks = new TreeSet<>();
            for (int[] t : table) { marks.add((long) t[1]); marks.add((long) t[1] + t[0]); }
            g2.setFont(Fonts.body(10f)); g2.setColor(Theme.muted());
            double last = -100;
            for (long a : marks) {
                double y = yOf.applyAsDouble(a);
                if (y - last < 11) continue;
                g2.drawString(String.valueOf(a), 18, (float) (y + 3.5));
                last = y;
            }

            if (physical >= 0) {
                double y = yOf.applyAsDouble(physical);
                g2.setColor(Theme.pink());
                g2.fill(new Rectangle.Double(34, y - 1, 166, 2));
                g2.setFont(Fonts.body(11f));
                g2.drawString("← " + physical, 200, (float) (y + 4));
            }
            g2.dispose();
        }
    }
}
