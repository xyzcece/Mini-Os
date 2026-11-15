package modules.banker;

import shared.ModulePanel;
import shared.Theme;

import javax.swing.*;

/**
 * BANKER'S ALGORITHM  —  OWNER: Member 3
 * TODO:
 *   [ ] Available (R1-R4), Allocation and Maximum input grids
 *   [ ] Need = Maximum - Allocation
 *   [ ] Available after each step + SAFE / NOT SAFE + safe sequence
 * CHECK: Safe sequence: P3, P4, P5, P1, P2
 *
 * Tip: copy the pattern in modules/paging/PagingModule.java
 */
public class BankerModule extends ModulePanel {

    @Override public String title()    { return "Banker's Algorithm"; }
    @Override public String subtitle() { return "Calculate Need and discover a safe sequence."; }

    @Override protected void buildInputs(JPanel p) {
        // TODO: add your inputs, e.g.  JTextField f = inputRow(p, "Label", "default value");
    }

    @Override protected void runSimulation(JPanel r) {
        r.add(kicker("Simulation output · Banker's Algorithm"));
        r.add(text("Coming soon — Member 3 is building this module.", 14, Theme.muted()));
        // TODO: read inputs, compute, then add results:  r.add(table(headers, rows));
    }
}
