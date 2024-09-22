package net.fununity.cloud.server.command;

import net.fununity.cloud.server.command.handler.Command;

/**
 * Command class to remove a server, when crashed.
 * @see Command
 * @author Niko
 * @since 0.0.1
 */
public class RemoveServerCommand extends Command {

    /**
     * Instantiate this class with the name of a command and with none or specified aliases
     * @since 0.0.1
     */
    public RemoveServerCommand() {
        super("removeserver", "removeserver <serverId>", "Will flush the server from system. Won't stop the server! Should only be used, when server crashed.", "flush");
    }

    /**
     * Will be called when the user typed in the command name or aliase.
     * @param args String[] - The arguments behind the command
     * @since 0.0.1
     */
    @Override
    public void execute(String[] args) {
        if (args.length != 1) {
            sendCommandUsage();
            return;
        }

        getServerIdOrSendIllegal(args[0], server -> {
            log.info("Command successfully!");
            server.flushServer();
        });
    }
}
