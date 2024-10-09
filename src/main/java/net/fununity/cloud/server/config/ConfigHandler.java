package net.fununity.cloud.server.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.common.util.SystemConstants;
import net.fununity.cloud.server.CloudServer;
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


	public ConfigHandler(Path directory) {
		this.pluginConfig = new ArrayList<>();
		this.serverConfig = new LinkedList<>();
		this.configPath = directory.resolve("network-configuration.json");
		log.debug("Booting up ConfigHandler...");
		checkAndCreateDefaultConfig();
		loadNetworkConfig();

		String customServers = System.getProperty("customServers");

		if (customServers == null) {
			startDefaultServers();
		} else if (List.of("none", "null", "no", "off").contains(customServers.toLowerCase())) {
			log.info("No servers will start on boot. (Custom servers: {})", customServers);
		} else {
			log.info("Starting custom servers: {}", customServers);
			for (String server : customServers.split(",")) {
				try {
					CloudServer.getInstance().getServerManager().createServerByServerType(ServerType.valueOf(server));
				} catch (IllegalArgumentException exception) {
					log.warn("Could not start server, because illegal servertype: {}", server);
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
			try (InputStream fileStream = Objects.requireNonNull(Thread.currentThread().getContextClassLoader().getResourceAsStream("network-configuration.json"))) {
				Files.createDirectories(configPath);
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

			if (networkConfig.isEnableRepositoryManager() &&
			    (System.getProperty(SystemConstants.PROP_NEXUS_USER) == null ||
			     System.getProperty(SystemConstants.PROP_NEXUS_PASSWORD) == null)) {
				this.networkConfig.setEnableRepositoryManager(false);
				log.warn("Credentials for Nexus Repository Manager is not set. Disabling repository manager.");
			}
			this.pluginConfig.clear();
			if (networkConfig.getPluginConfigs() != null)
				this.pluginConfig.addAll(networkConfig.getPluginConfigs());
			this.serverConfig.clear();
			if (networkConfig.getServerConfigs() != null)
				this.serverConfig.addAll(networkConfig.getServerConfigs().stream().sorted(Comparator.comparing(ServerConfig::getPriority)).toList());

			log.debug("Network config-file is: {}", networkConfig);
			log.debug("Found {} servers", serverConfig.size());
			log.debug("Found {} plugins", pluginConfig.size());

			String unsetServer = Arrays.stream(ServerType.values()).filter(s -> getByServerConfigByType(s).isEmpty()).map(Enum::name).collect(Collectors.joining(","));
			if (!unsetServer.isEmpty())
				log.warn("Some server were not be set in the server config but are found as ServerTypes: {}", unsetServer);

			String notfoundPlugins = getPluginConfig().stream()
					.filter(f -> !new File(f.getLocalPath()).exists())
					.map(PluginConfig::getName).collect(Collectors.joining(", "));
			if (!notfoundPlugins.isEmpty())
				log.warn("The following plugin-names have not be found locally: {}", notfoundPlugins);
		} catch (IOException e) {
			log.error("The network config file could not been loaded correctly: ", e);
		}
	}

	public void startDefaultServers() {
		log.info("Starting default servers: {}", serverConfig.stream().filter(s -> s.getAmountOnStartup() > 0).map(s -> s.getServerType() + ": " + s.getAmountOnStartup()).collect(Collectors.joining(", ")));
		for (ServerConfig server : serverConfig) {
			if (server.getAmountOnStartup() > 0) {
				for (int i = 0; i < server.getAmountOnStartup(); i++)
					CloudServer.getInstance().getServerManager().createServerByServerType(server.getServerType());
			}
		}
	}

	public Optional<ServerConfig> getByServerConfigByType(ServerType serverType) {
		return serverConfig.stream().filter(s -> s.getServerType() == serverType).findFirst();
	}

	public List<PluginConfig> getByNames(List<String> pluginNames) {
		return pluginConfig.stream().filter(plugin -> pluginNames.contains(plugin.getName())).toList();
	}
}
