package net.fununity.cloud.server.misc;

import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.common.utils.CloudLogger;
import net.fununity.cloud.server.server.ServerHandler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Singleton class for handling the cloud configuration.
 * Used to store/retrieve default servers.
 *
 * @author Niko
 * @since 0.0.1
 */
public class ConfigHandler {

    private static final CloudLogger LOG = CloudLogger.getLogger(ConfigHandler.class.getSimpleName());
    private static ConfigHandler instance;

    /**
     * Gets the instance of the singelton.
     *
     * @return ConfigHandler - the handler.
     * @since 0.0.1
     */
    public static ConfigHandler getInstance() {
        if (instance == null)
            instance = new ConfigHandler();
        return instance;
    }


    public static void createInstance(String[] args) {
        instance = new ConfigHandler(args);
    }

    private Path configPath;

    private ConfigHandler(String... args) {
        LOG.debug("Boot up ConfigHandler...");
        try {
            File configFile = new File("config.txt");
            if (!configFile.exists() && configFile.createNewFile())
                this.loadDefaultConfiguration(configFile.toPath());

            this.configPath = configFile.toPath();

            if (args.length == 0 || args[0].equalsIgnoreCase("default"))
                this.loadDefaultServers();
            else if (!args[0].equalsIgnoreCase("nodefaults")) {
                for (String arg : args) {
                    try {
                        ServerHandler.getInstance().createServerByServerType(ServerType.valueOf(arg));
                    } catch (IllegalArgumentException exception) {
                        LOG.error("Could not start server, illegal : " + arg);
                    }
                }
            }
        } catch (IOException e) {
            LOG.warn(e.getMessage());
        }
    }


    /**
     * Saves the default configuration to the file.
     *
     * @since 0.0.1
     */
    private void loadDefaultConfiguration(Path path) {
        try {
            Files.write(path, """
                    BUNGEECORD
                    LOBBY
                    """.getBytes());
        } catch (IOException exception) {
            LOG.error("Could not save default config: " + exception.getMessage());
        }
    }

    /**
     * Loads the default servers from the config and adds the server to the cloud.
     *
     * @since 0.0.1
     */
    public void loadDefaultServers() {
        LOG.info("Starting default servers...");
        StringBuilder builder = new StringBuilder();
        try {
            List<String> servers = Files.readAllLines(configPath);

            for (String server : servers) {
                String[] serverAmount = server.split(" ");
                try {
                    ServerType serverType = ServerType.valueOf(serverAmount[0]);
                    int amount = serverAmount.length > 1 ? Integer.parseInt(serverAmount[1]) : 1;
                    for (int i = 0; i < amount; i++)
                        ServerHandler.getInstance().createServerByServerType(serverType);

                    builder.append(serverType).append(": ").append(amount).append(", ");
                } catch (NumberFormatException exception) {
                    LOG.error("Illegal number given %s in default config for servertype %s: %s", serverAmount[1], serverAmount[0], exception.getMessage());
                } catch (IllegalArgumentException exception) {
                    LOG.error("Illegal ServerType %s in default config given: %s", serverAmount[0], exception.getMessage());
                }
            }
        } catch (IOException exception) {
            LOG.error("Could not load config: " + exception.getMessage());
            return;
        }
        LOG.info("Server started: " + builder);
    }
}
