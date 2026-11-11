package io.github.vthattey.twinrail.core.codec;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.MxWriteConfiguration;

/**
 * Reads and writes ISO 20022 XML. Output uses fixed serialisation settings so that the same message
 * always produces the same bytes (needed for golden files and deterministic replay).
 */
public final class MessageCodec {
  private static final String INDENT = "  ";

  /**
   * Parses XML into a message. The message type is detected from the document namespace.
   *
   * @throws CodecException if the XML is malformed, uses an unknown message type, or is empty
   */
  public ParsedMessage parse(String xml) {
    if (xml == null || xml.isBlank()) {
      throw new CodecException("The message is empty. Send an ISO 20022 XML document.");
    }
    AbstractMX mx;
    try {
      mx = AbstractMX.parse(xml);
    } catch (RuntimeException e) {
      throw new CodecException(
          "The message could not be read as ISO 20022 XML ("
              + e.getClass().getSimpleName()
              + "). Check that it is well-formed and uses a supported message namespace.",
          e);
    }
    if (mx == null) {
      throw new CodecException(
          "The message type was not recognised. Check the document namespace, for example "
              + "urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08.");
    }
    return new ParsedMessage(mx.getMxId().id(), mx);
  }

  /** Writes a message as XML with the codec's fixed settings. */
  public String write(AbstractMX mx) {
    MxWriteConfiguration config = new MxWriteConfiguration();
    config.includeXMLDeclaration = true;
    config.indent = INDENT;
    // Prowide uses the platform line separator. Pin LF and one trailing newline: same on every OS.
    return mx.message(config).replace("\r\n", "\n").stripTrailing() + "\n";
  }
}
