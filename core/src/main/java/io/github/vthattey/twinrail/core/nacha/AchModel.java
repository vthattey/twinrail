package io.github.vthattey.twinrail.core.nacha;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** The data types of the ACH lifecycle. */
public final class AchModel {
  private AchModel() {}

  /** ACCEPTED: received, settlement pending. SETTLED: booked. RETURNED: returned (final). */
  public enum State {
    ACCEPTED,
    SETTLED,
    RETURNED
  }

  /**
   * A return of an entry.
   *
   * @param timeframe the return time frame as the code table's source states it
   * @param addendaRecord the 94-character type 99 addenda record for this return. Its trace number
   *     field holds the original entry's trace number: Twinrail does not generate the return entry
   *     itself yet (ADR 0011)
   */
  public record Return(
      String code,
      String title,
      String timeframe,
      String source,
      String text,
      Instant at,
      String addendaRecord) {}

  /**
   * A notification of change.
   *
   * @param corrected the corrected values the scenario gave
   * @param addendaRecord the 94-character type 98 addenda record
   */
  public record Change(
      String code,
      String title,
      String source,
      Map<String, String> corrected,
      Instant at,
      String addendaRecord) {}

  /** One entry of a received file and what has happened to it. */
  public record Entry(
      String id,
      String traceNumber,
      int batchNumber,
      String companyName,
      String secCode,
      String transactionCode,
      String routingNumber,
      String accountNumber,
      BigDecimal amount,
      String individualName,
      State state,
      Instant receivedAt,
      Instant settleAt,
      Instant closedAt,
      Return returned,
      Change change) {}

  /**
   * The outcome of receiving a file. If {@code problems} is not empty the whole file was refused
   * and no entry was created.
   */
  public record Result(List<NachaCheck.Problem> problems, List<Entry> entries) {
    public Result {
      problems = List.copyOf(problems);
      entries = List.copyOf(entries);
    }

    public boolean rejected() {
      return !problems.isEmpty();
    }
  }

  /** A state change, with the virtual time it happened at. */
  public record Transition(String entryId, State from, State to, String kind, Instant at) {}
}
