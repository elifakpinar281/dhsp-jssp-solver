package at.fhv.experiment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class RunLogWriter {
    private final Path directory;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    public RunLogWriter() {
        this(Path.of("runs"));
    }

    public RunLogWriter(Path directory) {
        this.directory = directory;
    }

    public Path write(RunLog log) throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve(log.runId() + ".json");
        String json = gson.toJson(log);
        Files.write(target, json.getBytes(StandardCharsets.UTF_8));
        return target;
    }
}