package net.fununity.cloud.server.misc;

import io.netty.channel.ChannelHandlerContext;
import net.fununity.cloud.common.events.EventPriority;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.server.ServerDefinition;
import net.fununity.cloud.common.server.ServerState;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.server.Server;
import net.fununity.cloud.server.server.ServerDeleter;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler class for all server mechanics and server util methods.
 * @author Marco Hajek
 * @since 0.0.1
 */
public class ServerHandler {

    public static final Logger LOG = Logger.getLogger(ServerHandler.class.getName());
    public static final int MAX_RAM = 35200;
    private static ServerHandler instance;

    private final ClientHandler clientHandler;
    private final List<Server> servers;
    private final Queue<Server> startQueue;
    private final Queue<Server> serverDeleteQueue;
    private final Set<ServerType> expireServers;
    private int networkCount;

    /**
     * Default constructor of the server handler.
     * @since 0.0.1
     */
    private ServerHandler() {
        instance = this;
        PatternLayout layout = new PatternLayout("[%d{HH:mm:ss}] %c{1} [%p]: %m%n");
        LOG.addAppender(new ConsoleAppender(layout));
        LOG.setLevel(Level.INFO);
        LOG.setAdditivity(false);

        this.clientHandler = ClientHandler.getInstance();
        this.servers = new ArrayList<>();
        this.startQueue = new LinkedList<>();
        this.serverDeleteQueue = new LinkedList<>();
        this.expireServers = new HashSet<>();
        this.networkCount = 0;
    }

    /**
     * Gets the instance of the singleton ServerHandler.
     * @return ServerHandler - the server handler.
     * @since 0.0.1
     */
    public static ServerHandler getInstance() {
        if (instance == null)
            instance = new ServerHandler();
        return instance;
    }

    /**
     * Gets the list of known servers.
     * @return List<Server> - the list of servers.
     * @since 0.0.1
     */
    public List<Server> getServers() {
        return new ArrayList<>(this.servers);
    }

    /**
     * Removes a server from the list.
     * @param server Server - the server to remove
     * @since 1.0
     */
    public void removeServer(Server server) {
        this.servers.remove(server);
    }

    /**
     * Adds a server to the servers array.
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
     * Sets the server state of the server with the given serverId to IDLE.
     * @param serverId String - the id of the server.
     * @see ServerState
     * @see Server
     * @since 0.0.1
     */
    public void setServerIdle(String serverId) {
        for (Server server : this.servers) {
            if (server.getServerId().equalsIgnoreCase(serverId)) {
                server.setServerState(ServerState.IDLE);
                createNewServerIfNeeded(server);
            }
        }
    }

    /**
     * Checks if a new server with the server type of the given server is needed.
     * @param oldServer Server - the old server.
     * @see Server
     * @since 0.0.1
     */
    private void createNewServerIfNeeded(Server oldServer) {
        int idleServers = 0;
        for (Server server : this.servers) {
            if (server != oldServer && server.getServerType() == oldServer.getServerType() && server.getServerState() == ServerState.IDLE)
                idleServers += 1;
        }

        if (idleServers < 1)
            this.addServer(new Server(RandomStringUtils.random(16), oldServer.getServerIp(), oldServer.getServerMotd(), oldServer.getServerMotd(), oldServer.getMaxPlayers(), oldServer.getServerType()));
    }

    /**
     * Gets a server by its identifier.
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
     * @param port int - the port of the server.
     * @return String - the identifier of the server.
     * @since 0.0.1
     */
    public String getServerIdentifierByPort(int port) {
        for (Server server : getServers()) {
            if (server.getServerPort() == port)
                return server.getServerId();
        }
        StringBuilder builder = new StringBuilder();
        for (Server server : this.getServers()) {
            builder.append("[").append(server.getServerId()).append(":").append(server.getServerPort()).append("]").append(",");
        }
        LOG.warn("Port " + port + " was not found in the server list, but was requested! All Servers: " + builder);
        return "";
    }

    /**
     * Gets the current amount of lobby servers.
     * @return int - the amount of lobby servers.
     * @since 0.0.1
     */
    public int getLobbyCount(){
        return (int) this.getServers().stream().filter(s -> s.getServerType() == ServerType.LOBBY).count();
    }


    private static final List<Integer> BLACKLISTED_PORTS = Arrays.asList(30004, 30011, 31001, 31002, 31003, 31004, 31005, 31006, 31007, 31008, 31009, 32002, 32003);

    /**
     * Get the best free port for the given server type.
     * @param serverType {@link ServerType} - the server type.
     * @return int - the next free port.
     * @since 1.0
     */
    public int getOptimalPort(ServerType serverType) {
        return getNextFreeServerPort(ServerUtils.getDefaultPortForServerType(serverType));
    }

    /**
     * Returns the next free not used port considering all known servers.
     * @param startPort int - the port to start searching.
     * @since 0.0.1
     * @return int - the highest port.
     */
    private int getNextFreeServerPort(int startPort) {
        int finalStartPort = startPort;
        boolean portExits = getAllServers().stream().anyMatch(s -> s.getServerPort() == finalStartPort);
        while (portExits) {
            int p = startPort;
            portExits = getAllServers().stream().anyMatch(s -> s.getServerPort() == p) || BLACKLISTED_PORTS.contains(startPort);

            if (!portExits) {
                ServerSocket ss = null;
                DatagramSocket ds = null;
                try {
                    ss = new ServerSocket(startPort);
                    ss.setReuseAddress(true);
                    ds = new DatagramSocket(startPort);
                    ds.setReuseAddress(true);
                    return startPort;
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

            startPort++;
        }
        return startPort;
    }



    /**
     * Get the ram that is being used from the server
     * @return int - the ram currently used
     * @since 0.0.1
     */
    public int getCurrentRamUsed() {
        int ram = 0;
        for (Server server : getServers()) {
            ram += Integer.parseInt(server.getServerMaxRam().replaceAll("[^\\d.]", ""));
        }
        return ram;
    }

    /**
     * Shuts the server with the given server id down.
     * @param server Server - The server.
     * @since 0.0.1
     */
    public void shutdownServer(Server server) {
        shutdownServer(server, new ServerShutdown(true) {});
    }

    /**
     * Sends a remove request to the bungeecord server
     * @param server Server - The server.
     * @param shutdown {@link ServerShutdown} - Abstract class which will be called, when remove confirmation was sent.
     * @since 0.0.1
     */
    public void shutdownServer(Server server, ServerShutdown shutdown) {
        if (server != null) {
            server.setShutdownProcess(shutdown);
            sendToBungeeCord(new CloudEvent(CloudEvent.BUNGEE_SERVER_REMOVE_REQUEST).addData(server.getServerId()).setEventPriority(EventPriority.HIGH));
            if (getBungeeServers().isEmpty())
                stopServerFinally(server);
        }
    }

    /**
     * Remove response from BungeeCord was received or server disconnected.
     * Adds the server to the delete queue.
     * Calls {@link ServerHandler#stopServerFinally(Server)} if is first element.
     * @param server Server - the server to delete.
     * @since 0.0.1
     */
    public void deleteServer(Server server) {
        this.serverDeleteQueue.add(server);
        if (this.serverDeleteQueue.size() == 1) {
            stopServerFinally(server);
        }
    }

    /**
     * Removes the server from the queue and calls {@link ServerHandler#stopServerFinally(Server)} if queue is not empty.
     * This ensures, that the server was deleted completely.
     * @param server Server - server to remove from queue.
     */
    public void continueStopQueue(Server server) {
        if (this.serverDeleteQueue.contains(server)) {
            this.serverDeleteQueue.remove(server);
            if (!this.serverDeleteQueue.isEmpty())
                stopServerFinally(this.serverDeleteQueue.peek());
        }
    }

    /**
     * Calls {@link ClientHandler#sendDisconnect(String)} and {@link Server#stop()} to finally stop the server.
     * @param server Server - the server to stop.
     * @since 0.0.1
     */
    public void stopServerFinally(Server server) {
        this.clientHandler.sendDisconnect(server.getServerId());
        server.stop(); // will create ServerDeleter and removes server from list
    }


    /**
     * Restarts a specified server.
     * @param server {@link Server} - the server.
     * @since 0.0.1
     */
    public void restartServer(Server server) {
        shutdownServer(server, new ServerShutdown(false) {
                    @Override
                    public void serverStopped() {
                        createServerByServerType(server.getServerType());
                    }
                });
    }

    /**
     * Restarts all servers of the given server type.
     * @param serverType ServerType - the server type.
     * @since 0.0.1
     */
    public void restartAllServersOfType(ServerType serverType) {
        for (Server server : getServersByType(serverType)) {
            restartServer(server);
        }
    }

    /**
     * Returns a list containing all lobby servers.
     * @return List<Server> - all lobby servers.
     * @since 0.0.1
     */
    public List<Server> getLobbyServers() {
        return getServersByType(ServerType.LOBBY);
    }

    /**
     * Returns a list containing all servers with specified type.
     * @param serverType {@link ServerType} - The type of server.
     * @return List<Server> - all servers with specified type.
     * @since 0.0.1
     */
    public List<Server> getServersByType(ServerType serverType) {
        return this.getServers().stream().filter(s -> s.getServerType() == serverType).collect(Collectors.toList());
    }

    /**
     * Generate a ServerDefinition for a server with the given port.
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
     * @param serverType ServerType - the type of the server.
     * @since 0.0.1
     */
    public void createServerByServerType(ServerType serverType) {
        if (expireServers.contains(serverType)) {
            LOG.warn(serverType + " was tried to start, but is in expire mode!");
            return;
        }

        List<Server> serversWithSameType = getServersByType(serverType);
        serversWithSameType.sort(Comparator.comparingInt(server -> Integer.parseInt(server.getServerId().replaceAll("[^\\d.]", ""))));

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

        addServer(new Server(serverId, "127.0.0.1", ServerUtils.getRamFromType(serverType) + "M", serverId, ServerUtils.getMaxPlayersOfServerType(serverType), serverType));
    }


    /**
     * Shuts down all servers of the given server type.
     * @param type ServerType - the server type.
     * @since 0.0.1
     */
    public void shutdownAllServersOfType(ServerType type) {
        ServerShutdown serverShutdown = new ServerShutdown() {};
        for (Server server : getServersByType(type)) {
            shutdownServer(server, serverShutdown);
        }
    }


    /**
     * Shuts down all servers.
     * @since 0.0.1
     */
    public void shutdownAllServers() {
        ServerShutdown serverShutdown = new ServerShutdown() {
            @Override
            public void serverStopped() {
                if (getServers().size() == getBungeeServers().size())
                    shutdownAllServersOfType(ServerType.BUNGEECORD);
            }
        };
        this.startQueue.clear();
        for (Server server : getServers()) {
            if (server.getServerType() != ServerType.BUNGEECORD)
                shutdownServer(server, serverShutdown);
        }
    }

    /**
     * Shuts down all servers and then the cloud
     * @since 0.0.1
     */
    public void exitCloud() {
        ServerShutdown serverShutdown = new ServerShutdown() {
            @Override
            public void serverStopped() {
                if (getServers().size() == getBungeeServers().size()) {
                    ServerShutdown cloudShutdown = new ServerShutdown() {
                        @Override
                        public void serverStopped() {
                            if (getServers().isEmpty())
                                CloudServer.getInstance().shutdownEverything();
                        }
                    };
                    for (Server bungeeServer : getBungeeServers()) {
                        shutdownServer(bungeeServer, cloudShutdown);
                    }
                }
            }
        };
        this.startQueue.clear();
        for (Server server : getServers()) {
            if (server.getServerType() != ServerType.BUNGEECORD)
                shutdownServer(server, serverShutdown);
        }
    }

    /**
     * Gets the serverId of the lobby with the lowest player count.
     * @param blacklist Server[] - Server, which can not be taken.
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
     * @param blackListLobbies List<Server> - The lobbies the player can not be send
     * @param sendingPlayers Queue<UUID> - A queue of players which should be send.
     * @since 0.0.1
     */
    public void sendPlayerToLobby(List<Server> blackListLobbies, Queue<UUID> sendingPlayers) {
        Server lobby = getSuitableLobby(blackListLobbies.toArray(new Server[0]));

        if (lobby == null) {
            LOG.warn("No suitable lobby to send player!");
            return;
        }

        CloudEvent event = new CloudEvent(CloudEvent.BUNGEE_SEND_PLAYER).addData(lobby.getServerId());

        int canBeMoved = lobby.getMaxPlayers() - lobby.getPlayerCount() - 1;
        for (int i = 0; i < sendingPlayers.size() && i < canBeMoved; i++)
            event.addData(sendingPlayers.poll());

        sendToBungeeCord(event);

        if (sendingPlayers.size() > 0) {
            blackListLobbies.add(lobby);
            sendPlayerToLobby(blackListLobbies, sendingPlayers);
        }
    }

    /**
     * Sends the given cloud event to all bungeecord servers.
     * @param event CloudEvent - the cloud event to send.
     * @since 0.0.1
     */
    public void sendToBungeeCord(CloudEvent event) {
        for (Server server : getBungeeServers()) {
            ChannelHandlerContext ctx = this.clientHandler.getClientContext(server.getServerId());
            if (ctx != null) {
                ClientHandler.getInstance().sendEvent(ctx, event);
            }
        }
    }

    /**
     * Adds a server to the start queue.
     * @param server Server - the server to add.
     * @since 0.0.1
     */
    public void addToStartQueue(Server server) {
        this.startQueue.add(server);
        if (startQueue.size() == 1) {
            server.start();
        }
    }

    /**
     * Checks if a server is in the start queue.
     * If yes: remove it and start the next server.
     * @param def ServerDefinition - the definition of the server to be checked.
     * @since 0.0.1
     */
    public void checkStartQueue(ServerDefinition def) {
        checkStartQueue(getServerByIdentifier(def.getServerId()));
    }

    /**
     * Checks if a server is in the start queue.
     * If yes: remove it and start the next server.
     * @param server Server - the server to be checked.
     * @since 0.0.1
     */
    public void checkStartQueue(Server server) {
        if (this.startQueue.contains(server)) {
            this.startQueue.remove(server);
            if(!this.startQueue.isEmpty()) {
                this.startQueue.peek().start();
            }
        }
    }

    /**
     * Gets a list of all registered bungeecord servers.
     * @return List<Server> - the list of bungeecord servers.
     * @since 0.0.1
     */
    public List<Server> getBungeeServers() {
        return getServersByType(ServerType.BUNGEECORD);
    }

    /**
     * Finds the server by server identifier and executes the playerJoined() method in specified Server.
     * @param serverID String - Identifier of Server
     * @param playerCount int - The amount of players on the server
     * @see Server#setPlayerCount(int)
     * @since 0.0.1
     */
    public void setPlayerCountFromServer(String serverID, int playerCount) {
        Server server = getServerByIdentifier(serverID);
        if (server == null) {
            return;
        }
        server.setPlayerCount(playerCount);
        if (server.getServerType() == ServerType.LOBBY)
            ClientHandler.getInstance().sendLobbyInformationToLobbies();
    }

    /**
     * Sets the amount of players on the network
     * @param count int - Amount of players on network
     * @since 0.0.1
     */
    public void setPlayerCountOfNetwork(int count) {
        this.networkCount = count;
        if (this.networkCount > getLobbyServers().stream().mapToInt(Server::getMaxPlayers).sum()) {
            createServerByServerType(ServerType.LOBBY);
        }
    }

    /**
     * Returns the amount of players on the network
     * @return int - Amount of players on network
     * @since 0.0.1
     */
    public int getPlayerCountOfNetwork() {
        return this.networkCount;
    }

    /**
     * Returns the amount of players on a specific server
     * @param serverId String - Identifier of server
     * @return int - Amount of players on server
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
     * Returns the amount of players on a specific server type
     * @param serverType ServerType - Type of servers
     * @return int - amount of all players on a specific ServerType
     * @see Server#getPlayerCount()
     * @since 0.0.1
     */
    public int getPlayerCountOfServerType(ServerType serverType) {
        int playerCount = 0;
        for(Server server : getServersByType(serverType)) {
            playerCount += server.getPlayerCount();
        }
        return playerCount;
    }

    /**
     * Enables expire mode for this server type.
     * When enabled, no more servers with this type will start.
     * @param serverType {@link ServerType} - The type of server
     * @since 0.0.1
     */
    public void expire(ServerType serverType) {
        this.expireServers.add(serverType);
    }

    /**
     * Disables expire mode for this server type.
     * When enabled, no more servers with this type will start.
     * @param serverType {@link ServerType} - The type of server
     * @since 0.0.1
     */
    public void validate(ServerType serverType) {
        this.expireServers.remove(serverType);
    }

    /**
     * Will be called, when a server could not start properly or when server crashed.
     * Will flush out the server from the system.
     * @param server Server - the server that could not start.
     * @since 0.0.1
     */
    public void flushServer(Server server) {
        sendToBungeeCord(new CloudEvent(CloudEvent.BUNGEE_SERVER_REMOVE_REQUEST).addData(server.getServerId()));
        new ServerDeleter(server);
        this.clientHandler.sendDisconnect(server.getServerId());
        this.clientHandler.removeClient(server.getServerId());
        checkStartQueue(server);
    }

    /**
     * Get all servers running servers and queued.
     * @return List<Server> - all servers.
     * @since 1.0
     */
    private List<Server> getAllServers() {
        List<Server> servers = getServers();
        servers.addAll(getStartQueue());
        servers.addAll(getServerDeleteQueue());
        return servers;
    }

    public Queue<Server> getServerDeleteQueue() {
        return new LinkedList<>(serverDeleteQueue);
    }

    public Queue<Server> getStartQueue() {
        return new LinkedList<>(startQueue);
    }

    public ClientHandler getClientHandler() {
        return clientHandler;
    }

}
