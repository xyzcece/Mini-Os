package modules.multiprogramming;

import shared.ModulePanel;
import shared.Theme;

import javax.swing.*;

/**
 * MULTIPROGRAMMING  —  OWNER: Member 2
 * TODO:
 *   [ ] Jobs (ID, arrival, CPU min), I/O wait %, start time
 *   [ ] Job table: Time, Event, Nj, %cpu/j, ET, HW/J, J#, TIMELEFT
 *   [ ] %cpu/j = (1 - 0.8^n) / n
 * CHECK: Last job done at 10:31.6
 *
 * Tip: copy the pattern in modules/paging/PagingModule.java
 */
public class MultiprogrammingModule extends ModulePanel {

    @Override public String title()    { return "Multiprogramming"; }
    @Override public String subtitle() { return "Share the CPU between jobs and track the time each job has left."; }

    @Override protected void buildInputs(JPanel p) {
        // TODO: add your inputs, e.g.  JTextField f = inputRow(p, "Label", "default value");
    }

    @Override protected void runSimulation(JPanel r) {
        r.add(kicker("Simulation output · Multiprogramming"));
        r.add(text("Coming soon — Member 2 is building this module.", 14, Theme.muted()));
        // TODO: read inputs, compute, then add results:  r.add(table(headers, rows));
    }
}
