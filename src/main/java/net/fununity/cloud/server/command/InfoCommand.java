package net.fununity.cloud.server.command;

import net.fununity.cloud.server.command.handler.Command;
import net.fununity.cloud.server.misc.ServerHandler;
import net.fununity.cloud.server.server.Server;

public class InfoCommand extends Command {

    /**
     * Instantiate this class with the name of a command and with none or specified aliases
     * @since 0.0.1
     */
    public InfoCommand() {
        super("info", "info <Server>", "Shows information about a specific server", "serverinfo");
    }

    /**
     * Will be called, when the user typed in the command name or aliase.
     * @param args String[] - The arguments behind the command
     * @since 0.0.1
     */
    @Override
    public void execute(String[] args) {
        if(args.length == 0) {
            sendCommandUsage();
            return;
        }
        Server server = ServerHandler.getInstance().getServerByIdentifier(args[0]);
        if(server == null) {
            sendIllegalServerId(args[0]);
            return;
        }
        log.info(new StringBuilder().append("Info of server ")
                .append(server.getServerId()).append(":").append(server.getServerPort())
                .append(server.getServerType()).append(" ")
                .append(server.getPlayerCount()).append("/").append(server.getMaxPlayers()).append(" ")
                .append(server.getServerMaxRam()).append(" ")
                .toString());
    }
}
