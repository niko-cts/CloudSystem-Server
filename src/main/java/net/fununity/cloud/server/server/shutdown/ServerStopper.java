package net.fununity.cloud.server.server.shutdown;

import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.server.Server;
import net.fununity.cloud.server.server.util.EventSendingHelper;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ServerStopper {

	private final AtomicBoolean bungeeRemovedServer = new AtomicBoolean(false);
	private static final int BUNGEE_TIMEOUT = 2;

	private final AtomicBoolean clientShutdown = new AtomicBoolean(false);
	private static final int SHUTDOWN_TIMEOUT = 10;
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

	// Simulate asynchronous response listeners
	private final CompletableFuture<Void> bungeeResponse;
	private final CompletableFuture<Void> clientDisconnected;

	private final Server server;

	public ServerStopper(Server server) {
		this.server = server;
		bungeeResponse = new CompletableFuture<>();
		clientDisconnected = new CompletableFuture<>();
	}

	boolean shuttingDown = false;

	public void shutdownServer() {
		shuttingDown = true;
		sendRemoveToBungee()
				.thenComposeAsync(unused -> sendClientShutdown())
				.thenComposeAsync(unused -> killContainer())
				.thenComposeAsync(unused -> removeContainer())
				.thenComposeAsync(unused -> postShutdownActions())
				.exceptionally(throwable -> {
					log.warn("Unhandled error while stopping server {}: ", server.getServerId(), throwable);
					return null;
				})
				.join();
		log.info("Server {} shutdown process completed.", server.getServerId());
		scheduler.shutdown();
	}

	// Task 1: Send remove request and handle response or timeout (2 seconds)
	private CompletableFuture<Void> sendRemoveToBungee() {
		if (bungeeRemovedServer.get()) {
			log.info("Skipping remove bungee request as already completed.");
			return CompletableFuture.completedFuture(null);
		}
		log.info("Sending remove request to bungee...");
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

	// Task 2: Send shutdown request and handle response or timeout (5 seconds)
	private CompletableFuture<Void> sendClientShutdown() {
		if (clientShutdown.get()) {
			log.debug("Skipping server shutdown as server disconnected itself.");
			return CompletableFuture.completedFuture(null);
		}

		log.debug("Sending shutdown request to server...");
		CloudServer.getInstance().getClientHandler().sendEvent(server, new CloudEvent(CloudEvent.CLIENT_SHUTDOWN_COMMAND));

		scheduler.schedule(() -> {
			if (!clientDisconnected.isDone()) {
				log.warn("No disconnect response from server {} after {} seconds.", server.getServerId(), SHUTDOWN_TIMEOUT);
				clientDisconnected.complete(null); // Complete after timeout
			}
		}, SHUTDOWN_TIMEOUT, TimeUnit.SECONDS);

		return clientDisconnected;
	}

	// Task 3: Remove or kill the Docker container
	private CompletableFuture<Void> killContainer() {
		return CompletableFuture.runAsync(() -> {
			if (!clientShutdown.get()) {
				log.info("Killing docker container {} because of no server response.", server.getServerId());

				// TODO

			} else {
				log.debug("No need to kill docker container, server shutdown normally.");
			}
		}).handle((unused, throwable) -> {
			if (throwable != null) {
				log.warn("Error while killing/removing Docker container {} ", server.getServerId(), throwable);
			}
			return null;
		});
	}

	private CompletableFuture<Void> removeContainer() {
		return CompletableFuture.runAsync(() -> {
			log.info("Removing Docker container {}.", server.getServerId());
			// TODO Docker container removal logic here
		});
	}


	// Task 4: Post-action cleanup
	private CompletableFuture<Void> postShutdownActions() {
		return CompletableFuture.runAsync(() -> {
			log.info("Performing post-action cleanup...");
			// Cleanup logic here
		}).handle((unused, throwable) -> {
			if (throwable != null) {
				log.warn("Error during post-action cleanup: ", throwable);
			}
			return null;
		});
	}

	public void bungeecordRemovedServer() {
		if (!bungeeResponse.isDone()) {
			log.debug("Received server {} removed response from bungeecord.", server.getServerId());
			bungeeRemovedServer.set(true);
			bungeeResponse.complete(null);
			if (!shuttingDown)
				shutdownServer();
		}
	}

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
