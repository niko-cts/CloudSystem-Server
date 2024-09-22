package net.fununity.cloud.server.command;

import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.command.handler.Command;

public class ExpireCommand extends Command {

    /**
     * Instantiate this class with the name of a command and with none or specified aliases
     * @since 0.0.1
     */
    public ExpireCommand() {
        super("expire", "expire <serverType>", "Expires a server type. No server with this type won't start anymore till one type 'validate <serverType>'");
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
        try {
            ServerType serverType = ServerType.valueOf(args[0]);
            manager.getExpireServers().add(serverType);
	        log.info("Expire-Mode enabled on {}", serverType);
        } catch (IllegalArgumentException exception) {
            sendIllegalServerType();
        }
    }
}
