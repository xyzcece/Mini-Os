package modules.paging;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import modules.common.FigmaModule;
import shared.Fonts;
import shared.Theme;

/**
 * PAGING LAB  —  OWNER: Member 1
 * Number of page table entries = Address space ÷ Page size
 * CHECK: 1 GB ÷ 4 KB = 262,144 entries
 *
 * Layout follows the Figma "Paging Lab" frames (mini-os file, light + dark).
 */
public class PagingModule extends FigmaModule {
    private static final long serialVersionUID = 1L;
    private static final Pattern SIZE = Pattern.compile("([0-9]*\\.?[0-9]+)\\s*([KMG]B?)?");
    /** Practice set from class: {address space, page size}. Solutions are worked out below. */
    static final String[][] PRACTICE = {{"1 GB", "4 KB"}, {"2 GB", "8 KB"}, {"512 MB", "4 KB"}};

    private JTextField addressSpace, pageSize;

    @Override public String title()    { return "Paging Lab"; }
    @Override public String subtitle() { return "Number of page table entries = address space ÷ page size."; }
    @Override protected String crumb() { return "PAGING"; }
    @Override protected String lead()  { return "Find how many page table entries a computer system needs."; }

    // ============================ INPUTS ============================

    @Override protected void buildInputs(JPanel p) {
        addressSpace = new RoundField(addressSpace == null ? "" : addressSpace.getText());
        pageSize     = new RoundField(pageSize     == null ? "" : pageSize.getText());
        JPanel grid = new JPanel(new GridLayout(1, 2, 14, 0));
        grid.setOpaque(false);
        grid.add(field("Address space", addressSpace, null));
        grid.add(field("Page size", pageSize, "Use KB"));
        p.add(grid, BorderLayout.CENTER);
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
            r.add(new Notice(false, "Input check",
                    "use a number + KB, MB or GB (e.g. 1 GB, 4 KB); the page size must be at least 1 KB and not bigger than the address space"));
            r.add(Box.createVerticalStrut(14));
            practice(r);
            return;
        }
        long entries = spaceKb / pageKb;

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
