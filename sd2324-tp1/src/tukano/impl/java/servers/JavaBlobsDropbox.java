package tukano.impl.java.servers;



import static utils.ExternalServices.BLOBS_DROPBOX;
import static utils.ExternalServices.HTTP_SUCCESS;
import static tukano.api.java.Result.ErrorCode.*;
import static tukano.api.java.Result.error;
import static tukano.api.java.Result.ok;
import static java.lang.String.format;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import com.github.scribejava.core.model.Response;
import tukano.api.java.Result;
import tukano.impl.api.dropbox.DeleteAllArgs;
import tukano.impl.api.java.ExtendedBlobs;
import tukano.impl.java.clients.Clients;
import utils.*;

public class JavaBlobsDropbox implements ExtendedBlobs {

   // private static final String BLOBS_ROOT_DIR = DROPBOX_ROOT+"/"+ IP.hostName()+"/";
    private static final String BLOBS_ROOT_DIR = BLOBS_DROPBOX +"/";
    private static Logger Log = Logger.getLogger(JavaBlobs.class.getName());
    private static final int CHUNK_SIZE = 4096;

    @Override
    public Result<Void> upload(String verifier, byte[] bytes) {
        Log.info(() -> format("upload : blobId = %s, sha256 = %s\n", verifier, Hex.of(Hash.sha256(bytes))));

        if (!validBlobId(verifier))
            return error(FORBIDDEN);

        var file = toStringPath(verifier);

        if (file == null)
            return error(BAD_REQUEST);

        var dir = file.substring(0, file.lastIndexOf("/"));

        try {
            var up = new ExternalServices();

            if (!up.createDirectory(dir))
                return error(INTERNAL_ERROR);

            if (fileExists(file, up))
                return error(CONFLICT);

            up.write(file, bytes);
            return ok();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Result<byte[]> download(String blobId) {

        if (blobId == null)
            return error(BAD_REQUEST);

        var file = toStringPath(blobId);
        Log.info(() -> format("download : file = %s\n", file));

        var down = new ExternalServices();

        if (!fileExists(file, down))
            return error(NOT_FOUND);

        try {
            return ok(down.read(file));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Result<Void> delete(String blobId, String token) {
        Log.info(() -> format("delete : blobId = %s, token=%s\n", blobId, token));

        if (!Token.matches(token))
            return error(FORBIDDEN);

        var file = toStringPath(blobId);

        if (file == null)
            return error(BAD_REQUEST);

        var fileTo = new ExternalServices();

        if (!fileExists(file, fileTo))
            return error(NOT_FOUND);

        try {
            fileTo.delete(file);
            return ok();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Result<Void> deleteAllBlobs(String userId, String token) {
        Log.info(() -> format("deleteAllBlobs : userId = %s\n", userId));

        if (!Token.matches(token))
            return error(FORBIDDEN);

        var path = BLOBS_ROOT_DIR + userId;
        var filesTo = new ExternalServices();
        Log.info(() -> format("delete path = %s\n", path));

        try {
            DeleteAllArgs all = new DeleteAllArgs(new LinkedList<>());
            all.addEntries(path);

            try (Response r = filesTo.delete(all.getEntries())) {

                if (r.getCode() != HTTP_SUCCESS)
                    throw new RuntimeException(String.format("Failed to delete files: %s, Status: %d, \nReason: %s\n", userId, r.getCode(), r.getBody()));
                return ok();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean validBlobId(String blobId) {
        return Clients.ShortsClients.get().getShort(blobId).isOK();
    }

    private String toStringPath(String blobId) {
        var parts = blobId.split("-");
        if (parts.length != 2)
            return null;

        return (BLOBS_ROOT_DIR + parts[0] + "/" + parts[1]);
    }

    private Boolean fileExists(String file, ExternalServices es) {
        var dir = file.substring(0, file.lastIndexOf("/"));
        var blob = file.substring(file.lastIndexOf("/") + 1);

        try {
            List<String> filesList = es.listDirectory(dir);
            if (filesList.isEmpty())
                return false;

            for (String filename : filesList)
                Log.info(() -> format("path : %s\n", filename));

            return filesList.contains(blob);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
