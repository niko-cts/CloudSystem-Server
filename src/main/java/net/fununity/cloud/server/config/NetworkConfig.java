package net.fununity.cloud.server.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
public class NetworkConfig {

	@JsonProperty("hostname")
	private String hostname;

	@JsonProperty("port")
	private int port;

	@JsonProperty("maxRam")
	private int maxRam;

	@JsonProperty("playerAmountNewLobbyThreshold")
	private int playerAmountNewLobbyThreshold;

	@JsonProperty("aliveEventPeriodInMillis")
	private long aliveEventPeriodInMillis;

	@JsonProperty("startSendingAliveEventOnTimerRuns")
	private int startSendingAliveEventOnTimerRuns;

	@JsonProperty("hasRepositoryManager")
	private boolean enableRepositoryManager;

	@JsonProperty("servers")
	private List<ServerConfig> serverConfigs;

	@JsonProperty("plugins")
	private List<PluginConfig> pluginConfigs;

}
