package net.fununity.cloud.server.server;

import net.fununity.cloud.common.server.ServerState;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.common.utils.CloudLogger;
import net.fununity.cloud.server.command.DebugCommand;
import net.fununity.cloud.server.misc.ServerUtils;
import org.apache.commons.io.FileUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract class to define the basic server instance.
 *
 * @author Niko
 * @since 0.0.1
 */
public final class Server {

    private static final String FILE_START = "start.sh";
    private static final String FILE_STATUS = "serverStatus.sh";
    private static final String FILE_KILL = "killServer.sh";
    private static final String FILE_LOG = "logs/latest.log";

    private static final String FILE_SERVER_PROPERTIES = "server.properties";
    private static final CloudLogger LOG = CloudLogger.getLogger(Server.class.getSimpleName());


    private final String serverId;
    private final String serverIp;
    private final int serverPort;
    private final ServerType serverType;
    private final String serverMaxRam;
    private final String serverMotd;

    private ServerState serverState;

    final String serverPath;
    private final String backupPath;
    private final AtomicInteger maxPlayers;
    private final AtomicInteger playerCount;
    private String saveLogfilePrefix;
    private ServerShutdown shutdownProcess;
    private ServerAliveChecker aliveChecker;
    private ServerStopper serverStopper;

    /**
     * Creates a new server instance.
     *
     * @param serverId   String - the identifier of the server.
     * @param serverIp   String - the ip of the server.
     * @param serverPort int - the port of the server.
     * @param maxRam     String - the Xmx java string.
     * @param motd       String - the motd of the server.
     * @param serverType ServerType - the type of the server.
     * @see ServerType
     * @since 0.0.1
     */
    public Server(String serverId, String serverIp, int serverPort, String maxRam, String motd, int maxPlayers, ServerType serverType) {
        this.serverId = serverId;
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.serverType = serverType;
        this.serverState = ServerState.IDLE;
        this.serverMaxRam = maxRam;
        this.serverMotd = motd;
        this.maxPlayers = new AtomicInteger(maxPlayers);
        this.playerCount = new AtomicInteger(0);
        this.shutdownProcess = null;
        this.serverPath = new StringBuilder()
                .append("./Servers/")
                .append(this.serverType == ServerType.BUNGEECORD ? "BungeeCord/" : "Spigot/")
                .append(this.serverId).append("/").toString();
        this.backupPath = new StringBuilder()
                .append("./Servers/Backups/")
                .append(this.serverId).append("/").toString();
    }

    /**
     * Creates a server with the optimal port, ram and motd.
     *
     * @param serverId   String - the identifier of the server.
     * @param serverIp   String - the ip of the server.
     * @param serverType ServerType - the type of the server.
     * @author Niko
     * @see ServerType
     * @since 0.0.1
     */
    public Server(String serverId, String serverIp, ServerType serverType) {
        this(serverId, serverIp, ServerHandler.getInstance().getOptimalPort(serverType), ServerUtils.getRamFromType(serverType) + "M", serverId, ServerUtils.getMaxPlayersOfServerType(serverType), serverType);
    }

    /**
     * Adds the number of players by one.
     * Creates a new lobby if the player count of a lobby goes above 20
     *
     * @param playerCount int - The number of players on this server
     * @since 0.0.1
     */
    public void setPlayerCount(int playerCount) {
        this.playerCount.set(playerCount);
    }

    /**
     * Creates the server directory and copies the template if it doesn't exist.
     *
     * @since 0.0.1
     */
    public void createFiles() throws IOException {
        File serverDirectory = new File(this.serverPath);
        if (serverDirectory.exists()) {
            LOG.warn("Server directory for %s already exist. Delete to create a new server...", serverId);
            FileUtils.deleteDirectory(serverDirectory);
        }

        String copyPath;
        if (new File(this.backupPath).exists()) {
            copyPath = this.backupPath;
            LOG.debug("Copying backup for %s out of %s", serverId, copyPath);
        } else {
            copyPath = ServerUtils.getTemplatePath(this.serverType);
            LOG.debug("Copying template for %s out of %s", serverId, copyPath);
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
                            server-ip=%s
                            server-port=%s
                            motd=%s
                            max-players=%s
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
            LOG.warn("Tried to start %s, but is already running!", serverId);
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
            LOG.error("%s for server %s does not exist!", serverId, FILE_STATUS);
            return false;
        }
        try {
            int result = new ProcessBuilder("sh", this.serverPath + FILE_STATUS, serverId).start().waitFor();
            return result == 1;
        } catch (InterruptedException | IOException exception) {
            LOG.error("Could not check if server %s is running: %s ", serverId, exception.getMessage());
        }
        return false;
    }

    /**
     * Tries to stop a server.
     *
     * @since 0.0.1
     */
    public void stop() {
        LOG.debug("Trying to stop server %s", serverId);
        createStopperIfNotExist().executeState(ServerStopper.ServerStoppingState.REQ_BUNGEECORD_REMOVE);
    }

    boolean kill() {
        serverStopped();
        File file = new File(serverPath + FILE_KILL);
        if (!file.exists()) {
            LOG.error("%s for %s does not be exist!", FILE_KILL, serverId);
            return false;
        }

        try {
            LOG.debug("Killing server %s via sh script...", serverId);
            new ProcessBuilder().command("sh", file.getPath(), serverId).start();
            return true;
        } catch (IOException e) {
            LOG.warn("Could not kill server %s because of: %s", serverId, e.getMessage());
        }
        return false;
    }

    public void clientDisconnected() {
        this.serverState = ServerState.STOPPED;
        createStopperIfNotExist().executeState(ServerStopper.ServerStoppingState.RES_CLIENT_DISCONNECTED);
    }

    public void flushServer() {
        LOG.info("Flushing server %s... Will save logfile.", serverId);
        setSaveLogFile("flush");
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

    void saveLogFile() {
        File logFile = new File(this.serverPath + "/" + FILE_LOG);
        if (!logFile.exists()) {
            LOG.error("Could not save logfile %s for server %s: Does not exist", FILE_LOG, serverId);
            return;
        }

        String filename = String.format("%s-%s.log", getLogFilePrefix(), OffsetDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy-HH:mm:ss")));

        try {
            Path savePath = DebugCommand.DEBUG_OUTPUT.resolve(serverId + "/" + filename);
            FileUtils.copyFile(logFile, savePath.toFile(), true);
            LOG.info("Logfile saved for server %s in %s", serverId, savePath);
        } catch (IOException e) {
            LOG.error("Could not save log file for server %s: %s", serverId, e.getMessage());
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
        LOG.debug("Server %s backed up: %s", serverId, this.serverPath);
    }

    void serverStopped() {
        this.serverState = ServerState.STOPPED;
    }

    boolean isStopped() {
        return this.serverState == ServerState.STOPPED;
    }

    /**
     * Sets remove confirmation to true.
     * Remove confirmation needs to be sent from bungee, so the server can finally be stopped.
     *
     * @param shutdownProcess {@link ServerShutdown} - the process instance.
     * @since 0.0.1
     */
    public void setShutdownProcess(ServerShutdown shutdownProcess) {
        this.shutdownProcess = shutdownProcess;
    }

    void stopAliveChecker() {
        if (this.aliveChecker != null)
            this.aliveChecker.stopTimer();
    }

    public void receivedClientAliveResponse() {
        if (this.aliveChecker != null)
            this.aliveChecker.receivedEvent();
    }


    public ServerState getServerState() {
        return isRunning() ? ServerState.RUNNING : this.serverState;
    }

    /**
     * Sets the max players of the server.
     *
     * @param maxPlayers int - max players
     */
    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers.set(maxPlayers);
    }


    /**
     * Check if the server has received remove confirmation from bungee.
     *
     * @return IServerShutdown - remove confirmation.
     * @since 0.0.1
     */
    public ServerShutdown getShutdownProcess() {
        return shutdownProcess;
    }

    /**
     * Gets the server identifier.
     *
     * @return String - the identifier.
     * @since 0.0.1
     */
    public String getServerId() {
        return this.serverId;
    }

    /**
     * Gets the server ip.
     *
     * @return String - the ip.
     * @since 0.0.1
     */
    public String getServerIp() {
        return this.serverIp;
    }

    /**
     * Gets the server port.
     *
     * @return int - the port.
     * @since 0.0.1
     */
    public int getServerPort() {
        return this.serverPort;
    }

    /**
     * Gets the motd of the server.
     *
     * @return String - the motd.
     * @since 0.0.1
     */
    public String getServerMotd() {
        return this.serverMotd;
    }

    /**
     * Get the number of maximum players of the server
     *
     * @return int - the maximum number of players of the server
     * @since 0.0.1
     */
    public int getMaxPlayers() {
        return maxPlayers.get();
    }

    /**
     * Gets the servers maxram configuration.
     *
     * @return String - the max ram config.
     * @since 0.0.1
     */
    public String getServerMaxRam() {
        return this.serverMaxRam;
    }

    /**
     * Gets the server type.
     *
     * @return ServerType - the type.
     * @see ServerType
     * @since 0.0.1
     */
    public ServerType getServerType() {
        return this.serverType;
    }

    /**
     * Get the number of current players on the server
     *
     * @return int - Number of players
     * @since 0.0.1
     */
    public int getPlayerCount() {
        return playerCount.get();
    }

    public void setSaveLogFile(String saveLogfilePrefix) {
        this.saveLogfilePrefix = saveLogfilePrefix;
    }

    public String getLogFilePrefix() {
        return saveLogfilePrefix;
    }

    @Override
    public String toString() {
        return "Server{" +
               "serverId='" + serverId + '\'' +
               ", serverPort=" + serverPort +
               ", serverType=" + serverType +
               ", serverState=" + serverState +
               ", serverPath='" + serverPath + '\'' +
               ", playerCount=" + playerCount +
               ", maxPlayers=" + maxPlayers +
               ", shutdownProcess=" + (shutdownProcess != null) +
               ", aliveChecker=" + (aliveChecker != null) +
               ", serverStopper=" + (serverStopper != null) +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Server server = (Server) o;
        return serverPort == server.serverPort && Objects.equals(serverId, server.serverId) && Objects.equals(serverIp, server.serverIp) && serverType == server.serverType && Objects.equals(serverPath, server.serverPath) && Objects.equals(serverMaxRam, server.serverMaxRam);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serverId, serverIp, serverPort, serverType, serverPath, serverMaxRam);
    }

}
