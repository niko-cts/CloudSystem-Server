package net.fununity.cloud.server.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
public class LogFileCleanerUtil {

	@NotNull
	private final File directory;
	private final OffsetDateTime deleteBefore;

	public void clean() {
		if (!directory.exists() || directory.listFiles() == null) {
			log.warn("Directory {} does not exist!", directory);
			return;
		}

		int amount = 0;
		for (File file : Objects.requireNonNull(directory.listFiles())) {
			if (file.getName().endsWith(".log")) {
				String[] s = file.getName().split(" ");
				try {
					OffsetDateTime fileDateTime = OffsetDateTime.parse(s[0], DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

					if (fileDateTime.isBefore(deleteBefore)) {
						if (file.delete()) {
							amount++;
						}
					}
				} catch (DateTimeParseException e) {
					log.warn("Could not parse date from file name: {}", file.getName());
				}
			}
		}

		if (amount > 0) {
			log.info("Deleted {} log files which were older than {}.", amount, deleteBefore);
		}
	}

}
