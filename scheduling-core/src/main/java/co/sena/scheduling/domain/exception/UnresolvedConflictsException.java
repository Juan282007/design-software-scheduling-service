package co.sena.scheduling.domain.exception;

/** Hay conflictos bloqueantes sin resolver: 409 UNRESOLVED_CONFLICTS (E3). */
public class UnresolvedConflictsException extends RuntimeException {

    private final int unresolvedCount;

    public UnresolvedConflictsException(int unresolvedCount) {
        super("No se puede publicar: hay " + unresolvedCount + " conflictos bloqueantes sin resolver");
        this.unresolvedCount = unresolvedCount;
    }

    public int getUnresolvedCount() {
        return unresolvedCount;
    }
}
