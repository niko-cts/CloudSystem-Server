package net.fununity.cloud.server.command.handler;

import net.fununity.cloud.server.command.CloudConsole;
import org.apache.log4j.Logger;

/**
 * The abstract Command class for the command system.
 * Extend this class and instantiate it to register the command.
 * If the user types the name or aliases in the {@link CloudConsole} the {@link CommandHandler} will try to execute it.
 * @author Niko
 * @since 0.0.1
 */
public abstract class Command {

    protected final Logger log;
    private final String name;
    private final String usage;
    private final String description;
    private final String[] aliases;

    /**
     * Instantiate this class with the name of a command and with none or specified aliases
     * @param name String - Name of command
     * @param usage String - usage of command
     * @param description String - description of command
     * @param aliases String[] - Aliases of command
     * @since 0.0.1
     */
    public Command(String name, String usage, String description, String... aliases) {
        this.name = name;
        this.usage = usage;
        this.description = description;
        this.aliases = aliases;
        this.log = CloudConsole.getInstance().getLogger();
    }

    /**
     * Will be called, when the user typed in the command name or aliase.
     * @param args String[] - The arguments behind the command
     * @since 0.0.1
     */
    public abstract void execute(String[] args);

    /**
     * Get the name of the command
     * @return String - Name of command
     * @since 0.0.1
     */
    public String getName() {
        return name;
    }

    /**
     * Get the usage of the Command
     * @return String - usage of command
     * @since 0.0.1
     */
    public String getUsage() {
        return usage;
    }

    /**
     * Get the description of the command.
     * @return String - description of command.
     * @since 0.0.1
     */
    public String getDescription() {
        return description;
    }

    /**
     * Get the Aliases of the command
     * @return String[] - All aliases of this command
     * @since 0.0.1
     */
    public String[] getAliases() {
        return aliases;
    }

    public void sendCommandUsage() {
        log.info(getUsage() + " - " + getDescription());
    }

    public void sendIllegalServerType() {
        log.warn("This servertype is invalid! Type 'servertype' to lookup the servertypes.");
    }

    public void sendIllegalServerId(String type) {
        log.warn(type + " is invalid! Type 'list' to see all servers.");
    }

    public void sendIllegalIdOrServerType(String argument) {
        log.warn(argument + " is not a valid server id or servertype! (Type: 'list' or 'servertype' to get help)");
    }
}
