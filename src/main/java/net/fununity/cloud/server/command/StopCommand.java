package net.fununity.cloud.server.command;

import net.fununity.cloud.server.command.handler.Command;
import net.fununity.cloud.server.misc.ServerHandler;
import net.fununity.cloud.server.server.Server;

public class StopCommand extends Command {

    /**
     * Instantiate this class with the name of a command and with no or x aliases
     * @since 0.0.1
     */
    public StopCommand() {
        super("stop", "stop <serverId>", "Stops a specified server id");
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
        log.info("Shutting down " + server.getServerId());
        ServerHandler.getInstance().shutdownServer(server);
    }
}
