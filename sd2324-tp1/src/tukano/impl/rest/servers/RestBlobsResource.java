package tukano.impl.rest.servers;

import static tukano.impl.rest.servers.RestBlobsServer.ignoreDropboxState;
import jakarta.inject.Singleton;
import tukano.impl.api.java.ExtendedBlobs;
import tukano.impl.api.rest.RestExtendedBlobs;
import tukano.impl.java.servers.JavaBlobs;
import tukano.impl.java.servers.JavaBlobsDropbox;

import java.net.URI;

@Singleton
public class RestBlobsResource extends RestResource implements RestExtendedBlobs {

    final ExtendedBlobs impl;

    public RestBlobsResource() {
        if (ignoreDropboxState == null)
            this.impl = new JavaBlobs();
        else {
            this.impl = new JavaBlobsDropbox();
        }
    }

    @Override
    public void upload(String blobId, String timestamp, String verifier, byte[] bytes) {
        super.resultOrThrow(impl.upload(blobId, timestamp, verifier, bytes));
    }

    @Override
    public byte[] download(String blobId, String timestamp, String verifier) {
        return super.resultOrThrow(impl.download(blobId, timestamp, verifier));
    }

    @Override
    public void delete(String blobId, String token) {
        super.resultOrThrow(impl.delete(blobId, token));
    }

    @Override
    public void deleteAllBlobs(String userId, String password) {
        super.resultOrThrow(impl.deleteAllBlobs(userId, password));
    }
}
