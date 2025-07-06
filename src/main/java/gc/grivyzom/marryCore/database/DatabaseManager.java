package gc.grivyzom.marryCore.database;

import gc.grivyzom.marryCore.MarryCore;
import gc.grivyzom.marryCore.enums.MaritalStatus;
import gc.grivyzom.marryCore.models.MarryPlayer;

import java.sql.*;
import java.util.UUID;

/**
 * Clase encargada de gestionar todas las operaciones de base de datos
 * relacionadas con el sistema de matrimonio.
 *
 * @author Brocolitx
 * @version 0.0.1
 */
public class DatabaseManager {

    private final MarryCore plugin;

    public DatabaseManager(MarryCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Obtiene la conexión a la base de datos desde el plugin principal
     * @return Connection activa
     */
    private Connection getConnection() {
        return plugin.getConnection();
    }

    /**
     * Obtiene los datos de un jugador, creándolos si no existen
     * @param uuid UUID del jugador
     * @return MarryPlayer con los datos del jugador
     * @throws SQLException Si hay error en la base de datos
     */
    public MarryPlayer getPlayerData(UUID uuid) throws SQLException {
        String query = "SELECT * FROM marry_players WHERE uuid = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setString(1, uuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    // Jugador existe, cargar datos
                    return new MarryPlayer(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("username"),
                            MaritalStatus.fromDatabase(rs.getString("status")),
                            rs.getString("partner_uuid") != null ?
                                    UUID.fromString(rs.getString("partner_uuid")) : null,
                            rs.getTimestamp("created_at"),
                            rs.getTimestamp("updated_at")
                    );
                } else {
                    // Jugador no existe, crear nuevo registro
                    return createNewPlayer(uuid, "Unknown");
                }
            }
        }
    }

    /**
     * Obtiene los datos de un jugador por nombre de usuario
     * @param username Nombre del jugador
     * @return MarryPlayer con los datos del jugador, null si no existe
     * @throws SQLException Si hay error en la base de datos
     */
    public MarryPlayer getPlayerDataByUsername(String username) throws SQLException {
        String query = "SELECT * FROM marry_players WHERE username = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setString(1, username);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new MarryPlayer(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("username"),
                            MaritalStatus.fromDatabase(rs.getString("status")),
                            rs.getString("partner_uuid") != null ?
                                    UUID.fromString(rs.getString("partner_uuid")) : null,
                            rs.getTimestamp("created_at"),
                            rs.getTimestamp("updated_at")
                    );
                }
            }
        }

        return null;
    }

    /**
     * Crea un nuevo jugador en la base de datos
     * @param uuid UUID del jugador
     * @param username Nombre del jugador
     * @return MarryPlayer creado
     * @throws SQLException Si hay error en la base de datos
     */
    private MarryPlayer createNewPlayer(UUID uuid, String username) throws SQLException {
        String query = "INSERT INTO marry_players (uuid, username, status, partner_uuid) VALUES (?, ?, ?, ?)";

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, username);
            stmt.setString(3, MaritalStatus.SOLTERO.getDatabaseValue());
            stmt.setString(4, null);

            stmt.executeUpdate();
        }

        // Retornar el jugador creado
        return new MarryPlayer(uuid, username);
    }

    /**
     * Actualiza el nombre de usuario de un jugador
     * @param uuid UUID del jugador
     * @param username Nuevo nombre de usuario
     * @throws SQLException Si hay error en la base de datos
     */
    public void updatePlayerUsername(UUID uuid, String username) throws SQLException {
        String query = "UPDATE marry_players SET username = ?, updated_at = CURRENT_TIMESTAMP WHERE uuid = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setString(1, username);
            stmt.setString(2, uuid.toString());

            stmt.executeUpdate();
        }
    }

    /**
     * Actualiza el estado civil de un jugador
     * @param uuid UUID del jugador
     * @param status Nuevo estado civil
     * @throws SQLException Si hay error en la base de datos
     */
    public void updatePlayerStatus(UUID uuid, MaritalStatus status) throws SQLException {
        String query = "UPDATE marry_players SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE uuid = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setString(1, status.getDatabaseValue());
            stmt.setString(2, uuid.toString());

            stmt.executeUpdate();
        }
    }

    /**
     * Actualiza la pareja de un jugador
     * @param uuid UUID del jugador
     * @param partnerUuid UUID de la pareja (null para remover)
     * @throws SQLException Si hay error en la base de datos
     */
    public void updatePlayerPartner(UUID uuid, UUID partnerUuid) throws SQLException {
        String query = "UPDATE marry_players SET partner_uuid = ?, updated_at = CURRENT_TIMESTAMP WHERE uuid = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setString(1, partnerUuid != null ? partnerUuid.toString() : null);
            stmt.setString(2, uuid.toString());

            stmt.executeUpdate();
        }
    }

    /**
     * Crea un compromiso entre dos jugadores
     * @param player1Uuid UUID del primer jugador
     * @param player2Uuid UUID del segundo jugador
     * @throws SQLException Si hay error en la base de datos
     */
    public void createEngagement(UUID player1Uuid, UUID player2Uuid) throws SQLException {
        Connection conn = getConnection();

        try {
            conn.setAutoCommit(false); // Iniciar transacción

            // Actualizar estado de ambos jugadores
            updatePlayerStatus(player1Uuid, MaritalStatus.COMPROMETIDO);
            updatePlayerStatus(player2Uuid, MaritalStatus.COMPROMETIDO);

            // Establecer parejas
            updatePlayerPartner(player1Uuid, player2Uuid);
            updatePlayerPartner(player2Uuid, player1Uuid);

            // Crear registro en tabla de matrimonios
            String marriageQuery = "INSERT INTO marry_marriages (player1_uuid, player2_uuid, status) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(marriageQuery)) {
                stmt.setString(1, player1Uuid.toString());
                stmt.setString(2, player2Uuid.toString());
                stmt.setString(3, "comprometido");
                stmt.executeUpdate();
            }

            conn.commit(); // Confirmar transacción

        } catch (SQLException e) {
            conn.rollback(); // Revertir en caso de error
            throw e;
        } finally {
            conn.setAutoCommit(true); // Restaurar auto-commit
        }
    }

    /**
     * Convierte un compromiso en matrimonio
     * @param player1Uuid UUID del primer jugador
     * @param player2Uuid UUID del segundo jugador
     * @throws SQLException Si hay error en la base de datos
     */
    public void createMarriage(UUID player1Uuid, UUID player2Uuid) throws SQLException {
        Connection conn = getConnection();

        try {
            conn.setAutoCommit(false); // Iniciar transacción

            // Actualizar estado de ambos jugadores
            updatePlayerStatus(player1Uuid, MaritalStatus.CASADO);
            updatePlayerStatus(player2Uuid, MaritalStatus.CASADO);

            // Actualizar registro de matrimonio
            String marriageQuery = """
                UPDATE marry_marriages 
                SET status = 'divorciado', divorce_date = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP 
                WHERE (player1_uuid = ? AND player2_uuid = ?) OR (player1_uuid = ? AND player2_uuid = ?) 
                AND status IN ('comprometido', 'casado')
            """;

            try (PreparedStatement stmt = conn.prepareStatement(marriageQuery)) {
                stmt.setString(1, player1Uuid.toString());
                stmt.setString(2, player2Uuid.toString());
                stmt.setString(3, player2Uuid.toString());
                stmt.setString(4, player1Uuid.toString());
                stmt.executeUpdate();
            }

            conn.commit(); // Confirmar transacción

        } catch (SQLException e) {
            conn.rollback(); // Revertir en caso de error
            throw e;
        } finally {
            conn.setAutoCommit(true); // Restaurar auto-commit
        }
    }

    /**
     * Obtiene el ID del matrimonio entre dos jugadores
     * @param player1Uuid UUID del primer jugador
     * @param player2Uuid UUID del segundo jugador
     * @return ID del matrimonio, -1 si no existe
     * @throws SQLException Si hay error en la base de datos
     */
    public int getMarriageId(UUID player1Uuid, UUID player2Uuid) throws SQLException {
        String query = """
            SELECT id FROM marry_marriages 
            WHERE (player1_uuid = ? AND player2_uuid = ?) OR (player1_uuid = ? AND player2_uuid = ?) 
            AND status IN ('comprometido', 'casado')
        """;

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setString(1, player1Uuid.toString());
            stmt.setString(2, player2Uuid.toString());
            stmt.setString(3, player2Uuid.toString());
            stmt.setString(4, player1Uuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id");
                }
            }
        }

        return -1;
    }

    /**
     * Programa una ceremonia de matrimonio
     * @param marriageId ID del matrimonio
     * @param weddingDate Fecha de la ceremonia
     * @param location Ubicación de la ceremonia
     * @throws SQLException Si hay error en la base de datos
     */
    public void scheduleWedding(int marriageId, Timestamp weddingDate, String location) throws SQLException {
        String query = """
            UPDATE marry_marriages 
            SET wedding_date = ?, ceremony_location = ?, updated_at = CURRENT_TIMESTAMP 
            WHERE id = ?
        """;

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setTimestamp(1, weddingDate);
            stmt.setString(2, location);
            stmt.setInt(3, marriageId);

            stmt.executeUpdate();
        }
    }

    /**
     * Añade un invitado a una ceremonia
     * @param marriageId ID del matrimonio
     * @param guestUuid UUID del invitado
     * @param invitedBy UUID de quien invita
     * @throws SQLException Si hay error en la base de datos
     */
    public void addGuest(int marriageId, UUID guestUuid, UUID invitedBy) throws SQLException {
        String query = "INSERT INTO marry_guests (marriage_id, guest_uuid, invited_by) VALUES (?, ?, ?)";

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setInt(1, marriageId);
            stmt.setString(2, guestUuid.toString());
            stmt.setString(3, invitedBy.toString());

            stmt.executeUpdate();
        }
    }

    /**
     * Remueve un invitado de una ceremonia
     * @param marriageId ID del matrimonio
     * @param guestUuid UUID del invitado
     * @throws SQLException Si hay error en la base de datos
     */
    public void removeGuest(int marriageId, UUID guestUuid) throws SQLException {
        String query = "DELETE FROM marry_guests WHERE marriage_id = ? AND guest_uuid = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setInt(1, marriageId);
            stmt.setString(2, guestUuid.toString());

            stmt.executeUpdate();
        }
    }

    /**
     * Actualiza el estado de confirmación de un invitado
     * @param marriageId ID del matrimonio
     * @param guestUuid UUID del invitado
     * @param status Nuevo estado (invitado, confirmado, rechazado)
     * @throws SQLException Si hay error en la base de datos
     */
    public void updateGuestStatus(int marriageId, UUID guestUuid, String status) throws SQLException {
        String query = "UPDATE marry_guests SET status = ? WHERE marriage_id = ? AND guest_uuid = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setString(1, status);
            stmt.setInt(2, marriageId);
            stmt.setString(3, guestUuid.toString());

            stmt.executeUpdate();
        }
    }

    /**
     * Verifica si una fecha está disponible para ceremonia
     * @param weddingDate Fecha a verificar
     * @return true si está disponible
     * @throws SQLException Si hay error en la base de datos
     */
    public boolean isDateAvailable(Timestamp weddingDate) throws SQLException {
        String query = """
            SELECT COUNT(*) as count FROM marry_marriages 
            WHERE DATE(wedding_date) = DATE(?) AND status = 'comprometido'
        """;

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setTimestamp(1, weddingDate);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int maxCeremoniesPerDay = plugin.getConfig().getInt("marriage.wedding.max_ceremonies_per_day", 5);
                    return rs.getInt("count") < maxCeremoniesPerDay;
                }
            }
        }

        return true;
    }

    /**
     * Obtiene el número de invitados confirmados para una ceremonia
     * @param marriageId ID del matrimonio
     * @return Número de invitados confirmados
     * @throws SQLException Si hay error en la base de datos
     */
    public int getConfirmedGuestsCount(int marriageId) throws SQLException {
        String query = "SELECT COUNT(*) as count FROM marry_guests WHERE marriage_id = ? AND status = 'confirmado'";

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setInt(1, marriageId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
        }

        return 0;
    }

    /**
     * Verifica si un jugador ya está invitado a una ceremonia
     * @param marriageId ID del matrimonio
     * @param guestUuid UUID del potencial invitado
     * @return true si ya está invitado
     * @throws SQLException Si hay error en la base de datos
     */
    public boolean isPlayerInvited(int marriageId, UUID guestUuid) throws SQLException {
        String query = "SELECT COUNT(*) as count FROM marry_guests WHERE marriage_id = ? AND guest_uuid = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setInt(1, marriageId);
            stmt.setString(2, guestUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count") > 0;
                }
            }
        }

        return false;
    }

    /**
     * Elimina completamente los datos de un jugador (para uso administrativo)
     * @param uuid UUID del jugador
     * @throws SQLException Si hay error en la base de datos
     */
    public void deletePlayerData(UUID uuid) throws SQLException {
        Connection conn = getConnection();

        try {
            conn.setAutoCommit(false); // Iniciar transacción

            // Si el jugador tiene pareja, divorciarlos primero
            MarryPlayer player = getPlayerData(uuid);
            if (player.hasPartner()) {
                createDivorce(uuid, player.getPartnerUuid());
            }

            // Eliminar invitaciones del jugador
            String deleteGuestsQuery = "DELETE FROM marry_guests WHERE guest_uuid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteGuestsQuery)) {
                stmt.setString(1, uuid.toString());
                stmt.executeUpdate();
            }

            // Eliminar registro del jugador
            String deletePlayerQuery = "DELETE FROM marry_players WHERE uuid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deletePlayerQuery)) {
                stmt.setString(1, uuid.toString());
                stmt.executeUpdate();
            }

            conn.commit(); // Confirmar transacción

        } catch (SQLException e) {
            conn.rollback(); // Revertir en caso de error
            throw e;
        } finally {
            conn.setAutoCommit(true); // Restaurar auto-commit
        }
    }

    /**
     * Obtiene estadísticas generales del sistema
     * @return Array con [total_jugadores, total_solteros, total_comprometidos, total_casados]
     * @throws SQLException Si hay error en la base de datos
     */
    public int[] getSystemStats() throws SQLException {
        String query = """
            SELECT 
                COUNT(*) as total,
                SUM(CASE WHEN status = 'soltero' THEN 1 ELSE 0 END) as solteros,
                SUM(CASE WHEN status = 'comprometido' THEN 1 ELSE 0 END) as comprometidos,
                SUM(CASE WHEN status = 'casado' THEN 1 ELSE 0 END) as casados
            FROM marry_players
        """;

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new int[] {
                            rs.getInt("total"),
                            rs.getInt("solteros"),
                            rs.getInt("comprometidos"),
                            rs.getInt("casados")
                    };
                }
            }
        }

        return new int[]{0, 0, 0, 0};
    }

    /**
     * Verifica la integridad de la base de datos y repara inconsistencias
     * @return Número de registros reparados
     * @throws SQLException Si hay error en la base de datos
     */
    public int repairDatabase() throws SQLException {
        int repairedCount = 0;

        // Reparar referencias de parejas rotas
        String repairQuery = """
            UPDATE marry_players p1 
            SET partner_uuid = NULL, status = 'soltero' 
            WHERE partner_uuid IS NOT NULL 
            AND NOT EXISTS (
                SELECT 1 FROM marry_players p2 
                WHERE p2.uuid = p1.partner_uuid 
                AND p2.partner_uuid = p1.uuid
            )
        """;

        try (PreparedStatement stmt = getConnection().prepareStatement(repairQuery)) {
            repairedCount = stmt.executeUpdate();
        }

        return repairedCount;
    }
} status = 'casado', wedding_date = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
WHERE (player1_uuid = ? AND player2_uuid = ?) OR (player1_uuid = ? AND player2_uuid = ?)
AND status = 'comprometido'
            """;

            try (PreparedStatement stmt = conn.prepareStatement(marriageQuery)) {
                stmt.setString(1, player1Uuid.toString());
                stmt.setString(2, player2Uuid.toString());
                stmt.setString(3, player2Uuid.toString());
                stmt.setString(4, player1Uuid.toString());
                stmt.executeUpdate();
            }

            conn.commit(); // Confirmar transacción

        } catch (SQLException e) {
            conn.rollback(); // Revertir en caso de error
            throw e;
        } finally {
            conn.setAutoCommit(true); // Restaurar auto-commit
        }
    }

    /**
     * Procesa un divorcio entre dos jugadores
     * @param player1Uuid UUID del primer jugador
     * @param player2Uuid UUID del segundo jugador
     * @throws SQLException Si hay error en la base de datos
     */
    public void createDivorce(UUID player1Uuid, UUID player2Uuid) throws SQLException {
        Connection conn = getConnection();

        try {
            conn.setAutoCommit(false); // Iniciar transacción

            // Actualizar estado de ambos jugadores a soltero
            updatePlayerStatus(player1Uuid, MaritalStatus.SOLTERO);
            updatePlayerStatus(player2Uuid, MaritalStatus.SOLTERO);

            // Remover parejas
            updatePlayerPartner(player1Uuid, null);
            updatePlayerPartner(player2Uuid, null);

            // Actualizar registro de matrimonio
            String marriageQuery = """
UPDATE marry_marriages
SET