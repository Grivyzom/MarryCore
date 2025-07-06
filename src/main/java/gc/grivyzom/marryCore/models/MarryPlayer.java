package gc.grivyzom.marryCore.models;

import gc.grivyzom.marryCore.enums.MaritalStatus;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Modelo que representa a un jugador en el sistema de matrimonio.
 * Contiene toda la información relacionada con su estado civil.
 *
 * @author Brocolitx
 * @version 0.0.1
 */
public class MarryPlayer {

    private UUID uuid;
    private String username;
    private MaritalStatus status;
    private UUID partnerUuid;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    // Constructor vacío
    public MarryPlayer() {}

    // Constructor completo
    public MarryPlayer(UUID uuid, String username, MaritalStatus status,
                       UUID partnerUuid, Timestamp createdAt, Timestamp updatedAt) {
        this.uuid = uuid;
        this.username = username;
        this.status = status;
        this.partnerUuid = partnerUuid;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Constructor básico
    public MarryPlayer(UUID uuid, String username) {
        this.uuid = uuid;
        this.username = username;
        this.status = MaritalStatus.SOLTERO;
        this.partnerUuid = null;
        this.createdAt = new Timestamp(System.currentTimeMillis());
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    // Getters y Setters
    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public MaritalStatus getStatus() {
        return status;
    }

    public void setStatus(MaritalStatus status) {
        this.status = status;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public UUID getPartnerUuid() {
        return partnerUuid;
    }

    public void setPartnerUuid(UUID partnerUuid) {
        this.partnerUuid = partnerUuid;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Métodos de utilidad

    /**
     * Verifica si el jugador está soltero
     * @return true si está soltero
     */
    public boolean isSingle() {
        return status == MaritalStatus.SOLTERO;
    }

    /**
     * Verifica si el jugador está comprometido
     * @return true si está comprometido
     */
    public boolean isEngaged() {
        return status == MaritalStatus.COMPROMETIDO;
    }

    /**
     * Verifica si el jugador está casado
     * @return true si está casado
     */
    public boolean isMarried() {
        return status == MaritalStatus.CASADO;
    }

    /**
     * Verifica si tiene pareja
     * @return true si tiene pareja
     */
    public boolean hasPartner() {
        return partnerUuid != null;
    }

    /**
     * Verifica si es pareja del UUID especificado
     * @param uuid UUID a verificar
     * @return true si es su pareja
     */
    public boolean isPartnerOf(UUID uuid) {
        return partnerUuid != null && partnerUuid.equals(uuid);
    }

    /**
     * Compromete al jugador con otra persona
     * @param partnerUuid UUID de la pareja
     */
    public void engageTo(UUID partnerUuid) {
        this.partnerUuid = partnerUuid;
        this.status = MaritalStatus.COMPROMETIDO;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    /**
     * Casa al jugador con su pareja comprometida
     */
    public void marry() {
        if (status == MaritalStatus.COMPROMETIDO && partnerUuid != null) {
            this.status = MaritalStatus.CASADO;
            this.updatedAt = new Timestamp(System.currentTimeMillis());
        }
    }

    /**
     * Divorcia al jugador
     */
    public void divorce() {
        this.partnerUuid = null;
        this.status = MaritalStatus.SOLTERO;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    /**
     * Obtiene una representación del estado del jugador
     * @return String descriptivo del estado
     */
    public String getStatusDescription() {
        if (status == MaritalStatus.SOLTERO) {
            return "Soltero/a";
        } else if (status == MaritalStatus.COMPROMETIDO) {
            return "Comprometido/a" + (partnerUuid != null ? " con alguien" : "");
        } else if (status == MaritalStatus.CASADO) {
            return "Casado/a" + (partnerUuid != null ? " con alguien" : "");
        }
        return "Estado desconocido";
    }

    @Override
    public String toString() {
        return "MarryPlayer{" +
                "uuid=" + uuid +
                ", username='" + username + '\'' +
                ", status=" + status +
                ", partnerUuid=" + partnerUuid +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        MarryPlayer that = (MarryPlayer) obj;
        return uuid != null && uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return uuid != null ? uuid.hashCode() : 0;
    }
}