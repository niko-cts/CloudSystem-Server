package net.fununity.cloud.server.command;

import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.command.handler.Command;

public class ExitCommand extends Command {

    /**
     * Instantiate this class with the name of a command and with none or specified aliases
     * @since 0.0.1
     */
    public ExitCommand() {
        super("exit", "exit", "Stops the network and the cloud");
    }

    /**
     * Will be called, when the user typed in the command name or aliase.
     * @param args String[] - The arguments behind the command
     * @since 0.0.1
     */
    @Override
    public void execute(String[] args) {
        CloudServer.getInstance().shutdownEverything();
    }
}
