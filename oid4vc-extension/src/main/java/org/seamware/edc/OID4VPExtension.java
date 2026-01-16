package org.seamware.edc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.nimbusds.jose.JWEAlgorithm;
import io.github.wistefan.dcql.DCQLEvaluator;
import io.github.wistefan.dcql.DcSdJwtCredentialEvaluator;
import io.github.wistefan.dcql.JwtCredentialEvaluator;
import io.github.wistefan.dcql.VcSdJwtCredentialEvaluator;
import io.github.wistefan.dcql.model.CredentialFormat;
import io.github.wistefan.dcql.model.TrustedAuthorityType;
import io.github.wistefan.oid4vp.HolderSigningService;
import io.github.wistefan.oid4vp.OID4VPClient;
import io.github.wistefan.oid4vp.SigningService;
import io.github.wistefan.oid4vp.client.X509SanDnsClientResolver;
import io.github.wistefan.oid4vp.config.HolderConfiguration;
import io.github.wistefan.oid4vp.credentials.CredentialsRepository;
import io.github.wistefan.oid4vp.credentials.FileSystemCredentialsRepository;
import io.github.wistefan.oid4vp.mapping.CredentialFormatDeserializer;
import io.github.wistefan.oid4vp.mapping.TrustedAuthorityTypeDeserializer;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.edc.protocol.spi.DefaultParticipantIdExtractionFunction;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.runtime.metamodel.annotation.Requires;
import org.eclipse.edc.spi.iam.AudienceResolver;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.seamware.edc.identity.CounterPartyAddressAudienceResolver;
import org.seamware.edc.identity.OID4VPIdentityService;
import org.seamware.edc.identity.OID4VPParticipantIdExtractionFunction;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.*;
import java.security.cert.Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OID4VPExtension implements ServiceExtension {

    private static final String OID4VC_NAME = "OID4VPExtension";
    @Inject
    private ObjectMapper objectMapper;
    @Inject
    private Monitor monitor;

    private HttpClient httpClient;
    private OID4VPClient oid4VPClient;
    private IdentityService identityService;
    private DefaultParticipantIdExtractionFunction defaultParticipantIdExtractionFunction;
    private AudienceResolver audienceResolver;

    private OID4VPConfig oid4VPConfig;

    @Override
    public String name() {
        return OID4VC_NAME;
    }

    public boolean isEnabled() {
        return oid4VPConfig.getEnabled();
    }

    @Provider
    public OID4VPConfig oid4VPConfig(ServiceExtensionContext context) {
        if (oid4VPConfig == null) {
            oid4VPConfig = OID4VPConfig.fromConfig(context.getConfig());
        }
        return oid4VPConfig;
    }

    @Provider
    public HttpClient httpClient(ServiceExtensionContext context) {

        if (httpClient == null) {
            OID4VPConfig config = oid4VPConfig(context);
            HttpClient.Builder httpClientBuilder = HttpClient.newBuilder();
            // required for the authorization flow to work
            httpClientBuilder.followRedirects(HttpClient.Redirect.NORMAL);
            if (config.getProxy().enabled()) {
                ProxySelector proxySelector = ProxySelector.of(new InetSocketAddress(config.getProxy().host(), config.getProxy().port()));
                httpClientBuilder.proxy(proxySelector);
            }
            if (config.getTrustAll()) {
                monitor.warning("The OID4VP Client is configured to trust all SSL Certificates. DO NOT USE THIS IN PRODUCTION.");
                httpClientBuilder.sslContext(trustAll());
            }
            httpClient = httpClientBuilder.build();
        }
        return httpClient;
    }


    @Provider
    public OID4VPClient oid4VPClient(ServiceExtensionContext context) {
        OID4VPConfig config = oid4VPConfig(context);
        if (oid4VPClient == null && config.getEnabled()) {

            monitor.info("Enabling OID4VP Client.");

            Security.addProvider(new BouncyCastleProvider());

            ObjectMapper authObjectMapper = objectMapper.copy();
            authObjectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            SimpleModule deserializerModule = new SimpleModule();
            deserializerModule.addDeserializer(CredentialFormat.class, new CredentialFormatDeserializer());
            deserializerModule.addDeserializer(TrustedAuthorityType.class, new TrustedAuthorityTypeDeserializer());
            authObjectMapper.registerModule(deserializerModule);

            CredentialsRepository credentialsRepository = new FileSystemCredentialsRepository(config.getCredentialsFolder(), objectMapper);

            //initialize the holder
            PrivateKey privateKey = loadPrivateKey(config.getHolder().keyConfig().type(), config.getHolder().keyConfig().path());
            HolderConfiguration holderConfiguration = new HolderConfiguration(
                    URI.create(config.getHolder().id()),
                    config.getHolder().id(),
                    JWEAlgorithm.parse(config.getHolder().signatureAlgorithm()),
                    privateKey);
            SigningService signingService = new HolderSigningService(holderConfiguration, objectMapper);

            // allows configuration of additional trust anchors
            Set<TrustAnchor> trustAnchors = Optional.ofNullable(config.getTrustAnchorsFolder())
                    .map(OID4VPExtension::loadCertificatesFromFolder)
                    .orElse(List.of())
                    .stream()
                    .map(c -> new TrustAnchor(c, null))
                    .collect(Collectors.toSet());

            X509SanDnsClientResolver clientResolver = new X509SanDnsClientResolver();
            if (!trustAnchors.isEmpty()) {
                //if trust anchors are explicitly configured, use them.
                clientResolver = new X509SanDnsClientResolver(trustAnchors, false);
            }

            DCQLEvaluator dcqlEvaluator = new DCQLEvaluator(List.of(
                    new JwtCredentialEvaluator(),
                    new DcSdJwtCredentialEvaluator(),
                    new VcSdJwtCredentialEvaluator()));


            oid4VPClient = new OID4VPClient(
                    httpClient(context),
                    holderConfiguration,
                    authObjectMapper,
                    List.of(clientResolver),
                    dcqlEvaluator,
                    credentialsRepository,
                    signingService);
        }
        return oid4VPClient;
    }

    @Provider
    public IdentityService identityService(ServiceExtensionContext context) {
        OID4VPConfig config = oid4VPConfig(context);
        if (!config.getEnabled()) {
            return null;
        }
        if (identityService == null) {
            identityService = new OID4VPIdentityService(monitor, objectMapper, oid4VPClient(context), config.getClientId(), config.getScope());
        }
        return identityService;
    }

    @Provider
    public DefaultParticipantIdExtractionFunction defaultParticipantIdExtractionFunction(ServiceExtensionContext context) {
        if (!oid4VPConfig(context).getEnabled()) {
            return null;
        }
        if (defaultParticipantIdExtractionFunction == null) {
            defaultParticipantIdExtractionFunction = new OID4VPParticipantIdExtractionFunction(monitor, oid4VPConfig(context).getOrganizationClaim());
        }
        return defaultParticipantIdExtractionFunction;
    }

    @Provider
    public AudienceResolver audienceResolver(ServiceExtensionContext context) {
        if (!oid4VPConfig(context).getEnabled()) {
            return null;
        }
        if (audienceResolver == null) {
            audienceResolver = new CounterPartyAddressAudienceResolver();
        }
        return audienceResolver;
    }

    private static PrivateKey loadPrivateKey(String keyType, String filename) {

        try (InputStream is = openInputStream(filename)) {
            if (is == null) {
                throw new IllegalArgumentException("Private key not found: " + filename);
            }

            // Read PEM file content
            String pem = new String(is.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("-----BEGIN (.*)-----", "")
                    .replaceAll("-----END (.*)-----", "")
                    .replaceAll("\\s", "");

            // Base64 decode
            byte[] decoded = Base64.getDecoder().decode(pem);

            // Build key spec
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance(keyType); // or "EC"
            return keyFactory.generatePrivate(keySpec);
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalArgumentException(String.format("Was not able to load the private key with type %s from %s", keyType, filename), e);
        }
    }


    private static InputStream openInputStream(String filepath) throws IOException {
        Path path = Paths.get(filepath);
        if (Files.isDirectory(path)) {
            return null;
        }
        if (Files.exists(path)) {
            return Files.newInputStream(path);
        }
        return null;
    }

    private static List<X509Certificate> loadCertificates(Path certificateFile) {
        try (InputStream is = Files.newInputStream(certificateFile)) {

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Collection<? extends Certificate> certs = cf.generateCertificates(is);

            List<X509Certificate> result = new ArrayList<>(certs.size());
            for (Certificate cert : certs) {
                result.add((X509Certificate) cert);
            }
            return result;

        } catch (IOException | CertificateException e) {
            throw new IllegalArgumentException(
                    "Unable to load certificates from file: " + certificateFile, e
            );
        }
    }

    private static List<X509Certificate> loadCertificatesFromFolder(String folderPath) {
        Path dir = Paths.get(folderPath);

        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Not a directory: " + folderPath);
        }

        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(Files::isRegularFile)
                    .flatMap(path -> loadCertificates(path).stream())
                    .toList();
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    "Failed to read certificate directory: " + folderPath, e
            );
        }
    }

    // provides an SSLContext trusting all certificates. DO NOT use this in production environments
    private SSLContext trustAll() {
        try {

            TrustManager trm = new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(X509Certificate[] certs, String authType) {

                }

                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                }
            };

            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, new TrustManager[]{trm}, null);
            return sc;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
