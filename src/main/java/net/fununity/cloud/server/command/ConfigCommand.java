package net.fununity.cloud.server.command;

import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.command.handler.Command;

/**
 * The config command of the cloud.
 * @see net.fununity.cloud.server.server.Server
 * @author Niko
 * @since 0.0.1
 */
public class ConfigCommand extends Command {

	/**
	 * Instantiate this class with the name of a command and with none or specified aliases
	 * @since 0.0.1
	 */
	public ConfigCommand() {
		super("config", "config", "Reloads the config");
	}

	/**
	 * Will be called, when the user typed in the command name or aliase.
	 * @param args String[] - The arguments behind the command
	 * @since 0.0.1
	 */
	@Override
	public void execute(String[] args) {
		log.info("Reloading config...");
		CloudServer.getInstance().getConfigHandler().loadNetworkConfig();
	}
}
