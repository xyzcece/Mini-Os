package modules.multiprogramming;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import modules.common.FigmaModule;
import shared.Fonts;
import shared.Theme;

/**
 * MULTIPROGRAMMING  —  OWNER: Member 2
 *   [x] Beginner-friendly JOBS grid (J#, AT, BT) + auto-switches to vertical JTable at 8+ jobs
 *   [x] Dynamic job count: +1, +5, -1, user-specified [Count] [Set], and Paste / Bulk import
 *   [x] Event table matching class format: TIME, EVENT, NJ, %CPU/J, ET, HW/J, J#, TIMELEFT
 *   [x] %cpu/j = (1 - p^n) / n  (when p = 0%: 1 / n) and HW/J = ET * (%cpu/j)
 * CHECK:
 *   - Teacher example (AT 7.4..8.4, BT 0.4..0.1, 0% I/O wait): last job done at 9.1
 *   - 80% I/O wait example (10:00..10:31.6): last job done at 10:31.6
 */
public class MultiprogrammingModule extends FigmaModule {
    private static final long serialVersionUID = 1L;
    private static final double EPS = 1e-6;

    private final transient List<String[]> jobs = new ArrayList<>();
    { for (int i = 0; i < 5; i++) jobs.add(new String[]{"", ""}); }
    private final transient List<RoundField[]> jobFields = new ArrayList<>();
    private JTable jobTable;
    private DefaultTableModel jobTableModel;
    private RoundField ioWait, start, countField;
    private String ioWaitText = "", startText = "";
    private boolean hasRun = false;
    private final transient List<HistoryItem> history = new ArrayList<>();
    private final transient Deque<UndoState> undoStack = new ArrayDeque<>();

    private record HistoryItem(String label, List<String[]> rows, String ioWait, String start) {}
    private record UndoState(List<String[]> rows, String ioWait, String start) {}

    @Override public String title()    { return "Multiprogramming"; }
    @Override public String subtitle() { return "Share the CPU between jobs and track the time each job has left."; }
    @Override protected String crumb() { return "MULTIPROGRAMMING"; }
    @Override protected String lead()  { return "Enter each job's arrival time (AT) and burst time (BT), then press RUN SIMULATION."; }
    @Override protected int inputsTop() { return 4; }

    // ============================ INPUTS ============================

    @Override protected void buildInputs(JPanel p) {
        saveFields();
        jobFields.clear();
        jobTable = null;
        jobTableModel = null;
        ioWait = null;
        start = null;
        countField = null;

        JComponent jobBox = jobBox(), settings = settings();
        JPanel grid = new JPanel(null) {
            private static final long serialVersionUID = 1L;
            private boolean assumedSide = true;
            private boolean side() { return getWidth() == 0 || getWidth() >= jobBox.getPreferredSize().width + 16 + 200; }
            @Override public void doLayout() {
                Dimension j = jobBox.getPreferredSize(), s = settings.getPreferredSize();
                boolean side = side();
                if (side) {
                    int jbW = Math.min(j.width, Math.max(280, getWidth() - 16 - 200));
                    jobBox.setBounds(0, 0, jbW, j.height);
                    settings.setBounds(jbW + 16, 0, getWidth() - jbW - 16, s.height);
                } else {
                    jobBox.setBounds(0, 0, Math.min(j.width, getWidth()), j.height);
                    settings.setBounds(0, j.height + 14, getWidth(), s.height);
                }
                if (side != assumedSide) SwingUtilities.invokeLater(this::revalidate);
            }
            @Override public Dimension getPreferredSize() {
                Dimension j = jobBox.getPreferredSize(), s = settings.getPreferredSize();
                assumedSide = side();
                return new Dimension(300, assumedSide ? Math.max(j.height, s.height) : j.height + 14 + s.height);
            }
        };
        grid.setOpaque(false);
        grid.add(jobBox);
        grid.add(settings);
        grid.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { grid.revalidate(); }
        });
        p.add(grid, BorderLayout.CENTER);

        // Quick preset pills underneath
        JPanel tools = new JPanel();
        tools.setOpaque(false);
        tools.setLayout(new WrapLayout(FlowLayout.LEFT, 0, 6));   // wraps onto a 2nd line when narrow
        tools.setBorder(new EmptyBorder(10, 0, 0, 0));

        DashedPill teacherBtn = new DashedPill("Sample (Teacher 7.4–9.1)");
        teacherBtn.addActionListener(e -> {
            pushUndo();
            hasRun = false;
            loadPreset(new String[][]{{"7.4", "0.4"}, {"7.9", "0.6"}, {"8.1", "0.3"}, {"8.3", "0.2"}, {"8.4", "0.1"}},
                    "0", "7.4");
            showToast(this, "Loaded Teacher example — press RUN SIMULATION");
        });

        DashedPill io80Btn = new DashedPill("Sample (80% I/O)");
        io80Btn.addActionListener(e -> {
            pushUndo();
            hasRun = false;
            loadPreset(new String[][]{{"10:00", "4"}, {"10:10", "3"}, {"10:15", "2"}, {"10:20", "2"}},
                    "80", "10:00");
            showToast(this, "Loaded 80% I/O example — press RUN SIMULATION");
        });

        DashedPill largeBtn = new DashedPill("Large sample (15)");
        largeBtn.addActionListener(e -> {
            pushUndo();
            hasRun = false;
            loadPreset(new String[][]{
                        {"0", "2.0"}, {"0.5", "1.5"}, {"1.0", "2.5"}, {"1.5", "1.0"}, {"2.0", "1.8"},
                        {"2.5", "1.2"}, {"3.0", "2.2"}, {"3.5", "0.8"}, {"4.0", "1.6"}, {"4.5", "1.4"},
                        {"5.0", "2.0"}, {"5.5", "1.1"}, {"6.0", "0.9"}, {"6.5", "1.3"}, {"7.0", "1.0"}
                }, "0", "0");
            showToast(this, "Loaded 15 jobs — press RUN SIMULATION");
        });

        DashedPill histBtn = new DashedPill("History");
        histBtn.addActionListener(e -> showHistoryMenu(histBtn));

        DashedPill undoBtn = new DashedPill("Undo");
        undoBtn.addActionListener(e -> {
            if (undoStack.isEmpty()) {
                showToast(this, "Nothing to undo");
                return;
            }
            UndoState prev = undoStack.pop();
            String[][] arr = prev.rows().toArray(new String[0][]);
            hasRun = false;
            loadPreset(arr, prev.ioWait(), prev.start());
            showToast(this, "Undid last change");
        });

        DashedPill clearBtn = new DashedPill("Clear");
        clearBtn.addActionListener(e -> {
            pushUndo();
            hasRun = false;
            loadPreset(new String[][]{{"", ""}, {"", ""}, {"", ""}, {"", ""}, {"", ""}}, "", "");
            showToast(this, "Cleared inputs");
        });

        tools.add(teacherBtn);
        tools.add(Box.createHorizontalStrut(8));
        tools.add(io80Btn);
        tools.add(Box.createHorizontalStrut(8));
        tools.add(largeBtn);
        tools.add(Box.createHorizontalStrut(8));
        tools.add(histBtn);
        tools.add(Box.createHorizontalStrut(8));
        tools.add(undoBtn);
        tools.add(Box.createHorizontalStrut(8));
        tools.add(clearBtn);
        tools.add(Box.createHorizontalStrut(8));
        DashedPill badBtn = new DashedPill("Invalid input test");
        badBtn.addActionListener(e -> {
            pushUndo();
            hasRun = true;
            loadPreset(new String[][]{{"7.4", "0.4"}, {"abc", "0.3"}, {"7.6", "-1"}, {"8.0", ""}}, "150", "");
            showToast(this, "Loaded bad input - the result card shows what to fix");
        });
        tools.add(badBtn);
        p.add(tools, BorderLayout.SOUTH);
    }

    private void pushUndo() {
        saveFields();
        List<String[]> copy = new ArrayList<>();
        for (String[] j : jobs) copy.add(new String[]{j[0], j[1]});
        undoStack.push(new UndoState(copy, ioWaitText, startText));
        if (undoStack.size() > 15) undoStack.removeLast();
    }

    private void loadPreset(String[][] data, String io, String st) {
        jobs.clear();
        for (String[] row : data) jobs.add(new String[]{row[0], row[1]});
        ioWaitText = io;
        startText = st;
        jobFields.clear();
        jobTable = null;
        jobTableModel = null;
        ioWait = null;
        start = null;
        rebuildInputs();
        rerun();
    }

    /** "JOBS" box: horizontal boxes for 1-7 jobs, compact scrollable JTable for 8+ jobs. */
    private JComponent jobBox() {
        Box12 b = new Box12(null);
        b.setLayout(new BoxLayout(b, BoxLayout.Y_AXIS));

        JPanel head = hRow();
        head.add(pixelText("JOBS", 7f, Theme.dark ? new Color(0xff8fd0) : Theme.pink(), 0f));
        head.add(Box.createHorizontalStrut(10));
        head.add(body(jobs.size() + (jobs.size() == 1 ? " job" : " jobs") + " · decimal or HH:mm", 11, Theme.muted()));
        b.add(head);
        b.add(Box.createVerticalStrut(6));

        if (jobs.size() <= 7) {
            JPanel ids = hRow(), at = hRow(), bt = hRow();
            ids.add(cell("J#", 36, 22, Theme.muted()));
            at.add(cell("AT", 36, 30, Theme.muted()));
            bt.add(cell("BT", 36, 30, Theme.muted()));

            for (int i = 0; i < jobs.size(); i++) {
                final int idx = i;
                RoundField a = new RoundField(jobs.get(i)[0], 6, 56, 30);
                RoundField r = new RoundField(jobs.get(i)[1], 6, 56, 30);
                // Enter runs simulation; Up/Down moves between AT and BT
                a.addActionListener(e -> onRunPressed());
                r.addActionListener(e -> onRunPressed());
                a.addKeyListener(new KeyAdapter() {
                    @Override public void keyPressed(KeyEvent e) {
                        if (e.getKeyCode() == KeyEvent.VK_DOWN && idx < jobFields.size()) {
                            jobFields.get(idx)[1].requestFocusInWindow();
                        }
                    }
                });
                r.addKeyListener(new KeyAdapter() {
                    @Override public void keyPressed(KeyEvent e) {
                        if (e.getKeyCode() == KeyEvent.VK_UP && idx < jobFields.size()) {
                            jobFields.get(idx)[0].requestFocusInWindow();
                        }
                    }
                });
                jobFields.add(new RoundField[]{a, r});
                ids.add(Box.createHorizontalStrut(6)); ids.add(cell("J" + (i + 1), 56, 22, Theme.text()));
                at.add(Box.createHorizontalStrut(6));  at.add(a);
                bt.add(Box.createHorizontalStrut(6));  bt.add(r);
            }
            b.add(ids); b.add(Box.createVerticalStrut(6));
            b.add(at);  b.add(Box.createVerticalStrut(6));
            b.add(bt);
        } else {
            // Vertical JTable for 8+ jobs: virtualized, fixed-height scroll, fast Excel-like entry
            jobTableModel = new DefaultTableModel(new String[]{"J#", "AT", "BT"}, 0) {
                private static final long serialVersionUID = 1L;
                @Override public boolean isCellEditable(int row, int col) { return col > 0; }
            };
            for (int i = 0; i < jobs.size(); i++) {
                jobTableModel.addRow(new Object[]{"J" + (i + 1), jobs.get(i)[0], jobs.get(i)[1]});
            }
            jobTable = new JTable(jobTableModel);
            jobTable.setFont(Fonts.body(14f));
            jobTable.setRowHeight(28);
            jobTable.setForeground(Theme.text());
            jobTable.setBackground(Theme.input());
            jobTable.setGridColor(Theme.line());
            jobTable.setShowGrid(true);
            jobTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
            DefaultTableCellRenderer center = new DefaultTableCellRenderer() {
                private static final long serialVersionUID = 1L;
                @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int r, int c) {
                    Component comp = super.getTableCellRendererComponent(tb, v, s, f, r, c);
                    if (!s) comp.setBackground(r % 2 == 0 ? Theme.panel() : Theme.panel2());
                    comp.setForeground(c == 0 ? Theme.muted() : Theme.text());
                    return comp;
                }
            };
            center.setHorizontalAlignment(SwingConstants.CENTER);
            jobTable.setDefaultRenderer(Object.class, center);
            JTableHeader th = jobTable.getTableHeader();
            th.setFont(Fonts.label(9f));
            th.setBackground(Theme.panel2());
            th.setForeground(Theme.muted());
            jobTable.getColumnModel().getColumn(0).setPreferredWidth(64);
            jobTable.getColumnModel().getColumn(1).setPreferredWidth(180);
            jobTable.getColumnModel().getColumn(2).setPreferredWidth(180);

            JScrollPane sp = new JScrollPane(jobTable);
            sp.setAlignmentX(LEFT_ALIGNMENT);
            sp.setBorder(BorderFactory.createLineBorder(Theme.inputLine(), 1));
            sp.setPreferredSize(new Dimension(460, 210));
            sp.setMaximumSize(new Dimension(560, 210));
            styleScrollPane(sp);
            b.add(sp);
        }
        b.add(Box.createVerticalStrut(8));

        // Controls row: +1, +5, -1, [Count] [Set], Paste / Bulk
        JPanel foot1 = hRow();
        DashedPill add1 = new DashedPill("+1");
        add1.addActionListener(e -> resizeJobs(jobs.size() + 1));
        DashedPill add5 = new DashedPill("+5");
        add5.addActionListener(e -> resizeJobs(jobs.size() + 5));
        foot1.add(add1);
        foot1.add(Box.createHorizontalStrut(6));
        foot1.add(add5);
        if (jobs.size() > 1) {
            DashedPill rem = new DashedPill("−1");
            rem.addActionListener(e -> resizeJobs(jobs.size() - 1));
            foot1.add(Box.createHorizontalStrut(6));
            foot1.add(rem);
        }
        foot1.add(Box.createHorizontalStrut(10));
        countField = new RoundField(String.valueOf(jobs.size()), 6, 48, 28);
        countField.addActionListener(e -> applyCustomCount());
        DashedPill setBtn = new DashedPill("Set");
        setBtn.addActionListener(e -> applyCustomCount());
        DashedPill pasteBtn = new DashedPill("Paste / Bulk");
        pasteBtn.addActionListener(e -> openBulkDialog());

        foot1.add(countField);
        foot1.add(Box.createHorizontalStrut(4));
        foot1.add(setBtn);
        foot1.add(Box.createHorizontalStrut(8));
        foot1.add(pasteBtn);
        b.add(foot1);

        b.add(Box.createVerticalStrut(4));
        JPanel foot2 = hRow();
        foot2.add(body("AT = arrival · BT = burst time (switches to table at 8+ jobs)", 11, Theme.muted()));
        b.add(foot2);
        return b;
    }

    private void resizeJobs(int targetCount) {
        pushUndo();
        int clamped = Math.max(1, targetCount);
        while (jobs.size() < clamped) jobs.add(new String[]{"", ""});
        while (jobs.size() > clamped) jobs.remove(jobs.size() - 1);
        hasRun = false;
        rebuildInputs();
        rerun();
    }

    private void applyCustomCount() {
        if (countField == null) return;
        try {
            int n = Integer.parseInt(countField.getText().trim());
            if (n >= 1) resizeJobs(n);
        } catch (NumberFormatException ignored) {
            countField.setText(String.valueOf(jobs.size()));
        }
    }

    private void openBulkDialog() {
        JTextArea area = new JTextArea(10, 28);
        area.setFont(Fonts.body(14f));
        StringBuilder existing = new StringBuilder();
        saveFields();
        for (String[] j : jobs) {
            if (!j[0].isEmpty() || !j[1].isEmpty()) {
                existing.append(j[0]).append(", ").append(j[1]).append("\n");
            }
        }
        area.setText(existing.toString());
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JLabel("Paste one job per line (AT, BT or AT  BT from Excel):"), BorderLayout.NORTH);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        int ok = JOptionPane.showConfirmDialog(this, panel, "Bulk Paste Jobs",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;

        List<String[]> parsed = new ArrayList<>();
        for (String line : area.getText().split("\\R")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            String[] parts = t.split("[\\t,;\\s]+");
            if (parts.length >= 3) parsed.add(new String[]{parts[1].trim(), parts[2].trim()});
            else if (parts.length == 2) parsed.add(new String[]{parts[0].trim(), parts[1].trim()});
        }
        if (!parsed.isEmpty()) {
            pushUndo();
            jobs.clear();
            jobs.addAll(parsed);
            jobFields.clear();
            jobTable = null;
            jobTableModel = null;
            hasRun = false;
            rebuildInputs();
            rerun();
            showToast(this, "Imported " + parsed.size() + " jobs — press RUN SIMULATION");
        }
    }

    /** Right-hand settings column: I/O wait % and Start time. */
    private JComponent settings() {
        JPanel s = new JPanel();
        s.setOpaque(false);
        s.setLayout(new BoxLayout(s, BoxLayout.Y_AXIS));

        ioWait = new RoundField(ioWaitText);
        ioWait.addActionListener(e -> onRunPressed());
        start = new RoundField(startText);
        start.addActionListener(e -> onRunPressed());

        s.add(labeled("I/O wait %", ioWait, "0% = pure CPU (default)"));
        s.add(Box.createVerticalStrut(10));
        s.add(labeled("Start time", start, "auto from first AT if blank"));
        return s;
    }

    private void saveFields() {
        if (ioWait != null) ioWaitText = ioWait.getText();
        if (start != null) startText = start.getText();
        if (jobTable != null && jobTableModel != null) {
            if (jobTable.isEditing()) jobTable.getCellEditor().stopCellEditing();
            for (int i = 0; i < Math.min(jobs.size(), jobTableModel.getRowCount()); i++) {
                Object a = jobTableModel.getValueAt(i, 1);
                Object b = jobTableModel.getValueAt(i, 2);
                jobs.get(i)[0] = a == null ? "" : a.toString().trim();
                jobs.get(i)[1] = b == null ? "" : b.toString().trim();
            }
            return;
        }
        if (jobFields.size() != jobs.size()) return;
        for (int i = 0; i < jobs.size(); i++) {
            for (int c = 0; c < 2; c++) jobs.get(i)[c] = jobFields.get(i)[c].getText().trim();
        }
    }

    private static JPanel hRow() {
        JPanel r = new JPanel();
        r.setOpaque(false);
        r.setLayout(new BoxLayout(r, BoxLayout.X_AXIS));
        r.setAlignmentX(LEFT_ALIGNMENT);
        return r;
    }

    private static JLabel cell(String s, int w, int h, Color c) {
        JLabel l = new JLabel(s, SwingConstants.CENTER);
        l.setFont(Fonts.label(9f));
        l.setForeground(c);
        Dimension d = new Dimension(w, h);
        l.setPreferredSize(d);
        l.setMinimumSize(d);
        l.setMaximumSize(d);
        return l;
    }

    // ============================ RESULTS ============================

    @Override protected void onRunPressed() {
        hasRun = true;
        rerun();
    }

    @Override protected void runSimulation(JPanel r) {
        saveFields();
        clearInvalidMarks();
        r.add(pixelText("SIMULATION OUTPUT · MULTIPROGRAMMING", 7f, Theme.dark ? Theme.cyan() : Theme.text(), 0f));
        r.add(Box.createVerticalStrut(12));
        r.add(new FormulaStrip("%CPU/J = (1 − p^NJ) ÷ NJ   ·   HW/J = ET × (%CPU/J)"));
        r.add(Box.createVerticalStrut(14));

        boolean allEmpty = ioWaitText.trim().isEmpty() && startText.trim().isEmpty()
                && jobs.stream().allMatch(j -> j[0].isEmpty() && j[1].isEmpty());
        if (allEmpty) {
            r.add(Notice.prompt("Type AT and BT for each job in the JOBS box above, or click a sample preset."));
            return;
        }
        if (!hasRun) {
            r.add(Notice.prompt("Inputs ready — press RUN SIMULATION (or Enter) to trace the jobs."));
            return;
        }

        double p = 0, startMin = 0, parsedStart = 0;
        boolean clockMode = false;
        String startRaw = startText.trim();
        boolean bad = false;
        List<String> problems = new ArrayList<>();
        boolean startOk = true;
        try {
            boolean anyColon = false;
            for (String[] j : jobs) if (j[0].contains(":")) { anyColon = true; break; }
            clockMode = startRaw.isEmpty() ? anyColon : startRaw.contains(":");
            parsedStart = startRaw.isEmpty() ? 0.0 : parseStartTime(startRaw);
        } catch (IllegalArgumentException e) {
            if (start != null) start.setInvalid(true); bad = true; startOk = false;
            problems.add("the start time (\"" + startRaw + "\") is not a time like 7.4 or 10:00");
        }
        try {
            p = parseIoWait(ioWaitText);
        } catch (IllegalArgumentException e) {
            if (ioWait != null) ioWait.setInvalid(true); bad = true;
            problems.add("the I/O wait (\"" + ioWaitText.trim() + "\") must be a number from 0% to 99.9%");
        }

        List<Job> jobList = null;
        {   // check the jobs even when another field is wrong, so every problem shows at once
            boolean startGiven = !startRaw.isEmpty() && startOk;
            try {
                jobList = parseJobRows(jobs, parsedStart, clockMode, startGiven);
                if (startRaw.isEmpty()) {
                    double earliest = jobList.get(0).arrivalMin;
                    for (Job j : jobList) earliest = Math.min(earliest, j.arrivalMin);
                    startMin = earliest;
                } else {
                    startMin = parsedStart;
                }
            } catch (IllegalArgumentException e) {
                markInvalidJobFields(parsedStart, clockMode, startGiven);
                bad = true;
                problems.add(jobProblem(e.getMessage()));
            }
        }
        if (bad) {
            r.add(new Notice(false, "Input check", problems.isEmpty() ? "check the highlighted fields" : problems.get(0)));
            for (int i = 1; i < problems.size(); i++) { r.add(Box.createVerticalStrut(6)); r.add(new Notice(false, "Also", problems.get(i))); }
            r.add(Box.createVerticalStrut(6));
            r.add(body("Rule: every job needs AT (not before the start time) and BT more than 0; I/O wait is 0% to 99.9%.",
                    12, Theme.muted()));
            return;
        }

        Result res = simulate(jobList, p, startMin);
        recordHistory(String.format("%d jobs · %.0f%% I/O · done %s",
                jobList.size(), p * 100, formatTime(res.endTime, clockMode)));
        double totalCpu = 0;
        for (Job j : jobList) totalCpu += j.cpuMin;

        // 1) Summary cards
        Row cards = new Row(new GridLayout(1, 3, 12, 0));
        cards.add(new SummaryCard("START TIME", formatTime(startMin, clockMode),
                jobList.size() + " jobs · " + String.format("%.0f%% I/O wait", p * 100), false));
        cards.add(new SummaryCard("LAST JOB DONE", formatTime(res.endTime, clockMode),
                "all jobs finished", true));
        cards.add(new SummaryCard("TOTAL ELAPSED", String.format("%.2f", res.endTime - startMin),
                String.format("%.2f BT total", totalCpu), false));
        r.add(cards);
        r.add(Box.createVerticalStrut(14));

        // 2) Main 8-column event table + CPU utilization timeline (collapsible + hover-synced)
        MultiTable table = new MultiTable(res.rows, clockMode);
        MultiTimeline timeline = new MultiTimeline(res.slices, clockMode);
        table.onHoverRow(rowIdx -> timeline.setHighlightedSlice(rowIdx));
        timeline.onHoverSlice(sliceIdx -> table.setHighlightedRow(sliceIdx));

        r.add(collapsibleSection("EXECUTION TRACE TABLE (" + res.rows.size() + " EVENTS)", cappedScroll(table, 420)));
        r.add(Box.createVerticalStrut(14));

        JPanel tlBox = new JPanel();
        tlBox.setOpaque(false);
        tlBox.setLayout(new BoxLayout(tlBox, BoxLayout.Y_AXIS));
        JPanel playBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        playBar.setOpaque(false);
        playBar.setAlignmentX(LEFT_ALIGNMENT);
        DashedPill playBtn = new DashedPill("Play >");
        playBtn.addActionListener(e -> timeline.play());
        playBar.add(playBtn);
        tlBox.add(playBar);
        tlBox.add(Box.createVerticalStrut(6));
        tlBox.add(timeline);

        r.add(collapsibleSection("CPU UTILIZATION TIMELINE", tlBox));
        r.add(Box.createVerticalStrut(14));

        // 4) Per-job completion summary table
        String[][] summary = new String[jobList.size()][];
        for (int i = 0; i < jobList.size(); i++) {
            Job j = jobList.get(i);
            double fin = res.finishTime.get(j.order);
            summary[i] = new String[]{
                    j.id,
                    formatTime(j.arrivalMin, clockMode),
                    String.format("%.2f", j.cpuMin),
                    formatTime(fin, clockMode),
                    String.format("%.2f", fin - j.arrivalMin)
            };
        }
        r.add(collapsibleSection("JOB COMPLETION SUMMARY", cappedScroll(new JobSummaryTable(summary), 320)));
        r.add(explainSection(explain(res, p, startMin, clockMode)));

        r.add(Box.createVerticalStrut(10));
        StringBuilder sbCopy = new StringBuilder();
        sbCopy.append(String.format("Multiprogramming (%d jobs, %.0f%% I/O wait, start %s)%n",
                jobList.size(), p * 100, formatTime(startMin, clockMode)));
        sbCopy.append("Last job done: ").append(formatTime(res.endTime, clockMode)).append("\n");
        for (String[] row : summary) sbCopy.append(String.join(" | ", row)).append("\n");
        final String copyStr = sbCopy.toString();
        DashedPill copyBtn = new DashedPill("Copy summary");
        copyBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(copyStr), null);
            showToast(this, "Copied summary to clipboard!");
        });
        r.add(copyBtn);
    }

    private void clearInvalidMarks() {
        if (ioWait != null) ioWait.setInvalid(false);
        if (start != null) start.setInvalid(false);
        for (RoundField[] pair : jobFields) { pair[0].setInvalid(false); pair[1].setInvalid(false); }
    }

    private void markInvalidJobFields(double startMin, boolean clockMode, boolean hasExplicitStart) {
        for (int i = 0; i < Math.min(jobs.size(), jobFields.size()); i++) {
            String at = jobs.get(i)[0].trim(), bt = jobs.get(i)[1].trim();
            if (at.isEmpty() && bt.isEmpty()) continue;
            try {
                if (at.isEmpty()) throw new IllegalArgumentException();
                double arr = parseArrival(at, startMin, clockMode, hasExplicitStart);
                if ((hasExplicitStart && arr + EPS < startMin) || arr < 0) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) { jobFields.get(i)[0].setInvalid(true); }
            try {
                if (bt.isEmpty() || Double.parseDouble(bt) <= 0) throw new IllegalArgumentException();
            } catch (IllegalArgumentException e) { jobFields.get(i)[1].setInvalid(true); }
        }
    }

    private void recordHistory(String label) {
        List<String[]> copy = new ArrayList<>();
        for (String[] j : jobs) copy.add(new String[]{j[0], j[1]});
        HistoryItem item = new HistoryItem(label, copy, ioWaitText.trim(), startText.trim());
        history.removeIf(h -> h.label.equals(item.label) && h.rows.size() == item.rows.size());
        history.add(0, item);
        if (history.size() > 6) history.remove(history.size() - 1);
    }

    private void showHistoryMenu(JComponent invoker) {
        if (history.isEmpty()) {
            showToast(this, "No recent runs yet");
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        menu.setBorder(BorderFactory.createLineBorder(Theme.inputLine(), 1));
        for (HistoryItem item : history) {
            JMenuItem mi = new JMenuItem(item.label);
            mi.setFont(Fonts.body(13f));
            mi.setBackground(Theme.input());
            mi.setForeground(Theme.text());
            mi.setBorder(new EmptyBorder(6, 12, 6, 14));
            mi.addActionListener(e -> {
                String[][] arr = item.rows.toArray(new String[0][]);
                hasRun = false;
                loadPreset(arr, item.ioWait, item.start);
                showToast(this, "Restored from history — press RUN SIMULATION");
            });
            menu.add(mi);
        }
        if (invoker.isShowing()) menu.show(invoker, 0, invoker.getHeight());
    }

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

    // ============================ PARSING & FORMATTING ============================

    static double parseStartTime(String s) {
        String trimmed = s.trim();
        if (trimmed.contains(":")) return parseClock(trimmed);
        double m = Double.parseDouble(trimmed);
        if (m < 0) throw new IllegalArgumentException("negative start");
        return m;
    }

    static double parseIoWait(String s) {
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return 0.0;
        boolean hasPct = trimmed.endsWith("%");
        if (hasPct) trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        double val = Double.parseDouble(trimmed);
        double p = (hasPct || val > 1.0) ? val / 100.0 : val;
        if (p < 0 || p >= 1.0) throw new IllegalArgumentException("ioWait out of range");
        return p;
    }

    /** Turns parseJobRows' short error into a sentence for the Input check notice. */
    private static String jobProblem(String msg) {
        if (msg == null) return "a job has a value that is not a number";
        if (msg.startsWith("incomplete job ")) return "job " + msg.substring(15) + " is missing its AT or BT";
        if (msg.startsWith("invalid job ")) return "job " + msg.substring(12) + ": AT must be 0 or more (and not before the start time) and BT more than 0";
        if (msg.equals("empty jobs")) return "there are no jobs - type AT and BT for at least one job";
        return "a job has a value that is not a number (" + msg.replace("For input string: ", "") + ")";
    }

    static List<Job> parseJobRows(List<String[]> rows, double startMin, boolean clockMode, boolean hasExplicitStart) {
        List<Job> list = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            String atStr = rows.get(i)[0].trim();
            String btStr = rows.get(i)[1].trim();
            if (atStr.isEmpty() && btStr.isEmpty()) continue;
            if (atStr.isEmpty() || btStr.isEmpty()) throw new IllegalArgumentException("incomplete job J" + (i + 1));
            double arrival = parseArrival(atStr, startMin, clockMode, hasExplicitStart);
            double cpu = Double.parseDouble(btStr);
            if ((hasExplicitStart && arrival + EPS < startMin) || arrival < 0 || cpu <= 0) {
                throw new IllegalArgumentException("invalid job J" + (i + 1));
            }
            list.add(new Job("J" + (i + 1), arrival, cpu, list.size()));
        }
        if (list.isEmpty()) throw new IllegalArgumentException("empty jobs");
        return list;
    }

    private static double parseArrival(String token, double startMin, boolean clockMode, boolean hasExplicitStart) {
        if (token.contains(":")) return parseClock(token);
        double val = Double.parseDouble(token);
        if (val < 0) throw new IllegalArgumentException("negative arrival");
        return (clockMode && hasExplicitStart) ? startMin + val : val;
    }

    private static double parseClock(String hhmm) {
        String[] hm = hhmm.split(":");
        if (hm.length != 2) throw new IllegalArgumentException(hhmm);
        int h = Integer.parseInt(hm[0].trim());
        double m = Double.parseDouble(hm[1].trim());
        if (h < 0 || h > 23 || m < 0 || m >= 60) throw new IllegalArgumentException(hhmm);
        return h * 60.0 + m;
    }

    static String formatTime(double value, boolean clockMode) {
        if (!clockMode) {
            double rounded = Math.round(value * 100.0) / 100.0;
            if (Math.abs(rounded - Math.rint(rounded)) < 1e-6) return String.format("%.0f", rounded);
            if (Math.abs(rounded * 10 - Math.rint(rounded * 10)) < 1e-6) return String.format("%.1f", rounded);
            return String.format("%.2f", rounded);
        }
        long tenths = Math.round(value * 10.0);
        long wholeMins = tenths / 10;
        long tenth = tenths % 10;
        long hh = (wholeMins / 60) % 24;
        long mm = wholeMins % 60;
        return String.format("%d:%02d.%d", hh, mm, tenth);
    }

    private static String fmtRate(double v) {
        if (Math.abs(v) < 1e-7) return "0";
        if (Math.abs(v - 1.0) < 1e-7) return "1";
        String s = String.format("%.4f", v);
        while (s.contains(".") && (s.endsWith("0") || s.endsWith("."))) {
            if (s.endsWith(".")) { s = s.substring(0, s.length() - 1); break; }
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    // ============================ SIMULATION ============================

    static final class Job {
        final String id;
        final double arrivalMin, cpuMin;
        final int order;

        Job(String id, double arrivalMin, double cpuMin, int order) {
            this.id = id;
            this.arrivalMin = arrivalMin;
            this.cpuMin = cpuMin;
            this.order = order;
        }
    }

    static final class JobState {
        final String id;
        final double timeLeft;
        final boolean finishedNow;

        JobState(String id, double timeLeft, boolean finishedNow) {
            this.id = id;
            this.timeLeft = timeLeft;
            this.finishedNow = finishedNow;
        }
    }

    static final class EventRow {
        final double time, rate, et, hw;
        final String event;
        final int nj;
        final List<JobState> states;
        final boolean allDone;

        EventRow(double time, String event, int nj, double rate, double et, double hw,
                 List<JobState> states, boolean allDone) {
            this.time = time;
            this.event = event;
            this.nj = nj;
            this.rate = rate;
            this.et = et;
            this.hw = hw;
            this.states = states;
            this.allDone = allDone;
        }
    }

    static final class TimelineSlice {
        final double from, to, cpuUtil;
        final int nj;

        TimelineSlice(double from, double to, int nj, double cpuUtil) {
            this.from = from;
            this.to = to;
            this.nj = nj;
            this.cpuUtil = cpuUtil;
        }
    }

    static final class Result {
        final List<EventRow> rows = new ArrayList<>();
        final List<TimelineSlice> slices = new ArrayList<>();
        final Map<Integer, Double> finishTime = new HashMap<>();
        double endTime;
    }

    /** One line per event: how many jobs share the CPU, the share each gets, and the work done. */
    static List<String> explain(Result res, double p, double startMin, boolean clockMode) {
        List<String> out = new ArrayList<>();
        out.add(String.format("With I/O wait p = %.0f%%, n jobs keep the CPU busy 1 − p^n of the time, so each job gets "
                + "%%CPU/J = (1 − p^n) ÷ n%s.", p * 100, p == 0 ? " (= 1 ÷ n when p = 0)" : ""));
        out.add("Between two events: ET = elapsed time, HW/J = ET × %CPU/J = work each job gets done; a job ends when its BT is used up.");
        double prev = startMin;
        for (EventRow row : res.rows) {
            if (row.nj == 0) { out.add(formatTime(row.time, clockMode) + ": " + row.event + " (arrives)."); prev = row.time; continue; }
            out.add(String.format("%s → %s: %d job%s share the CPU → %%CPU/J = %s, ET = %s, HW/J = %s × %s = %s; %s.",
                    formatTime(prev, clockMode), formatTime(row.time, clockMode), row.nj, row.nj == 1 ? "" : "s",
                    fmtRate(row.rate), fmtRate(row.et), fmtRate(row.et), fmtRate(row.rate), fmtRate(row.hw), row.event));
            prev = row.time;
        }
        out.add("Last job done at " + formatTime(res.endTime, clockMode) + ".");
        return out;
    }

    static Result simulate(List<Job> inputJobs, double p, double startMin) {
        List<Job> sorted = new ArrayList<>(inputJobs);
        sorted.sort(Comparator.<Job>comparingDouble(j -> j.arrivalMin).thenComparingInt(j -> j.order));

        int n = sorted.size();
        double[] rem = new double[n];
        boolean[] started = new boolean[n];
        boolean[] done = new boolean[n];
        for (int i = 0; i < n; i++) rem[i] = sorted.get(i).cpuMin;

        Result res = new Result();
        double t = startMin;

        List<String> initialArrived = new ArrayList<>();
        List<JobState> initialStates = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (sorted.get(i).arrivalMin <= t + EPS) {
                started[i] = true;
                initialArrived.add(sorted.get(i).id);
                initialStates.add(new JobState(sorted.get(i).id, rem[i], false));
            }
        }
        if (!initialArrived.isEmpty()) {
            res.rows.add(new EventRow(t, String.join(", ", initialArrived) + " a/s",
                    0, 0, 0, 0, initialStates, false));
        }

        int maxSteps = Math.max(1000, n * 4 + 100);
        for (int step = 0; step < maxSteps; step++) {
            List<Integer> active = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (started[i] && !done[i]) active.add(i);
            }

            if (active.isEmpty()) {
                double nextArr = Double.POSITIVE_INFINITY;
                for (int i = 0; i < n; i++) {
                    if (!started[i] && sorted.get(i).arrivalMin < nextArr) nextArr = sorted.get(i).arrivalMin;
                }
                if (Double.isInfinite(nextArr)) {
                    res.endTime = t;
                    break;
                }
                if (nextArr - t > EPS) {
                    res.slices.add(new TimelineSlice(t, nextArr, 0, 0));
                }
                t = nextArr;
                List<String> arrivedNow = new ArrayList<>();
                List<JobState> states = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    if (!started[i] && sorted.get(i).arrivalMin <= t + EPS) {
                        started[i] = true;
                        arrivedNow.add(sorted.get(i).id);
                        states.add(new JobState(sorted.get(i).id, rem[i], false));
                    }
                }
                res.rows.add(new EventRow(t, String.join(", ", arrivedNow) + " a/s",
                        0, 0, 0, 0, states, false));
                continue;
            }

            int nj = active.size();
            double cpuUtil = 1.0 - Math.pow(p, nj);
            double rate = cpuUtil / nj;

            double nextArr = Double.POSITIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                if (!started[i] && sorted.get(i).arrivalMin > t + EPS && sorted.get(i).arrivalMin < nextArr) {
                    nextArr = sorted.get(i).arrivalMin;
                }
            }
            double minFinishDt = Double.POSITIVE_INFINITY;
            for (int i : active) {
                double dt = rem[i] / rate;
                if (dt < minFinishDt) minFinishDt = dt;
            }

            double et = Math.min(nextArr - t, minFinishDt);
            double hw = et * rate;
            double nextT = t + et;

            List<String> finishedNow = new ArrayList<>();
            List<JobState> states = new ArrayList<>();
            for (int i : active) {
                rem[i] -= hw;
                boolean finished = rem[i] <= EPS;
                if (finished) {
                    rem[i] = 0;
                    done[i] = true;
                    res.finishTime.put(sorted.get(i).order, nextT);
                    finishedNow.add(sorted.get(i).id);
                }
                states.add(new JobState(sorted.get(i).id, Math.max(0, rem[i]), finished));
            }

            List<String> arrivedNow = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if (!started[i] && sorted.get(i).arrivalMin <= nextT + EPS) {
                    started[i] = true;
                    arrivedNow.add(sorted.get(i).id);
                    states.add(new JobState(sorted.get(i).id, rem[i], false));
                }
            }

            boolean everythingDone = true;
            for (int i = 0; i < n; i++) {
                if (!done[i]) { everythingDone = false; break; }
            }

            String eventLabel = buildEventLabel(finishedNow, arrivedNow);
            res.rows.add(new EventRow(nextT, eventLabel, nj, rate, et, hw, states, everythingDone));
            res.slices.add(new TimelineSlice(t, nextT, nj, cpuUtil));
            t = nextT;

            if (everythingDone) {
                res.endTime = t;
                break;
            }
        }
        return res;
    }

    private static String buildEventLabel(List<String> finished, List<String> arrived) {
        List<String> parts = new ArrayList<>();
        if (!finished.isEmpty()) parts.add(String.join(", ", finished) + " t");
        if (!arrived.isEmpty()) parts.add(String.join(", ", arrived) + " a/s");
        return parts.isEmpty() ? "step" : String.join(" · ", parts);
    }

    // ============================ COMPONENTS ============================

    static class FormulaStrip extends Row {
        private static final long serialVersionUID = 1L;
        FormulaStrip(String formula) {
            super(new BorderLayout());
            setBorder(new EmptyBorder(8, 18, 8, 8));
            add(body(formula, 15, Theme.text()), BorderLayout.CENTER);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(Theme.cyan());
            g2.fill(new RoundRectangle2D.Double(0, 0, 4, getHeight(), 4, 4));
            g2.dispose();
        }
    }

    static class SummaryCard extends JPanel {
        private static final long serialVersionUID = 1L;
        private final boolean accent;

        SummaryCard(String caption, String value, String note, boolean accent) {
            this.accent = accent;
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(12, 16, 12, 16));
            JLabel c = new SafeLabel(caption);
            c.setFont(Fonts.label(8f));
            c.setForeground(Theme.muted());
            add(c);
            add(Box.createVerticalStrut(6));
            add(body(value, 23, accent ? Theme.green() : Theme.text()));
            add(Box.createVerticalStrut(4));
            add(body(note, 11, Theme.muted()));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            RoundRectangle2D box = new RoundRectangle2D.Double(0.75, 0.75, getWidth() - 1.5, getHeight() - 1.5, 18, 18);
            g2.setColor(Theme.panel());
            g2.fill(box);
            g2.setColor(accent ? Theme.green() : Theme.inputLine());
            g2.setStroke(new BasicStroke(accent ? 2f : 1.5f));
            g2.draw(box);
            g2.dispose();
        }
    }

    /** 8-column multiprogramming trace table: TIME, EVENT, NJ, %CPU/J, ET, HW/J, J#, TIMELEFT. */
    static class MultiTable extends JComponent {
        private static final long serialVersionUID = 1L;
        static final String[] HEAD = {"TIME", "EVENT", "NJ", "%CPU/J", "ET", "HW/J", "J#", "TIMELEFT"};
        static final double[] WEIGHT = {0.10, 0.19, 0.06, 0.14, 0.10, 0.13, 0.10, 0.18};
        static final int HEAD_H = 28, SUB_H = 22, MIN_ROW_H = 28;
        private final transient List<EventRow> rows;
        private final boolean clockMode;
        private int highlightedRow = -1;
        private java.util.function.IntConsumer hoverCb;

        MultiTable(List<EventRow> rows, boolean clockMode) {
            this.rows = rows;
            this.clockMode = clockMode;
            setAlignmentX(LEFT_ALIGNMENT);
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int y = 2 + HEAD_H;
                    int found = -1;
                    for (int i = 0; i < rows.size(); i++) {
                        int rh = rowHeight(rows.get(i));
                        if (e.getY() >= y && e.getY() < y + rh) { found = i; break; }
                        y += rh;
                    }
                    if (found != highlightedRow) {
                        highlightedRow = found;
                        if (hoverCb != null) hoverCb.accept(found > 0 ? found - 1 : -1);
                        repaint();
                    }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent e) {
                    if (highlightedRow != -1) {
                        highlightedRow = -1;
                        if (hoverCb != null) hoverCb.accept(-1);
                        repaint();
                    }
                }
            });
        }

        void onHoverRow(java.util.function.IntConsumer cb) { this.hoverCb = cb; }
        void setHighlightedRow(int sliceIdx) {
            int target = sliceIdx >= 0 ? sliceIdx + 1 : -1;
            if (target != highlightedRow) { highlightedRow = target; repaint(); }
        }

        private int rowHeight(EventRow r) {
            return Math.max(MIN_ROW_H, r.states.size() * SUB_H + 6);
        }

        @Override public Dimension getPreferredSize() {
            int totalH = HEAD_H + 3;
            for (EventRow r : rows) totalH += rowHeight(r);
            return new Dimension(360, totalH);
        }

        @Override public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getPreferredSize().height;
            double[] x = new double[HEAD.length + 1];
            x[0] = 1.5;
            for (int c = 0; c < HEAD.length; c++) x[c + 1] = x[c] + (w - 3) * WEIGHT[c];

            RoundRectangle2D box = new RoundRectangle2D.Double(0.75, 0.75, w - 1.5, h - 1.5, 20, 20);
            Shape oldClip = g2.getClip();
            g2.clip(box);

            g2.setColor(Theme.panel2());
            g2.fillRect(0, 0, w, HEAD_H + 2);

            int[] rowY = new int[rows.size()];
            int[] rowH = new int[rows.size()];
            int curY = 2 + HEAD_H;
            for (int i = 0; i < rows.size(); i++) {
                EventRow r = rows.get(i);
                rowY[i] = curY;
                rowH[i] = rowHeight(r);
                if (i == highlightedRow) {
                    Color yellow = Theme.yellow();
                    g2.setColor(new Color(yellow.getRed(), yellow.getGreen(), yellow.getBlue(), Theme.dark ? 55 : 95));
                } else if (r.allDone) {
                    Color green = Theme.green();
                    g2.setColor(new Color(green.getRed(), green.getGreen(), green.getBlue(), Theme.dark ? 60 : 75));
                } else {
                    g2.setColor(i % 2 == 0 ? Theme.panel() : Theme.panel2());
                }
                g2.fillRect(0, rowY[i], w, rowH[i]);
                curY += rowH[i];
            }

            g2.setColor(Theme.line());
            g2.setStroke(new BasicStroke(1f));
            for (int i = 0; i < rows.size(); i++) {
                g2.drawLine(0, rowY[i], w, rowY[i]);
            }
            for (int c = 1; c < HEAD.length; c++) {
                g2.drawLine((int) Math.round(x[c]), 0, (int) Math.round(x[c]), h);
            }

            g2.setClip(oldClip);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(box);

            g2.setFont(Fonts.label(9f));
            g2.setColor(Theme.muted());
            for (int c = 0; c < HEAD.length; c++) {
                centerText(g2, HEAD[c], (x[c] + x[c + 1]) / 2, middle(g2, 2 + HEAD_H / 2.0));
            }

            g2.setFont(Fonts.body(13f));
            for (int i = 0; i < rows.size(); i++) {
                EventRow r = rows.get(i);
                double midY = rowY[i] + rowH[i] / 2.0;
                double base = middle(g2, midY);

                String[] cols = {
                        formatTime(r.time, clockMode),
                        r.event,
                        String.valueOf(r.nj),
                        fmtRate(r.rate),
                        fmtRate(r.et),
                        fmtRate(r.hw)
                };

                for (int c = 0; c < cols.length; c++) {
                    g2.setColor(r.allDone && c <= 1 && Theme.dark ? Theme.green() : Theme.text());
                    centerText(g2, cols[c], (x[c] + x[c + 1]) / 2, base);
                }

                double topY = rowY[i] + (rowH[i] - r.states.size() * SUB_H) / 2.0;
                for (int sIdx = 0; sIdx < r.states.size(); sIdx++) {
                    JobState st = r.states.get(sIdx);
                    double subMid = topY + sIdx * SUB_H + SUB_H / 2.0;
                    double subBase = middle(g2, subMid);
                    g2.setColor(st.finishedNow ? Theme.green() : Theme.text());
                    centerText(g2, st.id, (x[6] + x[7]) / 2, subBase);
                    centerText(g2, String.format("%.2f", st.timeLeft), (x[7] + x[8]) / 2, subBase);
                }
            }
            g2.dispose();
        }
    }

    /** Proportional timeline showing active degree of multiprogramming NJ & CPU utilization. */
    static class MultiTimeline extends JComponent {
        private static final long serialVersionUID = 1L;
        static final int BAR = 34;
        private final transient List<TimelineSlice> slices;
        private final boolean clockMode;
        private int highlightedSlice = -1;
        private double animProgress = 1.0;
        private javax.swing.Timer animTimer;
        private java.util.function.IntConsumer hoverCb;

        MultiTimeline(List<TimelineSlice> slices, boolean clockMode) {
            this.slices = slices;
            this.clockMode = clockMode;
            setAlignmentX(LEFT_ALIGNMENT);
            ToolTipManager.sharedInstance().registerComponent(this);
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int found = sliceAt(e.getX());
                    if (found != highlightedSlice) {
                        highlightedSlice = found;
                        if (hoverCb != null) hoverCb.accept(found);
                        repaint();
                    }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent e) {
                    if (highlightedSlice != -1) {
                        highlightedSlice = -1;
                        if (hoverCb != null) hoverCb.accept(-1);
                        repaint();
                    }
                }
            });
        }

        void play() {
            if (slices.isEmpty()) return;
            if (animTimer != null && animTimer.isRunning()) animTimer.stop();
            animProgress = 0.0;
            repaint();
            animTimer = new javax.swing.Timer(25, e -> {
                animProgress += 0.04;
                if (animProgress >= 1.0) {
                    animProgress = 1.0;
                    ((javax.swing.Timer) e.getSource()).stop();
                }
                repaint();
            });
            animTimer.start();
        }

        void onHoverSlice(java.util.function.IntConsumer cb) { this.hoverCb = cb; }
        void setHighlightedSlice(int sliceIdx) {
            if (sliceIdx != highlightedSlice) { highlightedSlice = sliceIdx; repaint(); }
        }

        private int sliceAt(int mx) {
            if (slices.isEmpty()) return -1;
            double start = slices.get(0).from, end = slices.get(slices.size() - 1).to;
            double scale = (getWidth() - 3) / Math.max(1e-6, end - start);
            for (int i = 0; i < slices.size(); i++) {
                TimelineSlice s = slices.get(i);
                double x1 = 1.5 + (s.from - start) * scale, x2 = 1.5 + (s.to - start) * scale;
                if (mx >= x1 && mx <= x2) return i;
            }
            return -1;
        }

        @Override public String getToolTipText(MouseEvent e) {
            int idx = sliceAt(e.getX());
            if (idx < 0) return null;
            TimelineSlice s = slices.get(idx);
            return String.format("%s → %s | NJ=%d | CPU Util=%.1f%%",
                    formatTime(s.from, clockMode), formatTime(s.to, clockMode), s.nj, s.cpuUtil * 100);
        }

        @Override public Dimension getPreferredSize() { return new Dimension(300, BAR + 3 + 2 + 16); }
        @Override public Dimension getMaximumSize()   { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

        @Override protected void paintComponent(Graphics g) {
            if (slices.isEmpty()) return;
            Graphics2D g2 = smooth(g);
            int w = getWidth();
            double start = slices.get(0).from;
            double end = slices.get(slices.size() - 1).to;
            double scale = (w - 3) / Math.max(1e-6, end - start);
            RoundRectangle2D bar = new RoundRectangle2D.Double(0.75, 0.75, w - 1.5, BAR + 1.5, 16, 16);

            Shape oldClip = g2.getClip();
            g2.clip(bar);
            double maxDrawX = 1.5 + (w - 3) * animProgress;
            g2.setFont(Fonts.body(12f));
            for (int i = 0; i < slices.size(); i++) {
                TimelineSlice s = slices.get(i);
                double x1 = 1.5 + (s.from - start) * scale, x2 = Math.min(maxDrawX, 1.5 + (s.to - start) * scale);
                if (x2 <= x1) continue;
                g2.setColor(s.nj == 0 ? Theme.panel2() : blockColor(s.nj - 1, i == highlightedSlice ? 1.0 : 0.85));
                g2.fill(new Rectangle.Double(x1, 0, x2 - x1, BAR + 3));
                if (i == highlightedSlice) {
                    g2.setColor(Theme.yellow());
                    g2.setStroke(new BasicStroke(2f));
                    g2.draw(new Rectangle.Double(x1, 1, x2 - x1, BAR + 1));
                }
                g2.setColor(Theme.panel());
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new Line2D.Double(x2, 0, x2, BAR + 3));

                String fullLbl = s.nj == 0 ? "idle" : String.format("NJ=%d (%.0f%%)", s.nj, s.cpuUtil * 100);
                String shortLbl = s.nj == 0 ? "idle" : "NJ=" + s.nj;
                String lbl = g2.getFontMetrics().stringWidth(fullLbl) < x2 - x1 - 6 ? fullLbl
                        : (g2.getFontMetrics().stringWidth(shortLbl) < x2 - x1 - 4 ? shortLbl : null);
                if (lbl != null) {
                    g2.setColor(s.nj == 0 ? Theme.muted() : glyph());
                    centerText(g2, lbl, (x1 + x2) / 2, middle(g2, 1.5 + BAR / 2.0));
                }
            }
            g2.setClip(oldClip);
            g2.setColor(Theme.line());
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(bar);

            g2.setFont(Fonts.body(10f));
            g2.setColor(Theme.muted());
            FontMetrics fm = g2.getFontMetrics();
            List<Double> marks = new ArrayList<>();
            for (TimelineSlice s : slices) marks.add(s.from);
            marks.add(end);
            double lastRight = -100;
            for (double t : marks) {
                String lbl = formatTime(t, clockMode);
                int tw = fm.stringWidth(lbl);
                double x = 1.5 + (t - start) * scale;
                double lx = Math.max(0, Math.min(w - tw, x - tw / 2.0));
                if (lx < lastRight + 6) continue;
                g2.drawString(lbl, (float) lx, BAR + 3 + 2 + fm.getAscent());
                lastRight = lx + tw;
            }
            g2.dispose();
        }
    }

    static class JobSummaryTable extends JComponent {
        private static final long serialVersionUID = 1L;
        static final String[] HEAD = {"JOB", "AT", "BT", "FINISH TIME", "ELAPSED"};
        static final double[] WEIGHT = {0.16, 0.21, 0.21, 0.21, 0.21};
        static final int HEAD_H = 27, ROW = 29;
        private final String[][] rows;

        JobSummaryTable(String[][] rows) {
            this.rows = rows;
            setAlignmentX(LEFT_ALIGNMENT);
        }

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
            g2.setColor(Theme.panel2());
            g2.fillRect(0, 0, w, HEAD_H + 2);
            for (int i = 0; i < rows.length; i++) {
                g2.setColor(i % 2 == 0 ? Theme.panel() : Theme.panel2());
                g2.fillRect(0, 2 + HEAD_H + i * ROW, w, ROW + 2);
            }
            g2.setColor(Theme.line());
            g2.setStroke(new BasicStroke(1f));
            for (int c = 1; c < HEAD.length; c++) {
                g2.drawLine((int) Math.round(x[c]), 0, (int) Math.round(x[c]), h);
            }
            g2.setClip(oldClip);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(box);

            g2.setFont(Fonts.label(9f));
            g2.setColor(Theme.muted());
            for (int c = 0; c < HEAD.length; c++) {
                centerText(g2, HEAD[c], (x[c] + x[c + 1]) / 2, middle(g2, 2 + HEAD_H / 2.0));
            }
            g2.setFont(Fonts.body(13f));
            g2.setColor(Theme.text());
            for (int i = 0; i < rows.length; i++) {
                double midY = middle(g2, 2 + HEAD_H + i * ROW + ROW / 2.0);
                for (int c = 0; c < HEAD.length; c++) {
                    centerText(g2, rows[i][c], (x[c] + x[c + 1]) / 2, midY);
                }
            }
            g2.dispose();
        }
    }
}
