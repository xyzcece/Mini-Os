package modules.segmentation;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import javax.swing.*;
import modules.common.FigmaModule;
import shared.Fonts;
import shared.Theme;

/**
 * SEGMENTATION  —  OWNER: Member 1
 *   [x] Segment table input (limit, base), segment number, offset
 *   [x] Logical space + segment table + physical memory map
 *   [x] Translation: offset < limit ? base + offset : invalid
 * CHECK: Segment 2, offset 53 -> physical 4353
 *        Segment 1, offset 500 -> invalid (500 is not < limit 400)
 *
 * Layout follows the Figma frame "Segmentation — Light" (mini-os file, node 20:740).
 */
public class SegmentationModule extends FigmaModule {
    private static final long serialVersionUID = 1L;

    /** Names from the class example (logical address space). Extra segments get "segment N". */
    static final String[] NAMES = {"subroutine", "sqrt", "main program", "stack", "symbol table"};
    /** Class example to type in: segment 2, offset 53 -> 4353. Fields start empty. */
    static final String EXAMPLE_TABLE = "1000,1400; 400,6300; 400,4300; 1100,3200; 1000,4700";

    private JTextField tableField, segField, offsetField;

    @Override public String title()    { return "Segmentation"; }
    @Override public String subtitle() { return "Validate offsets against base and limit pairs."; }
    @Override protected String crumb() { return "SEGMENTS"; }
    @Override protected String lead()  { return "Validate a segment:offset pair and find its physical address."; }

    // ============================ INPUTS ============================

    @Override protected void buildInputs(JPanel p) {
        tableField  = new RoundField(tableField  == null ? "" : tableField.getText());
        segField    = new RoundField(segField    == null ? "" : segField.getText());
        offsetField = new RoundField(offsetField == null ? "" : offsetField.getText());

        // 3 columns sized 2.4fr / 1fr / 1fr with a 14px gap, like the design grid
        JPanel grid = new JPanel(null) {
            private static final long serialVersionUID = 1L;
            final double[] fr = {2.4, 1, 1};
            @Override public void doLayout() {
                int gap = 14, avail = getWidth() - gap * 2, x = 0;
                for (int i = 0; i < getComponentCount(); i++) {
                    int w = (int) Math.round(avail * fr[i] / 4.4);
                    Component c = getComponent(i);
                    c.setBounds(x, 0, w, c.getPreferredSize().height);
                    x += w + gap;
                }
            }
            @Override public Dimension getPreferredSize() {
                int h = 0;
                for (Component c : getComponents()) h = Math.max(h, c.getPreferredSize().height);
                return new Dimension(300, h);
            }
        };
        grid.setOpaque(false);
        grid.add(field("Segment table (limit, base)", tableField, "limit, base; limit, base"));
        grid.add(field("Segment number", segField, null));
        grid.add(field("Offset", offsetField, null));
        p.add(grid, BorderLayout.CENTER);
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
        r.add(pixelText("SIMULATION OUTPUT · EXAMPLE OF SEGMENTATION", 7f, Theme.text(), 0f));
        r.add(Box.createVerticalStrut(12));

        if (allBlank(tableField, segField, offsetField)) {
            r.add(Notice.prompt("Type the segment table, a segment number and an offset, then press RUN SIMULATION."));
            return;
        }

        List<int[]> table;   // each entry: {limit, base}
        int seg, offset;
        try {
            table = parseTable(tableField.getText());
            seg = Integer.parseInt(segField.getText().trim());
            offset = Integer.parseInt(offsetField.getText().trim());
        } catch (IllegalArgumentException e) {   // also covers NumberFormatException
            r.add(new Notice(false, "Input check",
                    "write the table as  limit,base; limit,base  with whole numbers (limit > 0, base ≥ 0)"));
            return;
        }

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
    }

    static List<int[]> parseTable(String s) {
        List<int[]> t = new ArrayList<>();
        for (String part : s.split(";")) {
            if (part.trim().isEmpty()) continue;
            String[] lb = part.split(",");
            if (lb.length != 2) throw new IllegalArgumentException(part);
            int limit = Integer.parseInt(lb[0].trim()), base = Integer.parseInt(lb[1].trim());
            if (limit <= 0 || base < 0) throw new IllegalArgumentException(part);
            t.add(new int[]{limit, base});
        }
        if (t.isEmpty()) throw new IllegalArgumentException();
        return t;
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
