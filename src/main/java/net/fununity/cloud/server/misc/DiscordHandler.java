package net.fununity.cloud.server.misc;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import net.fununity.cloud.common.events.discord.DiscordEvent;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.common.utils.MessagingUtils;
import net.fununity.cloud.server.server.Server;

/**
 * Handler class for all discord mechanics and discord util methods.
 * @author Marco Hajek
 * @since 0.0.1
 */
public class DiscordHandler {

    private static DiscordHandler instance;

    private final ClientHandler clientHandler;
    private final ServerHandler serverHandler;

    /**
     * Default constructor for the discord handler.
     * @since 0.0.1
     */
    private DiscordHandler(){
        this.clientHandler = ClientHandler.getInstance();
        this.serverHandler = ServerHandler.getInstance();
    }

    public static DiscordHandler getInstance(){
        if(instance == null)
            instance = new DiscordHandler();
        return instance;
    }

    /**
     * Sends a discord message to all spigot servers.
     * @param event DiscordEvent - the discord event.
     * @since 0.0.1
     */
    public void sendDiscordMessage(DiscordEvent event) {
        for(Server server : this.serverHandler.getServers()){
            if(server.getServerType() == ServerType.BUNGEECORD){
                ChannelHandlerContext ctx = this.clientHandler.getClientContext(server.getServerId());
                if(ctx != null){
                    ctx.writeAndFlush(Unpooled.copiedBuffer(MessagingUtils.convertEventToStream(event).toByteArray()));
                }
            }
        }
    }

    /**
     * Checks whether the discord bot is registered or not.
     * @return True/False
     * @since 0.0.1
     */
    public boolean isDiscordBotConnected(){
        return this.clientHandler.getDiscordContext() != null;
    }
}
