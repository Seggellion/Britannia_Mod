package com.seggellion.britannia_mod.service.banking;

import com.seggellion.britannia_mod.bank.currency.CurrencyItemRegistry;

import java.util.Objects;
import java.util.UUID;

/**
 * The body {@code POST /api/banking/cheque/issue/prepare} needs
 * (docs/banking_bank_cheque_issuance.md): the same player/teller/idempotency identification every
 * prepare carries, plus a {@code cheque: { amount, currency_key }} envelope.
 *
 * <p>{@code amount} is expressed in copper -- the unit Rails' {@code ChequePayloadValidator} and
 * {@code BankCheque} validate and store against (ADR-018/019), not gold.
 *
 * <h2>{@code currencyKey} names the balance, not the unit</h2>
 * Milestone 8a/8b. This is the part of the contract Rails' own docs flag as easy to get backwards,
 * so it is repeated here: the key selects <b>which balance funds the cheque</b> and never rescales
 * {@code amount}. {@code (5_000_000, "gold")} debits 500 gold; {@code (5_000_000, "copper")}
 * debits 5,000,000 copper. Both store a {@code BankCheque#amount} of 5,000,000 and redeem
 * identically -- a cheque remains currency-agnostic at rest (ADR-012 narrowed, not reversed); only
 * its funding gained a denomination.
 *
 * <p>Always sent, even for gold. Rails treats an absent key as gold so that clients predating
 * Milestone 8a keep working indefinitely (§1.1 rule 3), but relying on that default from a client
 * that <em>does</em> know about denominations would make "gold" indistinguishable from "the field
 * got dropped".
 */
public record BankingChequeIssuancePrepareRequest(
    UUID playerUuid,
    UUID worldNpcPublicId,
    String idempotencyKey,
    int amount,
    String currencyKey
) {
    public BankingChequeIssuancePrepareRequest {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(worldNpcPublicId, "worldNpcPublicId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.isBlank()) throw new IllegalArgumentException("idempotencyKey must not be blank");
        if (amount <= 0) throw new IllegalArgumentException("amount must be positive");
        Objects.requireNonNull(currencyKey, "currencyKey");
        if (CurrencyItemRegistry.copperUnitFor(currencyKey).isEmpty()) {
            throw new IllegalArgumentException("unsupported currency key: " + currencyKey);
        }
    }
}
