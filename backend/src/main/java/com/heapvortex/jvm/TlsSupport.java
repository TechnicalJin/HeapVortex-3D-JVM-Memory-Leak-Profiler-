package com.heapvortex.jvm;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/** Builds SSL contexts from PKCS12 files. */
public final class TlsSupport {

    private TlsSupport() {
    }

    /** A context that trusts exactly the certificates in the given trust store. */
    public static SSLContext clientContext(Path trustStore, char[] password) throws IOException {
        try {
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(load(trustStore, password));
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, factory.getTrustManagers(), null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IOException("Cannot build a TLS client context from " + trustStore, e);
        }
    }

    /** A context that presents the private key of the given key store. Used by servers and tests. */
    public static SSLContext serverContext(Path keyStore, char[] password) throws IOException {
        try {
            KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            factory.init(load(keyStore, password), password);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(factory.getKeyManagers(), null, null);
            return context;
        } catch (GeneralSecurityException e) {
            throw new IOException("Cannot build a TLS server context from " + keyStore, e);
        }
    }

    private static KeyStore load(Path file, char[] password) throws IOException, GeneralSecurityException {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(file)) {
            store.load(in, password);
        }
        return store;
    }
}