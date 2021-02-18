package net.fununity.cloud.server.command;

import net.fununity.cloud.server.command.handler.CommandHandler;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.util.NoSuchElementException;
import java.util.Scanner;

/**
 * The intern console of the {@link net.fununity.cloud.server.CloudServer}
 * @author Niko
 * @since 0.0.1
 */
public class CloudConsole {

    private static CloudConsole instance;

    /**
     * Get a singleton instance of this class
     * @return {@link CloudConsole} - Instance of this class
     * @since 0.0.1
     */
    public static CloudConsole getInstance() {
        if(instance == null)
            instance = new CloudConsole();
        return instance;
    }

    private final CommandHandler commandHandler;
    private final Logger logger;
    private boolean exit;

    /**
     * Instantiate this class.
     * Loads the {@link CommandHandler}
     * Starts the console input via {@link #run()}
     * @since 0.0.1
     */
    public CloudConsole() {
        instance = this;
        PatternLayout layout = new PatternLayout("[%d{HH:mm:ss}] %c{1} [%p]: %m%n");
        this.logger = Logger.getLogger("Console");
        this.logger.addAppender(new ConsoleAppender(layout));
        this.logger.setLevel(Level.INFO);
        this.logger.setAdditivity(false);
        this.commandHandler = new CommandHandler(logger);
        this.exit = false;
        this.run();
    }

    /**
     * Listens with a scanner to notify insert of user
     * @since 0.0.1
     */
    private void run() {
        while(!exit) {
            try(Scanner scanner = new Scanner(System.in)) {
                String input = scanner.nextLine();
                this.commandHandler.tryToExecuteCommand(input.split(" "));
            } catch (NoSuchElementException ignored) {
                // ignored
            }
        }
    }

    /**
     * Exits the scanner console
     * @since 0.0.1
     */
    public void shutDown() {
        this.exit = true;
    }

    /**
     * Returns the CloudConsole Logger.
     * @return Logger - Logger of Console
     * @since 0.0.1
     */
    public Logger getLogger() {
        return this.logger;
    }

    /**
     * Returns the instance of the {@link CommandHandler}
     * @return {@link CommandHandler} - Instance of the commandHandler.
     * @since 0.0.1
     */
    public CommandHandler getCommandHandler() {
        return this.commandHandler;
    }
}
