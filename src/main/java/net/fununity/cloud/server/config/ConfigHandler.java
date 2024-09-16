package net.fununity.cloud.server.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.server.ServerHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Singleton class for handling the cloud configuration.
 * Used to store/retrieve default servers.
 *
 * @author Niko
 * @since 0.0.1
 */
@Slf4j
public class ConfigHandler {

	@Getter
	@Nullable
	private NetworkConfig networkConfig;
	@Getter
	@NotNull
	private final List<PluginConfig> pluginConfig;
	@Getter
	@NotNull
	private final List<ServerConfig> serverConfig;
	@NotNull
	private final Path configPath;
	@Nullable
	private final String repoUsername;
	@Nullable
	private final String repoPassword;


	public ConfigHandler(String... args) {
		this.pluginConfig = new ArrayList<>();
		this.serverConfig = new LinkedList<>();
		this.configPath = Path.of("network-configuration.json");
		log.debug("Boot up ConfigHandler...");
		checkAndCreateDefaultConfig();
		loadNetworkConfig();

		String customServers = System.getProperty("customServers");
		this.repoUsername = System.getProperty("repo.username");
		this.repoPassword = System.getProperty("repo.password");

		if (customServers == null) {
			startDefaultServers();
		} else {
			for (String server : customServers.split(",")) {
				try {
					ServerHandler.getInstance().createServerByServerType(ServerType.valueOf(server));
				} catch (IllegalArgumentException exception) {
					log.error("Could not start server, because illegal servertype: {}", server);
				}

			}
		}
	}

	/**
	 * Saves the default configuration to the file.
	 *
	 * @since 0.0.1
	 */
	private void checkAndCreateDefaultConfig() {
		if (!Files.exists(configPath)) {
			log.debug("Default network config does not exist. Copying jar...");

			try (InputStream fileStream = Objects.requireNonNull(getClass().getResourceAsStream("resources/network-configuration.json"))) {
				Files.createDirectories(configPath.getParent());
				Files.copy(fileStream, configPath, StandardCopyOption.REPLACE_EXISTING);
				log.info("Default network config created.");
			} catch (IOException e) {
				log.error("Could not copy default network config file", e);
			}
		}
	}

	public void loadNetworkConfig() {
		log.debug("Loading network config...");
		try {
			ObjectMapper objectMapper = new ObjectMapper();
			this.networkConfig = objectMapper.readValue(configPath.toFile(), new TypeReference<>() {
			});

			this.networkConfig.setEnableRepositoryManager(networkConfig.isEnableRepositoryManager() && this.repoPassword != null && this.repoUsername != null);
			this.pluginConfig.clear();
			this.pluginConfig.addAll(networkConfig.getPluginConfigs());
			this.serverConfig.clear();
			this.serverConfig.addAll(networkConfig.getServerConfigs().stream().sorted(Comparator.comparing(ServerConfig::getPriority)).toList());

			log.debug("Network config-file is: {}", networkConfig);
			log.debug("Found {} servers", serverConfig.size());
			log.debug("Found {} plugins", pluginConfig.size());

			String unsetServer = Arrays.stream(ServerType.values()).filter(s -> getByServerConfigByType(s).isEmpty()).map(Enum::name).collect(Collectors.joining(","));
			if (!unsetServer.isEmpty())
				log.warn("Some server were not be set in the server config but are found as ServerTypes: {}", unsetServer);

			String notfoundPlugins = getPluginConfig().stream().filter(f -> !new File(f.getPath()).exists()).map(PluginConfig::getName).collect(Collectors.joining(", "));
			if (!notfoundPlugins.isEmpty())
				log.warn("The following plugin-names do not have a valid path: {}", notfoundPlugins);
		} catch (IOException e) {
			log.error("The network config file could not been loaded correctly: ", e);
		}
	}

	public void startDefaultServers() {
		log.info("Starting servers: {}", serverConfig.stream().filter(s -> s.getAmountOnStartup() > 0).map(s -> s.getServerType() + ": " + s.getAmountOnStartup()).collect(Collectors.joining(", ")));
		for (ServerConfig server : serverConfig) {
			if (server.getAmountOnStartup() > 0) {
				for (int i = 0; i < server.getAmountOnStartup(); i++)
					ServerHandler.getInstance().createServerByServerType(server.getServerType());
			}
		}
	}

	public Optional<ServerConfig> getByServerConfigByType(ServerType serverType) {
		return serverConfig.stream().filter(s -> s.getServerType() == serverType).findFirst();
	}
}
