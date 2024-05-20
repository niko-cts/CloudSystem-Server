package net.fununity.cloud.server.command;

import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.command.handler.Command;
import net.fununity.cloud.server.misc.ConfigHandler;
import net.fununity.cloud.server.server.ServerHandler;

public class StartCommand extends Command {

    /**
     * Instantiate this class with the name of a command and with none or specified aliases
     * @since 0.0.1
     */
    public StartCommand() {
        super("start", "start <serverType>/default (<amount>)", "Starts a new server with specified type");
    }

    /**
     * Will be called when the user typed in the command name or aliase.
     * @param args String[] - The arguments behind the command
     * @since 0.0.1
     */
    @Override
    public void execute(String[] args) {
        if(args.length == 0) {
            sendCommandUsage();
            return;
        }
        if (ServerHandler.getInstance().getCurrentRamUsed() > ServerHandler.MAX_RAM) {
            log.warn("The network has reached it's maximum amount of ram.");
            return;
        }
        ServerType serverType;
        try {
            serverType = ServerType.valueOf(args[0]);
        } catch (IllegalArgumentException exception) {
            if (args[0].equalsIgnoreCase("default")) {
                log.info("Starting default servers...");
                ConfigHandler.getInstance().loadDefaultServers();
                return;
            }
            sendIllegalServerType();
            return;
        }
        int amount = 1;
        if (args.length == 2) {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException exception) {
                sendCommandUsage();
            }
        }

        log.info("Starting %s server with type %s", amount, args[0]);
        for (int i = 0; i < amount; i++)
            ServerHandler.getInstance().createServerByServerType(serverType);
    }
}
