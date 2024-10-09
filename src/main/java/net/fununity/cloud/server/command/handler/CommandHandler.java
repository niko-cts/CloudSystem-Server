package net.fununity.cloud.server.command.handler;

import net.fununity.cloud.server.command.*;
import org.slf4j.Logger;

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
        this.commandList = Arrays.asList(new HelpCommand(), new ServerTypeCommand(), new ListCommand(), new StopCommand(), new BackupCommand(),
                new RestartCommand(), new InfoCommand(), new StartCommand(), new ExpireCommand(), new ValidateCommand(), new ExitCommand());
        this.log = logger;
        log.debug("Registered {} commands.", commandList.size());
    }

    /**
     * Will be called from {@link CloudConsole}, when user executes a command.
     * @param args String[] - All arguments
     * @since 0.0.1
     */
    public void tryToExecuteCommand(String[] args) {
        String cmd = args[0];
        log.debug("Console command: {}", cmd);
        Command command = commandList.stream().filter(c -> c.getName().equalsIgnoreCase(cmd) ||
                Arrays.stream(c.getAliases()).filter(a -> a.equalsIgnoreCase(cmd)).findFirst().orElse(null) != null).findFirst().orElse(null);
        if (command == null) {
            log.warn("No command found for '{}'. Type 'help' for help.", cmd);
            return;
        }

        try {
            String[] arguments = new String[args.length - 1];
            System.arraycopy(args, 1, arguments, 0, args.length - 1);
            command.execute(arguments);
        } catch (Exception exception) {
	        log.warn(String.format("Exception while executing command %s:", command.getName()), exception);
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
