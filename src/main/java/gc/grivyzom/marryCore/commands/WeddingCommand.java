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

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Comando para gestionar ceremonias de matrimonio.
 *
 * @author Brocolitx
 * @version 0.0.1
 */
public class WeddingCommand implements CommandExecutor {

    private final MarryCore plugin;
    private final MessageUtils messageUtils;
    private final ValidationUtils validationUtils;

    public WeddingCommand(MarryCore plugin) {
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
        if (!player.hasPermission("marrycore.wedding")) {
            messageUtils.sendMessage(player, "general.no-permission");
            return true;
        }

        // Manejar subcomandos
        if (args.length == 0) {
            showWeddingStatus(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "programar":
            case "schedule":
                if (args.length < 2) {
                    messageUtils.sendMessage(player, "general.invalid-command",
                            "{usage}", "/casamiento programar <fecha> [hora]");
                    return true;
                }
                scheduleWedding(player, args);
                break;

            case "cancelar":
            case "cancel":
                cancelWedding(player);
                break;

            case "estado":
            case "status":
                showWeddingStatus(player);
                break;

            case "ayuda":
            case "help":
                showHelp(player);
                break;

            default:
                // Si no es un subcomando, asumir que es una fecha
                String[] newArgs = new String[args.length + 1];
                newArgs[0] = "programar";
                System.arraycopy(args, 0, newArgs, 1, args.length);
                scheduleWedding(player, newArgs);
                break;
        }

        return true;
    }

    private void scheduleWedding(Player player, String[] args) {
        // Verificar que el jugador esté comprometido
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

                if (playerData.getStatus() != MaritalStatus.COMPROMETIDO) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "wedding.schedule.not-engaged");
                    });
                    return;
                }

                // Validar fecha
                String dateString = args[1];
                String timeString = args.length > 2 ? args[2] : "18:00";

                ValidationUtils.ValidationResult validation = validationUtils.validateWeddingDate(dateString, timeString);

                if (validation.isFailure()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, validation.getErrorMessage(), validation.getReplacements());
                    });
                    return;
                }

                // Programar la ceremonia
                LocalDateTime dateTime = LocalDateTime.parse(dateString + " " + timeString,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                Timestamp weddingDate = Timestamp.valueOf(dateTime);

                int marriageId = plugin.getDatabaseManager().getMarriageId(
                        player.getUniqueId(), playerData.getPartnerUuid());

                if (marriageId == -1) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "general.database-error");
                    });
                    return;
                }

                // Obtener ubicación de ceremonia disponible
                String location = getAvailableLocation();

                plugin.getDatabaseManager().scheduleWedding(marriageId, weddingDate, location);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "wedding.schedule.ceremony-scheduled",
                            "{date}", dateString,
                            "{time}", timeString);
                    messageUtils.sendMessage(player, "wedding.schedule.location-assigned",
                            "{location}", location);

                    // Notificar a la pareja
                    Player partner = Bukkit.getPlayer(playerData.getPartnerUuid());
                    if (partner != null) {
                        messageUtils.sendMessage(partner, "wedding.schedule.ceremony-scheduled",
                                "{date}", dateString,
                                "{time}", timeString);
                        messageUtils.sendMessage(partner, "wedding.schedule.location-assigned",
                                "{location}", location);
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al programar ceremonia: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "general.database-error");
                });
            }
        });
    }

    private void cancelWedding(Player player) {
        // TODO: Implementar cancelación de ceremonia
        messageUtils.sendMessage(player, "wedding.cancel.success");
    }

    private void showWeddingStatus(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

                if (playerData.getStatus() != MaritalStatus.COMPROMETIDO) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "wedding.status.not-engaged");
                    });
                    return;
                }

                // Obtener información de la ceremonia
                int marriageId = plugin.getDatabaseManager().getMarriageId(
                        player.getUniqueId(), playerData.getPartnerUuid());

                if (marriageId != -1) {
                    // TODO: Obtener detalles de la ceremonia de la base de datos
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "wedding.status.ceremony-info",
                                "{date}", "No programada",
                                "{time}", "N/A",
                                "{location}", "N/A",
                                "{guests}", "0",
                                "{max_guests}", String.valueOf(plugin.getConfig().getInt("marriage.wedding.max_guests", 20)),
                                "{cost}", "0");
                    });
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "wedding.status.not-engaged");
                    });
                }

            } catch (Exception e) {
                plugin.getLogger().severe("Error al obtener estado de ceremonia: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "general.database-error");
                });
            }
        });
    }

    private void showHelp(Player player) {
        messageUtils.sendMultilineMessage(player, "wedding.help");
    }

    private String getAvailableLocation() {
        // Obtener una ubicación disponible de la configuración
        var locations = plugin.getConfig().getConfigurationSection("marriage.wedding.ceremony_locations");

        if (locations != null) {
            for (String key : locations.getKeys(false)) {
                if (locations.getBoolean(key + ".enabled", false)) {
                    return locations.getString(key + ".name", key);
                }
            }
        }

        return "Ubicación por defecto";
    }


    /**
     * NUEVO MÉTODO: Completar ceremonia y crear matrimonio oficial
     */
    public void completeWedding(Player player1, Player player2) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Verificar que ambos estén comprometidos
                ValidationUtils.ValidationResult validation = validationUtils.canMarry(player1, player2);

                if (validation.isFailure()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player1, validation.getErrorMessage(), validation.getReplacements());
                        messageUtils.sendMessage(player2, validation.getErrorMessage(), validation.getReplacements());
                    });
                    return;
                }

                // CREAR MATRIMONIO OFICIAL
                plugin.getDatabaseManager().createMarriage(player1.getUniqueId(), player2.getUniqueId());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Dar anillos de boda
                    String currentDate = java.time.LocalDate.now().toString();
                    new gc.grivyzom.marryCore.items.ItemManager(plugin).giveWeddingRing(player1, player2.getName(), currentDate);
                    new gc.grivyzom.marryCore.items.ItemManager(plugin).giveWeddingRing(player2, player1.getName(), currentDate);

                    // Mensajes de éxito
                    messageUtils.sendMessage(player1, "ceremony.completion.marriage-success",
                            "{player1}", player1.getName(),
                            "{player2}", player2.getName());
                    messageUtils.sendMessage(player2, "ceremony.completion.marriage-success",
                            "{player1}", player1.getName(),
                            "{player2}", player2.getName());

                    // Anuncio global
                    if (plugin.getConfig().getBoolean("chat.announcements.marriages", true)) {
                        messageUtils.broadcastMessage("ceremony.completion.marriage-success",
                                "{player1}", player1.getName(),
                                "{player2}", player2.getName());
                    }

                    // Efectos especiales
                    try {
                        new gc.grivyzom.marryCore.items.ItemManager(plugin).playEngagementEffects(player1, player2);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Error al reproducir efectos de boda: " + e.getMessage());
                    }

                    plugin.getLogger().info("Matrimonio completado exitosamente: " + player1.getName() + " <-> " + player2.getName());
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al completar ceremonia: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player1, "general.database-error");
                    messageUtils.sendMessage(player2, "general.database-error");
                });
            }
        });
    }

    /**
     * COMANDO MEJORADO: Casamiento instantáneo para administradores
     */
    public void forceMarry(Player admin, Player player1, Player player2) {
        if (!admin.hasPermission("marrycore.admin.force")) {
            messageUtils.sendMessage(admin, "general.no-permission");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Forzar compromiso primero si no están comprometidos
                plugin.getDatabaseManager().createEngagement(player1.getUniqueId(), player2.getUniqueId());

                // Luego forzar matrimonio
                plugin.getDatabaseManager().createMarriage(player1.getUniqueId(), player2.getUniqueId());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Dar anillos
                    String currentDate = java.time.LocalDate.now().toString();
                    new gc.grivyzom.marryCore.items.ItemManager(plugin).giveWeddingRing(player1, player2.getName(), currentDate);
                    new gc.grivyzom.marryCore.items.ItemManager(plugin).giveWeddingRing(player2, player1.getName(), currentDate);

                    // Notificar a todos
                    admin.sendMessage("§a¡Has casado exitosamente a " + player1.getName() + " y " + player2.getName() + "!");
                    player1.sendMessage("§a¡Has sido casado/a con " + player2.getName() + " por un administrador!");
                    player2.sendMessage("§a¡Has sido casado/a con " + player1.getName() + " por un administrador!");

                    plugin.getLogger().info("Matrimonio forzado por " + admin.getName() + ": " + player1.getName() + " <-> " + player2.getName());
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al forzar matrimonio: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    admin.sendMessage("§cError al procesar el matrimonio forzado.");
                });
            }
        });
    }
}