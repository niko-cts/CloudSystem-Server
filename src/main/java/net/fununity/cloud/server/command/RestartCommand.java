package net.fununity.cloud.server.command;

import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.command.handler.Command;
import net.fununity.cloud.server.misc.ServerHandler;
import net.fununity.cloud.server.server.Server;

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
            ServerType serverType = ServerType.valueOf(args[0].toUpperCase());
            log.info("Restarting all servers with server type " + args[0]);
            ServerHandler.getInstance().restartAllServersOfType(serverType);
        } catch (IllegalArgumentException exception) {
            Server server = ServerHandler.getInstance().getServerByIdentifier(args[0]);
            if(server == null) {
                log.warn("Illegal ServerType or ServerID");
                return;
            }
            log.info("Restarting server " + args[0]);
            server.restart();
        }
    }
}
