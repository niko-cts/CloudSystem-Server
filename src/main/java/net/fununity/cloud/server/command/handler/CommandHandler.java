package net.fununity.cloud.server.command.handler;

import net.fununity.cloud.server.command.*;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandHandler {

    private final List<Command> commandList;
    private final Logger log;

    /**
     * Instantiates the class with a Logger.
     * Will be instantiated from {@link CloudConsole}.
     * @param logger Logger - Logger to output warnings or infos
     * @since 0.0.1
     */
    public CommandHandler(Logger logger) {
        this.commandList = Arrays.asList(new HelpCommand(), new ServerTypeCommand(), new ListCommand(), new StopCommand(),
                new RestartCommand(), new InfoCommand(), new StartCommand(), new ExpireCommand(), new ValidateCommand(), new ExitCommand());
        this.log = logger;

    }

    /**
     * Will be called from {@link CloudConsole}, when user executes a command.
     * @param args String[] - All arguments
     * @since 0.0.1
     */
    public void tryToExecuteCommand(String[] args) {
        String cmd = args[0];
        Command command = commandList.stream().filter(c -> c.getName().equalsIgnoreCase(cmd) ||
                Arrays.stream(c.getAliases()).filter(a -> a.equalsIgnoreCase(cmd)).findFirst().orElse(null) != null).findFirst().orElse(null);
        if (command == null) {
            log.warn("No command found. Type 'help' for help.");
            return;
        }
        try {
            String[] arguments = new String[args.length - 1];
            for (int i = 1; i < args.length; i++)
                arguments[i - 1] = args[i];
            command.execute(arguments);
        } catch (Exception exception) {
            log.warn("Exception while executing command " + command.getName() + ": " + exception.getMessage());
        }
    }

    /**
     * Get a copied list of the commands.
     * @return List<Command> - List of all commands
     */
    public List<Command> getCommands() {
        return new ArrayList<>(this.commandList);
    }
}
