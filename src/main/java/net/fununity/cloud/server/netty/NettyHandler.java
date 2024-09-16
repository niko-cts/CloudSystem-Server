package net.fununity.cloud.server.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.server.CloudServer;
import org.jetbrains.annotations.Nullable;

@Slf4j
public class NettyHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        log.debug("New channel active: {}", ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof CloudEvent cloudEvent) {
            log.debug("Received from '{}' (CTX={}) event {}", getId(ctx), ctx.channel(), cloudEvent);
            cloudEvent.addData(ctx);
            CloudServer.getInstance().getCloudEventManager().fireCloudEvent(cloudEvent);
        } else {
            log.error("Received object from {} which was not an event!", getId(ctx));
        }
    }


    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        super.channelReadComplete(ctx);
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Exception caught for '{}' (CTX={}): {}", getId(ctx), ctx.channel(), cause.getMessage());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        String clientId = getId(ctx);
        CloudServer.getInstance().getServerManager().getServerByIdentifier(clientId).ifPresentOrElse(
                server -> {
                    log.debug("Client offline (inactive): {} - {}", server.getServerId(), ctx.channel());
                    server.clientDisconnected();
                },
                () -> log.debug("Client offline (inactive): {}", ctx.channel())
        );
    }


    private @Nullable String getId(ChannelHandlerContext ctx) {
        return CloudServer.getInstance().getClientHandler().getClientId(ctx);
    }
}
