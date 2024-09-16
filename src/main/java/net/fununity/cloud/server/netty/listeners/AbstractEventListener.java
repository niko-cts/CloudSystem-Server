package net.fununity.cloud.server.netty.listeners;

import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.events.cloud.CloudEventListener;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.CloudServer;
import net.fununity.cloud.server.server.Server;

import java.util.function.Consumer;

@Slf4j
public abstract class AbstractEventListener implements CloudEventListener {

	protected void getServerByIdentifierOrLogWarning(Object serverId, Consumer<Server> onExists, String warnMessage) {
		CloudServer.getInstance().getServerManager().getServerByIdentifier(String.valueOf(serverId))
				.ifPresentOrElse(onExists, () -> log.warn(warnMessage, serverId));
	}

	protected void getServerByIdentifierOrLogWarning(Object serverId, Consumer<Server> onExists, Consumer<String> doesNotExist) {
		CloudServer.getInstance().getServerManager().getServerByIdentifier(String.valueOf(serverId))
				.ifPresentOrElse(onExists, () -> doesNotExist.accept(String.valueOf(serverId)));
	}

	protected void getServerTypeFromObjectOrLogWarning(Object serverType, Consumer<ServerType> serverTypeConsumer, String warnMessage) {
		try {
			serverTypeConsumer.accept(ServerType.valueOf(String.valueOf(serverType)));
		} catch (IllegalArgumentException exception) {
			log.warn(warnMessage, serverType);
		}
	}
}
