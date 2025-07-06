package gc.grivyzom.marryCore.database;

import gc.grivyzom.marryCore.MarryCore;
import gc.grivyzom.marryCore.enums.MaritalStatus;
import gc.grivyzom.marryCore.models.MarryPlayer;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
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
     *
     * @return Connection activa
     */
    private Connection getConnection() {
        return plugin.getConnection();
    }

    /**
     * Obtiene los datos de un jugador, creándolos si no existen
     *
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
     *
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
     *
     * @param uuid     UUID del jugador
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
     *
     * @param uuid     UUID del jugador
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
     *
     * @param uuid   UUID del jugador
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
     *
     * @param uuid        UUID del jugador
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
     *
     * @param player1Uuid UUID del primer jugador
     * @param player2Uuid UUID del segundo jugador
     * @throws SQLException Si hay error en la base de datos
     */
    /**
     * MÉTODO CORREGIDO: Crea un compromiso entre dos jugadores
     * Mejorado para registrar correctamente los datos
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

            // CORRECCIÓN: Crear registro en tabla de matrimonios con mejor manejo
            String marriageQuery = """
                INSERT INTO marry_marriages (player1_uuid, player2_uuid, status, engagement_date, created_at, updated_at) 
                VALUES (?, ?, 'comprometido', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """;

            try (PreparedStatement stmt = conn.prepareStatement(marriageQuery)) {
                stmt.setString(1, player1Uuid.toString());
                stmt.setString(2, player2Uuid.toString());

                int rowsInserted = stmt.executeUpdate();

                if (rowsInserted == 0) {
                    throw new SQLException("No se pudo crear el registro de compromiso en la base de datos");
                }

                plugin.getLogger().info("Compromiso registrado correctamente en la base de datos");
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
     * NUEVO MÉTODO: Obtiene la fecha de matrimonio real de un jugador
     */
    public Timestamp getActualWeddingDate(UUID playerUuid) throws SQLException {
        String query = """
            SELECT wedding_date FROM marry_marriages 
            WHERE (player1_uuid = ? OR player2_uuid = ?) 
            AND status = 'casado' 
            AND wedding_date IS NOT NULL
            ORDER BY wedding_date DESC 
            LIMIT 1
        """;

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getTimestamp("wedding_date");
                }
            }
        }

        return null;
    }

    /**
     * NUEVO MÉTODO: Obtiene información completa del matrimonio activo de un jugador
     */
    public Map<String, Object> getActiveMarriageInfo(UUID playerUuid) throws SQLException {
        String query = """
            SELECT m.*, 
                   p1.username as player1_name,
                   p2.username as player2_name
            FROM marry_marriages m
            INNER JOIN marry_players p1 ON m.player1_uuid = p1.uuid
            INNER JOIN marry_players p2 ON m.player2_uuid = p2.uuid
            WHERE (m.player1_uuid = ? OR m.player2_uuid = ?)
            AND m.status IN ('comprometido', 'casado')
            ORDER BY m.updated_at DESC
            LIMIT 1
        """;

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> info = new HashMap<>();
                    info.put("id", rs.getInt("id"));
                    info.put("player1_uuid", rs.getString("player1_uuid"));
                    info.put("player2_uuid", rs.getString("player2_uuid"));
                    info.put("player1_name", rs.getString("player1_name"));
                    info.put("player2_name", rs.getString("player2_name"));
                    info.put("status", rs.getString("status"));
                    info.put("engagement_date", rs.getTimestamp("engagement_date"));
                    info.put("wedding_date", rs.getTimestamp("wedding_date"));
                    info.put("ceremony_location", rs.getString("ceremony_location"));
                    info.put("created_at", rs.getTimestamp("created_at"));
                    info.put("updated_at", rs.getTimestamp("updated_at"));
                    return info;
                }
            }
        }

        return null;
    }

    /**
     * Convierte un compromiso en matrimonio
     *
     * @param player1Uuid UUID del primer jugador
     * @param player2Uuid UUID del segundo jugador
     * @throws SQLException Si hay error en la base de datos
     */
    /**
     * MÉTODO CORREGIDO: Convierte un compromiso en matrimonio
     * Ahora registra correctamente la fecha de casamiento
     */
    public void createMarriage(UUID player1Uuid, UUID player2Uuid) throws SQLException {
        Connection conn = getConnection();

        try {
            conn.setAutoCommit(false); // Iniciar transacción

            // Actualizar estado de ambos jugadores
            updatePlayerStatus(player1Uuid, MaritalStatus.CASADO);
            updatePlayerStatus(player2Uuid, MaritalStatus.CASADO);

            // CORRECCIÓN: Actualizar registro de matrimonio con fecha actual
            String marriageQuery = """
                UPDATE marry_marriages 
                SET status = 'casado', 
                    wedding_date = CURRENT_TIMESTAMP, 
                    updated_at = CURRENT_TIMESTAMP 
                WHERE (player1_uuid = ? AND player2_uuid = ?) OR (player1_uuid = ? AND player2_uuid = ?) 
                AND status = 'comprometido'
            """;

            try (PreparedStatement stmt = conn.prepareStatement(marriageQuery)) {
                stmt.setString(1, player1Uuid.toString());
                stmt.setString(2, player2Uuid.toString());
                stmt.setString(3, player2Uuid.toString());
                stmt.setString(4, player1Uuid.toString());

                int rowsUpdated = stmt.executeUpdate();

                // Verificar que se actualizó correctamente
                if (rowsUpdated == 0) {
                    throw new SQLException("No se pudo actualizar el registro de matrimonio. Verifique que estén comprometidos.");
                }

                plugin.getLogger().info("Matrimonio registrado correctamente en la base de datos");
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
     *
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
     *
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
     *
     * @param marriageId  ID del matrimonio
     * @param weddingDate Fecha de la ceremonia
     * @param location    Ubicación de la ceremonia
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
     * MÉTODO CORREGIDO: Obtiene información de estado más precisa
     */
    public MaritalStatus getActualMaritalStatus(UUID playerUuid) throws SQLException {
        // Primero verificar en la tabla de matrimonios
        String marriageQuery = """
            SELECT status FROM marry_marriages 
            WHERE (player1_uuid = ? OR player2_uuid = ?) 
            AND status IN ('comprometido', 'casado')
            ORDER BY updated_at DESC 
            LIMIT 1
        """;

        try (PreparedStatement stmt = getConnection().prepareStatement(marriageQuery)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String status = rs.getString("status");
                    return MaritalStatus.fromDatabase(status);
                }
            }
        }

        // Si no hay registro de matrimonio, verificar en la tabla de jugadores
        MarryPlayer playerData = getPlayerData(playerUuid);
        return playerData.getStatus();
    }
    /**
     * NUEVO MÉTODO: Sincroniza el estado del jugador con los registros de matrimonio
     */
    public void synchronizePlayerStatus(UUID playerUuid) throws SQLException {
        MaritalStatus actualStatus = getActualMaritalStatus(playerUuid);

        // Solo actualizar si el estado es diferente
        MarryPlayer currentData = getPlayerData(playerUuid);
        if (currentData.getStatus() != actualStatus) {
            updatePlayerStatus(playerUuid, actualStatus);

            plugin.getLogger().info("Estado sincronizado para jugador " + playerUuid + ": " + actualStatus);
        }
    }

    /**
     * NUEVO MÉTODO: Verifica y corrige inconsistencias en los datos
     */
    public int validateAndFixData() throws SQLException {
        int fixedCount = 0;

        // Obtener todos los jugadores con pareja
        String query = "SELECT uuid, partner_uuid, status FROM marry_players WHERE partner_uuid IS NOT NULL";

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID playerUuid = UUID.fromString(rs.getString("uuid"));
                    String currentStatus = rs.getString("status");

                    // Verificar estado real en matrimonios
                    MaritalStatus realStatus = getActualMaritalStatus(playerUuid);

                    if (!realStatus.getDatabaseValue().equals(currentStatus)) {
                        updatePlayerStatus(playerUuid, realStatus);
                        fixedCount++;
                        plugin.getLogger().info("Corregido estado de " + playerUuid + " de " + currentStatus + " a " + realStatus);
                    }
                }
            }
        }

        return fixedCount;
    }

    /**
     * Añade un invitado a una ceremonia
     *
     * @param marriageId ID del matrimonio
     * @param guestUuid  UUID del invitado
     * @param invitedBy  UUID de quien invita
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
     *
     * @param marriageId ID del matrimonio
     * @param guestUuid  UUID del invitado
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
     *
     * @param marriageId ID del matrimonio
     * @param guestUuid  UUID del invitado
     * @param status     Nuevo estado (invitado, confirmado, rechazado)
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
     *
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
     *
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
     *
     * @param marriageId ID del matrimonio
     * @param guestUuid  UUID del potencial invitado
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
     *
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
     *
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
                    return new int[]{
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
     *
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
// Métodos adicionales para DatabaseManager.java
// Añadir estos métodos a la clase DatabaseManager existente

    /**
     * Obtiene la fecha de boda de un matrimonio específico
     *
     * @param marriageId ID del matrimonio
     * @return Timestamp de la fecha de boda, null si no existe
     * @throws SQLException Si hay error en la base de datos
     */
    public Timestamp getWeddingDateByMarriageId(int marriageId) throws SQLException {
        String query = "SELECT wedding_date FROM marry_marriages WHERE id = ? AND status = 'casado'";

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setInt(1, marriageId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getTimestamp("wedding_date");
                }
            }
        }

        return null;
    }

    /**
     * Obtiene la fecha de ceremonia programada de un matrimonio
     *
     * @param marriageId ID del matrimonio
     * @return Timestamp de la fecha de ceremonia, null si no existe
     * @throws SQLException Si hay error en la base de datos
     */
    public Timestamp getCeremonyDateByMarriageId(int marriageId) throws SQLException {
        String query = "SELECT wedding_date FROM marry_marriages WHERE id = ? AND status = 'comprometido'";

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setInt(1, marriageId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getTimestamp("wedding_date");
                }
            }
        }

        return null;
    }


    /**
     * Obtiene la ubicación de ceremonia de un matrimonio
     *
     * @param marriageId ID del matrimonio
     * @return Ubicación de la ceremonia, null si no existe
     * @throws SQLException Si hay error en la base de datos
     */
    public String getCeremonyLocationByMarriageId(int marriageId) throws SQLException {
        String query = "SELECT ceremony_location FROM marry_marriages WHERE id = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setInt(1, marriageId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("ceremony_location");
                }
            }
        }

        return null;
    }

    /**
     * Obtiene la fecha de compromiso de un matrimonio
     *
     * @param marriageId ID del matrimonio
     * @return Timestamp de la fecha de compromiso
     * @throws SQLException Si hay error en la base de datos
     */
    public Timestamp getEngagementDateByMarriageId(int marriageId) throws SQLException {
        String query = "SELECT engagement_date FROM marry_marriages WHERE id = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setInt(1, marriageId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getTimestamp("engagement_date");
                }
            }
        }

        return null;
    }


    /**
     * Obtiene la lista de invitados de un matrimonio
     *
     * @param marriageId ID del matrimonio
     * @param status     Estado de la invitación (null para todos)
     * @return Lista de UUIDs de invitados
     * @throws SQLException Si hay error en la base de datos
     */
    public java.util.List<java.util.UUID> getGuestsByMarriageId(int marriageId, String status) throws SQLException {
        java.util.List<java.util.UUID> guests = new java.util.ArrayList<>();

        String query = "SELECT guest_uuid FROM marry_guests WHERE marriage_id = ?";
        if (status != null) {
            query += " AND status = ?";
        }

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setInt(1, marriageId);
            if (status != null) {
                stmt.setString(2, status);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    guests.add(java.util.UUID.fromString(rs.getString("guest_uuid")));
                }
            }
        }

        return guests;
    }

    /**
     * Obtiene los nombres de los invitados de un matrimonio
     *
     * @param marriageId ID del matrimonio
     * @param status     Estado de la invitación (null para todos)
     * @param limit      Límite de resultados (-1 para sin límite)
     * @return Lista de nombres de invitados
     * @throws SQLException Si hay error en la base de datos
     */
    public java.util.List<String> getGuestNamesByMarriageId(int marriageId, String status, int limit) throws SQLException {
        java.util.List<String> guestNames = new java.util.ArrayList<>();

        String query = """
        SELECT p.username 
        FROM marry_guests g 
        INNER JOIN marry_players p ON g.guest_uuid = p.uuid 
        WHERE g.marriage_id = ?
    """;

        if (status != null) {
            query += " AND g.status = ?";
        }

        if (limit > 0) {
            query += " LIMIT ?";
        }

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            int paramIndex = 1;
            stmt.setInt(paramIndex++, marriageId);

            if (status != null) {
                stmt.setString(paramIndex++, status);
            }

            if (limit > 0) {
                stmt.setInt(paramIndex, limit);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    guestNames.add(rs.getString("username"));
                }
            }
        }

        return guestNames;
    }

    /**
     * Obtiene estadísticas extendidas del sistema
     *
     * @return Map con estadísticas detalladas
     * @throws SQLException Si hay error en la base de datos
     */
    public java.util.Map<String, Integer> getExtendedStats() throws SQLException {
        java.util.Map<String, Integer> stats = new java.util.HashMap<>();

        // Estadísticas básicas de jugadores
        String playerStatsQuery = """
        SELECT 
            COUNT(*) as total_players,
            SUM(CASE WHEN status = 'soltero' THEN 1 ELSE 0 END) as single_players,
            SUM(CASE WHEN status = 'comprometido' THEN 1 ELSE 0 END) as engaged_players,
            SUM(CASE WHEN status = 'casado' THEN 1 ELSE 0 END) as married_players
        FROM marry_players
    """;

        try (PreparedStatement stmt = getConnection().prepareStatement(playerStatsQuery)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("total_players", rs.getInt("total_players"));
                    stats.put("single_players", rs.getInt("single_players"));
                    stats.put("engaged_players", rs.getInt("engaged_players"));
                    stats.put("married_players", rs.getInt("married_players"));
                }
            }
        }

        // Estadísticas de matrimonios
        String marriageStatsQuery = """
        SELECT 
            COUNT(*) as total_marriages,
            SUM(CASE WHEN status = 'comprometido' THEN 1 ELSE 0 END) as active_engagements,
            SUM(CASE WHEN status = 'casado' THEN 1 ELSE 0 END) as active_marriages,
            SUM(CASE WHEN status = 'divorciado' THEN 1 ELSE 0 END) as divorces
        FROM marry_marriages
    """;

        try (PreparedStatement stmt = getConnection().prepareStatement(marriageStatsQuery)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("total_marriages", rs.getInt("total_marriages"));
                    stats.put("active_engagements", rs.getInt("active_engagements"));
                    stats.put("active_marriages", rs.getInt("active_marriages"));
                    stats.put("divorces", rs.getInt("divorces"));
                }
            }
        }

        // Estadísticas de ceremonias programadas
        String ceremonyStatsQuery = """
        SELECT COUNT(*) as scheduled_ceremonies
        FROM marry_marriages 
        WHERE status = 'comprometido' 
        AND wedding_date IS NOT NULL 
        AND wedding_date > NOW()
    """;

        try (PreparedStatement stmt = getConnection().prepareStatement(ceremonyStatsQuery)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("scheduled_ceremonies", rs.getInt("scheduled_ceremonies"));
                }
            }
        }

        // Estadísticas de invitados
        String guestStatsQuery = """
        SELECT 
            COUNT(*) as total_invitations,
            SUM(CASE WHEN status = 'confirmado' THEN 1 ELSE 0 END) as confirmed_guests,
            SUM(CASE WHEN status = 'rechazado' THEN 1 ELSE 0 END) as declined_guests
        FROM marry_guests
    """;

        try (PreparedStatement stmt = getConnection().prepareStatement(guestStatsQuery)) {
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    stats.put("total_invitations", rs.getInt("total_invitations"));
                    stats.put("confirmed_guests", rs.getInt("confirmed_guests"));
                    stats.put("declined_guests", rs.getInt("declined_guests"));
                }
            }
        }

        return stats;
    }

    /**
     * Obtiene las próximas ceremonias programadas
     *
     * @param days  Días hacia adelante para buscar
     * @param limit Límite de resultados
     * @return Lista de información de ceremonias
     * @throws SQLException Si hay error en la base de datos
     */
    public java.util.List<java.util.Map<String, Object>> getUpcomingCeremonies(int days, int limit) throws SQLException {
        java.util.List<java.util.Map<String, Object>> ceremonies = new java.util.ArrayList<>();

        String query = """
        SELECT 
            m.id,
            m.wedding_date,
            m.ceremony_location,
            p1.username as player1_name,
            p2.username as player2_name
        FROM marry_marriages m
        INNER JOIN marry_players p1 ON m.player1_uuid = p1.uuid
        INNER JOIN marry_players p2 ON m.player2_uuid = p2.uuid
        WHERE m.status = 'comprometido'
        AND m.wedding_date IS NOT NULL
        AND m.wedding_date BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL ? DAY)
        ORDER BY m.wedding_date ASC
        LIMIT ?
    """;

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setInt(1, days);
            stmt.setInt(2, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    java.util.Map<String, Object> ceremony = new java.util.HashMap<>();
                    ceremony.put("id", rs.getInt("id"));
                    ceremony.put("wedding_date", rs.getTimestamp("wedding_date"));
                    ceremony.put("ceremony_location", rs.getString("ceremony_location"));
                    ceremony.put("player1_name", rs.getString("player1_name"));
                    ceremony.put("player2_name", rs.getString("player2_name"));

                    ceremonies.add(ceremony);
                }
            }
        }

        return ceremonies;
    }

    /**
     * Verifica si un jugador tiene ceremonias programadas próximas
     *
     * @param playerUuid UUID del jugador
     * @param hoursAhead Horas hacia adelante para verificar
     * @return true si tiene ceremonia próxima
     * @throws SQLException Si hay error en la base de datos
     */
    public boolean hasUpcomingCeremony(java.util.UUID playerUuid, int hoursAhead) throws SQLException {
        String query = """
        SELECT COUNT(*) as count
        FROM marry_marriages 
        WHERE (player1_uuid = ? OR player2_uuid = ?)
        AND status = 'comprometido'
        AND wedding_date IS NOT NULL
        AND wedding_date BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL ? HOUR)
    """;

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerUuid.toString());
            stmt.setInt(3, hoursAhead);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count") > 0;
                }
            }
        }

        return false;
    }

    /**
     * Obtiene el número de matrimonios de un jugador en su historial
     *
     * @param playerUuid UUID del jugador
     * @return Número total de matrimonios (incluyendo divorcios)
     * @throws SQLException Si hay error en la base de datos
     */
    public int getPlayerMarriageCount(java.util.UUID playerUuid) throws SQLException {
        String query = """
        SELECT COUNT(*) as count
        FROM marry_marriages 
        WHERE (player1_uuid = ? OR player2_uuid = ?)
        AND status IN ('casado', 'divorciado')
    """;

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
        }

        return 0;
    }

    /**
     * Obtiene la fecha del último divorcio de un jugador
     *
     * @param playerUuid UUID del jugador
     * @return Timestamp del último divorcio, null si no tiene
     * @throws SQLException Si hay error en la base de datos
     */
    public Timestamp getLastDivorceDate(java.util.UUID playerUuid) throws SQLException {
        String query = """
        SELECT divorce_date
        FROM marry_marriages 
        WHERE (player1_uuid = ? OR player2_uuid = ?)
        AND status = 'divorciado'
        AND divorce_date IS NOT NULL
        ORDER BY divorce_date DESC
        LIMIT 1
    """;

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setString(1, playerUuid.toString());
            stmt.setString(2, playerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getTimestamp("divorce_date");
                }
            }
        }

        return null;
    }

    /**
     * Verifica si un jugador puede divorciarse (sin cooldown activo)
     *
     * @param playerUuid   UUID del jugador
     * @param cooldownDays Días de cooldown entre divorcios
     * @return true si puede divorciarse
     * @throws SQLException Si hay error en la base de datos
     */
    public boolean canPlayerDivorce(java.util.UUID playerUuid, int cooldownDays) throws SQLException {
        if (cooldownDays <= 0) {
            return true; // Sin cooldown
        }

        Timestamp lastDivorce = getLastDivorceDate(playerUuid);
        if (lastDivorce == null) {
            return true; // Nunca se ha divorciado
        }

        long cooldownMs = cooldownDays * 24L * 60L * 60L * 1000L; // Convertir días a milisegundos
        long timeSinceLastDivorce = System.currentTimeMillis() - lastDivorce.getTime();

        return timeSinceLastDivorce >= cooldownMs;
    }

    /**
     * Obtiene información completa de un matrimonio
     *
     * @param marriageId ID del matrimonio
     * @return Map con toda la información del matrimonio
     * @throws SQLException Si hay error en la base de datos
     */
    public java.util.Map<String, Object> getCompleteMarriageInfo(int marriageId) throws SQLException {
        String query = """
        SELECT 
            m.*,
            p1.username as player1_name,
            p2.username as player2_name
        FROM marry_marriages m
        INNER JOIN marry_players p1 ON m.player1_uuid = p1.uuid
        INNER JOIN marry_players p2 ON m.player2_uuid = p2.uuid
        WHERE m.id = ?
    """;

        try (PreparedStatement stmt = getConnection().prepareStatement(query)) {
            stmt.setInt(1, marriageId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    java.util.Map<String, Object> info = new java.util.HashMap<>();

                    info.put("id", rs.getInt("id"));
                    info.put("player1_uuid", rs.getString("player1_uuid"));
                    info.put("player2_uuid", rs.getString("player2_uuid"));
                    info.put("player1_name", rs.getString("player1_name"));
                    info.put("player2_name", rs.getString("player2_name"));
                    info.put("status", rs.getString("status"));
                    info.put("engagement_date", rs.getTimestamp("engagement_date"));
                    info.put("wedding_date", rs.getTimestamp("wedding_date"));
                    info.put("ceremony_location", rs.getString("ceremony_location"));
                    info.put("divorce_date", rs.getTimestamp("divorce_date"));
                    info.put("created_at", rs.getTimestamp("created_at"));
                    info.put("updated_at", rs.getTimestamp("updated_at"));

                    return info;
                }
            }
        }

        return null;
    }

    /**
     * MÉTODO IMPLEMENTADO: Comando de administración para verificar y reparar datos
     */
    public void runMaintenanceCheck() throws SQLException {
        plugin.getLogger().info("=== INICIO DE VERIFICACIÓN DE MANTENIMIENTO ===");

        // 1. Verificar y corregir estados inconsistentes
        int fixedStates = validateAndFixData();
        plugin.getLogger().info("Estados corregidos: " + fixedStates);

        // 2. Limpiar referencias de parejas rotas
        int repairedReferences = repairDatabase();
        plugin.getLogger().info("Referencias reparadas: " + repairedReferences);

        // 3. Mostrar estadísticas finales
        int[] stats = getSystemStats();
        plugin.getLogger().info("Estado final - Total: " + stats[0] + ", Solteros: " + stats[1] +
                ", Comprometidos: " + stats[2] + ", Casados: " + stats[3]);

        plugin.getLogger().info("=== FIN DE VERIFICACIÓN DE MANTENIMIENTO ===");
    }
}