package tukano.impl.java.servers.monitoring;


import tukano.impl.api.java.ExtendedBlobs;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static java.lang.String.format;
import static tukano.impl.java.clients.Clients.BlobsClients;
import static tukano.impl.java.servers.JavaShorts.*;

public class ServiceMonitor {

    private static Logger Log = Logger.getLogger(ServiceMonitor.class.getName());
    private static ServiceMonitor instance;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    private ServiceMonitor() {
        startBlobsSync();
    }

    public static synchronized ServiceMonitor getInstance() {
        if (instance == null) {
            instance = new ServiceMonitor();
        }
        return instance;
    }

    private void startBlobsSync() {
        executorService.scheduleAtFixedRate(() -> {
            try {
                var servers = blobCountCache.get(BLOB_COUNT);

                Set<String> uris = new HashSet<>();
                for (ExtendedBlobs blob : BlobsClients.all()) {
                    uris.add(blob.toString());
                }

                for (var blobserver : servers.keySet()) {
                    if (!uris.contains(blobserver)) {
                        Log.info(() -> format("Removing blob server %s from the list of candidates\n", blobserver));
                        removeFromBlobUrl(blobserver);
                        blobCountCache.invalidate(blobserver);
                    }
                }
            } catch (ExecutionException e) {
                Log.severe("Failed to monitor blobs: " + e.getMessage());
                e.printStackTrace();
            }
        }, 0, 5, TimeUnit.SECONDS);
    }

}
