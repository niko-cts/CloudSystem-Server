package net.fununity.cloud.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.fununity.cloud.common.server.ServerType;
import net.fununity.cloud.server.config.ConfigHandler;
import net.fununity.cloud.server.config.NetworkConfig;
import net.fununity.cloud.server.config.PluginConfig;
import net.fununity.cloud.server.config.ServerConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConfigHandlerTest {

	public static final String NETWORK_CONFIGURATION_JSON = "network-configuration.json";
	@TempDir
	Path tempDir;
	private ConfigHandler configHandler;
	private CloudServer cloudServerMock;

	@BeforeEach
	void setUp() throws IOException {
		cloudServerMock = mock(CloudServer.class, RETURNS_DEEP_STUBS);
		CloudServer.setInstance(cloudServerMock);

		Path configFilePath = tempDir.resolve(NETWORK_CONFIGURATION_JSON);
		Files.createFile(configFilePath);
		configHandler = new ConfigHandler(tempDir);
	}

	@AfterAll
	static void tearDown() throws IOException {
		Files.deleteIfExists(Path.of(NETWORK_CONFIGURATION_JSON));
		CloudServer.setInstance(null);
	}

	@Test
	void loadNetworkConfig_validConfigFile_networkConfigLoaded() throws IOException {
		ObjectMapper objectMapper = new ObjectMapper();
		NetworkConfig networkConfig = new NetworkConfig();
		objectMapper.writeValue(tempDir.resolve(NETWORK_CONFIGURATION_JSON).toFile(), networkConfig);

		configHandler.loadNetworkConfig();

		assertNotNull(configHandler.getNetworkConfig());
	}

	@Test
	void loadNetworkConfig_invalidConfigFile_throwsIOException() throws IOException {
		Files.writeString(tempDir.resolve(NETWORK_CONFIGURATION_JSON), "invalid json");
		configHandler.loadNetworkConfig();
		assertNull(configHandler.getNetworkConfig());
	}

	@Test
	void startDefaultServers_noServersConfigured_noServersStarted() {
		configHandler.startDefaultServers();
		verify(cloudServerMock.getServerManager(), never()).createServerByServerType(any(ServerType.class));
	}

	@Test
	void startDefaultServers_serversConfigured_serversStarted() {
		ServerConfig serverConfig = new ServerConfig();
		serverConfig.setServerType(ServerType.BEATINGPIRATES);
		serverConfig.setAmountOnStartup(2);
		configHandler.getServerConfig().add(serverConfig);

		configHandler.startDefaultServers();

		verify(cloudServerMock.getServerManager(), times(2)).createServerByServerType(ServerType.BEATINGPIRATES);
	}

	@Test
	void getByServerConfigByType_existingServerType_returnsServerConfig() {
		ServerConfig serverConfig = new ServerConfig();
		serverConfig.setServerType(ServerType.BEATINGPIRATES);
		configHandler.getServerConfig().add(serverConfig);

		Optional<ServerConfig> result = configHandler.getByServerConfigByType(ServerType.BEATINGPIRATES);

		assertTrue(result.isPresent());
		assertEquals(ServerType.BEATINGPIRATES, result.get().getServerType());
	}

	@Test
	void getByServerConfigByType_nonExistingServerType_returnsEmpty() {
		Optional<ServerConfig> result = configHandler.getByServerConfigByType(ServerType.BEATINGPIRATES);

		assertFalse(result.isPresent());
	}

	@Test
	void getByNames_existingPluginNames_returnsPluginConfigs() {
		PluginConfig pluginConfig = new PluginConfig();
		pluginConfig.setName("plugin1");
		configHandler.getPluginConfig().add(pluginConfig);

		List<PluginConfig> result = configHandler.getByNames(List.of("plugin1"));

		assertEquals(1, result.size());
		assertEquals("plugin1", result.getFirst().getName());
	}

	@Test
	void getByNames_nonExistingPluginNames_returnsEmptyList() {
		List<PluginConfig> result = configHandler.getByNames(List.of("plugin1"));

		assertTrue(result.isEmpty());
	}
}