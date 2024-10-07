package net.fununity.cloud.server.server;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.server.ServerState;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.common.util.RandomUtil;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.config.ServerConfig;
import net.fununity.cloud.server.server.shutdown.ServerShutdown;
import net.fununity.cloud.server.server.start.ServerAliveChecker;
import net.fununity.cloud.server.server.util.ServerUtils;
import net.fununity.cloud.serverstarter.PluginLocation;
import net.fununity.cloud.serverstarter.ServerStarter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * Class to define the basic server instance.
 *
 * @author Niko
 * @since 0.0.1
 */
@EqualsAndHashCode
@Slf4j
public final class Server {


	@Getter
	@NotNull
	private final String serverId;
	@Getter
	@NotNull
	private final String serverIp;
	@Getter
	private final int serverPort;
	@Getter
	@NotNull
	private final String serverName;
	@Getter
	@NotNull
	private final ServerConfig config;

	@Setter
	@Getter
	private int playerCount;
	@Setter
	@Getter
	private int maxPlayers;
	@Setter
	@Getter
	private ServerState serverState;

	@Nullable
	private ServerAliveChecker aliveChecker;
	@Setter
	@Getter
	@Nullable
	private ServerShutdown shutdownProcess;
	@Getter
	@Setter
	private boolean markedForStop;

	public Server(@NotNull String serverId, @NotNull String serverName, @NotNull String serverIp, int serverPort, @NotNull ServerConfig config) {
		this.serverId = serverId;
		this.serverIp = serverIp;
		this.serverName = serverName;
		this.serverPort = serverPort;
		this.config = config;
		this.playerCount = 0;
		this.shutdownProcess = null;
		this.maxPlayers = config.getMaxPlayers();
		this.serverState = ServerState.IDLE;
	}

	public Server(@NotNull String serverName, @NotNull String serverIp, @NotNull ServerType serverType) {
		this(serverName + "-" + RandomUtil.getRandomString(10), serverName,
				serverIp, serverType == ServerType.BUNGEECORD ? 25565 : ServerUtils.getNextFreeServerPort(),
				CloudServer.getInstance().getConfigHandler().getByServerConfigByType(serverType)
						.orElseThrow(() -> new IllegalStateException("Instance of servertype was created but was not configured: " + serverType)));
	}


	/**
	 * Starts the server instance. Calls {@link ServerStarter#loadPluginsAndStartServerDockerized()}
	 *
	 * @since 0.0.1
	 */
	public void start() throws IllegalStateException {
		if (isRunning()) {
			log.warn("Tried to start {}, but is already running!", serverId);
			return;
		}
		File serverDir = new File(config.getDirectory());
		if (!serverDir.exists() || !serverDir.isDirectory()) {
			log.error("Server directory {} does not exist or is not a directory. Can not start server: {}", serverDir, serverId);
			return;
		}

		this.serverState = ServerState.BOOTING;
		this.aliveChecker = new ServerAliveChecker(this);
		new ServerStarter(config.getServerType().name(), serverId, serverPort, serverDir,
				CloudServer.getInstance().getConfigHandler().getByNames(config.getPlugins())
						.stream().map(plugin -> new PluginLocation(new File(plugin.getLocalPath()), plugin.getNexusPluginUrl())).toList())
				.loadPluginsAndStartServerDockerized();
	}

	public void stop() {
		log.info("Stopping server {}", serverId);
		this.serverState = ServerState.STOPPING;
		// TODO
	}


	public void flushServer() {
		log.info("Flushing server {}", serverId);
		this.serverState = ServerState.STOPPING;
		// TODO
	}

	public void bungeeRemovedServer() {
		log.debug("Bungee removed Server {}", serverId);
		// TODO
	}

	public void clientDisconnected() {
		this.serverState = ServerState.IDLE;
		log.debug("Server {} disconnected", serverId);
		// TODO
	}


	void serverStopped() {
		this.serverState = ServerState.IDLE;
	}

	public boolean isStopped() {
		return this.serverState == ServerState.IDLE;
	}

	public boolean isRunning() {
		return !isStopped();
	}

	void stopAliveChecker() {
		if (this.aliveChecker != null)
			this.aliveChecker.stopTimer();
	}

	public void receivedClientAliveResponse() {
		if (this.aliveChecker != null)
			this.aliveChecker.receivedEvent();
	}

	public void markForStop() {
		this.markedForStop = true;
	}

	@Override
	public String toString() {
		return "Server{" +
		       "serverId='" + serverId + '\'' +
		       ", serverName='" + serverName + '\'' +
		       ", serverIp='" + serverIp + '\'' +
		       ", serverPort=" + serverPort +
		       ", markedForStop=" + markedForStop +
		       ", playerCount=" + playerCount +
		       ", maxPlayers=" + maxPlayers +
		       ", config=" + config +
		       '}';
	}
}
