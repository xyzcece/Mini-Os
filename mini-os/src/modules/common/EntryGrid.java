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

    /** true = always the scrolling Excel-style table, even for a few entries. */
    private boolean alwaysTable = true;
    private String[] colTips;

    /** Always show the scrolling table, whatever the number of entries. Returns this for chaining. */
    public EntryGrid alwaysTable() { alwaysTable = true; return this; }

    /** Optional tooltips shown when hovering over column headers. */
    public EntryGrid headerTips(String... tips) { colTips = tips; return this; }

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
        b.add(!alwaysTable && rows.size() <= BOX_LIMIT ? boxes() : table(resize, bulk, unit));
        b.add(Box.createVerticalStrut(8));
        b.add(controls(resize, bulk, unit));
        b.add(Box.createVerticalStrut(4));
        JPanel note = hRow();
        note.add(FigmaModule.body(legend + " · click cell to type · Enter/Tab next · Ctrl+C/V Excel · × or Del to remove",
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

    private JComponent table(IntConsumer resize, Consumer<List<String[]>> bulk, String unit) {
        boolean showDel = rows.size() > 1;
        String[] head = new String[cols.length + 1 + (showDel ? 1 : 0)];
        head[0] = idHead;
        System.arraycopy(cols, 0, head, 1, cols.length);
        if (showDel) head[cols.length + 1] = "";
        model = new DefaultTableModel(head, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int row, int col) { return col > 0 && col <= cols.length; }
        };
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = new Object[head.length];
            r[0] = idOf.apply(i);
            for (int c = 0; c < cols.length; c++) r[c + 1] = rows.get(i)[c];
            if (showDel) r[cols.length + 1] = "×";
            model.addRow(r);
        }
        table = new JTable(model) {
            private static final long serialVersionUID = 1L;
            @Override public void changeSelection(int row, int col, boolean toggle, boolean extend) {
                // Keep multi-row selection intact when clicking × on any selected row
                if (showDel && col == cols.length + 1 && !toggle && !extend && getSelectedRowCount() > 1 && isRowSelected(row)) {
                    return;
                }
                super.changeSelection(row, col, toggle, extend);
            }
        };
        table.setFont(Fonts.body(14f));
        table.setRowHeight(34);
        table.setRowMargin(2);
        table.setIntercellSpacing(new Dimension(6, 2));
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setForeground(Theme.text());
        table.setBackground(Theme.input());
        table.setGridColor(Theme.line());
        table.setShowGrid(true);
        table.setSurrendersFocusOnKeystroke(true);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        JTextField editorField = new JTextField();
        editorField.setFont(Fonts.body(14f));
        editorField.setHorizontalAlignment(SwingConstants.CENTER);
        DefaultCellEditor excelEditor = new DefaultCellEditor(editorField) {
            private static final long serialVersionUID = 1L;
            @Override public Component getTableCellEditorComponent(JTable tb, Object val, boolean isSel, int r, int c) {
                JTextField tf = (JTextField) super.getTableCellEditorComponent(tb, val, isSel, r, c);
                SwingUtilities.invokeLater(tf::selectAll);
                return tf;
            }
        };
        excelEditor.setClickCountToStart(1);
        table.setDefaultEditor(Object.class, excelEditor);
        // Pressing Enter while actively typing in a cell commits AND advances to the next cell in 1 keypress
        editorField.addActionListener(e -> SwingUtilities.invokeLater(() -> {
            if (table != null && table.getActionMap().get("excelNext") != null) {
                table.getActionMap().get("excelNext").actionPerformed(null);
            }
        }));
        model.addTableModelListener(e -> {
            if (e.getType() == javax.swing.event.TableModelEvent.UPDATE && e.getFirstRow() >= 0 && e.getColumn() > 0 && e.getColumn() <= cols.length) {
                invalid.remove((long) e.getFirstRow() * 64 + (e.getColumn() - 1));
                table.repaint();
            }
        });
        final int[] hoverRow = {-1};
        final int[] hoverCol = {-1};
        DefaultTableCellRenderer center = new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int r, int c) {
                Component comp = super.getTableCellRendererComponent(tb, v, s, f, r, c);
                boolean isDel = showDel && c == cols.length + 1;
                boolean bad = c > 0 && c <= cols.length && invalid.contains((long) r * 64 + (c - 1));
                if (bad) {
                    comp.setBackground(new Color(255, 79, 168, Theme.dark ? 90 : 60));
                } else if (isDel && hoverRow[0] == r && hoverCol[0] == c) {
                    comp.setBackground(new Color(255, 79, 168, Theme.dark ? 85 : 55));
                } else if (!s) {
                    if (hoverRow[0] == r) {
                        comp.setBackground(Theme.dark ? new Color(255, 255, 255, 18) : new Color(255, 79, 168, 18));
                    } else {
                        comp.setBackground(r % 2 == 0 ? Theme.panel() : Theme.panel2());
                    }
                }
                comp.setForeground(isDel ? Theme.pink() : bad ? Theme.pink() : c == 0 ? Theme.muted() : Theme.text());
                if (comp instanceof JComponent jc) jc.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                return comp;
            }
        };
        center.setHorizontalAlignment(SwingConstants.CENTER);
        table.setDefaultRenderer(Object.class, center);
        JTableHeader th = new JTableHeader(table.getColumnModel()) {
            private static final long serialVersionUID = 1L;
            @Override public String getToolTipText(java.awt.event.MouseEvent e) {
                int col = columnAtPoint(e.getPoint());
                if (col < 0) return null;
                if (showDel && col == cols.length + 1) return "Click × on a row to delete it";
                if (col == 0) return "Row identifier (" + idHead + ")";
                int idx = col - 1;
                if (colTips != null && idx < colTips.length && colTips[idx] != null) return colTips[idx];
                return defaultColTip(cols[idx]);
            }
        };
        table.setTableHeader(th);
        ToolTipManager.sharedInstance().registerComponent(th);
        th.setFont(Fonts.label(10f));
        th.setPreferredSize(new Dimension(th.getPreferredSize().width, 32));
        th.setBackground(Theme.panel2());
        th.setForeground(Theme.muted());
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        for (int c = 1; c <= cols.length; c++) table.getColumnModel().getColumn(c).setPreferredWidth(190);
        table.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override public void mouseMoved(java.awt.event.MouseEvent e) {
                int r = table.rowAtPoint(e.getPoint()), c = table.columnAtPoint(e.getPoint());
                if (r != hoverRow[0] || c != hoverCol[0]) {
                    hoverRow[0] = r; hoverCol[0] = c;
                    table.setCursor(showDel && c == cols.length + 1
                            ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                    table.repaint();
                }
            }
        });
        if (showDel) {
            table.getColumnModel().getColumn(cols.length + 1).setPreferredWidth(40);
            table.getColumnModel().getColumn(cols.length + 1).setMaxWidth(46);
            table.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseExited(java.awt.event.MouseEvent e) {
                    hoverRow[0] = -1; hoverCol[0] = -1;
                    table.setCursor(Cursor.getDefaultCursor());
                    table.repaint();
                }
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    int col = table.columnAtPoint(e.getPoint());
                    int row = table.rowAtPoint(e.getPoint());
                    if (col == cols.length + 1 && row >= 0 && row < rows.size() && rows.size() > 1) {
                        int[] sel = table.getSelectedRows();
                        boolean multi = sel.length > 1;
                        if (multi) {
                            removeSelectedRows(bulk, unit);
                            return;
                        }
                        save();
                        String removedId = idOf.apply(row);
                        List<String[]> next = new ArrayList<>();
                        for (int i = 0; i < rows.size(); i++) {
                            if (i != row) next.add(rows.get(i).clone());
                        }
                        bulk.accept(next);
                        FigmaModule.showToast(table, "Removed " + removedId + " · Ctrl+Z to undo");
                    }
                }
            });
        }
        // Ctrl+Enter runs simulation; Ctrl+V pastes directly from Excel/Notepad
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, java.awt.event.InputEvent.CTRL_DOWN_MASK), "runSim");
        table.getActionMap().put("runSim", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { onEnter.run(); }
        });
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_V, java.awt.event.InputEvent.CTRL_DOWN_MASK), "pasteExcel");
        table.getActionMap().put("pasteExcel", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                try {
                    String clip = (String) Toolkit.getDefaultToolkit().getSystemClipboard()
                            .getData(java.awt.datatransfer.DataFlavor.stringFlavor);
                    List<String[]> parsed = parseBulk(clip);
                    if (!parsed.isEmpty()) {
                        bulk.accept(parsed);
                        FigmaModule.showToast(table, "Pasted " + parsed.size() + " " + unit + "(s) from clipboard!");
                    }
                } catch (Exception ignored) {}
            }
        });

        table.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "clearCells");
        table.getActionMap().put("clearCells", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { clearSelectedCells(); }
        });
        table.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_C, java.awt.event.InputEvent.CTRL_DOWN_MASK), "copyExcel");
        table.getActionMap().put("copyExcel", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { copySelectedRows(unit); }
        });
        // Excel-style Enter / Tab navigation; Enter on the last row adds a new row
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "excelNext");
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), "excelNext");
        table.getActionMap().put("excelNext", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                int r = table.getSelectedRow(), c = table.getSelectedColumn();
                if (table.isEditing()) table.getCellEditor().stopCellEditing();
                if (r < 0) r = 0;
                if (c < 1) c = 1;
                if (c < cols.length) {
                    table.changeSelection(r, c + 1, false, false);
                } else if (r + 1 < rows.size()) {
                    table.changeSelection(r + 1, 1, false, false);
                } else {
                    resize.accept(rows.size() + 1);
                    SwingUtilities.invokeLater(() -> {
                        if (table != null && table.getRowCount() > 0) {
                            table.changeSelection(table.getRowCount() - 1, 1, false, false);
                            table.requestFocusInWindow();
                        }
                    });
                }
            }
        });

        JScrollPane sp = new JScrollPane(table);
        sp.setAlignmentX(Component.LEFT_ALIGNMENT);
        sp.setBorder(BorderFactory.createLineBorder(Theme.inputLine(), 1));
        sp.getVerticalScrollBar().setUnitIncrement(28);
        // fit the rows (header + one line each), up to 260px, then scroll; no grey gap under short tables
        int h = Math.min(260, table.getTableHeader().getPreferredSize().height + table.getRowHeight() * rows.size() + 4);
        sp.setPreferredSize(new Dimension(660, h));
        sp.setMaximumSize(new Dimension(860, h));
        table.setFillsViewportHeight(true);
        sp.getViewport().setBackground(Theme.panel());
        FigmaModule.styleScrollPane(sp);
        return sp;
    }

    private void copySelectedRows(String unit) {
        if (table == null) return;
        save();
        int[] sel = table.getSelectedRows();
        if (sel.length == 0) {
            sel = new int[rows.size()];
            for (int i = 0; i < rows.size(); i++) sel[i] = i;
        }
        StringBuilder sb = new StringBuilder();
        for (int r : sel) {
            if (r < 0 || r >= rows.size()) continue;
            sb.append(String.join("\t", rows.get(r))).append('\n');
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(sb.toString()), null);
        FigmaModule.showToast(table, "Copied " + sel.length + " " + unit + (sel.length == 1 ? "" : "s") + " (Ctrl+V in Excel)");
    }

    private static String defaultColTip(String col) {
        return switch (col.toUpperCase()) {
            case "AT" -> "Arrival Time — when the job enters the ready queue";
            case "RT" -> "Run / Burst Time — total CPU time required";
            case "BT" -> "Burst Time — CPU time needed by the job";
            case "PRI" -> "Priority — lower number = higher priority (1 is highest)";
            case "FRAME" -> "Physical memory frame number (- = not in memory / page fault)";
            case "LIMIT" -> "Segment Limit — maximum valid offset length for this segment";
            case "BASE" -> "Base Address — starting physical address of this segment";
            default -> col;
        };
    }

    private void clearSelectedCells() {
        if (table == null) return;
        int[] selRows = table.getSelectedRows();
        int[] selCols = table.getSelectedColumns();
        if (selRows.length == 0 || selCols.length == 0) return;
        for (int r : selRows) {
            for (int c : selCols) {
                if (c > 0 && c <= cols.length) {
                    model.setValueAt("", r, c);
                    rows.get(r)[c - 1] = "";
                }
            }
        }
    }

    private void removeSelectedRows(Consumer<List<String[]>> bulk, String unit) {
        if (table == null || rows.size() <= 1) return;
        int[] sel = table.getSelectedRows();
        if (sel.length == 0) {
            FigmaModule.showToast(table, "Select row(s) first (Ctrl/Shift+Click) to remove");
            return;
        }
        if (sel.length >= rows.size()) {
            FigmaModule.showToast(table, "At least 1 " + unit + " must remain");
            return;
        }
        save();
        Set<Integer> drop = new HashSet<>();
        for (int r : sel) drop.add(r);
        List<String[]> next = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (!drop.contains(i)) next.add(rows.get(i).clone());
        }
        String label = sel.length == 1 ? idOf.apply(sel[0]) : sel.length + " " + unit + "s";
        bulk.accept(next);
        FigmaModule.showToast(table, "Removed " + label + " · Ctrl+Z to undo");
    }

    /** +1 · +5 · −1 · Remove selected · [count] Set · Paste / Bulk (wraps cleanly on narrow/scaled screens) */
    private JComponent controls(IntConsumer resize, Consumer<List<String[]>> bulk, String unit) {
        JPanel row = new JPanel(new FigmaModule.WrapLayout(FlowLayout.LEFT, 6, 4));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(pill("+1", () -> resize.accept(rows.size() + 1)));
        row.add(pill("+5", () -> resize.accept(rows.size() + 5)));
        if (rows.size() > 1) {
            row.add(pill("−1", () -> resize.accept(rows.size() - 1)));
        }
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
        row.add(pill("Set", apply));
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

        List<String[]> parsed = parseBulk(area.getText());
        if (parsed.isEmpty()) { FigmaModule.showToast(parent, "Nothing to import - use one " + unit + " per line"); return null; }
        return parsed;
    }

    private List<String[]> parseBulk(String text) {
        List<String[]> parsed = new ArrayList<>();
        if (text == null) return parsed;
        boolean firstLine = true;
        for (String line : text.split("\\R")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            // Skip Excel header row (e.g. "J#\tAT\tRT" or "LIMIT\tBASE") if it has no digits
            if (firstLine && !t.matches(".*\\d.*")) {
                firstLine = false;
                continue;
            }
            firstLine = false;
            // Strip thousands commas (1,000 -> 1000) and support tab/semicolon/space separation
            String norm = t.replaceAll("(?<=\\d),(?=\\d{3}(\\D|$))", "");
            String[] parts = norm.contains("\t") ? norm.split("\\t+") : norm.split("[\\t,;\\s]+");
            // a leading label that is not a number (e.g. "J1" or "P1") is an ID column: skip it
            int skip = parts.length > 1 && !parts[0].trim().matches("-?\\d+([.,]\\d+)?") && !parts[0].trim().equals("-") ? 1 : 0;
            if (parts.length - skip < 2 && cols.length > 1) continue;   // need at least the first two values
            String[] r = new String[cols.length];
            for (int c = 0; c < cols.length; c++) {
                String val = skip + c < parts.length ? parts[skip + c].trim() : "";
                if (val.matches("-?\\d+,\\d{1,2}")) val = val.replace(',', '.');
                r[c] = val;
            }
            parsed.add(r);
        }
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
