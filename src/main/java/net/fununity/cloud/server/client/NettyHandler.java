package net.fununity.cloud.server.client;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.utils.CloudLogger;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.server.Server;
import net.fununity.cloud.server.server.ServerHandler;

public class NettyHandler extends ChannelInboundHandlerAdapter {

    private static final CloudLogger LOG = ClientHandler.getLogger();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof CloudEvent cloudEvent) {
            LOG.debug("Received from '%s' (CTX=%s) event %s", getId(ctx), ctx.channel(), cloudEvent);
            cloudEvent.addData(ctx);
            CloudServer.getInstance().getCloudEventManager().fireCloudEvent(cloudEvent);
        } else {
            LOG.error("Received object from %s which was not an event!", getId(ctx));
        }
    }


    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        super.channelReadComplete(ctx);
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOG.error("Exception caught for '%s' (CTX=%s): %s", getId(ctx), ctx.channel(), cause.getMessage());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        String clientId = ClientHandler.getInstance().getClientId(ctx);
        LOG.debug("Client offline (inactive): %s - %s", clientId, ctx.channel());
        Server server = ServerHandler.getInstance().getServerByIdentifier(clientId);
        if (server != null) {
            server.clientDisconnected();
        }
    }

    private String getId(ChannelHandlerContext ctx) {
        return ClientHandler.getInstance().getClientId(ctx);
    }
}
