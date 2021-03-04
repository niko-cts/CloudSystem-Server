package net.fununity.cloud.server.netty;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.fununity.cloud.common.events.Event;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.events.discord.DiscordEvent;
import net.fununity.cloud.common.utils.MessagingUtils;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.misc.ClientHandler;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.util.HashSet;
import java.util.Set;

public class NettyHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOG = Logger.getLogger(NettyHandler.class.getName());
    private final Set<Long> receivedEvents;

    public NettyHandler() {
        this.receivedEvents = new HashSet<>();
        PatternLayout layout = new PatternLayout("[%d{HH:mm:ss}] %c{1} [%p]: %m%n");
        LOG.addAppender(new ConsoleAppender(layout));
        LOG.setLevel(Level.INFO);
        LOG.setAdditivity(false);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        ClientHandler.getInstance().registerSendingManager(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf inBuf = (ByteBuf) msg;

        Event event = MessagingUtils.convertStreamToEvent(inBuf);

        if(event == null) {
            LOG.warn(ClientHandler.getInstance().getClientId(ctx) + " | Received null event");
            ClientHandler.getInstance().sendResendEvent(ctx);
            return;
        }


        if (receivedEvents.contains(event.getUniqueId())) {
            LOG.warn(ClientHandler.getInstance().getClientId(ctx) + " | " + event.toString() + " event already received!");
            ClientHandler.getInstance().openChannel(ctx);
            return;
        }

        this.receivedEvents.add(event.getUniqueId());

        ClientHandler.getInstance().closeChannel(ctx);


        if (event instanceof CloudEvent) {
            CloudEvent cloudEvent = (CloudEvent) event;

            LOG.info(ClientHandler.getInstance().getClientId(ctx) + " | Received " + cloudEvent );

            switch (cloudEvent.getId()) {
                case CloudEvent.CLOUD_RESEND_REQUEST:
                    ClientHandler.getInstance().resendLastEvent(ctx);
                    return;
                case CloudEvent.CLOUD_QUEUE_EMPTY:
                    ClientHandler.getInstance().receiverQueueEmptied(ctx);
                    break;
                default:
                    cloudEvent.addData(ctx);
                    CloudServer.getInstance().getCloudEventManager().fireCloudEvent(cloudEvent);
            }

        } else if (event instanceof DiscordEvent) {
            DiscordEvent discordEvent = (DiscordEvent) event;
            LOG.info("Received " + discordEvent);
            CloudServer.getInstance().getDiscordEventManager().fireDiscordEvent(discordEvent);
        }

        ctx.flush();
        ClientHandler.getInstance().openChannel(ctx);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        // not needed
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
