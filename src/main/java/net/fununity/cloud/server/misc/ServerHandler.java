package net.fununity.cloud.server.misc;

import io.netty.channel.ChannelHandlerContext;
import net.fununity.cloud.common.events.EventPriority;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.server.ServerDefinition;
import net.fununity.cloud.common.server.ServerState;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.server.Server;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler class for all server mechanics and server util methods.
 * @author Marco Hajek
 * @since 0.0.1
 */
public class ServerHandler {

    public static final Logger LOG = Logger.getLogger(ServerHandler.class.getName());
    public static final int MAX_RAM = 42200;
    private static ServerHandler instance;

    private final ClientHandler clientHandler;
    private final List<Server> servers;
    private final Queue<Server> startQueue;
    private final Queue<Server> stopQueue;
    private final Map<ServerType, ServerRestartingIterator> restartQueue;
    private final Set<ServerType> expireServers;
    private int networkCount;

    /**
     * Default constructor of the server handler.
     * @since 0.0.1
     */
    private ServerHandler(){
        instance = this;
        PatternLayout layout = new PatternLayout("[%d{HH:mm:ss}] %c{1} [%p]: %m%n");
        LOG.addAppender(new ConsoleAppender(layout));
        LOG.setLevel(Level.INFO);
        LOG.setAdditivity(false);

        this.clientHandler = ClientHandler.getInstance();
        this.servers = new ArrayList<>();
        this.startQueue = new LinkedList<>();
        this.stopQueue = new LinkedList<>();
        this.restartQueue = new EnumMap<>(ServerType.class);
        this.expireServers = new HashSet<>();
        this.networkCount = 0;
    }

    /**
     * Gets the instance of the singleton ServerHandler.
     * @return ServerHandler - the server handler.
     * @since 0.0.1
     */
    public static ServerHandler getInstance(){
        if(instance == null)
            instance = new ServerHandler();
        return instance;
    }

    /**
     * Gets the list of known servers.
     * @return List<Server> - the list of servers.
     * @since 0.0.1
     */
    public List<Server> getServers() {
        return this.servers;
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
    public void setServerIdle(String serverId){
        for(Server server : this.servers) {
            if(server.getServerId().equalsIgnoreCase(serverId)) {
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
        for(Server server : this.servers) {
            if(server != oldServer && server.getServerType() == oldServer.getServerType() && server.getServerState() == ServerState.IDLE)
                idleServers+=1;
        }

        if(idleServers < 1)
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
        return servers.stream().filter(server -> server.getServerId().equals(identifier)).findFirst().orElse(null);
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
        for (Server server : this.servers) {
            builder.append("[").append(server.getServerId()).append(":").append(server.getServerPort()).append("]").append(",");
        }
        LOG.warn("Port " + port + " was not found in the server list, but was requested! All Servers: " + builder.toString());
        return "";
    }

    /**
     * Gets the current amount of lobby servers.
     * @return int - the amount of lobby servers.
     * @since 0.0.1
     */
    public int getLobbyCount(){
        return (int) this.servers.stream().filter(s-> s.getServerType() == ServerType.LOBBY).count();
    }
    /**
     * Returns the highest used port considering all known servers.
     * @since 0.0.1
     * @return int - the highest port.
     */
    public int getHighestServerPort() {
        int port = 0;
        for(Server server : this.servers)
            if (server.getServerPort() > port)
                port = server.getServerPort();
        return port;
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
     * Get the ram that is been used from the server
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
     * @param shutdown {@link ServerShutdown} - Interface which will be executed, when remove confirmation was sent.
     * @since 0.0.1
     */
    public void shutdownServer(Server server, ServerShutdown shutdown) {
        if (server != null && server.getShutdownProcess() == null) {
            server.setShutdownProcess(shutdown);
            sendToBungeeCord(new CloudEvent(CloudEvent.BUNGEE_REMOVE_SERVER).addData(server.getServerId()).setEventPriority(EventPriority.HIGH));
        }
    }

    /**
     * Bungee send the shutdown confirmation.
     * Server will be stopped.
     * @param server Server - the server that will be stopped
     * @since 0.0.1
     */
    public void receivedShutdownConfirmation(Server server) {
        if(server == null) return;
        this.clientHandler.sendDisconnect(server.getServerId());
        this.clientHandler.removeClient(server.getServerId());

        this.stopQueue.add(server);
        if (stopQueue.size() == 1) {
            deleteServer(server);
        }
    }

    /**
     * Removes the server from the queue.
     * Calls {@link #deleteServer(Server)} to the next server in queue.
     * @param server Server - the server to delete.
     * @since 0.0.1
     */
    private void checkStopQueue(Server server) {
        if (this.stopQueue.contains(server)) {
            this.stopQueue.remove(server);
            if (!this.stopQueue.isEmpty()) {
                deleteServer(this.stopQueue.peek());
            }
        }
    }

    /**
     * Delets a server completely.
     * @param server Server - the server
     * @since 0.0.1
     */
    private void deleteServer(Server server) {
        server.stop();
        this.servers.remove(server);
        if (server.getServerType() == ServerType.LOBBY)
            this.clientHandler.sendLobbyInformationToLobbies();
        else
            MinigameHandler.getInstance().removeServer(server,
                    server.getShutdownProcess() != null && server.getShutdownProcess().needsMinigameCheck());
        if (server.getShutdownProcess() != null)
            server.getShutdownProcess().serverStopped();
        checkStopQueue(server);
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
                        if (!restartQueue.containsKey(server.getServerType())) {
                            createServerByServerType(server.getServerType());
                            return;
                        }

                        ServerRestartingIterator serverRestartingIterator = restartQueue.get(server.getServerType());
                        if (serverRestartingIterator.getIterator().hasNext()) {
                            restartServer(serverRestartingIterator.getIterator().next());
                            return;
                        }

                        restartQueue.remove(serverRestartingIterator.getServerType());
                        for (int i = 0; i < serverRestartingIterator.getSize(); i++) {
                            createServerByServerType(server.getServerType());
                        }
                    }
                });
    }

    /**
     * Restarts all servers of the given server type.
     * @param serverType ServerType - the server type.
     * @since 0.0.1
     */
    public void restartAllServersOfType(ServerType serverType) {
        if (this.restartQueue.containsKey(serverType)) return;
        List<Server> serversByType = getServersByType(serverType);
        if (serversByType.isEmpty()) return;
        Iterator<Server> iterate = serversByType.iterator();
        this.restartQueue.put(serverType, new ServerRestartingIterator(serverType, iterate, serversByType.size()));
        restartServer(iterate.next());
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
        return this.servers.stream().filter(s -> s.getServerType() == serverType).collect(Collectors.toList());
    }

    /**
     * Generates a list of ServerDefinitions.
     * @see ServerDefinition
     * @return List<ServerDefinition> - the server definitions.
     * @since 0.0.1
     */
    public List<ServerDefinition> generateServerDefinitions() {
        List<ServerDefinition> definitions = new ArrayList<>();
        for(Server server : this.servers) {
            definitions.add(new ServerDefinition(server.getServerId(), server.getServerIp(), server.getServerMaxRam(), server.getServerMotd(), server.getMaxPlayers(), server.getPlayerCount(), server.getServerPort(), server.getServerType(), server.getServerState()));
        }
        return definitions;
    }

    /**
     * Generate a ServerDefinition for a server with the given port.
     * @param port int - the port of the server.
     * @return ServerDefinition - the definition of the server.
     * @since 0.0.1
     */
    public ServerDefinition getServerDefinitionByPort(int port) {
        Server server = getServerByIdentifier(getServerIdentifierByPort(port));
        if(server != null) {
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
            if(nextNumber == Integer.parseInt(server.getServerId().replaceAll("[^\\d.]", ""))) {
                nextNumber++;
            }
        }

        String serverId = ServerUtils.getServerIdOfServerType(serverType);
        if(nextNumber < 10)
            serverId += "0" + nextNumber;
        else
            serverId += nextNumber;

        int maxPlayers = ServerUtils.getMaxPlayersOfServerType(serverType);
        addServer(new Server(serverId, "127.0.0.1", ServerUtils.getRamFromType(serverType) + "M", serverId, maxPlayers, serverType));
    }


    /**
     * Shuts down all servers of the given server type.
     * @param type ServerType - the server type.
     * @since 0.0.1
     */
    public void shutdownAllServersOfType(ServerType type) {
        ServerShutdown serverShutdown = new ServerShutdown(false) {};
        for (Server server : getServersByType(type)) {
            shutdownServer(server, serverShutdown);
        }
    }


    /**
     * Shuts down all servers.
     * @since 0.0.1
     */
    public void shutdownAllServers() {
        ServerShutdown serverShutdown = new ServerShutdown(false) {};
        for (Server server : getServers())
            shutdownServer(server, serverShutdown);
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
        for (int i=0; i < sendingPlayers.size() && i < canBeMoved; i++)
            event.addData(sendingPlayers.poll());

        sendToBungeeCord(event);

        if(sendingPlayers.size() > 0) {
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
        if(server.getServerType() == ServerType.BUNGEECORD) {
            server.start();
            return;
        }
        this.startQueue.add(server);
        if(startQueue.size() == 1) {
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
        Server server = getServerByIdentifier(def.getServerId());
        if(this.startQueue.contains(server)) {
            this.startQueue.remove(server);
            if(!this.startQueue.isEmpty()) {
                this.startQueue.peek().start();
            }
        }
    }

    /**
     * Checks if a server is in the start queue.
     * If yes: remove it and start the next server.
     * @param server Server - the server to be checked.
     * @since 0.0.1
     */
    public void checkStartQueue(Server server) {
        if(this.startQueue.contains(server)) {
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
        if(server == null) {
            return;
        }
        server.setPlayerCount(playerCount);
        if(server.getServerType() == ServerType.LOBBY)
            ClientHandler.getInstance().sendLobbyInformationToLobbies();
    }

    /**
     * Sets the amount of players on the network
     * @param count int - Amount of players on network
     * @since 0.0.1
     */
    public void setPlayerCountOfNetwork(int count) {
        this.networkCount = count;
        if (this.networkCount + 10 > getLobbyServers().stream().mapToInt(Server::getMaxPlayers).sum()) {
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
        if(server == null) {
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
        List<Server> servers = getServers().stream().filter(type -> type.getServerType() == serverType).collect(Collectors.toList());
        int playerCount = 0;
        for(Server server : servers) {
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
        this.servers.remove(server);
        this.clientHandler.removeClient(server.getServerId());
        sendToBungeeCord(new CloudEvent(CloudEvent.BUNGEE_REMOVE_SERVER).addData(server.getServerId()));
        checkStartQueue(server);
        checkStopQueue(server);
    }
}
