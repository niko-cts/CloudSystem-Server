package net.fununity.cloud.server.server;

import net.fununity.cloud.common.server.ServerState;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.common.utils.DebugLoggerUtil;
import net.fununity.cloud.server.misc.ServerHandler;
import net.fununity.cloud.server.misc.ServerShutdown;
import net.fununity.cloud.server.misc.ServerUtils;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Objects;

/**
 * Abstract class to define the basic server instance.
 *
 * @author Marco Hajek, Niko
 * @since 0.0.1
 */
public final class Server {

    private static final String ERROR_COULD_NOT_SET_PROPERTIES = "Could not set properties: ";
    private static final String ERROR_COULD_NOT_RUN_COMMAND = "Could not execute command: ";
    private static final String ERROR_SERVER_ALREADY_RUNNING = "Server is already running!";
    private static final String ERROR_SERVER_IS_NOT_RUNNING = "Server is not running!";
    private static final String ERROR_FILE_NOT_EXISTS = " does not exists. Can not execute it.";
    private static final String INFO_COPY_TEMPLATE_FOR = "Copying template for ";
    private static final String INFO_COPY_BACKUP_FOR = "Copying backup for ";
    private static final String INFO_SERVER_STARTED = "Server started: ";
    private static final String INFO_SERVER_STOPPED = "Server stopped: ";
    private static final String INFO_DELETE_SERVER = "Server deleted: ";
    private static final String FILE_START = "start.sh";
    private static final String FILE_STOP = "stop.sh";
    private static final String FILE_SERVER_PROPERTIES = "server.properties";

    static final Logger LOG = Logger.getLogger(Server.class);

    static {
        LOG.addAppender(new ConsoleAppender(new PatternLayout("[%d{HH:mm:ss}] %c{1} [%p]: %m%n")));
        LOG.setAdditivity(false);
        LOG.setLevel(Level.INFO);
    }

    private final String serverId;
    private final String serverIp;
    private final int serverPort;
    private final ServerType serverType;
    private ServerState serverState;
    String serverPath;
    private String backupPath;
    private final String serverMaxRam;
    private final String serverMotd;
    private int maxPlayers;
    private int playerCount;
    private ServerShutdown shutdownProcess;
    private ServerAliveChecker aliveChecker;

    /**
     * Creates a new server instance.
     *
     * @param serverId   String - the identifier of the server.
     * @param serverIp   String - the ip of the server.
     * @param serverPort int - the port of the server.
     * @param maxRam     String - the Xmx java string.
     * @param motd       String - the motd of the server.
     * @param serverType ServerType - the type of the server.
     * @author Marco Hajek
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
        this.maxPlayers = maxPlayers;
        this.playerCount = 0;
        this.shutdownProcess = null;
        this.createServerPath();
        this.createFiles();
        this.setServerProperties();
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
     * Get the amount of maximum players of the server
     *
     * @return int - the maximum amount of players of the server
     * @since 0.0.1
     */
    public int getMaxPlayers() {
        return maxPlayers;
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
     * Gets the current state of the server.
     *
     * @return ServerState - the state.
     * @see ServerState
     * @since 0.0.1
     */
    public ServerState getServerState() {
        return this.serverState;
    }

    /**
     * Sets the state of the server.
     *
     * @param state ServerState - the state.
     * @see ServerState
     * @since 0.0.1
     */
    public void setServerState(ServerState state) {
        this.serverState = state;
    }

    /**
     * Get the amount of current players on the server
     *
     * @return int - Amount of players
     * @since 0.0.1
     */
    public int getPlayerCount() {
        return playerCount;
    }

    /**
     * Adds the amount of players by one.
     * Creates new lobby if player count of lobby goes above 20
     *
     * @param playerCount int - The amount of players on this server
     * @since 0.0.1
     */
    public void setPlayerCount(int playerCount) {
        this.playerCount = playerCount;
        if (serverType == ServerType.LOBBY && playerCount == 0 && !serverId.equals("Lobby01") &&
                ServerHandler.getInstance().getPlayerCountOfNetwork() + getMaxPlayers() < ServerHandler.getInstance().getLobbyServers().stream()
                        .mapToInt(Server::getMaxPlayers).sum()) {
            ServerHandler.getInstance().shutdownServer(this);
        }
    }

    /**
     * Creates the path of the server.
     *
     * @since 0.0.1
     */
    private void createServerPath() {
        this.serverPath = new StringBuilder()
                .append("./Servers/")
                .append(this.serverType == ServerType.BUNGEECORD ? "BungeeCord/" : "Spigot/")
                .append(this.serverId).append("/").toString();
        this.backupPath = new StringBuilder()
                .append("./Servers/Backups/")
                .append(this.serverId).append("/").toString();
    }

    /**
     * Creates the server directory and copies the template if it doesn't exist.
     *
     * @since 0.0.1
     */
    private void createFiles() {
        try {
            File serverDirectory = new File(this.serverPath);
            if (serverDirectory.exists()) {
                FileUtils.deleteDirectory(serverDirectory);
            }

            String copyPath;
            if (new File(this.backupPath).exists()) {
                copyPath = this.backupPath;
                DebugLoggerUtil.getInstance().info(INFO_COPY_BACKUP_FOR + this.serverId);
                LOG.info(INFO_COPY_BACKUP_FOR + this.serverId);
            } else {
                copyPath = ServerUtils.getTemplatePath(this.serverType);
                DebugLoggerUtil.getInstance().info(INFO_COPY_TEMPLATE_FOR + this.serverId);
                LOG.info(INFO_COPY_TEMPLATE_FOR + this.serverId);
            }

            FileUtils.copyDirectory(new File(copyPath), serverDirectory);
            /*Path dest = Paths.get(this.serverPath);
            try (Stream<Path> paths = Files.walk(path)) {
                paths.forEach(file -> {
                    try {
                        Files.copy(file, dest.resolve(path.relativize(file)), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    } catch (IOException exception) {
                        DebugLoggerUtil.getInstance().warn("Could not copy file: " + file.toAbsolutePath() + " (" + exception.getMessage() + ")");
                        exception.printStackTrace();
                    }
                });
            } catch (IOException e) {
                DebugLoggerUtil.getInstance().warn("Could not copy files: " + path + " (" + e.getMessage() + ")");
                ServerHandler.getInstance().flushServer(this);
            }*/

        } catch (IOException exception) {
            ServerHandler.getInstance().flushServer(this);
        }
    }


    private void setServerProperties() {
        try {
            if (this.serverType != ServerType.BUNGEECORD) {
                File file = new File(this.serverPath + FILE_SERVER_PROPERTIES);
                if (file.createNewFile()) {
                    BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                    writer.write("server-ip=" + this.serverIp + "\n");
                    writer.write("server-port=" + this.serverPort + "\n");
                    writer.write("motd=" + this.serverMotd + "\n");
                    writer.write("max-players=" + this.maxPlayers + "\n");
                    writer.write("allow-flight=true\n");
                    writer.write("online-mode=false\n");
                    writer.write("allow-nether=false\n");
                    writer.flush();
                    writer.close();
                }
            }
        } catch (IOException e) {
            LOG.warn(ERROR_COULD_NOT_SET_PROPERTIES + e.getMessage());
        }
    }

    /**
     * Tries to start the server instance.
     *
     * @since 0.0.1
     */
    public void start() {
        if (this.serverState == ServerState.RUNNING) {
            LOG.warn(ERROR_SERVER_ALREADY_RUNNING);
            ServerHandler.getInstance().flushServer(this);
            return;
        }

        File file = new File(this.serverPath + FILE_START);
        if (!file.exists()) {
            DebugLoggerUtil.getInstance().warn(file.getPath() + ERROR_FILE_NOT_EXISTS);
            LOG.warn(file.getPath() + ERROR_FILE_NOT_EXISTS);
            ServerHandler.getInstance().flushServer(this);
            return;
        }

        try {
            Runtime.getRuntime().exec("sh " + file.getPath() + " " + this.serverPath + " " + this.serverId + " " + this.serverMaxRam);
            this.serverState = ServerState.RUNNING;

            if (serverType != ServerType.BUNGEECORD)
                this.aliveChecker = new ServerAliveChecker(this);

            LOG.info(INFO_SERVER_STARTED + this.serverId);
            DebugLoggerUtil.getInstance().info(INFO_SERVER_STARTED + this.serverId);
        } catch (IOException e) {
            LOG.warn(ERROR_COULD_NOT_RUN_COMMAND + e.getMessage());
            DebugLoggerUtil.getInstance().warn(ERROR_COULD_NOT_RUN_COMMAND + e.getMessage());
            ServerHandler.getInstance().flushServer(this);
        }
    }

    /**
     * Tries to stop a server.
     *
     * @since 0.0.1
     */
    public void stop() {
        if (this.serverState != ServerState.RUNNING) {
            LOG.warn(ERROR_SERVER_IS_NOT_RUNNING);
            DebugLoggerUtil.getInstance().warn(ERROR_SERVER_IS_NOT_RUNNING);
            ServerHandler.getInstance().flushServer(this);
            return;
        }

        File file = new File(this.serverPath + FILE_STOP);
        if (!file.exists()) {
            LOG.warn(file.getPath() + ERROR_FILE_NOT_EXISTS);
            DebugLoggerUtil.getInstance().warn(file.getPath() + ERROR_FILE_NOT_EXISTS);
            ServerHandler.getInstance().flushServer(this);
            return;
        }

        try {
            Runtime.getRuntime().exec("sh " + file.getPath() + " " + this.serverId);
            this.serverState = ServerState.STOPPED;
            LOG.info(INFO_SERVER_STOPPED + this.serverId);
            DebugLoggerUtil.getInstance().info(INFO_SERVER_STOPPED + this.serverId);

            new ServerDeleter(this);
        } catch (IOException e) {
            LOG.warn(ERROR_COULD_NOT_RUN_COMMAND + e.getMessage());
            ServerHandler.getInstance().flushServer(this);
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

        LOG.info(INFO_COPY_BACKUP_FOR + this.serverId);
        FileUtils.deleteDirectory(backupFile);

        if (copy)
            FileUtils.copyDirectory(new File(this.serverPath), backupFile, false);
        else
            FileUtils.moveDirectory(new File(this.serverPath), backupFile);
    }

    /**
     * Deletes the whole instance directory.
     * @since 1.0.0
     */
    public void deleteContent() {
        LOG.info(INFO_DELETE_SERVER + this.serverPath);
        FileUtils.deleteQuietly(new File(this.serverPath));
    }

    /**
     * Sets the max players of the server.
     *
     * @param maxPlayers int - max players
     */
    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }


    /**
     * Check if server has received remove confirmation from bungee.
     *
     * @return IServerShutdown - remove confirmation.
     * @since 0.0.1
     */
    public ServerShutdown getShutdownProcess() {
        return shutdownProcess;
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

    public void stopAliveChecker() {
        if (this.aliveChecker != null)
            this.aliveChecker.stopTimer();
    }

    public void receivedClientAliveResponse() {
        if (this.aliveChecker != null)
            this.aliveChecker.receivedEvent();
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
