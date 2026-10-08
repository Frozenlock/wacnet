package wacnet;

import java.awt.GraphicsEnvironment;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

import clojure.java.api.Clojure;
import clojure.lang.IFn;
import clojure.lang.RT;

public class Launcher {

    private static JWindow splashWindow;

    public static void main(String[] args) {
        // Handle --help / -h before loading Clojure (instant response)
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg)) {
                printHelp();
                System.exit(0);
            }
        }

        // Check if headless mode is requested
        boolean headless = false;
        for (String arg : args) {
            if ("--headless".equals(arg)) {
                headless = true;
                break;
            }
        }

        // Show splash screen if not headless and display is available
        if (!headless && !GraphicsEnvironment.isHeadless()) {
            showSplash();
        }

        // Load Clojure runtime and call wacnet.server/-main (the slow part)
        IFn require = Clojure.var("clojure.core", "require");
        require.invoke(Clojure.read("wacnet.server"));
        IFn main = Clojure.var("wacnet.server", "-main");
        main.applyTo(RT.seq(args));
    }

    private static void printHelp() {
        try (InputStream is = Launcher.class.getClassLoader().getResourceAsStream("help.txt")) {
            if (is != null) {
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int n;
                while ((n = is.read(chunk)) != -1) {
                    buf.write(chunk, 0, n);
                }
                System.out.println(new String(buf.toByteArray(), StandardCharsets.UTF_8));
                return;
            }
        } catch (Exception e) {
            // Fall through to fallback
        }
        System.out.println("Wacnet - BACnet webserver and toolkit\n\nRun with --help for usage information.");
    }

    private static void showSplash() {
        try {
            URL imageUrl = Launcher.class.getClassLoader().getResource("public/img/splash.png");
            if (imageUrl == null) return;

            SwingUtilities.invokeAndWait(() -> {
                ImageIcon icon = new ImageIcon(imageUrl);
                splashWindow = new JWindow();
                splashWindow.getContentPane().add(new JLabel(icon));
                splashWindow.pack();
                splashWindow.setLocationRelativeTo(null);
                splashWindow.setAlwaysOnTop(true);
                splashWindow.setVisible(true);
            });
        } catch (Exception e) {
            // Splash is non-essential — silently ignore
        }
    }

    public static void closeSplash() {
        if (splashWindow != null) {
            SwingUtilities.invokeLater(() -> {
                splashWindow.dispose();
                splashWindow = null;
            });
        }
    }
}
