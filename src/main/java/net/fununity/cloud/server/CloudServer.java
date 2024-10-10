package net.fununity.cloud.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.events.cloud.CloudEventManager;
import net.fununity.cloud.server.command.CloudConsole;
import net.fununity.cloud.server.config.ConfigHandler;
import net.fununity.cloud.server.config.NetworkConfig;
import net.fununity.cloud.server.netty.ClientHandler;
import net.fununity.cloud.server.netty.NettyServer;
import net.fununity.cloud.server.netty.listeners.GeneralEventListener;
import net.fununity.cloud.server.netty.listeners.RequestEventListener;
import net.fununity.cloud.server.server.ServerManager;
import net.fununity.cloud.server.util.LogFileCleanerUtil;
import net.fununity.cloud.server.util.ServerUtils;

import java.nio.file.Path;
import java.time.OffsetDateTime;
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
		new CloudServer();
	}

	public static void setInstance(CloudServer instance) {
		INSTANCE = instance;
	}

	private final CloudEventManager cloudEventManager;
	private final NettyServer nettyServer;
	private final ServerManager serverManager;
	private final ConfigHandler configHandler;
	private final ClientHandler clientHandler;
	private final CloudConsole cloudConsole;

	private CloudServer() {
		setInstance(this);
		log.info("Booting up CloudServer...");
		this.nettyServer = new NettyServer(HOSTNAME, PORT);
		this.clientHandler = new ClientHandler();
		this.serverManager = new ServerManager();
		this.configHandler = new ConfigHandler(Path.of("config"));
		this.cloudConsole = new CloudConsole();
		this.cloudEventManager = new CloudEventManager();
		this.cloudEventManager.addCloudListener(new GeneralEventListener());
		this.cloudEventManager.addCloudListener(new RequestEventListener());

		Thread.ofVirtual().name("NettyServer").start(nettyServer);
		Thread.ofVirtual().name("LogFileCleaner").start(new LogFileCleanerUtil(Path.of("logs", "cloud").toFile(), OffsetDateTime.now().minusMonths(3)));
		this.cloudConsole.start();
	}


	/**
	 * Shuts down every server and the cloud.
	 *
	 * @since 0.0.1
	 */
	public void shutdownEverything() {
		if (!serverManager.getRunningServers().isEmpty() && !serverManager.getStartQueue().isEmpty()) {
			log.info("Cloud-Shutdown: {} servers are still up, try to shutdown...", serverManager.getRunningServers().size());
			ServerUtils.shutdownAll();
			return;
		}
		log.info("Cloud-Shutdown: Console shutdown...");
		cloudConsole.shutDown();
		log.info("Cloud-Shutdown: Stopping NettyServer...");
		nettyServer.stop();
		log.info("Everything is down. Bye!");
		System.exit(0);
	}

	public Optional<NetworkConfig> getNetworkConfig() {
		return Optional.ofNullable(getConfigHandler()).map(ConfigHandler::getNetworkConfig);
	}

	public int getMaxRam() {
		return getNetworkConfig().map(NetworkConfig::getMaxRam).orElse(1024);
	}
}