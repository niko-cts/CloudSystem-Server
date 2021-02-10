package net.fununity.cloud.server.misc;

import io.netty.channel.ChannelHandlerContext;
import net.fununity.cloud.common.events.Event;
import net.fununity.cloud.common.events.EventPriority;
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
    private static ClientHandler instance;

    private final ServerHandler serverHandler;
    private final ConcurrentMap<String, ChannelHandlerContext> clients;
    private ChannelHandlerContext discordContext;

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
        this.discordContext = null;
    }

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
        if (clientId.equalsIgnoreCase("CloudBot"))
            this.discordContext = ctx;
        ctx.channel().closeFuture().addListener(channelFuture -> removeClient(ctx));
    }

    /**
     * Removes a client based on the given client id.
     *
     * @param clientId String - the client id.
     * @since 0.0.1
     */
    public void removeClient(String clientId) {
        this.senderMap.remove(getClientContext(clientId));
        this.clients.remove(clientId);
        if (clientId.equalsIgnoreCase("CloudBot"))
            this.discordContext = null;
    }

    /**
     * Removes a client based on the given ChannelHandlerContext.
     *
     * @param ctx ChannelHandlerContext - the context.
     * @since 0.0.1
     */
    public void removeClient(ChannelHandlerContext ctx) {
        for (Map.Entry<String, ChannelHandlerContext> entry : this.clients.entrySet()) {
            if (entry.getValue() == ctx) {
                this.clients.remove(entry.getKey());
                this.senderMap.remove(entry.getValue());
                return;
            }
        }
    }

    /**
     * Remaps the ChannelHandlerContext to the correct server id.
     *
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
        this.senderMap.get(ctx).setClientID(newID);
    }

    /**
     * Sends a disconnect message to a specific client.
     *
     * @param clientId String - the client id.
     * @since 0.0.1
     */
    public void sendDisconnect(String clientId) {
        ChannelHandlerContext ctx = this.getClientContext(clientId);
        if (ctx != null) {
            this.addToQueue(ctx, new CloudEvent(CloudEvent.CLIENT_DISCONNECT_GRACEFULLY).setEventPriority(EventPriority.HIGH));
            this.receiveACK(ctx);
        } else
            LOG.warn("CTX of " + clientId + " was null!");
    }

    /**
     * Sends the current lobby information to all lobbies.
     *
     * @param ctx ChannelHandlerContext - The channel that sent the request
     * @since 0.0.1
     */
    public void sendLobbyInformationToLobbies(ChannelHandlerContext ctx) {
        CloudEvent cloudEvent = new CloudEvent(CloudEvent.RES_LOBBY_INFOS);
        Map<String, Integer> lobbyInformation = new HashMap<>();
        for (Server lobbyServer : ServerHandler.getInstance().getLobbyServers()) {
            lobbyInformation.put(lobbyServer.getServerId(), lobbyServer.getPlayerCount());
        }
        cloudEvent.addData(lobbyInformation);
        for (Map.Entry<String, ChannelHandlerContext> entry : this.clients.entrySet()) {
            if (entry.getKey().toLowerCase().contains("lobby") || entry.getKey().toLowerCase().contains("main")) {
                if (entry.getValue() == ctx) {
                    this.addToQueue(entry.getValue(), cloudEvent);
                } else {
                    this.sendEvent(entry.getValue(), cloudEvent);
                }
            }
        }
    }

    private final Map<ChannelHandlerContext, EventSendingManager> senderMap = new HashMap<>();

    /**
     * Sends an event to the specified CTX
     *
     * @param ctx   ChannelHandlerContext - Channel to send
     * @param event {@link CloudEvent} - Event to send
     * @since 0.0.1
     */
    public void sendEvent(ChannelHandlerContext ctx, Event event) {
        senderMap.get(ctx).sendEvent(event.clone());
    }

    public void addToQueue(ChannelHandlerContext ctx, Event event) {
        senderMap.get(ctx).addQueue(event.clone());
    }

    public void openChannel(ChannelHandlerContext ctx) {
        if (this.senderMap.containsKey(ctx))
            this.senderMap.get(ctx).openChannel();
    }

    public String getClientId(ChannelHandlerContext ctx) {
        for (Map.Entry<String, ChannelHandlerContext> entry : getClients().entrySet()) {
            if (entry.getValue() == ctx)
                return entry.getKey();
        }
        return null;
    }

    /**
     * Gets the {@link io.netty.channel.ChannelHandlerContext} of the discord bot.
     * Could be null if the bot is not registered.
     *
     * @return the ChannelHandlerContext of the bot.
     * @since 0.0.1
     */
    public ChannelHandlerContext getDiscordContext() {
        return this.discordContext;
    }

    public void registerSendingManager(ChannelHandlerContext ctx) {
        senderMap.put(ctx, new EventSendingManager(ctx));
    }

    public boolean receiveACK(ChannelHandlerContext ctx) {
        if (this.senderMap.containsKey(ctx))
            return senderMap.get(ctx).receiveACK();
        return false;
    }

    public void setClientIdToEventSender(ChannelHandlerContext ctx, String main) {
        if(this.senderMap.containsKey(ctx))
            senderMap.get(ctx).setClientID(main);
    }

    public void resendEvent(ChannelHandlerContext ctx) {
        if(this.senderMap.containsKey(ctx))
            senderMap.get(ctx).resendEvent();
    }

    public Server getServerFromCTX(ChannelHandlerContext ctx) {
        String clientId = getClientId(ctx);
        return clientId != null ? serverHandler.getServerByIdentifier(clientId) : null;
    }

}
