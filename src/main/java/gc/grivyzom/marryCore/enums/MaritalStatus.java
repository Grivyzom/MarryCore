package gc.grivyzom.marryCore.enums;

/**
 * Enum que representa los diferentes estados civiles
 * que puede tener un jugador en el sistema de matrimonio.
 * ACTUALIZADO: Incluye estado de noviazgo
 *
 * @author Brocolitx
 * @version 0.1.0
 */
public enum MaritalStatus {

    /**
     * Estado inicial - El jugador puede proponer o recibir propuestas
     */
    SOLTERO("soltero", "Soltero/a"),

    /**
     * NUEVO: El jugador está en una relación casual
     */
    NOVIO("novio", "En relación"),

    /**
     * El jugador está comprometido - Puede programar ceremonia
     */
    COMPROMETIDO("comprometido", "Comprometido/a"),

    /**
     * El jugador está casado - Tiene acceso a beneficios matrimoniales
     */
    CASADO("casado", "Casado/a");

    private final String databaseValue;
    private final String displayName;

    MaritalStatus(String databaseValue, String displayName) {
        this.databaseValue = databaseValue;
        this.displayName = displayName;
    }

    /**
     * Obtiene el valor usado en la base de datos
     * @return Valor para almacenar en base de datos
     */
    public String getDatabaseValue() {
        return databaseValue;
    }

    /**
     * Obtiene el nombre para mostrar al jugador
     * @return Nombre legible del estado
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Convierte un valor de base de datos a enum
     * @param databaseValue Valor de la base de datos
     * @return MaritalStatus correspondiente
     */
    public static MaritalStatus fromDatabase(String databaseValue) {
        for (MaritalStatus status : values()) {
            if (status.getDatabaseValue().equals(databaseValue)) {
                return status;
            }
        }
        return SOLTERO; // Valor por defecto
    }

    /**
     * Verifica si el jugador puede proponer matrimonio
     * @return true si puede proponer
     */
    public boolean canPropose() {
        return this == SOLTERO;
    }

    /**
     * NUEVO: Verifica si el jugador puede proponer noviazgo
     * @return true si puede proponer noviazgo
     */
    public boolean canProposeRelationship() {
        return this == SOLTERO;
    }

    /**
     * Verifica si el jugador puede recibir propuestas
     * @return true si puede recibir propuestas
     */
    public boolean canReceiveProposal() {
        return this == SOLTERO;
    }

    /**
     * NUEVO: Verifica si el jugador puede recibir propuestas de noviazgo
     * @return true si puede recibir propuestas de noviazgo
     */
    public boolean canReceiveRelationshipProposal() {
        return this == SOLTERO;
    }

    /**
     * NUEVO: Verifica si el jugador puede comprometerse (debe estar de novio)
     * @return true si puede comprometerse
     */
    public boolean canGetEngaged() {
        return this == NOVIO;
    }

    /**
     * Verifica si el jugador puede programar ceremonias
     * @return true si puede programar ceremonia
     */
    public boolean canScheduleWedding() {
        return this == COMPROMETIDO;
    }

    /**
     * Verifica si el jugador tiene acceso a beneficios matrimoniales
     * @return true si tiene beneficios
     */
    public boolean hasMarriageBenefits() {
        return this == CASADO;
    }

    /**
     * NUEVO: Verifica si el jugador tiene acceso a beneficios de relación
     * @return true si tiene beneficios básicos de relación
     */
    public boolean hasRelationshipBenefits() {
        return this == NOVIO || this == COMPROMETIDO || this == CASADO;
    }

    /**
     * NUEVO: Verifica si el jugador está en una relación (cualquier tipo)
     * @return true si está en algún tipo de relación
     */
    public boolean isInRelationship() {
        return this != SOLTERO;
    }

    /**
     * NUEVO: Obtiene el próximo estado en la progresión natural
     * @return Siguiente estado o null si no hay progresión
     */
    public MaritalStatus getNextStatus() {
        switch (this) {
            case SOLTERO:
                return NOVIO;
            case NOVIO:
                return COMPROMETIDO;
            case COMPROMETIDO:
                return CASADO;
            case CASADO:
                return null; // No hay siguiente estado
            default:
                return null;
        }
    }

    /**
     * NUEVO: Verifica si puede avanzar al siguiente estado
     * @return true si puede avanzar
     */
    public boolean canAdvance() {
        return getNextStatus() != null;
    }

    @Override
    public String toString() {
        return displayName;
    }
}