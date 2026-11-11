package io.github.vthattey.twinrail.testcontainers;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Properties;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * The Twinrail payment-rail emulator in a container: the payment scheme and the other bank,
 * speaking ISO 20022 and ACH, on a virtual clock. It is ready when its health check answers.
 *
 * <pre>{@code
 * try (TwinrailContainer twinrail = new TwinrailContainer()) {
 *   twinrail.start();
 *   TwinrailClient client = twinrail.client();
 *   Payment payment = client.sendMessage(pacs008Xml);
 * }
 * }</pre>
 *
 * Twinrail complements scheme and network testing; it does not replace it.
 */
public class TwinrailContainer extends GenericContainer<TwinrailContainer> {
  /** The port the emulator listens on inside the container. */
  public static final int PORT = 8080;

  private static final String IMAGE_REPOSITORY = "ghcr.io/vthattey/twinrail";
  private static final String RULE_PACK_PATH = "/etc/twinrail/rule-pack.yaml";

  /** The image that goes with this version of the module. */
  public static String defaultImage() {
    Properties p = new Properties();
    try (InputStream in =
        TwinrailContainer.class.getResourceAsStream("/twinrail-testcontainers.properties")) {
      if (in != null) {
        p.load(in);
      }
    } catch (IOException e) {
      // fall back to latest
    }
    String version = p.getProperty("version", "");
    // A snapshot has no published image: use latest, or pass your own image to the constructor.
    String tag = version.isBlank() || version.endsWith("-SNAPSHOT") ? "latest" : version;
    return IMAGE_REPOSITORY + ":" + tag;
  }

  /** Uses the image that matches this version of the module. */
  public TwinrailContainer() {
    this(defaultImage());
  }

  /**
   * @param image for example {@code ghcr.io/vthattey/twinrail:0.1.0} or a locally built {@code
   *     twinrail}
   */
  public TwinrailContainer(String image) {
    this(DockerImageName.parse(image));
  }

  // Testcontainers modules configure the container in their constructor, as GenericContainer does.
  @SuppressWarnings("this-escape")
  public TwinrailContainer(DockerImageName image) {
    super(image);
    addExposedPort(PORT);
    waitingFor(
        Wait.forHttp("/_twinrail/health")
            .forPort(PORT)
            .forStatusCode(200)
            .withStartupTimeout(Duration.ofSeconds(60)));
  }

  /** The seed for generated ids. The same seed, start time and calls give the same results. */
  public TwinrailContainer withSeed(long seed) {
    return withEnv("TWINRAIL_SEED", Long.toString(seed));
  }

  /** Where the virtual clock starts. */
  public TwinrailContainer withStart(Instant start) {
    return withEnv("TWINRAIL_START", start.toString());
  }

  /** How long an accepted payment may stay unsettled before it times out (virtual time). */
  public TwinrailContainer withPaymentTimeout(Duration timeout) {
    return withEnv("TWINRAIL_TIMEOUT", timeout.toString());
  }

  /** Uses your own rule pack file instead of the bundled illustrative one. */
  public TwinrailContainer withRulePack(MountableFile rulePack) {
    return withCopyFileToContainer(rulePack, RULE_PACK_PATH)
        .withEnv("TWINRAIL_RULE_PACK", RULE_PACK_PATH);
  }

  /** Uses a rule pack given as text. */
  public TwinrailContainer withRulePack(String rulePackYaml) {
    return withCopyToContainer(Transferable.of(rulePackYaml), RULE_PACK_PATH)
        .withEnv("TWINRAIL_RULE_PACK", RULE_PACK_PATH);
  }

  /** Where the emulator is reachable from the test, for example {@code http://localhost:49153}. */
  public String baseUrl() {
    return "http://" + getHost() + ":" + getMappedPort(PORT);
  }

  /** A client for the control API of this container. The container must be started. */
  public TwinrailClient client() {
    return new TwinrailClient(URI.create(baseUrl()));
  }
}
