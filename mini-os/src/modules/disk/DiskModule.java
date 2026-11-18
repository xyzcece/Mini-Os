package modules.disk;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
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
 * DISK SCHEDULING  —  OWNER: Member 2
 *   [x] Requests, initial head, disk max, algorithm dropdown + direction toggle
 *   [x] FCFS, SSTF, SCAN, C-SCAN, LOOK, C-LOOK
 *   [x] Seek table (next track, traverse) + total cylinders + ave seek
 *   [x] Seek-path graph + all-algorithm comparison
 * CHECK: SSTF (head 53): 236 cylinders
 *
 * The page design (header, inputs, run button, result card) comes from FigmaModule.
 */
public class DiskModule extends FigmaModule {
    private static final long serialVersionUID = 1L;
    static final String[] ALGOS = {"FCFS", "SSTF", "SCAN", "C-SCAN", "LOOK", "C-LOOK"};
    private static final String SAMPLE_REQUESTS = "98 183 37 122 14 124 65 67";
    private static final String SAMPLE_HEAD = "53";
    private static final String SAMPLE_MAX = "199";
    private static final String LARGE_REQUESTS = "206 121 445 18 392 84 275 490 63 310 154 22 411 338 95 177 260 44 369 12 480 233 142 301 77";
    private static final String LARGE_HEAD = "150";
    private static final String LARGE_MAX = "499";

    private RoundField requests, head, max;
    private String algo = "FCFS";
    private boolean dirUp = true;
    private boolean hasRun = false;
    private final transient List<HistoryItem> history = new ArrayList<>();
    private final transient Deque<String[]> undoStack = new ArrayDeque<>();
    private final transient Deque<String[]> redoStack = new ArrayDeque<>();
    private boolean restoring = false;

    private record HistoryItem(String label, String reqs, String head, String max, String algo, boolean dirUp) {}

    @Override public String title()    { return "Disk Scheduling"; }
    @Override public String subtitle() { return "Compare seek order and total head movement."; }
    @Override protected void onUndoPressed() {
        if (undoStack.isEmpty()) { showToast(this, "Nothing to undo"); return; }
        if (requests != null) redoStack.push(new String[]{requests.getText(), head.getText(), max.getText()});
        String[] prev = undoStack.pop();
        setFields(prev[0], prev[1], prev[2]);
        hasRun = false;
        markInputsDirty();
        showToast(this, "Undid · Ctrl+Y to redo");
    }

    @Override protected void onRedoPressed() {
        if (redoStack.isEmpty()) { showToast(this, "Nothing to redo"); return; }
        if (requests != null) undoStack.push(new String[]{requests.getText(), head.getText(), max.getText()});
        String[] next = redoStack.pop();
        setFields(next[0], next[1], next[2]);
        hasRun = false;
        markInputsDirty();
        showToast(this, "Redid change");
    }

    @Override protected String crumb() { return "DISK"; }
    @Override protected String lead()  { return "Order the disk requests and add up the head movement."; }

    // ============================ INPUTS ============================

    @Override protected void buildInputs(JPanel p) {
        requests = new RoundField(requests == null ? "" : requests.getText());
        head     = new RoundField(head     == null ? "" : head.getText());
        max      = new RoundField(max      == null ? "" : max.getText());
        requests.addActionListener(e -> onRunPressed());
        head.addActionListener(e -> onRunPressed());
        max.addActionListener(e -> onRunPressed());
        Dropdown pick = new Dropdown(algo, ALGOS, a -> { algo = a; hasRun = false; rebuildInputs(); rerun(); });

        p.add(columns(new double[]{2.4, 1, 1, 1.2},
                labeled("Requests", requests, "tracks separated by spaces"),
                labeled("Initial head", head, null),
                labeled("Disk max", max, null),
                labeled("Algorithm", pick, null)), BorderLayout.CENTER);

        JPanel tools = new JPanel();
        tools.setOpaque(false);
        tools.setLayout(new WrapLayout(FlowLayout.LEFT, 0, 6));   // wraps onto a 2nd line when narrow
        tools.setBorder(new EmptyBorder(10, 0, 0, 0));

        DashedPill dirBtn = new DashedPill(dirUp ? "Direction: Up (→ max)" : "Direction: Down (→ 0)");
        dirBtn.addActionListener(e -> { dirUp = !dirUp; hasRun = false; rebuildInputs(); rerun(); });

        DashedPill sampleBtn = new DashedPill("Sample data");
        sampleBtn.addActionListener(e -> {
            pushUndo();
            setFields(SAMPLE_REQUESTS, SAMPLE_HEAD, SAMPLE_MAX);
            hasRun = false;
            markInputsDirty();
            showToast(this, "Loaded 8-request sample — press RUN SIMULATION");
        });

        DashedPill largeBtn = new DashedPill("Large sample (25)");
        largeBtn.addActionListener(e -> {
            pushUndo();
            setFields(LARGE_REQUESTS, LARGE_HEAD, LARGE_MAX);
            hasRun = false;
            markInputsDirty();
            showToast(this, "Loaded 25 requests — press RUN SIMULATION");
        });

        DashedPill randomBtn = new DashedPill("Random");
        randomBtn.addActionListener(e -> {
            pushUndo();
            int m = 199;
            try {
                int parsed = Integer.parseInt(max.getText().trim());
                if (parsed > 0) m = parsed;
            } catch (NumberFormatException ignored) {}
            Random rng = new Random();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 12; i++) {
                if (i > 0) sb.append(" ");
                sb.append(rng.nextInt(m + 1));
            }
            setFields(sb.toString(), String.valueOf(rng.nextInt(m + 1)), String.valueOf(m));
            hasRun = false;
            markInputsDirty();
            showToast(this, "Generated 12 tracks — press RUN SIMULATION");
        });

        DashedPill trimBtn = new DashedPill("−1");
        trimBtn.setToolTipText("Remove the last request from the queue");
        trimBtn.addActionListener(e -> {
            String cur = requests.getText().trim();
            if (cur.isEmpty()) return;
            pushUndo();
            String[] toks = cur.split("[\\s,;]+");
            String next = toks.length <= 1 ? "" : String.join(" ", java.util.Arrays.copyOf(toks, toks.length - 1));
            requests.setText(next);
            markInputsDirty();
            showToast(this, "Removed last request · Ctrl+Z to undo");
        });

        DashedPill histBtn = new DashedPill("History");
        histBtn.addActionListener(e -> showHistoryMenu(histBtn));

        DashedPill undoBtn = new DashedPill("Undo");
        undoBtn.addActionListener(e -> onUndoPressed());

        DashedPill clearBtn = new DashedPill("Clear");
        clearBtn.addActionListener(e -> {
            pushUndo();
            setFields("", "", "");
            hasRun = false;
            rerun();
            showToast(this, "Cleared · Ctrl+Z to undo");
        });

        JLabel countBadge = body(countTracksLabel(requests.getText()), 11, Theme.pink());
        requests.getDocument().addDocumentListener(new DocumentListener() {
            private void upd() { countBadge.setText(countTracksLabel(requests.getText())); }
            @Override public void insertUpdate(DocumentEvent e) { upd(); }
            @Override public void removeUpdate(DocumentEvent e) { upd(); }
            @Override public void changedUpdate(DocumentEvent e) { upd(); }
        });

        DashedPill badBtn = new DashedPill("Invalid input test");
        badBtn.addActionListener(e -> {
            pushUndo();
            setFields("98 183 -5 400 abc", "250", "199");
            hasRun = true;
            rerun();
            showToast(this, "Loaded bad input - the result card shows what to fix");
        });

        tools.add(dirBtn);
        tools.add(Box.createHorizontalStrut(8));
        tools.add(sampleBtn);
        tools.add(Box.createHorizontalStrut(8));
        tools.add(largeBtn);
        tools.add(Box.createHorizontalStrut(8));
        tools.add(randomBtn);
        tools.add(Box.createHorizontalStrut(8));
        tools.add(badBtn);
        tools.add(Box.createHorizontalStrut(8));
        tools.add(histBtn);
        tools.add(Box.createHorizontalStrut(8));
        tools.add(undoBtn);
        tools.add(Box.createHorizontalStrut(8));
        tools.add(clearBtn);
        tools.add(Box.createHorizontalStrut(8));
        tools.add(trimBtn);
        tools.add(Box.createHorizontalStrut(12));
        tools.add(countBadge);
        p.add(tools, BorderLayout.SOUTH);
    }

    private void pushUndo() {
        if (restoring || requests == null) return;
        String[] cur = {requests.getText(), head.getText(), max.getText()};
        if (!undoStack.isEmpty() && Arrays.equals(undoStack.peek(), cur)) return;
        undoStack.push(cur);
        redoStack.clear();
        if (undoStack.size() > 50) undoStack.removeLast();
    }

    private void setFields(String r, String h, String m) {
        restoring = true;
        requests.setText(r);
        head.setText(h);
        max.setText(m);
        restoring = false;
    }

    private static String countTracksLabel(String raw) {
        String t = raw.trim();
        if (t.isEmpty()) return "0 tracks";
        int n = 0;
        for (String tok : t.split("[\\s,]+")) if (!tok.isEmpty()) n++;
        return n + (n == 1 ? " track" : " tracks");
    }

    // ============================ RESULTS ============================

    @Override protected void onRunPressed() {
        hasRun = true;
        rerun();
    }

    @Override protected void runSimulation(JPanel r) {
        r.add(pixelText("SIMULATION OUTPUT · DISK SCHEDULING", 7f, Theme.dark ? Theme.cyan() : Theme.text(), 0f));
        r.add(Box.createVerticalStrut(12));

        requests.setInvalid(false);
        head.setInvalid(false);
        max.setInvalid(false);

        if (allBlank(requests, head, max)) {
            r.add(Notice.prompt("Type track requests (e.g. 98 183 37 ...), initial head (53) and disk max (199), or click Sample data."));
            return;
        }
        if (!hasRun) {
            r.add(Notice.prompt("Inputs ready — press RUN SIMULATION (or Enter) to see the seek graph and table."));
            return;
        }

        List<Integer> reqList = new ArrayList<>();
        int headPos = 0, maxTrack = 0;
        List<String> problems = new ArrayList<>();
        Integer mx = whole(max.getText(), "the disk max", problems);
        if (mx != null && mx <= 0) { problems.add("the disk max is " + mx + " - it must be more than 0"); mx = null; }
        if (mx == null) max.setInvalid(true); else maxTrack = mx;
        Integer hd = whole(head.getText(), "the initial head", problems);
        if (hd != null && (hd < 0 || (mx != null && hd > maxTrack))) {
            problems.add("the initial head " + hd + " is outside the disk (0 to " + (mx == null ? "disk max" : maxTrack) + ")");
            hd = null;
        }
        if (hd == null) head.setInvalid(true); else headPos = hd;
        String raw = requests.getText().trim();
        if (raw.isEmpty()) { problems.add("the request list is blank - type tracks, e.g. 98 183 37 122"); requests.setInvalid(true); }
        for (String t : raw.isEmpty() ? new String[0] : raw.split("[\\s,]+")) {
            if (t.isEmpty()) continue;
            try {
                int req = Integer.parseInt(t);
                if (req < 0 || (mx != null && req > maxTrack)) {
                    problems.add("request " + req + " is outside the disk (0 to " + (mx == null ? "disk max" : maxTrack) + ")");
                    requests.setInvalid(true);
                }
                reqList.add(req);
            } catch (NumberFormatException e) {
                problems.add("request \"" + t + "\" is not a whole number");
                requests.setInvalid(true);
            }
        }

        if (!problems.isEmpty()) {
            r.add(new Notice(false, "Input check", problems.get(0)));
            for (int i = 1; i < Math.min(problems.size(), 4); i++) { r.add(Box.createVerticalStrut(6)); r.add(new Notice(false, "Also", problems.get(i))); }
            if (problems.size() > 4) { r.add(Box.createVerticalStrut(6)); r.add(body("... and " + (problems.size() - 4) + " more to fix.", 12, Theme.muted())); }
            r.add(Box.createVerticalStrut(6));
            r.add(body("Rule: whole numbers only; disk max > 0, and every request and the initial head between 0 and disk max.", 12, Theme.muted()));
            return;
        }

        Result res = schedule(algo, reqList, headPos, maxTrack, dirUp);
        recordHistory(String.format("%s · head %d · %d tracks (%d cyl)", algo, headPos, reqList.size(), res.total));
        int lastTrack = res.steps.isEmpty() ? headPos : res.steps.get(res.steps.size() - 1).to;

        // 1) Summary cards
        Row cards = new Row(new GridLayout(1, 3, 12, 0));
        cards.add(new SummaryCard("TOTAL CYLINDERS", String.format("%,d cyl", res.total),
                reqList.size() + " requests · " + algo, true));
        cards.add(new SummaryCard("AVERAGE SEEK", String.format("%.3f", res.average),
                String.format("%,d ÷ %d requests", res.total, reqList.size()), false));
        cards.add(new SummaryCard("HEAD MOVEMENT", headPos + " → " + lastTrack,
                res.steps.size() + " moves · " + (dirUp ? "Up (→ " + maxTrack + ")" : "Down (→ 0)"), false));
        r.add(cards);
        r.add(Box.createVerticalStrut(14));

        // 2) Seek-path graph & Seek table (collapsible + hover-synced + Play/Step animation)
        SeekGraph graph = new SeekGraph(headPos, maxTrack, res.steps);
        JPanel graphBox = new JPanel();
        graphBox.setOpaque(false);
        graphBox.setLayout(new BoxLayout(graphBox, BoxLayout.Y_AXIS));
        JPanel playBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        playBar.setOpaque(false);
        playBar.setAlignmentX(LEFT_ALIGNMENT);
        DashedPill playBtn = new DashedPill("Play >");
        playBtn.addActionListener(e -> graph.play());
        DashedPill stepBtn = new DashedPill("Step >>");
        stepBtn.addActionListener(e -> graph.stepOnce());
        DashedPill allBtn = new DashedPill("Show All");
        allBtn.addActionListener(e -> graph.showAll());
        playBar.add(playBtn); playBar.add(stepBtn); playBar.add(allBtn);
        graphBox.add(playBar);
        graphBox.add(Box.createVerticalStrut(6));
        graphBox.add(cappedScroll(graph, 440));

        SeekTable seekTable = new SeekTable(res, reqList.size(), maxTrack, stepIdx -> {
            graph.setHoveredStep(stepIdx);
        });
        r.add(collapsibleSection("SEEK-PATH GRAPH · " + algo.toUpperCase() + " (" + reqList.size() + " REQUESTS)", graphBox));
        r.add(Box.createVerticalStrut(14));

        r.add(collapsibleSection("SEEK TABLE", cappedScroll(seekTable, 380)));
        r.add(Box.createVerticalStrut(14));

        // 4) Compare all 6 algorithms
        String[][] cmp = new String[ALGOS.length][];
        int current = -1;
        int minTotal = Integer.MAX_VALUE, maxTotal = Integer.MIN_VALUE;
        String bestAlgo = ALGOS[0], worstAlgo = ALGOS[0];
        int[] totals = new int[ALGOS.length];
        for (int i = 0; i < ALGOS.length; i++) {
            if (ALGOS[i].equals(algo)) current = i;
            Result x = schedule(ALGOS[i], reqList, headPos, maxTrack, dirUp);
            totals[i] = x.total;
            if (x.total < minTotal) { minTotal = x.total; bestAlgo = ALGOS[i]; }
            if (x.total > maxTotal) { maxTotal = x.total; worstAlgo = ALGOS[i]; }
            cmp[i] = new String[]{
                    ALGOS[i],
                    formatSequence(headPos, x.steps),
                    String.format("%,d", x.total),
                    String.format("%.3f", x.average)
            };
        }
        r.add(collapsibleSection("COMPARE ALL ALGORITHMS", new CompareTable(cmp, current, totals, minTotal)));
        r.add(explainSection(explain(algo, headPos, maxTrack, dirUp, res)));

        if (maxTotal > minTotal) {
            int saved = maxTotal - minTotal;
            double pct = 100.0 * saved / maxTotal;
            r.add(Box.createVerticalStrut(10));
            r.add(new Notice(true, "Why it won",
                    String.format("%s (%d cyl) saved %,d cylinders (%.3f%% less movement) compared to %s (%d cyl).",
                            bestAlgo, minTotal, saved, pct, worstAlgo, maxTotal)));
        }
    }

    private void recordHistory(String label) {
        HistoryItem item = new HistoryItem(label, requests.getText().trim(), head.getText().trim(), max.getText().trim(), algo, dirUp);
        history.removeIf(h -> h.reqs.equals(item.reqs) && h.head.equals(item.head) && h.max.equals(item.max) && h.algo.equals(item.algo) && h.dirUp == item.dirUp);
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
                requests.setText(item.reqs);
                head.setText(item.head);
                max.setText(item.max);
                algo = item.algo;
                dirUp = item.dirUp;
                rebuildInputs();
                rerun();
                showToast(this, "Restored from history");
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

    /** Whole number, or null with a message naming the field. */
    private static Integer whole(String s, String what, List<String> problems) {
        String t = s.trim();
        if (t.isEmpty()) { problems.add(what + " is blank"); return null; }
        try { return Integer.parseInt(t); }
        catch (NumberFormatException e) { problems.add(what + " (\"" + t + "\") is not a whole number"); return null; }
    }

    static List<Integer> parseRequests(String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("empty requests");
        String[] parts = trimmed.split("[\\s,]+");
        List<Integer> list = new ArrayList<>();
        for (String p : parts) {
            if (!p.isEmpty()) list.add(Integer.parseInt(p));
        }
        if (list.isEmpty()) throw new IllegalArgumentException("empty requests");
        return list;
    }

    private static String formatSequence(int head, List<Step> steps) {
        StringBuilder sb = new StringBuilder();
        sb.append(head);
        for (Step s : steps) sb.append(" → ").append(s.to);
        return sb.toString();
    }

    // ============================ ALGORITHMS ============================

    /** One line per head movement: where it went, why, and how far. */
    static List<String> explain(String algo, int head, int maxTrack, boolean dirUp, Result res) {
        List<String> out = new ArrayList<>();
        String dir = dirUp ? "up (toward " + maxTrack + ")" : "down (toward 0)";
        out.add(switch (algo) {
            case "SSTF" -> "Rule (SSTF): always go to the pending request closest to the head.";
            case "SCAN" -> "Rule (SCAN): sweep " + dir + " serving requests, go all the way to the end of the disk, then turn around.";
            case "C-SCAN" -> "Rule (C-SCAN): sweep " + dir + " serving requests to the end of the disk, jump to the other end, and sweep the same way again.";
            case "LOOK" -> "Rule (LOOK): sweep " + dir + " serving requests, turn around at the last request (not the disk end).";
            case "C-LOOK" -> "Rule (C-LOOK): sweep " + dir + " to the last request, jump to the farthest request on the other side, and sweep the same way again.";
            default -> "Rule (FCFS): serve the requests in the order they were given.";
        });
        out.add("Start: head at track " + head + ".");
        for (Step s : res.steps) {
            String why = s.boundary ? "end of the sweep" : switch (algo) {
                case "SSTF" -> "closest pending request";
                case "FCFS" -> "next in the list";
                default -> "next request in the sweep";
            };
            out.add(String.format("%d → %d (%s): |%d − %d| = %,d cylinders.", s.from, s.to, why, s.to, s.from, s.traverse));
        }
        out.add(String.format("Total head movement = sum of the moves = %,d cylinders; average = %,d ÷ requests = %.2f.",
                res.total, res.total, res.average));
        return out;
    }

    static final class Step {
        final int index, from, to, traverse;
        final boolean boundary;

        Step(int index, int from, int to, boolean boundary) {
            this.index = index;
            this.from = from;
            this.to = to;
            this.traverse = Math.abs(to - from);
            this.boundary = boundary;
        }
    }

    static final class Result {
        final List<Step> steps = new ArrayList<>();
        int total;
        double average;
    }

    static Result schedule(String algo, List<Integer> requests, int head, int maxTrack, boolean dirUp) {
        Result r = new Result();
        List<Integer> order = new ArrayList<>();
        List<Boolean> isBoundary = new ArrayList<>();

        switch (algo) {
            case "FCFS" -> {
                for (int req : requests) {
                    order.add(req);
                    isBoundary.add(false);
                }
            }
            case "SSTF" -> {
                List<Integer> remaining = new ArrayList<>(requests);
                int cur = head;
                while (!remaining.isEmpty()) {
                    int bestIdx = 0;
                    int bestDist = Math.abs(remaining.get(0) - cur);
                    for (int i = 1; i < remaining.size(); i++) {
                        int cand = remaining.get(i);
                        int dist = Math.abs(cand - cur);
                        if (dist < bestDist || (dist == bestDist && (dirUp ? cand >= cur : cand <= cur))) {
                            bestDist = dist;
                            bestIdx = i;
                        }
                    }
                    cur = remaining.remove(bestIdx);
                    order.add(cur);
                    isBoundary.add(false);
                }
            }
            case "SCAN" -> {
                List<Integer> down = new ArrayList<>();
                List<Integer> up = new ArrayList<>();
                splitByHead(requests, head, dirUp, down, up);
                if (dirUp) {
                    addAll(order, isBoundary, up);
                    if (!down.isEmpty()) {
                        int last = order.isEmpty() ? head : order.get(order.size() - 1);
                        if (last != maxTrack) { order.add(maxTrack); isBoundary.add(true); }
                        Collections.reverse(down);
                        addAll(order, isBoundary, down);
                    }
                } else {
                    Collections.reverse(down);
                    addAll(order, isBoundary, down);
                    if (!up.isEmpty()) {
                        int last = order.isEmpty() ? head : order.get(order.size() - 1);
                        if (last != 0) { order.add(0); isBoundary.add(true); }
                        addAll(order, isBoundary, up);
                    }
                }
            }
            case "C-SCAN" -> {
                List<Integer> down = new ArrayList<>();
                List<Integer> up = new ArrayList<>();
                splitByHead(requests, head, dirUp, down, up);
                if (dirUp) {
                    addAll(order, isBoundary, up);
                    if (!down.isEmpty()) {
                        int last = order.isEmpty() ? head : order.get(order.size() - 1);
                        if (last != maxTrack) { order.add(maxTrack); isBoundary.add(true); }
                        if (down.get(0) != 0) { order.add(0); isBoundary.add(true); }
                        addAll(order, isBoundary, down);
                    }
                } else {
                    Collections.reverse(down);
                    addAll(order, isBoundary, down);
                    if (!up.isEmpty()) {
                        int last = order.isEmpty() ? head : order.get(order.size() - 1);
                        if (last != 0) { order.add(0); isBoundary.add(true); }
                        Collections.reverse(up);
                        if (up.get(0) != maxTrack) { order.add(maxTrack); isBoundary.add(true); }
                        addAll(order, isBoundary, up);
                    }
                }
            }
            case "LOOK" -> {
                List<Integer> down = new ArrayList<>();
                List<Integer> up = new ArrayList<>();
                splitByHead(requests, head, dirUp, down, up);
                if (dirUp) {
                    addAll(order, isBoundary, up);
                    Collections.reverse(down);
                    addAll(order, isBoundary, down);
                } else {
                    Collections.reverse(down);
                    addAll(order, isBoundary, down);
                    addAll(order, isBoundary, up);
                }
            }
            case "C-LOOK" -> {
                List<Integer> down = new ArrayList<>();
                List<Integer> up = new ArrayList<>();
                splitByHead(requests, head, dirUp, down, up);
                if (dirUp) {
                    addAll(order, isBoundary, up);
                    addAll(order, isBoundary, down);
                } else {
                    Collections.reverse(down);
                    addAll(order, isBoundary, down);
                    Collections.reverse(up);
                    addAll(order, isBoundary, up);
                }
            }
            default -> {
                for (int req : requests) {
                    order.add(req);
                    isBoundary.add(false);
                }
            }
        }

        int cur = head;
        int sum = 0;
        for (int i = 0; i < order.size(); i++) {
            int next = order.get(i);
            Step s = new Step(i + 1, cur, next, isBoundary.get(i));
            r.steps.add(s);
            sum += s.traverse;
            cur = next;
        }
        r.total = sum;
        r.average = requests.isEmpty() ? 0 : (double) sum / requests.size();
        return r;
    }

    private static void splitByHead(List<Integer> requests, int head, boolean dirUp,
                                    List<Integer> down, List<Integer> up) {
        for (int req : requests) {
            if (req < head || (!dirUp && req == head)) down.add(req);
            else up.add(req);
        }
        Collections.sort(down);
        Collections.sort(up);
    }

    private static void addAll(List<Integer> order, List<Boolean> isBoundary, List<Integer> items) {
        for (int v : items) {
            order.add(v);
            isBoundary.add(false);
        }
    }

    // ============================ COMPONENTS ============================

    /** Summary card matching PagingModule's NumberCard style. */
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
            add(body(value, 22, accent ? Theme.green() : Theme.text()));
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

    /** Seek-path graph: track ruler along the top, head path descending step by step. */
    static class SeekGraph extends JComponent {
        private static final long serialVersionUID = 1L;
        private static final int LEFT_GUTTER = 52, RIGHT_GUTTER = 68, TOP_PAD = 36, BOTTOM_PAD = 18;
        private final int head, maxTrack, rowH;
        private final transient List<Step> steps;
        private int hoveredStep = -1;
        private double animStep;
        private javax.swing.Timer animTimer;

        SeekGraph(int head, int maxTrack, List<Step> steps) {
            this.head = head;
            this.maxTrack = maxTrack;
            this.steps = steps;
            this.animStep = steps.size();
            this.rowH = steps.size() > 30 ? 18 : steps.size() > 18 ? 22 : 26;
            setAlignmentX(LEFT_ALIGNMENT);
            ToolTipManager.sharedInstance().registerComponent(this);
        }

        void play() {
            if (steps.isEmpty()) return;
            if (animTimer != null && animTimer.isRunning()) animTimer.stop();
            animStep = 0;
            animTimer = new javax.swing.Timer(25, e -> {
                animStep += 0.14;
                if (animStep >= steps.size()) {
                    animStep = steps.size();
                    ((javax.swing.Timer) e.getSource()).stop();
                }
                repaint();
            });
            animTimer.start();
        }

        void stepOnce() {
            if (animTimer != null && animTimer.isRunning()) animTimer.stop();
            int cur = (int) Math.floor(animStep);
            animStep = cur >= steps.size() ? 1 : Math.min(steps.size(), cur + 1);
            repaint();
        }

        void showAll() {
            if (animTimer != null && animTimer.isRunning()) animTimer.stop();
            animStep = steps.size();
            repaint();
        }

        void setHoveredStep(int stepIdx) {
            if (this.hoveredStep != stepIdx) {
                this.hoveredStep = stepIdx;
                repaint();
            }
        }

        @Override public String getToolTipText(MouseEvent e) {
            int idx = (int) Math.round((e.getY() - (TOP_PAD + 6.0)) / rowH);
            if (idx == 0) return "Initial head: track " + head;
            if (idx >= 1 && idx <= steps.size()) {
                Step s = steps.get(idx - 1);
                return String.format("Step #%d: %d → %d (traverse %d)", s.index, s.from, s.to, s.traverse);
            }
            return null;
        }

        @Override public Dimension getPreferredSize() {
            return new Dimension(360, TOP_PAD + steps.size() * rowH + BOTTOM_PAD);
        }

        @Override public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getPreferredSize().height;
            RoundRectangle2D box = new RoundRectangle2D.Double(0.75, 0.75, w - 1.5, h - 1.5, 20, 20);
            g2.setColor(Theme.panel());
            g2.fill(box);
            g2.setColor(Theme.line());
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(box);

            double plotL = LEFT_GUTTER, plotR = Math.max(plotL + 60, w - RIGHT_GUTTER), plotW = plotR - plotL;
            double scale = plotW / Math.max(1.0, maxTrack);

            // Unique sorted tracks for vertical dashed guides & top ruler
            TreeSet<Integer> tracks = new TreeSet<>();
            tracks.add(0);
            tracks.add(maxTrack);
            tracks.add(head);
            for (Step s : steps) tracks.add(s.to);

            // Ruler line
            double rulerY = TOP_PAD - 8;
            g2.setColor(Theme.line());
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(new Line2D.Double(plotL, rulerY, plotR, rulerY));

            g2.setFont(Fonts.body(10f));
            FontMetrics fmRuler = g2.getFontMetrics();
            Stroke dashed = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{3f, 4f}, 0f);
            String maxLbl = String.valueOf(maxTrack);
            double maxLblLeft = plotR - fmRuler.stringWidth(maxLbl) / 2.0;
            double lastLabelRight = -100;
            for (int t : tracks) {
                double x = plotL + t * scale;
                g2.setColor(Theme.line());
                g2.setStroke(dashed);
                g2.draw(new Line2D.Double(x, rulerY, x, h - BOTTOM_PAD + 4));

                String lbl = String.valueOf(t);
                int tw = fmRuler.stringWidth(lbl);
                double lx = Math.max(plotL - tw / 2.0, Math.min(plotR - tw / 2.0, x - tw / 2.0));
                boolean isEnd = t == 0 || t == maxTrack;
                boolean fitsBetween = lx >= lastLabelRight + 6 && lx + tw <= maxLblLeft - 6;
                if (isEnd || fitsBetween) {
                    g2.setColor(t == head ? Theme.cyan() : Theme.muted());
                    g2.drawString(lbl, (float) lx, (float) (rulerY - 6));
                    lastLabelRight = lx + tw;
                }
            }

            // Build vertex list: index 0 is initial head, 1..n are step destinations
            int count = steps.size() + 1;
            double[] vx = new double[count];
            double[] vy = new double[count];
            int[] vt = new int[count];
            boolean[] vb = new boolean[count];
            vx[0] = plotL + head * scale;
            vy[0] = TOP_PAD + 6;
            vt[0] = head;
            for (int i = 0; i < steps.size(); i++) {
                Step s = steps.get(i);
                vx[i + 1] = plotL + s.to * scale;
                vy[i + 1] = vy[0] + (i + 1) * rowH;
                vt[i + 1] = s.to;
                vb[i + 1] = s.boundary;
            }

            // Horizontal row bands + left step # + right traverse delta
            g2.setFont(Fonts.body(11f));
            for (int i = 0; i < count; i++) {
                g2.setColor(Theme.muted());
                centerText(g2, "#" + i, LEFT_GUTTER / 2.0, middle(g2, vy[i]));
                if (i > 0) {
                    Step s = steps.get(i - 1);
                    g2.setColor(s.boundary ? Theme.muted() : Theme.pink());
                    centerText(g2, "+" + s.traverse, plotR + RIGHT_GUTTER / 2.0, middle(g2, (vy[i - 1] + vy[i]) / 2.0));
                }
            }

            // Segments (supports Play/Step animation)
            int fullSegs = Math.min(count - 1, (int) Math.floor(animStep));
            double frac = animStep - fullSegs;
            for (int i = 0; i < fullSegs; i++) {
                boolean hl = i == hoveredStep;
                g2.setColor(hl ? Theme.yellow() : (steps.get(i).boundary ? Theme.cyan() : Theme.pink()));
                g2.setStroke(new BasicStroke(hl ? 4.0f : 2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Line2D.Double(vx[i], vy[i], vx[i + 1], vy[i + 1]));
            }
            double headDotX = vx[fullSegs], headDotY = vy[fullSegs];
            if (fullSegs < count - 1 && frac > 0.001) {
                headDotX = vx[fullSegs] + (vx[fullSegs + 1] - vx[fullSegs]) * frac;
                headDotY = vy[fullSegs] + (vy[fullSegs + 1] - vy[fullSegs]) * frac;
                g2.setColor(Theme.pink());
                g2.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.draw(new Line2D.Double(vx[fullSegs], vy[fullSegs], headDotX, headDotY));
            }

            // Nodes + track tags
            g2.setFont(Fonts.body(11f));
            FontMetrics fmNode = g2.getFontMetrics();
            int visNodes = Math.min(count, fullSegs + 1);
            for (int i = 0; i < visNodes; i++) {
                double r = i == 0 ? 5.0 : 4.5;
                Color fill = i == 0 ? Theme.cyan() : (vb[i] ? Theme.yellow() : Theme.green());
                g2.setColor(fill);
                g2.fill(new Ellipse2D.Double(vx[i] - r, vy[i] - r, r * 2, r * 2));
                g2.setColor(Theme.panel());
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new Ellipse2D.Double(vx[i] - r, vy[i] - r, r * 2, r * 2));

                String tag = String.valueOf(vt[i]);
                int tw = fmNode.stringWidth(tag);
                boolean placeRight = vx[i] + 8 + tw <= plotR + 6;
                float tx = (float) (placeRight ? vx[i] + 8 : vx[i] - 8 - tw);
                g2.setColor(Theme.text());
                g2.drawString(tag, tx, (float) middle(g2, vy[i]));
            }
            if (animStep < steps.size()) {
                g2.setColor(new Color(255, 79, 168, 90));
                g2.fill(new Ellipse2D.Double(headDotX - 9, headDotY - 9, 18, 18));
                g2.setColor(Theme.pink());
                g2.fill(new Ellipse2D.Double(headDotX - 5, headDotY - 5, 10, 10));
            }
            g2.dispose();
        }
    }

    /** Seek table: # / FROM / NEXT TRACK / CALCULATION / TRAVERSE + total row. */
    static class SeekTable extends JComponent {
        private static final long serialVersionUID = 1L;
        static final String[] HEAD = {"#", "FROM", "NEXT TRACK", "CALCULATION", "TRAVERSE"};
        static final double[] WEIGHT = {0.08, 0.16, 0.22, 0.34, 0.20};
        static final int HEAD_H = 27, ROW = 30;
        private final String[][] rows;
        private int hoveredRow = -1;

        SeekTable(Result res, int reqCount, int maxTrack, java.util.function.IntConsumer onHover) {
            this.rows = new String[res.steps.size() + 1][];
            for (int i = 0; i < res.steps.size(); i++) {
                Step s = res.steps.get(i);
                String nextLbl = s.boundary
                        ? s.to + (s.to == maxTrack ? " (max)" : " (end)")
                        : String.valueOf(s.to);
                rows[i] = new String[]{
                        String.valueOf(s.index),
                        String.valueOf(s.from),
                        nextLbl,
                        String.format("|%d − %d|", s.to, s.from),
                        String.format("%,d", s.traverse)
                };
            }
            rows[res.steps.size()] = new String[]{
                    "TOTAL",
                    "—",
                    reqCount + " requests",
                    String.format("Ave = %.3f cyl/req", res.average),
                    String.format("%,d cyl", res.total)
            };
            setAlignmentX(LEFT_ALIGNMENT);
            addMouseMotionListener(new MouseAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int idx = (e.getY() - (2 + HEAD_H)) / ROW;
                    int next = (idx >= 0 && idx < res.steps.size()) ? idx : -1;
                    if (next != hoveredRow) {
                        hoveredRow = next;
                        if (onHover != null) onHover.accept(next);
                        repaint();
                    }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent e) {
                    if (hoveredRow != -1) {
                        hoveredRow = -1;
                        if (onHover != null) onHover.accept(-1);
                        repaint();
                    }
                }
            });
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
                boolean isTotal = i == rows.length - 1;
                if (isTotal) {
                    Color green = Theme.green();
                    g2.setColor(new Color(green.getRed(), green.getGreen(), green.getBlue(), Theme.dark ? 55 : 70));
                } else if (i == hoveredRow) {
                    Color yellow = Theme.yellow();
                    g2.setColor(new Color(yellow.getRed(), yellow.getGreen(), yellow.getBlue(), Theme.dark ? 55 : 95));
                } else {
                    g2.setColor(i % 2 == 0 ? Theme.panel() : Theme.panel2());
                }
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
            g2.setFont(Fonts.body(14f));
            for (int i = 0; i < rows.length; i++) {
                boolean isTotal = i == rows.length - 1;
                for (int c = 0; c < HEAD.length; c++) {
                    g2.setColor(isTotal && c == HEAD.length - 1 && Theme.dark ? Theme.green() : Theme.text());
                    centerText(g2, rows[i][c], (x[c] + x[c + 1]) / 2, middle(g2, 2 + HEAD_H + i * ROW + ROW / 2.0));
                }
            }
            g2.dispose();
        }
    }

    /** Comparison table across all 6 disk scheduling algorithms, with clipping-safe Seek Order. */
    static class CompareTable extends JComponent {
        private static final long serialVersionUID = 1L;
        static final String[] HEAD = {"ALGORITHM", "SEEK ORDER", "TOTAL CYLINDERS", "AVE SEEK"};
        static final double[] WEIGHT = {0.14, 0.52, 0.18, 0.16};
        static final int HEAD_H = 27, ROW = 29;
        private final String[][] rows;
        private final int highlight, minTotal;
        private final int[] totals;

        CompareTable(String[][] rows, int highlight, int[] totals, int minTotal) {
            this.rows = rows;
            this.highlight = highlight;
            this.totals = totals;
            this.minTotal = minTotal;
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
            FontMetrics fm = g2.getFontMetrics();
            for (int i = 0; i < rows.length; i++) {
                double midY = middle(g2, 2 + HEAD_H + i * ROW + ROW / 2.0);
                boolean isBest = totals[i] == minTotal;
                for (int c = 0; c < HEAD.length; c++) {
                    g2.setColor(isBest && c >= 2 ? Theme.green() : Theme.text());
                    if (c == 1) {
                        double maxW = x[2] - x[1] - 18;
                        String s = fitText(rows[i][c], fm, maxW);
                        g2.drawString(s, (float) (x[1] + 10), (float) midY);
                    } else {
                        String val = (isBest && c == 2) ? rows[i][c] + " (Best)" : rows[i][c];
                        centerText(g2, val, (x[c] + x[c + 1]) / 2, midY);
                    }
                }
            }
            g2.dispose();
        }

        private static String fitText(String s, FontMetrics fm, double maxW) {
            if (fm.stringWidth(s) <= maxW) return s;
            String ell = "…";
            int ellW = fm.stringWidth(ell);
            int end = s.length();
            while (end > 0 && fm.stringWidth(s.substring(0, end)) + ellW > maxW) end--;
            return s.substring(0, end) + ell;
        }
    }
}
