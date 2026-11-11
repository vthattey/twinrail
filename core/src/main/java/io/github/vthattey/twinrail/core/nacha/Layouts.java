package io.github.vthattey.twinrail.core.nacha;

/**
 * The record layouts, as data. Source: docs/research.md section 7 (a vendor article for types 1, 5,
 * 6, 7/05, 8 and 9; the open-source moov-io/ach addenda files for 98 and 99). Nacha's own documents
 * have not been read, so treat positions as a secondary-source reading.
 */
public final class Layouts {
  private Layouts() {}

  public static final Layout FILE_HEADER =
      Layout.named("FileHeader")
          .text("priorityCode", 2, 3)
          .text("immediateDestination", 4, 13)
          .text("immediateOrigin", 14, 23)
          .text("fileCreationDate", 24, 29)
          .text("fileCreationTime", 30, 33)
          .text("fileIdModifier", 34, 34)
          .text("recordSize", 35, 37)
          .text("blockingFactor", 38, 39)
          .text("formatCode", 40, 40)
          .text("destinationName", 41, 63)
          .text("originName", 64, 86)
          .text("referenceCode", 87, 94)
          .build();

  public static final Layout BATCH_HEADER =
      Layout.named("BatchHeader")
          .text("serviceClassCode", 2, 4)
          .text("companyName", 5, 20)
          .text("discretionaryData", 21, 40)
          .text("companyId", 41, 50)
          .text("secCode", 51, 53)
          .text("entryDescription", 54, 63)
          .text("descriptiveDate", 64, 69)
          .text("effectiveEntryDate", 70, 75)
          .text("settlementDate", 76, 78)
          .text("originatorStatusCode", 79, 79)
          .text("odfiId", 80, 87)
          .number("batchNumber", 88, 94)
          .build();

  public static final Layout ENTRY_DETAIL =
      Layout.named("EntryDetail")
          .text("transactionCode", 2, 3)
          .text("rdfiId", 4, 11)
          .text("checkDigit", 12, 12)
          .text("accountNumber", 13, 29)
          .number("amount", 30, 39)
          .text("individualId", 40, 54)
          .text("individualName", 55, 76)
          .text("discretionaryData", 77, 78)
          .text("addendaIndicator", 79, 79)
          .text("traceNumber", 80, 94)
          .build();

  /** Addenda type code 05: payment-related information. */
  public static final Layout ADDENDA_05 =
      Layout.named("Addenda05")
          .text("typeCode", 2, 3)
          .text("paymentInfo", 4, 83)
          .number("sequenceNumber", 84, 87)
          .number("entrySequenceNumber", 88, 94)
          .build();

  /** Addenda type code 98: notification of change. */
  public static final Layout ADDENDA_98 =
      Layout.named("Addenda98")
          .text("typeCode", 2, 3)
          .text("changeCode", 4, 6)
          .text("originalTrace", 7, 21)
          .text("reserved1", 22, 27)
          .text("originalRdfiId", 28, 35)
          .text("correctedData", 36, 64)
          .text("reserved2", 65, 79)
          .text("traceNumber", 80, 94)
          .build();

  /** Addenda type code 99: return. */
  public static final Layout ADDENDA_99 =
      Layout.named("Addenda99")
          .text("typeCode", 2, 3)
          .text("returnCode", 4, 6)
          .text("originalTrace", 7, 21)
          .text("dateOfDeath", 22, 27)
          .text("originalRdfiId", 28, 35)
          .text("addendaInfo", 36, 79)
          .text("traceNumber", 80, 94)
          .build();

  public static final Layout BATCH_CONTROL =
      Layout.named("BatchControl")
          .text("serviceClassCode", 2, 4)
          .number("entryAddendaCount", 5, 10)
          .number("entryHash", 11, 20)
          .number("totalDebit", 21, 32)
          .number("totalCredit", 33, 44)
          .text("companyId", 45, 54)
          .text("reserved", 55, 79)
          .text("odfiId", 80, 87)
          .number("batchNumber", 88, 94)
          .build();

  public static final Layout FILE_CONTROL =
      Layout.named("FileControl")
          .number("batchCount", 2, 7)
          .number("blockCount", 8, 13)
          .number("entryAddendaCount", 14, 21)
          .number("entryHash", 22, 31)
          .number("totalDebit", 32, 43)
          .number("totalCredit", 44, 55)
          .text("reserved", 56, 94)
          .build();

  /** The layout for an addenda record, chosen by its type code (positions 2-3). */
  static Layout addendaFor(String typeCode) {
    return switch (typeCode) {
      case "05" -> ADDENDA_05;
      case "98" -> ADDENDA_98;
      case "99" -> ADDENDA_99;
      default -> null;
    };
  }
}
