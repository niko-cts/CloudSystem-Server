package net.fununity.cloud.server.command;

import net.fununity.cloud.common.server.ServerDefinition;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.command.handler.Command;
import net.fununity.cloud.server.misc.ServerHandler;
import net.fununity.cloud.server.server.Server;

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
        List<ServerDefinition> serverDefinitions = ServerHandler.getInstance().generateServerDefinitions();
        if (args.length == 1) {
            try {
                ServerType serverType = ServerType.valueOf(args[0]);
                serverDefinitions.removeIf(d -> d.getServerType() != serverType);
            } catch (IllegalArgumentException exception) {
                sendIllegalServerType();
                return;
            }
        }
        log.info("Players on network: " + ServerHandler.getInstance().getPlayerCountOfNetwork());
        StringBuilder builder = new StringBuilder(serverDefinitions.size() + " servers active: ");
        for (ServerDefinition serverDefinition : serverDefinitions)
            builder.append(serverDefinition.getServerId()).append("(").append(serverDefinition.getCurrentPlayers()).append("), ");
        log.info(builder.toString());

        if (!ServerHandler.getInstance().getStartQueue().isEmpty()) {
            builder = new StringBuilder();
            builder.append("In start queue: ");
            for (Server server : ServerHandler.getInstance().getStartQueue()) {
                builder.append(server.getServerId()).append(", ");
            }
            log.info(builder.toString());
        }
        if (!ServerHandler.getInstance().getStopQueue().isEmpty()) {
            builder = new StringBuilder();
            builder.append("In stop queue: ");
            for (Server server : ServerHandler.getInstance().getStopQueue()) {
                builder.append(server.getServerId()).append(", ");
            }
            log.info(builder.toString());
        }
        if (!ServerHandler.getInstance().getRestartQueue().isEmpty()) {
            builder = new StringBuilder();
            builder.append("In restart queue: ");
            for (ServerType server : ServerHandler.getInstance().getRestartQueue()) {
                builder.append(server).append(", ");
            }
            log.info(builder.toString());
        }
    }
}
