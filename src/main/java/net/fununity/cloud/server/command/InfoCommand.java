package net.fununity.cloud.server.command;

import net.fununity.cloud.server.command.handler.Command;

public class InfoCommand extends Command {

	/**
	 * Instantiate this class with the name of a command and with none or specified aliases
	 * @since 0.0.1
	 */
	public InfoCommand() {
		super("info", "info <server>", "Shows information about a specific server", "serverinfo");
	}

	/**
	 * Will be called, when the user typed in the command name or aliase.
	 * @param args String[] - The arguments behind the command
	 * @since 0.0.1
	 */
	@Override
	public void execute(String[] args) {
		if (args.length == 0) {
			sendCommandUsage();
			return;
		}
		getServerIdOrSendIllegal(args[0], server -> {
			log.info("Info of server {}",
					server.getServerId());
			log.info("{}", server);
		});
	}
}
