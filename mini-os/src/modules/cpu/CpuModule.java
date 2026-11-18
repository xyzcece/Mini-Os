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
import modules.common.EntryGrid;
import modules.common.FigmaModule;
import shared.Fonts;
import shared.Theme;

/**
 * CPU SCHEDULING  —  OWNER: Member 1
 *   [x] AT/RT job input: boxes for up to 7 jobs, a scrolling table for 8+ (same as Multiprogramming),
 *       with +1 / +5 / −1 / [count] Set / Paste / Bulk (J# is numbered 1..n)
 *   [x] Algorithm dropdown: FIFO, SJF, SRT, Priority (non-preemptive) and Priority (preemptive)
 *       (lower PRI number = higher priority),
 *       Round Robin + time quantum
 *   [x] Job table: J#, AT, RT, ST, FT, WT, CT, TAT
 *   [x] AWT and ATAT + Gantt chart (+ comparison of all 6 algorithms)
 *   [x] Toolbar: Sample data, Idle-gap sample, Large sample (20 jobs), Random, Invalid input test,
 *       History (recent runs), Undo, Clear - for the small/large/boundary/error test cases in the rubric
 * CHECK: FIFO (7 sample jobs): AWT = 46.43, ATAT = 63.57
 *        SJF 33.57 / 50.71 · SRT 27.86 / 45.00 · RR(q=10) 57.86 / 75.00
 *        Priority (PRI 3,1,4,2,5,1,3): order J1 J2 J6 J4 J7 J3 J5, AWT = 36.43, ATAT = 53.57
 *
 * Words used in the table:
 *   AT = arrival time, RT = run (burst) time, PRI = priority (1 = highest), ST = start time, FT = finish time,
 *   WT = waiting time (TAT - RT), CT = completion time (= FT), TAT = turnaround (FT - AT)
 *
 * Layout follows the Figma frame "CPU Scheduling" (mini-os file, node 1:7).
 */
public class CpuModule extends FigmaModule {
    private static final long serialVersionUID = 1L;

    static final String[] ALGOS = {"FIFO", "SJF", "SRT", "Priority", "Priority (preemptive)", "Round Robin"};

    /**
     * Jobs as {AT, RT}; job J# is its position (1..n). Starts with 7 empty jobs for the person to fill in
     * (class example: AT 0,5,10,15,20,25,30 · RT 30,15,25,10,20,5,15 · quantum 10).
     */
    private final transient EntryGrid grid = new EntryGrid("J#", new String[]{"AT", "RT", "PRI"}, i -> "J" + (i + 1), 52, this::onRunPressed);
    { grid.resize(7); }
    private JTextField quantum;
    private String algoChoice = "FIFO";
    private String quantumText = "";

    /** Class example (CHECK values above): AT and RT of jobs 1..7, quantum 10. */
    static final String[] SAMPLE_AT = {"0", "5", "10", "15", "20", "25", "30"};
    static final String[] SAMPLE_RT = {"30", "15", "25", "10", "20", "5", "15"};
    static final String[] SAMPLE_PRI = {"3", "1", "4", "2", "5", "1", "3"};
    /** Boundary case: the CPU sits idle from 6 to 20 because no job has arrived yet. */
    static final String[] GAP_AT = {"0", "2", "20", "22"};
    static final String[] GAP_RT = {"4", "2", "5", "3"};
    static final String[] GAP_PRI = {"2", "1", "3", "1"};
    /** Exceptional case: a blank AT, a negative AT, a zero RT and a letter for a priority. */
    static final String[] BAD_AT = {"0", "", "-3", "8"};
    static final String[] BAD_RT = {"5", "4", "6", "0"};
    static final String[] BAD_PRI = {"1", "2", "x", "1"};

    /** Everything the person typed, for Undo and History. */
    private record State(List<String[]> jobs, String algo, String quantum) {}
    private final transient InputHistory<State> history = new InputHistory<>();

    @Override public String title()    { return "CPU Scheduling"; }
    @Override public String subtitle() { return "Compare FIFO, SJF, SRT, Priority, and Round Robin (quantum = 10 min)."; }
    @Override protected void onUndoPressed() { history.undo(this, snapshot(), this::restore); }
    @Override protected void onRedoPressed() { history.redo(this, snapshot(), this::restore); }

    @Override protected String crumb() { return "CPU"; }
    @Override protected String lead()  { return "Pick a policy, then read the job table, averages and Gantt chart."; }
    @Override protected int inputsTop() { return 4; }

    // ============================ INPUTS ============================

    @Override protected void buildInputs(JPanel p) {
        saveFields();   // keep what the user typed (theme change, add/remove job, new algorithm)
        quantum = null;

        JComponent jobBox = jobBox(), settings = settings();
        // job box (fit to content) + settings column (fills the rest); stacks when there is no room
        JPanel layout = new JPanel(null) {
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
        layout.setOpaque(false);
        layout.add(jobBox);
        layout.add(settings);
        layout.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { layout.revalidate(); }
        });
        p.add(layout, BorderLayout.CENTER);
        p.add(toolbar(), BorderLayout.SOUTH);
    }

    /** Sample / Idle-gap / Large / Random / Invalid input / History / Undo / Clear pills. */
    private JComponent toolbar() {
        DashedPill hist = new DashedPill("History");
        hist.addActionListener(e -> history.showMenu(hist, this::restore));
        return pillRow(12,
                pill("Sample data (7 jobs)", () -> load(SAMPLE_AT, SAMPLE_RT, SAMPLE_PRI, "10",
                        "Loaded the class example (7 jobs, q = 10)")),
                pill("Idle-gap sample", () -> load(GAP_AT, GAP_RT, GAP_PRI, "3",
                        "Loaded a case where the CPU is idle from 6 to 20")),
                pill("Large sample (20 jobs)", () -> loadRandom(20, new Random(7), "Loaded 20 jobs")),
                pill("Random", () -> loadRandom(grid.size(), new Random(), "Generated " + grid.size() + " random jobs")),
                pill("Invalid input test", () -> load(BAD_AT, BAD_RT, BAD_PRI, "0",
                        "Loaded bad input - the result card shows what to fix")),
                hist,
                pill("Undo", () -> onUndoPressed()),
                pill("Clear", () -> {
                    String[] blank = new String[grid.size()];
                    Arrays.fill(blank, "");
                    load(blank, blank, blank, "", "Cleared all jobs");
                }));
    }

    private State snapshot() {
        saveFields();
        return new State(grid.copy(), algoChoice, quantumText);
    }

    /** Puts a snapshot back on screen and runs it. */
    private void restore(State st) {
        grid.setRows(st.jobs());   // drops the old cells so they are not copied back over the new data
        algoChoice = st.algo();
        quantumText = st.quantum();
        quantum = null;
        rebuildInputs();
        markInputsDirty();
    }

    private void load(String[] at, String[] rt, String[] pri, String q, String toast) {
        history.push(snapshot());
        List<String[]> list = new ArrayList<>();
        for (int i = 0; i < at.length; i++) list.add(new String[]{at[i], rt[i], pri[i]});
        restore(new State(list, algoChoice, q));
        showToast(this, toast);
    }

    private void loadRandom(int n, Random rng, String toast) {
        saveFields();
        String[] at = new String[n], rt = new String[n], pri = new String[n];
        for (int i = 0; i < n; i++) {
            at[i] = String.valueOf(rng.nextInt(4 * n + 1));   // arrivals spread over 0 .. 4n minutes
            rt[i] = String.valueOf(1 + rng.nextInt(30));      // run time 1 .. 30 minutes
            pri[i] = String.valueOf(1 + rng.nextInt(5));      // priority 1 (highest) .. 5
        }
        load(at, rt, pri, quantum(quantumText) == null ? "10" : quantumText, toast);
    }

    /** +1 / +5 / −1 / Set: changes the number of jobs, keeping what was typed (with Undo). */
    private void resizeJobs(int n) {
        history.push(snapshot());
        grid.resize(n);
        rebuildInputs();
        showToast(this, grid.size() + (grid.size() == 1 ? " job" : " jobs"));
    }

    /** Paste / Bulk: replaces the jobs with the pasted lines (with Undo) and runs them. */
    private void bulkLoad(List<String[]> pasted) {
        history.push(snapshot());
        grid.setRows(pasted);
        rebuildInputs();
        markInputsDirty();
        showToast(this, "Imported " + pasted.size() + " jobs — press RUN SIMULATION");
    }

    /** "JOBS" box: boxes for up to 7 jobs, a scrolling table for 8+, and the +1 / +5 / −1 / Set / Paste row. */
    private JComponent jobBox() {
        return grid.box("JOBS", "job", "times in minutes",
                "PRI: 1 = highest, used by Priority",
                this::resizeJobs, this::bulkLoad);
    }

    /** Algorithm dropdown, plus the time quantum when Round Robin is picked. */
    private JComponent settings() {
        JPanel s = new JPanel();
        s.setOpaque(false);
        s.setLayout(new BoxLayout(s, BoxLayout.Y_AXIS));
        s.add(fieldLabel("Algorithm"));
        s.add(Box.createVerticalStrut(6));
        s.add(new Dropdown(algoChoice, ALGOS, pick -> { saveFields(); algoChoice = pick; rebuildInputs(); markInputsDirty(); }));
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
        grid.save();
    }

    private static JPanel hRow() {
        JPanel r = new JPanel();
        r.setOpaque(false);
        r.setLayout(new BoxLayout(r, BoxLayout.X_AXIS));
        r.setAlignmentX(LEFT_ALIGNMENT);
        return r;
    }

    // ============================ RESULTS ============================

    @Override protected void runSimulation(JPanel r) {
        saveFields();
        grid.clearInvalid();
        String algo = algoChoice;

        boolean rr = algo.equals("Round Robin"), prio = algo.startsWith("Priority");
        boolean nothingTyped = grid.rows().stream().allMatch(j -> j[0].isEmpty() && j[1].isEmpty() && j[2].isEmpty());
        if (nothingTyped) {
            r.add(heading(algo.toUpperCase(), null));
            r.add(Box.createVerticalStrut(10));
            r.add(Notice.prompt("Type the arrival time (AT) and run time (RT) of each job"
                    + (prio ? " and its priority (PRI)," : rr ? ", and the time quantum," : ",") + " then press RUN SIMULATION."));
            return;
        }

        List<Job> list;
        Integer q = quantum(quantumText);   // only needed for Round Robin
        try {
            list = readJobs(prio);
            if (rr && q == null) throw new NumberFormatException("the time quantum must be a whole number more than 0");
        } catch (NumberFormatException e) {
            r.add(heading(algo.toUpperCase(), null));
            r.add(Box.createVerticalStrut(10));
            r.add(new Notice(false, "Input check", e.getMessage()));
            r.add(Box.createVerticalStrut(6));
            r.add(body("Rule: every AT and RT is a whole number; AT is 0 or more and RT is more than 0"
                    + (prio ? "; every PRI is a whole number, 0 or more (1 = highest)." : "")
                    + (rr ? "; the time quantum is more than 0." : "."), 12, Theme.muted()));
            return;
        }

        Result res = schedule(algo, list, rr ? q : 1);
        history.record(String.format("%s · %d jobs · AWT %.3f", rr ? "RR q=" + q : algo, list.size(), res.awt), snapshot());

        // 1) heading + job table
        r.add(heading(rr ? "ROUND ROBIN · Q=" + q : algo.toUpperCase(),
                list.size() + " jobs · times in minutes" + (prio ? " · lower PRI runs first, ties by arrival" : "")));
        r.add(Box.createVerticalStrut(10));
        String[][] rows = new String[list.size()][];
        for (int i = 0; i < list.size(); i++) {
            Job j = list.get(i);
            int st = res.start.get(j.id), ft = res.finish.get(j.id);
            int tat = ft - j.at, wt = tat - j.rt;
            rows[i] = prio
                    ? new String[]{j.id, "" + j.at, "" + j.rt, "" + j.pri, "" + st, "" + ft, "" + wt, "" + ft, "" + tat}
                    : new String[]{j.id, "" + j.at, "" + j.rt, "" + st, "" + ft, "" + wt, "" + ft, "" + tat};
        }
        r.add(new DataTable(prio ? new String[]{"J#", "AT", "RT", "PRI", "ST", "FT", "WT", "CT", "TAT"}
                : new String[]{"J#", "AT", "RT", "ST", "FT", "WT", "CT", "TAT"}, rows, -1));
        r.add(Box.createVerticalStrut(10));

        // 2) averages
        JPanel avg = hRow();
        avg.add(body(String.format("AWT = %.3f", res.awt), 16, Theme.pink()));
        avg.add(Box.createHorizontalStrut(28));
        avg.add(body(String.format("ATAT = %.3f", res.atat), 16, Theme.dark ? Theme.cyan() : Theme.text()));
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
        r.add(Box.createVerticalStrut(6));
        r.add(body("Order: " + runOrder(res), 12, Theme.muted()));

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
            if (ALGOS[i].startsWith("Priority") && list.stream().anyMatch(j -> j.pri == null)) {   // PRI not filled in
                cmp[i] = new String[]{ALGOS[i] + " - fill in PRI", "—", "—"};
                continue;
            }
            Result x = schedule(ALGOS[i], list, q == null ? 1 : q);
            cmp[i] = new String[]{ALGOS[i].equals("Round Robin") ? "Round Robin (q=" + q + ")" : ALGOS[i],
                    String.format("%.3f", x.awt), String.format("%.3f", x.atat)};
        }
        r.add(new DataTable(new String[]{"Algorithm", "AWT", "ATAT"}, cmp, current));

        // 5) the decisions, in words
        r.add(explainSection(explain(algo, list, res, rr ? q : 1)));
    }

    /** One line per Gantt block: who was ready, which job was picked and why, and what happened. */
    static List<String> explain(String algo, List<Job> jobs, Result res, int q) {
        List<String> out = new ArrayList<>();
        Map<String, Job> byId = new HashMap<>();
        for (Job j : jobs) byId.put(j.id, j);
        Map<String, Integer> done = new HashMap<>();   // minutes each job has run so far
        out.add(switch (algo) {
            case "SJF" -> "Rule (SJF): when the CPU is free, run the waiting job with the shortest RT to the end; ties go to the earlier arrival.";
            case "SRT" -> "Rule (SRT): always run the job with the least time left; check again whenever a new job arrives.";
            case "Priority" -> "Rule (Priority, non-preemptive): when the CPU is free, run the waiting job with the lowest PRI number to the end; ties go to the earlier arrival.";
            case "Priority (preemptive)" -> "Rule (Priority, preemptive): always run the waiting job with the lowest PRI number; a newly arrived job with a lower PRI takes the CPU.";
            case "Round Robin" -> "Rule (Round Robin, q = " + q + "): jobs take turns in arrival order; each runs at most " + q + " min, then goes to the back of the line.";
            default -> "Rule (FIFO): run jobs to the end in the order they arrived.";
        });
        for (Segment s : res.segments) {
            if (s.id == null) {
                String next = jobs.stream().filter(j -> j.at >= s.to).min(Comparator.comparingInt(j -> j.at))
                        .map(j -> "J" + j.id).orElse("the next job");
                out.add(String.format("t=%d–%d: CPU idle - no job is waiting; %s arrives at %d.", s.from, s.to, next, s.to));
                continue;
            }
            Job j = byId.get(s.id);
            int ran = done.getOrDefault(s.id, 0), left = j.rt - ran, run = s.to - s.from;
            // jobs that were waiting at s.from (arrived, not finished)
            List<String> ready = new ArrayList<>();
            for (Job k : jobs) {
                int kRan = done.getOrDefault(k.id, 0);
                if (k.at <= s.from && kRan < k.rt) ready.add("J" + k.id + key(algo, k, k.rt - kRan));
            }
            String readyText = ready.size() > 8 ? String.join(", ", ready.subList(0, 8)) + ", ..." : String.join(", ", ready);
            String why = switch (algo) {
                case "SJF" -> "shortest RT = " + j.rt;
                case "SRT" -> "least time left = " + left;
                case "Priority", "Priority (preemptive)" -> "lowest PRI = " + j.pri;
                case "Round Robin" -> "its turn in the line";
                default -> "arrived first, AT " + j.at;
            };
            boolean finishes = run == left;
            String end = finishes ? "finishes at " + s.to
                    : algo.equals("Round Robin") ? "used its " + q + " min, " + (left - run) + " left - back of the line"
                    : "stopped at " + s.to + " by a new arrival, " + (left - run) + " left";
            out.add(String.format("t=%d: waiting %s → J%s (%s) runs %d–%d, %s.", s.from, readyText, j.id, why, s.from, s.to, end));
            done.put(s.id, ran + run);
        }
        StringBuilder wt = new StringBuilder();
        int sum = 0;
        for (Job j : jobs) {
            int w = res.finish.get(j.id) - j.at - j.rt;
            sum += w;
            if (wt.length() > 0) wt.append(" + ");
            wt.append(w);
        }
        out.add(String.format("WT of each job = CT − AT − RT;  AWT = (%s) ÷ %d = %.2f.",
                jobs.size() > 12 ? "sum of all WT = " + sum : wt.toString(), jobs.size(), sum / (double) jobs.size()));
        out.add(String.format("TAT of each job = CT − AT;  ATAT = AWT + average RT = %.2f.", res.atat));
        return out;
    }

    /** The number the rule compares, shown next to each waiting job. */
    private static String key(String algo, Job k, int left) {
        return switch (algo) {
            case "SJF" -> " (RT " + k.rt + ")";
            case "SRT" -> " (left " + left + ")";
            case "Priority", "Priority (preemptive)" -> " (PRI " + k.pri + ")";
            case "Round Robin" -> "";
            default -> " (AT " + k.at + ")";
        };
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

    /** Reads every job; PRI is only required when needPri (the Priority algorithm). */
    private List<Job> readJobs(boolean needPri) {
        List<Job> list = new ArrayList<>();
        List<String[]> rows = grid.rows();
        for (int i = 0; i < rows.size(); i++) {
            int n = i + 1, at, rt;
            Integer pri = null;
            try {
                at = whole(rows.get(i)[0], "AT of job " + n);
                if (at < 0) throw new NumberFormatException("job " + n + ": AT " + at + " is negative - arrival time must be 0 or more");
            } catch (NumberFormatException e) { grid.markInvalid(i, 0); throw e; }
            try {
                rt = whole(rows.get(i)[1], "RT of job " + n);
                if (rt <= 0) throw new NumberFormatException("job " + n + ": RT " + rt + " - run time must be more than 0");
            } catch (NumberFormatException e) { grid.markInvalid(i, 1); throw e; }
            String p = rows.get(i)[2];
            if (needPri || !p.isBlank()) {
                try {
                    pri = whole(p, "PRI of job " + n);
                    if (pri < 0) throw new NumberFormatException("job " + n + ": PRI " + pri + " is negative - priority must be 0 or more");
                } catch (NumberFormatException e) {
                    if (needPri) { grid.markInvalid(i, 2); throw e; }
                    pri = null;   // a bad PRI only matters when Priority is picked
                }
            }
            list.add(new Job(String.valueOf(n), at, rt, pri, i));
        }
        return list;
    }

    /** The order jobs first get the CPU, e.g. "J1 → J2 → J6"; repeats show up for SRT / Round Robin. */
    private static String runOrder(Result res) {
        StringBuilder sb = new StringBuilder();
        for (Segment s : res.segments) {
            if (s.id == null) continue;
            if (sb.length() > 0) sb.append(" → ");
            sb.append('J').append(s.id);
        }
        return sb.toString();
    }

    /** Parses a whole number, or throws with a message that names the field. */
    private static int whole(String s, String what) {
        if (s.isBlank()) throw new NumberFormatException(what + " is blank");
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { throw new NumberFormatException(what + " (\"" + s + "\") is not a whole number"); }
    }

    // ============================ ALGORITHMS ============================

    static class Job {
        final String id; final int at, rt, order; final Integer pri;   // pri is null when not given
        Job(String id, int at, int rt, Integer pri, int order) { this.id = id; this.at = at; this.rt = rt; this.pri = pri; this.order = order; }
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
            case "Priority (preemptive)" -> preemptivePriority(jobs);
            // Priority (non-preemptive): of the jobs that have arrived, run the lowest PRI number to the end
            case "Priority" -> nonPreemptive(jobs, Comparator.<Job>comparingInt(j -> j.pri).thenComparingInt(j -> j.at).thenComparingInt(j -> j.order));
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

    /**
     * Priority (preemptive): always run the waiting job with the lowest PRI number; re-check whenever a
     * new job arrives, so a more important arrival takes the CPU. Ties: earlier arrival, then job number.
     */
    static Result preemptivePriority(List<Job> jobs) {
        Result r = new Result();
        Map<String, Integer> rem = new HashMap<>();
        for (Job j : jobs) rem.put(j.id, j.rt);
        int t = 0;
        while (r.finish.size() < jobs.size()) {
            final int now = t;
            Optional<Job> next = jobs.stream().filter(j -> j.at <= now && !r.finish.containsKey(j.id))
                    .min(Comparator.<Job>comparingInt(j -> j.pri).thenComparingInt(j -> j.at).thenComparingInt(j -> j.order));
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
