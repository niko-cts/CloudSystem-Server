package net.fununity.cloud.server.server;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.server.ServerState;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.common.util.RandomUtil;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.command.DebugCommand;
import net.fununity.cloud.server.config.ServerConfig;
import net.fununity.cloud.server.server.shutdown.ServerShutdown;
import net.fununity.cloud.server.server.shutdown.ServerStopper;
import net.fununity.cloud.server.server.start.ServerAliveChecker;
import net.fununity.cloud.server.server.util.ServerUtils;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Abstract class to define the basic server instance.
 *
 * @author Niko
 * @since 0.0.1
 */
@EqualsAndHashCode
@ToString
@Slf4j
public final class Server {

	private static final String FILE_START = "start.sh";
	private static final String FILE_STATUS = "serverStatus.sh";
	private static final String FILE_KILL = "killServer.sh";
	private static final String FILE_log = "logs/latest.log";

	private static final String FILE_SERVER_PROPERTIES = "server.properties";

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

	final String serverPath;
	private final String backupPath;
	private String savelogfilePrefix;

	@Setter
	@Getter
	@Nullable
	private ServerShutdown shutdownProcess;
	@Nullable
	private ServerAliveChecker aliveChecker;
	@Nullable
	private ServerStopper serverStopper;

	@Getter
	@Setter
	private boolean markedForStop;

	public Server(@NotNull String serverId, @NotNull String serverName, @NotNull  String serverIp, int serverPort, @NotNull ServerConfig config) {
		this.serverId = serverId;
		this.serverIp = serverIp;
		this.serverName = serverName;
		this.serverPort = serverPort;
		this.config = config;
		this.playerCount = 0;
		this.shutdownProcess = null;
		this.maxPlayers = config.getMaxPlayers();
		this.serverPath = new StringBuilder()
				.append("./Servers/")
				.append(this.serverType == ServerType.BUNGEECORD ? "BungeeCord/" : "Spigot/")
				.append(this.serverId).append("/").toString();
		this.backupPath = new StringBuilder()
				.append("./Servers/Backups/")
				.append(this.serverId).append("/").toString();
	}

	public Server(@NotNull String serverName, @NotNull String serverIp, @NotNull ServerType serverType) {
		this(serverName + "-" + RandomUtil.getRandomString(10), serverName,
				serverIp, serverType == ServerType.BUNGEECORD ? 25565 : ServerUtils.getNextFreeServerPort(),
				CloudServer.getInstance().getConfigHandler().getByServerConfigByType(serverType).orElseThrow(() -> new IllegalStateException("Instance of servertype was created but was not configured: " + serverType)));
	}

	/**
	 * Creates the server directory and copies the template if it doesn't exist.
	 *
	 * @since 0.0.1
	 */
	public void createFiles() throws IOException {
		File serverDirectory = new File(this.serverPath);
		if (serverDirectory.exists()) {
			log.warn("Server directory for {} already exist. Delete to create a new server...", serverId);
			FileUtils.deleteDirectory(serverDirectory);
		}

		String copyPath;
		if (new File(this.backupPath).exists()) {
			copyPath = this.backupPath;
			log.debug("Copying backup for {} out of {}", serverId, copyPath);
		} else {
			copyPath = ServerUtils.getTemplatePath(this.serverType);
			log.debug("Copying template for {} out of {}", serverId, copyPath);
		}

		FileUtils.copyDirectory(new File(copyPath), serverDirectory);
	}

	public void setFileServerProperties() throws IOException {
		try {
			if (this.serverType != ServerType.BUNGEECORD) {
				File file = new File(this.serverPath + FILE_SERVER_PROPERTIES);
				if (file.createNewFile()) {
					BufferedWriter writer = new BufferedWriter(new FileWriter(file));
					writer.write("""
							server-ip={}
							server-port={}
							motd={}
							max-players={}
							allow-flight=true
							online-mode=false
							allow-nether=false
							""".formatted(this.serverIp, this.serverPort, this.serverMotd, this.maxPlayers));
					writer.flush();
					writer.close();
				}
			}
		} catch (IOException e) {
		}
	}

	/**
	 * Tries to start the server instance.
	 *
	 * @since 0.0.1
	 */
	public void start() throws IllegalStateException {
		if (isRunning()) {
			log.warn("Tried to start {}, but is already running!", serverId);
			ServerHandler.getInstance().checkStartQueue(this);
			return;
		}

		File file = new File(this.serverPath + FILE_START);
		if (!file.exists()) {
			throw new IllegalStateException(FILE_START + " for server " + serverId + " does not exist: " + file.getPath());
		}

		try {
			new ProcessBuilder("sh", file.getPath(), this.serverPath, this.serverId, this.serverMaxRam).start();
			if (serverType != ServerType.BUNGEECORD)
				this.aliveChecker = new ServerAliveChecker(this);
		} catch (IOException e) {
			throw new IllegalStateException("Could execute stop command for server " + serverId + ": " + e.getMessage());
		}
	}

	public boolean isRunning() {
		File file = new File(this.serverPath + FILE_STATUS);
		if (!file.exists()) {
			log.error("{} for server {} does not exist!", serverId, FILE_STATUS);
			return false;
		}
		try {
			int result = new ProcessBuilder("sh", this.serverPath + FILE_STATUS, serverId).start().waitFor();
			return result == 1;
		} catch (InterruptedException | IOException exception) {
			log.error("Could not check if server {} is running: {} ", serverId, exception.getMessage());
		}
		return false;
	}

	/**
	 * Tries to stop a server.
	 *
	 * @since 0.0.1
	 */
	public void stop() {
		log.debug("Trying to stop server {}", serverId);
		createStopperIfNotExist().executeState(ServerStopper.ServerStoppingState.REQ_BUNGEECORD_REMOVE);
	}

	boolean kill() {
		serverStopped();
		File file = new File(serverPath + FILE_KILL);
		if (!file.exists()) {
			log.error("{} for {} does not be exist!", FILE_KILL, serverId);
			return false;
		}

		try {
			log.debug("Killing server {} via sh script...", serverId);
			new ProcessBuilder().command("sh", file.getPath(), serverId).start();
			return true;
		} catch (IOException e) {
			log.warn("Could not kill server {} because of: {}", serverId, e.getMessage());
		}
		return false;
	}

	public void clientDisconnected() {
		this.serverState = ServerState.STOPPED;
		createStopperIfNotExist().executeState(ServerStopper.ServerStoppingState.RES_CLIENT_DISCONNECTED);
	}

	public void flushServer() {
		log.info("Flushing server {}... Will save logfile.", serverId);
		setSavelogFile("flush");
		createStopperIfNotExist().flushServer();
	}

	public void deleteServer() {
		createStopperIfNotExist().executeState(ServerStopper.ServerStoppingState.EXECUTE_DELETE_AND_CLEANUP);
	}

	private ServerStopper createStopperIfNotExist() {
		if (serverStopper == null) {
			serverStopper = new ServerStopper(this);
		}
		return serverStopper;
	}

	void savelogFile() {
		File logFile = new File(this.serverPath + "/" + FILE_log);
		if (!logFile.exists()) {
			log.error("Could not save logfile {} for server {}: Does not exist", FILE_log, serverId);
			return;
		}

		String filename = String.format("%s-%s.log", getlogFilePrefix(), OffsetDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy-HH:mm:ss")));

		try {
			Path savePath = DebugCommand.DEBUG_OUTPUT.resolve(serverId + "/" + filename);
			FileUtils.copyFile(logFile, savePath.toFile(), true);
			log.info("logfile saved for server {} in {}", serverId, savePath);
		} catch (IOException e) {
			log.error("Could not save log file for server {}: {}", serverId, e.getMessage());
		}
	}

	/**
	 * Moves the current server to the backup path.
	 *
	 * @since 0.0.1
	 */
	public void moveToBackup(boolean copy) throws IOException {
		File backupFile = new File(this.backupPath);
		if (!backupFile.exists()) {
			backupFile.mkdirs();
		}

		FileUtils.deleteDirectory(backupFile);

		if (copy)
			FileUtils.copyDirectory(new File(this.serverPath), backupFile, false);
		else
			FileUtils.moveDirectory(new File(this.serverPath), backupFile);
		log.debug("Server {} backed up: {}", serverId, this.serverPath);
	}

	void serverStopped() {
		this.serverState = ServerState.STOPPED;
	}

	public boolean isStopped() {
		return this.serverState == ServerState.STOPPED;
	}

	void stopAliveChecker() {
		if (this.aliveChecker != null)
			this.aliveChecker.stopTimer();
	}

	public void receivedClientAliveResponse() {
		if (this.aliveChecker != null)
			this.aliveChecker.receivedEvent();
	}

	public void setSavelogFile(String savelogfilePrefix) {
		this.savelogfilePrefix = savelogfilePrefix;
	}

	public String getlogFilePrefix() {
		return savelogfilePrefix;
	}

	public void bungeeRemovedServer() {
		// TODO
	}

	public void markForStop() {
		this.markedForStop = true;
	}
}
