package modules.fragmentation;

import java.awt.BorderLayout;
import javax.swing.*;
import modules.common.FigmaModule;
import shared.Theme;

/**
 * FRAGMENTATION  —  OWNER: Member 3
 * TODO:
 *   [ ] Memory blocks, process sizes, policy dropdown
 *   [ ] First / Best / Worst Fit
 *   [ ] Memory map + where each process went + free space
 * CHECK: First Fit: 959 KB free, P3 not allocated
 *
 * The page design (header, inputs, run button, result card) comes from FigmaModule.
 * Tip: see modules/paging/PagingModule.java for a finished module on the same design.
 */
public class FragmentationModule extends FigmaModule {
    private static final long serialVersionUID = 1L;
    static final String[] POLICIES = {"First Fit", "Best Fit", "Worst Fit"};
    private JTextField blocks, processes;
    private String policy = "First Fit";

    @Override public String title()    { return "Fragmentation"; }
    @Override public String subtitle() { return "Explore first, best, and worst fit allocation."; }
    @Override protected String crumb() { return "FRAGMENTATION"; }
    @Override protected String lead()  { return "Place each process in a memory block and see how much space is left."; }

    @Override protected void buildInputs(JPanel p) {
        blocks    = new RoundField(blocks    == null ? "" : blocks.getText());
        processes = new RoundField(processes == null ? "" : processes.getText());
        Dropdown pick = new Dropdown(policy, POLICIES, a -> { policy = a; rebuildInputs(); });
        p.add(columns(new double[]{1.6, 1.6, 1.2},
                labeled("Memory blocks (KB)", blocks, "sizes separated by spaces"),
                labeled("Process sizes (KB)", processes, "sizes separated by spaces"),
                labeled("Policy", pick, null)), BorderLayout.CENTER);
    }

    @Override protected void runSimulation(JPanel r) {
        r.add(pixelText("SIMULATION OUTPUT · FRAGMENTATION", 7f, Theme.dark ? Theme.cyan() : Theme.text(), 0f));
        r.add(Box.createVerticalStrut(12));
        r.add(Notice.prompt("Coming soon — Member 3 is building this module."));
        // TODO: read inputs, compute, then add results (memory map, placements, free space).
    }
}
