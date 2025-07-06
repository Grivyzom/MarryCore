package gc.grivyzom.marryCore.utils;

import gc.grivyzom.marryCore.MarryCore;
import gc.grivyzom.marryCore.enums.MaritalStatus;
import gc.grivyzom.marryCore.models.MarryPlayer;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.concurrent.TimeUnit;

/**
 * Clase utilitaria para validaciones del sistema de matrimonio.
 * Centraliza todas las verificaciones y reglas de negocio.
 *
 * @author Brocolitx
 * @version 0.0.1
 */
public class ValidationUtils {

    private final MarryCore plugin;

    public ValidationUtils(MarryCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Valida si un jugador puede proponer matrimonio
     * @param player Jugador que quiere proponer
     * @return ValidationResult con el resultado de la validación
     */
    public ValidationResult canPropose(Player player) {
        try {
            MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

            // Verificar estado civil
            if (playerData.getStatus() != MaritalStatus.SOLTERO) {
                if (playerData.getStatus() == MaritalStatus.CASADO) {
                    return ValidationResult.fail("marriage.proposal.already-married");
                } else {
                    return ValidationResult.fail("marriage.proposal.already-engaged");
                }
            }

            // Verificar límite de propuestas diarias
            if (!canMakeMoreProposalsToday(player)) {
                return ValidationResult.fail("marriage.proposal.daily-limit-reached");
            }

            // Verificar tiempo de juego mínimo si está habilitado
            if (!hasMinimumPlaytime(player)) {
                int minHours = plugin.getConfig().getInt("security.anti_abuse.minimum_playtime_hours", 24);
                return ValidationResult.fail("marriage.proposal.insufficient-playtime",
                        "{hours}", String.valueOf(minHours));
            }

            return ValidationResult.success();

        } catch (SQLException e) {
            plugin.getLogger().severe("Error validando propuesta: " + e.getMessage());
            return ValidationResult.fail("general.database-error");
        }
    }

    /**
     * Valida si un jugador puede recibir propuestas
     * @param player Jugador objetivo
     * @return ValidationResult con el resultado de la validación
     */
    public ValidationResult canReceiveProposal(Player player) {
        try {
            MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

            // Verificar estado civil
            if (playerData.getStatus() != MaritalStatus.SOLTERO) {
                if (playerData.getStatus() == MaritalStatus.CASADO) {
                    return ValidationResult.fail("marriage.proposal.target-married",
                            "{player}", player.getName());
                } else {
                    return ValidationResult.fail("marriage.proposal.target-engaged",
                            "{player}", player.getName());
                }
            }

            return ValidationResult.success();

        } catch (SQLException e) {
            plugin.getLogger().severe("Error validando receptor: " + e.getMessage());
            return ValidationResult.fail("general.database-error");
        }
    }

    /**
     * Valida si una fecha de ceremonia es válida
     * @param dateString Fecha en formato string
     * @param timeString Hora en formato string (opcional)
     * @return ValidationResult con el resultado de la validación
     */
    public ValidationResult validateWeddingDate(String dateString, String timeString) {
        try {
            // Parsear fecha
            LocalDateTime dateTime;

            if (timeString != null && !timeString.isEmpty()) {
                String fullDateTime = dateString + " " + timeString;
                dateTime = LocalDateTime.parse(fullDateTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            } else {
                dateTime = LocalDateTime.parse(dateString + " 18:00", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            }

            LocalDateTime now = LocalDateTime.now();

            // Verificar que sea en el futuro
            int minAdvanceDays = plugin.getConfig().getInt("marriage.wedding.min_advance_days", 1);
            LocalDateTime minDate = now.plusDays(minAdvanceDays);

            if (dateTime.isBefore(minDate)) {
                return ValidationResult.fail("wedding.schedule.date-too-early",
                        "{days}", String.valueOf(minAdvanceDays));
            }

            // Verificar que no sea muy lejana
            int maxAdvanceDays = plugin.getConfig().getInt("marriage.wedding.max_advance_days", 30);
            LocalDateTime maxDate = now.plusDays(maxAdvanceDays);

            if (dateTime.isAfter(maxDate)) {
                return ValidationResult.fail("wedding.schedule.date-too-late",
                        "{days}", String.valueOf(maxAdvanceDays));
            }

            // Verificar que la fecha esté disponible
            Timestamp timestamp = Timestamp.valueOf(dateTime);
            if (!plugin.getDatabaseManager().isDateAvailable(timestamp)) {
                return ValidationResult.fail("wedding.schedule.date-occupied");
            }

            // Verificar horarios permitidos
            if (timeString != null && !isValidWeddingTime(timeString)) {
                return ValidationResult.fail("wedding.schedule.invalid-time");
            }

            return ValidationResult.success();

        } catch (DateTimeParseException e) {
            return ValidationResult.fail("wedding.schedule.invalid-date");
        } catch (SQLException e) {
            plugin.getLogger().severe("Error validando fecha: " + e.getMessage());
            return ValidationResult.fail("general.database-error");
        }
    }

    /**
     * Valida si se puede añadir un invitado
     * @param marriageId ID del matrimonio
     * @param guestPlayer Jugador a invitar
     * @return ValidationResult con el resultado de la validación
     */
    public ValidationResult canAddGuest(int marriageId, Player guestPlayer) {
        try {
            // Verificar si ya está invitado
            if (plugin.getDatabaseManager().isPlayerInvited(marriageId, guestPlayer.getUniqueId())) {
                return ValidationResult.fail("guests.add.already-invited",
                        "{player}", guestPlayer.getName());
            }

            // Verificar límite de invitados
            int confirmedGuests = plugin.getDatabaseManager().getConfirmedGuestsCount(marriageId);
            int maxGuests = plugin.getConfig().getInt("marriage.wedding.max_guests", 20);

            if (confirmedGuests >= maxGuests) {
                return ValidationResult.fail("guests.add.limit-reached",
                        "{max}", String.valueOf(maxGuests));
            }

            return ValidationResult.success();

        } catch (SQLException e) {
            plugin.getLogger().severe("Error validando invitado: " + e.getMessage());
            return ValidationResult.fail("general.database-error");
        }
    }

    /**
     * Valida distancia entre jugadores
     * @param player1 Primer jugador
     * @param player2 Segundo jugador
     * @return ValidationResult con el resultado de la validación
     */
    public ValidationResult validateDistance(Player player1, Player player2) {
        double maxDistance = plugin.getConfig().getDouble("marriage.proposal.max_distance", 10.0);
        double distance = player1.getLocation().distance(player2.getLocation());

        if (distance > maxDistance) {
            return ValidationResult.fail("marriage.proposal.too-far",
                    "{distance}", String.valueOf((int) maxDistance));
        }

        return ValidationResult.success();
    }

    /**
     * Verifica si un jugador puede hacer más propuestas hoy
     * @param player Jugador a verificar
     * @return true si puede hacer más propuestas
     */
    private boolean canMakeMoreProposalsToday(Player player) {
        // TODO: Implementar contador de propuestas diarias en base de datos
        // Por ahora retorna true
        return true;
    }

    /**
     * Verifica si un jugador tiene el tiempo de juego mínimo
     * @param player Jugador a verificar
     * @return true si tiene suficiente tiempo de juego
     */
    private boolean hasMinimumPlaytime(Player player) {
        if (!plugin.getConfig().getBoolean("security.anti_abuse.minimum_playtime_hours", false)) {
            return true; // Si no está habilitado, siempre permitir
        }

        int minHours = plugin.getConfig().getInt("security.anti_abuse.minimum_playtime_hours", 24);
        long minTicks = TimeUnit.HOURS.toSeconds(minHours) * 20; // Convertir a ticks

        return player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) >= minTicks;
    }

    /**
     * Verifica si una hora es válida para ceremonias
     * @param timeString Hora en formato HH:mm
     * @return true si es una hora válida
     */
    private boolean isValidWeddingTime(String timeString) {
        var validHours = plugin.getConfig().getStringList("time.ceremony_hours");

        if (validHours.isEmpty()) {
            return true; // Si no hay restricciones, permitir cualquier hora
        }

        return validHours.contains(timeString);
    }

    /**
     * Verifica si dos jugadores pueden casarse
     * @param player1 Primer jugador
     * @param player2 Segundo jugador
     * @return ValidationResult con el resultado de la validación
     */
    public ValidationResult canMarry(Player player1, Player player2) {
        try {
            MarryPlayer player1Data = plugin.getDatabaseManager().getPlayerData(player1.getUniqueId());
            MarryPlayer player2Data = plugin.getDatabaseManager().getPlayerData(player2.getUniqueId());

            // Verificar que ambos estén comprometidos
            if (player1Data.getStatus() != MaritalStatus.COMPROMETIDO) {
                return ValidationResult.fail("wedding.ceremony.not-engaged",
                        "{player}", player1.getName());
            }

            if (player2Data.getStatus() != MaritalStatus.COMPROMETIDO) {
                return ValidationResult.fail("wedding.ceremony.not-engaged",
                        "{player}", player2.getName());
            }

            // Verificar que estén comprometidos entre sí
            if (!player1Data.isPartnerOf(player2.getUniqueId()) ||
                    !player2Data.isPartnerOf(player1.getUniqueId())) {
                return ValidationResult.fail("wedding.ceremony.not-partners");
            }

            return ValidationResult.success();

        } catch (SQLException e) {
            plugin.getLogger().severe("Error validando matrimonio: " + e.getMessage());
            return ValidationResult.fail("general.database-error");
        }
    }

    /**
     * Verifica si un jugador puede divorciarse
     * @param player Jugador que quiere divorciarse
     * @return ValidationResult con el resultado de la validación
     */
    public ValidationResult canDivorce(Player player) {
        try {
            MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

            // Verificar que esté casado
            if (playerData.getStatus() != MaritalStatus.CASADO) {
                return ValidationResult.fail("divorce.not-married");
            }

            // Verificar cooldown de divorcio si está habilitado
            if (!canDivorceNow(player)) {
                int cooldownDays = plugin.getConfig().getInt("security.limits.divorce_cooldown_days", 7);
                return ValidationResult.fail("divorce.cooldown-active",
                        "{days}", String.valueOf(cooldownDays));
            }

            return ValidationResult.success();

        } catch (SQLException e) {
            plugin.getLogger().severe("Error validando divorcio: " + e.getMessage());
            return ValidationResult.fail("general.database-error");
        }
    }

    /**
     * Verifica si un jugador puede divorciarse ahora (sin cooldown)
     * @param player Jugador a verificar
     * @return true si puede divorciarse
     */
    private boolean canDivorceNow(Player player) {
        // TODO: Implementar verificación de cooldown de divorcio
        // Por ahora retorna true
        return true;
    }

    /**
     * Clase interna para representar resultados de validación
     */
    public static class ValidationResult {
        private final boolean success;
        private final String errorMessage;
        private final String[] replacements;

        private ValidationResult(boolean success, String errorMessage, String... replacements) {
            this.success = success;
            this.errorMessage = errorMessage;
            this.replacements = replacements;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult fail(String errorMessage, String... replacements) {
            return new ValidationResult(false, errorMessage, replacements);
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isFailure() {
            return !success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String[] getReplacements() {
            return replacements;
        }
    }
}