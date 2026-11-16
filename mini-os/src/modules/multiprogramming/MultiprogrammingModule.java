package modules.multiprogramming;

import java.awt.BorderLayout;
import javax.swing.*;
import modules.common.FigmaModule;
import shared.Theme;

/**
 * MULTIPROGRAMMING  —  OWNER: Member 2
 * TODO:
 *   [ ] Jobs (ID, arrival, CPU min), I/O wait %, start time
 *   [ ] Job table: Time, Event, Nj, %cpu/j, ET, HW/J, J#, TIMELEFT
 *   [ ] %cpu/j = (1 - 0.8^n) / n
 * CHECK: Last job done at 10:31.6
 *
 * The page design (header, inputs, run button, result card) comes from FigmaModule.
 * Tip: see modules/paging/PagingModule.java for a finished module on the same design.
 */
public class MultiprogrammingModule extends FigmaModule {
    private static final long serialVersionUID = 1L;
    private JTextField jobs, ioWait, start;

    @Override public String title()    { return "Multiprogramming"; }
    @Override public String subtitle() { return "Share the CPU between jobs and track the time each job has left."; }
    @Override protected String crumb() { return "MULTIPROGRAMMING"; }
    @Override protected String lead()  { return "Follow each job as the CPU is shared and see when it finishes."; }

    @Override protected void buildInputs(JPanel p) {
        jobs   = new RoundField(jobs   == null ? "" : jobs.getText());
        ioWait = new RoundField(ioWait == null ? "" : ioWait.getText());
        start  = new RoundField(start  == null ? "" : start.getText());
        p.add(columns(new double[]{2.4, 1, 1},
                labeled("Jobs (ID, arrival, CPU min)", jobs, "one job per ;"),
                labeled("I/O wait %", ioWait, null),
                labeled("Start time", start, null)), BorderLayout.CENTER);
    }

    @Override protected void runSimulation(JPanel r) {
        r.add(pixelText("SIMULATION OUTPUT · MULTIPROGRAMMING", 7f, Theme.dark ? Theme.cyan() : Theme.text(), 0f));
        r.add(Box.createVerticalStrut(12));
        r.add(Notice.prompt("Coming soon — Member 2 is building this module."));
        // TODO: read inputs, compute, then add results (job table, time left per job).
    }
}
