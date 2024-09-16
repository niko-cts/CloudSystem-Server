package net.fununity.cloud.server.netty.listeners;

import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.misc.MinigameHandler;
import net.fununity.cloud.server.netty.ClientHandler;
import net.fununity.cloud.server.server.Server;
import net.fununity.cloud.server.server.ServerHandler;
import net.fununity.cloud.server.server.ServerManager;
import net.fununity.cloud.server.server.util.EventSendingHelper;
import net.fununity.cloud.server.server.util.ServerUtils;

import java.util.List;

@Slf4j
public class GeneralEventListener extends AbstractEventListener {

	private static final ClientHandler CLIENT_HANDLER = CloudServer.getInstance().getClientHandler();
	private static final ServerManager MANAGER = CloudServer.getInstance().getServerManager();

	@Override
	public void newCloudEvent(CloudEvent cloudEvent) {
		ChannelHandlerContext ctx;
		String serverId;
		switch (cloudEvent.getId()) {
			case CloudEvent.CLIENT_REGISTER_INFO -> {
				ctx = (ChannelHandlerContext) cloudEvent.getData().getLast();
				String clientId = cloudEvent.getData().getFirst().toString();
				CLIENT_HANDLER.saveClient(clientId, ctx);

				int port;

				if (cloudEvent.getData().size() == 2) { // BUNGEE CORD ONLY
					List<Server> bungeeServers = ServerHandler.getInstance().getServersByType(ServerType.BUNGEECORD);
					if (bungeeServers.isEmpty()) {
						log.error("There was a bungeecord server registration, but no bungeecord is registered in the list! Sending disconnect...");
						CLIENT_HANDLER.sendEvent(ctx, new CloudEvent(CloudEvent.CLIENT_DISCONNECT_GRACEFULLY));
						return;
					}
					port = bungeeServers.getLast().getServerPort();
				} else {
					port = Integer.parseInt(cloudEvent.getData().get(1).toString());
				}

				CLIENT_HANDLER.remapChannelHandlerContext(ctx, port);

				MANAGER.getServerDefinitionByPort(port).ifPresentOrElse(def -> {
					CLIENT_HANDLER.sendEvent(ctx, new CloudEvent(CloudEvent.RES_SERVER_INFO).addData(def));

					if (def.serverType() == ServerType.LOBBY) {
						EventSendingHelper.sendLobbyInformationToLobbies();
					} else if (def.serverType() == ServerType.COCATTACK) {
						EventSendingHelper.sendCocAttackServerAmount();
					}

					log.info("Client registered: {}", def.serverId());
					MANAGER.getServerByIdentifier(def.serverId()).ifPresent(MANAGER::serverCompletelyStarted);
				}, () -> {
					log.error("Could not get server definition of {}:{}! Can not register server!", clientId, port);
					CLIENT_HANDLER.sendEvent(ctx, new CloudEvent(CloudEvent.CLIENT_DISCONNECT_GRACEFULLY));
					ctx.close();
				});
			}
			case CloudEvent.CLIENT_DISCONNECT_GRACEFULLY -> getServerByIdentifierOrLogWarning(cloudEvent.getData().getFirst(),
					Server::clientDisconnected, "Client {} sent disconnection, but could not be found.");
			case CloudEvent.BUNGEE_SERVER_REMOVED_RESPONSE ->
					getServerByIdentifierOrLogWarning(cloudEvent.getData().getFirst(),
							Server::bungeeRemovedServer, "Server removed response could not be made because serverId '{}' could not be found");
			case CloudEvent.CLIENT_ALIVE_RESPONSE -> getServerByIdentifierOrLogWarning(cloudEvent.getData().getFirst(),
					Server::receivedClientAliveResponse,
					"Server '{}' send alive response, but could not be found");
			case CloudEvent.NOTIFY_SERVER_PLAYER_COUNT -> {
				serverId = String.valueOf(cloudEvent.getData().getFirst());
				int playerCount = Integer.parseInt(cloudEvent.getData().get(1).toString());
				ServerUtils.serverPlayerCountUpdate(serverId, playerCount);
			}
			case CloudEvent.NOTIFY_NETWORK_PLAYER_COUNT ->
					ServerUtils.networkPlayerCountUpdate(Integer.parseInt(String.valueOf(cloudEvent.getData().getFirst())));
			case CloudEvent.FORWARD_TO_BUNGEE ->
					EventSendingHelper.sendToBungeeCord((CloudEvent) cloudEvent.getData().get(0));
			case CloudEvent.FORWARD_TO_SERVER -> {
				ctx = CLIENT_HANDLER.getClientContext(cloudEvent.getData().get(0).toString());
				if (ctx != null)
					CLIENT_HANDLER.sendEvent(ctx, (CloudEvent) cloudEvent.getData().get(1));
			}
			case CloudEvent.FORWARD_TO_SERVERTYPE -> {
				ServerType serverType = (ServerType) cloudEvent.getData().getFirst();
				List<Server> serversByType = MANAGER.getRunningServerByType(serverType);

				if (serversByType.isEmpty())
					break;

				CloudEvent forwardingEvent = (CloudEvent) cloudEvent.getData().get(1);

				if (forwardingEvent.getId() == CloudEvent.REQ_FOLLOW_ME) {
					forwardingEvent = new CloudEvent(CloudEvent.RES_FOLLOW_ME);
					for (int i = 0; i < ((CloudEvent) cloudEvent.getData().get(1)).getData().size(); i++)
						forwardingEvent.addData(((CloudEvent) cloudEvent.getData().get(1)).getData().get(i));
				} else if (forwardingEvent.getId() == CloudEvent.STATUS_MINIGAME) {
					MinigameHandler.getInstance().receivedStatusUpdate(forwardingEvent);
					if (forwardingEvent.getData().size() == 7) {
						CloudEvent finalToForward = forwardingEvent;
						serversByType.removeIf(s -> !s.getServerId().equals(finalToForward.getData().get(6).toString()));
					}
				}

				for (Server s : serversByType) {
					CLIENT_HANDLER.sendEvent(s, forwardingEvent);
				}
			}
		}
	}
}
