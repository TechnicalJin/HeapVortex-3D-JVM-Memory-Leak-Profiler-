package com.heapvortex.jvm;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Makes the backend trust the certificate of the monitored JVMs. The RMI socket factory that JMX uses
 * always takes the default SSL context of the JVM, so the trust store is installed as that default.
 * HeapVortex makes no other HTTPS calls, so nothing else is affected.
 */
@Component
public class JmxTlsConfig {

    private static final Logger log = LoggerFactory.getLogger(JmxTlsConfig.class);

    private final String trustStore;
    private final String password;

    public JmxTlsConfig(@Value("${heapvortex.jmx.ssl.trust-store:}") String trustStore,
                        @Value("${heapvortex.jmx.ssl.trust-store-password:changeit}") String password) {
        this.trustStore = trustStore;
        this.password = password;
    }

    @PostConstruct
    void install() {
        if (trustStore == null || trustStore.isBlank()) {
            log.info("JMX TLS: no trust store configured, the JVM default trust applies");
            return;
        }
        try {
            SSLContext.setDefault(TlsSupport.clientContext(Path.of(trustStore), password.toCharArray()));
            log.info("JMX TLS: trusting the certificates in {}", trustStore);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load the JMX trust store " + trustStore, e);
        }
    }
}