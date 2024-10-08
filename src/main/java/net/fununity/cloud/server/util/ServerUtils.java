package net.fununity.cloud.server.util;

import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.config.NetworkConfig;
import net.fununity.cloud.server.config.ServerConfig;
import net.fununity.cloud.server.server.Server;
import net.fununity.cloud.server.server.ServerManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ServerUtils {

	private ServerUtils() {
		throw new IllegalAccessError("Utility class");
	}

	private static final ServerManager MANAGER = CloudServer.getInstance().getServerManager();
	private static final AtomicInteger NETWORK_AMOUNT = new AtomicInteger(0);

	public static void restartAllServerOfType(ServerType serverType) {
		MANAGER.getRunningServerByType(serverType).forEach(MANAGER::requestRestartServer);
		MANAGER.getStartQueue().stream().filter(s -> s.getConfig().getServerType() == serverType).forEach(server -> {
			server.markForStop();
			server.setShutdownProcess(() -> MANAGER.createServerByServerType(serverType));
		});
	}

	public static void shutdownAllServersOfType(ServerType serverType) {
		log.info("Stopping all server of type {}...", serverType);
		MANAGER.getStartQueue().stream().filter(s -> s.getConfig().getServerType() == serverType).forEach(Server::markForStop);
		MANAGER.getRunningServerByType(serverType).forEach(MANAGER::requestStopServer);
	}

	public static void shutdownAll() {
		log.info("Stopping all server...");
		MANAGER.getStartQueue().forEach(Server::markForStop);
		MANAGER.getRunningServers().stream().filter(s -> s.getConfig().getServerType() != ServerType.BUNGEECORD).forEach(MANAGER::requestStopServer);
		MANAGER.getAllServerByType(ServerType.BUNGEECORD).forEach(MANAGER::requestStopServer);
	}

	/**
	 * Gets the server identifier of the server with the given port.
	 *
	 * @param port int - the port of the server.
	 * @return String - the identifier of the server.
	 * @since 0.0.1
	 */
	public static Optional<String> getServerIdentifierByPort(int port) {
		return MANAGER.getRunningServers().stream().filter(s -> s.getServerPort() == port)
				.findFirst().map(Server::getServerId);
	}

	/**
	 * Get the best free port for the given server type.
	 *
	 * @return int - the next free port.
	 * @since 1.0
	 */
	public static int getNextFreeServerPort() {
		return getNextFreeServerPort(25566);
	}

	/**
	 * Returns the next free not used port considering all known servers.
	 *
	 * @param port int - the port to start searching.
	 * @return int - the highest port.
	 * @since 0.0.1
	 */
	private static int getNextFreeServerPort(int port) {
		if (MANAGER.getAllServers().stream().noneMatch(s -> s.getServerPort() == port)) {
			ServerSocket ss = null;
			DatagramSocket ds = null;
			try {
				ss = new ServerSocket(port);
				ss.setReuseAddress(true);
				ds = new DatagramSocket(port);
				ds.setReuseAddress(true);
				return port;
			} catch (IOException ignored) {
			} finally {
				if (ds != null) {
					ds.close();
				}
				if (ss != null) {
					try {
						ss.close();
					} catch (IOException e) {
						/* should not be thrown */
					}
				}
			}
		}

		return getNextFreeServerPort(port + 1);
	}

	public static void networkPlayerCountUpdate(int amount) {
		log.debug("Setting network count to {}", amount);
		NETWORK_AMOUNT.set(amount);
		int maxLobbyPlayers = getLobbyServers().stream().mapToInt(Server::getMaxPlayers).sum();
		int threshold = CloudServer.getInstance().getNetworkConfig()
				.map(NetworkConfig::getPlayerAmountNewLobbyThreshold).orElse(5);

		if (amount + threshold > maxLobbyPlayers) {
			log.debug("More players than lobbies can hold. Creating new lobby server...");
			MANAGER.createServerByServerType(ServerType.LOBBY);
		} else if (amount < maxLobbyPlayers - CloudServer.getInstance().getConfigHandler().getByServerConfigByType(ServerType.LOBBY)
				.map(ServerConfig::getMaxPlayers).orElse(0) &&
		           getLobbyServers().size() > 2) {
			log.debug("Less players than lobbies can hold. Removing last lobby server...");
			MANAGER.requestStopServer(getLobbyServers().getLast());
		}
	}

	public static void serverPlayerCountUpdate(String serverId, int amount) {
		log.debug("Setting player count of server {} to {}", serverId, amount);
		MANAGER.getServerByIdentifier(serverId).ifPresentOrElse(
				server -> server.setPlayerCount(amount),
				() -> log.warn("Could not set player count for server {}. Server was not found.", serverId));
	}

	/**
	 * Returns the number of players on a specific server type
	 *
	 * @param serverType ServerType - Type of servers
	 * @return int - number of all players on a specific ServerType
	 * @see Server#getPlayerCount()
	 * @since 0.0.1
	 */
	public static int getPlayerCountOfServerType(ServerType serverType) {
		return MANAGER.getRunningServerByType(serverType).stream().mapToInt(Server::getPlayerCount).sum();
	}

	/**
	 * Returns the number of players on a specific server
	 *
	 * @param serverId String - Identifier of server
	 * @return int - Number of players on server
	 * @see Server#getPlayerCount()
	 * @since 0.0.1
	 */
	public static int getPlayerCountOfServer(String serverId) {
		return MANAGER.getServerByIdentifier(serverId).map(Server::getPlayerCount).orElse(-1);
	}

	/**
	 * Returns the number of players on the network
	 *
	 * @return int - Number of players on the network
	 * @since 0.0.1
	 */
	public static int getPlayerCountOfNetwork() {
		return MANAGER.getRunningServers().stream().mapToInt(Server::getPlayerCount).sum();
	}

	/**
	 * Gets a list of all registered bungeecord servers.
	 *
	 * @return List<Server> - the list of bungeecord servers.
	 * @since 0.0.1
	 */
	public static @NotNull List<Server> getBungeeServers() {
		return MANAGER.getRunningServerByType(ServerType.BUNGEECORD);
	}


	/**
	 * Returns a list containing all lobby servers.
	 *
	 * @return List<Server> - all lobby servers.
	 * @since 0.0.1
	 */
	public static @NotNull List<Server> getLobbyServers() {
		return MANAGER.getRunningServerByType(ServerType.LOBBY);
	}

	public static int getCurrentRamUsed() {
		return MANAGER.getAllServers().stream().mapToInt(s -> s.getConfig().getRam()).sum();
	}

	/**
	 * Gets the serverId of the lobby with the lowest player count.
	 *
	 * @param blacklist Server[] - Server, which cannot be taken.
	 * @return Server - the server of the lobby.
	 * @since 0.0.1
	 */
	public static Server getSuitableLobby(Server... blacklist) {
		List<Server> blacklistServers = Arrays.asList(blacklist);
		return getLobbyServers().stream()
				.filter(server -> !blacklistServers.contains(server))
				.filter(server -> server.getPlayerCount() + 1 < server.getMaxPlayers())
				.max(Comparator.comparing(Server::getPlayerCount)).orElse(null);
	}

	/**
	 * Sends player to a lobby.
	 *
	 * @param blackListLobbies List<Server> - The lobbies the player cannot be sent
	 * @param sendingPlayers   Queue<UUID> - A queue of players which should be sent.
	 * @since 0.0.1
	 */
	public static void sendPlayerToLobby(List<Server> blackListLobbies, Queue<UUID> sendingPlayers) {
		Server lobby = getSuitableLobby(blackListLobbies.toArray(new Server[0]));

		if (lobby == null) {
			log.warn("No suitable lobby to send player: {}", sendingPlayers);
			return;
		}

		CloudEvent event = new CloudEvent(CloudEvent.BUNGEE_SEND_PLAYER).addData(lobby.getServerId());

		int canBeMoved = lobby.getMaxPlayers() - lobby.getPlayerCount() - 1;
		for (int i = 0; i < sendingPlayers.size() && i < canBeMoved; i++)
			event.addData(sendingPlayers.poll());

		EventSendingHelper.sendToBungeeCord(event);

		if (!sendingPlayers.isEmpty()) {
			blackListLobbies.add(lobby);
			sendPlayerToLobby(blackListLobbies, sendingPlayers);
		}
	}
}
