package net.fununity.cloud.server.misc;

import io.netty.channel.ChannelHandlerContext;
import net.fununity.cloud.common.events.Event;
import net.fununity.cloud.common.events.EventSendingManager;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.server.server.Server;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Handler class for all cloud clients.
 *
 * @author Marco Hajek
 * @since 0.0.1
 */
public class ClientHandler {

    private static final Logger LOG = Logger.getLogger(ClientHandler.class.getName());

    /**
     * Gets the instance of the singleton ClientHandler.
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
    private final ConcurrentMap<ChannelHandlerContext, EventSendingManager> eventSenderMap;

    /**
     * Default constructor for the ClientHandler.
     *
     * @since 0.0.1
     */
    private ClientHandler() {
        instance = this;
        PatternLayout layout = new PatternLayout("[%d{HH:mm:ss}] %c{1} [%p]: %m%n");
        LOG.addAppender(new ConsoleAppender(layout));
        LOG.setLevel(Level.INFO);
        LOG.setAdditivity(false);

        this.serverHandler = ServerHandler.getInstance();
        this.clients = new ConcurrentHashMap<>();
        this.eventSenderMap = new ConcurrentHashMap<>();
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
     * @param clientId String - the client id.
     * @since 0.0.1
     */
    public void removeClient(String clientId) {
        removeClient(getClientContext(clientId));
    }

    /**
     * Removes a client based on the given ChannelHandlerContext.
     * @param ctx ChannelHandlerContext - the context.
     * @since 0.0.1
     */
    public void removeClient(ChannelHandlerContext ctx) {
        for (Map.Entry<String, ChannelHandlerContext> entry : new ConcurrentHashMap<>(clients).entrySet()) {
            if (entry.getValue().equals(ctx)) {
                this.clients.remove(entry.getKey());
                EventSendingManager eventSendingManager = this.eventSenderMap.getOrDefault(entry.getValue(), null);
                if (eventSendingManager != null) {
                    eventSendingManager.disconnect();
                    this.eventSenderMap.remove(entry.getValue());
                }
                return;
            }
        }
    }

    /**
     * Remaps the ChannelHandlerContext to the correct server id.
     * @param ctx  ChannelHandlerContext - the channel handler context.
     * @param port int - the port of the server.
     * @since 0.0.1
     */
    public void remapChannelHandlerContext(ChannelHandlerContext ctx, int port) {
        String serverId = "";
        for (Map.Entry<String, ChannelHandlerContext> entry : this.clients.entrySet()) {
            if (ctx == entry.getValue()) {
                serverId = entry.getKey();
                break;
            }
        }

        this.clients.remove(serverId);
        String newID = this.serverHandler.getServerIdentifierByPort(port);
        this.clients.putIfAbsent(newID, ctx);
        this.eventSenderMap.get(ctx).setClientID(newID);
    }

    /**
     * Sends a disconnect message to a specific client.
     * @param clientId String - the client id.
     * @since 0.0.1
     */
    public void sendDisconnect(String clientId) {
        ChannelHandlerContext ctx = this.getClientContext(clientId);
        if (ctx != null)
            this.sendEvent(ctx, new CloudEvent(CloudEvent.CLIENT_DISCONNECT_GRACEFULLY));
        else
            LOG.warn("CTX of " + clientId + " was null!");
    }

    /**
     * Sends the current lobby information to all lobbies.
     * @since 0.0.1
     */
    public void sendLobbyInformationToLobbies() {
        CloudEvent cloudEvent = new CloudEvent(CloudEvent.RES_LOBBY_INFOS);
        Map<String, Integer> lobbyInformation = new HashMap<>();
        for (Server lobbyServer : ServerHandler.getInstance().getLobbyServers())
            lobbyInformation.put(lobbyServer.getServerId(), lobbyServer.getPlayerCount());

        cloudEvent.addData(lobbyInformation);
        for (Map.Entry<String, ChannelHandlerContext> entry : getClients().entrySet()) {
            if (entry.getKey().toLowerCase().contains("lobby") || entry.getKey().toLowerCase().contains("main")) {
                this.sendEvent(entry.getValue(), cloudEvent);
            }
        }
    }

    /**
     * Sends minigame data to the server.
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
     * Sends an event to the specified CTX
     * @param ctx   ChannelHandlerContext - Channel to send
     * @param event {@link CloudEvent} - Event to send
     * @since 0.0.1
     */
    public void sendEvent(ChannelHandlerContext ctx, Event event) {
        if (this.eventSenderMap.containsKey(ctx))
            this.eventSenderMap.get(ctx).sendEvent(event.clone());
    }

    /**
     * Sends resend event to the given ctx.
     * @param ctx ChannelHandlerContext - the channel to send the resend request
     * @since 0.0.1
     */
    public void sendResendEvent(ChannelHandlerContext ctx) {
        if (this.eventSenderMap.containsKey(ctx))
            this.eventSenderMap.get(ctx).sendResendEvent();
    }

    /**
     * Get the client Id from the ctx
     * @param ctx ChannelHandlerContext - the channel
     * @return String - the id of the client
     * @since 0.0.1
     */
    public String getClientId(ChannelHandlerContext ctx) {
        for (Map.Entry<String, ChannelHandlerContext> entry : getClients().entrySet()) {
            if (entry.getValue() == ctx)
                return entry.getKey();
        }
        return null;
    }


    /**
     * Caches the new event sender for the given ctx
     * @param ctx ChannelHandlerContext - The channel
     * @since 0.0.1
     */
    public void registerSendingManager(ChannelHandlerContext ctx) {
        this.eventSenderMap.put(ctx, new EventSendingManager(ctx));
    }

    /**
     * Stores the client id in the {@link EventSendingManager}.
     * @param ctx ChannelHandlerContext - the channel
     * @param clientId String - the client id
     * @since 0.0.1
     */
    public void setClientIdToEventSender(ChannelHandlerContext ctx, String clientId) {
        if(this.eventSenderMap.containsKey(ctx))
            this.eventSenderMap.get(ctx).setClientID(clientId);
    }

    /**
     * Stores the client id in the {@link EventSendingManager}.
     * @param ctx ChannelHandlerContext - the channel
     * @param event Event - last received event
     * @since 0.0.1
     */
    public void setLastReceivedEvent(ChannelHandlerContext ctx, Event event) {
        if(this.eventSenderMap.containsKey(ctx))
            this.eventSenderMap.get(ctx).receivedEvent(event);
    }

}
