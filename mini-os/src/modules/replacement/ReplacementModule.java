package modules.replacement;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import modules.common.FigmaModule;
import shared.Fonts;
import shared.Theme;

/**
 * PAGE REPLACEMENT  —  OWNER: Member 2
 *   [x] Reference string, frame count, algorithm dropdown
 *   [x] Frame grid (one column per page) + H/F row
 *   [x] #FAULT and #HIT + comparison across FIFO, LRU, Optimal
 * CHECK: FIFO, 3 frames, 7 0 1 2 0 3 0 4 2 3 0 3 2 -> 10 faults, 3 hits
 *
 * The page design (header, inputs, run button, result card) comes from FigmaModule.
 */
public class ReplacementModule extends FigmaModule {
    private static final long serialVersionUID = 1L;
    static final String[] ALGOS = {"FIFO", "LRU", "Optimal"};
    private static final String SAMPLE_REF = "7 0 1 2 0 3 0 4 2 3 0 3 2";
    private static final String SAMPLE_FRAMES = "3";
    private static final String LARGE_REF = "7 0 1 2 0 3 0 4 2 3 0 3 2 1 2 0 1 7 0 1 4 5 2 3 0";
    private static final String LARGE_FRAMES = "4";

    private RoundField reference, frames;
    private String algo = "FIFO";
    private boolean hasRun = false;
    private final transient List<HistoryItem> history = new ArrayList<>();
    private final transient Deque<String[]> undoStack = new ArrayDeque<>();
    private boolean restoring = false;

    private record HistoryItem(String label, String ref, String frames, String algo) {}

    @Override public String title()    { return "Page Replacement"; }
    @Override public String subtitle() { return "Visualize FIFO, LRU, and Optimal replacement."; }
    @Override protected String crumb() { return "REPLACEMENT"; }
    @Override protected String lead()  { return "Trace the page faults and hits for a reference string."; }

    // ============================ INPUTS ============================

    @Override protected void buildInputs(JPanel p) {
        reference = new RoundField(reference == null ? "" : reference.getText());
        frames    = new RoundField(frames    == null ? "" : frames.getText());
        reference.addActionListener(e -> onRunPressed());
        frames.addActionListener(e -> onRunPressed());
        Dropdown pick = new Dropdown(algo, ALGOS, a -> { algo = a; hasRun = false; rebuildInputs(); rerun(); });

        p.add(columns(new double[]{2.4, 1, 1.2},
                labeled("Reference string", reference, "pages separated by spaces"),
                labeled("Frames", frames, "any count >= 1"),
                labeled("Algorithm", pick, null)), BorderLayout.CENTER);

        JPanel tools = new JPanel();
        tools.setOpaque(false);
        tools.setLayout(new BoxLayout(tools, BoxLayout.X_AXIS));
        tools.setBorder(new EmptyBorder(10, 0, 0, 0));

        DashedPill sampleBtn = new DashedPill("Sample data");
        sampleBtn.addActionListener(e -> {
            pushUndo();
            setFields(SAMPLE_REF, SAMPLE_FRAMES);
            hasRun = false;
            rerun();
            showToast(this, "Loaded sample — press RUN SIMULATION");
        });

        DashedPill largeBtn = new DashedPill("Large sample (25)");
        largeBtn.addActionListener(e -> {
            pushUndo();
            setFields(LARGE_REF, LARGE_FRAMES);
            hasRun = false;
            rerun();
            showToast(this, "Loaded 25 pages — press RUN SIMULATION");
        });

        DashedPill randomBtn = new DashedPill("Random");
        randomBtn.addActionListener(e -> {
            pushUndo();
            Random rng = new Random();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                if (i > 0) sb.append(" ");
                sb.append(rng.nextInt(8));
            }
            setFields(sb.toString(), frames.getText().trim().isEmpty() ? "3" : frames.getText());
            hasRun = false;
            rerun();
            showToast(this, "Generated 16 pages — press RUN SIMULATION");
        });

        DashedPill histBtn = new DashedPill("History");
        histBtn.addActionListener(e -> showHistoryMenu(histBtn));

        DashedPill undoBtn = new DashedPill("Undo");
        undoBtn.addActionListener(e -> {
            if (undoStack.isEmpty()) {
                showToast(this, "Nothing to undo");
                return;
            }
            String[] prev = undoStack.pop();
            setFields(prev[0], prev[1]);
            hasRun = false;
            rerun();
            showToast(this, "Undid last change");
        });

        DashedPill clearBtn = new DashedPill("Clear");
        clearBtn.addActionListener(e -> {
            pushUndo();
            setFields("", "");
            hasRun = false;
            rerun();
            showToast(this, "Cleared inputs");
        });

        JLabel countBadge = body(countPagesLabel(reference.getText()), 11, Theme.pink());
        reference.getDocument().addDocumentListener(new DocumentListener() {
            private void upd() { countBadge.setText(countPagesLabel(reference.getText())); }
            @Override public void insertUpdate(DocumentEvent e) { upd(); }
            @Override public void removeUpdate(DocumentEvent e) { upd(); }
            @Override public void changedUpdate(DocumentEvent e) { upd(); }
        });

        tools.add(sampleBtn);
        tools.add(Box.createHorizontalStrut(8));
        tools.add(largeBtn);
        tools.add(Box.createHorizontalStrut(8));
        tools.add(randomBtn);
        tools.add(Box.createHorizontalStrut(8));
        tools.add(histBtn);
        tools.add(Box.createHorizontalStrut(8));
        tools.add(undoBtn);
        tools.add(Box.createHorizontalStrut(8));
        tools.add(clearBtn);
        tools.add(Box.createHorizontalStrut(12));
        tools.add(countBadge);
        p.add(tools, BorderLayout.SOUTH);
    }

    private void pushUndo() {
        if (restoring || reference == null) return;
        String[] cur = {reference.getText(), frames.getText()};
        if (!undoStack.isEmpty() && Arrays.equals(undoStack.peek(), cur)) return;
        undoStack.push(cur);
        if (undoStack.size() > 15) undoStack.removeLast();
    }

    private void setFields(String ref, String fr) {
        restoring = true;
        reference.setText(ref);
        frames.setText(fr);
        restoring = false;
    }

    private static String countPagesLabel(String raw) {
        String t = raw.trim();
        if (t.isEmpty()) return "0 pages";
        int n = 0;
        for (String tok : t.split("[\\s,]+")) if (!tok.isEmpty()) n++;
        return n + (n == 1 ? " page" : " pages");
    }

    // ============================ RESULTS ============================

    @Override protected void onRunPressed() {
        hasRun = true;
        rerun();
    }

    @Override protected void runSimulation(JPanel r) {
        r.add(pixelText("SIMULATION OUTPUT · PAGE REPLACEMENT", 7f, Theme.dark ? Theme.cyan() : Theme.text(), 0f));
        r.add(Box.createVerticalStrut(12));

        reference.setInvalid(false);
        frames.setInvalid(false);

        if (allBlank(reference, frames)) {
            r.add(Notice.prompt("Type a reference string (e.g. 7 0 1 2 0 3 ...) and frame count (3), or click Sample data."));
            return;
        }
        if (!hasRun) {
            r.add(Notice.prompt("Inputs ready — press RUN SIMULATION (or Enter) to see the frame grid and hit/fault counts."));
            return;
        }

        List<String> refs = null;
        int numFrames = 0;
        boolean bad = false;
        try {
            refs = parseRefs(reference.getText());
        } catch (IllegalArgumentException e) { reference.setInvalid(true); bad = true; }
        try {
            numFrames = Integer.parseInt(frames.getText().trim());
            if (numFrames < 1) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) { frames.setInvalid(true); bad = true; }

        if (bad) {
            r.add(new Notice(false, "Input check",
                    "enter at least one page reference and a positive frame count (1 or more)."));
            return;
        }

        Result res = simulate(algo, refs, numFrames);
        recordHistory(String.format("%s · %d frames · %d refs (%dF / %dH)", algo, numFrames, refs.size(), res.faults, res.hits));
        int total = refs.size();

        // 1) Summary cards (#FAULT, #HIT, SUMMARY)
        Row cards = new Row(new GridLayout(1, 3, 12, 0));
        cards.add(new SummaryCard("#FAULT", String.valueOf(res.faults),
                String.format("%.1f%% fault rate", 100.0 * res.faults / total), Theme.pink()));
        cards.add(new SummaryCard("#HIT", String.valueOf(res.hits),
                String.format("%.1f%% hit ratio", 100.0 * res.hits / total), Theme.green()));
        cards.add(new SummaryCard("SUMMARY", numFrames + (numFrames == 1 ? " frame" : " frames"),
                total + " references · " + algo, Theme.inputLine()));
        r.add(cards);
        r.add(Box.createVerticalStrut(14));

        // 2) Frame grid + H/F row (collapsible + theme scrollbar)
        FrameGrid grid = new FrameGrid(refs, numFrames, res);
        JScrollPane sp = new JScrollPane(grid, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED) {
            private static final long serialVersionUID = 1L;
            @Override public Dimension getPreferredSize() {
                Dimension d = grid.getPreferredSize();
                int sbH = getHorizontalScrollBar().isVisible() ? getHorizontalScrollBar().getPreferredSize().height : 0;
                return new Dimension(d.width, Math.min(d.height + sbH, 460));
            }
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        sp.setAlignmentX(LEFT_ALIGNMENT);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getHorizontalScrollBar().setUnitIncrement(24);
        styleScrollPane(sp);

        JPanel gridBox = new JPanel();
        gridBox.setOpaque(false);
        gridBox.setLayout(new BoxLayout(gridBox, BoxLayout.Y_AXIS));
        JPanel playBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        playBar.setOpaque(false);
        playBar.setAlignmentX(LEFT_ALIGNMENT);
        DashedPill playBtn = new DashedPill("Play >");
        playBtn.addActionListener(e -> grid.play());
        DashedPill stepBtn = new DashedPill("Step >>");
        stepBtn.addActionListener(e -> grid.stepOnce());
        DashedPill allBtn = new DashedPill("Show All");
        allBtn.addActionListener(e -> grid.showAll());
        playBar.add(playBtn); playBar.add(stepBtn); playBar.add(allBtn);
        gridBox.add(playBar);
        gridBox.add(Box.createVerticalStrut(6));
        gridBox.add(sp);

        r.add(collapsibleSection("FRAME GRID · " + algo.toUpperCase() + " (" + total + " PAGES)", gridBox));
        r.add(Box.createVerticalStrut(14));

        // 3) Compare all 3 algorithms (collapsible)
        String[][] cmp = new String[ALGOS.length][];
        int current = -1;
        int minFaults = Integer.MAX_VALUE, maxFaults = Integer.MIN_VALUE;
        String bestAlgo = ALGOS[0], worstAlgo = ALGOS[0];
        int[] faultsArr = new int[ALGOS.length];
        for (int i = 0; i < ALGOS.length; i++) {
            if (ALGOS[i].equals(algo)) current = i;
            Result x = simulate(ALGOS[i], refs, numFrames);
            faultsArr[i] = x.faults;
            if (x.faults < minFaults) { minFaults = x.faults; bestAlgo = ALGOS[i]; }
            if (x.faults > maxFaults) { maxFaults = x.faults; worstAlgo = ALGOS[i]; }
            cmp[i] = new String[]{
                    ALGOS[i],
                    String.valueOf(x.faults),
                    String.valueOf(x.hits),
                    String.format("%.1f%%", 100.0 * x.faults / total),
                    String.format("%.1f%%", 100.0 * x.hits / total)
            };
        }
        r.add(collapsibleSection("COMPARE ALL ALGORITHMS", new CompareTable(cmp, current, faultsArr, minFaults)));

        if (maxFaults > minFaults) {
            r.add(Box.createVerticalStrut(10));
            r.add(new Notice(true, "Why it won",
                    String.format("%s had %d fewer page faults than %s (%d vs %d).",
                            bestAlgo, maxFaults - minFaults, worstAlgo, minFaults, maxFaults)));
        }

        r.add(Box.createVerticalStrut(10));
        final String copyStr = String.format("Page Replacement (%s, %d frames, %d refs)%n#FAULT: %d (%.1f%%) | #HIT: %d (%.1f%%)%n",
                algo, numFrames, total, res.faults, 100.0 * res.faults / total, res.hits, 100.0 * res.hits / total);
        DashedPill copyBtn = new DashedPill("Copy summary");
        copyBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(copyStr), null);
            showToast(this, "Copied summary to clipboard!");
        });
        r.add(copyBtn);
    }

    private void recordHistory(String label) {
        HistoryItem item = new HistoryItem(label, reference.getText().trim(), frames.getText().trim(), algo);
        history.removeIf(h -> h.ref.equals(item.ref) && h.frames.equals(item.frames) && h.algo.equals(item.algo));
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
                reference.setText(item.ref);
                frames.setText(item.frames);
                algo = item.algo;
                rebuildInputs();
                rerun();
                showToast(this, "Restored from history");
            });
            menu.add(mi);
        }
        menu.show(invoker, 0, invoker.getHeight());
    }

    static List<String> parseRefs(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("empty refs");
        String[] parts = trimmed.split("[\\s,]+");
        List<String> list = new ArrayList<>();
        for (String p : parts) {
            if (!p.isEmpty()) list.add(p);
        }
        if (list.isEmpty()) throw new IllegalArgumentException("empty refs");
        return list;
    }

    // ============================ ALGORITHMS ============================

    static final class Result {
        final String[][] grid;   // [frame][step]
        final boolean[] hit;
        final int[] activeSlot;  // which frame slot was loaded (fault) or matched (hit) at step i
        int faults, hits;

        Result(int frames, int steps) {
            this.grid = new String[frames][steps];
            this.hit = new boolean[steps];
            this.activeSlot = new int[steps];
        }
    }

    static Result simulate(String algo, List<String> refs, int numFrames) {
        int n = refs.size();
        Result r = new Result(numFrames, n);
        String[] slots = new String[numFrames];
        int[] loadedAt = new int[numFrames];
        int[] lastUsed = new int[numFrames];

        for (int step = 0; step < n; step++) {
            String page = refs.get(step);
            int found = -1;
            for (int f = 0; f < numFrames; f++) {
                if (page.equals(slots[f])) { found = f; break; }
            }

            if (found >= 0) {
                r.hit[step] = true;
                r.activeSlot[step] = found;
                r.hits++;
                lastUsed[found] = step;
            } else {
                r.hit[step] = false;
                r.faults++;

                int victim = -1;
                for (int f = 0; f < numFrames; f++) {
                    if (slots[f] == null) { victim = f; break; }
                }
                if (victim < 0) {
                    victim = pickVictim(algo, slots, loadedAt, lastUsed, refs, step);
                }

                slots[victim] = page;
                loadedAt[victim] = step;
                lastUsed[victim] = step;
                r.activeSlot[step] = victim;
            }

            for (int f = 0; f < numFrames; f++) {
                r.grid[f][step] = slots[f];
            }
        }
        return r;
    }

    private static int pickVictim(String algo, String[] slots, int[] loadedAt, int[] lastUsed,
                                  List<String> refs, int step) {
        int frames = slots.length;
        return switch (algo) {
            case "LRU" -> {
                int best = 0;
                for (int f = 1; f < frames; f++) {
                    if (lastUsed[f] < lastUsed[best]) best = f;
                }
                yield best;
            }
            case "Optimal" -> {
                int best = 0;
                int furthest = -1;
                for (int f = 0; f < frames; f++) {
                    int nextUse = Integer.MAX_VALUE;
                    for (int k = step + 1; k < refs.size(); k++) {
                        if (slots[f].equals(refs.get(k))) { nextUse = k; break; }
                    }
                    if (nextUse > furthest || (nextUse == furthest && loadedAt[f] < loadedAt[best])) {
                        furthest = nextUse;
                        best = f;
                    }
                }
                yield best;
            }
            default -> { // FIFO
                int best = 0;
                for (int f = 1; f < frames; f++) {
                    if (loadedAt[f] < loadedAt[best]) best = f;
                }
                yield best;
            }
        };
    }

    // ============================ COMPONENTS ============================

    static class SummaryCard extends JPanel {
        private static final long serialVersionUID = 1L;
        private final Color borderColor;

        SummaryCard(String caption, String value, String note, Color borderColor) {
            this.borderColor = borderColor;
            setOpaque(false);
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(12, 16, 12, 16));
            JLabel c = new SafeLabel(caption);
            c.setFont(Fonts.label(8f));
            c.setForeground(Theme.muted());
            add(c);
            add(Box.createVerticalStrut(6));
            Color valColor = (borderColor.equals(Theme.green()) || borderColor.equals(Theme.pink()))
                    ? borderColor : Theme.text();
            add(body(value, 24, valColor));
            add(Box.createVerticalStrut(4));
            add(body(note, 11, Theme.muted()));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            RoundRectangle2D box = new RoundRectangle2D.Double(0.75, 0.75, getWidth() - 1.5, getHeight() - 1.5, 18, 18);
            g2.setColor(Theme.panel());
            g2.fill(box);
            boolean accented = !borderColor.equals(Theme.inputLine());
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(accented ? 2f : 1.5f));
            g2.draw(box);
            g2.dispose();
        }
    }

    /** Frame grid: REF header row, F1..Fn frame rows, and H / F status row at bottom. */
    static class FrameGrid extends JComponent implements Scrollable {
        private static final long serialVersionUID = 1L;
        static final int LABEL_W = 72, MIN_COL_W = 46, HEAD_H = 30, ROW_H = 32, HF_H = 34;
        private final transient List<String> refs;
        private final int numFrames;
        private final transient Result res;
        private int visibleCols;
        private javax.swing.Timer animTimer;

        FrameGrid(List<String> refs, int numFrames, Result res) {
            this.refs = refs;
            this.numFrames = numFrames;
            this.res = res;
            this.visibleCols = refs.size();
            setAlignmentX(LEFT_ALIGNMENT);
            ToolTipManager.sharedInstance().registerComponent(this);
        }

        void play() {
            if (refs.isEmpty()) return;
            if (animTimer != null && animTimer.isRunning()) animTimer.stop();
            visibleCols = 1;
            repaint();
            animTimer = new javax.swing.Timer(300, e -> {
                visibleCols++;
                if (visibleCols >= refs.size()) {
                    visibleCols = refs.size();
                    ((javax.swing.Timer) e.getSource()).stop();
                }
                repaint();
            });
            animTimer.start();
        }

        void stepOnce() {
            if (animTimer != null && animTimer.isRunning()) animTimer.stop();
            visibleCols = visibleCols >= refs.size() ? 1 : visibleCols + 1;
            repaint();
        }

        void showAll() {
            if (animTimer != null && animTimer.isRunning()) animTimer.stop();
            visibleCols = refs.size();
            repaint();
        }

        @Override public String getToolTipText(MouseEvent e) {
            double colW = (getWidth() - 3.0 - LABEL_W) / Math.max(1, refs.size());
            int c = (int) Math.floor((e.getX() - 1.5 - LABEL_W) / colW);
            if (c < 0 || c >= refs.size()) return null;
            String page = refs.get(c);
            int slot = res.activeSlot[c] + 1;
            return res.hit[c]
                    ? String.format("Step %d · Page %s: HIT in F%d", c + 1, page, slot)
                    : String.format("Step %d · Page %s: FAULT → loaded into F%d", c + 1, page, slot);
        }

        @Override public Dimension getPreferredSize() {
            int w = LABEL_W + refs.size() * MIN_COL_W + 3;
            int h = HEAD_H + numFrames * ROW_H + HF_H + 3;
            return new Dimension(w, h);
        }

        @Override public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle v, int o, int d) { return MIN_COL_W; }
        @Override public int getScrollableBlockIncrement(Rectangle v, int o, int d) { return MIN_COL_W * 3; }
        @Override public boolean getScrollableTracksViewportHeight() {
            Container p = getParent();
            return p instanceof JViewport && getPreferredSize().height <= p.getHeight();
        }
        @Override public boolean getScrollableTracksViewportWidth() {
            Container p = getParent();
            return p instanceof JViewport && getPreferredSize().width <= p.getWidth();
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getPreferredSize().height;
            int cols = refs.size();
            double colW = (w - 3.0 - LABEL_W) / Math.max(1, cols);

            RoundRectangle2D box = new RoundRectangle2D.Double(0.75, 0.75, w - 1.5, h - 1.5, 20, 20);
            Shape oldClip = g2.getClip();
            g2.clip(box);

            // Header background
            g2.setColor(Theme.panel2());
            g2.fillRect(0, 0, w, HEAD_H + 2);

            // Frame rows background + cell highlight for active fault/hit cell
            for (int f = 0; f < numFrames; f++) {
                int y = 2 + HEAD_H + f * ROW_H;
                g2.setColor(f % 2 == 0 ? Theme.panel() : Theme.panel2());
                g2.fillRect(0, y, w, ROW_H);

                for (int c = 0; c < visibleCols; c++) {
                    if (res.activeSlot[c] == f) {
                        Color hl = res.hit[c] ? Theme.green() : Theme.pink();
                        g2.setColor(new Color(hl.getRed(), hl.getGreen(), hl.getBlue(), Theme.dark ? 65 : 50));
                        double x1 = 1.5 + LABEL_W + c * colW;
                        g2.fill(new Rectangle.Double(x1, y, colW, ROW_H));
                    }
                }
            }

            // H/F row background
            int hfY = 2 + HEAD_H + numFrames * ROW_H;
            g2.setColor(Theme.panel2());
            g2.fillRect(0, hfY, w, HF_H + 2);

            // Grid lines
            g2.setColor(Theme.line());
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(0, 2 + HEAD_H, w, 2 + HEAD_H);
            for (int f = 1; f <= numFrames; f++) {
                int y = 2 + HEAD_H + f * ROW_H;
                g2.drawLine(0, y, w, y);
            }
            g2.drawLine(Math.round(1.5f + LABEL_W), 0, Math.round(1.5f + LABEL_W), h);
            for (int c = 1; c < cols; c++) {
                int x = (int) Math.round(1.5 + LABEL_W + c * colW);
                g2.drawLine(x, 0, x, h);
            }

            g2.setClip(oldClip);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(box);

            // Row labels on the left
            g2.setFont(Fonts.label(9f));
            g2.setColor(Theme.muted());
            centerText(g2, "REF", 1.5 + LABEL_W / 2.0, middle(g2, 2 + HEAD_H / 2.0));
            for (int f = 0; f < numFrames; f++) {
                centerText(g2, "F" + (f + 1), 1.5 + LABEL_W / 2.0, middle(g2, 2 + HEAD_H + f * ROW_H + ROW_H / 2.0));
            }
            centerText(g2, "H / F", 1.5 + LABEL_W / 2.0, middle(g2, hfY + HF_H / 2.0));

            // Header reference tokens
            g2.setFont(Fonts.body(15f));
            g2.setColor(Theme.text());
            for (int c = 0; c < cols; c++) {
                double cx = 1.5 + LABEL_W + (c + 0.5) * colW;
                centerText(g2, refs.get(c), cx, middle(g2, 2 + HEAD_H / 2.0));
            }

            // Frame cell values
            g2.setFont(Fonts.body(14f));
            for (int f = 0; f < numFrames; f++) {
                double midY = 2 + HEAD_H + f * ROW_H + ROW_H / 2.0;
                for (int c = 0; c < visibleCols; c++) {
                    String val = res.grid[f][c];
                    if (val == null) {
                        g2.setColor(Theme.muted());
                        val = "·";
                    } else if (res.activeSlot[c] == f) {
                        g2.setColor(res.hit[c] ? Theme.green() : Theme.pink());
                    } else {
                        g2.setColor(Theme.text());
                    }
                    double cx = 1.5 + LABEL_W + (c + 0.5) * colW;
                    centerText(g2, val, cx, middle(g2, midY));
                }
            }

            // H / F badges
            g2.setFont(Fonts.arcade(8f));
            for (int c = 0; c < visibleCols; c++) {
                double cx = 1.5 + LABEL_W + (c + 0.5) * colW;
                double cy = hfY + HF_H / 2.0;
                Color badge = res.hit[c] ? Theme.green() : Theme.pink();
                double bw = Math.min(28, colW - 8), bh = 20;
                RoundRectangle2D pill = new RoundRectangle2D.Double(cx - bw / 2.0, cy - bh / 2.0, bw, bh, 10, 10);
                g2.setColor(new Color(badge.getRed(), badge.getGreen(), badge.getBlue(), Theme.dark ? 60 : 45));
                g2.fill(pill);
                g2.setColor(badge);
                g2.setStroke(new BasicStroke(1.2f));
                g2.draw(pill);
                centerText(g2, res.hit[c] ? "H" : "F", cx, middle(g2, cy));
            }
            g2.dispose();
        }
    }

    static class CompareTable extends JComponent {
        private static final long serialVersionUID = 1L;
        static final String[] HEAD = {"ALGORITHM", "#FAULT", "#HIT", "FAULT RATE", "HIT RATIO"};
        static final double[] WEIGHT = {0.24, 0.19, 0.19, 0.19, 0.19};
        static final int HEAD_H = 27, ROW = 29;
        private final String[][] rows;
        private final int highlight, minFaults;
        private final int[] faultsArr;

        CompareTable(String[][] rows, int highlight, int[] faultsArr, int minFaults) {
            this.rows = rows;
            this.highlight = highlight;
            this.faultsArr = faultsArr;
            this.minFaults = minFaults;
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
            Color yellow = Theme.yellow();
            for (int i = 0; i < rows.length; i++) {
                g2.setColor(i == highlight
                        ? new Color(yellow.getRed(), yellow.getGreen(), yellow.getBlue(), Theme.dark ? 60 : 110)
                        : (i % 2 == 0 ? Theme.panel() : Theme.panel2()));
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
            for (int i = 0; i < rows.length; i++) {
                double midY = middle(g2, 2 + HEAD_H + i * ROW + ROW / 2.0);
                boolean isBest = faultsArr[i] == minFaults;
                for (int c = 0; c < HEAD.length; c++) {
                    g2.setColor(isBest && c == 1 ? Theme.green() : Theme.text());
                    String val = (isBest && c == 1) ? rows[i][c] + " (Best)" : rows[i][c];
                    centerText(g2, val, (x[c] + x[c + 1]) / 2, midY);
                }
            }
            g2.dispose();
        }
    }
}
