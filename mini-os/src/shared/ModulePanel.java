package shared;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;

/**
 * Base class for every module so all 8 screens look the same:
 *   title -> subtitle -> inputs -> RUN SIMULATION -> results panel
 *
 * HOW TO USE (Members 1, 2, 3):
 *   1. extend ModulePanel
 *   2. buildInputs(JPanel inputs)   -> add your fields with inputRow(...)
 *   3. runSimulation(JPanel results) -> read inputs, compute, add tables/charts
 * See modules/paging/PagingModule.java for a finished example.
 * OWNER: Member 3 (ask before changing this file).
 */
public abstract class ModulePanel extends JPanel {
    protected final JPanel inputs = new JPanel();
    protected final JPanel results = new JPanel();

    public abstract String title();
    public abstract String subtitle();
    protected abstract void buildInputs(JPanel inputs);
    protected abstract void runSimulation(JPanel results);

    protected ModulePanel() {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(20, 28, 20, 28));
        SwingUtilities.invokeLater(this::rebuild);
        Theme.onChange(this::rebuild);
    }

    /** Rebuilds the screen (called on start and when the theme changes). */
    public void rebuild() {
        removeAll();
        JLabel hype = new JLabel("HYPERPUFF", SwingConstants.CENTER);
        hype.setFont(Fonts.arcade(8f)); hype.setForeground(Theme.pink()); hype.setAlignmentX(LEFT_ALIGNMENT);
        JLabel t = new JLabel(title());
        t.setFont(Fonts.title(48f)); t.setForeground(Theme.green()); t.setAlignmentX(LEFT_ALIGNMENT);
        JLabel st = new JLabel(subtitle());
        st.setFont(Fonts.body(13f)); st.setForeground(Theme.muted()); st.setAlignmentX(LEFT_ALIGNMENT);
        add(hype); add(t); add(st); add(Box.createVerticalStrut(16));

        inputs.removeAll(); inputs.setOpaque(false);
        inputs.setLayout(new BoxLayout(inputs, BoxLayout.Y_AXIS)); inputs.setAlignmentX(LEFT_ALIGNMENT);
        buildInputs(inputs);
        add(inputs); add(Box.createVerticalStrut(12));

        JButton run = runButton();
        run.addActionListener(e -> { results.removeAll(); runSimulation(results); results.revalidate(); results.repaint(); });
        add(run); add(Box.createVerticalStrut(20));

        results.removeAll(); results.setAlignmentX(LEFT_ALIGNMENT);
        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
        results.setBackground(Theme.panel2());
        results.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.line(), 2, true), new EmptyBorder(14, 14, 14, 14)));
        runSimulation(results);   // show results right away (like the Figma prototype)
        add(results);
        revalidate(); repaint();
    }

    // ---------- shared helpers ----------

    /** Label + text box. Returns the text field so you can read it in runSimulation. */
    protected JTextField inputRow(JPanel parent, String label, String value) {
        JLabel l = new JLabel(label.toUpperCase());
        l.setFont(Fonts.label(10f)); l.setForeground(Theme.text()); l.setAlignmentX(LEFT_ALIGNMENT);
        JTextField f = textBox(value);
        f.setAlignmentX(LEFT_ALIGNMENT);
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        parent.add(l); parent.add(Box.createVerticalStrut(4)); parent.add(f); parent.add(Box.createVerticalStrut(10));
        return f;
    }

    protected JTextField textBox(String value) {
        JTextField f = new JTextField(value);
        f.setFont(Fonts.body(15f)); f.setForeground(Theme.text()); f.setBackground(Theme.input());
        f.setCaretColor(Theme.text());
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.inputLine(), 2, true), new EmptyBorder(6, 10, 6, 10)));
        return f;
    }

    protected <T> JComboBox<T> dropdown(JPanel parent, String label, T[] options) {
        JLabel l = new JLabel(label.toUpperCase());
        l.setFont(Fonts.label(10f)); l.setForeground(Theme.text()); l.setAlignmentX(LEFT_ALIGNMENT);
        JComboBox<T> box = new JComboBox<>(options);
        box.setFont(Fonts.body(15f)); box.setAlignmentX(LEFT_ALIGNMENT);
        box.setMaximumSize(new Dimension(320, 38));
        parent.add(l); parent.add(Box.createVerticalStrut(4)); parent.add(box); parent.add(Box.createVerticalStrut(10));
        return box;
    }

    protected JButton runButton() {
        JButton b = new JButton("RUN SIMULATION  >") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x8e1556)); g2.fillRoundRect(0, 4, getWidth(), getHeight() - 4, 40, 40);
                g2.setPaint(new GradientPaint(0, 0, new Color(0xff9ccf), 0, getHeight(), new Color(0xd82b85)));
                g2.fillRoundRect(0, 0, getWidth(), getHeight() - 4, 40, 40);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setFont(Fonts.arcade(9f)); b.setForeground(Color.WHITE);
        b.setContentAreaFilled(false); b.setBorderPainted(false); b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(14, 22, 18, 22)); b.setAlignmentX(LEFT_ALIGNMENT);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** Small pixel-font heading inside the results panel. */
    protected JLabel kicker(String s) {
        JLabel l = new JLabel(s.toUpperCase());
        l.setFont(Fonts.arcade(8f)); l.setForeground(Theme.dark ? Theme.cyan() : Theme.text());
        l.setAlignmentX(LEFT_ALIGNMENT); l.setBorder(new EmptyBorder(0, 0, 8, 0));
        return l;
    }

    protected JLabel text(String s, float size, Color c) {
        JLabel l = new JLabel(s); l.setFont(Fonts.body(size)); l.setForeground(c); l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    /** Styled table in the Mini OS look. */
    protected JScrollPane table(String[] headers, Object[][] rows) {
        JTable t = new JTable(rows, headers) { @Override public boolean isCellEditable(int r, int c) { return false; } };
        t.setFont(Fonts.body(14f)); t.setRowHeight(28); t.setForeground(Theme.text());
        t.setGridColor(Theme.line()); t.setShowGrid(true); t.setBackground(Theme.panel());
        DefaultTableCellRenderer center = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable tb, Object v, boolean s, boolean f, int r, int c) {
                Component comp = super.getTableCellRendererComponent(tb, v, false, false, r, c);
                comp.setBackground(r % 2 == 0 ? Theme.panel() : Theme.panel2());
                return comp;
            }
        };
        center.setHorizontalAlignment(SwingConstants.CENTER);
        t.setDefaultRenderer(Object.class, center);
        JTableHeader h = t.getTableHeader();
        h.setFont(Fonts.label(10f)); h.setBackground(Theme.panel2()); h.setForeground(Theme.muted());
        JScrollPane sp = new JScrollPane(t);
        sp.setAlignmentX(LEFT_ALIGNMENT);
        sp.setBorder(BorderFactory.createLineBorder(Theme.line(), 1));
        sp.setPreferredSize(new Dimension(700, 30 + rows.length * 28));
        sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30 + rows.length * 28));
        return sp;
    }
}
