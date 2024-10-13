package net.fununity.cloud.server.server.shutdown;

import com.github.dockerjava.api.DockerClient;
import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.server.ServerState;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.server.Server;
import net.fununity.cloud.server.util.DockerUtil;
import net.fununity.cloud.server.util.EventSendingHelper;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Class responsible for stopping the server and handling related processes.
 *
 * @author Niko
 */
@Slf4j
public class ServerStopper {

    private final AtomicBoolean bungeeRemovedServer = new AtomicBoolean(false);
    private static final int BUNGEE_TIMEOUT = 2;

    private final AtomicBoolean clientShutdown = new AtomicBoolean(false);
    private static final int SHUTDOWN_TIMEOUT = 10;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private final CompletableFuture<Void> bungeeResponse;
    private final CompletableFuture<Void> clientDisconnected;

    private final Server server;
    boolean shuttingDown;

    /**
     * Constructor for ServerStopper.
     *
     * @param server The server instance to be stopped.
     */
    public ServerStopper(Server server) {
        this.server = server;
        shuttingDown = false;
        bungeeResponse = new CompletableFuture<>();
        clientDisconnected = new CompletableFuture<>();
    }


    /**
     * Initiates the server shutdown process.
     * This includes sending removal requests to BungeeCord, sending shutdown commands to the client,
     * and stopping and removing the Docker container.
     */
    public void shutdownServer() {
        if (shuttingDown) return;
        shuttingDown = true;
        server.stopAliveChecker();
        sendRemoveToBungee()
                .thenRun(this::sendClientShutdown)
                .thenRun(this::stopAndRemoveContainer)
                .exceptionally(throwable -> {
                    log.warn("Unhandled error while stopping server {}: ", server.getServerId(), throwable);
                    return null;
                }).join();

        log.info("Server {} shutdown process completed.", server.getServerId());
        server.setServerState(ServerState.IDLE);
        scheduler.shutdownNow();
        CloudServer.getInstance().getServerManager().serverCompletelyStopped(server);
    }

    /**
     * Sends a request to BungeeCord to remove the server and handles the response or timeout.
     *
     * @return A CompletableFuture that completes when the response is received or the timeout occurs.
     */
    private CompletableFuture<Void> sendRemoveToBungee() {
        if (bungeeRemovedServer.get()) {
            log.debug("Skipping remove bungee request as already completed.");
            return CompletableFuture.completedFuture(null);
        }
        log.debug("Sending remove request to bungee...");
        EventSendingHelper.sendToBungeeCord(new CloudEvent(CloudEvent.BUNGEE_SERVER_REMOVE_REQUEST).addData(server.getServerId()));

        scheduler.schedule(() -> {
            if (!bungeeResponse.isDone()) {
                log.info("No server-removed response from bungeecord after {} seconds.", BUNGEE_TIMEOUT);
                bungeeResponse.complete(null); // Complete after timeout
            }
        }, BUNGEE_TIMEOUT, TimeUnit.SECONDS);

        return bungeeResponse.handle((unused, throwable) -> {
            if (throwable != null) {
                log.warn("Error during bungeecord removal for server {}", server.getServerId(), throwable);
            }
            return null;
        });
    }

    /**
     * Sends a shutdown request to the client and handles the response or timeout.
     */
    private void sendClientShutdown() {
        if (clientShutdown.get()) {
            log.debug("Skipping server shutdown as server disconnected itself.");
            return;
        }

        log.debug("Sending shutdown request to server...");
        CloudServer.getInstance().getClientHandler().sendEvent(server, new CloudEvent(CloudEvent.CLIENT_SHUTDOWN_COMMAND));

        scheduler.schedule(() -> {
            if (!clientDisconnected.isDone()) {
                log.warn("No disconnect response from server {} after {} seconds.", server.getServerId(), SHUTDOWN_TIMEOUT);
                clientDisconnected.complete(null); // Complete after timeout
            }
        }, SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);
    }

    /**
     * Stops the Docker container associated with the server.
     */
    private void stopAndRemoveContainer() {
        String serverId = server.getServerId();
        log.debug("Stopping docker container {}", serverId);
        try (DockerClient dockerClient = DockerUtil.createDockerClient()) {
            if (doesContainerNotExist(dockerClient)) {
                log.debug("Docker container {} does not exist. Skipping stop and remove.", serverId);
                return;
            }

            try {
                dockerClient.stopContainerCmd(serverId).exec();
                log.debug("Docker container {} stopped successfully.", serverId);
            } catch (Exception e) {
                log.warn("Error while stopping Docker container {}. Attempting to kill it.", serverId, e);
                try {
                    dockerClient.killContainerCmd(serverId).exec();
                    log.info("Docker container {} killed successfully.", serverId);
                } catch (Exception killException) {
                    log.warn("Error while killing Docker container {} ", serverId, killException);
                }
            }

            try {
                log.debug("Removing Docker container {}.", server.getServerId());
                dockerClient.removeContainerCmd(server.getServerId()).exec();
            } catch (Exception e) {
                log.warn("Error while removing Docker container {} ", server.getServerId(), e);
            }
        } catch (IOException e) {
            log.warn("Error while closing Docker client.", e);
        }
    }


    private boolean doesContainerNotExist(DockerClient dockerClient) {
        return dockerClient.listContainersCmd().withShowAll(true).exec().stream()
                .noneMatch(container -> container.getNames()[0].equals("/" + server.getServerId()));
    }

    /**
     * Handles the response from BungeeCord indicating the server has been removed.
     */
    public void bungeecordRemovedServer() {
        if (!bungeeResponse.isDone()) {
            log.debug("Received server {} removed response from bungeecord.", server.getServerId());
            bungeeRemovedServer.set(true);
            bungeeResponse.complete(null);
            if (!shuttingDown)
                shutdownServer();
        }
    }


    /**
     * Handles the response from the client indicating it has disconnected.
     */
    public void clientDisconnected() {
        if (!clientDisconnected.isDone()) {
            log.info("Received disconnect response from server {}.", server.getServerId());
            clientShutdown.set(true);
            clientDisconnected.complete(null); // Complete task 2 when response is received
            if (!shuttingDown)
                shutdownServer();
        }
    }
}
