package io.github.vthattey.twinrail.core;

import io.github.vthattey.twinrail.core.codec.MessageCodec;
import io.github.vthattey.twinrail.core.matcher.XmlMessage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Test helpers for building messages from the sample pacs.008 and loading classpath files. */
public final class TestMessages {
  private static final MessageCodec CODEC = new MessageCodec();

  private TestMessages() {}

  public static String resource(String name) {
    try (InputStream in = TestMessages.class.getResourceAsStream(name)) {
      if (in == null) {
        throw new IllegalStateException("Missing test resource " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /** The sample pacs.008 as a rule-checkable message. */
  public static XmlMessage pacs008() {
    return pacs008(resource("/codec/pacs008-input.xml"));
  }

  /** The sample with one text replaced, for example {@code with("<NbOfTxs>1<", "<NbOfTxs>2<")}. */
  public static XmlMessage pacs008With(String from, String to) {
    String xml = resource("/codec/pacs008-input.xml");
    if (!xml.contains(from)) {
      throw new IllegalArgumentException("Sample does not contain " + from);
    }
    return pacs008(xml.replace(from, to));
  }

  public static XmlMessage pacs008(String xml) {
    return XmlMessage.of(CODEC.parse(xml), CODEC);
  }
}
