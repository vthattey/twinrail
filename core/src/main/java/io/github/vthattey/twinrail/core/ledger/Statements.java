package io.github.vthattey.twinrail.core.ledger;

import io.github.vthattey.twinrail.core.codec.MessageCodec;
import io.github.vthattey.twinrail.core.ids.SeededIds;
import java.math.BigDecimal;
import java.time.InstantSource;
import java.util.List;

/**
 * Writes {@code camt.053.001.08} account statements and {@code camt.054.001.08} debit/credit
 * notifications from the ledger. The output is passed through the codec, so it is always parseable
 * and has fixed formatting.
 */
public final class Statements {
  private static final String NS_053 = "urn:iso:std:iso:20022:tech:xsd:camt.053.001.08";
  private static final String NS_054 = "urn:iso:std:iso:20022:tech:xsd:camt.054.001.08";

  private final Ledger ledger;
  private final InstantSource clock;
  private final SeededIds ids;
  private final MessageCodec codec;

  public Statements(Ledger ledger, InstantSource clock, SeededIds ids, MessageCodec codec) {
    this.ledger = ledger;
    this.clock = clock;
    this.ids = ids;
    this.codec = codec;
  }

  /** A statement of every booking on the account since it was opened. */
  public String camt053(String account) {
    BigDecimal opening = ledger.openingBalance(account);
    BigDecimal closing = ledger.balance(account);
    String currency = ledger.currency(account);
    List<Entry> entries = ledger.entries(account);
    String now = clock.instant().toString();
    var x = new Xml();
    x.open("Document xmlns=\"" + NS_053 + "\"").open("BkToCstmrStmt");
    x.open("GrpHdr").text("MsgId", ids.sequence("STMT")).text("CreDtTm", now).close("GrpHdr");
    x.open("Stmt").text("Id", ids.sequence("STMT")).text("CreDtTm", now);
    account(x, account, currency);
    balance(x, "OPBD", opening, currency, now);
    balance(x, "CLBD", closing, currency, now);
    entries.forEach(e -> entry(x, e));
    x.close("Stmt").close("BkToCstmrStmt").close("Document");
    return codec.write(codec.parse(x.toString()).mx());
  }

  /** A notification for one booking. */
  public String camt054(Entry entry) {
    String now = clock.instant().toString();
    var x = new Xml();
    x.open("Document xmlns=\"" + NS_054 + "\"").open("BkToCstmrDbtCdtNtfctn");
    x.open("GrpHdr").text("MsgId", ids.sequence("NTFN")).text("CreDtTm", now).close("GrpHdr");
    x.open("Ntfctn").text("Id", ids.sequence("NTFN")).text("CreDtTm", now);
    account(x, entry.account(), entry.currency());
    entry(x, entry);
    x.close("Ntfctn").close("BkToCstmrDbtCdtNtfctn").close("Document");
    return codec.write(codec.parse(x.toString()).mx());
  }

  private static void account(Xml x, String account, String currency) {
    x.open("Acct")
        .open("Id")
        .open("Othr")
        .text("Id", account)
        .close("Othr")
        .close("Id")
        .text("Ccy", currency)
        .close("Acct");
  }

  private static void balance(Xml x, String code, BigDecimal amount, String currency, String at) {
    x.open("Bal")
        .open("Tp")
        .open("CdOrPrtry")
        .text("Cd", code)
        .close("CdOrPrtry")
        .close("Tp")
        .text("Amt Ccy=\"" + currency + "\"", amount.abs().toPlainString())
        .text("CdtDbtInd", amount.signum() < 0 ? Side.DEBIT.code() : Side.CREDIT.code())
        .open("Dt")
        .text("DtTm", at)
        .close("Dt")
        .close("Bal");
  }

  private static void entry(Xml x, Entry e) {
    boolean debit = e.side() == Side.DEBIT;
    x.open("Ntry")
        .text("Amt Ccy=\"" + e.currency() + "\"", e.amount().toPlainString())
        .text("CdtDbtInd", e.side().code())
        .open("Sts")
        .text("Cd", "BOOK")
        .close("Sts")
        .open("BookgDt")
        .text("DtTm", e.bookedAt().toString())
        .close("BookgDt")
        .open("ValDt")
        .text("DtTm", e.bookedAt().toString())
        .close("ValDt")
        .text("AcctSvcrRef", e.paymentId())
        .open("BkTxCd")
        .open("Domn")
        .text("Cd", "PMNT")
        .open("Fmly")
        .text("Cd", debit ? "ICDT" : "RCDT")
        .text("SubFmlyCd", "OTHR")
        .close("Fmly")
        .close("Domn")
        .close("BkTxCd");
    if (e.endToEndId() != null) {
      x.open("NtryDtls")
          .open("TxDtls")
          .open("Refs")
          .text("EndToEndId", e.endToEndId())
          .close("Refs")
          .close("TxDtls")
          .close("NtryDtls");
    }
    x.close("Ntry");
  }

  /** Minimal escaping XML builder. A tag may carry attributes after its first space. */
  private static final class Xml {
    private final StringBuilder out = new StringBuilder();

    Xml open(String tag) {
      out.append('<').append(tag).append('>');
      return this;
    }

    Xml close(String tag) {
      out.append("</").append(tag).append('>');
      return this;
    }

    Xml text(String tag, String value) {
      String name = tag.contains(" ") ? tag.substring(0, tag.indexOf(' ')) : tag;
      out.append('<')
          .append(tag)
          .append('>')
          .append(escape(value))
          .append("</")
          .append(name)
          .append('>');
      return this;
    }

    private static String escape(String s) {
      return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public String toString() {
      return out.toString();
    }
  }
}
