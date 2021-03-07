package net.fununity.cloud.server.command;

import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.command.handler.Command;
import net.fununity.cloud.server.misc.ServerHandler;
import net.fununity.cloud.server.server.Server;

import java.util.List;
import java.util.stream.Collectors;

public class UpdateCommand extends Command {

    /**
     * Instantiate this class with the name of a command and with none or specified aliases
     * @since 0.0.1
     */
    public UpdateCommand() {
        super("update", "update <serverType>", "Sends all user to the lobby, stops the server and starts a new one with the servertype.");
    }

    /**
     * Will be called, when the user typed in the command name or aliase.
     * @param args String[] - The arguments behind the command
     * @since 0.0.1
     */
    @Override
    public void execute(String[] args) {
        if (args.length != 1) {
            sendCommandUsage();
            return;
        }
        ServerType serverType;
        try {
            serverType = ServerType.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException exception) {
            sendIllegalServerType();
            return;
        }

        if(serverType == ServerType.BUNGEECORD || serverType == ServerType.LOBBY) {
            log.warn("This type can not be updated");
            return;
        }

        List<Server> servers = ServerHandler.getInstance().getServers().stream().filter(server -> server.getServerType() == serverType).collect(Collectors.toList());
        int size = servers.size();
        for (Server server : servers)
            server.stop(true);
        for (int i = 0; i < size; i++)
            ServerHandler.getInstance().createServerByServerType(serverType);
    }
}
