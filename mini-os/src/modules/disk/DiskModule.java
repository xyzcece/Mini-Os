package modules.disk;

import shared.ModulePanel;
import shared.Theme;

import javax.swing.*;

/**
 * DISK SCHEDULING  —  OWNER: Member 2
 * TODO:
 *   [ ] Requests, initial head, disk max, algorithm dropdown
 *   [ ] FCFS, SSTF, SCAN, C-SCAN, LOOK, C-LOOK
 *   [ ] Seek table (next track, traverse) + total cylinders + ave seek
 *   [ ] Seek-path graph
 * CHECK: SSTF (head 53): 236 cylinders
 *
 * Tip: copy the pattern in modules/paging/PagingModule.java
 */
public class DiskModule extends ModulePanel {

    @Override public String title()    { return "Disk Scheduling"; }
    @Override public String subtitle() { return "Compare seek order and total head movement."; }

    @Override protected void buildInputs(JPanel p) {
        // TODO: add your inputs, e.g.  JTextField f = inputRow(p, "Label", "default value");
    }

    @Override protected void runSimulation(JPanel r) {
        r.add(kicker("Simulation output · Disk Scheduling"));
        r.add(text("Coming soon — Member 2 is building this module.", 14, Theme.muted()));
        // TODO: read inputs, compute, then add results:  r.add(table(headers, rows));
    }
}
