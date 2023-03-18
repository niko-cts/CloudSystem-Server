package net.fununity.cloud.server.server;

import net.fununity.cloud.common.server.ServerState;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.common.utils.DebugLoggerUtil;
import net.fununity.cloud.server.misc.ServerHandler;
import net.fununity.cloud.server.misc.ServerShutdown;
import net.fununity.cloud.server.misc.ServerUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/**
 * Abstract class to define the basic server instance.
 * @since 0.0.1
 * @author Marco Hajek, Niko
 */
public final class Server {

    private static final String ERROR_NOT_EXIST_CREATING = " does not exists. Creating...";
    private static final String ERROR_COULD_NOT_CREATE_DIRECTORIES = "Could not create directories: ";
    private static final String ERROR_COULD_NOT_SET_PROPERTIES = "Could not set properties: ";
    private static final String ERROR_COULD_NOT_RUN_COMMAND = "Could not execute command: ";
    private static final String ERROR_SERVER_ALREADY_RUNNING = "Server is already running!";
    private static final String ERROR_SERVER_IS_NOT_RUNNING = "Server is not running!";
    private static final String ERROR_FILE_NOT_EXISTS = " does not exists. Can not execute it.";
    private static final String INFO_COPY_TEMPLATE_FOR = "Copying template for ";
    private static final String INFO_COPY_BACKUP_FOR = "Copying backup for ";
    private static final String INFO_SERVER_STARTED = "Server started: ";
    private static final String INFO_SERVER_STOPPED = "Server stopped: ";
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
     * @param serverId String - the identifier of the server.
     * @param serverIp String - the ip of the server.
     * @param serverPort int - the port of the server.
     * @param maxRam String - the Xmx java string.
     * @param motd String - the motd of the server.
     * @param serverType ServerType - the type of the server.
     * @see ServerType
     * @since 0.0.1
     * @author Marco Hajek
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
        this.createIfNotExists();
        this.setServerProperties();
    }

    /**
     * Creates a server with the optimal port, ram and motd.
     * @param serverId String - the identifier of the server.
     * @param serverIp String - the ip of the server.
     * @param serverType ServerType - the type of the server.
     * @see ServerType
     * @since 0.0.1
     * @author Niko
     */
    public Server(String serverId, String serverIp, ServerType serverType) {
        this (serverId, serverIp, ServerHandler.getInstance().getOptimalPort(serverType), ServerUtils.getRamFromType(serverType) + "M", serverId, ServerUtils.getMaxPlayersOfServerType(serverType), serverType);
    }

    /**
     * Gets the server identifier.
     * @return String - the identifier.
     * @since 0.0.1
     */
    public String getServerId(){
        return this.serverId;
    }

    /**
     * Gets the server ip.
     * @return String - the ip.
     * @since 0.0.1
     */
    public String getServerIp(){
        return this.serverIp;
    }

    /**
     * Gets the server port.
     * @return int - the port.
     * @since 0.0.1
     */
    public int getServerPort(){
        return this.serverPort;
    }

    /**
     * Gets the motd of the server.
     * @return String - the motd.
     * @since 0.0.1
     */
    public String getServerMotd(){
        return this.serverMotd;
    }

    /**
     * Get the amount of maximum players of the server
     * @return int - the maximum amount of players of the server
     * @since 0.0.1
     */
    public int getMaxPlayers() {
        return maxPlayers;
    }

    /**
     * Gets the servers maxram configuration.
     * @return String - the max ram config.
     * @since 0.0.1
     */
    public String getServerMaxRam(){
        return this.serverMaxRam;
    }
    /**
     * Gets the server type.
     * @see ServerType
     * @return ServerType - the type.
     * @since 0.0.1
     */
    public ServerType getServerType(){
        return this.serverType;
    }

    /**
     * Gets the current state of the server.
     * @see ServerState
     * @return ServerState - the state.
     * @since 0.0.1
     */
    public ServerState getServerState(){
        return this.serverState;
    }

    /**
     * Sets the state of the server.
     * @see ServerState
     * @param state ServerState - the state.
     * @since 0.0.1
     */
    public void setServerState(ServerState state){
        this.serverState = state;
    }

    /**
     * Get the amount of current players on the server
     * @return int - Amount of players
     * @since 0.0.1
     */
    public int getPlayerCount() {
        return playerCount;
    }

    /**
     * Adds the amount of players by one.
     * Creates new lobby if player count of lobby goes above 20
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
     * @since 0.0.1
     */
    private void createServerPath() {
        StringBuilder path = new StringBuilder();
        path.append("./Servers/");
        path.append(this.serverType == ServerType.BUNGEECORD ? "BungeeCord/" : "Spigot/");
        path.append(this.serverId).append("/");
        this.serverPath = path.toString();
        StringBuilder backup = new StringBuilder();
        backup.append("./Servers/Backups/");
        backup.append(this.serverId).append("/");
        this.backupPath = backup.toString();
    }

    /**
     * Creates the server directory and copies the template if it doesn't exist.
     * @since 0.0.1
     */
    private void createIfNotExists() {
        try {
            if (Files.exists(Paths.get(this.serverPath)) && !Files.exists(Paths.get(this.serverPath + FILE_START))) {
                deleteServerContent(Paths.get(this.serverPath).toFile());
            }
            if (!Files.exists(Paths.get(this.serverPath))) {
                LOG.warn(this.serverPath + ERROR_NOT_EXIST_CREATING);
                Files.createDirectories(Paths.get(this.serverPath));
                String copyPath = Files.exists(Paths.get(this.backupPath)) ? this.backupPath : ServerUtils.createTemplatePath(this.serverType);
                if (!Files.exists(Paths.get(copyPath))) {
                    DebugLoggerUtil.getInstance().warn(copyPath + ERROR_NOT_EXIST_CREATING);
                    Files.createDirectories(Paths.get(copyPath));
                }
                if (copyPath.equals(backupPath)) {
                    DebugLoggerUtil.getInstance().info(INFO_COPY_BACKUP_FOR + this.serverId);
                    LOG.info(INFO_COPY_BACKUP_FOR + this.serverId);
                } else {
                    DebugLoggerUtil.getInstance().info(INFO_COPY_TEMPLATE_FOR + this.serverId);
                    LOG.info(INFO_COPY_TEMPLATE_FOR + this.serverId);
                }
                Path src = Paths.get(copyPath);
                Path dest = Paths.get(this.serverPath);
                Files.walk(Paths.get(copyPath)).forEach(file -> {
                    try {
                        Files.copy(file, dest.resolve(src.relativize(file)), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    } catch (IOException e) {
                        DebugLoggerUtil.getInstance().warn("Could not copy file: " + file.toAbsolutePath() + " (" + e.getMessage() + ")");
                        LOG.warn("Could not copy file: " + file.toAbsolutePath() + " (" + e.getMessage() + ")");
                        e.printStackTrace();
                    }
                });
            }
        } catch (IOException e) {
            LOG.warn(ERROR_COULD_NOT_CREATE_DIRECTORIES + e.getMessage());
            ServerHandler.getInstance().flushServer(this);
        }
    }


    private void setServerProperties() {
        try {
            if(this.serverType != ServerType.BUNGEECORD) {
                File file = new File(this.serverPath + FILE_SERVER_PROPERTIES);
                file.createNewFile();
                BufferedWriter writer = new BufferedWriter(new FileWriter(file));
                writer.write("server-ip=" + this.serverIp + "\n");
                writer.write("server-port=" + this.serverPort+ "\n");
                writer.write("motd=" + this.serverMotd + "\n");
                writer.write("max-players=" + this.maxPlayers + "\n");
                writer.write("allow-flight=true\n");
                writer.write("online-mode=false\n");
                writer.write("allow-nether=false\n");
                writer.flush();
                writer.close();
            }
        } catch (IOException e) {
            LOG.warn(ERROR_COULD_NOT_SET_PROPERTIES + e.getMessage());
        }
    }

    /**
     * Tries to start the server instance.
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

            if (serverType != ServerType.BUNGEECORD) this.aliveChecker = new ServerAliveChecker(this);

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
            new ServerDeleter(this);

            LOG.info(INFO_SERVER_STOPPED + this.serverId);
            DebugLoggerUtil.getInstance().info(INFO_SERVER_STOPPED + this.serverId);
        } catch (IOException e) {
            LOG.warn(ERROR_COULD_NOT_RUN_COMMAND + e.getMessage());
            ServerHandler.getInstance().flushServer(this);
        }
    }

    /**
     * Moves the current server to the backup path.
     * @since 0.0.1
     */
    public void moveToBackup(boolean copy) throws IOException {
        if (!Files.exists(Paths.get(this.backupPath))) {
            LOG.warn(this.backupPath + ERROR_NOT_EXIST_CREATING);
            Files.createDirectories(Paths.get(this.backupPath));
        }
        LOG.info(INFO_COPY_BACKUP_FOR + this.serverId);
        Path src = Paths.get(this.serverPath);
        Path dest = Paths.get(this.backupPath);
        deleteServerContent(dest.toFile());

        if (copy)
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        else
            Files.move(src, dest);
    }

    /**
     * Deletes the whole server recursively.
     * @param content File - the file to be deleted.
     * @since 0.0.1
     */
    protected void deleteServerContent(File content) throws IOException {
        File[] allContents = content.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                if (file.isDirectory())
                    deleteServerContent(file);
                else
                    file.delete();
            }
        }
        content.delete();
    }

    /**
     * Sets the max players of the server.
     * @param maxPlayers int - max players
     */
    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }


    /**
     * Check if server has received remove confirmation from bungee.
     * @return IServerShutdown - remove confirmation.
     * @since 0.0.1
     */
    public ServerShutdown getShutdownProcess() {
        return shutdownProcess;
    }

    /**
     * Sets remove confirmation to true.
     * Remove confirmation needs to be sent from bungee, so the server can finally be stopped.
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
