package net.fununity.cloud.server.command;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import net.fununity.cloud.server.command.handler.Command;
import org.slf4j.LoggerFactory;

public class LogLevelCommand extends Command {

    /**
     * Instantiate this class with the name of a command and with none or specified aliases
     * @since 0.0.1
     */
    public LogLevelCommand() {
        super("loglevel", "loglevel <level>", "Sets the logging level of the console output.");
    }

    /**
     * Will be called when the user typed in the command name or aliase.
     * @param args String[] - The arguments behind the command
     * @since 0.0.1
     */
    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            sendCommandUsage();
            return;
        }

        String level = args[0].toUpperCase();
        try {
            setLoggingLevelForAllContexts(level);
            log.info("Set log level to {}", level);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid log level: {}", level);
        }
    }

    private void setLoggingLevelForAllContexts(String levelStr) {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        Level level = Level.toLevel(levelStr, Level.INFO); // Default to INFO if the levelStr is invalid
        loggerContext.getLoggerList().stream()
            .filter(logger -> logger.getName().startsWith("net.fununity.cloud"))
            .forEach(logger -> logger.setLevel(level));
    }
}
