package net.fununity.cloud.server.misc;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.utils.MessagingUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Handler class for all cloud clients.
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
     * @since 0.0.1
     */
    private ClientHandler(){
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
     * @return ClientHandler - the instance of the clienthandler.
     * @since 0.0.1
     */
    public static ClientHandler getInstance(){
        if(instance == null)
            instance = new ClientHandler();
        return instance;
    }

    /**
     * Gets all saved client ids and their mapped ChannelHandlerContext.
     * @return ConcurrentHashMap<String, ChannelHandlerContext> - the map of clients.
     * @since 0.0.1
     */
    public ConcurrentMap<String, ChannelHandlerContext> getClients(){
        return new ConcurrentHashMap<>(this.clients);
    }

    /**
     * Returns a client based on the given client id or null.
     * @param clientId String - the client id.
     * @return ChannelHandlerContext - the context of the client or null.
     * @since 0.0.1
     */
    public ChannelHandlerContext getClientContext(String clientId){
        return this.clients.getOrDefault(clientId, null);
    }

    /**
     * Saves a client to the clients map.
     * @param clientId String - the id of the client.
     * @param ctx ChannelHandlerContext - the context of the client.
     * @since 0.0.1
     */
    public void saveClient(String clientId, ChannelHandlerContext ctx){
        this.clients.putIfAbsent(clientId, ctx);
        LOG.info("Registered client: " + clientId);
        if(clientId.equalsIgnoreCase("CloudBot"))
            this.discordContext = ctx;
        ctx.channel().closeFuture().addListener(channelFuture -> removeClient(ctx));
    }

    /**
     * Removes a client based on the given client id.
     * @param clientId String - the client id.
     * @since 0.0.1
     */
    public void removeClient(String clientId){
        this.clients.remove(clientId);
        if(clientId.equalsIgnoreCase("CloudBot"))
            this.discordContext = null;
    }

    /**
     * Removes a client based on the given ChannelHandlerContext.
     * @param ctx ChannelHandlerContext - the context.
     * @since 0.0.1
     */
    public void removeClient(ChannelHandlerContext ctx){
        for(Map.Entry<String, ChannelHandlerContext> entry : this.clients.entrySet()){
            if(entry.getValue() == ctx){
                this.clients.remove(entry.getKey());
                return;
            }
        }
    }

    /**
     * Remaps the ChannelHandlerContext to the correct server id.
     * @param ctx ChannelHandlerContext - the channel handler context.
     * @param port int - the port of the server.
     * @since 0.0.1
     */
    public void remapChannelHandlerContext(ChannelHandlerContext ctx, int port) {
        String serverId = "";
        for(Map.Entry<String, ChannelHandlerContext> entry : this.clients.entrySet()){
            if(ctx == entry.getValue()){
                serverId = entry.getKey();
                break;
            }
        }

        this.clients.remove(serverId);
        this.clients.putIfAbsent(this.serverHandler.getServerIdentifierByPort(port), ctx);
    }

    /**
     * Sends a disconnect message to a specific client.
     * @param clientId String - the client id.
     * @since 0.0.1
     */
    public void sendDisconnect(String clientId){
        ChannelHandlerContext ctx = this.getClientContext(clientId);
        if(ctx != null){
            ctx.writeAndFlush(Unpooled.copiedBuffer(MessagingUtils.convertEventToStream(new CloudEvent(CloudEvent.CLIENT_DISCONNECT_GRACEFULLY)).toByteArray()));
        }else LOG.warn("CTX of " + clientId + " was null!");
    }

    /**
     * Sends the current lobby count to all known clients.
     * @since 0.0.1
     */
    public void sendLobbyCountToAllClients(){
        CloudEvent cloudEvent = new CloudEvent(CloudEvent.RES_LOBBY_COUNT);
        cloudEvent.addData(this.serverHandler.getLobbyCount());
        for(Map.Entry<String, ChannelHandlerContext> entry : this.clients.entrySet()){
            entry.getValue().writeAndFlush(Unpooled.copiedBuffer(MessagingUtils.convertEventToStream(cloudEvent).toByteArray()));
        }
    }

    /**
     * Gets the {@link io.netty.channel.ChannelHandlerContext} of the discord bot.
     * Could be null if the bot is not registered.
     * @return the ChannelHandlerContext of the bot.
     * @since 0.0.1
     */
    public ChannelHandlerContext getDiscordContext(){
        return this.discordContext;
    }
}
