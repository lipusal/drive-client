package client.download;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.Change;
import com.google.api.services.drive.model.ChangeList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class ChangesTracker implements Callable<List<Change>> {
    private final Drive drive;
    private String token;
    private Logger logger = LoggerFactory.getLogger(getClass());

    public ChangesTracker(Drive drive, String token) {
        this.drive = drive;
        this.token = token;
    }

    public ChangesTracker(Drive drive) {
        this(drive, null);
    }

    private void requestToken() throws IOException {
        token = drive.changes().getStartPageToken().execute().getStartPageToken();  // Set to retrieve token; execute (ie. fire to server) and retrieve result
        logger.debug("Start token: {} (TODO remove this log)", token);
    }

    @Override
    public List<Change> call() throws Exception {
        if (token == null) {
            requestToken();
        }

        List<Change> result = new ArrayList<>();
        String currentToken = this.token;
        while (currentToken != null) {
            ChangeList changeList = drive.changes().list(currentToken).setFields("kind,newStartPageToken,changes").execute();   // Add all the fields in the "changes" element, we want "parents" specifically
            result.addAll(changeList.getChanges());
            if (changeList.getNewStartPageToken() != null) {
                // Last page, save this token for the next polling interval
                this.token = changeList.getNewStartPageToken();
            }
            currentToken = changeList.getNextPageToken();
        }
        return result;
    }

    public final Consumer<List<Change>> defaultChangeConsumer = changes -> {
        for (Change change : changes) {
            StringBuilder fullPath = new StringBuilder("Change found for file /");
            Optional.ofNullable(change.getFile().getParents()).orElse(Collections.emptyList()).forEach(s -> fullPath.append(s).append("/"));
            fullPath.append(change.getFile().getName())
                    .append(" (").append(change.getFileId()).append(")")
                    .append(" at ").append(change.getTime().toStringRfc3339());
            logger.debug(fullPath.toString());
        }
        if (changes.isEmpty()) {
            logger.debug("No changes at {}", LocalDateTime.now().toString());
        }
    };
}
