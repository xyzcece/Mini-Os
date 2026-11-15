package modules.banker;

import java.awt.BorderLayout;
import javax.swing.*;
import modules.common.FigmaModule;
import shared.Theme;

/**
 * BANKER'S ALGORITHM  —  OWNER: Member 3
 * TODO:
 *   [ ] Available (R1-R4), Allocation and Maximum input grids
 *   [ ] Need = Maximum - Allocation
 *   [ ] Available after each step + SAFE / NOT SAFE + safe sequence
 * CHECK: Safe sequence: P3, P4, P5, P1, P2
 *
 * The page design (header, inputs, run button, result card) comes from FigmaModule.
 * Tip: see modules/paging/PagingModule.java for a finished module on the same design.
 */
public class BankerModule extends FigmaModule {
    private static final long serialVersionUID = 1L;
    private JTextField available, allocation, maximum;

    @Override public String title()    { return "Banker's Algorithm"; }
    @Override public String subtitle() { return "Calculate Need and discover a safe sequence."; }
    @Override protected String crumb() { return "BANKER"; }
    @Override protected String lead()  { return "Check whether the system is safe and find a safe sequence."; }

    @Override protected void buildInputs(JPanel p) {
        available  = new RoundField(available  == null ? "" : available.getText());
        allocation = new RoundField(allocation == null ? "" : allocation.getText());
        maximum    = new RoundField(maximum    == null ? "" : maximum.getText());
        p.add(columns(new double[]{1, 1.6, 1.6},
                labeled("Available (R1-R4)", available, "e.g. 3 3 2 1"),
                labeled("Allocation", allocation, "one process per ;  e.g. 0 1 0 0; 2 0 0 1"),
                labeled("Maximum", maximum, "same order as Allocation")), BorderLayout.CENTER);
    }

    @Override protected void runSimulation(JPanel r) {
        r.add(pixelText("SIMULATION OUTPUT · BANKER'S ALGORITHM", 7f, Theme.dark ? Theme.cyan() : Theme.text(), 0f));
        r.add(Box.createVerticalStrut(12));
        r.add(Notice.prompt("Coming soon — Member 3 is building this module."));
        // TODO: read inputs, compute, then add results, e.g.  r.add(new Notice(true, "SAFE", "P3, P4, P5, P1, P2"));
    }
}
