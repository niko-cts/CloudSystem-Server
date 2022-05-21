package net.fununity.cloud.server.command;

import net.fununity.cloud.common.utils.DebugLoggerUtil;
import net.fununity.cloud.server.command.handler.Command;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public class DebugCommand extends Command {
    /**
     * Instantiate this class with the name of a command and with none or specified aliases
     * @since 0.0.1
     */
    public DebugCommand() {
        super("debug", "debug", "Saves a debug text file with every console output and cloud packets.", "log");
    }

    /**
     * Will be called, when the user typed in the command name or aliase.
     * @param args String[] - The arguments behind the command
     * @since 0.0.1
     */
    @Override
    public void execute(String[] args) {
        try {
            String date = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM-HH:mm"));
            DebugLoggerUtil.getInstance().saveToTextFile(Paths.get("./Servers/DebugOutput/CLOUD/" + date + ".txt"));
            log.info("Debug file was saved.");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }
}
