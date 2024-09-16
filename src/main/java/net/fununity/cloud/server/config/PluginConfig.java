package net.fununity.cloud.server.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class PluginConfig {

	@JsonProperty("name")
	private String name;

	@JsonProperty("path")
	private String path;

	@JsonProperty("nexusUrl")
	private String nexusUrl;

	@JsonProperty("repositoryId")
	private String repositoryId;

	@JsonProperty("group")
	private String group;

	@JsonProperty("artifactId")
	private String artifactId;

	@JsonProperty("version")
	private String version;
}
