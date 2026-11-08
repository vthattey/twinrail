package io.github.vthattey.twinrail.core.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prowidesoftware.swift.model.mx.MxPacs00800108;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MessageCodecTest {
  private final MessageCodec codec = new MessageCodec();

  static String resource(String name) throws IOException {
    try (InputStream in = MessageCodecTest.class.getResourceAsStream("/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
  }

  @Test
  void parsesPacs008AndDetectsTheType() throws IOException {
    ParsedMessage parsed = codec.parse(resource("codec/pacs008-input.xml"));
    assertEquals("pacs.008.001.08", parsed.type());
    MxPacs00800108 mx = (MxPacs00800108) parsed.mx();
    assertEquals("TWIN-000001", mx.getFIToFICstmrCdtTrf().getGrpHdr().getMsgId());
  }

  @Test
  void writesTheGoldenOutput() throws IOException {
    ParsedMessage parsed = codec.parse(resource("codec/pacs008-input.xml"));
    assertEquals(resource("codec/pacs008-golden.xml"), codec.write(parsed.mx()));
  }

  @Test
  void writingIsStableAcrossParseWriteCycles() throws IOException {
    String first = codec.write(codec.parse(resource("codec/pacs008-input.xml")).mx());
    String second = codec.write(codec.parse(first).mx());
    assertEquals(first, second);
  }

  @Test
  void rejectsEmptyInput() {
    CodecException e = assertThrows(CodecException.class, () -> codec.parse("  "));
    assertEquals("The message is empty. Send an ISO 20022 XML document.", e.getMessage());
  }

  @Test
  void rejectsMalformedXmlWithoutLeakingInput() {
    CodecException e = assertThrows(CodecException.class, () -> codec.parse("<Document><oops>"));
    assertFalse(e.getMessage().contains("oops"));
  }

  @Test
  void rejectsUnknownNamespace() {
    CodecException e =
        assertThrows(
            CodecException.class,
            () -> codec.parse("<Document xmlns=\"urn:example:not-iso\"><A/></Document>"));
    assertEquals(true, e.getMessage().contains("check") || e.getMessage().contains("Check"));
  }

  @Test
  void doesNotResolveExternalEntities() throws IOException {
    java.nio.file.Path secret = java.nio.file.Files.createTempFile("twinrail-xxe", ".txt");
    java.nio.file.Files.writeString(secret, "TOP-SECRET-CONTENT");
    try {
      String xml =
          "<?xml version=\"1.0\"?>\n<!DOCTYPE d [<!ENTITY x SYSTEM \""
              + secret.toUri()
              + "\">]>\n"
              + resource("codec/pacs008-input.xml").replace("TWIN-000001", "&x;");
      String result;
      try {
        result = codec.write(codec.parse(xml).mx());
      } catch (CodecException e) {
        result = String.valueOf(e.getMessage());
      }
      assertFalse(result.contains("TOP-SECRET-CONTENT"));
    } finally {
      java.nio.file.Files.deleteIfExists(secret);
    }
  }
}
