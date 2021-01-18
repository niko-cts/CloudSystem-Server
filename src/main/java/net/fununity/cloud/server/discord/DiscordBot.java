package net.fununity.cloud.server.discord;


import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.Compression;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.fununity.cloud.common.events.discord.DiscordEvent;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.misc.DiscordHandler;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import javax.security.auth.login.LoginException;
import java.util.ArrayList;
import java.util.List;

public class DiscordBot extends ListenerAdapter {

    private static final Logger LOG = Logger.getLogger(DiscordBot.class.getName());

    private JDA bot;
    private CloudServer cloudServer;

    public DiscordBot(){
        PatternLayout layout = new PatternLayout("[%d{HH:mm:ss}] %c{1} [%p]: %m%n");
        ConsoleAppender consoleAppender = new ConsoleAppender(layout);
        LOG.addAppender(consoleAppender);
        LOG.setLevel(Level.INFO);
        LOG.setAdditivity(false);
        this.cloudServer = CloudServer.getInstance();
        JDABuilder builder = JDABuilder.createDefault("NzUyODI0MTY4ODY0Njc3OTA4.X1dQJA.3wtM-LfV1D-rmbi_oV3kF-cgOOc");
        builder.disableCache(CacheFlag.VOICE_STATE);
        builder.setBulkDeleteSplittingEnabled(false);
        builder.setCompression(Compression.NONE);
        builder.setActivity(Activity.playing("auf den Wolken der Cloud."));
        builder.setChunkingFilter(ChunkingFilter.ALL);
        builder.setMemberCachePolicy(MemberCachePolicy.ALL);
        builder.setEnabledIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_EMOJIS, GatewayIntent.GUILD_MESSAGES);
        try {
            builder.addEventListeners(this);
            this.bot = builder.build();
        } catch (LoginException e) {

        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event){
        User author = event.getAuthor();
        Message message = event.getMessage();
        MessageChannel channel = event.getChannel();
        if(author.isBot()) return;

        if(channel instanceof TextChannel){
            TextChannel dcChannel = (TextChannel) channel;
            if(dcChannel.getName().toLowerCase().equalsIgnoreCase("lobby")){
                String toSend = message.getContentStripped();
                List<Member> mentions = message.getMentionedMembers();
                for(Member mentioned : mentions){
                    toSend = toSend.replace("<@!" + mentioned.getId() + ">", mentioned.getEffectiveName());
                }
                DiscordHandler.getInstance().sendDiscordMessage(new DiscordEvent(DiscordEvent.DISCORD_MESSAGE).addData(author.getName()).addData(toSend));
            }
        }
    }

    public void addMessageToChannel(String message){
        TextChannel channel = this.bot.getGuildById(688424563473776681L).getTextChannelById(688424564610695233L);
        if(message.contains("@")){
            List<Integer> indices = new ArrayList<>();
            int index = message.indexOf("@");
            for(; index < message.length(); index++)
                if(message.charAt(index) == '@')
                    indices.add(index);

            List<Member> toReplace = new ArrayList<>();
            for(int i : indices){
                String name = "";
                for(i = i+1; i < message.length(); i++){
                    if(!Character.isWhitespace(message.charAt(i)))
                        name += message.charAt(i);
                    else
                        break;
                }
                List<Member> mentioned = this.bot.getGuildById(688424563473776681L).getMembersByEffectiveName(name, true);
                if(!mentioned.isEmpty())
                    toReplace.add(mentioned.get(0));
            }
            for(Member member : toReplace){
                message = message.replace("@" + member.getEffectiveName(), "<@" + member.getId() + ">");
            }
        }
        channel.sendMessage(message).queue();
    }
}
