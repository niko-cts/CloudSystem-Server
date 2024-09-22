package net.fununity.cloud.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.events.cloud.CloudEventManager;
import net.fununity.cloud.server.command.CloudConsole;
import net.fununity.cloud.server.config.ConfigHandler;
import net.fununity.cloud.server.config.NetworkConfig;
import net.fununity.cloud.server.netty.ClientHandler;
import net.fununity.cloud.server.netty.NettyServer;
import net.fununity.cloud.server.netty.listeners.CacheEventListener;
import net.fununity.cloud.server.netty.listeners.GeneralEventListener;
import net.fununity.cloud.server.netty.listeners.RequestEventListener;
import net.fununity.cloud.server.server.ServerManager;
import net.fununity.cloud.server.server.util.ServerUtils;

import java.util.Optional;

@Getter
@Slf4j
public class CloudServer {

    private static final String HOSTNAME = "localhost";
    private static final int PORT = 1337;

    private static CloudServer INSTANCE;

    public static CloudServer getInstance() {
        return INSTANCE;
    }

    public static void main(String[] args) {
        new CloudServer(args);
    }

    private final CloudEventManager cloudEventManager;
    private final NettyServer nettyServer;
    private final ServerManager serverManager;
    private final ConfigHandler configHandler;
    private final ClientHandler clientHandler;
    private final CloudConsole cloudConsole;

    private CloudServer(String[] args) {
        INSTANCE = this;
        log.info("Booting up CloudServer...");
        this.cloudEventManager = new CloudEventManager();
        this.cloudEventManager.addCloudListener(new GeneralEventListener());
        this.cloudEventManager.addCloudListener(new RequestEventListener());
        this.cloudEventManager.addCloudListener(new CacheEventListener());
        this.nettyServer = new NettyServer(HOSTNAME, PORT);
        this.clientHandler = new ClientHandler();
        this.serverManager = new ServerManager();
        this.configHandler = new ConfigHandler(args);
        this.cloudConsole = new CloudConsole();

        Thread.ofVirtual().name("NettyServer").start(nettyServer);
        Thread.ofVirtual().name("CloudConsole").start(cloudConsole);
    }

	/**
     * Shuts down every server and the cloud.
     *
     * @since 0.0.1
     */
    public void shutdownEverything() {
        if (!serverManager.getRunningServers().isEmpty() && !serverManager.getStartQueue().isEmpty()) {
            log.debug("Exit Cloud: {} servers are still up, try to shutdown...", serverManager.getRunningServers().size());
            ServerUtils.shutdownAll();
            return;
        }
        log.debug("Exit Cloud: Console shutdown...");
        cloudConsole.shutDown();
        log.debug("Exit Cloud: Stopping NettyServer...");
        nettyServer.stop();
        System.exit(0);
    }

    public Optional<NetworkConfig> getNetworkConfig() {
        return Optional.ofNullable(getConfigHandler()).map(ConfigHandler::getNetworkConfig);
    }
}