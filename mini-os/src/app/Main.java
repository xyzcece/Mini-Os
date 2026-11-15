package app;

import javax.swing.SwingUtilities;

/** Run this file to start Mini OS. Run it from the project root folder (so /assets and /fonts are found). */
public class Main {
    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        SwingUtilities.invokeLater(() -> new MiniOS().setVisible(true));
    }
}
