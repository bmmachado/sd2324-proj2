package tukano.impl.java.clients.input;

import tukano.impl.api.java.ExtendedBlobs;
import tukano.impl.java.clients.ClientFactory;
import tukano.impl.java.clients.Clients;
import utils.Hash;
import utils.Hex;

import java.io.IOException;
import java.net.URI;
import java.util.logging.Logger;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Arrays;

import static java.lang.String.format;

public class UploadBlob {
    private static Logger Log = Logger.getLogger(UploadBlob.class.getName());

    public static void main(String[] args) throws IOException {

        if (args.length != 1) {
            System.err.println("Use: UploadBlob blobId < file");
            System.err.println("  or");
            System.err.println("Use: cat file | UploadBlob blobId");
            return;
        }

        String blobURL = args[0];

        ByteBuffer buf = ByteBuffer.allocate(1000000);
        ReadableByteChannel channel = Channels.newChannel(System.in);
        while (channel.read(buf) >= 0)
            ;
        buf.flip();
        byte[] bytes = Arrays.copyOf(buf.array(), buf.limit());
        Log.info(() -> format("download : file, sha256 = %s\n", Hex.of(Hash.sha256(bytes))));
        ClientFactory<ExtendedBlobs> blobsClientFactory = Clients.BlobsClients;
        ExtendedBlobs blobsClient = blobsClientFactory.get();

        var result = blobsClient.upload(blobURL, bytes);

        if (result.isOK())
            Log.info("Upload blob");
        else
            Log.info("Upload blob failed with error: " + result.error());
    }

}
