package net.fununity.cloud.server.misc;

import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.server.Server;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Handler class for minigame lobby mechanics
 * @author Niko
 * @since 0.0.1
 */
public class MinigameHandler {

    public static final Logger LOG = Logger.getLogger(MinigameHandler.class.getSimpleName());
    private static MinigameHandler instance;

    private final Set<Server> minigames;
    private final Map<ServerType, Integer> minigameLobbies;

    /**
     * Default constructor of the minigame handler.
     * @since 0.0.1
     */
    private MinigameHandler() {
        instance = this;
        PatternLayout layout = new PatternLayout("[%d{HH:mm:ss}] %c{1} [%p]: %m%n");
        LOG.addAppender(new ConsoleAppender(layout));
        LOG.setLevel(Level.INFO);
        LOG.setAdditivity(false);
        this.minigameLobbies = new EnumMap<>(ServerType.class);
        this.minigames = new HashSet<>();
    }

    /**
     * Removes a lobby of a server type and starts a new one if it drops below a defined amount
     * @param serverType ServerType - The type of Server
     */
    public void removeLobby(ServerType serverType) {
        int lobbies = minigameLobbies.getOrDefault(serverType, 1) - 1;
        minigameLobbies.put(serverType, lobbies);
        checkToCreate(serverType, lobbies);
    }

    /**
     * Adds a lobby to a serverType
     * @param serverType ServerType - The type of Server
     */
    public void addLobby(ServerType serverType) {
        int lobbies = minigameLobbies.getOrDefault(serverType, 0) + 1;
        minigameLobbies.put(serverType, lobbies);
    }

    /**
     * Checks if the server should create another server
     * @param lobbies int - amount of lobbies
     */
    private void checkToCreate(ServerType serverType, int lobbies) {
        if(lobbies < 2) {
            CloudServer.getLogger().info("Creating a new " + serverType.name() + " because there are only " + lobbies + " lobbies");
            ServerHandler.getInstance().createServerByServerType(serverType);
        }
    }

    /**
     * Returns a singleton instance of this class
     * @return MinigameHandler - Instance of {@link MinigameHandler}
     */
    public static MinigameHandler getInstance() {
        if(instance == null)
            instance = new MinigameHandler();
        return instance;
    }

    /**
     * Called when a Minigame sent STATUS_MINIGAME event.
     * Will check if a new lobby registered
     * @see net.fununity.cloud.server.listeners.cloud.CloudEvents
     * @param event CloudEvent - Event that was sent
     */
    public void receivedStatusUpdate(CloudEvent event) {
        Server server = ServerHandler.getInstance().getServerByIdentifier(event.getData().get(0).toString());
        if(server == null) {
            CloudServer.getLogger().warn("STATUS_MINIGAME Event was sent with unknown id: " + event.getData().get(0));
            return;
        }

        String state = event.getData().get(3).toString();

        if(!minigames.contains(server) && state.equalsIgnoreCase("Lobby")) {
            addLobby(server.getServerType());
        } else if(minigames.contains(server) && !state.equalsIgnoreCase("Lobby")) {
            removeLobby(server.getServerType());
        }

        minigames.add(server);
    }

    public void removeServer(Server server) {
        this.minigames.remove(server);
    }
}
