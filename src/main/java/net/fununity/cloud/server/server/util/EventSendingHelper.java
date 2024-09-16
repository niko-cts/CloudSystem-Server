package net.fununity.cloud.server.server.util;

import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.misc.MinigameHandler;
import net.fununity.cloud.server.netty.ClientHandler;
import net.fununity.cloud.server.server.Server;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * This class holds helper methods for sending specific events to server.
 * @author Niko
 */
public class EventSendingHelper {

	public static final ClientHandler CLIENT_HANDLER = CloudServer.getInstance().getClientHandler();

	private EventSendingHelper() {
		throw new IllegalAccessError("Utility class");
	}

	/**
	 * Sends the current lobby information to all lobbies.
	 *
	 * @since 0.0.1
	 */
	public static void sendLobbyInformationToLobbies() {
		CloudEvent cloudEvent = new CloudEvent(CloudEvent.RES_LOBBY_INFOS);
		Map<String, Integer> lobbyInformation = new HashMap<>();
		List<Server> lobbies = ServerUtils.getLobbyServers();
		for (Server lobbyServer : lobbies)
			lobbyInformation.put(lobbyServer.getServerId(), lobbyServer.getPlayerCount());

		cloudEvent.addData(lobbyInformation);

		lobbies.forEach(lobby -> CLIENT_HANDLER.sendEvent(lobby, cloudEvent));
		sendToBungeeCord(cloudEvent);
	}


	/**
	 * Send coc attack server
	 *
	 * @since 1.0.0
	 */
	public static void sendCocAttackServerAmount() {
		var servers = CloudServer.getInstance().getServerManager().getRunningServerByType(ServerType.COCATTACK);
		for (Server cocbase : servers) {
			CLIENT_HANDLER.sendEvent(cocbase, new CloudEvent(CloudEvent.COC_RESPONSE_ATTACK_SERVER_AMOUNT).addData(servers.size()));
		}
	}

	/**
	 * Sends minigame data to the server.
	 *
	 * @param lobbyId String - the lobby id that requested the status.
	 * @since 0.0.1
	 */
	public static void sendMinigameInformationToLobby(@NotNull String lobbyId) {
		MinigameHandler.getInstance().getLobbyServers().forEach(serverId ->
				CLIENT_HANDLER.sendEvent(serverId, new CloudEvent(CloudEvent.RES_LOBBY_INFOS).addData(lobbyId)));
	}

	/**
	 * Sends the given cloud event to all bungeecord servers.
	 *
	 * @param event CloudEvent - the cloud event to send.
	 * @since 0.0.1
	 */
	public static void sendToBungeeCord(@NotNull CloudEvent event) {
		ServerUtils.getBungeeServers().forEach(s -> CLIENT_HANDLER.sendEvent(s, event));
	}

	/**
	 * Sends the given cloud event to all bungeecord servers.
	 *
	 * @param event CloudEvent - the cloud event to send.
	 * @since 0.0.1
	 */
	public static void sendToLobby(@NotNull CloudEvent event) {
		ServerUtils.getLobbyServers().forEach(s -> CLIENT_HANDLER.sendEvent(s, event));
	}

	public static void sendPlayerToLobby(List<Server> blacklisted, Queue<UUID> playerToSend) { // TODO
	}
}
