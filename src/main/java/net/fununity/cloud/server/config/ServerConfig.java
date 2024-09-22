package net.fununity.cloud.server.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import net.fununity.cloud.common.server.ServerType;

import java.util.List;

@Getter
@Setter
@ToString(exclude = "maxPlayers")
public class ServerConfig {
	@JsonProperty("serverType")
	private ServerType serverType;

	@JsonProperty("maxPlayers")
	private int maxPlayers;

	@JsonProperty("ram")
	private int ram;

	@JsonProperty("directory")
	private String directory;

	@JsonProperty("amountOnStartup")
	private int amountOnStartup;

	@JsonProperty("minimumAmount")
	private int minimumAmount;

	@JsonProperty("priority")
	private int priority;

	@JsonProperty("backup")
	private boolean backup;

	@JsonProperty("plugins")
	private List<String> plugins;
}
