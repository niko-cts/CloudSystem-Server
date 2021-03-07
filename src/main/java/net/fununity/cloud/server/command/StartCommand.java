package net.fununity.cloud.server.command;

import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.command.handler.Command;
import net.fununity.cloud.server.misc.ServerHandler;

public class StartCommand extends Command {

    /**
     * Instantiate this class with the name of a command and with none or specified aliases
     * @since 0.0.1
     */
    public StartCommand() {
        super("start", "start <serverType>", "Starts a new server with specified type");
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
        if(ServerHandler.getInstance().getCurrentRamUsed() > ServerHandler.MAX_RAM) {
            log.warn("The network has reached it's maximum amount of ram.");
            return;
        }
        ServerType serverType;
        try {
            serverType = ServerType.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException exception) {
            sendIllegalServerType();
            return;
        }
        log.info("Starting server with server type " + args[0]);
        ServerHandler.getInstance().createServerByServerType(serverType);
    }
}
