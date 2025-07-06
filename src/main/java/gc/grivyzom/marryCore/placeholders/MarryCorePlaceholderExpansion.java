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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Expansion de PlaceholderAPI para MarryCore.
 * Proporciona placeholders relacionados con el sistema de matrimonio.
 *
 * @author Brocolitx
 * @version 0.0.1
 */
public class MarryCorePlaceholderExpansion extends PlaceholderExpansion {

    private final MarryCore plugin;

    // Cache para mejorar el rendimiento
    private final ConcurrentHashMap<String, PlaceholderCache> cache = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = TimeUnit.MINUTES.toMillis(5); // 5 minutos

    public MarryCorePlaceholderExpansion(MarryCore plugin) {
        this.plugin = plugin;

        // Limpiar cache periódicamente
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanExpiredCache,
                20L * 60 * 5, 20L * 60 * 5); // Cada 5 minutos
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

        // Verificar cache
        String cacheKey = player.getUniqueId() + ":" + params;
        PlaceholderCache cached = cache.get(cacheKey);

        if (cached != null && !cached.isExpired()) {
            return cached.getValue();
        }

        try {
            // Obtener datos del jugador
            MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());
            String result = processPlaceholder(player, playerData, params);

            // Guardar en cache
            if (result != null) {
                cache.put(cacheKey, new PlaceholderCache(result, System.currentTimeMillis()));
            }

            return result;

        } catch (SQLException e) {
            plugin.getLogger().warning("Error al procesar placeholder '" + params + "' para " + player.getName() + ": " + e.getMessage());
            return "Error";
        }
    }

    /**
     * Procesa un placeholder específico
     */
    private String processPlaceholder(OfflinePlayer player, MarryPlayer playerData, String params) throws SQLException {
        switch (params.toLowerCase()) {

            // Estado civil actual
            case "status":
                return getStatusDisplay(playerData.getStatus());

            // Tiempo en el estado actual
            case "status_time":
                return getStatusTime(playerData);

            // Nombre del cónyuge/comprometido
            case "name":
                return getPartnerName(playerData);

            // Fecha de casamiento
            case "casamiento":
                return getWeddingDate(playerData);

            // Fecha de compromiso
            case "compromiso":
                return getEngagementDate(playerData);

            // Estado con emoji
            case "status_emoji":
                return getStatusWithEmoji(playerData.getStatus());

            // Días casado
            case "dias_casado":
                return getDaysMarried(playerData);

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

            // Género de la pareja (si está configurado)
            case "partner_gender":
                return getPartnerGender(playerData);

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

            // Lista de invitados (top 3)
            case "invitados_lista":
                return getGuestList(playerData);

            // Estadísticas del servidor
            case "server_casados":
                return getServerMarriedCount();

            case "server_comprometidos":
                return getServerEngagedCount();

            case "server_solteros":
                return getServerSingleCount();

            // Placeholders condicionales
            case "is_single":
                return playerData.getStatus() == MaritalStatus.SOLTERO ? "true" : "false";

            case "is_engaged":
                return playerData.getStatus() == MaritalStatus.COMPROMETIDO ? "true" : "false";

            case "is_married":
                return playerData.getStatus() == MaritalStatus.CASADO ? "true" : "false";

            case "has_partner":
                return playerData.hasPartner() ? "true" : "false";

            // Placeholder de pareja online
            case "partner_online":
                return isPartnerOnline(playerData) ? "true" : "false";

            case "partner_world":
                return getPartnerWorld(playerData);

            // Placeholders de compatibilidad con otros plugins
            case "compatibility_status":
                return getCompatibilityStatus(playerData);

            default:
                // Verificar si es un placeholder con parámetros
                return processParameterizedPlaceholder(player, playerData, params);
        }
    }

    /**
     * Procesa placeholders con parámetros
     */
    private String processParameterizedPlaceholder(OfflinePlayer player, MarryPlayer playerData, String params) throws SQLException {
        String[] parts = params.split("_", 2);

        if (parts.length < 2) {
            return null;
        }

        switch (parts[0].toLowerCase()) {
            case "format":
                // %marry_format_<tipo>% - Formatos personalizados
                return getCustomFormat(playerData, parts[1]);

            case "time":
                // %marry_time_<formato>% - Tiempos en diferentes formatos
                return getTimeInFormat(playerData, parts[1]);

            case "date":
                // %marry_date_<formato>% - Fechas en diferentes formatos
                return getDateInFormat(playerData, parts[1]);

            case "stats":
                // %marry_stats_<tipo>% - Estadísticas específicas
                return getSpecificStats(parts[1]);

            default:
                return null;
        }
    }

    // ========================================
    // MÉTODOS DE PROCESAMIENTO
    // ========================================

    private String getStatusDisplay(MaritalStatus status) {
        return status.getDisplayName();
    }

    private String getStatusTime(MarryPlayer playerData) {
        if (playerData.getUpdatedAt() == null) {
            return "0s";
        }

        long diff = System.currentTimeMillis() - playerData.getUpdatedAt().getTime();
        return formatDuration(diff);
    }

    private String getPartnerName(MarryPlayer playerData) {
        if (!playerData.hasPartner()) {
            return "Soltero";
        }

        try {
            MarryPlayer partner = plugin.getDatabaseManager().getPlayerData(playerData.getPartnerUuid());
            return partner.getUsername();
        } catch (SQLException e) {
            return "Desconocido";
        }
    }

    private String getWeddingDate(MarryPlayer playerData) {
        if (playerData.getStatus() != MaritalStatus.CASADO || !playerData.hasPartner()) {
            return "Sin fecha";
        }

        try {
            int marriageId = plugin.getDatabaseManager().getMarriageId(
                    playerData.getUuid(), playerData.getPartnerUuid());

            if (marriageId == -1) {
                return "Sin fecha";
            }

            // Usar el nuevo método del DatabaseManager
            Timestamp weddingDate = plugin.getDatabaseManager().getWeddingDateByMarriageId(marriageId);

            if (weddingDate == null) {
                return "Sin fecha";
            }

            return formatDate(weddingDate.toLocalDateTime().toLocalDate());

        } catch (SQLException e) {
            return "Error";
        }
    }

    private String getEngagementDate(MarryPlayer playerData) {
        if (playerData.getStatus() == MaritalStatus.SOLTERO) {
            return "Sin fecha";
        }

        try {
            int marriageId = plugin.getDatabaseManager().getMarriageId(
                    playerData.getUuid(), playerData.getPartnerUuid());

            if (marriageId == -1) {
                return formatDate(playerData.getUpdatedAt().toLocalDateTime().toLocalDate());
            }

            // Usar el nuevo método del DatabaseManager
            Timestamp engagementDate = plugin.getDatabaseManager().getEngagementDateByMarriageId(marriageId);

            if (engagementDate == null) {
                return formatDate(playerData.getUpdatedAt().toLocalDateTime().toLocalDate());
            }

            return formatDate(engagementDate.toLocalDateTime().toLocalDate());

        } catch (SQLException e) {
            return "Error";
        }
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

    private String getDaysMarried(MarryPlayer playerData) {
        if (playerData.getStatus() != MaritalStatus.CASADO) {
            return "0";
        }

        try {
            int marriageId = plugin.getDatabaseManager().getMarriageId(
                    playerData.getUuid(), playerData.getPartnerUuid());

            Timestamp weddingDate = plugin.getDatabaseManager().getWeddingDateByMarriageId(marriageId);

            if (weddingDate == null) {
                return "0";
            }

            long days = ChronoUnit.DAYS.between(
                    weddingDate.toLocalDateTime().toLocalDate(),
                    LocalDate.now()
            );

            return String.valueOf(Math.max(0, days));

        } catch (SQLException e) {
            return "0";
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

        status.append(getStatusWithEmoji(playerData.getStatus()));

        if (playerData.hasPartner()) {
            String partnerName = getPartnerName(playerData);
            status.append(" con ").append(partnerName);

            if (playerData.getStatus() == MaritalStatus.CASADO) {
                String days = getDaysMarried(playerData);
                status.append(" (").append(days).append(" días casados)");
            } else if (playerData.getStatus() == MaritalStatus.COMPROMETIDO) {
                String days = getDaysEngaged(playerData);
                status.append(" (").append(days).append(" días comprometidos)");
            }
        }

        return status.toString();
    }

    private String getNextAnniversary(MarryPlayer playerData) {
        if (playerData.getStatus() != MaritalStatus.CASADO) {
            return "N/A";
        }

        try {
            int marriageId = plugin.getDatabaseManager().getMarriageId(
                    playerData.getUuid(), playerData.getPartnerUuid());

            Timestamp weddingDate = plugin.getDatabaseManager().getWeddingDateByMarriageId(marriageId);

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
        if (playerData.getStatus() != MaritalStatus.CASADO) {
            return "N/A";
        }

        try {
            int marriageId = plugin.getDatabaseManager().getMarriageId(
                    playerData.getUuid(), playerData.getPartnerUuid());

            Timestamp weddingDate = plugin.getDatabaseManager().getWeddingDateByMarriageId(marriageId);

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

    private String getPartnerGender(MarryPlayer playerData) {
        // Esta función requerirá implementación adicional
        // según cómo quieras manejar el género de los jugadores
        return "N/A";
    }

private String getCeremonyStatus(MarryPlayer playerData) {
    if (playerData.getStatus() != MaritalStatus.COMPROMETIDO) {
        return "N/A";
    }

    try {
        int marriageId = plugin.getDatabaseManager().getMarriageId(
                playerData.getUuid(), playerData.getPartnerUuid());

        if (marriageId == -1) {
            return "Sin programar";
        }

        // Verificar si hay fecha programada
        Timestamp ceremonyDate = getCeremonyDateFromDB(marriageId);

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
    if (playerData.getStatus() != MaritalStatus.COMPROMETIDO) {
        return "N/A";
    }

    try {
        int marriageId = plugin.getDatabaseManager().getMarriageId(
                playerData.getUuid(), playerData.getPartnerUuid());

        Timestamp ceremonyDate = getCeremonyDateFromDB(marriageId);

        if (ceremonyDate == null) {
            return "Sin fecha";
        }

        return formatDateTime(ceremonyDate.toLocalDateTime());

    } catch (SQLException e) {
        return "Error";
    }
}

private String getCeremonyLocation(MarryPlayer playerData) {
    // Implementar obtención de ubicación de ceremonia
    return "Sin ubicación";
}

private String getGuestCount(MarryPlayer playerData) {
    if (playerData.getStatus() != MaritalStatus.COMPROMETIDO) {
        return "0";
    }

    try {
        int marriageId = plugin.getDatabaseManager().getMarriageId(
                playerData.getUuid(), playerData.getPartnerUuid());

        if (marriageId == -1) {
            return "0";
        }

        int count = plugin.getDatabaseManager().getConfirmedGuestsCount(marriageId);
        return String.valueOf(count);

    } catch (SQLException e) {
        return "0";
    }
}

private String getGuestList(MarryPlayer playerData) {
    // Implementar lista de invitados
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
    if (!playerData.hasPartner()) {
        return false;
    }

    Player partner = Bukkit.getPlayer(playerData.getPartnerUuid());
    return partner != null && partner.isOnline();
}

private String getPartnerWorld(MarryPlayer playerData) {
    if (!playerData.hasPartner()) {
        return "N/A";
    }

    Player partner = Bukkit.getPlayer(playerData.getPartnerUuid());
    if (partner == null) {
        return "Offline";
    }

    return partner.getWorld().getName();
}

private String getCompatibilityStatus(MarryPlayer playerData) {
    // Implementar sistema de compatibilidad si es necesario
    return "Compatible";
}

// ========================================
// MÉTODOS CON PARÁMETROS
// ========================================

private String getCustomFormat(MarryPlayer playerData, String formatType) {
    switch (formatType.toLowerCase()) {
        case "short":
            return getStatusDisplay(playerData.getStatus()).substring(0, 1);
        case "color":
            return getColoredStatus(playerData.getStatus());
        case "bracket":
            return "[" + getStatusDisplay(playerData.getStatus()) + "]";
        default:
            return getStatusDisplay(playerData.getStatus());
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