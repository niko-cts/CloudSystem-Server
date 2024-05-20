package net.fununity.cloud.server.client;

import io.netty.channel.ChannelHandlerContext;
import net.fununity.cloud.common.events.Event;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.common.utils.CloudLogger;
import net.fununity.cloud.server.misc.MinigameHandler;
import net.fununity.cloud.server.server.Server;
import net.fununity.cloud.server.server.ServerHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Handler class for all cloud clients.
 *
 * @author Niko
 * @since 0.0.1
 */
public class ClientHandler {

    private static final CloudLogger LOG = CloudLogger.getLogger(ClientHandler.class.getSimpleName());

    /**
     * Gets the instance of the singleton ClientHandler.
     *
     * @return ClientHandler - the instance of the clienthandler.
     * @since 0.0.1
     */
    public static ClientHandler getInstance() {
        if (instance == null)
            instance = new ClientHandler();
        return instance;
    }

    private static ClientHandler instance;

    private final ServerHandler serverHandler;
    private final ConcurrentMap<String, ChannelHandlerContext> clients;

    /**
     * Default constructor for the ClientHandler.
     *
     * @since 0.0.1
     */
    private ClientHandler() {
        instance = this;
        this.serverHandler = ServerHandler.getInstance();
        this.clients = new ConcurrentHashMap<>();
    }

    /**
     * Gets all saved client ids and their mapped ChannelHandlerContext.
     *
     * @return ConcurrentHashMap<String, ChannelHandlerContext> - the map of clients.
     * @since 0.0.1
     */
    public ConcurrentMap<String, ChannelHandlerContext> getClients() {
        return new ConcurrentHashMap<>(this.clients);
    }

    /**
     * Returns a client based on the given client id or null.
     *
     * @param clientId String - the client id.
     * @return ChannelHandlerContext - the context of the client or null.
     * @since 0.0.1
     */
    public ChannelHandlerContext getClientContext(String clientId) {
        return this.clients.getOrDefault(clientId, null);
    }

    /**
     * Saves a client to the clients map.
     *
     * @param clientId String - the id of the client.
     * @param ctx      ChannelHandlerContext - the context of the client.
     * @since 0.0.1
     */
    public void saveClient(String clientId, ChannelHandlerContext ctx) {
        this.clients.putIfAbsent(clientId, ctx);
        ctx.channel().closeFuture().addListener(channelFuture -> removeClient(ctx));
    }

    /**
     * Removes a client based on the given client id.
     *
     * @param clientId String - the client id.
     * @since 0.0.1
     */
    public void removeClient(String clientId) {
        this.clients.remove(clientId);
        LOG.debug("Client id %s was removed", clientId);
    }

    /**
     * Removes a client based on the given ChannelHandlerContext.
     *
     * @param ctx ChannelHandlerContext - the context.
     * @since 0.0.1
     */
    public void removeClient(ChannelHandlerContext ctx) {
        getClients().entrySet().stream().filter(c -> c.getValue().channel() == ctx.channel()).forEach(c -> removeClient(c.getKey()));
    }

    /**
     * Remaps the ChannelHandlerContext to the correct server id.
     *
     * @param ctx  ChannelHandlerContext - the channel handler context.
     * @param port int - the port of the server.
     * @since 0.0.1
     */
    public void remapChannelHandlerContext(ChannelHandlerContext ctx, int port) {
        LOG.debug("Remapping ctx for %s to port %s", ctx.channel(), port);
        removeClient(ctx);
        this.clients.putIfAbsent(this.serverHandler.getServerIdentifierByPort(port), ctx);
    }

    /**
     * Sends the current lobby information to all lobbies.
     *
     * @since 0.0.1
     */
    public void sendLobbyInformationToLobbies() {
        CloudEvent cloudEvent = new CloudEvent(CloudEvent.RES_LOBBY_INFOS);
        Map<String, Integer> lobbyInformation = new HashMap<>();
        List<Server> lobbies = ServerHandler.getInstance().getLobbyServers();
        for (Server lobbyServer : lobbies)
            lobbyInformation.put(lobbyServer.getServerId(), lobbyServer.getPlayerCount());

        cloudEvent.addData(lobbyInformation);

        lobbies.forEach(s -> sendEvent(s, cloudEvent));
        ServerHandler.getInstance().sendToBungeeCord(cloudEvent);
    }

    /**
     * Send coc attack server
     *
     * @since 1.0.0
     */
    public void sendCocAttackServerAmount() {
        var servers = serverHandler.getActiveServersByType(ServerType.COCATTACK);
        for (Server cocbase : servers) {
            this.sendEvent(getClientContext(cocbase.getServerId()), new CloudEvent(CloudEvent.COC_RESPONSE_ATTACK_SERVER_AMOUNT).addData(servers.size()));
        }
    }

    /**
     * Sends minigame data to the server.
     *
     * @param lobbyId String - the lobby id that requested the status.
     * @since 0.0.1
     */
    public void sendMinigameInformationToLobby(String lobbyId) {
        for (String serverId : MinigameHandler.getInstance().getLobbyServers()) {
            ChannelHandlerContext server = getClientContext(serverId);
            if (server != null)
                sendEvent(server, new CloudEvent(CloudEvent.REQ_MINIGAME_RESEND_STATUS).addData(lobbyId));
        }
    }

    /**
     * Sends an event to the server.
     *
     * @param server Server - server to send event.
     * @param event  Event - the event
     */
    public void sendEvent(Server server, Event event) {
        ChannelHandlerContext ctx = getClientContext(server.getServerId());
        if (ctx != null)
            sendEvent(ctx, event);
        else
            LOG.error("Tried to send an event to Server '%s' but CTX is null. Event was: %s", server.getServerId(), event);
    }

    /**
     * Sends an event to the specified CTX
     *
     * @param ctx   ChannelHandlerContext - Channel to send
     * @param event {@link CloudEvent} - Event to send
     * @since 0.0.1
     */
    public void sendEvent(ChannelHandlerContext ctx, Event event) {
        if (ctx == null) {
            LOG.error("Can not send event, because CTX is null! Event is: " + event);
            return;
        }
        LOG.debug("Sending to '%s' event: %s", getClientId(ctx), event);
        ctx.writeAndFlush(event);
    }


    /**
     * Get the client-id from the ctx
     *
     * @param ctx ChannelHandlerContext - the channel
     * @return String - the id of the client
     * @since 0.0.1
     */
    public String getClientId(ChannelHandlerContext ctx) {
        return getClients().entrySet().stream().filter(c -> c.getValue().channel() == ctx.channel()).findFirst().map(Map.Entry::getKey).orElse(null);
    }


    public static CloudLogger getLogger() {
        return LOG;
    }
}
