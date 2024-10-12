package net.fununity.cloud.server.server;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.server.ServerDefinition;
import net.fununity.cloud.common.server.ServerState;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.misc.MinigameHandler;
import net.fununity.cloud.server.server.shutdown.ServerShutdown;
import net.fununity.cloud.server.util.EventSendingHelper;
import net.fununity.cloud.server.util.ServerUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

@Slf4j
@Getter
public class ServerManager {

    private final List<Server> runningServers;
    private final Queue<Server> startQueue;
    private final Queue<Server> stopQueue;
    @Getter
    private final Set<ServerType> expireServers;

    public ServerManager() {
        this.runningServers = new CopyOnWriteArrayList<>();
        this.stopQueue = new ConcurrentLinkedQueue<>();
        this.startQueue = new ConcurrentLinkedQueue<>();
        this.expireServers = new CopyOnWriteArraySet<>();
    }

    /**
     * Create a server with the given server type.
     *
     * @param serverType ServerType - the type of the server.
     * @since 0.0.1
     */
    public void createServerByServerType(ServerType serverType) {
        if (expireServers.contains(serverType)) {
            log.warn("{} was tried to start, but is in expire mode!", serverType);
            return;
        }

        List<Integer> serversWithSameType = getAllServerByType(serverType).stream()
                .map(s -> Integer.parseInt(s.getServerName().replaceAll("[^\\d.]", ""))).toList();

        int nextNumber = 1;
        while (serversWithSameType.contains(nextNumber)) {
            nextNumber++;
        }

        String serverName = serverType.getServerId();
        if (nextNumber < 10)
            serverName += "0" + nextNumber;
        else
            serverName += nextNumber;

        log.debug("Creating new server named '{}' by type '{}'", serverName, serverType.name());
        requestStartServer(new Server(serverName, "127.0.0.1", serverType));
    }

    private void requestStartServer(Server server) {
        if (getAllServers().contains(server)) {
            log.warn("Could not add server, because it is already in the list {}", server.getServerId());
            return;
        }
        int maxRam = CloudServer.getInstance().getMaxRam();
        if (ServerUtils.getCurrentRamUsed() >= maxRam) {
            log.warn("Can not start {} because maximum ram of {}M is currently used by all servers.", server.getServerId(), maxRam);
            return;
        }

        log.info("Adding server to start queue {}", server);
        startQueue.add(server);
        if (startQueue.size() == 1) {
            server.start();
        }
    }

    public void startFailed(@NotNull Server server) {
        log.info("Server {} failed to start. Removing from queue", server.getServerId());
        if (startQueue.contains(server)) {
            boolean top = server.equals(startQueue.peek());
            startQueue.remove(server);
            if (top) {
                Server nextStartingServer = startQueue.peek();
                if (nextStartingServer != null) {
                    log.debug("Failed server was on top of queue. Starting next server {}...", nextStartingServer.getServerId());
                    nextStartingServer.start();
                }
            }
        }
    }

    public void serverCompletelyStarted(Server server) {
        log.info("Server {} has started.", server.getServerId());
        startQueue.remove(server);
        server.setServerState(ServerState.RUNNING);
        runningServers.add(server);
        if (server.isMarkedForStop())
            requestStopServer(server);
        if (!startQueue.isEmpty())
            startQueue.peek().start();
    }

    public void requestRestartServer(Server server) {
        log.info("Restarting server {}...", server.getServerId());
        ServerType serverType = server.getConfig().getServerType();
        requestStopServer(server, () -> createServerByServerType(serverType));
    }

    public void requestStopServer(Server server) {
        requestStopServer(server, null);
    }

    public void requestStopServer(Server server, ServerShutdown serverStopped) {
        if (stopQueue.contains(server)) {
            log.warn("Can not stop server, because it is already in the stopping queue {}", server.getServerId());
            return;
        }

        log.info("Adding server {} to stop queue", server.getServerId());
        server.markForStop();
        if (serverStopped != null)
            server.setShutdownProcess(serverStopped);

        if (runningServers.contains(server)) { // only remove server when stopped
            stopQueue.add(server);

            runningServers.remove(server);
            if (stopQueue.size() == 1) {
                server.stop();
            }
        }
    }

    public void serverCompletelyStopped(Server server) {
        log.debug("Server {} completely stopped. Removing from queue", server.getServerId());
        stopQueue.remove(server);

        switch (server.getConfig().getServerType()) {
            case LOBBY -> EventSendingHelper.sendLobbyInformationToLobbies();
            case COCATTACK -> EventSendingHelper.sendCocAttackServerAmount();
            default -> MinigameHandler.getInstance()
                    .removeServer(server, server.getShutdownProcess() != null && server.getShutdownProcess().needsMinigameCheck());
        }

        if (server.getShutdownProcess() != null) {
            log.debug("Executing shutdown process of server {}...", server.getServerId());
            server.getShutdownProcess().serverStopped();
        }

        if (!stopQueue.isEmpty())
            stopQueue.peek().stop();
    }

    public ServerDefinition getServerDefinition(Server server) {
        return new ServerDefinition(server.getServerId(), server.getServerName(), server.getServerIp(), server.getServerPort(),
                server.getConfig().getRam(), server.getPlayerCount(), server.getMaxPlayers(), server.getConfig().getServerType());
    }

    /**
     * Gets a server by its identifier.
     *
     * @param identifier String - the identifier.
     * @return Server or null.
     * @see Server
     * @since 0.0.1
     */
    public @NotNull Optional<Server> getServerByIdentifier(@Nullable String identifier) {
        return identifier != null ? getAllServers().stream().filter(server -> server.getServerId().equals(identifier)).findFirst() : Optional.empty();
    }
    public  @NotNull Optional<Server> getServerByName(@Nullable String serverName) {
        return getAllServers().stream().filter(server -> server.getServerName().equals(serverName)).findFirst();
    }

    public @NotNull List<Server> getRunningServerByType(@Nullable ServerType serverType) {
        return serverType != null ? getRunningServers().stream().filter(s -> s.getConfig().getServerType() == serverType).toList() : List.of();
    }

    public @NotNull List<Server> getAllServerByType(@Nullable ServerType serverType) {
        return serverType != null ? getAllServers().stream().filter(s -> s.getConfig().getServerType() == serverType).toList() : List.of();
    }


    /**
     * Get all servers running servers and queued.
     *
     * @return List<Server> - all servers.
     * @since 1.0
     */
    public @NotNull List<Server> getAllServers() {
        List<Server> servers = getRunningServers();
        servers.addAll(this.startQueue);
        servers.addAll(this.stopQueue);
        return servers;
    }

}
