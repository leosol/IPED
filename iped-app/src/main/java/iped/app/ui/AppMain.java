package iped.app.ui;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.app.config.LogConfiguration;
import iped.app.ui.utils.UiScale;
import iped.engine.Version;
import iped.engine.config.Configuration;
import iped.engine.core.Manager;
import iped.engine.util.Util;
import iped.utils.IOUtil;

public class AppMain {

    private static final String appLogFileName = "IPED-SearchApp.log"; //$NON-NLS-1$

    File casePath;

    // configure to debug the analysis UI with some case
    File testPath;// = new File("E:\\teste\\case-to-debug");

    boolean isMultiCase = false;
    boolean nolog = false;
    File casesPathFile = null;
    File libDir;

    public static void main(String[] args) {
        // Set the UiScale (must be before any UI-related code).
        UiScale.loadUserSetting();

        checkJavaVersion();
        AppMain appMain = new AppMain();
        try {
            appMain.detectCasePath();
            appMain.start(args);

        } catch (Exception e) {
            e.printStackTrace();
            showError(e);
        }
    }

    private static void checkJavaVersion() {
        try {
            if (System.getProperty("iped.javaVersionChecked") != null) //$NON-NLS-1$
                return;

            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    String warn = Util.getJavaVersionWarn();
                    if (warn != null) {
                        JOptionPane.showMessageDialog(null, warn, Messages.getString("AppMain.warn.Title"), //$NON-NLS-1$
                                JOptionPane.WARNING_MESSAGE);
                    }
                    Messages.resetLocale();
                }
            });
            System.setProperty("iped.javaVersionChecked", "true"); //$NON-NLS-1$

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void start(String[] args) {
        start(casePath, null, args);
    }

    private void detectCasePath() throws URISyntaxException {
        if (testPath != null) {
            casePath = testPath;
            libDir = new File(casePath + "/iped/lib");
            return;
        }

        libDir = detectLibDir();
        casePath = libDir.getParentFile().getParentFile();

        if (!new File(casePath, "iped").exists()) //$NON-NLS-1$
            casePath = null;
    }

    private File detectLibDir() throws URISyntaxException {
        URL url = AppMain.class.getProtectionDomain().getCodeSource().getLocation();
        File jarFile = null;
        if (url.toURI().getAuthority() == null)
            jarFile = new File(url.toURI());
        else
            jarFile = new File(url.toURI().getSchemeSpecificPart());

        return jarFile.getParentFile();
    }

    private void loadArgs(String[] args) {
        if (args == null)
            return;

        boolean skipNext = false;
        for (int i = 0; i < args.length; i++) {
            if (skipNext) {
                skipNext = false;
                continue;
            }
            if (args[i].equals("--nologfile")) { //$NON-NLS-1$
                nolog = true;
            } else if (args[i].equals("-multicases")) { //$NON-NLS-1$
                isMultiCase = true;
                casesPathFile = new File(args[i + 1]).getAbsoluteFile();
                skipNext = true;

                if (!casesPathFile.exists()) {
                    System.out.println(Messages.getString("AppMain.NoCasesFile") + args[1]); //$NON-NLS-1$
                    System.exit(1);
                }
            } else {
                throw new IllegalArgumentException("Unknown option " + args[i]); //$NON-NLS-1$
            }
        }
    }

    public void start(File casePath, Manager processingManager, String[] args) {

        try {
            loadArgs(args);

            if (casesPathFile == null)
                casesPathFile = casePath;

            File logParent = casesPathFile;
            if (isMultiCase && casesPathFile.isFile())
                logParent = casesPathFile.getParentFile();

            File logFile = new File(logParent, appLogFileName).getCanonicalFile();
            if ((logFile.exists() && !IOUtil.canWrite(logFile)) || !IOUtil.canCreateFile(logFile.getParentFile())) {
                logFile = new File(System.getProperty("java.io.tmpdir"), appLogFileName);
            }

            if (libDir == null)
                libDir = detectLibDir();

            LogConfiguration logConfiguration = null;

            if (processingManager == null) {
                logConfiguration = new LogConfiguration(libDir.getParentFile().getAbsolutePath(), logFile);
                logConfiguration.configureLogParameters(nolog);

                Logger LOGGER = LoggerFactory.getLogger(AppMain.class);
                LOGGER.info(Version.APP_NAME);

                Configuration.getInstance().loadIpedRoot();
            }
            
            Configuration.getInstance().loadConfigurables(libDir.getParentFile().getAbsolutePath(), true);

            App.get().init(logConfiguration, isMultiCase, casesPathFile, processingManager, libDir.getAbsolutePath());

            UICaseDataLoader init = new UICaseDataLoader(processingManager);
            init.execute();

        } catch (Exception e) {
            e.printStackTrace();
            showError(e);
        }

    }

    private static void showError(Exception e) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintStream ps = new PrintStream(baos, true)) {
            e.printStackTrace(ps);
        }
        JOptionPane.showMessageDialog(null, "Error: " + new String(baos.toByteArray()), // $NON-NLS-1$
                Messages.getString("AppLazyInitializer.errorTitle"), JOptionPane.ERROR_MESSAGE); //$NON-NLS-1$
    }

}