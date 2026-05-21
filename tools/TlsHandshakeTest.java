import java.io.*;
import java.net.Socket;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.*;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import org.spongycastle.tls.*;
import org.spongycastle.tls.crypto.impl.bc.BcTlsCrypto;

/**
 * Standalone TLS handshake test that replicates the exact BouncyCastle
 * TLS client configuration from BouncyCastleHttpClient.java.
 *
 * Usage: java -cp spongycastle-core-1.58.0.0.jar:spongycastle-prov-1.58.0.0.jar:spongycastle-bctls-jdk15on-1.58.0.0.jar:. TlsHandshakeTest <host> <port> [ca_bundle.pem]
 */
public class TlsHandshakeTest {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: TlsHandshakeTest <host> <port> [ca_bundle.pem]");
            System.exit(1);
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String caBundlePath = args.length >= 3 ? args[2] : null;

        System.out.println("=== TLS Handshake Test ===");
        System.out.println("Target: " + host + ":" + port);
        System.out.println();

        // Load CA bundle
        X509TrustManager tm = null;
        if (caBundlePath != null) {
            tm = loadTrustManager(caBundlePath);
            System.out.println("CA bundle loaded from: " + caBundlePath);
        } else {
            System.out.println("WARNING: No CA bundle, will trust all certs (self-signed mode)");
        }
        System.out.println();

        // TCP connect
        long t0 = System.currentTimeMillis();
        System.out.println("Connecting TCP...");
        Socket socket = new Socket(host, port);
        socket.setSoTimeout(20000);
        socket.setTcpNoDelay(true);
        long t1 = System.currentTimeMillis();
        System.out.println("TCP connected in " + (t1 - t0) + "ms");
        System.out.println();

        try {
            // Create TLS protocol
            TlsClientProtocol tlsProtocol = new TlsClientProtocol(
                    socket.getInputStream(), socket.getOutputStream());

            // Create TLS client with same config as app
            DefaultTlsClient tlsClient = createTlsClient(host, tm);

            // Handshake
            long t2 = System.currentTimeMillis();
            System.out.println("Starting TLS handshake...");
            tlsProtocol.connect(tlsClient);
            long t3 = System.currentTimeMillis();
            System.out.println("TLS handshake SUCCESS in " + (t3 - t2) + "ms");
            System.out.println("Total time: " + (t3 - t0) + "ms");

        } catch (Exception e) {
            long t3 = System.currentTimeMillis();
            System.out.println("TLS handshake FAILED after " + (t3 - t0) + "ms");
            System.out.println("Error: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace(System.out);
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    private static X509TrustManager loadTrustManager(String path) throws Exception {
        InputStream is = new FileInputStream(path);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate caCert = (X509Certificate) cf.generateCertificate(is);
        is.close();

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setCertificateEntry("ca", caCert);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        return (X509TrustManager) tmf.getTrustManagers()[0];
    }

    private static DefaultTlsClient createTlsClient(final String hostname,
                                                     final X509TrustManager tm) {
        SecureRandom secureRandom = new SecureRandom();
        final BcTlsCrypto crypto = new BcTlsCrypto(secureRandom);

        return new DefaultTlsClient(crypto) {
            public ProtocolVersion getClientVersion() {
                return ProtocolVersion.TLSv12;
            }

            public ProtocolVersion getMinimumVersion() {
                return ProtocolVersion.TLSv12;
            }

            public ProtocolVersion getClientHelloRecordLayerVersion() {
                return ProtocolVersion.TLSv10;
            }

            public int[] getCipherSuites() {
                // Test all 3 families to see which work
                String mode = System.getProperty("test.ciphers", "all");
                if ("gcm".equals(mode)) {
                    return new int[] {
                            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                    };
                } else if ("chacha".equals(mode)) {
                    return new int[] {
                            CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
                    };
                } else if ("cbc".equals(mode)) {
                    return new int[] {
                            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256,
                            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384,
                    };
                } else {
                    // all: try GCM first, then ChaCha, then CBC
                    return new int[] {
                            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                            CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
                            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256,
                            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384,
                    };
                }
            }

            public void notifySelectedCipherSuite(int selectedCipherSuite) {
                super.notifySelectedCipherSuite(selectedCipherSuite);
                System.out.println("Negotiated cipher suite: 0x" +
                        Integer.toHexString(selectedCipherSuite));
            }

            public TlsKeyExchange getKeyExchange() throws java.io.IOException {
                TlsKeyExchange kex = super.getKeyExchange();
                System.out.println("Key exchange: " + kex.getClass().getSimpleName());
                return kex;
            }

            protected CertificateStatusRequest getCertificateStatusRequest() {
                return null; // Disable OCSP stapling
            }

            public Hashtable getClientExtensions() throws java.io.IOException {
                Hashtable extensions = super.getClientExtensions();
                extensions = TlsExtensionsUtils.ensureExtensionsInitialised(extensions);

                // Keep encrypt_then_mac, ec_point_formats, supported_groups,
                // and signature_algorithms for Go crypto/tls compatibility

                // Remove extended_master_secret for ngrok compatibility
                extensions.remove(TlsExtensionsUtils.EXT_extended_master_secret);

                // Limit to P-256 (secp256r1=23) for faster ECDHE on slow hardware
                extensions.remove(TlsExtensionsUtils.EXT_supported_groups);
                Vector curveList = new Vector();
                curveList.addElement(new Integer(23));
                TlsExtensionsUtils.addSupportedGroupsExtension(extensions, curveList);

                // Add SNI
                if (hostname != null && hostname.length() > 0) {
                    Vector serverNames = new Vector();
                    serverNames.addElement(new ServerName(NameType.host_name, hostname));
                    TlsExtensionsUtils.addServerNameExtension(
                            extensions, new ServerNameList(serverNames));
                }

                // Log extensions
                StringBuilder sb = new StringBuilder("Extensions: [");
                java.util.Enumeration keys = extensions.keys();
                boolean first = true;
                while (keys.hasMoreElements()) {
                    if (!first) sb.append(", ");
                    first = false;
                    sb.append(keys.nextElement());
                }
                sb.append("]");
                System.out.println(sb.toString());

                return extensions;
            }

            public TlsAuthentication getAuthentication() {
                return new TlsAuthentication() {
                    public void notifyServerCertificate(TlsServerCertificate serverCertificate)
                            throws java.io.IOException {
                        if (tm == null) {
                            System.out.println("Skipping cert validation (self-signed mode)");
                            return;
                        }
                        try {
                            org.spongycastle.tls.crypto.TlsCertificate[] chain =
                                    serverCertificate.getCertificate().getCertificateList();
                            if (chain == null || chain.length == 0) {
                                throw new TlsFatalAlert(AlertDescription.bad_certificate);
                            }
                            X509Certificate[] x509Chain = new X509Certificate[chain.length];
                            CertificateFactory cf = CertificateFactory.getInstance("X.509");
                            for (int i = 0; i < chain.length; i++) {
                                x509Chain[i] = (X509Certificate) cf.generateCertificate(
                                        new ByteArrayInputStream(chain[i].getEncoded()));
                            }
                            tm.checkServerTrusted(x509Chain, "RSA");
                            System.out.println("Server certificate validated");

                            // Print cert Subject
                            X500Principal subject = x509Chain[0].getSubjectX500Principal();
                            System.out.println("  Subject: " + subject.getName());

                            // Print SANs
                            try {
                                Collection<List<?>> sanList = x509Chain[0].getSubjectAlternativeNames();
                                if (sanList != null) {
                                    for (List<?> san : sanList) {
                                        System.out.println("  SAN: type=" + san.get(0) + " value=" + san.get(1));
                                    }
                                }
                            } catch (CertificateParsingException ignored) {}
                        } catch (TlsFatalAlert e) {
                            throw e;
                        } catch (Throwable t) {
                            System.out.println("Certificate validation FAILED: " + t.getMessage());
                            throw new TlsFatalAlert(AlertDescription.bad_certificate);
                        }
                    }

                    public TlsCredentials getClientCredentials(CertificateRequest request) {
                        return null;
                    }
                };
            }
        };
    }
}
