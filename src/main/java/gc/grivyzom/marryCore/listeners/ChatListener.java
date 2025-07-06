package gc.grivyzom.marryCore.listeners;

import gc.grivyzom.marryCore.MarryCore;
import gc.grivyzom.marryCore.commands.MarryCommand;
import gc.grivyzom.marryCore.enums.MaritalStatus;
import gc.grivyzom.marryCore.models.MarryPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Listener para manejar eventos de chat y mostrar estado civil.
 * También maneja la aceptación/rechazo de propuestas por chat.
 *
 * @author Brocolitx
 * @version 0.0.1
 */
public class ChatListener implements Listener {

    private final MarryCore plugin;

    public ChatListener(MarryCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().toLowerCase().trim();

        // Verificar si el mensaje es para aceptar/rechazar propuesta
        if (message.equals("aceptar") || message.equals("accept") || message.equals("si") || message.equals("yes")) {
            if (MarryCommand.hasPendingProposal(player.getUniqueId())) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    MarryCommand.acceptProposal(player);
                });
                return;
            }
        }

        if (message.equals("rechazar") || message.equals("reject") || message.equals("no")) {
            if (MarryCommand.hasPendingProposal(player.getUniqueId())) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    MarryCommand.rejectProposal(player);
                });
                return;
            }
        }

        // Verificar si debe mostrar estado civil en el chat
        if (plugin.getConfig().getBoolean("chat.display_status.enabled", true)) {
            modifyDisplayName(player);
        }
    }

    private void modifyDisplayName(Player player) {
        // Ejecutar en hilo asíncrono para consulta de base de datos
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());
                String statusFormat = getStatusFormat(playerData);

                // Volver al hilo principal para modificar el display name
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!statusFormat.isEmpty()) {
                        String currentDisplayName = player.getDisplayName();

                        // Remover formato anterior si existe
                        currentDisplayName = removeOldStatusFormat(currentDisplayName);

                        // Añadir nuevo formato
                        player.setDisplayName(statusFormat + " " + currentDisplayName);
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().warning("Error al obtener estado civil para chat: " + e.getMessage());
            }
        });
    }

    private String getStatusFormat(MarryPlayer playerData) {
        switch (playerData.getStatus()) {
            case SOLTERO:
                return plugin.getConfig().getString("chat.display_status.single_format", "");

            case COMPROMETIDO:
                return plugin.getConfig().getString("chat.display_status.engaged_format", "&7[&bComprometido&7]");

            case CASADO:
                String marriedFormat = plugin.getConfig().getString("chat.display_status.married_format", "&7[&d♥ {partner}&7]");

                if (playerData.hasPartner()) {
                    try {
                        MarryPlayer partnerData = plugin.getDatabaseManager().getPlayerData(playerData.getPartnerUuid());
                        return marriedFormat.replace("{partner}", partnerData.getUsername());
                    } catch (Exception e) {
                        return marriedFormat.replace("{partner}", "Desconocido");
                    }
                }
                return marriedFormat.replace("{partner}", "Desconocido");

            default:
                return "";
        }
    }

    private String removeOldStatusFormat(String displayName) {
        // Remover formatos anteriores conocidos
        displayName = displayName.replaceFirst("§7\\[§bComprometido§7\\]\\s*", "");
        displayName = displayName.replaceFirst("§7\\[§d♥ .+?§7\\]\\s*", "");

        return displayName;
    }

    /**
     * Evento para chat privado entre cónyuges
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPrivateChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage();

        // Verificar si es comando de chat privado
        String privateChatCommand = plugin.getConfig().getString("benefits.private_chat.command", "pc");

        if (!message.startsWith("/" + privateChatCommand + " ")) {
            return;
        }

        // Verificar si el chat privado está habilitado
        if (!plugin.getConfig().getBoolean("benefits.private_chat.enabled", true)) {
            return;
        }

        event.setCancelled(true);

        // Extraer mensaje
        String privateMessage = message.substring(privateChatCommand.length() + 2);

        if (privateMessage.trim().isEmpty()) {
            player.sendMessage("§cUso: /" + privateChatCommand + " <mensaje>");
            return;
        }

        // Procesar en hilo asíncrono
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

                if (playerData.getStatus() != MaritalStatus.CASADO || !playerData.hasPartner()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§cDebes estar casado/a para usar el chat privado.");
                    });
                    return;
                }

                Player partner = Bukkit.getPlayer(playerData.getPartnerUuid());

                if (partner == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§cTu cónyuge no está conectado.");
                    });
                    return;
                }

                // Formatear y enviar mensaje
                String format = plugin.getConfig().getString("benefits.private_chat.format", "&d[♥] &f{sender}: {message}");
                String formattedMessage = format
                        .replace("{sender}", player.getName())
                        .replace("{message}", privateMessage)
                        .replace("&", "§");

                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage(formattedMessage);
                    partner.sendMessage(formattedMessage);
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error en chat privado: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§cError al enviar mensaje privado.");
                });
            }
        });
    }
}