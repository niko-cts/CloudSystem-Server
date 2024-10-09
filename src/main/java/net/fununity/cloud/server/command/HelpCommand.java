package net.fununity.cloud.server.command;

import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.command.handler.Command;

public class HelpCommand extends Command {

    /**
     * Instantiate this class with the name of a command and with no or x aliases
     * @since 0.0.1
     */
    public HelpCommand() {
        super("help", "help", "Shows all commands.","hilfe");
    }

    /**
     * Will be called, when the user typed in the command name or aliase.
     * @param args String[] - The arguments behind the command
     * @since 0.0.1
     */
    @Override
    public void execute(String[] args) {
        log.info("All Commands listed below:");
        for (Command command : CloudServer.getInstance().getCloudConsole().getCommandHandler().getCommands()) {
           log.info("{} - {}", command.getName(), command.getDescription());
        }
    }
}
