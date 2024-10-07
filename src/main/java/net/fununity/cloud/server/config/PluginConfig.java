package net.fununity.cloud.server.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PluginConfig {

	@JsonProperty("name")
	private String name;

	@JsonProperty("localPath")
	private String localPath;

	@JsonProperty("nexusPluginUrl")
	private String nexusPluginUrl;
}
