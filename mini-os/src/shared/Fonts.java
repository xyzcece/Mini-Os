package shared;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Loads bundled fonts and image resources from the JAR classpath or local folders.
 */
public final class Fonts {
    private Fonts() {
    }

    private static final Map<String, Font> cache = new HashMap<>();
    /** Global UI font scale for projector zoom (0.85 to 1.35). */
    public static float scale = 1.0f;

    /** Resolves a local file from the working directory, mini-os/, or alongside the JAR. */
    public static File file(String path) {
        File f = new File(path);
        if (f.exists())
            return f;
        File nested = new File("mini-os/" + path);
        if (nested.exists())
            return nested;
        try {
            File loc = new File(Fonts.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File base = loc.isFile() ? loc.getParentFile() : loc;
            if (base != null) {
                File sibling = new File(base, path);
                if (sibling.exists())
                    return sibling;
            }
        } catch (Exception ignored) {
        }
        return f;
    }

    /** Opens a resource stream from inside the JAR first, then falls back to the filesystem. */
    public static InputStream stream(String path) {
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        InputStream in = Fonts.class.getResourceAsStream("/" + normalized);
        if (in != null)
            return in;
        File f = file(normalized);
        if (f.exists()) {
            try {
                return new FileInputStream(f);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /** Loads a BufferedImage from the JAR classpath or filesystem, or null if not found. */
    public static BufferedImage image(String path) {
        try (InputStream in = stream(path)) {
            return in == null ? null : ImageIO.read(in);
        } catch (Exception e) {
            return null;
        }
    }

    private static Font load(String file) {
        return cache.computeIfAbsent(file, f -> {
            try (InputStream in = stream("fonts/" + f)) {
                if (in == null)
                    throw new java.io.FileNotFoundException(f);
                Font font = Font.createFont(Font.TRUETYPE_FONT, in);
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
                return font;
            } catch (Exception e) {
                System.err.println("Font missing: " + f + " (using SansSerif)");
                return new Font("SansSerif", Font.PLAIN, 12);
            }
        });
    }

    /** Brush-script module titles. */
    public static Font title(float size) {
        return load("Yellowtail-Regular.ttf").deriveFont(size * scale);
    }

    /** Small arcade labels, RUN SIMULATION, HYPERPUFF. */
    public static Font arcade(float size) {
        return load("PressStart2P-Regular.ttf").deriveFont(size * scale);
    }

    /** Table headers and field labels. */
    public static Font label(float size) {
        return load("Silkscreen-Regular.ttf").deriveFont(size * scale);
    }

    /** Body text, inputs, results, numbers. */
    public static Font body(float size) {
        return load("DotGothic16-Regular.ttf").deriveFont(size * scale);
    }

    /** "OS Lab" and sidebar module names. */
    public static Font pixel(float size) {
        return load("PixelifySans-Bold.ttf").deriveFont(size * scale);
    }

    /** Clean bold UI text. */
    public static Font ui(float size) {
        return load("Nunito-VariableFont_wght.ttf").deriveFont(Font.BOLD, size * scale);
    }

    /** Sidebar module names (Nunito Bold). */
    public static Font uiBold(float size) {
        return load("Nunito-Bold.ttf").deriveFont(size * scale);
    }

    /** The open module in the sidebar (Nunito ExtraBold). */
    public static Font uiExtraBold(float size) {
        return load("Nunito-ExtraBold.ttf").deriveFont(size * scale);
    }
}
