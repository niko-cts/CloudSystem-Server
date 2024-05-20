package net.fununity.cloud.server.command;

import net.fununity.cloud.common.utils.CloudLogger;
import net.fununity.cloud.server.command.handler.Command;
import org.apache.log4j.Level;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class DebugCommand extends Command {

    public static final Path DEBUG_OUTPUT = Paths.get("Servers", "DebugOutput");

    /**
     * Instantiate this class with the name of a command and with none or specified aliases
     *
     * @since 0.0.1
     */
    public DebugCommand() {
        super("debug", "debug save/info/debug", "Saves a debug text file with every console output and cloud packets.", "log");
    }

    /**
     * Will be called when the user typed in the command name or aliase.
     *
     * @param args String[] - The arguments behind the command
     * @since 0.0.1
     */
    @Override
    public void execute(String[] args) {
        if (args.length != 1) {
            sendCommandUsage();
            return;
        }

        switch (args[0].toLowerCase()) {
            case "save" -> {
                try {
                    CloudLogger.saveLogs(DEBUG_OUTPUT
                            .resolve("CloudServer/" + OffsetDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy-HH:mm:ss"))
                                     + ".txt").toAbsolutePath());
                    log.info("Debug file was saved.");
                } catch (IOException e) {
                    log.error("Could not save logfile of cloudserver because: " + e.getMessage());
                }
            }
            case "info", "debug" -> {
                CloudLogger.setLoggingLevel(Level.toLevel(args[0].toUpperCase()));
                log.info("Log-Level was changed to " + args[0]);
                log.debug("Log-Level was changed to " + args[0]);
            }
            default -> sendCommandUsage();
        }
    }
}
