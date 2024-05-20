package net.fununity.cloud.server.command;

import net.fununity.cloud.common.utils.CloudLogger;
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

import java.io.IOException;

/**
 * The intern console of the {@link net.fununity.cloud.server.CloudServer}
 *
 * @author Niko
 * @since 0.0.1
 */
public class CloudConsole {

    private static final CloudLogger LOG = CloudLogger.getLogger(CloudConsole.class.getSimpleName());
    private static CloudConsole instance;

    /**
     * Get a singleton instance of this class
     *
     * @return {@link CloudConsole} - Instance of this class
     * @since 0.0.1
     */
    public static CloudConsole getInstance() {
        if (instance == null)
            instance = new CloudConsole();
        return instance;
    }

    private Terminal terminal;
    private final CommandHandler commandHandler;
    private boolean exit;

    /**
     * Instantiate this class.
     * Loads the {@link CommandHandler}
     * Starts the console input via {@link #startTerminal()}
     *
     * @since 0.0.1
     */
    public CloudConsole() {
        instance = this;
        this.commandHandler = new CommandHandler(LOG);
        this.terminal = null;
        this.exit = false;
        startTerminal();
    }

    private void startTerminal() {
        try {
            terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();
            LineReader lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(new NullCompleter())
                    .parser(new DefaultParser())
                    .build();

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
                    LOG.warn("Console through exception: " + exception.getMessage());
                }
            }

            terminal.close();
        } catch (IOException e) {
            LOG.error("Caught exception in terminal " + e.getMessage());
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
                LOG.error("Could not close terminal: " + e.getMessage());
            }
        }
    }

    /**
     * Returns the CloudConsole Logger.
     *
     * @return Logger - Logger of Console
     * @since 0.0.1
     */
    public static CloudLogger getLog() {
        return LOG;
    }

    /**
     * Returns the instance of the {@link CommandHandler}
     *
     * @return {@link CommandHandler} - Instance of the commandHandler.
     * @since 0.0.1
     */
    public CommandHandler getCommandHandler() {
        return this.commandHandler;
    }
}
