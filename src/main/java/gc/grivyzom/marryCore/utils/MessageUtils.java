package gc.grivyzom.marryCore.utils;

import gc.grivyzom.marryCore.MarryCore;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Clase utilitaria para manejar mensajes del plugin.
 * Se encarga de cargar, formatear y enviar mensajes desde messages.yml
 *
 * @author Brocolitx
 * @version 0.0.1
 */
public class MessageUtils {

    private final MarryCore plugin;
    private FileConfiguration messagesConfig;
    private String prefix;

    public MessageUtils(MarryCore plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    /**
     * Carga el archivo de mensajes
     */
    private void loadMessages() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");

        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        prefix = ChatColor.translateAlternateColorCodes('&',
                messagesConfig.getString("prefix", "&8[&cMarryCore&8] &r"));
    }

    /**
     * Recarga el archivo de mensajes
     */
    public void reloadMessages() {
        loadMessages();
    }

    /**
     * Obtiene un mensaje del archivo de configuración
     * @param path Ruta del mensaje en el archivo
     * @return Mensaje formateado
     */
    public String getMessage(String path) {
        String message = messagesConfig.getString(path);
        if (message == null) {
            plugin.getLogger().warning("Mensaje no encontrado: " + path);
            return "&cMensaje no encontrado: " + path;
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Obtiene un mensaje con reemplazos de placeholders
     * @param path Ruta del mensaje
     * @param replacements Pares de placeholder-valor
     * @return Mensaje formateado con reemplazos
     */
    public String getMessage(String path, String... replacements) {
        String message = getMessage(path);

        if (replacements.length % 2 != 0) {
            plugin.getLogger().warning("Número impar de reemplazos para: " + path);
            return message;
        }

        for (int i = 0; i < replacements.length; i += 2) {
            String placeholder = replacements[i];
            String value = replacements[i + 1];
            message = message.replace(placeholder, value);
        }

        return message;
    }

    /**
     * Envía un mensaje a un jugador
     * @param player Jugador destinatario
     * @param path Ruta del mensaje
     */
    public void sendMessage(Player player, String path) {
        String message = getMessage(path);
        player.sendMessage(prefix + message);
    }

    /**
     * Envía un mensaje a un jugador con reemplazos
     * @param player Jugador destinatario
     * @param path Ruta del mensaje
     * @param replacements Pares de placeholder-valor
     */
    public void sendMessage(Player player, String path, String... replacements) {
        String message = getMessage(path, replacements);
        player.sendMessage(prefix + message);
    }

    /**
     * Envía un mensaje a un CommandSender
     * @param sender CommandSender destinatario
     * @param path Ruta del mensaje
     */
    public void sendMessage(CommandSender sender, String path) {
        String message = getMessage(path);
        sender.sendMessage(prefix + message);
    }

    /**
     * Envía un mensaje a un CommandSender con reemplazos
     * @param sender CommandSender destinatario
     * @param path Ruta del mensaje
     * @param replacements Pares de placeholder-valor
     */
    public void sendMessage(CommandSender sender, String path, String... replacements) {
        String message = getMessage(path, replacements);
        sender.sendMessage(prefix + message);
    }

    /**
     * Envía un mensaje sin prefijo
     * @param player Jugador destinatario
     * @param path Ruta del mensaje
     */
    public void sendMessageWithoutPrefix(Player player, String path) {
        String message = getMessage(path);
        player.sendMessage(message);
    }

    /**
     * Envía un mensaje sin prefijo con reemplazos
     * @param player Jugador destinatario
     * @param path Ruta del mensaje
     * @param replacements Pares de placeholder-valor
     */
    public void sendMessageWithoutPrefix(Player player, String path, String... replacements) {
        String message = getMessage(path, replacements);
        player.sendMessage(message);
    }

    /**
     * Envía un mensaje broadcast a todos los jugadores online
     * @param path Ruta del mensaje
     */
    public void broadcastMessage(String path) {
        String message = prefix + getMessage(path);
        Bukkit.broadcastMessage(message);
    }

    /**
     * Envía un mensaje broadcast con reemplazos
     * @param path Ruta del mensaje
     * @param replacements Pares de placeholder-valor
     */
    public void broadcastMessage(String path, String... replacements) {
        String message = prefix + getMessage(path, replacements);
        Bukkit.broadcastMessage(message);
    }

    /**
     * Envía múltiples líneas de mensaje
     * @param player Jugador destinatario
     * @param path Ruta base del mensaje
     */
    public void sendMultilineMessage(Player player, String path) {
        List<String> lines = messagesConfig.getStringList(path);

        if (lines.isEmpty()) {
            // Si no es una lista, intentar como string simple
            sendMessage(player, path);
            return;
        }

        for (String line : lines) {
            String formattedLine = ChatColor.translateAlternateColorCodes('&', line);
            player.sendMessage(formattedLine);
        }
    }

    /**
     * Envía múltiples líneas con reemplazos
     * @param player Jugador destinatario
     * @param path Ruta base del mensaje
     * @param replacements Pares de placeholder-valor
     */
    public void sendMultilineMessage(Player player, String path, String... replacements) {
        List<String> lines = messagesConfig.getStringList(path);

        if (lines.isEmpty()) {
            sendMessage(player, path, replacements);
            return;
        }

        for (String line : lines) {
            String formattedLine = ChatColor.translateAlternateColorCodes('&', line);

            // Aplicar reemplazos
            for (int i = 0; i < replacements.length; i += 2) {
                String placeholder = replacements[i];
                String value = replacements[i + 1];
                formattedLine = formattedLine.replace(placeholder, value);
            }

            player.sendMessage(formattedLine);
        }
    }

    /**
     * Envía un mensaje de título al jugador
     * @param player Jugador destinatario
     * @param titlePath Ruta del título
     * @param subtitlePath Ruta del subtítulo
     */
    public void sendTitle(Player player, String titlePath, String subtitlePath) {
        String title = getMessage(titlePath);
        String subtitle = getMessage(subtitlePath);

        try {
            // Intentar usar el método moderno primero
            player.sendTitle(title, subtitle, 10, 70, 20);
        } catch (Exception e) {
            // Si falla, usar reflexión para versiones más antiguas
            sendTitleReflection(player, title, subtitle, 10, 70, 20);
        }
    }

    /**
     * Envía un mensaje de título con reemplazos
     * @param player Jugador destinatario
     * @param titlePath Ruta del título
     * @param subtitlePath Ruta del subtítulo
     * @param replacements Pares de placeholder-valor
     */
    public void sendTitle(Player player, String titlePath, String subtitlePath, String... replacements) {
        String title = getMessage(titlePath, replacements);
        String subtitle = getMessage(subtitlePath, replacements);

        try {
            player.sendTitle(title, subtitle, 10, 70, 20);
        } catch (Exception e) {
            sendTitleReflection(player, title, subtitle, 10, 70, 20);
        }
    }

    /**
     * Envía un título usando reflexión (para compatibilidad)
     */
    private void sendTitleReflection(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            // Intentar usar el método de Paper/Spigot más reciente
            Class<?> titleClass = Class.forName("org.bukkit.Title");
            Method createMethod = titleClass.getMethod("create", String.class, String.class, int.class, int.class, int.class);
            Object titleObj = createMethod.invoke(null, title, subtitle, fadeIn, stay, fadeOut);

            Method sendTitleMethod = Player.class.getMethod("sendTitle", titleClass);
            sendTitleMethod.invoke(player, titleObj);
        } catch (Exception e) {
            // Si todo falla, enviar como mensaje normal
            player.sendMessage(title);
            if (subtitle != null && !subtitle.isEmpty()) {
                player.sendMessage(subtitle);
            }
        }
    }

    /**
     * Envía un mensaje de acción (action bar)
     * @param player Jugador destinatario
     * @param path Ruta del mensaje
     */
    public void sendActionBar(Player player, String path) {
        String message = getMessage(path);
        sendActionBarCompat(player, message);
    }

    /**
     * Envía un mensaje de acción con reemplazos
     * @param player Jugador destinatario
     * @param path Ruta del mensaje
     * @param replacements Pares de placeholder-valor
     */
    public void sendActionBar(Player player, String path, String... replacements) {
        String message = getMessage(path, replacements);
        sendActionBarCompat(player, message);
    }

    /**
     * Envía action bar compatible con diferentes versiones
     */
    private void sendActionBarCompat(Player player, String message) {
        try {
            // Intentar método directo de Paper/Spigot moderno
            Method sendActionBarMethod = Player.class.getMethod("sendActionBar", String.class);
            sendActionBarMethod.invoke(player, message);
        } catch (NoSuchMethodException e) {
            // Intentar con ComponentBuilder (versiones más recientes)
            try {
                Class<?> componentClass = Class.forName("net.md_5.bungee.api.chat.TextComponent");
                Object component = componentClass.getConstructor(String.class).newInstance(message);

                Method sendActionBarMethod = Player.class.getMethod("spigot");
                Object spigotPlayer = sendActionBarMethod.invoke(player);

                Method sendMessageMethod = spigotPlayer.getClass().getMethod("sendMessage",
                        Class.forName("net.md_5.bungee.api.ChatMessageType"),
                        Class.forName("net.md_5.bungee.api.chat.BaseComponent"));

                Object actionBarType = Class.forName("net.md_5.bungee.api.ChatMessageType")
                        .getField("ACTION_BAR").get(null);

                sendMessageMethod.invoke(spigotPlayer, actionBarType, component);
            } catch (Exception ex) {
                // Si todo falla, enviar como mensaje normal
                player.sendMessage("§e[ActionBar] " + message);
            }
        } catch (Exception e) {
            // Fallback: enviar como mensaje normal
            player.sendMessage("§e[ActionBar] " + message);
        }
    }

    /**
     * Verifica si existe un mensaje en la configuración
     * @param path Ruta del mensaje
     * @return true si existe
     */
    public boolean hasMessage(String path) {
        return messagesConfig.contains(path);
    }

    /**
     * Obtiene el prefijo del plugin
     * @return Prefijo formateado
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Formatea un texto con colores
     * @param text Texto a formatear
     * @return Texto con colores aplicados
     */
    public static String formatText(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Limpia los códigos de color de un texto
     * @param text Texto a limpiar
     * @return Texto sin códigos de color
     */
    public static String stripColors(String text) {
        return ChatColor.stripColor(text);
    }

    /**
     * Envía un mensaje de debug si está habilitado
     * @param message Mensaje de debug
     */
    public void sendDebugMessage(String message) {
        if (plugin.getConfig().getBoolean("general.debug", false)) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }

    /**
     * Envía un mensaje de error a consola
     * @param message Mensaje de error
     */
    public void sendErrorMessage(String message) {
        plugin.getLogger().severe("[ERROR] " + message);
    }

    /**
     * Envía un mensaje de advertencia a consola
     * @param message Mensaje de advertencia
     */
    public void sendWarningMessage(String message) {
        plugin.getLogger().warning("[WARNING] " + message);
    }

    /**
     * Envía un mensaje personalizado con formato específico
     * @param player Jugador destinatario
     * @param message Mensaje a enviar
     * @param usePrefix Si usar el prefijo del plugin
     */
    public void sendCustomMessage(Player player, String message, boolean usePrefix) {
        String formattedMessage = ChatColor.translateAlternateColorCodes('&', message);
        if (usePrefix) {
            player.sendMessage(prefix + formattedMessage);
        } else {
            player.sendMessage(formattedMessage);
        }
    }

    /**
     * Obtiene una lista de mensajes
     * @param path Ruta de la lista
     * @return Lista de mensajes formateados
     */
    public List<String> getMessageList(String path) {
        List<String> messages = messagesConfig.getStringList(path);
        messages.replaceAll(message -> ChatColor.translateAlternateColorCodes('&', message));
        return messages;
    }

    /**
     * Envía un mensaje de confirmación con tiempo límite
     * @param player Jugador destinatario
     * @param confirmationPath Ruta del mensaje de confirmación
     * @param timeoutSeconds Segundos antes del timeout
     */
    public void sendConfirmationMessage(Player player, String confirmationPath, int timeoutSeconds) {
        sendMessage(player, confirmationPath);
        sendMessage(player, "general.confirmation-timeout",
                "{time}", String.valueOf(timeoutSeconds));
    }
}