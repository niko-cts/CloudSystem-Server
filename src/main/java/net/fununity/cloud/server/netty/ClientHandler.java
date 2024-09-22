package net.fununity.cloud.server.netty;

import io.netty.channel.ChannelHandlerContext;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.events.cloud.CloudEvent;
import net.fununity.cloud.server.server.Server;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler class for all cloud clients.
 *
 * @author Niko
 * @since 0.0.1
 */
@Slf4j
@Getter
public class ClientHandler {

	private final Map<String, ChannelHandlerContext> clients;

	/**
	 * Default constructor for the ClientHandler.
	 *
	 * @since 0.0.1
	 */
	public ClientHandler() {
		this.clients = new ConcurrentHashMap<>();
	}


	/**
	 * Returns a client based on the given client id or null.
	 *
	 * @param clientId String - the client id.
	 * @return ChannelHandlerContext - the context of the client or null.
	 * @since 0.0.1
	 */
	public @Nullable ChannelHandlerContext getClientContext(@NotNull String clientId) {
		return this.clients.get(clientId);
	}

	/**
	 * Saves a client to the clients map.
	 *
	 * @param clientId String - the id of the client.
	 * @param ctx      ChannelHandlerContext - the context of the client.
	 * @since 0.0.1
	 */
	public void saveClient(@NotNull String clientId, @NotNull ChannelHandlerContext ctx) {
		this.clients.putIfAbsent(clientId, ctx);
		ctx.channel().closeFuture().addListener(channelFuture -> removeClient(ctx));
	}

	/**
	 * Removes a client based on the given client id.
	 *
	 * @param clientId String - the client id.
	 * @since 0.0.1
	 */
	public void removeClient(@NotNull String clientId) {
		this.clients.remove(clientId);
		log.debug("Client id {} was removed", clientId);
	}

	/**
	 * Removes a client based on the given ChannelHandlerContext.
	 *
	 * @param ctx ChannelHandlerContext - the context.
	 * @since 0.0.1
	 */
	public void removeClient(@NotNull ChannelHandlerContext ctx) {
		getClients().entrySet().stream().filter(c -> c.getValue().channel() == ctx.channel()).forEach(c -> removeClient(c.getKey()));
	}

	/**
	 * Sends an event to the server.
	 *
	 * @param serverId String - server to send event.
	 * @param event  Event - the event
	 */
	public void sendEvent(@NotNull String serverId, @NotNull CloudEvent event) {
		ChannelHandlerContext ctx = getClientContext(serverId);
		if (ctx != null) {
			log.debug("Sending event to server-id {}: {}", serverId, event);
			ctx.writeAndFlush(event);
		} else {
			log.error("Tried to send an event to ServerId '{}' but CTX was null. Event: {}", serverId, event);
		}
	}

	/**
	 * Sends an event to the server.
	 *
	 * @param server Server - server to send event.
	 * @param event  Event - the event
	 */
	public void sendEvent(@NotNull Server server, @NotNull CloudEvent event) {
		ChannelHandlerContext ctx = getClientContext(server.getServerId());
		if (ctx != null) {
			log.debug("Sending event to server {}: {}", server.getServerId(), event);
			ctx.writeAndFlush(event);
		} else {
			log.error("Tried to send an event to Server '{}:{}' but CTX was null. Event: {}", server.getServerId(), server.getServerPort(), event);
		}
	}

	/**
	 * Sends an event to the specified CTX
	 *
	 * @param ctx   ChannelHandlerContext - Channel to send
	 * @param event {@link CloudEvent} - Event to send
	 * @since 0.0.1
	 */
	public void sendEvent(@NotNull ChannelHandlerContext ctx,@NotNull CloudEvent event) {
		log.debug("Sending to '{}' event: {}", getClientId(ctx), event);
		ctx.writeAndFlush(event);
	}


	/**
	 * Get the client-id from the ctx
	 *
	 * @param ctx ChannelHandlerContext - the channel
	 * @return String - the id of the client
	 * @since 0.0.1
	 */
	public @Nullable String getClientId(ChannelHandlerContext ctx) {
		return getClients().entrySet().stream().filter(c -> c.getValue().channel() == ctx.channel()).findFirst().map(Map.Entry::getKey).orElse(null);
	}
}

