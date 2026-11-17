package modules.common;

import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import modules.common.FigmaModule.Box12;
import modules.common.FigmaModule.DashedPill;
import modules.common.FigmaModule.RoundField;
import shared.Fonts;
import shared.Theme;

/**
 * Editable list of entries (jobs, segments, ...) that grows the same way as the Multiprogramming job box:
 *   - up to 7 entries: one column of small boxes per entry (ID row on top, one row per value)
 *   - 8 or more:      a compact scrolling table, one row per entry (fast to type into, no width limit)
 *   - controls:       +1 · +5 · −1 · [count] Set · Paste / Bulk
 *
 * The module owns the data through rows(); call save() before reading it, and setRows() to replace it.
 */
public class EntryGrid {
    /** Above this many entries the boxes switch to the table. */
    public static final int BOX_LIMIT = 7;

    private final String idHead;               // e.g. "J#"
    private final String[] cols;               // value columns, e.g. {"AT", "RT"}
    private final IntFunction<String> idOf;    // entry index -> label, e.g. i -> "J" + (i + 1)
    private final int boxW;
    private final Runnable onEnter;
    private List<String[]> rows = new ArrayList<>();

    private final List<RoundField[]> fields = new ArrayList<>();
    private JTable table;
    private DefaultTableModel model;
    private final Set<Long> invalid = new HashSet<>();

    /** true = always the scrolling table, even for a few entries (no small boxes). */
    private boolean alwaysTable = false;

    /** Always show the scrolling table, whatever the number of entries. Returns this for chaining. */
    public EntryGrid alwaysTable() { alwaysTable = true; return this; }

    public EntryGrid(String idHead, String[] cols, IntFunction<String> idOf, int boxW, Runnable onEnter) {
        this.idHead = idHead; this.cols = cols; this.idOf = idOf; this.boxW = boxW; this.onEnter = onEnter;
    }

    public List<String[]> rows() { return rows; }
    public int size() { return rows.size(); }

    /** Replaces the data; the on-screen cells are dropped so the next build shows the new values. */
    public void setRows(List<String[]> newRows) {
        rows = new ArrayList<>();
        for (String[] r : newRows) rows.add(r.clone());
        discard();
    }

    /** Deep copy of the data (for Undo / History snapshots). */
    public List<String[]> copy() {
        save();
        List<String[]> c = new ArrayList<>();
        for (String[] r : rows) c.add(r.clone());
        return c;
    }

    /** Grows or shrinks to n entries (at least 1), keeping what was typed. */
    public void resize(int n) {
        save();
        n = Math.max(1, n);
        while (rows.size() < n) rows.add(new String[cols.length]);
        for (String[] r : rows) for (int c = 0; c < r.length; c++) if (r[c] == null) r[c] = "";
        while (rows.size() > n) rows.remove(rows.size() - 1);
        discard();
    }

    /** Copies whatever is typed on screen back into rows(). */
    public void save() {
        if (table != null && model != null) {
            if (table.isEditing()) table.getCellEditor().stopCellEditing();
            for (int i = 0; i < Math.min(rows.size(), model.getRowCount()); i++)
                for (int c = 0; c < cols.length; c++) {
                    Object v = model.getValueAt(i, c + 1);
                    rows.get(i)[c] = v == null ? "" : v.toString().trim();
                }
            return;
        }
        if (fields.size() != rows.size()) return;
        for (int i = 0; i < rows.size(); i++)
            for (int c = 0; c < cols.length; c++) rows.get(i)[c] = fields.get(i)[c].getText().trim();
    }

    private void discard() {
        fields.clear();
        table = null;
        model = null;
        invalid.clear();
    }

    // ---------- invalid-cell marking ----------

    public void clearInvalid() {
        invalid.clear();
        for (RoundField[] f : fields) for (RoundField x : f) x.setInvalid(false);
        if (table != null) table.repaint();
    }

    /** Turns one cell red (box border, or the table cell's background). */
    public void markInvalid(int row, int col) {
        invalid.add((long) row * 64 + col);
        if (table == null && row < fields.size()) fields.get(row)[col].setInvalid(true);
        if (table != null) table.repaint();
    }

    // ---------- building ----------

    /**
     * The whole box: title + note on top, the boxes or the table, then the +1 / +5 / −1 / Set / Paste row.
     * resize is called with the new count, bulk with the pasted rows; the module pushes Undo and rebuilds.
     */
    public JComponent box(String title, String unit, String footnote, String legend, IntConsumer resize, Consumer<List<String[]>> bulk) {
        save();
        discard();
        Box12 b = new Box12(null);
        b.setLayout(new BoxLayout(b, BoxLayout.Y_AXIS));

        JPanel head = hRow();
        head.add(FigmaModule.pixelText(title, 7f, Theme.dark ? new Color(0xff8fd0) : Theme.pink(), 0f));
        head.add(Box.createHorizontalStrut(10));
        head.add(FigmaModule.body(rows.size() + " " + unit + (rows.size() == 1 ? "" : "s")
                + (footnote == null ? "" : " · " + footnote), 11, Theme.muted()));
        b.add(head);
        b.add(Box.createVerticalStrut(6));
        b.add(!alwaysTable && rows.size() <= BOX_LIMIT ? boxes() : table());
        b.add(Box.createVerticalStrut(8));
        b.add(controls(resize, bulk, unit));
        b.add(Box.createVerticalStrut(4));
        JPanel note = hRow();
        note.add(FigmaModule.body(alwaysTable ? legend : legend + " · table at " + (BOX_LIMIT + 1) + "+ " + unit + "s",
                11, Theme.muted()));
        b.add(note);
        return b;
    }

    private JComponent boxes() {
        JPanel grid = new JPanel();
        grid.setOpaque(false);
        grid.setLayout(new BoxLayout(grid, BoxLayout.Y_AXIS));
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel ids = hRow();
        ids.add(cell(idHead, 44, 22, Theme.muted()));
        JPanel[] lines = new JPanel[cols.length];
        for (int c = 0; c < cols.length; c++) {
            lines[c] = hRow();
            lines[c].add(cell(cols[c], 44, 30, Theme.muted()));
        }
        for (int i = 0; i < rows.size(); i++) {
            RoundField[] f = new RoundField[cols.length];
            for (int c = 0; c < cols.length; c++) {
                f[c] = new RoundField(rows.get(i)[c], 6, boxW, 30);
                f[c].addActionListener(e -> onEnter.run());
                final int row = i, col = c;
                f[c].addKeyListener(new KeyAdapter() {   // Up/Down moves between the value rows
                    @Override public void keyPressed(KeyEvent e) {
                        int to = e.getKeyCode() == KeyEvent.VK_DOWN ? col + 1 : e.getKeyCode() == KeyEvent.VK_UP ? col - 1 : -1;
                        if (to >= 0 && to < cols.length && row < fields.size()) fields.get(row)[to].requestFocusInWindow();
                    }
                });
                lines[c].add(Box.createHorizontalStrut(6));
                lines[c].add(f[c]);
            }
            fields.add(f);
            ids.add(Box.createHorizontalStrut(6));
            ids.add(cell(idOf.apply(i), boxW, 22, Theme.text()));
        }
        grid.add(ids);
        for (JPanel l : lines) { grid.add(Box.createVerticalStrut(6)); grid.add(l); }
        return grid;
    }

    private JComponent table() {
        String[] head = new String[cols.length + 1];
        head[0] = idHead;
        System.arraycopy(cols, 0, head, 1, cols.length);
        model = new DefaultTableModel(head, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int row, int col) { return col > 0; }
        };
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = new Object[cols.length + 1];
            r[0] = idOf.apply(i);
            for (int c = 0; c < cols.length; c++) r[c + 1] = rows.get(i)[c];
            model.addRow(r);
        }
        table = new JTable(model);
        table.setFont(Fonts.body(14f));
        table.setRowHeight(28);
        table.setForeground(Theme.text());
        table.setBackground(Theme.input());
        table.setGridColor(Theme.line());
        table.setShowGrid(true);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        DefaultTableCellRenderer center = new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int r, int c) {
                Component comp = super.getTableCellRendererComponent(tb, v, s, f, r, c);
                boolean bad = c > 0 && invalid.contains((long) r * 64 + (c - 1));
                if (bad) comp.setBackground(new Color(255, 79, 168, Theme.dark ? 90 : 60));
                else if (!s) comp.setBackground(r % 2 == 0 ? Theme.panel() : Theme.panel2());
                comp.setForeground(bad ? Theme.pink() : c == 0 ? Theme.muted() : Theme.text());
                return comp;
            }
        };
        center.setHorizontalAlignment(SwingConstants.CENTER);
        table.setDefaultRenderer(Object.class, center);
        JTableHeader th = table.getTableHeader();
        th.setFont(Fonts.label(9f));
        th.setBackground(Theme.panel2());
        th.setForeground(Theme.muted());
        table.getColumnModel().getColumn(0).setPreferredWidth(64);
        for (int c = 1; c <= cols.length; c++) table.getColumnModel().getColumn(c).setPreferredWidth(180);
        // Enter on the last cell runs the simulation
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, java.awt.event.InputEvent.CTRL_DOWN_MASK), "runSim");
        table.getActionMap().put("runSim", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { onEnter.run(); }
        });

        JScrollPane sp = new JScrollPane(table);
        sp.setAlignmentX(Component.LEFT_ALIGNMENT);
        sp.setBorder(BorderFactory.createLineBorder(Theme.inputLine(), 1));
        // fit the rows (header + one line each), up to 210px, then scroll; no grey gap under short tables
        int h = Math.min(210, table.getTableHeader().getPreferredSize().height + table.getRowHeight() * rows.size() + 3);
        sp.setPreferredSize(new Dimension(460, h));
        sp.setMaximumSize(new Dimension(560, h));
        table.setFillsViewportHeight(true);
        sp.getViewport().setBackground(Theme.panel());
        FigmaModule.styleScrollPane(sp);
        return sp;
    }

    /** +1 · +5 · −1 · [count] Set · Paste / Bulk */
    private JComponent controls(IntConsumer resize, Consumer<List<String[]>> bulk, String unit) {
        JPanel row = hRow();
        row.add(pill("+1", () -> resize.accept(rows.size() + 1)));
        row.add(Box.createHorizontalStrut(6));
        row.add(pill("+5", () -> resize.accept(rows.size() + 5)));
        if (rows.size() > 1) {
            row.add(Box.createHorizontalStrut(6));
            row.add(pill("−1", () -> resize.accept(rows.size() - 1)));
        }
        row.add(Box.createHorizontalStrut(10));
        RoundField count = new RoundField(String.valueOf(rows.size()), 6, 48, 28);
        Runnable apply = () -> {
            try {
                int n = Integer.parseInt(count.getText().trim());
                if (n >= 1) { resize.accept(n); return; }
            } catch (NumberFormatException ignored) { }
            count.setText(String.valueOf(rows.size()));
            FigmaModule.showToast(count, "Type how many " + unit + "s you want (1 or more)");
        };
        count.addActionListener(e -> apply.run());
        row.add(count);
        row.add(Box.createHorizontalStrut(4));
        row.add(pill("Set", apply));
        row.add(Box.createHorizontalStrut(8));
        DashedPill paste = new DashedPill("Paste / Bulk");
        paste.addActionListener(e -> {
            List<String[]> parsed = bulkDialog(paste, unit);
            if (parsed != null) bulk.accept(parsed);
        });
        row.add(paste);
        return row;
    }

    /** One entry per line ("a, b" or tab-separated from Excel); an extra leading ID column is skipped. */
    private List<String[]> bulkDialog(Component parent, String unit) {
        save();
        JTextArea area = new JTextArea(10, 28);
        area.setFont(Fonts.body(14f));
        StringBuilder existing = new StringBuilder();
        for (String[] r : rows) {
            boolean any = false;
            for (String v : r) any |= !v.isEmpty();
            if (any) existing.append(String.join(", ", r)).append('\n');
        }
        area.setText(existing.toString());
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JLabel("Paste one " + unit + " per line (" + String.join(", ", cols) + " - or tab-separated from Excel):"),
                BorderLayout.NORTH);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        int ok = JOptionPane.showConfirmDialog(parent, panel, "Bulk Paste " + Character.toUpperCase(unit.charAt(0)) + unit.substring(1) + "s",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return null;

        List<String[]> parsed = new ArrayList<>();
        for (String line : area.getText().split("\\R")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            String[] parts = t.split("[\\t,;\\s]+");
            // a leading label that is not a number (e.g. "J1" or "P1") is an ID column: skip it
            int skip = parts.length > 1 && !parts[0].matches("-?\\d+") && !parts[0].equals("-") ? 1 : 0;
            if (parts.length - skip < 2 && cols.length > 1) continue;   // need at least the first two values
            String[] r = new String[cols.length];
            for (int c = 0; c < cols.length; c++) r[c] = skip + c < parts.length ? parts[skip + c].trim() : "";   // missing -> blank
            parsed.add(r);
        }
        if (parsed.isEmpty()) { FigmaModule.showToast(parent, "Nothing to import - use one " + unit + " per line"); return null; }
        return parsed;
    }

    // ---------- small helpers ----------

    private static DashedPill pill(String label, Runnable action) {
        DashedPill p = new DashedPill(label);
        p.addActionListener(e -> action.run());
        return p;
    }

    private static JPanel hRow() {
        JPanel r = new JPanel();
        r.setOpaque(false);
        r.setLayout(new BoxLayout(r, BoxLayout.X_AXIS));
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        return r;
    }

    private static JLabel cell(String s, int w, int h, Color c) {
        JLabel l = new FigmaModule.SafeLabel(s, SwingConstants.CENTER);
        l.setFont(Fonts.label(9f));
        l.setForeground(c);
        Dimension d = new Dimension(w, h);
        l.setPreferredSize(d); l.setMinimumSize(d); l.setMaximumSize(d);
        return l;
    }
}
