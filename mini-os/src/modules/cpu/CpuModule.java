package modules.cpu;

import shared.ModulePanel;
import shared.Theme;

import javax.swing.*;

/**
 * CPU SCHEDULING  —  OWNER: Member 1
 * TODO:
 *   [ ] J#/AT/RT job input table (7 jobs) + Add job
 *   [ ] Algorithm dropdown: FIFO, SJF, SRT, Round Robin + time quantum
 *   [ ] Job table: J#, AT, RT, ST, FT, WT, CT, TAT
 *   [ ] AWT and ATAT + Gantt chart
 * CHECK: FIFO (7 sample jobs): AWT = 46.43, ATAT = 63.57
 *
 * Tip: copy the pattern in modules/paging/PagingModule.java
 */
public class CpuModule extends ModulePanel {

    @Override public String title()    { return "CPU Scheduling"; }
    @Override public String subtitle() { return "Compare FIFO, SJF, SRT, and Round Robin (quantum = 10 min)."; }

    @Override protected void buildInputs(JPanel p) {
        // TODO: add your inputs, e.g.  JTextField f = inputRow(p, "Label", "default value");
    }

    @Override protected void runSimulation(JPanel r) {
        r.add(kicker("Simulation output · CPU Scheduling"));
        r.add(text("Coming soon — Member 1 is building this module.", 14, Theme.muted()));
        // TODO: read inputs, compute, then add results:  r.add(table(headers, rows));
    }
}
