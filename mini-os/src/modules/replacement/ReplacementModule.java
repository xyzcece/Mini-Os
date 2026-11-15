package modules.replacement;

import shared.ModulePanel;
import shared.Theme;

import javax.swing.*;

/**
 * PAGE REPLACEMENT  —  OWNER: Member 2
 * TODO:
 *   [ ] Reference string, frame count, algorithm dropdown
 *   [ ] Frame grid (one column per page) + H/F row
 *   [ ] #FAULT and #HIT
 * CHECK: FIFO, 3 frames, 7 0 1 2 0 3 0 4 2 3 0 3 2 -> 10 faults, 3 hits
 *
 * Tip: copy the pattern in modules/paging/PagingModule.java
 */
public class ReplacementModule extends ModulePanel {

    @Override public String title()    { return "Page Replacement"; }
    @Override public String subtitle() { return "Visualize FIFO, LRU, and Optimal replacement."; }

    @Override protected void buildInputs(JPanel p) {
        // TODO: add your inputs, e.g.  JTextField f = inputRow(p, "Label", "default value");
    }

    @Override protected void runSimulation(JPanel r) {
        r.add(kicker("Simulation output · Page Replacement"));
        r.add(text("Coming soon — Member 2 is building this module.", 14, Theme.muted()));
        // TODO: read inputs, compute, then add results:  r.add(table(headers, rows));
    }
}
