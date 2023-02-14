package net.fununity.cloud.server.server;

import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.common.utils.DebugLoggerUtil;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.misc.ClientHandler;
import net.fununity.cloud.server.misc.MinigameHandler;
import net.fununity.cloud.server.misc.ServerHandler;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Server deleter class delays the deletion or backup moving of the server in order to stop the server perfectly.
 * @author Niko
 * @since 0.0.1
 */
public class ServerDeleter extends TimerTask {

    private final Server server;

    /**
     * Instantiates the class and starts the delay.
     * @param server Server - the server instance.
     * @since 0.0.1
     */
    public ServerDeleter(Server server) {
        this.server = server;
        this.server.stopAliveChecker();
        new Timer().schedule(this, 200);
    }

    /**
     * Will remove the server from all lists
     * Calls {@link ServerHandler#continueStopQueue(Server)}.
     */
    @Override
    public void run() {
        ServerType serverType = server.getServerType();
        try {
            if (serverType == ServerType.LANDSCAPES || serverType == ServerType.FREEBUILD || serverType == ServerType.COCBASE)
                server.moveToBackup(false);
            else
                server.deleteServerContent(Paths.get(server.serverPath).toFile());
        } catch (IOException exception) {
            Server.LOG.warn("Error while deleting server: " + exception.getMessage());
            DebugLoggerUtil.getInstance().warn("Error while deleting server: " + exception.getMessage());
        }

        ServerHandler.getInstance().removeServer(server);

        if (ClientHandler.getInstance().getClientContext(server.getServerId()) != null)
            ClientHandler.getInstance().removeClient(server.getServerId());

        if (serverType == ServerType.LOBBY)
            ClientHandler.getInstance().sendLobbyInformationToLobbies();
        else
            MinigameHandler.getInstance().removeServer(server, server.getShutdownProcess() != null && server.getShutdownProcess().needsMinigameCheck());


        if (server.getShutdownProcess() != null) {
            try {
                server.getShutdownProcess().serverStopped();
            } catch (Exception exception) {
                CloudServer.getLogger().warn(exception.getMessage());
            }
        }

        DebugLoggerUtil.getInstance().info(server.getServerId() + " fully stopped.");

        ServerHandler.getInstance().continueStopQueue(server);
    }
}
