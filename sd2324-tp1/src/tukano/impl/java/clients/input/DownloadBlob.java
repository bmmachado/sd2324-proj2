package tukano.impl.java.clients.input;

import tukano.impl.api.java.ExtendedBlobs;
import tukano.impl.java.clients.ClientFactory;
import tukano.impl.java.clients.Clients;
import utils.Hash;
import utils.Hex;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.logging.Logger;

import static java.lang.String.format;

public class DownloadBlob {
    private static Logger Log = Logger.getLogger(DownloadBlob.class.getName());

    public static void main(String[] args) throws IOException {

        if (args.length != 1) {
            System.err.println("Use: DownloadBlob blobId > file");
            return;
        }

        String blobId = args[0];

        ClientFactory<ExtendedBlobs> blobsClientFactory = Clients.BlobsClients;
        ExtendedBlobs blobsClient = blobsClientFactory.get();

        var result = blobsClient.download(blobId);
        if (result.isOK())
            Log.info(() -> format("download : file, sha256 = %s\n", Hex.of(Hash.sha256(result.value()))));

       /* byte[] bytes;

        if (result.isOK()) {
            Log.info("Saving blob " + blobId + " to disk");
            bytes = result.value();
            ByteArrayInputStream blob = new ByteArrayInputStream(bytes);

            for (int i = 0; i < bytes.length; i++) {
                // Reads the bytes
                int data = blob.read();
                System.out.print(data);
            }
            blob.close();
        } */ else {
            Log.info("Failed to download blob " + blobId + " with error: " + result.error());
        }
    }

}