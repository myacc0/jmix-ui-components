package uz.kapitalbank.umida.security;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Map;

/**
 * Replaces {@code spring.ldap.embedded.port=0} with a free port before the context starts.
 * <p>
 * Spring Boot binds the embedded LDAP server to a random port itself, but the actual port becomes known only
 * once the server is up, too late for {@code jmix.ldap.urls}. Fixing the port up front lets both refer to
 * {@code spring.ldap.embedded.port}, and lets several contexts (e.g. the cached test contexts, or the tests
 * next to a running application) each have their own server.
 */
public class EmbeddedLdapPortEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PORT_PROPERTY = "spring.ldap.embedded.port";
    private static final String BASE_DN_PROPERTY = "spring.ldap.embedded.base-dn";

    private final Log log;

    public EmbeddedLdapPortEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(EmbeddedLdapPortEnvironmentPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.containsProperty(BASE_DN_PROPERTY)
                || environment.getProperty(PORT_PROPERTY, Integer.class, 0) != 0) {
            return;
        }
        int port = findFreePort();
        environment.getPropertySources().addFirst(
                new MapPropertySource("umidaEmbeddedLdapPort", Map.of(PORT_PROPERTY, port)));
        log.info("Embedded LDAP server port: " + port);
    }

    @Override
    public int getOrder() {
        // after the application properties are loaded
        return Ordered.LOWEST_PRECEDENCE;
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot find a free port for the embedded LDAP server", e);
        }
    }
}
