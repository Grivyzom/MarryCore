package gc.grivyzom.marryCore.placeholders;

import gc.grivyzom.marryCore.MarryCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

/**
 * Gestor de placeholders para MarryCore.
 * Se encarga de registrar y manejar la integración con PlaceholderAPI.
 *
 * @author Brocolitx
 * @version 0.0.1
 */
public class PlaceholderManager {

    private final MarryCore plugin;
    private MarryCorePlaceholderExpansion expansion;
    private boolean placeholderAPIEnabled = false;

    public PlaceholderManager(MarryCore plugin) {
        this.plugin = plugin;
        checkPlaceholderAPI();
    }

    /**
     * Verifica si PlaceholderAPI está disponible y lo registra
     */
    private void checkPlaceholderAPI() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                expansion = new MarryCorePlaceholderExpansion(plugin);

                if (expansion.register()) {
                    placeholderAPIEnabled = true;
                    plugin.getLogger().info(ChatColor.GREEN + "PlaceholderAPI detectado y registrado correctamente.");
                    logAvailablePlaceholders();
                } else {
                    plugin.getLogger().warning("No se pudo registrar la expansión de PlaceholderAPI.");
                }

            } catch (Exception e) {
                plugin.getLogger().severe("Error al registrar PlaceholderAPI: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            plugin.getLogger().info("PlaceholderAPI no detectado. Los placeholders no estarán disponibles.");
        }
    }

    /**
     * Muestra en consola todos los placeholders disponibles
     */
    private void logAvailablePlaceholders() {
        plugin.getLogger().info("========================================");
        plugin.getLogger().info("PLACEHOLDERS DISPONIBLES DE MARRYCORE:");
        plugin.getLogger().info("========================================");

        plugin.getLogger().info("BÁSICOS:");
        plugin.getLogger().info("  %marry_status% - Estado civil actual");
        plugin.getLogger().info("  %marry_status_time% - Tiempo en el estado actual");
        plugin.getLogger().info("  %marry_name% - Nombre del cónyuge/comprometido");
        plugin.getLogger().info("  %marry_casamiento% - Fecha de casamiento");
        plugin.getLogger().info("  %marry_compromiso% - Fecha de compromiso");

        plugin.getLogger().info("CON EMOJIS Y FORMATOS:");
        plugin.getLogger().info("  %marry_status_emoji% - Estado con emoji");
        plugin.getLogger().info("  %marry_estado_completo% - Estado detallado completo");

        plugin.getLogger().info("TIEMPOS Y FECHAS:");
        plugin.getLogger().info("  %marry_dias_casado% - Días casado");
        plugin.getLogger().info("  %marry_dias_comprometido% - Días comprometido");
        plugin.getLogger().info("  %marry_tiempo_relacion% - Tiempo total en relación");
        plugin.getLogger().info("  %marry_proximo_aniversario% - Próximo aniversario");
        plugin.getLogger().info("  %marry_dias_aniversario% - Días hasta aniversario");

        plugin.getLogger().info("CEREMONIA:");
        plugin.getLogger().info("  %marry_ceremonia_estado% - Estado de la ceremonia");
        plugin.getLogger().info("  %marry_ceremonia_fecha% - Fecha de ceremonia programada");
        plugin.getLogger().info("  %marry_ceremonia_lugar% - Ubicación de ceremonia");
        plugin.getLogger().info("  %marry_invitados_count% - Número de invitados");
        plugin.getLogger().info("  %marry_invitados_lista% - Lista de invitados");

        plugin.getLogger().info("ESTADÍSTICAS DEL SERVIDOR:");
        plugin.getLogger().info("  %marry_server_casados% - Jugadores casados en el servidor");
        plugin.getLogger().info("  %marry_server_comprometidos% - Jugadores comprometidos");
        plugin.getLogger().info("  %marry_server_solteros% - Jugadores solteros");

        plugin.getLogger().info("CONDICIONALES (true/false):");
        plugin.getLogger().info("  %marry_is_single% - Si está soltero");
        plugin.getLogger().info("  %marry_is_engaged% - Si está comprometido");
        plugin.getLogger().info("  %marry_is_married% - Si está casado");
        plugin.getLogger().info("  %marry_has_partner% - Si tiene pareja");
        plugin.getLogger().info("  %marry_partner_online% - Si la pareja está online");

        plugin.getLogger().info("INFORMACIÓN DE PAREJA:");
        plugin.getLogger().info("  %marry_partner_world% - Mundo donde está la pareja");
        plugin.getLogger().info("  %marry_partner_gender% - Género de la pareja");
        plugin.getLogger().info("  %marry_compatibility_status% - Estado de compatibilidad");

        plugin.getLogger().info("PLACEHOLDERS CON PARÁMETROS:");
        plugin.getLogger().info("  %marry_format_<tipo>% - Formatos personalizados");
        plugin.getLogger().info("    Tipos: short, color, bracket");
        plugin.getLogger().info("  %marry_time_<formato>% - Tiempos en diferentes formatos");
        plugin.getLogger().info("    Formatos: seconds, minutes, hours, days, detailed");
        plugin.getLogger().info("  %marry_date_<formato>% - Fechas en diferentes formatos");
        plugin.getLogger().info("    Formatos: short, long, iso");
        plugin.getLogger().info("  %marry_stats_<tipo>% - Estadísticas específicas");
        plugin.getLogger().info("    Tipos: total, single, engaged, married, percentage_married");

        plugin.getLogger().info("========================================");
    }

    /**
     * Verifica si PlaceholderAPI está habilitado
     */
    public boolean isPlaceholderAPIEnabled() {
        return placeholderAPIEnabled;
    }

    /**
     * Recarga los placeholders
     */
    public void reload() {
        if (placeholderAPIEnabled && expansion != null) {
            expansion.unregister();
        }
        checkPlaceholderAPI();
    }

    /**
     * Desregistra los placeholders al deshabilitar el plugin
     */
    public void disable() {
        if (placeholderAPIEnabled && expansion != null) {
            expansion.unregister();
            plugin.getLogger().info("Placeholders de MarryCore desregistrados.");
        }
    }

    /**
     * Reemplaza placeholders en un texto manualmente (fallback si PlaceholderAPI no está disponible)
     */
    public String replacePlaceholders(org.bukkit.entity.Player player, String text) {
        if (!placeholderAPIEnabled) {
            return replaceManualPlaceholders(player, text);
        }

        // Si PlaceholderAPI está disponible, usar su sistema
        try {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        } catch (Exception e) {
            plugin.getLogger().warning("Error al reemplazar placeholders: " + e.getMessage());
            return replaceManualPlaceholders(player, text);
        }
    }

    /**
     * Reemplaza placeholders manualmente cuando PlaceholderAPI no está disponible
     */
    private String replaceManualPlaceholders(org.bukkit.entity.Player player, String text) {
        if (player == null || text == null || !text.contains("%marry_")) {
            return text;
        }

        try {
            gc.grivyzom.marryCore.models.MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

            // Reemplazos básicos más comunes
            text = text.replace("%marry_status%", playerData.getStatus().getDisplayName());
            text = text.replace("%marry_name%", getPartnerNameSafe(playerData));
            text = text.replace("%marry_is_single%", playerData.getStatus() == gc.grivyzom.marryCore.enums.MaritalStatus.SOLTERO ? "true" : "false");
            text = text.replace("%marry_is_engaged%", playerData.getStatus() == gc.grivyzom.marryCore.enums.MaritalStatus.COMPROMETIDO ? "true" : "false");
            text = text.replace("%marry_is_married%", playerData.getStatus() == gc.grivyzom.marryCore.enums.MaritalStatus.CASADO ? "true" : "false");
            text = text.replace("%marry_has_partner%", playerData.hasPartner() ? "true" : "false");

            // Más reemplazos se pueden añadir aquí según sea necesario

        } catch (Exception e) {
            plugin.getLogger().warning("Error en reemplazo manual de placeholders: " + e.getMessage());
        }

        return text;
    }

    /**
     * Obtiene el nombre de la pareja de forma segura
     */
    private String getPartnerNameSafe(gc.grivyzom.marryCore.models.MarryPlayer playerData) {
        if (!playerData.hasPartner()) {
            return "Soltero";
        }

        try {
            gc.grivyzom.marryCore.models.MarryPlayer partner = plugin.getDatabaseManager().getPlayerData(playerData.getPartnerUuid());
            return partner.getUsername();
        } catch (Exception e) {
            return "Desconocido";
        }
    }

    /**
     * Obtiene información de debug sobre placeholders
     */
    public String getDebugInfo() {
        StringBuilder info = new StringBuilder();
        info.append("PlaceholderAPI Habilitado: ").append(placeholderAPIEnabled).append("\n");

        if (placeholderAPIEnabled && expansion != null) {
            info.append("Expansión Registrada: ").append(expansion.isRegistered()).append("\n");
            info.append("Identificador: ").append(expansion.getIdentifier()).append("\n");
            info.append("Versión: ").append(expansion.getVersion()).append("\n");
            info.append("Autor: ").append(expansion.getAuthor()).append("\n");
        }

        return info.toString();
    }

    /**
     * Verifica si un placeholder específico está disponible
     */
    public boolean isPlaceholderAvailable(String placeholder) {
        return placeholderAPIEnabled && placeholder.startsWith("%marry_") && placeholder.endsWith("%");
    }

    /**
     * Lista todos los placeholders disponibles como array
     */
    public String[] getAvailablePlaceholders() {
        return new String[]{
                // Básicos
                "%marry_status%",
                "%marry_status_time%",
                "%marry_name%",
                "%marry_casamiento%",
                "%marry_compromiso%",

                // Con emojis y formatos
                "%marry_status_emoji%",
                "%marry_estado_completo%",

                // Tiempos y fechas
                "%marry_dias_casado%",
                "%marry_dias_comprometido%",
                "%marry_tiempo_relacion%",
                "%marry_proximo_aniversario%",
                "%marry_dias_aniversario%",

                // Ceremonia
                "%marry_ceremonia_estado%",
                "%marry_ceremonia_fecha%",
                "%marry_ceremonia_lugar%",
                "%marry_invitados_count%",
                "%marry_invitados_lista%",

                // Estadísticas del servidor
                "%marry_server_casados%",
                "%marry_server_comprometidos%",
                "%marry_server_solteros%",

                // Condicionales
                "%marry_is_single%",
                "%marry_is_engaged%",
                "%marry_is_married%",
                "%marry_has_partner%",
                "%marry_partner_online%",

                // Información de pareja
                "%marry_partner_world%",
                "%marry_partner_gender%",
                "%marry_compatibility_status%",

                // Con parámetros (ejemplos)
                "%marry_format_short%",
                "%marry_format_color%",
                "%marry_format_bracket%",
                "%marry_time_seconds%",
                "%marry_time_minutes%",
                "%marry_time_hours%",
                "%marry_time_days%",
                "%marry_time_detailed%",
                "%marry_date_short%",
                "%marry_date_long%",
                "%marry_date_iso%",
                "%marry_stats_total%",
                "%marry_stats_single%",
                "%marry_stats_engaged%",
                "%marry_stats_married%",
                "%marry_stats_percentage_married%"
        };
    }

    /**
     * Obtiene ayuda sobre un placeholder específico
     */
    public String getPlaceholderHelp(String placeholder) {
        switch (placeholder.toLowerCase()) {
            case "%marry_status%":
                return "Muestra el estado civil actual (Soltero/a, Comprometido/a, Casado/a)";
            case "%marry_status_time%":
                return "Muestra cuánto tiempo llevas en tu estado actual (formato: XdXhXmXs)";
            case "%marry_name%":
                return "Muestra el nombre de tu cónyuge o comprometido ('Soltero' si no tienes pareja)";
            case "%marry_casamiento%":
                return "Muestra la fecha de tu casamiento ('Sin fecha' si no estás casado)";
            case "%marry_compromiso%":
                return "Muestra la fecha de tu compromiso";
            case "%marry_status_emoji%":
                return "Muestra tu estado civil con emoji (💔 💍 💖)";
            case "%marry_dias_casado%":
                return "Número de días que llevas casado";
            case "%marry_dias_comprometido%":
                return "Número de días que llevas comprometido";
            case "%marry_partner_online%":
                return "true/false - Si tu pareja está conectada";
            case "%marry_server_casados%":
                return "Número total de jugadores casados en el servidor";
            // Agregar más ayudas según sea necesario
            default:
                return "Placeholder no reconocido o sin ayuda disponible";
        }
    }

    /**
     * Comando para mostrar información de placeholders a un jugador
     */
    public void showPlaceholderInfo(org.bukkit.command.CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "========== PLACEHOLDERS DE MARRYCORE ==========");
        sender.sendMessage(ChatColor.YELLOW + "Estado: " + ChatColor.WHITE + (placeholderAPIEnabled ? "✓ Habilitado" : "✗ Deshabilitado"));

        if (!placeholderAPIEnabled) {
            sender.sendMessage(ChatColor.RED + "PlaceholderAPI no está instalado o habilitado.");
            sender.sendMessage(ChatColor.YELLOW + "Instala PlaceholderAPI para usar todos los placeholders.");
            return;
        }

        sender.sendMessage(ChatColor.GREEN + "Placeholders más usados:");
        sender.sendMessage(ChatColor.AQUA + "• %marry_status% " + ChatColor.GRAY + "- Tu estado civil");
        sender.sendMessage(ChatColor.AQUA + "• %marry_name% " + ChatColor.GRAY + "- Nombre de tu pareja");
        sender.sendMessage(ChatColor.AQUA + "• %marry_casamiento% " + ChatColor.GRAY + "- Fecha de boda");
        sender.sendMessage(ChatColor.AQUA + "• %marry_dias_casado% " + ChatColor.GRAY + "- Días casado");

        sender.sendMessage(ChatColor.YELLOW + "Total de placeholders disponibles: " + ChatColor.WHITE + getAvailablePlaceholders().length);
        sender.sendMessage(ChatColor.GRAY + "Usa '/marrycore placeholders list' para ver todos.");
        sender.sendMessage(ChatColor.GOLD + "===============================================");
    }

    /**
     * Comando para listar todos los placeholders
     */
    public void listAllPlaceholders(org.bukkit.command.CommandSender sender, int page) {
        String[] placeholders = getAvailablePlaceholders();
        int itemsPerPage = 10;
        int totalPages = (int) Math.ceil((double) placeholders.length / itemsPerPage);

        if (page < 1 || page > totalPages) {
            sender.sendMessage(ChatColor.RED + "Página inválida. Usa entre 1 y " + totalPages);
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "===== PLACEHOLDERS DE MARRYCORE (Página " + page + "/" + totalPages + ") =====");

        int start = (page - 1) * itemsPerPage;
        int end = Math.min(start + itemsPerPage, placeholders.length);

        for (int i = start; i < end; i++) {
            String placeholder = placeholders[i];
            sender.sendMessage(ChatColor.AQUA + placeholder + ChatColor.GRAY + " - " + getPlaceholderHelp(placeholder));
        }

        if (page < totalPages) {
            sender.sendMessage(ChatColor.YELLOW + "Usa '/marrycore placeholders list " + (page + 1) + "' para la siguiente página.");
        }

        sender.sendMessage(ChatColor.GOLD + "================================================");
    }
}