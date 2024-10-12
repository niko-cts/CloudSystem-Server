package net.fununity.cloud.server.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Setter
@Getter
public class PluginConfig {

	@JsonProperty(value = "name", required = true)
	@NotNull
	private String name;

	@JsonProperty(value = "localPath", required = true)
	@NotNull
	private String localPath;

	@JsonProperty(value = "repository")
	@Nullable
	private RepositoryConfig repository;

}
