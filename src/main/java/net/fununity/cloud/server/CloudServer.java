package net.fununity.cloud.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.serialization.ClassResolvers;
import io.netty.handler.codec.serialization.ObjectDecoder;
import io.netty.handler.codec.serialization.ObjectEncoder;
import net.fununity.cloud.common.events.cloud.CloudEventManager;
import net.fununity.cloud.common.utils.CloudLogger;
import net.fununity.cloud.server.client.NettyHandler;
import net.fununity.cloud.server.client.listeners.CloudEvents;
import net.fununity.cloud.server.client.listeners.CloudEventsCache;
import net.fununity.cloud.server.client.listeners.CloudEventsRequests;
import net.fununity.cloud.server.command.CloudConsole;
import net.fununity.cloud.server.misc.ConfigHandler;
import net.fununity.cloud.server.server.ServerHandler;

import java.net.InetSocketAddress;

public class CloudServer implements Runnable {

    private static final CloudLogger LOG = CloudLogger.getLogger(CloudServer.class.getSimpleName());
    private static final String HOSTNAME = "localhost";
    private static final int PORT = 1337;

    private static CloudServer INSTANCE;

    public static CloudServer getInstance() {
        return INSTANCE;
    }

    public static void main(String[] args) {
        LOG.info("CloudServer is starting...");
        new Thread(new CloudServer(), "Server").start();
        ConfigHandler.createInstance(args);
        CloudConsole.getInstance();
    }

    public static CloudLogger getLogger() {
        return LOG;
    }

    private final CloudEventManager cloudEventManager;

    private CloudServer() {
        INSTANCE = this;
        cloudEventManager = new CloudEventManager();
    }

    public void run() {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        this.cloudEventManager.addCloudListener(new CloudEvents());
        this.cloudEventManager.addCloudListener(new CloudEventsCache());
        this.cloudEventManager.addCloudListener(new CloudEventsRequests());
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .localAddress(new InetSocketAddress(HOSTNAME, PORT))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) {
                            socketChannel.pipeline().addLast(
                                    new ObjectEncoder(),
                                    new ObjectDecoder(Integer.MAX_VALUE, ClassResolvers.cacheDisabled(null)),
                                    new NettyHandler());
                        }
                    });

            ChannelFuture channelFuture = bootstrap.bind().sync();
            LOG.debug("Opening new ServerBootstrap on %s:%s", HOSTNAME, PORT);
            channelFuture.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.warn("NioSocket was interrupted: " + e.getMessage());
        } finally {
            LOG.debug("Shutting down boss and workergroup...");
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    /**
     * Gets the cloud event manager.
     *
     * @return CloudEventManager - the cloud event manager.
     * @see CloudEventManager
     * @since 0.0.1
     */
    public CloudEventManager getCloudEventManager() {
        return this.cloudEventManager;
    }

    /**
     * Shuts down every server and the cloud.
     *
     * @since 0.0.1
     */
    public void shutdownEverything() {
        if (!ServerHandler.getInstance().getServers().isEmpty()) {
            LOG.debug("Exit Cloud: %s servers are still up, try to shutdown...", ServerHandler.getInstance().getServers().size());
            ServerHandler.getInstance().exitCloud();
            return;
        }
        LOG.debug("Exit Cloud: Console shutdown...");
        CloudConsole.getInstance().shutDown();
        System.exit(0);
    }
}