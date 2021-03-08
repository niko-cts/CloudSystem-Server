package net.fununity.cloud.server.command;

import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.command.handler.Command;

import java.util.Arrays;

public class ServerTypeCommand extends Command {

    /**
     * Instantiate this class with the name of a command and with no or x aliases
     * @since 0.0.1
     */
    public ServerTypeCommand() {
        super("servertype", "servertype", "Displays all Servertypes", "types", "servertypes");
    }

    /**
     * Will be called, when the user typed in the command name or aliase.
     * @param args String[] - The arguments behind the command
     * @since 0.0.1
     */
    @Override
    public void execute(String[] args) {
        log.info("All ServerTypes: " + Arrays.toString(ServerType.values()));
    }
}
