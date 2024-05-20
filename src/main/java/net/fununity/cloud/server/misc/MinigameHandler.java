package net.fununity.cloud.server.misc;

import net.fununity.cloud.common.events.EventPriority;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.common.utils.CloudLogger;
import net.fununity.cloud.server.client.listeners.CloudEvents;
import net.fununity.cloud.server.server.Server;
import net.fununity.cloud.server.server.ServerHandler;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler class for minigame lobby mechanics
 * @author Niko
 * @since 0.0.1
 */
public class MinigameHandler {

    public static final CloudLogger LOG = CloudLogger.getLogger(MinigameHandler.class.getSimpleName());
    private static MinigameHandler instance;

    /**
     * Returns a singleton instance of this class
     * @return MinigameHandler - Instance of {@link MinigameHandler}
     */
    public static MinigameHandler getInstance() {
        if (instance == null)
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
            if (servers.isEmpty())
                minigameLobbies.remove(server.getServerType());
            else
                minigameLobbies.put(server.getServerType(), servers);
            LOG.info("Removing minigame lobby: " + server.getServerId());
            if (check) {
                checkToAdd(servers.size(), server.getServerType());
            }
        }
    }

    /**
     * Tries to add a new lobby
     * @param server Server - The Server
     * @since 0.0.1
     */
    public void addLobby(Server server) {
        Set<Server> lobbies = minigameLobbies.getOrDefault(server.getServerType(), new HashSet<>());
        if (!lobbies.contains(server)) {
            lobbies.add(server);
            if (startingServer > 0)
                startingServer--;
            minigameLobbies.put(server.getServerType(), lobbies);
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
     * @see CloudEvents
     * @param event CloudEvent - Event that was sent
     */
    public void receivedStatusUpdate(CloudEvent event) {
        Server server = ServerHandler.getInstance().getServerByIdentifier(event.getData().get(0).toString());
        if (server == null) {
            LOG.error("STATUS_MINIGAME Event was sent with unknown id: " + event.getData().get(0));
            return;
        }

        String state = event.getData().get(2).toString();

        if (state.equalsIgnoreCase("Lobby"))
            addLobby(server);
        else
            removeLobby(server, server.getShutdownProcess() == null || server.getShutdownProcess().needsMinigameCheck());

        server.setMaxPlayers(Integer.parseInt(event.getData().get(5).toString()));
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
     * @param needsMinigameCheck boolean - checks if a new lobby should be created
     * @since 0.0.1
     */
    public void removeServer(Server server, boolean needsMinigameCheck) {
        this.removeLobby(server, needsMinigameCheck);
    }

    /**
     * Get the lobby servers.
     * @return Set<String> - The server id's
     * @since 0.0.1
     */
    public Set<String> getLobbyServers() {
        return new HashMap<>(minigameLobbies).entrySet().stream()
                .flatMap(servers -> servers.getValue().stream().map(Server::getServerId)).collect(Collectors.toSet());
    }
}
