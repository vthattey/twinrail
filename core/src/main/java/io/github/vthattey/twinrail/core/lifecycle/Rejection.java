package io.github.vthattey.twinrail.core.lifecycle;

/**
 * Why a payment was rejected. A rejection always names the rule and the reason code.
 *
 * @param ruleId a rule-pack rule id, {@code scenario:<id>}, or a {@code twinrail.*} built-in check
 * @param reason the ISO 20022 external reason code, for example {@code AM04}
 * @param text what went wrong, in words
 * @param source the specification or scenario the rule comes from
 */
public record Rejection(String ruleId, String reason, String text, String source) {}
