package gc.grivyzom.marryCore.commands;

import gc.grivyzom.marryCore.MarryCore;
import gc.grivyzom.marryCore.enums.MaritalStatus;
import gc.grivyzom.marryCore.models.MarryPlayer;
import gc.grivyzom.marryCore.utils.MessageUtils;
import gc.grivyzom.marryCore.utils.ValidationUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Comando para divorciarse de la pareja actual.
 *
 * @author Brocolitx
 * @version 0.0.1
 */
public class DivorceCommand implements CommandExecutor {

    private final MarryCore plugin;
    private final MessageUtils messageUtils;
    private final ValidationUtils validationUtils;

    // Map para confirmaciones de divorcio pendientes
    private static final Map<UUID, Long> pendingDivorces = new HashMap<>();

    public DivorceCommand(MarryCore plugin) {
        this.plugin = plugin;
        this.messageUtils = new MessageUtils(plugin);
        this.validationUtils = new ValidationUtils(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Solo jugadores pueden usar este comando
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }

        Player player = (Player) sender;

        // Verificar permisos
        if (!player.hasPermission("marrycore.divorce")) {
            messageUtils.sendMessage(player, "general.no-permission");
            return true;
        }

        // Manejar subcomandos
        if (args.length > 0 && args[0].equalsIgnoreCase("confirmar")) {
            confirmDivorce(player);
            return true;
        }

        // Iniciar proceso de divorcio
        startDivorce(player);
        return true;
    }

    private void startDivorce(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Validar que el jugador pueda divorciarse
                ValidationUtils.ValidationResult validation = validationUtils.canDivorce(player);

                if (validation.isFailure()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, validation.getErrorMessage(), validation.getReplacements());
                    });
                    return;
                }

                MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

                if (playerData.getStatus() != MaritalStatus.CASADO && playerData.getStatus() != MaritalStatus.COMPROMETIDO) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "divorce.not-married");
                    });
                    return;
                }

                // Verificar si requiere confirmación
                boolean requireConfirmation = plugin.getConfig().getBoolean("security.anti_abuse.require_divorce_confirmation", true);

                if (requireConfirmation) {
                    // Añadir a confirmaciones pendientes
                    pendingDivorces.put(player.getUniqueId(), System.currentTimeMillis());

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "divorce.confirmation-required");
                    });

                    // Programar limpieza de confirmación después de 60 segundos
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        pendingDivorces.remove(player.getUniqueId());
                    }, 60 * 20L);
                } else {
                    // Procesar divorcio directamente
                    processDivorce(player, playerData);
                }

            } catch (Exception e) {
                plugin.getLogger().severe("Error al iniciar divorcio: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "general.database-error");
                });
            }
        });
    }

    private void confirmDivorce(Player player) {
        // Verificar si hay confirmación pendiente
        Long timestamp = pendingDivorces.remove(player.getUniqueId());

        if (timestamp == null) {
            messageUtils.sendMessage(player, "divorce.no-confirmation-pending");
            return;
        }

        // Verificar que no haya expirado (60 segundos)
        if (System.currentTimeMillis() - timestamp > 60000) {
            messageUtils.sendMessage(player, "divorce.confirmation-expired");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());
                processDivorce(player, playerData);

            } catch (Exception e) {
                plugin.getLogger().severe("Error al confirmar divorcio: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "general.database-error");
                });
            }
        });
    }

    private void processDivorce(Player player, MarryPlayer playerData) {
        try {
            if (!playerData.hasPartner()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "divorce.not-married");
                });
                return;
            }

            UUID partnerUuid = playerData.getPartnerUuid();
            Player partner = Bukkit.getPlayer(partnerUuid);

            // Obtener nombre de la pareja
            MarryPlayer partnerData = plugin.getDatabaseManager().getPlayerData(partnerUuid);
            String partnerName = partnerData.getUsername();

            Bukkit.getScheduler().runTask(plugin, () -> {
                messageUtils.sendMessage(player, "divorce.processing");
            });

            // Procesar divorcio en la base de datos
            plugin.getDatabaseManager().createDivorce(player.getUniqueId(), partnerUuid);

            // Volver al hilo principal para operaciones del servidor
            Bukkit.getScheduler().runTask(plugin, () -> {
                // Enviar mensajes
                messageUtils.sendMessage(player, "divorce.success",
                        "{player}", partnerName);

                if (partner != null) {
                    messageUtils.sendMessage(partner, "divorce.notification",
                            "{player}", player.getName());
                }

                // Anuncio global si está habilitado
                if (plugin.getConfig().getBoolean("chat.announcements.divorces", false)) {
                    messageUtils.broadcastMessage("divorce.announcement",
                            "{player1}", player.getName(),
                            "{player2}", partnerName);
                }

                // TODO: Remover anillos de matrimonio y efectos especiales
                // TODO: Aplicar costos económicos si está habilitado
            });

        } catch (Exception e) {
            plugin.getLogger().severe("Error al procesar divorcio: " + e.getMessage());
            Bukkit.getScheduler().runTask(plugin, () -> {
                messageUtils.sendMessage(player, "general.database-error");
            });
        }
    }

    // Método para limpiar confirmaciones de jugadores desconectados
    public static void cleanupConfirmation(UUID playerUuid) {
        pendingDivorces.remove(playerUuid);
    }
}