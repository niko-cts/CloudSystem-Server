package net.fununity.cloud.server.misc;

import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.config.ServerConfig;
import net.fununity.cloud.server.netty.listeners.GeneralEventListener;
import net.fununity.cloud.server.server.Server;
import net.fununity.cloud.server.server.ServerHandler;
import net.fununity.cloud.server.server.util.EventSendingHelper;
import net.fununity.cloud.server.server.util.ServerUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler class for minigame lobby mechanics
 * @author Niko
 * @since 0.0.1
 */
@Slf4j
public class MinigameHandler {

	private static MinigameHandler instance;

	/**
	 * Returns a singleton instance of this class
	 * @return MinigameHandler - Instance of {@link MinigameHandler}
	 */
	public static MinigameHandler getInstance() {
		if (instance == null)
			instance = new MinigameHandler();
		return instance;
	}

	private final Map<ServerType, Set<Server>> minigameLobbies;
	private int startingServer;

	/**
	 * Default constructor of the minigame handler.
	 * @since 0.0.1
	 */
	private MinigameHandler() {
		instance = this;
		this.minigameLobbies = new EnumMap<>(ServerType.class);
		this.startingServer = 0;
	}

	/**
	 * Removes a server that left the lobby state
	 * @param server Server - The Server
	 * @param check boolean - should check for a new lobby
	 * @since 0.0.1
	 */
	public void removeLobby(Server server, boolean check) {
		minigameLobbies.compute(server.getConfig().getServerType(), (serverType, servers) -> {
			if (servers == null) {
				if (check) {
					log.info("Removing minigame lobby '{}'. Checking if new lobby needs to be added", server.getServerId());
					checkToAdd(0, serverType);
				}
				return null;
			}

			servers.remove(server);

			if (check) {
				log.info("Removing minigame lobby '{}'. Checking if new lobby needs to be added", server.getServerId());
				checkToAdd(servers.size(), serverType);
			} else {
				log.info("Removing minigame lobby '{}'", server.getServerId());
			}
			return servers.isEmpty() ? null : servers;
		});
	}

	/**
	 * Tries to add a new lobby
	 * @param server Server - The Server
	 * @since 0.0.1
	 */
	public void addLobby(Server server) {
		minigameLobbies.compute(server.getConfig().getServerType(), (serverType, servers) -> {
			if (servers != null && !servers.contains(server)) {
				servers.add(server);
				if (startingServer > 0)
					startingServer--;
			}
			return servers;
		});
	}

	/**
	 * Checks if a server needs to be added
	 * @param lobbies int - number of lobbies
	 * @param serverType {@link ServerType} - The server type
	 * @since 0.0.1
	 */
	private void checkToAdd(int lobbies, ServerType serverType) {
		if (ServerUtils.getCurrentRamUsed() > ServerHandler.MAX_RAM)
			return;

		if (lobbies + startingServer < CloudServer.getInstance().getConfigHandler()
				.getByServerConfigByType(serverType).map(ServerConfig::getMinimumAmount).orElse(3)) {
			startingServer++;
			CloudServer.getInstance().getServerManager().createServerByServerType(serverType);
		}
	}

	/**
	 * Called when a Minigame sent STATUS_MINIGAME event.
	 * Will check if a new lobby registered
	 * @see GeneralEventListener
	 * @param event CloudEvent - Event that was sent
	 */
	public void receivedStatusUpdate(CloudEvent event) {
		CloudServer.getInstance().getServerManager().getServerByIdentifier(String.valueOf(event.getData().getFirst())).ifPresentOrElse(
				server -> {
					String state = event.getData().get(2).toString();

					if (state.equalsIgnoreCase("Lobby"))
						addLobby(server);
					else
						removeLobby(server, server.getShutdownProcess() == null || server.getShutdownProcess().needsMinigameCheck());

					server.setMaxPlayers(Integer.parseInt(event.getData().get(5).toString()));
				},
				() -> log.error("STATUS_MINIGAME Event was sent with unknown id: {}", event.getData().getFirst())
		);
	}

	/**
	 * Sends a player to a minigame lobby
	 * @param serverType {@link ServerType} - The server type to send
	 * @param uuid UUID - the uuid of the player
	 * @since 0.0.1
	 */
	public void sendPlayerToMinigameLobby(ServerType serverType, UUID uuid) {
		minigameLobbies.computeIfPresent(serverType, (type, servers) -> {
			servers.stream().filter(server -> server.getPlayerCount() < server.getMaxPlayers())
					.max(Comparator.comparing(Server::getPlayerCount))
					.ifPresent(server -> EventSendingHelper
							.sendToBungeeCord(new CloudEvent(CloudEvent.BUNGEE_SEND_PLAYER).addData(server.getServerId()).addData(uuid)));
			return servers;
		});
	}

	/**
	 * Calls {@link MinigameHandler#removeLobby(Server, boolean)}
	 * @param server Server - the Server.
	 * @param needsMinigameCheck boolean - checks if a new lobby should be created
	 * @since 0.0.1
	 */
	public void removeServer(Server server, boolean needsMinigameCheck) {
		this.removeLobby(server, needsMinigameCheck);
	}

	/**
	 * Get the lobby servers.
	 * @return Set<String> - The server id's
	 * @since 0.0.1
	 */
	public Set<String> getLobbyServers() {
		return new HashMap<>(minigameLobbies).entrySet().stream()
				.flatMap(servers -> servers.getValue().stream().map(Server::getServerId)).collect(Collectors.toSet());
	}
}
