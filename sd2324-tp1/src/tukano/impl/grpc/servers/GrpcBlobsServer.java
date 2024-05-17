package tukano.impl.grpc.servers;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.logging.Logger;

import tukano.api.java.Blobs;
import utils.Args;

public class GrpcBlobsServer extends AbstractGrpcServer {
public static final int PORT = 15678;
	
	private static Logger Log = Logger.getLogger(GrpcBlobsServer.class.getName());

	public GrpcBlobsServer(int port) throws UnrecoverableKeyException, CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException {
		super( Log, Blobs.NAME, port, new GrpcBlobsServerStub());
	}
	
	public static void main(String[] args) throws UnrecoverableKeyException, CertificateException, KeyStoreException, NoSuchAlgorithmException {
		try {
			Args.use(args);
			new GrpcBlobsServer(Args.valueOf("-port", PORT)).start();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}	
}
