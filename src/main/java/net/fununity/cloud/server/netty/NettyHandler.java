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

    private final Set<Long> receivedEvents;
    private Logger log = null;

    public NettyHandler() {
        this.receivedEvents = new HashSet<>();
    }

    private void setupLog(String name) {
        this.log = Logger.getLogger(NettyHandler.class.getName() + "-" + name);
        PatternLayout layout = new PatternLayout("[%d{HH:mm:ss}] %c{1} [%p]: %m%n");
        log.addAppender(new ConsoleAppender(layout));
        log.setLevel(Level.INFO);
        log.setAdditivity(false);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        ClientHandler.getInstance().registerSendingManager(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ClientHandler.getInstance().closeChannel(ctx);

        Event event = MessagingUtils.convertStreamToEvent( (ByteBuf) msg);

        if(event== null) {
            if(log != null)
                log.warn("Received null event");
            ClientHandler.getInstance().sendResendEvent(ctx);
            return;
        }

        if (receivedEvents.contains(event.getUniqueId())) {
            if (log != null)
                log.warn(event.toString() + " event already received!");
            ClientHandler.getInstance().receiverQueueEmptied(ctx);
            ClientHandler.getInstance().openChannel(ctx);
            return;
        }

        this.receivedEvents.add(event.getUniqueId());

        if (log != null)
            log.info("Received " + event);

        if (event instanceof CloudEvent) {
            CloudEvent cloudEvent = (CloudEvent) event;

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
                    break;
            }

        } else if (event instanceof DiscordEvent) {
            DiscordEvent discordEvent = (DiscordEvent) event;
            CloudServer.getInstance().getDiscordEventManager().fireDiscordEvent(discordEvent);
        }

        if(log == null && ClientHandler.getInstance().getClientId(ctx) != null)
            setupLog(ClientHandler.getInstance().getClientId(ctx));

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
