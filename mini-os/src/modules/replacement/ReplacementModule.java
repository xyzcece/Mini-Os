package modules.replacement;

import java.awt.BorderLayout;
import javax.swing.*;
import modules.common.FigmaModule;
import shared.Theme;

/**
 * PAGE REPLACEMENT  —  OWNER: Member 2
 * TODO:
 *   [ ] Reference string, frame count, algorithm dropdown
 *   [ ] Frame grid (one column per page) + H/F row
 *   [ ] #FAULT and #HIT
 * CHECK: FIFO, 3 frames, 7 0 1 2 0 3 0 4 2 3 0 3 2 -> 10 faults, 3 hits
 *
 * The page design (header, inputs, run button, result card) comes from FigmaModule.
 * Tip: see modules/paging/PagingModule.java for a finished module on the same design.
 */
public class ReplacementModule extends FigmaModule {
    private static final long serialVersionUID = 1L;
    static final String[] ALGOS = {"FIFO", "LRU", "Optimal"};
    private JTextField reference, frames;
    private String algo = "FIFO";

    @Override public String title()    { return "Page Replacement"; }
    @Override public String subtitle() { return "Visualize FIFO, LRU, and Optimal replacement."; }
    @Override protected String crumb() { return "REPLACEMENT"; }
    @Override protected String lead()  { return "Trace the page faults and hits for a reference string."; }

    @Override protected void buildInputs(JPanel p) {
        reference = new RoundField(reference == null ? "" : reference.getText());
        frames    = new RoundField(frames    == null ? "" : frames.getText());
        Dropdown pick = new Dropdown(algo, ALGOS, a -> { algo = a; rebuildInputs(); });
        p.add(columns(new double[]{2.4, 1, 1.2},
                labeled("Reference string", reference, "pages separated by spaces"),
                labeled("Frames", frames, null),
                labeled("Algorithm", pick, null)), BorderLayout.CENTER);
    }

    @Override protected void runSimulation(JPanel r) {
        r.add(pixelText("SIMULATION OUTPUT · PAGE REPLACEMENT", 7f, Theme.dark ? Theme.cyan() : Theme.text(), 0f));
        r.add(Box.createVerticalStrut(12));
        r.add(Notice.prompt("Coming soon — Member 2 is building this module."));
        // TODO: read inputs, compute, then add results (frame grid, #FAULT / #HIT).
    }
}
