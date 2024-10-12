package net.fununity.cloud.server.command;

import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.command.handler.Command;
import net.fununity.cloud.server.util.ServerUtils;

public class StopCommand extends Command {

    /**
     * Instantiate this class with the name of a command and with no or x aliases
     * @since 0.0.1
     */
    public StopCommand() {
        super("stop", "stop <serverId>/<serverType>/all", "Stops a specified server or all of one type.");
    }

    /**
     * Will be called when the user typed in the command name or aliase.
     * @param args String[] - The arguments behind the command
     * @since 0.0.1
     */
    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            sendCommandUsage();
            return;
        }

        if (args[0].equalsIgnoreCase("all")) {
            log.info("Shutting down servers...");
            ServerUtils.shutdownAll();
            return;
        }

        try {
            ServerType serverType = ServerType.valueOf(args[0]);
            log.info("Shutting down all server of type {}...", serverType);
            ServerUtils.shutdownAllServersOfType(serverType);
        } catch (IllegalArgumentException exception) {
            getServerIdOrSendIllegal(args[0], server -> {
                log.info("Shutting down server {}...", server.getServerId());
                manager.requestStopServer(server);
            });
        }
    }
}
