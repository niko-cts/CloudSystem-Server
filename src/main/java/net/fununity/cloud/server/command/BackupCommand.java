package net.fununity.cloud.server.command;

import net.fununity.cloud.server.command.handler.Command;
import net.fununity.cloud.server.misc.ServerHandler;
import net.fununity.cloud.server.server.Server;

/**
 * The backup command of the cloud.
 * @see net.fununity.cloud.server.server.Server
 * @author Niko
 * @since 0.0.1
 */
public class BackupCommand extends Command {

    /**
     * Instantiate this class with the name of a command and with none or specified aliases
     * @since 0.0.1
     */
    public BackupCommand() {
        super("backup", "backup <server>", "Backs up a whole server. (Doesnt save world)");
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
        Server server = ServerHandler.getInstance().getServerByIdentifier(args[0]);
        if(server == null) {
            sendIllegalServerId(args[0]);
            return;
        }
        server.createBackup();
    }
}
