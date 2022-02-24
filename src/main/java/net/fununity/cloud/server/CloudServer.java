package net.fununity.cloud.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import net.fununity.cloud.common.events.cloud.CloudEventManager;
import net.fununity.cloud.server.command.CloudConsole;
import net.fununity.cloud.server.listeners.cloud.CloudEvents;
import net.fununity.cloud.server.listeners.cloud.CloudEventsCache;
import net.fununity.cloud.server.listeners.cloud.CloudEventsRequests;
import net.fununity.cloud.server.misc.ClientHandler;
import net.fununity.cloud.server.misc.ConfigHandler;
import net.fununity.cloud.server.misc.ServerHandler;
import net.fununity.cloud.server.netty.NettyHandler;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.net.InetSocketAddress;

public class CloudServer implements Runnable{

    private static final Logger LOG = Logger.getLogger(CloudServer.class.getName());
    private static CloudServer INSTANCE;

    public static CloudServer getInstance(){
        return INSTANCE;
    }

    public static void main(String[] args) {
        PatternLayout layout = new PatternLayout("[%d{HH:mm:ss}] %c{1} [%p]: %m%n");
        LOG.addAppender(new ConsoleAppender(layout));
        LOG.setLevel(Level.INFO);
        LOG.setAdditivity(false);
        new Thread(new CloudServer()).start();
        ConfigHandler.getInstance();
        CloudConsole.getInstance();
    }

    public static Logger getLogger(){
        return LOG;
    }


    private final CloudEventManager cloudEventManager = new CloudEventManager();

    private CloudServer(){
        INSTANCE = this;
    }

    public void run() {
        EventLoopGroup group = new NioEventLoopGroup();
        this.cloudEventManager.addCloudListener(new CloudEvents());
        this.cloudEventManager.addCloudListener(new CloudEventsCache());
        this.cloudEventManager.addCloudListener(new CloudEventsRequests());
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(group);
            bootstrap.channel(NioServerSocketChannel.class);
            bootstrap.localAddress(new InetSocketAddress("localhost", 1337));

            bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel socketChannel) {
                    socketChannel.pipeline().addLast(new NettyHandler());
                }
            });

            ChannelFuture channelFuture = bootstrap.bind().sync();
            channelFuture.channel().closeFuture().sync();
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.log(Level.WARN, e.getMessage());
        } finally {
            group.shutdownGracefully();
        }
    }

    /**
     * Gets the cloud event manager.
     * @see CloudEventManager
     * @since 0.0.1
     * @return CloudEventManager - the cloud event manager.
     */
    public CloudEventManager getCloudEventManager(){
        return this.cloudEventManager;
    }

    /**
     * Shuts down every server and the cloud.
     * @since 0.0.1
     */
    public void shutdownEverything() {
        ServerHandler.getInstance().shutdownAllServers();
        ClientHandler.getInstance().sendDisconnect("DiscordBot");
        /*CloudConsole.getInstance().shutDown();
        try {
            Runtime.getRuntime().exec("screen -ls | grep Detached | cut -d. -f1 | awk '{print $1}' | xargs kill");
            Runtime.getRuntime().exec("exit");
        } catch (IOException e) {
            getLogger().warn(e.getMessage());
        }
        System.exit(0);*/
    }
}