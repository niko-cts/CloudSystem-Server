package net.fununity.cloud.server.command;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.command.handler.CommandHandler;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.completer.NullCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;

import java.io.IOException;

/**
 * The intern console of the {@link net.fununity.cloud.server.CloudServer}
 *
 * @author Niko
 * @since 0.0.1
 */
@Slf4j
public class CloudConsole {

    private Terminal terminal;
	@Getter
	private final CommandHandler commandHandler;
    private boolean exit;

    /**
     * Instantiate this class.
     * Loads the {@link CommandHandler}
     * Starts the console input via {@link #start()}
     *
     * @since 0.0.1
     */
    public CloudConsole() {
        this.commandHandler = new CommandHandler(log);
        this.terminal = null;
        this.exit = false;
    }

    public void start() {
        try {
            log.debug("Starting cloud console...");
            this.terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();
            LineReader lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(new NullCompleter())
                    .parser(new DefaultParser())
                    .build();

            log.info("Type 'help' for help.");
            while (!exit) {
                try {
                    String line = lineReader.readLine("> ");
                    if (line == null) {
                        CloudServer.getInstance().shutdownEverything();
                        return; // User pressed Ctrl+D or Ctrl+C
                    }
                    this.commandHandler.tryToExecuteCommand(line.split(" "));
                } catch (UserInterruptException | EndOfFileException ignore) {
                } catch (Exception exception) {
                    log.warn("Console through exception:", exception);
                }
            }

            terminal.close();
        } catch (IOException e) {
            log.error("Caught exception in terminal: ", e);
        }
    }

    /**
     * Exits the scanner console
     *
     * @since 0.0.1
     */
    public void shutDown() {
        this.exit = true;
        if (terminal != null) {
            try {
                terminal.close();
            } catch (IOException e) {
	            log.error("Could not close terminal:", e);
            }
        }
    }

    public static Logger getLogger() {
        return log;
    }
}
