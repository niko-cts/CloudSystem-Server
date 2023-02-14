package net.fununity.cloud.server.command;

import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.command.handler.Command;
import net.fununity.cloud.server.misc.ServerHandler;
import net.fununity.cloud.server.server.Server;

import java.util.Comparator;
import java.util.List;

public class ListCommand extends Command {

    /**
     * Instantiate this class with the name of a command and with no or x aliases
     * @since 0.0.1
     */
    public ListCommand() {
        super("list", "list (<servertype>)", "Shows all server or all from one type");
    }

    /**
     * Will be called, when the user typed in the command name or aliase.
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

        log.info("Players on network: " + ServerHandler.getInstance().getPlayerCountOfNetwork());
        log.info(servers.size() + " server active:");
        StringBuilder builder = new StringBuilder();
        for (Server server : servers)
            builder.append(getServerDetails(server)).append(", ");
        log.info(builder.toString());

        if (!ServerHandler.getInstance().getStartQueue().isEmpty()) {
            builder = new StringBuilder();
            builder.append("In start queue: ");
            for (Server server : ServerHandler.getInstance().getStartQueue()) {
                builder.append(server.getServerId()).append(", ");
            }
            log.info(builder.toString());
        }
        if (!ServerHandler.getInstance().getServerDeleteQueue().isEmpty()) {
            builder = new StringBuilder();
            builder.append("In stop queue: ");
            for (Server server : ServerHandler.getInstance().getServerDeleteQueue()) {
                builder.append(server.getServerId()).append(", ");
            }
            log.info(builder.toString());
        }
    }

    private String getServerDetails(Server server) {
        return new StringBuilder().append(server.getServerId())
                .append(" [").append(server.getServerIp()).append(":").append(server.getServerPort()).append(" w. ")
                .append(server.getPlayerCount()).append(", max-ram: ").append(server.getServerMaxRam()).append("]").toString();
    }
}
