package co.sena.scheduling.domain.exception;

/** El row_version no coincide (409 ROW_VERSION_MISMATCH, bloqueo optimista). */
public class RowVersionMismatchException extends RuntimeException {

    public RowVersionMismatchException(int expected, int actual) {
        super("row_version no coincide: esperado=" + expected + " actual=" + actual);
    }
}
