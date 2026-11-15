package modules.cpu;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import modules.common.FigmaModule;
import shared.Fonts;
import shared.Theme;

/**
 * CPU SCHEDULING  —  OWNER: Member 1
 *   [x] AT/RT job input table (7 jobs) + Add job / Remove job (J# is numbered 1..n)
 *   [x] Algorithm dropdown: FIFO, SJF, SRT, Round Robin + time quantum
 *   [x] Job table: J#, AT, RT, ST, FT, WT, CT, TAT
 *   [x] AWT and ATAT + Gantt chart (+ comparison of all 4 algorithms)
 * CHECK: FIFO (7 sample jobs): AWT = 46.43, ATAT = 63.57
 *        SJF 33.57 / 50.71 · SRT 27.86 / 45.00 · RR(q=10) 57.86 / 75.00
 *
 * Words used in the table:
 *   AT = arrival time, RT = run (burst) time, ST = start time, FT = finish time,
 *   WT = waiting time (TAT - RT), CT = completion time (= FT), TAT = turnaround (FT - AT)
 *
 * Layout follows the Figma frame "CPU Scheduling" (mini-os file, node 1:7).
 */
public class CpuModule extends FigmaModule {
    private static final long serialVersionUID = 1L;

    static final String[] ALGOS = {"FIFO", "SJF", "SRT", "Round Robin"};

    /**
     * Jobs as {AT, RT}; job J# is its position (1..n). Starts with 7 empty jobs for the person to fill in
     * (class example: AT 0,5,10,15,20,25,30 · RT 30,15,25,10,20,5,15 · quantum 10).
     */
    private final transient List<String[]> jobs = new ArrayList<>();
    { for (int i = 0; i < 7; i++) jobs.add(new String[]{"", ""}); }
    private final transient List<JTextField[]> jobFields = new ArrayList<>();
    private JTextField quantum;
    private String algoChoice = "FIFO";
    private String quantumText = "";

    @Override public String title()    { return "CPU Scheduling"; }
    @Override public String subtitle() { return "Compare FIFO, SJF, SRT, and Round Robin (quantum = 10 min)."; }
    @Override protected String crumb() { return "CPU"; }
    @Override protected String lead()  { return "Pick a policy, then read the job table, averages and Gantt chart."; }
    @Override protected int inputsTop() { return 4; }

    // ============================ INPUTS ============================

    @Override protected void buildInputs(JPanel p) {
        saveFields();   // keep what the user typed (theme change, add/remove job, new algorithm)
        jobFields.clear();
        quantum = null;

        JComponent jobBox = jobBox(), settings = settings();
        // job box (fit to content) + settings column (fills the rest); stacks when there is no room
        JPanel grid = new JPanel(null) {
            private static final long serialVersionUID = 1L;
            private boolean assumedSide = true;   // what the last preferred height was based on
            private boolean side() { return getWidth() == 0 || getWidth() >= jobBox.getPreferredSize().width + 16 + 220; }
            @Override public void doLayout() {
                Dimension j = jobBox.getPreferredSize(), s = settings.getPreferredSize();
                boolean side = side();
                if (side) {
                    jobBox.setBounds(0, 0, j.width, j.height);
                    settings.setBounds(j.width + 16, 0, getWidth() - j.width - 16, s.height);
                } else {
                    jobBox.setBounds(0, 0, Math.min(j.width, getWidth()), j.height);
                    settings.setBounds(0, j.height + 14, getWidth(), s.height);
                }
                // height was worked out for the other arrangement (width was not known yet): measure again
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
    }

    /** "JOBS" box: J# header, an AT row and an RT row of small inputs, + Add job. */
    private JComponent jobBox() {
        Box12 b = new Box12(null);
        b.setLayout(new BoxLayout(b, BoxLayout.Y_AXIS));

        JPanel head = hRow();
        head.add(pixelText("JOBS", 7f, Theme.dark ? new Color(0xff8fd0) : Theme.pink(), 0f));
        head.add(Box.createHorizontalStrut(10));
        head.add(body("times in minutes", 11, Theme.muted()));
        b.add(head);
        b.add(Box.createVerticalStrut(6));

        JPanel ids = hRow(), at = hRow(), rt = hRow();
        ids.add(cell("J#", 40, 22, Theme.muted()));
        at.add(cell("AT", 40, 30, Theme.muted()));
        rt.add(cell("RT", 40, 30, Theme.muted()));
        for (int i = 0; i < jobs.size(); i++) {
            JTextField a = new RoundField(jobs.get(i)[0], 6, 46, 30), r = new RoundField(jobs.get(i)[1], 6, 46, 30);
            jobFields.add(new JTextField[]{a, r});
            ids.add(Box.createHorizontalStrut(6)); ids.add(cell(String.valueOf(i + 1), 46, 22, Theme.text()));
            at.add(Box.createHorizontalStrut(6));  at.add(a);
            rt.add(Box.createHorizontalStrut(6));  rt.add(r);
        }
        b.add(ids); b.add(Box.createVerticalStrut(6));
        b.add(at);  b.add(Box.createVerticalStrut(6));
        b.add(rt);  b.add(Box.createVerticalStrut(6));

        JPanel foot = hRow();
        DashedPill add = new DashedPill("+ Add job");
        add.addActionListener(e -> { saveFields(); jobs.add(new String[]{"", ""}); rebuildInputs(); });
        foot.add(add);
        if (jobs.size() > 1) {
            DashedPill remove = new DashedPill("− Remove");
            remove.addActionListener(e -> { saveFields(); jobs.remove(jobs.size() - 1); rebuildInputs(); });
            foot.add(Box.createHorizontalStrut(8));
            foot.add(remove);
        }
        foot.add(Box.createHorizontalStrut(12));
        foot.add(body("AT = arrival time · RT = run time", 11, Theme.muted()));
        b.add(foot);
        return b;
    }

    /** Algorithm dropdown, plus the time quantum when Round Robin is picked. */
    private JComponent settings() {
        JPanel s = new JPanel();
        s.setOpaque(false);
        s.setLayout(new BoxLayout(s, BoxLayout.Y_AXIS));
        s.add(fieldLabel("Algorithm"));
        s.add(Box.createVerticalStrut(6));
        s.add(new Dropdown(algoChoice, ALGOS, pick -> { saveFields(); algoChoice = pick; rebuildInputs(); rerun(); }));
        if (algoChoice.equals("Round Robin")) {
            s.add(Box.createVerticalStrut(10));
            s.add(fieldLabel("Time quantum (min)"));
            s.add(Box.createVerticalStrut(6));
            quantum = new RoundField(quantumText);
            s.add(quantum);
        }
        return s;
    }

    private void saveFields() {
        if (quantum != null) quantumText = quantum.getText();
        if (jobFields.size() != jobs.size()) return;
        for (int i = 0; i < jobs.size(); i++)
            for (int c = 0; c < 2; c++) jobs.get(i)[c] = jobFields.get(i)[c].getText().trim();
    }

    private static JPanel hRow() {
        JPanel r = new JPanel();
        r.setOpaque(false);
        r.setLayout(new BoxLayout(r, BoxLayout.X_AXIS));
        r.setAlignmentX(LEFT_ALIGNMENT);
        return r;
    }

    /** Fixed-size centred Silkscreen label (the J#, AT and RT headings of the job box). */
    private static JLabel cell(String s, int w, int h, Color c) {
        JLabel l = new JLabel(s, SwingConstants.CENTER);
        l.setFont(Fonts.label(9f)); l.setForeground(c);
        Dimension d = new Dimension(w, h);
        l.setPreferredSize(d); l.setMinimumSize(d); l.setMaximumSize(d);
        return l;
    }

    // ============================ RESULTS ============================

    @Override protected void runSimulation(JPanel r) {
        saveFields();
        String algo = algoChoice;

        boolean rr = algo.equals("Round Robin");
        boolean nothingTyped = jobs.stream().allMatch(j -> j[0].isEmpty() && j[1].isEmpty());
        if (nothingTyped) {
            r.add(heading(algo.toUpperCase(), null));
            r.add(Box.createVerticalStrut(10));
            r.add(Notice.prompt("Type the arrival time (AT) and run time (RT) of each job"
                    + (rr ? ", and the time quantum," : ",") + " then press RUN SIMULATION."));
            return;
        }

        List<Job> list;
        Integer q = quantum(quantumText);   // only needed for Round Robin
        try {
            list = readJobs();
            if (rr && q == null) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            r.add(heading(algo.toUpperCase(), null));
            r.add(Box.createVerticalStrut(10));
            r.add(new Notice(false, "Input check",
                    "every AT and RT must be a whole number (AT ≥ 0, RT > 0)" + (rr ? " and the time quantum more than 0" : "")));
            return;
        }

        Result res = schedule(algo, list, rr ? q : 1);

        // 1) heading + job table
        r.add(heading(rr ? "ROUND ROBIN · Q=" + q : algo.toUpperCase(),
                list.size() + " jobs · times in minutes"));
        r.add(Box.createVerticalStrut(10));
        String[][] rows = new String[list.size()][];
        for (int i = 0; i < list.size(); i++) {
            Job j = list.get(i);
            int st = res.start.get(j.id), ft = res.finish.get(j.id);
            int tat = ft - j.at, wt = tat - j.rt;
            rows[i] = new String[]{j.id, "" + j.at, "" + j.rt, "" + st, "" + ft, "" + wt, "" + ft, "" + tat};
        }
        r.add(new DataTable(new String[]{"J#", "AT", "RT", "ST", "FT", "WT", "CT", "TAT"}, rows, -1));
        r.add(Box.createVerticalStrut(10));

        // 2) averages
        JPanel avg = hRow();
        avg.add(body(String.format("AWT = %.2f", res.awt), 16, Theme.pink()));
        avg.add(Box.createHorizontalStrut(28));
        avg.add(body(String.format("ATAT = %.2f", res.atat), 16, Theme.dark ? Theme.cyan() : Theme.text()));
        avg.add(Box.createHorizontalStrut(28));
        avg.add(body("WT = TAT − RT  ·  TAT = CT − AT", 12, Theme.muted()));
        r.add(pad(avg, 0, 0, false));
        r.add(Box.createVerticalStrut(10));

        // 3) Gantt chart
        r.add(pixelText("GANTT CHART", 7f, Theme.muted(), 0f));
        r.add(Box.createVerticalStrut(10));
        Map<String, Integer> colorOf = new HashMap<>();
        for (Job j : list) colorOf.put(j.id, j.order);
        r.add(new Gantt(res.segments, colorOf));

        // 4) compare all algorithms
        r.add(Box.createVerticalStrut(16));
        r.add(pixelText("COMPARE ALL ALGORITHMS", 7f, Theme.muted(), 0f));
        r.add(Box.createVerticalStrut(10));
        String[][] cmp = new String[ALGOS.length][];
        int current = -1;
        for (int i = 0; i < ALGOS.length; i++) {
            if (ALGOS[i].equals(algo)) current = i;
            if (ALGOS[i].equals("Round Robin") && q == null) {   // no quantum typed yet
                cmp[i] = new String[]{"Round Robin (pick it to set q)", "—", "—"};
                continue;
            }
            Result x = schedule(ALGOS[i], list, q == null ? 1 : q);
            cmp[i] = new String[]{ALGOS[i].equals("Round Robin") ? "Round Robin (q=" + q + ")" : ALGOS[i],
                    String.format("%.2f", x.awt), String.format("%.2f", x.atat)};
        }
        r.add(new DataTable(new String[]{"Algorithm", "AWT", "ATAT"}, cmp, current));
    }

    /** The time quantum if a whole number > 0 was typed, otherwise null. */
    private static Integer quantum(String s) {
        try { int q = Integer.parseInt(s.trim()); return q > 0 ? q : null; }
        catch (NumberFormatException e) { return null; }
    }

    private JComponent heading(String algo, String note) {
        JPanel h = hRow();
        h.add(pixelText(algo, 9f, Theme.text(), 0f));
        if (note != null) {
            h.add(Box.createHorizontalStrut(16));
            h.add(body(note, 12, Theme.muted()));
        }
        return pad(h, 0, 0, false);
    }

    private List<Job> readJobs() {
        List<Job> list = new ArrayList<>();
        for (String[] j : jobs) {
            Job job = new Job(String.valueOf(list.size() + 1), Integer.parseInt(j[0]), Integer.parseInt(j[1]), list.size());
            if (job.at < 0 || job.rt <= 0) throw new NumberFormatException();
            list.add(job);
        }
        return list;
    }

    // ============================ ALGORITHMS ============================

    static class Job {
        final String id; final int at, rt, order;
        Job(String id, int at, int rt, int order) { this.id = id; this.at = at; this.rt = rt; this.order = order; }
    }

    /** One block on the Gantt chart. id == null means the CPU is idle. */
    static class Segment {
        final String id; final int from; int to;
        Segment(String id, int from, int to) { this.id = id; this.from = from; this.to = to; }
    }

    static class Result {
        final List<Segment> segments = new ArrayList<>();
        final Map<String, Integer> start = new HashMap<>(), finish = new HashMap<>();
        double awt, atat;
    }

    static Result schedule(String algo, List<Job> jobs, int quantum) {
        Result r = switch (algo) {
            case "SJF" -> nonPreemptive(jobs, Comparator.<Job>comparingInt(j -> j.rt).thenComparingInt(j -> j.at).thenComparingInt(j -> j.order));
            case "SRT" -> srt(jobs);
            case "Round Robin" -> roundRobin(jobs, quantum);
            default -> nonPreemptive(jobs, Comparator.<Job>comparingInt(j -> j.at).thenComparingInt(j -> j.order));
        };
        double w = 0, t = 0;
        for (Job j : jobs) { int tat = r.finish.get(j.id) - j.at; t += tat; w += tat - j.rt; }
        r.awt = w / jobs.size(); r.atat = t / jobs.size();
        return r;
    }

    /** FIFO and SJF: pick a job from the ones that have arrived and run it to the end. */
    static Result nonPreemptive(List<Job> jobs, Comparator<Job> pick) {
        Result r = new Result();
        List<Job> pending = new ArrayList<>(jobs);
        int t = 0;
        while (!pending.isEmpty()) {
            final int now = t;
            Optional<Job> next = pending.stream().filter(j -> j.at <= now).min(pick);
            if (!next.isPresent()) {
                int arrive = pending.stream().mapToInt(j -> j.at).min().getAsInt();
                addSegment(r, null, t, arrive); t = arrive; continue;
            }
            Job j = next.get();
            r.start.put(j.id, t);
            addSegment(r, j.id, t, t + j.rt);
            t += j.rt;
            r.finish.put(j.id, t);
            pending.remove(j);
        }
        return r;
    }

    /** SRT: always run the job with the shortest remaining time; re-check whenever a new job arrives. */
    static Result srt(List<Job> jobs) {
        Result r = new Result();
        Map<String, Integer> rem = new HashMap<>();
        for (Job j : jobs) rem.put(j.id, j.rt);
        int t = 0;
        while (r.finish.size() < jobs.size()) {
            final int now = t;
            Optional<Job> next = jobs.stream().filter(j -> j.at <= now && !r.finish.containsKey(j.id))
                    .min(Comparator.<Job>comparingInt(j -> rem.get(j.id)).thenComparingInt(j -> j.at).thenComparingInt(j -> j.order));
            int nextArrival = jobs.stream().filter(j -> j.at > now).mapToInt(j -> j.at).min().orElse(Integer.MAX_VALUE);
            if (!next.isPresent()) { addSegment(r, null, t, nextArrival); t = nextArrival; continue; }
            Job j = next.get();
            int run = Math.min(rem.get(j.id), nextArrival - t);
            r.start.putIfAbsent(j.id, t);
            addSegment(r, j.id, t, t + run);
            t += run;
            rem.put(j.id, rem.get(j.id) - run);
            if (rem.get(j.id) == 0) r.finish.put(j.id, t);
        }
        return r;
    }

    /** Round Robin: each job runs for at most one quantum, then goes to the back of the line. */
    static Result roundRobin(List<Job> jobs, int quantum) {
        Result r = new Result();
        List<Job> byArrival = new ArrayList<>(jobs);
        byArrival.sort(Comparator.<Job>comparingInt(j -> j.at).thenComparingInt(j -> j.order));
        Map<String, Integer> rem = new HashMap<>();
        for (Job j : jobs) rem.put(j.id, j.rt);
        Deque<Job> queue = new ArrayDeque<>();
        Set<String> admitted = new HashSet<>();
        int t = 0;
        t = admit(byArrival, admitted, queue, t);
        while (r.finish.size() < jobs.size()) {
            if (queue.isEmpty()) {
                final int now = t;
                int arrive = byArrival.stream().filter(j -> !admitted.contains(j.id)).mapToInt(j -> j.at).min().getAsInt();
                addSegment(r, null, now, arrive); t = arrive;
                admit(byArrival, admitted, queue, t);
                continue;
            }
            Job j = queue.poll();
            int run = Math.min(quantum, rem.get(j.id));
            r.start.putIfAbsent(j.id, t);
            addSegment(r, j.id, t, t + run);
            t += run;
            rem.put(j.id, rem.get(j.id) - run);
            admit(byArrival, admitted, queue, t);          // new arrivals line up first
            if (rem.get(j.id) > 0) queue.add(j); else r.finish.put(j.id, t);
        }
        return r;
    }

    private static int admit(List<Job> byArrival, Set<String> admitted, Deque<Job> queue, int t) {
        for (Job j : byArrival) if (j.at <= t && admitted.add(j.id)) queue.add(j);
        return t;
    }

    /** Adds a block, joining it to the previous one if the same job keeps running. */
    private static void addSegment(Result r, String id, int from, int to) {
        if (to <= from) return;
        if (!r.segments.isEmpty()) {
            Segment last = r.segments.get(r.segments.size() - 1);
            if (Objects.equals(last.id, id) && last.to == from) { last.to = to; return; }
        }
        r.segments.add(new Segment(id, from, to));
    }

    // ============================ COMPONENTS ============================

    /** Rounded table with equal columns, alternating rows and an optional highlighted row. */
    static class DataTable extends JComponent {
        private static final long serialVersionUID = 1L;
        static final int HEAD = 27, ROW = 29;
        private final String[] headers; private final String[][] rows; private final int highlight;
        DataTable(String[] headers, String[][] rows, int highlight) {
            this.headers = headers; this.rows = rows; this.highlight = highlight;
            setAlignmentX(LEFT_ALIGNMENT);
        }
        @Override public Dimension getPreferredSize() { return new Dimension(300, HEAD + ROW * rows.length + 3); }
        @Override public Dimension getMaximumSize()   { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            int w = getWidth(), h = getPreferredSize().height;
            double colW = (w - 3) / (double) headers.length;
            RoundRectangle2D box = new RoundRectangle2D.Double(0.75, 0.75, w - 1.5, h - 1.5, 20, 20);
            Shape oldClip = g2.getClip();
            g2.clip(box);
            g2.setColor(Theme.panel2()); g2.fillRect(0, 0, w, HEAD + 2);
            for (int i = 0; i < rows.length; i++) {
                Color yellow = Theme.yellow();
                g2.setColor(i == highlight ? new Color(yellow.getRed(), yellow.getGreen(), yellow.getBlue(), Theme.dark ? 60 : 110)
                        : (i % 2 == 0 ? Theme.panel() : Theme.panel2()));
                g2.fillRect(0, 2 + HEAD + i * ROW, w, ROW + 2);
            }
            g2.setColor(Theme.line()); g2.setStroke(new BasicStroke(1f));
            for (int c = 1; c < headers.length; c++) {
                int x = (int) Math.round(1.5 + c * colW);
                g2.drawLine(x, 0, x, h);
            }
            g2.setClip(oldClip);
            g2.setStroke(new BasicStroke(1.5f)); g2.draw(box);

            g2.setFont(Fonts.label(10f)); g2.setColor(Theme.muted());
            for (int c = 0; c < headers.length; c++) centerText(g2, headers[c], 1.5 + (c + 0.5) * colW, middle(g2, 2 + HEAD / 2.0));
            g2.setFont(Fonts.body(13f)); g2.setColor(Theme.text());
            for (int i = 0; i < rows.length; i++)
                for (int c = 0; c < rows[i].length; c++)
                    centerText(g2, rows[i][c], 1.5 + (c + 0.5) * colW, middle(g2, 2 + HEAD + i * ROW + ROW / 2.0));
            g2.dispose();
        }
    }

    /** Gantt chart: one coloured block per run (colour by job), time marks underneath. */
    static class Gantt extends JComponent {
        private static final long serialVersionUID = 1L;
        static final int BAR = 34;
        private final transient List<Segment> segs; private final transient Map<String, Integer> colorOf;
        Gantt(List<Segment> segs, Map<String, Integer> colorOf) {
            this.segs = segs; this.colorOf = colorOf;
            setAlignmentX(LEFT_ALIGNMENT);
        }
        @Override public Dimension getPreferredSize() { return new Dimension(300, BAR + 3 + 2 + 16); }
        @Override public Dimension getMaximumSize()   { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }

        @Override protected void paintComponent(Graphics g) {
            if (segs.isEmpty()) return;
            Graphics2D g2 = smooth(g);
            int w = getWidth(), start = segs.get(0).from, end = segs.get(segs.size() - 1).to;
            double scale = (w - 3) / (double) Math.max(1, end - start);
            RoundRectangle2D bar = new RoundRectangle2D.Double(0.75, 0.75, w - 1.5, BAR + 1.5, 16, 16);

            Shape oldClip = g2.getClip();
            g2.clip(bar);
            g2.setFont(Fonts.body(13f));
            for (Segment s : segs) {
                double x1 = 1.5 + (s.from - start) * scale, x2 = 1.5 + (s.to - start) * scale;
                g2.setColor(s.id == null ? Theme.panel2() : blockColor(colorOf.getOrDefault(s.id, 0), 1));
                g2.fill(new Rectangle.Double(x1, 0, x2 - x1, BAR + 3));
                g2.setColor(Theme.panel()); g2.setStroke(new BasicStroke(1f));
                g2.draw(new java.awt.geom.Line2D.Double(x2, 0, x2, BAR + 3));
                String label = s.id == null ? "idle" : "J" + s.id;
                if (g2.getFontMetrics().stringWidth(label) < x2 - x1 - 4) {
                    g2.setColor(s.id == null ? Theme.muted() : glyph());
                    centerText(g2, label, (x1 + x2) / 2, middle(g2, 1.5 + BAR / 2.0));
                }
            }
            g2.setClip(oldClip);
            g2.setColor(Theme.line()); g2.setStroke(new BasicStroke(1.5f)); g2.draw(bar);

            // time marks at every block edge, centred on the edge, kept inside the chart and not overlapping
            g2.setFont(Fonts.body(10f)); g2.setColor(Theme.muted());
            FontMetrics fm = g2.getFontMetrics();
            List<Integer> marks = new ArrayList<>();
            for (Segment s : segs) marks.add(s.from);
            marks.add(end);
            double lastRight = -100;
            for (int t : marks) {
                String s = String.valueOf(t);
                int tw = fm.stringWidth(s);
                double x = 1.5 + (t - start) * scale, lx = Math.max(0, Math.min(w - tw, x - tw / 2.0));
                if (lx < lastRight + 4) continue;
                g2.drawString(s, (float) lx, BAR + 3 + 2 + fm.getAscent());
                lastRight = lx + tw;
            }
            g2.dispose();
        }
    }
}
