package gc.grivyzom.marryCore.placeholders;

import gc.grivyzom.marryCore.MarryCore;
import gc.grivyzom.marryCore.enums.MaritalStatus;
import gc.grivyzom.marryCore.models.MarryPlayer;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Expansion de PlaceholderAPI para MarryCore.
 * Proporciona placeholders relacionados con el sistema de matrimonio.
 * VERSIÓN CORREGIDA: Incluye métodos mejorados para obtener datos reales.
 *
 * @author Brocolitx
 * @version 0.0.1
 */
public class MarryCorePlaceholderExpansion extends PlaceholderExpansion {

    private final MarryCore plugin;

    // Cache para mejorar el rendimiento
    private final ConcurrentHashMap<String, PlaceholderCache> cache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = TimeUnit.MINUTES.toMillis(2); // Reducido a 2 minutos para datos más actuales

    public MarryCorePlaceholderExpansion(MarryCore plugin) {
        this.plugin = plugin;

        // Limpiar cache periódicamente
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanExpiredCache,
                20L * 60 * 2, 20L * 60 * 2); // Cada 2 minutos
    }

    @Override
    public @NotNull String getIdentifier() {
        return "marry";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Brocolitx";
    }

    @Override
    public @NotNull String getVersion() {
        return "0.0.1";
    }

    @Override
    public boolean persist() {
        return true; // Plugin no se desregistra al recargar
    }

    @Override
    public boolean canRegister() {
        return plugin != null && plugin.isEnabled();
    }

    @Override
    public @Nullable String onRequest(@NotNull OfflinePlayer player, @NotNull String params) {
        if (player == null || !player.isOnline()) {
            return null;
        }

        // Verificar cache solo para datos menos críticos
        String cacheKey = player.getUniqueId() + ":" + params;
        if (!isCriticalPlaceholder(params)) {
            PlaceholderCache cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpired()) {
                return cached.getValue();
            }
        }

        try {
            // CORRECCIÓN: Obtener datos actualizados y sincronizados
            MarryPlayer playerData = getUpdatedPlayerData(player.getUniqueId());
            String result = processPlaceholder(player, playerData, params);

            // Guardar en cache solo datos no críticos
            if (result != null && !isCriticalPlaceholder(params)) {
                cache.put(cacheKey, new PlaceholderCache(result, System.currentTimeMillis()));
            }

            return result;

        } catch (SQLException e) {
            plugin.getLogger().warning("Error al procesar placeholder '" + params + "' para " + player.getName() + ": " + e.getMessage());
            return "Error";
        }
    }

    /**
     * NUEVO MÉTODO: Determina si un placeholder es crítico y no debe usar cache
     */
    private boolean isCriticalPlaceholder(String params) {
        String lower = params.toLowerCase();
        return lower.equals("status") || lower.equals("name") || lower.equals("is_married") ||
                lower.equals("is_engaged") || lower.equals("is_single") || lower.equals("has_partner");
    }

    /**
     * NUEVO MÉTODO: Obtiene datos actualizados del jugador
     */
    private MarryPlayer getUpdatedPlayerData(UUID playerUuid) throws SQLException {
        // Primero sincronizar el estado si es necesario
        try {
            plugin.getDatabaseManager().synchronizePlayerStatus(playerUuid);
        } catch (Exception e) {
            // Si la sincronización falla, continuar con los datos existentes
            plugin.getLogger().warning("Error al sincronizar estado para placeholder: " + e.getMessage());
        }

        // Luego obtener los datos actualizados
        return plugin.getDatabaseManager().getPlayerData(playerUuid);
    }

    /**
     * MÉTODO CORREGIDO: Procesa placeholders con mejor precisión
     */
    private String processPlaceholder(OfflinePlayer player, MarryPlayer playerData, String params) throws SQLException {
        switch (params.toLowerCase()) {

            // CORREGIDO: Estado civil actual (obtiene el estado real)
            case "status":
                return getActualStatusDisplay(playerData);

            // Tiempo en el estado actual
            case "status_time":
                return getStatusTime(playerData);

            // CORREGIDO: Nombre del cónyuge/comprometido (verifica datos reales)
            case "name":
                return getActualPartnerName(playerData);

            // CORREGIDO: Fecha de casamiento (obtiene de base de datos)
            case "casamiento":
                return getActualWeddingDate(playerData);

            // CORREGIDO: Fecha de compromiso (obtiene de base de datos)
            case "compromiso":
                return getActualEngagementDate(playerData);

            // Estado con emoji
            case "status_emoji":
                return getStatusWithEmoji(getActualStatus(playerData));

            // CORREGIDO: Días casado (calcula desde fecha real de matrimonio)
            case "dias_casado":
                return getActualDaysMarried(playerData);

            // Días comprometido
            case "dias_comprometido":
                return getDaysEngaged(playerData);

            // Tiempo total en relación
            case "tiempo_relacion":
                return getTotalRelationshipTime(playerData);

            // Estado detallado
            case "estado_completo":
                return getCompleteStatus(playerData);

            // Aniversario próximo
            case "proximo_aniversario":
                return getNextAnniversary(playerData);

            // Días hasta aniversario
            case "dias_aniversario":
                return getDaysUntilAnniversary(playerData);

            // Estadísticas del servidor
            case "server_casados":
                return getServerMarriedCount();

            case "server_comprometidos":
                return getServerEngagedCount();

            case "server_solteros":
                return getServerSingleCount();

            // CORREGIDOS: Placeholders condicionales (usan estado real)
            case "is_single":
                return getActualStatus(playerData) == MaritalStatus.SOLTERO ? "true" : "false";

            case "is_engaged":
                return getActualStatus(playerData) == MaritalStatus.COMPROMETIDO ? "true" : "false";

            case "is_married":
                return getActualStatus(playerData) == MaritalStatus.CASADO ? "true" : "false";

            case "has_partner":
                return hasActualPartner(playerData) ? "true" : "false";

            // Placeholder de pareja online
            case "partner_online":
                return isPartnerOnline(playerData) ? "true" : "false";

            case "partner_world":
                return getPartnerWorld(playerData);

            // Estado de ceremonia
            case "ceremonia_estado":
                return getCeremonyStatus(playerData);

            // Fecha de ceremonia programada
            case "ceremonia_fecha":
                return getScheduledCeremonyDate(playerData);

            // Ubicación de ceremonia
            case "ceremonia_lugar":
                return getCeremonyLocation(playerData);

            // Número de invitados
            case "invitados_count":
                return getGuestCount(playerData);

            // Lista de invitados
            case "invitados_lista":
                return getGuestList(playerData);

            // Género de la pareja
            case "partner_gender":
                return getPartnerGender(playerData);

            // Compatibilidad
            case "compatibility_status":
                return getCompatibilityStatus(playerData);

            default:
                // Verificar si es un placeholder con parámetros
                return processParameterizedPlaceholder(player, playerData, params);
        }
    }

    // ========================================
    // MÉTODOS CORREGIDOS PARA OBTENER DATOS REALES
    // ========================================

    /**
     * NUEVO MÉTODO: Obtiene el estado real del jugador
     */
    private MaritalStatus getActualStatus(MarryPlayer playerData) {
        try {
            return plugin.getDatabaseManager().getActualMaritalStatus(playerData.getUuid());
        } catch (SQLException e) {
            plugin.getLogger().warning("Error al obtener estado real: " + e.getMessage());
            return playerData.getStatus();
        }
    }

    /**
     * NUEVO MÉTODO: Verifica si realmente tiene pareja activa
     */
    private boolean hasActualPartner(MarryPlayer playerData) {
        try {
            Map<String, Object> marriageInfo = plugin.getDatabaseManager().getActiveMarriageInfo(playerData.getUuid());
            return marriageInfo != null;
        } catch (SQLException e) {
            return playerData.hasPartner();
        }
    }

    /**
     * MÉTODO CORREGIDO: Obtiene display del estado real
     */
    private String getActualStatusDisplay(MarryPlayer playerData) {
        MaritalStatus actualStatus = getActualStatus(playerData);
        return actualStatus.getDisplayName();
    }

    /**
     * MÉTODO CORREGIDO: Obtiene nombre de pareja real
     */
    private String getActualPartnerName(MarryPlayer playerData) {
        try {
            Map<String, Object> marriageInfo = plugin.getDatabaseManager().getActiveMarriageInfo(playerData.getUuid());

            if (marriageInfo == null) {
                return "Soltero";
            }

            String player1Uuid = (String) marriageInfo.get("player1_uuid");
            String player2Uuid = (String) marriageInfo.get("player2_uuid");

            if (playerData.getUuid().toString().equals(player1Uuid)) {
                return (String) marriageInfo.get("player2_name");
            } else {
                return (String) marriageInfo.get("player1_name");
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("Error al obtener nombre de pareja: " + e.getMessage());
            return getPartnerNameFallback(playerData);
        }
    }

    /**
     * MÉTODO FALLBACK: Para obtener nombre de pareja si falla el método principal
     */
    private String getPartnerNameFallback(MarryPlayer playerData) {
        if (!playerData.hasPartner()) {
            return "Soltero";
        }

        try {
            MarryPlayer partner = plugin.getDatabaseManager().getPlayerData(playerData.getPartnerUuid());
            return partner.getUsername();
        } catch (SQLException ex) {
            return "Desconocido";
        }
    }

    /**
     * MÉTODO CORREGIDO: Obtiene fecha de matrimonio real
     */
    private String getActualWeddingDate(MarryPlayer playerData) {
        try {
            Timestamp weddingDate = plugin.getDatabaseManager().getActualWeddingDate(playerData.getUuid());

            if (weddingDate == null) {
                return "Sin fecha";
            }

            return formatDate(weddingDate.toLocalDateTime().toLocalDate());

        } catch (SQLException e) {
            plugin.getLogger().warning("Error al obtener fecha de matrimonio: " + e.getMessage());
            return "Error";
        }
    }

    /**
     * MÉTODO CORREGIDO: Obtiene fecha de compromiso real
     */
    private String getActualEngagementDate(MarryPlayer playerData) {
        try {
            Map<String, Object> marriageInfo = plugin.getDatabaseManager().getActiveMarriageInfo(playerData.getUuid());

            if (marriageInfo == null) {
                return "Sin fecha";
            }

            Timestamp engagementDate = (Timestamp) marriageInfo.get("engagement_date");

            if (engagementDate == null) {
                return "Sin fecha";
            }

            return formatDate(engagementDate.toLocalDateTime().toLocalDate());

        } catch (SQLException e) {
            plugin.getLogger().warning("Error al obtener fecha de compromiso: " + e.getMessage());
            return "Error";
        }
    }

    /**
     * MÉTODO CORREGIDO: Obtiene días casado real
     */
    private String getActualDaysMarried(MarryPlayer playerData) {
        try {
            Timestamp weddingDate = plugin.getDatabaseManager().getActualWeddingDate(playerData.getUuid());

            if (weddingDate == null) {
                return "0";
            }

            long days = ChronoUnit.DAYS.between(
                    weddingDate.toLocalDateTime().toLocalDate(),
                    LocalDate.now()
            );

            return String.valueOf(Math.max(0, days));

        } catch (SQLException e) {
            plugin.getLogger().warning("Error al calcular días casado: " + e.getMessage());
            return "0";
        }
    }

    // ========================================
    // MÉTODOS ORIGINALES MANTENIDOS
    // ========================================

    private String getStatusTime(MarryPlayer playerData) {
        if (playerData.getUpdatedAt() == null) {
            return "0s";
        }

        long diff = System.currentTimeMillis() - playerData.getUpdatedAt().getTime();
        return formatDuration(diff);
    }

    private String getStatusWithEmoji(MaritalStatus status) {
        switch (status) {
            case SOLTERO:
                return "💔 Soltero/a";
            case COMPROMETIDO:
                return "💍 Comprometido/a";
            case CASADO:
                return "💖 Casado/a";
            default:
                return status.getDisplayName();
        }
    }

    private String getDaysEngaged(MarryPlayer playerData) {
        if (playerData.getStatus() == MaritalStatus.SOLTERO) {
            return "0";
        }

        long days = ChronoUnit.DAYS.between(
                playerData.getUpdatedAt().toLocalDateTime().toLocalDate(),
                LocalDate.now()
        );

        return String.valueOf(Math.max(0, days));
    }

    private String getTotalRelationshipTime(MarryPlayer playerData) {
        if (playerData.getStatus() == MaritalStatus.SOLTERO) {
            return "0d";
        }

        LocalDateTime startDate = playerData.getCreatedAt().toLocalDateTime();
        if (playerData.getStatus() == MaritalStatus.COMPROMETIDO) {
            startDate = playerData.getUpdatedAt().toLocalDateTime();
        }

        long totalTime = System.currentTimeMillis() - Timestamp.valueOf(startDate).getTime();
        return formatDuration(totalTime);
    }

    private String getCompleteStatus(MarryPlayer playerData) {
        StringBuilder status = new StringBuilder();

        status.append(getStatusWithEmoji(getActualStatus(playerData)));

        if (hasActualPartner(playerData)) {
            String partnerName = getActualPartnerName(playerData);
            status.append(" con ").append(partnerName);

            MaritalStatus actualStatus = getActualStatus(playerData);
            if (actualStatus == MaritalStatus.CASADO) {
                String days = getActualDaysMarried(playerData);
                status.append(" (").append(days).append(" días casados)");
            } else if (actualStatus == MaritalStatus.COMPROMETIDO) {
                String days = getDaysEngaged(playerData);
                status.append(" (").append(days).append(" días comprometidos)");
            }
        }

        return status.toString();
    }

    private String getNextAnniversary(MarryPlayer playerData) {
        try {
            Timestamp weddingDate = plugin.getDatabaseManager().getActualWeddingDate(playerData.getUuid());

            if (weddingDate == null) {
                return "N/A";
            }

            LocalDate wedding = weddingDate.toLocalDateTime().toLocalDate();
            LocalDate now = LocalDate.now();

            LocalDate nextAnniversary = wedding.withYear(now.getYear());
            if (nextAnniversary.isBefore(now) || nextAnniversary.equals(now)) {
                nextAnniversary = nextAnniversary.withYear(now.getYear() + 1);
            }

            return formatDate(nextAnniversary);

        } catch (SQLException e) {
            return "Error";
        }
    }

    private String getDaysUntilAnniversary(MarryPlayer playerData) {
        try {
            Timestamp weddingDate = plugin.getDatabaseManager().getActualWeddingDate(playerData.getUuid());

            if (weddingDate == null) {
                return "N/A";
            }

            LocalDate wedding = weddingDate.toLocalDateTime().toLocalDate();
            LocalDate now = LocalDate.now();

            LocalDate nextAnniversary = wedding.withYear(now.getYear());
            if (nextAnniversary.isBefore(now) || nextAnniversary.equals(now)) {
                nextAnniversary = nextAnniversary.withYear(now.getYear() + 1);
            }

            long days = ChronoUnit.DAYS.between(now, nextAnniversary);
            return String.valueOf(days);

        } catch (SQLException e) {
            return "Error";
        }
    }

    private String getCeremonyStatus(MarryPlayer playerData) {
        if (getActualStatus(playerData) != MaritalStatus.COMPROMETIDO) {
            return "N/A";
        }

        try {
            Map<String, Object> marriageInfo = plugin.getDatabaseManager().getActiveMarriageInfo(playerData.getUuid());

            if (marriageInfo == null) {
                return "Sin programar";
            }

            // Verificar si hay fecha programada
            Timestamp ceremonyDate = (Timestamp) marriageInfo.get("wedding_date");

            if (ceremonyDate == null) {
                return "Sin programar";
            }

            LocalDateTime ceremony = ceremonyDate.toLocalDateTime();
            LocalDateTime now = LocalDateTime.now();

            if (ceremony.isBefore(now)) {
                return "Vencida";
            } else {
                return "Programada";
            }

        } catch (SQLException e) {
            return "Error";
        }
    }

    private String getScheduledCeremonyDate(MarryPlayer playerData) {
        if (getActualStatus(playerData) != MaritalStatus.COMPROMETIDO) {
            return "N/A";
        }

        try {
            Map<String, Object> marriageInfo = plugin.getDatabaseManager().getActiveMarriageInfo(playerData.getUuid());

            if (marriageInfo == null) {
                return "Sin fecha";
            }

            Timestamp ceremonyDate = (Timestamp) marriageInfo.get("wedding_date");

            if (ceremonyDate == null) {
                return "Sin fecha";
            }

            return formatDateTime(ceremonyDate.toLocalDateTime());

        } catch (SQLException e) {
            return "Error";
        }
    }

    private String getCeremonyLocation(MarryPlayer playerData) {
        try {
            Map<String, Object> marriageInfo = plugin.getDatabaseManager().getActiveMarriageInfo(playerData.getUuid());

            if (marriageInfo == null) {
                return "Sin ubicación";
            }

            String location = (String) marriageInfo.get("ceremony_location");
            return location != null ? location : "Sin ubicación";

        } catch (SQLException e) {
            return "Sin ubicación";
        }
    }

    private String getGuestCount(MarryPlayer playerData) {
        if (getActualStatus(playerData) != MaritalStatus.COMPROMETIDO) {
            return "0";
        }

        try {
            Map<String, Object> marriageInfo = plugin.getDatabaseManager().getActiveMarriageInfo(playerData.getUuid());

            if (marriageInfo == null) {
                return "0";
            }

            int marriageId = (int) marriageInfo.get("id");
            int count = plugin.getDatabaseManager().getConfirmedGuestsCount(marriageId);
            return String.valueOf(count);

        } catch (SQLException e) {
            return "0";
        }
    }

    private String getGuestList(MarryPlayer playerData) {
        // Implementar lista de invitados si es necesario
        return "Sin invitados";
    }

    private String getServerMarriedCount() {
        try {
            int[] stats = plugin.getDatabaseManager().getSystemStats();
            return String.valueOf(stats[3]); // casados
        } catch (SQLException e) {
            return "0";
        }
    }

    private String getServerEngagedCount() {
        try {
            int[] stats = plugin.getDatabaseManager().getSystemStats();
            return String.valueOf(stats[2]); // comprometidos
        } catch (SQLException e) {
            return "0";
        }
    }

    private String getServerSingleCount() {
        try {
            int[] stats = plugin.getDatabaseManager().getSystemStats();
            return String.valueOf(stats[1]); // solteros
        } catch (SQLException e) {
            return "0";
        }
    }

    private boolean isPartnerOnline(MarryPlayer playerData) {
        if (!hasActualPartner(playerData)) {
            return false;
        }

        try {
            Map<String, Object> marriageInfo = plugin.getDatabaseManager().getActiveMarriageInfo(playerData.getUuid());

            if (marriageInfo == null) {
                return false;
            }

            String player1Uuid = (String) marriageInfo.get("player1_uuid");
            String player2Uuid = (String) marriageInfo.get("player2_uuid");

            UUID partnerUuid;
            if (playerData.getUuid().toString().equals(player1Uuid)) {
                partnerUuid = UUID.fromString(player2Uuid);
            } else {
                partnerUuid = UUID.fromString(player1Uuid);
            }

            Player partner = Bukkit.getPlayer(partnerUuid);
            return partner != null && partner.isOnline();

        } catch (SQLException e) {
            return false;
        }
    }

    private String getPartnerWorld(MarryPlayer playerData) {
        if (!hasActualPartner(playerData)) {
            return "N/A";
        }

        try {
            Map<String, Object> marriageInfo = plugin.getDatabaseManager().getActiveMarriageInfo(playerData.getUuid());

            if (marriageInfo == null) {
                return "Offline";
            }

            String player1Uuid = (String) marriageInfo.get("player1_uuid");
            String player2Uuid = (String) marriageInfo.get("player2_uuid");

            UUID partnerUuid;
            if (playerData.getUuid().toString().equals(player1Uuid)) {
                partnerUuid = UUID.fromString(player2Uuid);
            } else {
                partnerUuid = UUID.fromString(player1Uuid);
            }

            Player partner = Bukkit.getPlayer(partnerUuid);
            if (partner == null) {
                return "Offline";
            }

            return partner.getWorld().getName();

        } catch (SQLException e) {
            return "Error";
        }
    }

    private String getPartnerGender(MarryPlayer playerData) {
        // Esta función requiere implementación adicional
        return "N/A";
    }

    private String getCompatibilityStatus(MarryPlayer playerData) {
        // Implementar sistema de compatibilidad si es necesario
        return "Compatible";
    }

    // ========================================
    // MÉTODOS CON PARÁMETROS
    // ========================================

    private String processParameterizedPlaceholder(OfflinePlayer player, MarryPlayer playerData, String params) throws SQLException {
        String[] parts = params.split("_", 2);

        if (parts.length < 2) {
            return null;
        }

        switch (parts[0].toLowerCase()) {
            case "format":
                return getCustomFormat(playerData, parts[1]);

            case "time":
                return getTimeInFormat(playerData, parts[1]);

            case "date":
                return getDateInFormat(playerData, parts[1]);

            case "stats":
                return getSpecificStats(parts[1]);

            default:
                return null;
        }
    }

    private String getCustomFormat(MarryPlayer playerData, String formatType) {
        MaritalStatus actualStatus = getActualStatus(playerData);

        switch (formatType.toLowerCase()) {
            case "short":
                return actualStatus.getDisplayName().substring(0, 1);
            case "color":
                return getColoredStatus(actualStatus);
            case "bracket":
                return "[" + actualStatus.getDisplayName() + "]";
            default:
                return actualStatus.getDisplayName();
        }
    }

    private String getTimeInFormat(MarryPlayer playerData, String format) {
        if (playerData.getUpdatedAt() == null) {
            return "0";
        }

        long diff = System.currentTimeMillis() - playerData.getUpdatedAt().getTime();

        switch (format.toLowerCase()) {
            case "seconds":
                return String.valueOf(TimeUnit.MILLISECONDS.toSeconds(diff));
            case "minutes":
                return String.valueOf(TimeUnit.MILLISECONDS.toMinutes(diff));
            case "hours":
                return String.valueOf(TimeUnit.MILLISECONDS.toHours(diff));
            case "days":
                return String.valueOf(TimeUnit.MILLISECONDS.toDays(diff));
            case "detailed":
                return formatDuration(diff);
            default:
                return formatDuration(diff);
        }
    }

    private String getDateInFormat(MarryPlayer playerData, String format) {
        if (playerData.getUpdatedAt() == null) {
            return "N/A";
        }

        LocalDate date = playerData.getUpdatedAt().toLocalDateTime().toLocalDate();

        switch (format.toLowerCase()) {
            case "short":
                return date.format(DateTimeFormatter.ofPattern("dd/MM/yy"));
            case "long":
                return date.format(DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy"));
            case "iso":
                return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            default:
                return formatDate(date);
        }
    }

    private String getSpecificStats(String statType) {
        try {
            int[] stats = plugin.getDatabaseManager().getSystemStats();

            switch (statType.toLowerCase()) {
                case "total":
                    return String.valueOf(stats[0]);
                case "single":
                    return String.valueOf(stats[1]);
                case "engaged":
                    return String.valueOf(stats[2]);
                case "married":
                    return String.valueOf(stats[3]);
                case "percentage_married":
                    if (stats[0] > 0) {
                        double percentage = (stats[3] * 100.0) / stats[0];
                        return String.format("%.1f%%", percentage);
                    }
                    return "0%";
                default:
                    return "0";
            }
        } catch (SQLException e) {
            return "Error";
        }
    }

// ========================================
// MÉTODOS AUXILIARES
// ========================================

    private String formatDuration(long milliseconds) {
        long days = TimeUnit.MILLISECONDS.toDays(milliseconds);
        long hours = TimeUnit.MILLISECONDS.toHours(milliseconds) % 24;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60;

        StringBuilder sb = new StringBuilder();

        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
    }
    if (seconds > 0 || sb.length() == 0) {
        sb.append(seconds).append("s");
    }

    return sb.toString().trim();
}

private String formatDate(LocalDate date) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
            plugin.getConfig().getString("time.date_format", "dd/MM/yyyy"));
    return date.format(formatter);
}

private String formatDateTime(LocalDateTime dateTime) {
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
            plugin.getConfig().getString("time.date_format", "dd/MM/yyyy") + " " +
                    plugin.getConfig().getString("time.time_format", "HH:mm"));
    return dateTime.format(formatter);
}

private String getColoredStatus(MaritalStatus status) {
    switch (status) {
        case SOLTERO:
            return "§7Soltero/a";
        case COMPROMETIDO:
            return "§bComprometido/a";
        case CASADO:
            return "§dCasado/a";
        default:
            return status.getDisplayName();
    }
}

private Timestamp getWeddingDateFromDB(int marriageId) throws SQLException {
    // Este método debe implementarse en DatabaseManager
    // Por ahora retorna null
    return null;
}

private Timestamp getCeremonyDateFromDB(int marriageId) throws SQLException {
    // Este método debe implementarse en DatabaseManager
    // Por ahora retorna null
    return null;
}

private void cleanExpiredCache() {
    long now = System.currentTimeMillis();
    cache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
}

// ========================================
// CLASE INTERNA PARA CACHE
// ========================================

private static class PlaceholderCache {
    private final String value;
    private final long timestamp;

    public PlaceholderCache(String value, long timestamp) {
        this.value = value;
        this.timestamp = timestamp;
    }

    public String getValue() {
        return value;
    }

    public boolean isExpired() {
        return isExpired(System.currentTimeMillis());
    }

    public boolean isExpired(long currentTime) {
        return (currentTime - timestamp) > CACHE_DURATION;
    }
}
}