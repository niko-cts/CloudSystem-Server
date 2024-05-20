package net.fununity.cloud.server.command;

import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.command.handler.Command;
import net.fununity.cloud.server.server.Server;
import net.fununity.cloud.server.server.ServerHandler;

public class StopCommand extends Command {

    /**
     * Instantiate this class with the name of a command and with no or x aliases
     * @since 0.0.1
     */
    public StopCommand() {
        super("stop", "stop <serverId>/<serverType>/all (save)", "Stops a specified server or all of one type. (saves log)");
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
        String saveLog = args.length > 1 && args[1].equalsIgnoreCase("save") ? "Command" : null;
        if (args[0].equalsIgnoreCase("all")) {
            log.info("Shutting down servers...");
            ServerHandler.getInstance().shutdownAllServers(saveLog);
            return;
        }

        try {
            ServerType serverType = ServerType.valueOf(args[0]);
            log.info("Shutting down %s...", serverType);
            ServerHandler.getInstance().shutdownAllServersOfType(serverType, saveLog);
        } catch (IllegalArgumentException exception) {
            Server server = ServerHandler.getInstance().getServerByIdentifier(args[0]);
            if (server == null) {
                sendIllegalIdOrServerType(args[0]);
                return;
            }

            log.info("Shutting down %s...", server.getServerId(), saveLog);
            server.setSaveLogFile(saveLog);
            ServerHandler.getInstance().shutdownServer(server, false);
        }
    }
}
