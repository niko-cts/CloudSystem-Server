package net.fununity.cloud.server.listeners.discord;

import net.fununity.cloud.common.events.discord.DiscordEvent;
import net.fununity.cloud.common.events.discord.DiscordEventListener;
import net.fununity.cloud.common.utils.MessagingUtils;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.misc.ClientHandler;
import net.fununity.cloud.server.misc.DiscordHandler;

import java.util.Vector;

public class DiscordEvents implements DiscordEventListener {

    private final ClientHandler clientHandler;
    private final DiscordHandler discordHandler;

    public DiscordEvents(){
        clientHandler = ClientHandler.getInstance();
        discordHandler = DiscordHandler.getInstance();
    }

    @Override
    public void newDiscordEvent(DiscordEvent discordEvent) {
        Vector data = discordEvent.getData();
        switch(discordEvent.getId()) {
            case DiscordEvent.DISCORD_MESSAGE:
                StringBuilder message = new StringBuilder();
                message.append("**").append(data.get(0)).append("**").append(" » ").append(data.get(1));
                if(this.discordHandler.isDiscordBotConnected())
                    this.clientHandler.getDiscordContext().writeAndFlush(MessagingUtils.convertEventToStream(new DiscordEvent(DiscordEvent.DISCORD_MESSAGE).addData(message.toString())));
                else
                    CloudServer.getLogger().warn("Received discord message but the discord bot is not registered!");
            break;
        }
    }
}
