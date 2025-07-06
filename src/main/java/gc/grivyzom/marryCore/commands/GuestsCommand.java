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

/**
 * Comando para gestionar invitados a ceremonias de matrimonio.
 *
 * @author Brocolitx
 * @version 0.0.1
 */
public class GuestsCommand implements CommandExecutor {

    private final MarryCore plugin;
    private final MessageUtils messageUtils;
    private final ValidationUtils validationUtils;

    public GuestsCommand(MarryCore plugin) {
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
        if (!player.hasPermission("marrycore.guests")) {
            messageUtils.sendMessage(player, "general.no-permission");
            return true;
        }

        // Verificar argumentos
        if (args.length == 0) {
            showGuestList(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "add":
            case "añadir":
            case "invitar":
                if (args.length < 2) {
                    messageUtils.sendMessage(player, "general.invalid-command",
                            "{usage}", "/invitados add <jugador>");
                    return true;
                }
                addGuest(player, args[1]);
                break;

            case "remove":
            case "remover":
            case "quitar":
                if (args.length < 2) {
                    messageUtils.sendMessage(player, "general.invalid-command",
                            "{usage}", "/invitados remove <jugador>");
                    return true;
                }
                removeGuest(player, args[1]);
                break;

            case "list":
            case "lista":
                showGuestList(player);
                break;

            case "confirmar":
            case "confirm":
                confirmAttendance(player);
                break;

            case "rechazar":
            case "decline":
                declineInvitation(player);
                break;

            case "ayuda":
            case "help":
                showHelp(player);
                break;

            default:
                messageUtils.sendMessage(player, "general.invalid-command",
                        "{usage}", "/invitados <add|remove|list|confirmar|rechazar> [jugador]");
                break;
        }

        return true;
    }

    private void addGuest(Player player, String guestName) {
        // Buscar jugador objetivo
        Player target = Bukkit.getPlayer(guestName);
        if (target == null) {
            messageUtils.sendMessage(player, "general.player-not-found",
                    "{player}", guestName);
            return;
        }

        // No puede invitarse a sí mismo
        if (player.equals(target)) {
            messageUtils.sendMessage(player, "guests.add.cannot-invite-self");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

                if (playerData.getStatus() != MaritalStatus.COMPROMETIDO) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "guests.add.not-engaged");
                    });
                    return;
                }

                int marriageId = plugin.getDatabaseManager().getMarriageId(
                        player.getUniqueId(), playerData.getPartnerUuid());

                if (marriageId == -1) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "general.database-error");
                    });
                    return;
                }

                // Validar si se puede añadir el invitado
                ValidationUtils.ValidationResult validation = validationUtils.canAddGuest(marriageId, target);

                if (validation.isFailure()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, validation.getErrorMessage(), validation.getReplacements());
                    });
                    return;
                }

                // Añadir invitado
                plugin.getDatabaseManager().addGuest(marriageId, target.getUniqueId(), player.getUniqueId());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "guests.add.invitation-sent",
                            "{player}", target.getName());
                    messageUtils.sendMessage(target, "guests.add.invitation-received",
                            "{player}", player.getName());
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al añadir invitado: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "general.database-error");
                });
            }
        });
    }

    private void removeGuest(Player player, String guestName) {
        // Buscar jugador objetivo
        Player target = Bukkit.getPlayer(guestName);
        if (target == null) {
            messageUtils.sendMessage(player, "general.player-not-found",
                    "{player}", guestName);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

                if (playerData.getStatus() != MaritalStatus.COMPROMETIDO) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "guests.remove.not-engaged");
                    });
                    return;
                }

                int marriageId = plugin.getDatabaseManager().getMarriageId(
                        player.getUniqueId(), playerData.getPartnerUuid());

                if (marriageId == -1) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "general.database-error");
                    });
                    return;
                }

                // Verificar si el jugador está invitado
                if (!plugin.getDatabaseManager().isPlayerInvited(marriageId, target.getUniqueId())) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "guests.remove.not-invited",
                                "{player}", target.getName());
                    });
                    return;
                }

                // Remover invitado
                plugin.getDatabaseManager().removeGuest(marriageId, target.getUniqueId());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "guests.remove.guest-removed",
                            "{player}", target.getName());
                    messageUtils.sendMessage(target, "guests.remove.invitation-revoked",
                            "{player}", player.getName());
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al remover invitado: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "general.database-error");
                });
            }
        });
    }

    private void showGuestList(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

                if (playerData.getStatus() != MaritalStatus.COMPROMETIDO) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "guests.list.not-engaged");
                    });
                    return;
                }

                int marriageId = plugin.getDatabaseManager().getMarriageId(
                        player.getUniqueId(), playerData.getPartnerUuid());

                if (marriageId == -1) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "general.database-error");
                    });
                    return;
                }

                // TODO: Obtener lista de invitados de la base de datos
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "guests.list.header");
                    messageUtils.sendMessage(player, "guests.list.empty");
                    messageUtils.sendMessage(player, "guests.list.footer");
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al obtener lista de invitados: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "general.database-error");
                });
            }
        });
    }

    private void confirmAttendance(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // TODO: Buscar invitaciones pendientes para este jugador
                // y permitir confirmar asistencia

                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "guests.confirm.not-invited");
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al confirmar asistencia: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "general.database-error");
                });
            }
        });
    }

    private void declineInvitation(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // TODO: Buscar invitaciones pendientes para este jugador
                // y permitir rechazar la invitación

                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "guests.decline.not-invited");
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al rechazar invitación: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "general.database-error");
                });
            }
        });
    }

    private void showHelp(Player player) {
        messageUtils.sendMultilineMessage(player, "guests.help");
    }
}