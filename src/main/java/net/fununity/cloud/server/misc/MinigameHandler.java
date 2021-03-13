package net.fununity.cloud.server.misc;

import net.fununity.cloud.common.events.EventPriority;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.server.Server;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.util.*;

/**
 * Handler class for minigame lobby mechanics
 * @author Niko
 * @since 0.0.1
 */
public class MinigameHandler {

    public static final Logger LOG = Logger.getLogger(MinigameHandler.class.getSimpleName());
    private static MinigameHandler instance;

    /**
     * Returns a singleton instance of this class
     * @return MinigameHandler - Instance of {@link MinigameHandler}
     */
    public static MinigameHandler getInstance() {
        if(instance == null)
            instance = new MinigameHandler();
        return instance;
    }

    private final Map<ServerType, Set<Server>> minigameLobbies;
    private int startingServer;

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
        this.startingServer = 0;
    }

    /**
     * Removes a server that left the lobby state
     * @param server Server - The Server
     * @param check boolean - should check for a new lobby
     * @since 0.0.1
     */
    public void removeLobby(Server server, boolean check) {
        Set<Server> servers = minigameLobbies.getOrDefault(server.getServerType(), new HashSet<>());
        if (servers.contains(server)) {
            servers.remove(server);
            minigameLobbies.put(server.getServerType(), servers);
            if (check)
                checkToAdd(servers.size(), server.getServerType());
        }
    }

    /**
     * Tries to add a new lobby
     * @param server Server - The Server
     * @since 0.0.1
     */
    public void addLobby(Server server) {
        Set<Server> lobbies = minigameLobbies.getOrDefault(server.getServerType(), new HashSet<>());
        if(!lobbies.contains(server)) {
            lobbies.add(server);
            if(startingServer > 0)
                startingServer--;
            minigameLobbies.put(server.getServerType(), lobbies);
            checkToAdd(lobbies.size(), server.getServerType());
        }
    }

    /**
     * Checks if a server needs to be added
     * @param lobbies int - amount of lobbies
     * @param serverType {@link ServerType} - The server type
     * @since 0.0.1
     */
    private void checkToAdd(int lobbies, ServerType serverType) {
        if (ServerHandler.getInstance().getCurrentRamUsed() > ServerHandler.MAX_RAM)
            return;

        if (lobbies + startingServer < 3) {
            startingServer++;
            ServerHandler.getInstance().createServerByServerType(serverType);
        }
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

        String state = event.getData().get(2).toString();

        if(state.equalsIgnoreCase("Lobby"))
            addLobby(server);
        else
            removeLobby(server, true);

        int maxPlayers = Integer.parseInt(event.getData().get(5).toString());
        server.setMaxPlayers(maxPlayers);

        CloudServer.getLogger().info("Received minigame update event " + event.getData());
    }

    /**
     * Sends a player to a minigame lobby
     * @param serverType {@link ServerType} - The server type to send
     * @param uuid UUID - the uuid of the player
     * @since 0.0.1
     */
    public void sendPlayerToMinigameLobby(ServerType serverType, UUID uuid) {
        minigameLobbies.getOrDefault(serverType, new HashSet<>()).stream()
                .filter(server -> server.getPlayerCount() < server.getMaxPlayers())
                .max(Comparator.comparing(Server::getPlayerCount))
                .ifPresent(server -> ServerHandler.getInstance()
                        .sendToBungeeCord(new CloudEvent(CloudEvent.BUNGEE_SEND_PLAYER).addData(server.getServerId()).addData(uuid).setEventPriority(EventPriority.HIGH)));
    }

    /**
     * Calls {@link MinigameHandler#removeLobby(Server, boolean)}
     * @param server Server - the Server.
     * @param check boolean - checks if a new lobby should be created
     * @since 0.0.1
     */
    public void removeServer(Server server, boolean check) {
        this.removeLobby(server, check);
    }

    /**
     * Get the lobby servers.
     * @return Set<String> - The server id's
     * @since 0.0.1
     */
    public Set<String> getLobbyServers() {
        Set<String> serverId = new HashSet<>();
        for (Map.Entry<ServerType, Set<Server>> entry : minigameLobbies.entrySet()) {
            for (Server server : entry.getValue()) {
                serverId.add(server.getServerId());
            }
        }
        return serverId;
    }
}
