package net.fununity.cloud.server.command;

import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.command.handler.Command;

public class StartCommand extends Command {

    /**
     * Instantiate this class with the name of a command and with none or specified aliases
     * @since 0.0.1
     */
    public StartCommand() {
        super("start", "start default/(<serverType> <amount>)", "Starts a new server with specified type or the default ones");
    }

    /**
     * Will be called when the user typed in the command name or aliase.
     * @param args String[] - The arguments behind the command
     * @since 0.0.1
     */
    @Override
    public void execute(String[] args) {
        if(args.length == 0) {
            sendCommandUsage();
            return;
        }
        if (args[0].equalsIgnoreCase("default")) {
            log.info("Starting default servers...");
            CloudServer.getInstance().getConfigHandler().startDefaultServers();
            return;
        }

        ServerType serverType;
        try {
            serverType = ServerType.valueOf(args[0]);
        } catch (IllegalArgumentException exception) {
            sendIllegalServerType();
            sendCommandUsage();
            return;
        }

        int amount = 1;
        if (args.length == 2) {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
	            log.warn("Invalid amount specified for {}", args[1]);
                sendCommandUsage();
                return;
            }
        }

        log.info("Starting {} server with type {}", amount, args[0]);
        for (int i = 0; i < amount; i++)
            manager.createServerByServerType(serverType);
    }
}
