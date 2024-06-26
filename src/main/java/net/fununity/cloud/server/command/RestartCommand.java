package net.fununity.cloud.server.command;

import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.command.handler.Command;
import net.fununity.cloud.server.server.Server;
import net.fununity.cloud.server.server.ServerHandler;

public class RestartCommand extends Command {

    /**
     * Instantiate this class with the name of a command and with none or specified aliases
     * @since 0.0.1
     */
    public RestartCommand() {
        super("restart", "restart <serverType>/<serverId>", "Restarts all server of one server types or a specific server. Restarting does not delete the server!");
    }

    /**
     * Will be called, when the user typed in the command name or aliase.
     * @param args String[] - The arguments behind the command
     * @since 0.0.1
     */
    @Override
    public void execute(String[] args) {
        if(args.length != 1) {
            sendCommandUsage();
            return;
        }
        try {
            ServerType serverType = ServerType.valueOf(args[0]);
            if(serverType == ServerType.BUNGEECORD) {
                log.warn("This servertype can not be restarted!");
                return;
            }
            log.info("Restarting all servers with server type " + serverType);
            ServerHandler.getInstance().restartAllServersOfType(serverType);
        } catch (IllegalArgumentException exception) {
            Server server = ServerHandler.getInstance().getServerByIdentifier(args[0]);
            if (server == null) {
                sendIllegalIdOrServerType(args[0]);
                return;
            }
            log.info("Restarting server " + args[0]);
            ServerHandler.getInstance().restartServer(server);
        }
    }
}
