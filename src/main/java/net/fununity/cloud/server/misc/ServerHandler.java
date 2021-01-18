package net.fununity.cloud.server.misc;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.server.ServerDefinition;
import net.fununity.cloud.common.server.ServerState;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.common.utils.MessagingUtils;
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
    private static ServerHandler instance;

    private final ClientHandler clientHandler;
    private List<Server> servers;
    private Map<CloudEvent, List<String>> lobbyQueue;
    private Queue<Server> startQueue;

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
        this.lobbyQueue = new HashMap<>();
        this.startQueue = new LinkedList<>();
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
    public List<Server> getServers(){
        return (List<Server>)(((ArrayList<Server>)this.servers).clone());
    }

    /**
     * Adds a server to the servers array.
     * @param server Server - the server.
     * @see Server
     * @since 0.0.1
     */
    public void addServer(Server server){
        if(!this.servers.contains(server)){
            this.servers.add(server);
            this.addToStartQueue(server);
            if(server.getServerId().toLowerCase().contains("lobby")){
                this.clientHandler.sendLobbyCountToAllClients();
            }
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
        for(Server server : this.servers){
            if(server.getServerId().equalsIgnoreCase(serverId)){
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
    private void createNewServerIfNeeded(Server oldServer){
        int idleServers = 0;
        for(Server server : this.servers){
            if(server != oldServer && server.getServerType() == oldServer.getServerType() && server.getServerState() == ServerState.IDLE)
                idleServers+=1;
        }

        if(idleServers < 1)
            this.addServer(new Server(RandomStringUtils.random(16), "127.0.0.1", "512M", "motd", oldServer.getServerType()));
    }

    /**
     * Gets a server by its identifier.
     * @param identifier String - the identifier.
     * @return Server or null.
     * @see Server
     * @since 0.0.1
     */
    public Server getServerByIdentifier(String identifier){
        for(Server server : this.servers)
            if(server.getServerId().equals(identifier)) return server;
        return null;
    }

    /**
     * Gets the server identifier of the server with the given port.
     * @param port int - the port of the server.
     * @return String - the identifier of the server.
     * @since 0.0.1
     */
    public String getServerIdentifierByPort(int port){
        for(Server server : this.servers){
            if(server.getServerPort() == port)
                return server.getServerId();
        }
        return "";
    }

    /**
     * Gets the current amount of lobby servers.
     * @return int - the amount of lobby servers.
     * @since 0.0.1
     */
    public int getLobbyCount(){
        int count = 0;
        for(Server server : this.servers){
            if(server.getServerId().toLowerCase().contains("lobby"))
                count += 1;
        }
        return count;
    }
    /**
     * Returns the highest used port considering all known servers.
     * @since 0.0.1
     * @return int - the highest port.
     */
    public int getHighestServerPort(){
        int port = 0;
        for(Server server : this.servers)
            if(server.getServerPort() > port)
                port = server.getServerPort();
        return port;
    }

    /**
     * Shuts the server with the given server id down.
     * @param serverId String - the server id.
     * @since 0.0.1
     */
    public void shutdownServer(String serverId) {
        Server s = null;
        for(Server server : this.servers){
            if(server.getServerId().equalsIgnoreCase(serverId)){
                s = server;
                break;
            }
        }

        if(s != null){
            this.servers.remove(s);
            s.stop(true);
        }
    }

    /**
     * Returns a list containing all lobby servers.
     * @return List<Server> - all lobby servers.
     * @since 0.0.1
     */
    public List<Server> getLobbyServers() {
        ArrayList<Server> localServers = new ArrayList<>();
        for(Server server : this.servers){
            if(server.getServerType() == ServerType.LOBBY)
                localServers.add(server);
        }
        return localServers;
    }

    /**
     * Generates a list of ServerDefinitions.
     * @see ServerDefinition
     * @return List<ServerDefinition> - the server definitions.
     * @since 0.0.1
     */
    public List<ServerDefinition> generateServerDefinitions() {
        List<ServerDefinition> definitions = new ArrayList<>();
        for(Server server : this.servers){
            definitions.add(new ServerDefinition(server.getServerId(), server.getServerIp(), server.getServerMaxRam(), server.getServerMotd(), server.getServerPort(), server.getServerType(), server.getServerState()));
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
        if(server != null){
            return new ServerDefinition(server.getServerId(), server.getServerIp(), server.getServerMaxRam(), server.getServerMotd(), port, server.getServerType(), server.getServerState());
        }
        return null;
    }

    /**
     * Removes a server from the list.
     * @param server Server - the server.
     * @since 0.0.1
     */
    public void removeServer(Server server){
        this.servers.remove(server);
    }

    /**
     * Create a server with the given server type.
     * @param serverType ServerType - the type of the server.
     * @since 0.0.1
     */
    public void createServerByServerType(ServerType serverType) {
        List<Server> serversWithSameType = new ArrayList<>();
        for(Server server : this.servers)
            if(server.getServerType() == serverType)
                serversWithSameType.add(server);

        int highestNumber = 0;
        for(Server server : serversWithSameType){
            int number = Integer.parseInt(server.getServerId().replaceAll("[^\\d.]", ""));
            if(number > highestNumber) highestNumber = number;
        }
        highestNumber+=1;
        String serverId = getServerIdOfServerType(serverType);
        if(highestNumber < 10)
            serverId += "0" + highestNumber;
        else
            serverId += highestNumber;

        String maxRam = getServerMaxRamOfServerType(serverType);

        Server newServer = new Server(serverId, "127.0.0.1", maxRam, serverId, serverType);
        this.servers.add(newServer);
        newServer.start();
    }

    /**
     * Get the maximum amount of ram for a specified server type
     * @param serverType ServerType - Type of Server
     * @return String - Maximum amount of Ram
     */
    private String getServerMaxRamOfServerType(ServerType serverType) {
        switch (serverType) {
            case LANDSCAPES:
                return "2048M";
            default:
                return "512M";
        }
    }

    private String getServerIdOfServerType(ServerType serverType){
        switch(serverType){
            case BUNGEECORD:
                return "BungeeCord";
            case LOBBY:
                return "Lobby";
            case CAVEHUNT:
                return "CH";
            case FLOWERWARS2x1:
                return "FWTxO";
            case FLOWERWARS2x2:
                return "FWTxT";
            case FLOWERWARS4x2:
                return "FWFxT";
            case BEATINGPIRATES:
                return "BP";
            case PAINTTHESHEEP:
                return "PTS";
            case LANDSCAPES:
                return "LandScapes";
            case DSGVO:
                return "DSGVO";
        }
        return "";
    }

    /**
     * Shuts down all servers of the given server type.
     * @param type ServerType - the server type.
     * @since 0.0.1
     */
    public void shutdownAllServersOfType(ServerType type){
        List<Server> toRemove = new ArrayList<>();
        for(Server server : this.servers){
            if(server.getServerType() == type){
                this.clientHandler.sendDisconnect(server.getServerId());
                server.stop(true);
                toRemove.add(server);
            }
        }
        for(Server server : toRemove)
            this.servers.remove(server);
    }

    /**
     * Restarts all servers of the given server type.
     * @param serverType ServerType - the server type.
     * @since 0.0.1
     */
    public void restartAllServersOfType(ServerType serverType) {
        for(Server server : this.servers){
            if(server.getServerType() == serverType){
                this.clientHandler.sendDisconnect(server.getServerId());
                server.restart();
            }
        }
    }

    /**
     * Shuts down all servers.
     * @since 0.0.1
     */
    public void shutdownAllServers() {
        for(Server server : this.servers){
            this.clientHandler.sendDisconnect(server.getServerId());
            server.stop(true);
        }
        this.servers.clear();
    }

    /**
     * Starts all default servers.
     * @since 0.0.1
     */
    public void startDefaultServers() {
        if(this.servers.isEmpty()){
            ConfigHandler.getInstance().loadDefaultServers();
        }else{
            LOG.warn("You cannot start all default servers when at least one server is already running.");
        }
    }

    /**
     * Gets the serverId of the lobby with the lowest player count.
     * @return String - the server id of the lobby.
     * @since 0.0.1
     */
    public String getServerIdOfSuitableLobby() {
        Server lobby = null;
        for(Server server : this.servers){
            //if(server.getServerType() == ServerType.LOBBY && (lobby == null || lobby.getPlayerCount() > server.getPlayerCount()))
            if(server.getServerType() == ServerType.LOBBY)
                lobby = server;
        }
        return lobby.getServerId();
    }

    /**
     * Adds a cloud event to the queue of a specific serverid
     * @param serverId String - the server id of the lobby.
     * @param event CloudEvent - the event to queue up.
     * @since 0.0.1
     */
    public void addToLobbyQueue(String serverId, CloudEvent event){
        List<String> serverIds = new ArrayList<>();
        if(this.lobbyQueue.containsKey(event))
            serverIds = this.lobbyQueue.get(event);
        serverIds.add(serverId);
        this.lobbyQueue.put(event, serverIds);
    }

    /**
     * Sends the events stored in the queue to the lobby server.
     * @since 0.0.1
     */
    public void sendLobbyQueue(){
        Map<CloudEvent, List<String>> newQueue = new HashMap<>();
        for(Map.Entry<CloudEvent, List<String>> entry : this.lobbyQueue.entrySet()){
            ArrayList<String> serverIds = (ArrayList<String>)entry.getValue();
            for(String id : (List<String>)serverIds.clone()){
                if(this.clientHandler.getClientContext(id) != null){
                    this.clientHandler.getClientContext(id).writeAndFlush(Unpooled.copiedBuffer(MessagingUtils.convertEventToStream(entry.getKey()).toByteArray()));
                    serverIds.remove(id);
                }
            }
            if(!serverIds.isEmpty())
                newQueue.put(entry.getKey(), serverIds);
        }
        this.lobbyQueue = newQueue;
    }

    /**
     * Sends the given cloud event to all bungeecord servers.
     * @param event CloudEvent - the cloud event to send.
     * @since 0.0.1
     */
    public void sendToBungeeCord(CloudEvent event){
        for(Server server : this.servers){
            if(server.getServerType() != ServerType.BUNGEECORD) continue;
            ChannelHandlerContext ctx = this.clientHandler.getClientContext(server.getServerId());
            if(ctx == null) continue;
            ctx.writeAndFlush(Unpooled.copiedBuffer(MessagingUtils.convertEventToStream(event).toByteArray()));
        }
    }

    /**
     * Adds a server to the start queue.
     * @param server Server - the server to add.
     * @since 0.0.1
     */
    public void addToStartQueue(Server server){
        if(server.getServerType() == ServerType.BUNGEECORD){
            server.start();
            return;
        }
        this.startQueue.add(server);
        if(startQueue.size() == 1)
            server.start();
    }

    /**
     * Checks if a server is in the start queue.
     * If yes: remove it and start the next server.
     * @param def ServerDefinition - the definition of the server to be checked.
     * @since 0.0.1
     */
    public void checkStartQueue(ServerDefinition def) {
        Server server = getServerByIdentifier(def.getServerId());
        if(this.startQueue.contains(server)){
            this.startQueue.remove(server);
            if(!this.startQueue.isEmpty())
                this.startQueue.peek().start();
        }
    }

    /**
     * Gets a list of all registered bungeecord servers.
     * @return List<Server> - the list of bungeecord servers.
     * @since 0.0.1
     */
    public List<Server> getBungeeServers(){
        List<Server> servers = new ArrayList<>();
        for(Server server : this.servers){
            if(server.getServerType() == ServerType.BUNGEECORD)
                servers.add(server);
        }
        return servers;
    }

    /**
     * Finds the server by server identifier and executes the playerJoined() method in specified Server.
     * @param serverID String - Identifier of Server
     * @see Server#playerJoined()
     * @since 0.0.1
     */
    public void increasePlayerCountFromServer(String serverID) {
        Server server = getServerByIdentifier(serverID);
        if(server == null) {
            return;
        }
        server.playerJoined();
    }

    /**
     * Finds the server by server identifier and executes the playerLeft() method in specified Server.
     * @param serverId String - Identifier of Server
     * @see Server#playerLeft()
     * @since 0.0.1
     */
    public void reducePlayerCountFromServer(String serverId) {
        Server server = getServerByIdentifier(serverId);
        if(server == null) {
            return;
        }
        server.playerLeft();
    }

    /**
     * Returns the amount of players on the network
     * @return int - Amount of players on network
     * @see Server#getPlayerCount()
     * @since 0.0.1
     */
    public int getPlayerCountOfNetwork() {
        return getServers().stream().mapToInt(Server::getPlayerCount).sum();
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
}
