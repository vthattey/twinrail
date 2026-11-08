package io.github.vthattey.twinrail.core.codec;

import com.prowidesoftware.swift.model.mx.AbstractMX;

/**
 * A parsed ISO 20022 message.
 *
 * @param type the message identifier, for example {@code pacs.008.001.08}
 * @param mx the parsed model
 */
public record ParsedMessage(String type, AbstractMX mx) {}
