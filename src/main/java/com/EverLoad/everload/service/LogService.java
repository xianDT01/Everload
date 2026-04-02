package com.EverLoad.everload.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

@Slf4j
@Service
public class LogService {

    private static final String LOG_PATH = "everload.log";

    public List<String> getLines(int lines, String filter) throws IOException {
        Path path = Path.of(LOG_PATH);
        if (!Files.exists(path)) {
            return List.of("Log file not found");
        }
        List<String> all = Files.readAllLines(path);
        return all.stream()
                .filter(line -> filter == null || line.toLowerCase().contains(filter.toLowerCase()))
                .skip(Math.max(0, all.size() - lines))
                .toList();
    }

    public boolean clearLog() throws IOException {
        Path path = Path.of(LOG_PATH);
        if (!Files.exists(path)) return false;
        Files.write(path, new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
        return true;
    }
}