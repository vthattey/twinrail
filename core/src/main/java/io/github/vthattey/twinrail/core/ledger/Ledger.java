package io.github.vthattey.twinrail.core.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Balances per test account, with every booking kept in order.
 *
 * <p>Accounts opened with {@link #open} do not allow overdrafts. An account that a payment mentions
 * but nobody opened is created on first use with a zero balance and may go negative, so a plain
 * test does not have to set up accounts. Money is only ever moved between two accounts, so the sum
 * of all balances never changes.
 *
 * <p>Thread-safe: every method is synchronised.
 */
public final class Ledger {
  private static final class Account {
    final String id;
    final String currency;
    final BigDecimal opening;
    final boolean overdraft;
    BigDecimal balance;
    BigDecimal held = BigDecimal.ZERO;

    Account(String id, String currency, BigDecimal opening, boolean overdraft) {
      this.id = id;
      this.currency = currency;
      this.opening = opening;
      this.balance = opening;
      this.overdraft = overdraft;
    }
  }

  private final Map<String, Account> accounts = new LinkedHashMap<>();
  private final List<Entry> entries = new ArrayList<>();

  /** Opens an account with a balance. It may not be overdrawn. */
  public synchronized void open(String account, String currency, BigDecimal openingBalance) {
    Objects.requireNonNull(account, "account");
    Objects.requireNonNull(currency, "currency");
    if (openingBalance.signum() < 0) {
      throw new LedgerException(
          "Opening balance for " + account + " is negative. Use zero or a positive amount.");
    }
    if (accounts.containsKey(account)) {
      throw new LedgerException(
          "Account " + account + " is already open. Use a different id or reset the emulator.");
    }
    accounts.put(account, new Account(account, currency, openingBalance, false));
  }

  public synchronized boolean has(String account) {
    return accounts.containsKey(account);
  }

  /** The account's currency, or {@code null} if it does not exist. */
  public synchronized String currency(String account) {
    Account a = accounts.get(account);
    return a == null ? null : a.currency;
  }

  public synchronized BigDecimal openingBalance(String account) {
    return require(account).opening;
  }

  public synchronized BigDecimal balance(String account) {
    return require(account).balance;
  }

  /** Balance minus money reserved for accepted but unsettled payments. */
  public synchronized BigDecimal available(String account) {
    Account a = require(account);
    return a.balance.subtract(a.held);
  }

  /**
   * Reserves money for an accepted payment, creating the account if needed.
   *
   * @return {@code OK}, or why it was refused: not enough available funds on an account that may
   *     not be overdrawn, or a currency different from the account's
   */
  public synchronized Reservation reserve(String account, BigDecimal amount, String currency) {
    Account a =
        accounts.computeIfAbsent(account, id -> new Account(id, currency, BigDecimal.ZERO, true));
    if (!a.currency.equals(currency)) {
      return Reservation.WRONG_CURRENCY;
    }
    if (!a.overdraft && a.balance.subtract(a.held).compareTo(amount) < 0) {
      return Reservation.INSUFFICIENT_FUNDS;
    }
    a.held = a.held.add(amount);
    return Reservation.OK;
  }

  /** Gives back money reserved with {@link #reserve}, for a payment that will not settle. */
  public synchronized void release(String account, BigDecimal amount) {
    Account a = require(account);
    a.held = a.held.subtract(amount);
  }

  /**
   * Books a settled payment: debit one account, credit the other. The debit consumes the
   * reservation made with {@link #reserve}.
   */
  public synchronized void settle(
      Instant at,
      String paymentId,
      String endToEndId,
      String debtor,
      String creditor,
      BigDecimal amount,
      String currency) {
    Account from = require(debtor);
    Account to =
        accounts.computeIfAbsent(creditor, id -> new Account(id, currency, BigDecimal.ZERO, true));
    if (!to.currency.equals(currency)) {
      throw new LedgerException(
          "Creditor account "
              + creditor
              + " holds "
              + to.currency
              + " but the payment is in "
              + currency
              + ". Open the account in the payment currency.");
    }
    from.held = from.held.subtract(amount);
    from.balance = from.balance.subtract(amount);
    to.balance = to.balance.add(amount);
    entries.add(
        new Entry(
            entries.size() + 1L, at, debtor, Side.DEBIT, amount, currency, paymentId, endToEndId));
    entries.add(
        new Entry(
            entries.size() + 1L,
            at,
            creditor,
            Side.CREDIT,
            amount,
            currency,
            paymentId,
            endToEndId));
  }

  /** Every booking, in booking order. */
  public synchronized List<Entry> entries() {
    return List.copyOf(entries);
  }

  /** Bookings on one account, in booking order. */
  public synchronized List<Entry> entries(String account) {
    return entries.stream().filter(e -> e.account().equals(account)).toList();
  }

  /** Account ids in the order they were first seen. */
  public synchronized List<String> accountIds() {
    return List.copyOf(accounts.keySet());
  }

  /** The sum of all balances. Equals the sum of opening balances at all times. */
  public synchronized BigDecimal total() {
    return accounts.values().stream().map(a -> a.balance).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private Account require(String account) {
    Account a = accounts.get(account);
    if (a == null) {
      throw new LedgerException(
          "Account " + account + " does not exist. Open it first or send a payment that uses it.");
    }
    return a;
  }

  /** The result of {@link #reserve}. */
  public enum Reservation {
    OK,
    INSUFFICIENT_FUNDS,
    WRONG_CURRENCY
  }
}
