package org.vidhura.assessment.ingestion;

import org.vidhura.assessment.domain.TransactionRequest;

import java.util.List;

/**
 * Replaceable input adapter. Production could swap in queue / DB / API polling.
 */
public interface TransactionSource {

    List<TransactionRequest> load();
}
