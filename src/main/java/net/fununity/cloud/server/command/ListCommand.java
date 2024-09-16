package net.fununity.cloud.server.command;

import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.command.handler.Command;
import net.fununity.cloud.server.server.Server;
import net.fununity.cloud.server.server.ServerHandler;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ListCommand extends Command {

    /**
     * Instantiate this class with the name of a command and with no or x aliases
     * @since 0.0.1
     */
    public ListCommand() {
        super("list", "list (<servertype>)", "Shows all server or all from one type");
    }

    /**
     * Will be called when the user typed in the command name or aliase.
     * @param args String[] - The arguments behind the command
     * @since 0.0.1
     */
    @Override
    public void execute(String[] args) {
        List<Server> servers = ServerHandler.getInstance().getServers();
        if (args.length == 1) {
            try {
                ServerType serverType = ServerType.valueOf(args[0]);
                servers.removeIf(d -> d.getServerType() != serverType);
            } catch (IllegalArgumentException exception) {
                sendIllegalServerType();
                return;
            }
        } else
            servers.sort(Comparator.comparingInt(value -> value.getServerType().ordinal()));

        log.info("Players on network: {}", ServerHandler.getInstance().getPlayerCountOfNetwork());
        log.info("{} server(s) active:", servers.size());
        log.info(servers.stream().map(this::getServerDetails).collect(Collectors.joining(", ")));

        if (!ServerHandler.getInstance().getStartQueue().isEmpty()) {
            log.info(ServerHandler.getInstance().getStartQueue().size() +
                     " in start queue: " +
                     ServerHandler.getInstance().getStartQueue().stream().map(Server::getServerId).collect(Collectors.joining(", ")));
        }
    }

    private String getServerDetails(Server server) {
        return new StringBuilder().append(server.getServerId())
                .append("[").append(server.getServerPort()).append(",P:")
                .append(server.getPlayerCount()).append("]").toString();
    }
}
