package net.fununity.cloud.server.command;

import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.command.handler.Command;
import net.fununity.cloud.server.server.Server;
import net.fununity.cloud.server.server.util.ServerUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ListCommand extends Command {

	/**
	 * Instantiate this class with the name of a command and with no or x aliases
	 * @since 0.0.1
	 */
	public ListCommand() {
		super("list", "list (<servertype>)", "Shows all server or all from one server type");
	}

	/**
	 * Will be called when the user typed in the command name or aliase.
	 * @param args String[] - The arguments behind the command
	 * @since 0.0.1
	 */
	@Override
	public void execute(String[] args) {
		List<Server> servers = new ArrayList<>(manager.getRunningServers());
		List<Server> startQueue = new ArrayList<>(manager.getStartQueue());
		List<Server> stopQueue = new ArrayList<>(manager.getStopQueue());
		if (args.length == 1) {
			try {
				ServerType serverType = ServerType.valueOf(args[0]);
				servers.removeIf(d -> d.getConfig().getServerType() != serverType);
				startQueue.removeIf(d -> d.getConfig().getServerType() != serverType);
				stopQueue.removeIf(d -> d.getConfig().getServerType() != serverType);
				log.info("Showing servers of type {}", serverType);
			} catch (IllegalArgumentException exception) {
				sendIllegalServerType();
				return;
			}
		} else {
			servers.sort(Comparator.comparingInt(value -> value.getConfig().getServerType().ordinal()));
			startQueue.sort(Comparator.comparingInt(value -> value.getConfig().getServerType().ordinal()));
			stopQueue.sort(Comparator.comparingInt(value -> value.getConfig().getServerType().ordinal()));
			log.info("Showing all servers. Players on network: {}", ServerUtils.getPlayerCountOfNetwork());
		}

		log.info("{} running server: {}", servers.size(), servers.stream()
				.map(server -> String.format("%s: %s/%s", server.getServerId(), server.getPlayerCount(), server.getMaxPlayers()))
				.collect(Collectors.joining(", ")));

		if (!startQueue.isEmpty()) {
			log.info("{} in start queue: {}",
					startQueue.size(),
					startQueue.stream().map(Server::getServerId).collect(Collectors.joining(", ")));
		}
		if (!stopQueue.isEmpty()) {
			log.info("{} in stop queue: {}",
					stopQueue.size(),
					stopQueue.stream().map(Server::getServerId).collect(Collectors.joining(", ")));
		}
	}
}
