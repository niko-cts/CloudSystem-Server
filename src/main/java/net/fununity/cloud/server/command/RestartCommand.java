package net.fununity.cloud.server.command;

import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.command.handler.Command;
import net.fununity.cloud.server.util.ServerUtils;

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
            if (serverType == ServerType.BUNGEECORD) {
                log.warn("This servertype can not be restarted!");
                return;
            }
	        log.info("Restarting all servers with server type {}", serverType);
            ServerUtils.restartAllServerOfType(serverType);
        } catch (IllegalArgumentException exception) {
            getServerIdOrSendIllegal(args[0], server -> {
	            log.info("Restarting server {}", server.getServerName());
                manager.requestRestartServer(server);
            });
        }
    }
}
