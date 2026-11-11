package io.github.vthattey.twinrail.core.matcher;

import io.github.vthattey.twinrail.core.codec.CodecException;
import io.github.vthattey.twinrail.core.codec.MessageCodec;
import io.github.vthattey.twinrail.core.codec.ParsedMessage;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** A message prepared for rule and scenario matching: its type plus a queryable XML tree. */
public final class XmlMessage {
  private final String type;
  private final Document document;

  private XmlMessage(String type, Document document) {
    this.type = type;
    this.document = document;
  }

  /** Builds a view from a parsed message by writing it with the codec's fixed settings. */
  public static XmlMessage of(ParsedMessage message, MessageCodec codec) {
    return parse(message.type(), codec.write(message.mx()));
  }

  /** Builds a view from XML text. DTDs and external entities are refused. */
  public static XmlMessage parse(String type, String xml) {
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setXIncludeAware(false);
      factory.setExpandEntityReferences(false);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      Document document =
          factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
      return new XmlMessage(type, document);
    } catch (Exception e) {
      throw new CodecException(
          "The message could not be prepared for rule checking ("
              + e.getClass().getSimpleName()
              + "). Check that it is well-formed XML without a DOCTYPE.",
          e);
    }
  }

  public String type() {
    return type;
  }

  /** Text of every node the path selects, trimmed, in document order. Empty if none. */
  public List<String> values(PathExpr path) {
    try {
      NodeList nodes =
          (NodeList)
              XPathFactory.newInstance()
                  .newXPath()
                  .evaluate(path.xpath(), document, XPathConstants.NODESET);
      List<String> values = new ArrayList<>(nodes.getLength());
      for (int i = 0; i < nodes.getLength(); i++) {
        Node node = nodes.item(i);
        values.add(node.getTextContent().strip());
      }
      return values;
    } catch (javax.xml.xpath.XPathExpressionException e) {
      throw new IllegalStateException("Path " + path + " could not be evaluated.", e);
    }
  }
}
