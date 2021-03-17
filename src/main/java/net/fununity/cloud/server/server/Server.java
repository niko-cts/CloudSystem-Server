package net.fununity.cloud.server.server;

import net.fununity.cloud.common.server.ServerState;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.misc.IServerShutdown;
import net.fununity.cloud.server.misc.ServerHandler;
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
import java.util.stream.Stream;

/**
 * Abstract class to define the basic server instance.
 * @since 0.0.1
 * @author Marco Hajek
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

    private static final Logger LOG = Logger.getLogger(Server.class);
    private static boolean LOG_CONFIGURED = false;

    private final String serverId;
    private final String serverIp;
    private final int serverPort;
    private final ServerType serverType;
    private ServerState serverState;
    private String serverPath;
    private String backupPath;
    private final String serverMaxRam;
    private final String serverMotd;
    private int maxPlayers;
    private int playerCount;
    private IServerShutdown removeConfirmation;

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
        if(!LOG_CONFIGURED) {
            LOG.addAppender(new ConsoleAppender(new PatternLayout("[%d{HH:mm:ss}] %c{1} [%p]: %m%n")));
            LOG.setAdditivity(false);
            LOG.setLevel(Level.INFO);
            LOG_CONFIGURED = true;
        }

        this.serverId = serverId;
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.serverType = serverType;
        this.serverState = ServerState.IDLE;
        this.serverMaxRam = maxRam;
        this.serverMotd = motd;
        this.maxPlayers = maxPlayers;
        this.playerCount = 0;
        this.removeConfirmation = null;
        this.createServerPath();
        this.createIfNotExists();
        this.setServerProperties();
    }

    /**
     * Creates a server with a "random" port.
     * "random": current highest port + 1. Lol nice random :3
     * @param serverId String - the identifier of the server.
     * @param serverIp String - the ip of the server.
     * @param maxRam String - the Xmx java string.
     * @param motd String - the motd of the server.
     * @param serverType ServerType - the type of the server.
     * @see ServerType
     * @since 0.0.1
     * @author Marco Hajek
     */
    public Server(String serverId, String serverIp, String maxRam, String motd, int maxPlayers, ServerType serverType){
        this(serverId, serverIp, ServerHandler.getInstance().getHighestServerPort()+1, maxRam, motd, maxPlayers, serverType);
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
        if (serverType == ServerType.LOBBY && playerCount == 0 && !serverId.equals("Lobby01") && !serverId.equals("Lobby02") &&
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
            if(!Files.exists(Paths.get(this.serverPath))) {
                LOG.warn(this.serverPath + ERROR_NOT_EXIST_CREATING);
                Files.createDirectories(Paths.get(this.serverPath));
                String templatePath = Files.exists(Paths.get(this.backupPath)) ? this.backupPath : createTemplatePath();
                if (!Files.exists(Paths.get(templatePath))) {
                    LOG.warn(templatePath + ERROR_NOT_EXIST_CREATING);
                    Files.createDirectories(Paths.get(templatePath));
                }
                LOG.info(INFO_COPY_TEMPLATE_FOR + this.serverId);
                Path src = Paths.get(templatePath);
                Path dest = Paths.get(this.serverPath);
                Stream<Path> files = Files.walk(Paths.get(templatePath));
                files.forEach(file -> {
                    try {
                        Files.copy(file, dest.resolve(src.relativize(file)), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    } catch (IOException e) {
                        LOG.warn("Could not copy file: " + file.toAbsolutePath());
                    }
                });
            }
        }catch(IOException e){
            LOG.warn(ERROR_COULD_NOT_CREATE_DIRECTORIES + e.getMessage());
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
     * Creates the path for the template directories.
     * @return String - the path.
     * @since 0.0.1
     */
    private String createTemplatePath() {
        StringBuilder path = new StringBuilder();
        path.append("./Servers/Templates/");
        switch(this.getServerType()){
            case BUNGEECORD:
                path.append("BungeeCord/");
                break;
            case LOBBY:
                path.append("Lobby/");
                break;
            case MINIGAME:
                path.append("MiniGame/");
                break;
            case CAVEHUNT:
                path.append("CaveHunt/");
                break;
            case FLOWERWARS2x1:
                path.append("FlowerWars2x1/");
                break;
            case FLOWERWARS2x2:
                path.append("FlowerWars2x2/");
                break;
            case FLOWERWARS4x2:
                path.append("FlowerWars4x2/");
                break;
            case BEATINGPIRATES:
                path.append("BeatingPirates/");
                break;
            case PAINTTHESHEEP:
                path.append("PaintTheSheep/");
                break;
            case LANDSCAPES:
                path.append("Landscapes/");
                break;
            case FREEBUILD:
                path.append("FreeBuild/");
                break;
            default:
                LOG.warn("Could not create template path for " + this.serverId + "! ServerType is not supported.");
        }
        return path.toString();
    }

    /**
     * Tries to start the server instance.
     * @since 0.0.1
     */
    public void start() {
        if (this.serverState == ServerState.RUNNING) {
            LOG.warn(ERROR_SERVER_ALREADY_RUNNING);
            return;
        }

        File file = new File(this.serverPath + FILE_START);
        if(!file.exists()) {
            LOG.warn(file.getPath() + ERROR_FILE_NOT_EXISTS);
            return;
        }

        try {
            Runtime.getRuntime().exec("sh " + file.getPath() + " " + this.serverPath + " " + this.serverId + " " + this.serverMaxRam);
            this.serverState = ServerState.RUNNING;
            LOG.info(INFO_SERVER_STARTED + this.serverId);
        } catch (IOException e) {
            LOG.warn(ERROR_COULD_NOT_RUN_COMMAND + e.getMessage());
        }
    }

    /**
     * Tries to stop a server.
     * @since 0.0.1
     */
    public void stop (boolean delete) {
        if (this.serverState != ServerState.RUNNING) {
            LOG.warn(ERROR_SERVER_IS_NOT_RUNNING);
            return;
        }

        File file = new File(this.serverPath + FILE_STOP);
        if (!file.exists()) {
            LOG.warn(file.getPath() + ERROR_FILE_NOT_EXISTS);
            return;
        }
        try {
            Runtime.getRuntime().exec("sh " + file.getPath() + " " + this.serverId);
            this.serverState = ServerState.STOPPED;
            LOG.info(INFO_SERVER_STOPPED + this.serverId);
            if (this.serverType == ServerType.LANDSCAPES || this.serverType == ServerType.FREEBUILD) {
                createBackup();
            } else if (delete)
                deleteServerContent(Paths.get(this.serverPath).toFile());

        } catch (IOException e) {
            LOG.warn(ERROR_COULD_NOT_RUN_COMMAND + e.getMessage());
        }
    }

    /**
     * Creates a backup from the current server path.
     * @since 0.0.1
     */
    public void createBackup() {
        try {
            if (!Files.exists(Paths.get(this.backupPath))) {
                LOG.warn(this.backupPath + ERROR_NOT_EXIST_CREATING);
                Files.createDirectories(Paths.get(this.backupPath));
            }
            LOG.info(INFO_COPY_BACKUP_FOR + this.serverId);
            Path src = Paths.get(this.serverPath);
            Path dest = Paths.get(this.backupPath);
            Stream<Path> files = Files.walk(src);
            files.forEach(file -> {
                try {
                    Files.copy(file, dest.resolve(src.relativize(file)), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                } catch (IOException e) {
                    LOG.warn("Could not copy file: " + file.toAbsolutePath());
                }
            });
        } catch (IOException e) {
            LOG.warn(ERROR_COULD_NOT_RUN_COMMAND + e.getMessage());
        }
    }

    /**
     * Deletes the whole server recursively.
     * @param content File - the file to be deleted.
     * @since 0.0.1
     */
    private void deleteServerContent(File content) {
        File[] allContents = content.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteServerContent(file);
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
    public IServerShutdown getRemoveConfirmation() {
        return removeConfirmation;
    }

    /**
     * Sets remove confirmation to true.
     * Remove confirmation needs to be send from bungee, so the server can finally be stopped.
     * @since 0.0.1
     */
    public void setReceivedRemoveConfirmation(IServerShutdown shutdown) {
        this.removeConfirmation = shutdown;
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
