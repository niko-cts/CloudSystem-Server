package net.fununity.cloud.server.command.handler;

import lombok.Getter;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.command.CloudConsole;
import net.fununity.cloud.server.server.ServerManager;
import org.slf4j.Logger;

/**
 * The abstract Command class for the command system.
 * Extend this class and instantiate it to register the command.
 * If the user types the name or aliases in the {@link CloudConsole} the {@link CommandHandler} will try to execute it.
 * @author Niko
 * @since 0.0.1
 */
@Getter
public abstract class Command {

	protected static final Logger log = CloudConsole.getLogger();
	protected final ServerManager manager;
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
		this.manager = CloudServer.getInstance().getServerManager();
		this.name = name;
		this.usage = usage;
		this.description = description;
		this.aliases = aliases;
	}

	/**
	 * Will be called when the user typed in the command name or aliase.
	 * @param args String[] - The arguments behind the command
	 * @since 0.0.1
	 */
	public abstract void execute(String[] args);

	public void sendCommandUsage() {
		log.info("{} - {}", getUsage(), getDescription());
	}

	public void sendIllegalServerType() {
		log.warn("This servertype is invalid! Type 'servertype' to lookup the servertypes.");
	}

	public void sendIllegalServerId(String type) {
		log.warn(type + " is invalid! Type 'list' to see all servers.");
	}

	public void sendIllegalIdOrServerType(String argument) {
		log.warn("{} is not a valid server id or servertype! (Type: 'list' or 'servertype' to get help)", argument);
	}
}
