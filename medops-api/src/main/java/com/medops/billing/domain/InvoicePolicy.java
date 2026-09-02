package com.medops.billing.domain;

import com.medops.shared.exception.InvalidRequestException;

public final class InvoicePolicy {

    private InvoicePolicy() {
    }

    public static void requireIssuable(InvoiceStatus status) {
        if (status != InvoiceStatus.DRAFT) {
            throw new InvalidRequestException("Only a DRAFT invoice can be issued");
        }
    }

    public static void requireVoidable(InvoiceStatus status) {
        if (status == InvoiceStatus.PAID || status == InvoiceStatus.VOID) {
            throw new InvalidRequestException("A " + status + " invoice cannot be voided");
        }
    }

    public static void requirePayable(InvoiceStatus status) {
        if (status == InvoiceStatus.VOID || status == InvoiceStatus.DRAFT) {
            throw new InvalidRequestException("A " + status + " invoice cannot accept payments");
        }
        if (status == InvoiceStatus.PAID) {
            throw new InvalidRequestException("Invoice is already fully paid");
        }
    }

    public static void requireItemAddable(InvoiceStatus status) {
        if (status != InvoiceStatus.DRAFT) {
            throw new InvalidRequestException("Items can only be added to a DRAFT invoice");
        }
    }
}
