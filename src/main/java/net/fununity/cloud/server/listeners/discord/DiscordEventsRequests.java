package net.fununity.cloud.server.listeners.discord;

import io.netty.buffer.Unpooled;
import net.fununity.cloud.common.events.discord.DiscordEvent;
import net.fununity.cloud.common.events.discord.DiscordEventListener;
import net.fununity.cloud.common.utils.MessagingUtils;
import net.fununity.cloud.server.misc.ClientHandler;
import net.fununity.cloud.server.misc.DiscordHandler;
import net.fununity.cloud.server.misc.ServerHandler;
import net.fununity.cloud.server.server.Server;

/**
 * Event listener for all REQ_* discord events.
 * @author Marco Hajek
 * @version 0.0.1
 */
public class DiscordEventsRequests implements DiscordEventListener {

    private final ClientHandler clientHandler;
    private final ServerHandler serverHandler;
    private final DiscordHandler discordHandler;

    public DiscordEventsRequests(){
        clientHandler = ClientHandler.getInstance();
        serverHandler = ServerHandler.getInstance();
        discordHandler = DiscordHandler.getInstance();
    }

    @Override
    public void newDiscordEvent(DiscordEvent discordEvent) {
        switch(discordEvent.getId()){
            case DiscordEvent.REQ_BUNGEE_ONLINE:
                if(this.discordHandler.isDiscordBotConnected()){
                    boolean isOnline = clientHandler.getClientContext("Main") != null;
                    this.clientHandler.getDiscordContext().writeAndFlush(Unpooled.copiedBuffer(MessagingUtils.convertEventToStream(new DiscordEvent(DiscordEvent.RES_BUNGEE_ONLINE).addData(isOnline)).toByteArray()));
                }
                break;
            case DiscordEvent.REQ_BUNGEE_PLAYERS:
                for(Server server : this.serverHandler.getBungeeServers()){
                    this.clientHandler.getClientContext(server.getServerId()).writeAndFlush(Unpooled.copiedBuffer(MessagingUtils.convertEventToStream(new DiscordEvent(DiscordEvent.REQ_BUNGEE_PLAYERS)).toByteArray()));
                }
                break;
            case DiscordEvent.RES_BUNGEE_PLAYERS:
                if(this.discordHandler.isDiscordBotConnected())
                    this.clientHandler.getDiscordContext().writeAndFlush(Unpooled.copiedBuffer(MessagingUtils.convertEventToStream(discordEvent).toByteArray()));
                else{
                    System.out.println("DISCORDBOT NOT CONNECTED");
                }
                break;
        }
    }
}
