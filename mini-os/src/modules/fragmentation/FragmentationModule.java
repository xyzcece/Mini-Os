package modules.fragmentation;

import shared.ModulePanel;
import shared.Theme;

import javax.swing.*;

/**
 * FRAGMENTATION  —  OWNER: Member 3
 * TODO:
 *   [ ] Memory blocks, process sizes, policy dropdown
 *   [ ] First / Best / Worst Fit
 *   [ ] Memory map + where each process went + free space
 * CHECK: First Fit: 959 KB free, P3 not allocated
 *
 * Tip: copy the pattern in modules/paging/PagingModule.java
 */
public class FragmentationModule extends ModulePanel {

    @Override public String title()    { return "Fragmentation"; }
    @Override public String subtitle() { return "Explore first, best, and worst fit allocation."; }

    @Override protected void buildInputs(JPanel p) {
        // TODO: add your inputs, e.g.  JTextField f = inputRow(p, "Label", "default value");
    }

    @Override protected void runSimulation(JPanel r) {
        r.add(kicker("Simulation output · Fragmentation"));
        r.add(text("Coming soon — Member 3 is building this module.", 14, Theme.muted()));
        // TODO: read inputs, compute, then add results:  r.add(table(headers, rows));
    }
}
