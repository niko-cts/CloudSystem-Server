package net.fununity.cloud.server.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
public class LogFileCleanerUtil implements Runnable {

    public static final String DATE_PATTERN = "yyyy-MM-dd";
    @NotNull
    private final File directory;
    private final OffsetDateTime deleteBefore;

    public void run() {
        if (!directory.exists() || directory.listFiles() == null) {
            log.warn("Directory {} does not exist!", directory);
            return;
        }
        log.debug("Starting log cleaner for directory: {}", directory);

        int amount = 0;
        for (File file : Objects.requireNonNull(directory.listFiles())) {
            if (file.getName().endsWith(".log")) {
                String[] s = file.getName().split(" ");
                try {
                    String dateAndTime = s[0].split("_")[0];
                    LocalDate fileDateTime = LocalDate.parse(dateAndTime, DateTimeFormatter.ofPattern(DATE_PATTERN));

                    if (fileDateTime.isBefore(deleteBefore.toLocalDate())) {
                        if (file.delete()) {
                            amount++;
                        }
                    }
                } catch (DateTimeParseException e) {
                    log.warn("Could not parse date by pattern {} from file name: {}", DATE_PATTERN, file.getName());
                } catch (Exception e) {
                    log.warn("Error while deleting file: {}", file.getName(), e);
                }
            }
        }

        if (amount > 0) {
            log.info("Deleted {} log files which were older than {}.", amount, deleteBefore);
        }
    }

}
