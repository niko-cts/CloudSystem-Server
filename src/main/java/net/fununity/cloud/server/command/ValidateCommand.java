package net.fununity.cloud.server.command;

import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.command.handler.Command;

/**
 * Command class to call {@link ServerHandler#validate(ServerType)}.
 * @see Command
 * @author Niko
 * @since 0.0.1
 */
public class ValidateCommand extends Command {

    /**
     * Instantiate this class with the name of a command and with none or specified aliases
     * @since 0.0.1
     */
    public ValidateCommand() {
        super("validate", "validate <serverType>", "Disables expire mode.");
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
            manager.getExpireServers().remove(serverType);
	        log.info("Expire-Mode disabled on {}", serverType);
        } catch (IllegalArgumentException exception) {
            sendIllegalServerType();
        }
    }
}
