package net.fununity.cloud.server.server;

import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.server.ServerDefinition;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.misc.MinigameHandler;
import net.fununity.cloud.server.netty.ClientHandler;
import net.fununity.cloud.server.server.shutdown.ServerShutdown;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Handler class for all server mechanics and server util methods.
 *
 * @author Niko
 * @since 0.0.1
 */
@Slf4j
public class ServerHandler {

    public static final int MAX_RAM = 35200;
    private static ServerHandler instance;

    /**
     * Gets the instance of the singleton ServerHandler.
     *
     * @return ServerHandler - the server handler.
     * @since 0.0.1
     */
    public static ServerHandler getInstance() {
        if (instance == null)
            instance = new ServerHandler();
        return instance;
    }


    private final ClientHandler clientHandler;
    private final List<Server> servers;
    private final Queue<Server> startQueue;
    private final Queue<Server> stopQueue;
    private final Set<ServerType> expireServers;
    private final AtomicInteger networkCount;

    /**
     * Default constructor of the server handler.
     *
     * @since 0.0.1
     */
    private ServerHandler() {
        instance = this;

        this.clientHandler = ClientHandler.getInstance();
        // thread safe collections
        this.servers = new CopyOnWriteArrayList<>();
        this.stopQueue = new ConcurrentLinkedQueue<>();
        this.startQueue = new ConcurrentLinkedQueue<>();
        this.expireServers = new CopyOnWriteArraySet<>();
        this.networkCount = new AtomicInteger(0);
    }


    /**
     * Removes a server from the list.
     *
     * @param server Server - the server to remove
     * @since 1.0
     */
    public void removeServer(Server server) {
        this.clientHandler.removeClient(server.getServerId());
        this.servers.remove(server);
    }

    /**
     * Adds a server to the server array.
     *
     * @param server Server - the server.
     * @see Server
     * @since 0.0.1
     */
    public void addServer(Server server) {
        if (!this.servers.contains(server)) {
            this.servers.add(server);
            this.addToStartQueue(server);
        }
    }

    /**
     * Gets a server by its identifier.
     *
     * @param identifier String - the identifier.
     * @return Server or null.
     * @see Server
     * @since 0.0.1
     */
    public Server getServerByIdentifier(String identifier) {
        return getServers().stream().filter(server -> server.getServerId().equals(identifier)).findFirst().orElse(null);
    }

    /**
     * Gets the server identifier of the server with the given port.
     *
     * @param port int - the port of the server.
     * @return String - the identifier of the server.
     * @since 0.0.1
     */
    public String getServerIdentifierByPort(int port) {
        return getServers().stream().filter(s -> s.getServerPort() == port)
                .findFirst().map(Server::getServerId)
                .orElseGet(() -> {
                    log.warn("Port {} was not found in the server list, but was requested! All Servers: {}", port,
                            getServers().stream().map(s -> s.getServerId() + ":" + s.getServerPort()).collect(Collectors.joining(", ")));
                    return null;
                });
    }


    /**
     * Get the best free port for the given server type.
     *
     * @return int - the next free port.
     * @since 1.0
     */
    public int getNextFreeServerPort() {
        return getNextFreeServerPort(25565);
    }

    /**
     * Returns the next free not used port considering all known servers.
     *
     * @param port int - the port to start searching.
     * @return int - the highest port.
     * @since 0.0.1
     */
    private int getNextFreeServerPort(int port) {
        if (getAllServers().stream().noneMatch(s -> s.getServerPort() == port)) {
            ServerSocket ss = null;
            DatagramSocket ds = null;
            try {
                ss = new ServerSocket(port);
                ss.setReuseAddress(true);
                ds = new DatagramSocket(port);
                ds.setReuseAddress(true);
                return port;
            } catch (IOException ignored) {
            } finally {
                if (ds != null) {
                    ds.close();
                }
                if (ss != null) {
                    try {
                        ss.close();
                    } catch (IOException e) {
                        /* should not be thrown */
                    }
                }
            }
        }

        return getNextFreeServerPort(port + 1);
    }


    /**
     * Get the ram that is being used from the server
     *
     * @return int - the ram currently used
     * @since 0.0.1
     */
    public int getCurrentRamUsed() {
        return getAllServers().stream().mapToInt(server -> server.getConfig().getRam()).sum();
    }

    /**
     * Shuts the server with the given server id down.
     *
     * @param server        Server - The server.
     * @param minigameCheck boolean
     * @since 0.0.1
     */
    public void shutdownServer(Server server, boolean minigameCheck) {
        shutdownServer(server, new ServerShutdown() {
            @Override
            public void serverStopped() {
            }

            @Override
            public boolean needsMinigameCheck() {
                return minigameCheck;
            }
        });
    }

    /**
     * Sends a remove request to the bungeecord server
     *
     * @param server         Server - The server.
     * @param serverShutdown {@link ServerShutdown} - Abstract class which will be called, when remove confirmation was sent.
     * @since 0.0.1
     */
    public void shutdownServer(Server server, ServerShutdown serverShutdown) {
        if (server != null) {
            log.debug("Init shutdown server '{}'", server.getServerId());
            this.stopQueue.add(server);
            server.setShutdownProcess(serverShutdown);
            server.stop();
        }
    }

    /**
     * Restarts a specified server.
     *
     * @param server {@link Server} - the server.
     * @since 0.0.1
     */
    public void restartServer(Server server) {
        log.info("Try to restart server: {}", server.getServerId());
        shutdownServer(server, () -> createServerByServerType(server.getServerType()));
    }

    /**
     * Restarts all servers of the given server type.
     *
     * @param serverType ServerType - the server type.
     * @since 0.0.1
     */
    public void restartAllServersOfType(ServerType serverType) {
        log.info("Try to restart all servers of type {}", serverType);
        getActiveServersByType(serverType).forEach(this::restartServer);
    }

    /**
     * Generate a ServerDefinition for a server with the given port.
     *
     * @param port int - the port of the server.
     * @return ServerDefinition - the definition of the server.
     * @since 0.0.1
     */
    public ServerDefinition getServerDefinitionByPort(int port) {
        Server server = getServerByIdentifier(getServerIdentifierByPort(port));
        if (server != null) {
            return new ServerDefinition(server.getServerId(), server.getServerIp(), server.getServerMaxRam(), server.getServerMotd(), server.getMaxPlayers(), server.getPlayerCount(), port, server.getServerType(), server.getServerState());
        }
        return null;
    }

    /**
     * Create a server with the given server type.
     *
     * @param serverType ServerType - the type of the server.
     * @since 0.0.1
     */
    public void createServerByServerType(ServerType serverType) {
        if (expireServers.contains(serverType)) {
            log.warn(serverType + " was tried to start, but is in expire mode!");
            return;
        }

        List<Server> serversWithSameType = getAllServers().stream()
                .filter(s -> s.getServerType() == serverType)
                .sorted(Comparator.comparingInt(server -> Integer.parseInt(server.getServerId().replaceAll("[^\\d.]", "")))).toList();

        int nextNumber = 1;
        for (Server server : serversWithSameType) {
            if (nextNumber == Integer.parseInt(server.getServerId().replaceAll("[^\\d.]", ""))) {
                nextNumber++;
            }
        }

        String serverId = serverType.getServerId();
        if (nextNumber < 10)
            serverId += "0" + nextNumber;
        else
            serverId += nextNumber;

        log.debug("Create new server '{}' by type '{}'", serverId, serverType.name());
        addServer(new Server(serverId, "127.0.0.1", serverType));
    }

    /**
     * Shuts down all servers of the given server type.
     *
     * @param type          ServerType - the server type.
     * @param logfilePrefix String - the prefix if a log file should be copied
     * @since 0.0.1
     */
    public void shutdownAllServersOfType(ServerType type, String logfilePrefix) {
        boolean firstServerOfTypeInQueue = !startQueue.isEmpty() && startQueue.peek().getServerType() == type;
        startQueue.removeIf(s -> s.getServerType() == type);
        if (firstServerOfTypeInQueue && !startQueue.isEmpty())
            startServer(startQueue.peek());

        for (Server server : getActiveServersByType(type)) {
            server.setSavelogFile(logfilePrefix);
            shutdownServer(server, () -> {});
        }
    }


    /**
     * Shuts down all servers.
     *
     * @since 0.0.1
     */
    public void shutdownAllServers(String savelogFile) {
        ServerShutdown serverShutdown = () -> {
            if (getServers().size() == getBungeeServers().size())
                shutdownAllServersOfType(ServerType.BUNGEECORD, savelogFile);
        };
        this.startQueue.clear();
        for (Server server : getServers()) {
            if (server.getServerType() != ServerType.BUNGEECORD) {
                server.setSavelogFile(savelogFile);
                shutdownServer(server, serverShutdown);
            }
        }
    }

    /**
     * Shuts down all servers and then the cloud
     *
     * @since 0.0.1
     */
    public void exitCloud() {
        this.startQueue.clear();
        getServers().stream().filter(s -> s.getServerType() != ServerType.BUNGEECORD).findFirst()
                .ifPresent(s -> shutdownServer(s, () -> CloudServer.getInstance().shutdownEverything()));
        if (getServers().stream().noneMatch(s -> s.getServerType() != ServerType.BUNGEECORD)) {
            log.debug("Only BungeeCordServers open, disconnecting those...");
            getBungeeServers().stream().findFirst()
                    .ifPresent(b -> shutdownServer(b, () -> CloudServer.getInstance().shutdownEverything()));
        }
    }

    public void removeStopQueue(Server server) {
        this.stopQueue.remove(server);
    }

    /**
     * Will do something to the server when a specific type of servers shutdowns
     */
    public void actionWhenServerTypeShutdowns(Server server) {
        switch (server.getServerType()) {
            case LOBBY -> ClientHandler.getInstance().sendLobbyInformationToLobbies();
            case COCATTACK -> ClientHandler.getInstance().sendCocAttackServerAmount();
            default -> MinigameHandler.getInstance()
                    .removeServer(server, server.getShutdownProcess() != null && server.getShutdownProcess().needsMinigameCheck());
        }
    }

    /**
     * Gets the serverId of the lobby with the lowest player count.
     *
     * @param blacklist Server[] - Server, which cannot be taken.
     * @return Server - the server of the lobby.
     * @since 0.0.1
     */
    public Server getSuitableLobby(Server... blacklist) {
        List<Server> blacklistServers = Arrays.asList(blacklist);
        return getLobbyServers().stream()
                .filter(server -> !blacklistServers.contains(server))
                .filter(server -> server.getPlayerCount() + 1 < server.getMaxPlayers())
                .max(Comparator.comparing(Server::getPlayerCount)).orElse(null);
    }

    /**
     * Sends player to a lobby.
     *
     * @param blackListLobbies List<Server> - The lobbies the player cannot be sent
     * @param sendingPlayers   Queue<UUID> - A queue of players which should be sent.
     * @since 0.0.1
     */
    public void sendPlayerToLobby(List<Server> blackListLobbies, Queue<UUID> sendingPlayers) {
        Server lobby = getSuitableLobby(blackListLobbies.toArray(new Server[0]));

        if (lobby == null) {
            log.warn("No suitable lobby to send player!");
            return;
        }

        CloudEvent event = new CloudEvent(CloudEvent.BUNGEE_SEND_PLAYER).addData(lobby.getServerId());

        int canBeMoved = lobby.getMaxPlayers() - lobby.getPlayerCount() - 1;
        for (int i = 0; i < sendingPlayers.size() && i < canBeMoved; i++)
            event.addData(sendingPlayers.poll());

        sendToBungeeCord(event);

        if (!sendingPlayers.isEmpty()) {
            blackListLobbies.add(lobby);
            sendPlayerToLobby(blackListLobbies, sendingPlayers);
        }
    }

    /**
     * Sends the given cloud event to all bungeecord servers.
     *
     * @param event CloudEvent - the cloud event to send.
     * @since 0.0.1
     */
    public void sendToBungeeCord(CloudEvent event) {
        getBungeeServers().forEach(s -> this.clientHandler.sendEvent(s, event));
    }

    /**
     * Adds a server to the start queue.
     *
     * @param server Server - the server to add.
     * @since 0.0.1
     */
    public void addToStartQueue(Server server) {
        this.startQueue.add(server);
        if (startQueue.size() == 1) {
            startServer(server);
        }
    }

    /**
     * Checks if a server is in the start queue.
     * If yes: remove it and start the next server.
     *
     * @param server Server - the server to be checked.
     * @since 0.0.1
     */
    public void checkStartQueue(Server server) {
        if (this.startQueue.contains(server)) {
            boolean top = this.startQueue.peek().getServerId().equals(server.getServerId());
            this.startQueue.remove(server);
            log.debug("Server {} was removed from start queue", server.getServerId());
            if (!this.startQueue.isEmpty() && top) {
                startServer(this.startQueue.peek());
            }
        }
    }

    public void startServer(Server server) {
        log.debug("Try to start server " + server.getServerId());
        try {
            log.debug("Creating server files for {}...", server.getServerId());
            server.createFiles();
        } catch (IOException exception) {
            log.error("Server directory for {} could not be created: {}", server.getServerId(), exception.getMessage());
            removeServer(server);
            checkStartQueue(server);
            return;
        }

        try {
            log.debug("Setting server properties for {}...", server.getServerId());
            server.setFileServerProperties();
        } catch (IOException e) {
            log.error("Could not set properties for server {}: {}", server.getServerId(), e.getMessage());
            server.deleteServer();
            checkStartQueue(server);
            return;
        }

        try {
            server.start();
            log.info("Server {} started.", server.getServerId());
        } catch (IllegalStateException exception) {
            log.error("Could not start server {}: {}", server.getServerId(), exception.getMessage());
            server.deleteServer();
            checkStartQueue(server);
        }
    }

    /**
     * Gets a list of all registered bungeecord servers.
     *
     * @return List<Server> - the list of bungeecord servers.
     * @since 0.0.1
     */
    public List<Server> getBungeeServers() {
        return getActiveServersByType(ServerType.BUNGEECORD);
    }


    /**
     * Returns a list containing all lobby servers.
     *
     * @return List<Server> - all lobby servers.
     * @since 0.0.1
     */
    public List<Server> getLobbyServers() {
        return getActiveServersByType(ServerType.LOBBY);
    }

    /**
     * Returns a list containing all servers with specified type.
     *
     * @param serverType {@link ServerType} - The type of server.
     * @return List<Server> - all servers with specified type.
     * @since 0.0.1
     */
    public List<Server> getServersByType(ServerType serverType) {
        return this.getServers().stream().filter(s -> s.getServerType() == serverType).toList();
    }

    public List<Server> getActiveServersByType(ServerType serverType) {
        return this.getServers().stream()
                .filter(s -> s.getServerType() == serverType)
                .filter(s -> !this.startQueue.contains(s))
                .filter(s -> !this.stopQueue.contains(s)).collect(Collectors.toList()); // mutable
    }

    /**
     * Finds the server by server identifier and executes the playerJoined() method in specified Server.
     *
     * @param serverID    String - Identifier of Server
     * @param playerCount int - The number of players on the server
     * @see Server#setPlayerCount(int)
     * @since 0.0.1
     */
    public void setPlayerCountFromServer(String serverID, int playerCount) {
        Server server = getServerByIdentifier(serverID);
        if (server == null) {
            return;
        }

        server.setPlayerCount(playerCount);

        if (server.getServerType() == ServerType.LOBBY) {
            List<Server> lobbyServers = getLobbyServers();
            if (playerCount == 0 && lobbyServers.size() > 1 &&
                getPlayerCountOfNetwork() + server.getMaxPlayers() + 5 < lobbyServers.stream().mapToInt(Server::getMaxPlayers).sum()) {
                shutdownServer(server, false);
            } else {
                ClientHandler.getInstance().sendLobbyInformationToLobbies();
            }
        }
    }

    /**
     * Sets the number of players on the network
     *
     * @param count int - Number of players on the network
     * @since 0.0.1
     */
    public void setPlayerCountOfNetwork(int count) {
        this.networkCount.set(count);
        if (this.networkCount.get() + 5 > getLobbyServers().stream().mapToInt(Server::getMaxPlayers).sum()) {
            createServerByServerType(ServerType.LOBBY);
        }
    }

    /**
     * Returns the number of players on the network
     *
     * @return int - Number of players on the network
     * @since 0.0.1
     */
    public int getPlayerCountOfNetwork() {
        return this.networkCount.get();
    }

    /**
     * Returns the number of players on a specific server
     *
     * @param serverId String - Identifier of server
     * @return int - Number of players on server
     * @see Server#getPlayerCount()
     * @since 0.0.1
     */
    public int getPlayerCountOfServer(String serverId) {
        Server server = getServerByIdentifier(serverId);
        if (server == null) {
            return -1;
        }
        return server.getPlayerCount();
    }

    /**
     * Returns the number of players on a specific server type
     *
     * @param serverType ServerType - Type of servers
     * @return int - number of all players on a specific ServerType
     * @see Server#getPlayerCount()
     * @since 0.0.1
     */
    public int getPlayerCountOfServerType(ServerType serverType) {
        return getActiveServersByType(serverType).stream().mapToInt(Server::getPlayerCount).sum();
    }

    /**
     * Enables expire mode for this server type.
     * When enabled, no more servers with this type will start.
     *
     * @param serverType {@link ServerType} - The type of server
     * @since 0.0.1
     */
    public void expire(ServerType serverType) {
        this.expireServers.add(serverType);
    }

    /**
     * Disables expire mode for this server type.
     * When enabled, no more servers with this type will start.
     *
     * @param serverType {@link ServerType} - The type of server
     * @since 0.0.1
     */
    public void validate(ServerType serverType) {
        this.expireServers.remove(serverType);
    }


    /**
     * Gets the list of known servers.
     *
     * @return List<Server> - the list of servers.
     * @since 0.0.1
     */
    public List<Server> getServers() {
        return new ArrayList<>(this.servers);
    }

    /**
     * Get all servers running servers and queued.
     *
     * @return List<Server> - all servers.
     * @since 1.0
     */
    private List<Server> getAllServers() {
        List<Server> servers = getServers();
        servers.addAll(this.startQueue);
        servers.addAll(this.stopQueue);
        return servers;
    }

    public Queue<Server> getStartQueue() {
        return new LinkedList<>(startQueue);
    }
}
