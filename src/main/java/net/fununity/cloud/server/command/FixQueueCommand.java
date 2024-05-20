package net.fununity.cloud.server.command;

import net.fununity.cloud.server.command.handler.Command;
import net.fununity.cloud.server.server.Server;
import net.fununity.cloud.server.server.ServerHandler;

/**
 * Command class to remove a server, when crashed.
 *
 * @author Niko
 * @see Command
 * @since 0.0.1
 */
public class FixQueueCommand extends Command {

    /**
     * Instantiate this class with the name of a command and with none or specified aliases
     *
     * @since 0.0.1
     */
    public FixQueueCommand() {
        super("fixQueue", "fixQueue [check <serverId>]/skipfirst", "Will skip the first in starting queue or checks if the given server can be removed from the queue.");
    }

    /**
     * Will be called when the user typed in the command name or aliase.
     *
     * @param args String[] - The arguments behind the command
     * @since 0.0.1
     */
    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            sendCommandUsage();
            return;
        }
        switch (args[0].toLowerCase()) {
            case "check" -> {
                if (args.length < 2) {
                    sendCommandUsage();
                    return;
                }
                Server server = ServerHandler.getInstance().getServerByIdentifier(args[1]);
                if (server == null) {
                    sendIllegalServerId(args[1]);
                    return;
                }

                log.info("Checking startqueue for server " + server.getServerId());
                ServerHandler.getInstance().checkStartQueue(server);
            }
            case "skipfirst" -> {
                log.info("Flushing first server in starting queue...");
                ServerHandler.getInstance().checkStartQueue(ServerHandler.getInstance().getStartQueue().peek());
            }
            default -> sendCommandUsage();
        }
    }
}
