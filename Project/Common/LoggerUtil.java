package Project.Common;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class LoggerUtil {
    public static final LoggerUtil INSTANCE = new LoggerUtil();

    public static class LoggerConfig {
        private int fileSizeLimit = 1024 * 1024;
        private int fileCount = 1;
        private String logLocation = "app.log";

        public void setFileSizeLimit(int bytes) {
            this.fileSizeLimit = bytes;
        }

        public int getFileSizeLimit() {
            return fileSizeLimit;
        }

        public void setFileCount(int count) {
            this.fileCount = count;
        }

        public int getFileCount() {
            return fileCount;
        }

        public void setLogLocation(String path) {
            this.logLocation = path;
        }

        public String getLogLocation() {
            return logLocation;
        }
    }

    private Logger logger = Logger.getLogger("it114-project");
    private FileHandler fileHandler = null;

    private LoggerUtil() {
        logger.setLevel(Level.ALL);
    }

    public void setConfig(LoggerConfig cfg) {
        if (cfg == null) return;
        try {
            if (fileHandler != null) {
                logger.removeHandler(fileHandler);
                fileHandler.close();
            }
            fileHandler = new FileHandler(cfg.getLogLocation(), cfg.getFileSizeLimit(), cfg.getFileCount(), true);
            fileHandler.setFormatter(new SimpleFormatter());
            logger.addHandler(fileHandler);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to create log file handler", e);
        }
    }

    public void info(String msg) {
        logger.info(msg);
    }

    public void info(String msg, Throwable t) {
        logger.log(Level.INFO, msg, t);
    }

    public void fine(String msg) {
        logger.fine(msg);
    }

    public void fine(String msg, Throwable t) {
        logger.log(Level.FINE, msg, t);
    }

    public void warning(String msg) {
        logger.warning(msg);
    }

    public void warning(String msg, Throwable t) {
        logger.log(Level.WARNING, msg, t);
    }

    public void severe(String msg) {
        logger.severe(msg);
    }

    public void severe(String msg, Throwable t) {
        logger.log(Level.SEVERE, msg, t);
    }
}
