package net.fununity.cloud.server.netty.listeners;

import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.misc.MinigameHandler;
import net.fununity.cloud.server.netty.ClientHandler;
import net.fununity.cloud.server.server.ServerHandler;
import net.fununity.cloud.server.server.ServerManager;
import net.fununity.cloud.server.server.shutdown.ServerShutdown;
import net.fununity.cloud.server.server.util.EventSendingHelper;
import net.fununity.cloud.server.server.util.ServerUtils;

import java.util.*;


@Slf4j
public class RequestEventListener extends AbstractEventListener {

	private static final ClientHandler CLIENT_HANDLER = CloudServer.getInstance().getClientHandler();
	private static final ServerManager MANAGER = CloudServer.getInstance().getServerManager();

	@Override
	public void newCloudEvent(CloudEvent cloudEvent) {
		final ChannelHandlerContext ctx = (ChannelHandlerContext) cloudEvent.getData().getLast();
		switch (cloudEvent.getId()) {
			case CloudEvent.REQ_LOBBY_COUNT ->
					CLIENT_HANDLER.sendEvent(ctx, new CloudEvent(CloudEvent.RES_LOBBY_INFOS).addData(ServerUtils.getLobbyServers().size()));
			case CloudEvent.REQ_SERVER_INFO -> {
				int port = Integer.parseInt(String.valueOf(cloudEvent.getData().getFirst()));

				MANAGER.getServerDefinitionByPort(port).ifPresentOrElse(
						def -> CLIENT_HANDLER.sendEvent(ctx, new CloudEvent(CloudEvent.RES_SERVER_INFO).addData(def)),
						() -> log.warn("Could not send server definition for port {}", port));
			}

			case CloudEvent.REQ_SERVER_CREATE_BY_TYPE ->
					MANAGER.createServerByServerType((ServerType) cloudEvent.getData().getFirst());

			case CloudEvent.REQ_SERVER_SHUTDOWN -> {
				String serverId = cloudEvent.getData().size() == 1 ?
						CLIENT_HANDLER.getClientId(ctx) :
						String.valueOf(cloudEvent.getData().getFirst());

				getServerByIdentifierOrLogWarning(serverId,
						server -> MANAGER.requestStopServer(server, new ServerShutdown() {
							@Override
							public void serverStopped() {
							}

							@Override
							public boolean needsMinigameCheck() {
								return true;
							}
						}),
						doesNotExist -> {
							log.warn("Received REQ_SERVER_SHUTDOWN without additional serverId and could not map CTX to any registered Client! CTX was {}", ctx);
							CLIENT_HANDLER.sendEvent(ctx, new CloudEvent(CloudEvent.CLIENT_DISCONNECT_GRACEFULLY));
							ctx.close();
						}
				);
			}

			case CloudEvent.REQ_SERVER_RESTART -> getServerByIdentifierOrLogWarning(cloudEvent.getData().getFirst(),
					MANAGER::requestRestartServer,
					"Tried to restart server but serverId was not found {}");
			case CloudEvent.REQ_SERVER_TYPE -> {
				getServerByIdentifierOrLogWarning(cloudEvent.getData().getFirst(),
						server -> CLIENT_HANDLER.sendEvent(ctx, new CloudEvent(CloudEvent.RES_SERVER_TYPE).addData(server.getConfig().getServerType())),
						"Server type was requested but server id was not found {}");
			}
			case CloudEvent.REQ_SEND_PLAYER_DIFFERENT_LOBBY -> {
				getServerByIdentifierOrLogWarning(cloudEvent.getData().getFirst(), server -> {
					Queue<UUID> queue = new LinkedList<>();
					for (int i = 1; i < cloudEvent.getData().size() - 1; i++)
						queue.add((UUID) cloudEvent.getData().get(i));
					EventSendingHelper.sendPlayerToLobby(new ArrayList<>(List.of(server)), queue);
				}, "Player should be sent to different lobby but current server id could not be found: {}");

			}
			case CloudEvent.REQ_SEND_PLAYER_TO_LOBBY -> {
				Queue<UUID> queue = new LinkedList<>();
				for (int i = 0; i < cloudEvent.getData().size() - 1; i++)
					queue.add((UUID) cloudEvent.getData().get(i));
				ServerHandler.getInstance().sendPlayerToLobby(new ArrayList<>(), queue);
			}
			case CloudEvent.REQ_MINIGAME_RESEND_STATUS ->
					EventSendingHelper.sendMinigameInformationToLobby(String.valueOf(cloudEvent.getData().getFirst()));
			case CloudEvent.REQ_PLAYER_COUNT_SERVER ->
					CLIENT_HANDLER.sendEvent(ctx, new CloudEvent(CloudEvent.RES_PLAYER_COUNT_SERVER).addData(ServerUtils.getPlayerCountOfServer(String.valueOf(cloudEvent.getData().getFirst()))));
			case CloudEvent.REQ_PLAYER_COUNT_TYPE ->
					getServerTypeFromObjectOrLogWarning(cloudEvent.getData().getFirst(),
							serverType -> CLIENT_HANDLER.sendEvent(ctx, new CloudEvent(CloudEvent.RES_PLAYER_COUNT_TYPE)
									.addData(ServerUtils.getPlayerCountOfServerType(serverType))),
							"Could not send player count type because server type {} is illegal ");
			case CloudEvent.REQ_PLAYER_COUNT_NETWORK ->
					CLIENT_HANDLER.sendEvent(ctx, new CloudEvent(CloudEvent.RES_PLAYER_COUNT_NETWORK)
							.addData(ServerUtils.getPlayerCountOfNetwork()));
			case CloudEvent.REQ_MINIGAME_LOBBY_SEND ->
					getServerTypeFromObjectOrLogWarning(cloudEvent.getData().getFirst(),
							serverType -> MinigameHandler.getInstance().sendPlayerToMinigameLobby(serverType, (UUID) cloudEvent.getData().get(1)),
							"Could not send minigame lobby info because server type {} is illegal ");
		}
	}

}
