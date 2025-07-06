package gc.grivyzom.marryCore.commands;

import gc.grivyzom.marryCore.MarryCore;
import gc.grivyzom.marryCore.items.ItemManager;
import gc.grivyzom.marryCore.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Comandos administrativos para MarryCore.
 *
 * @author Brocolitx
 * @version 0.0.1
 */
public class AdminCommand implements CommandExecutor {

    private final MarryCore plugin;
    private final MessageUtils messageUtils;
    private final ItemManager itemManager;

    public AdminCommand(MarryCore plugin) {
        this.plugin = plugin;
        this.messageUtils = new MessageUtils(plugin);
        this.itemManager = new ItemManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Verificar permisos de administrador
        if (!sender.hasPermission("marrycore.admin")) {
            messageUtils.sendMessage(sender, "general.no-permission");
            return true;
        }

        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "reload":
                reloadPlugin(sender);
                break;

            case "forceengage":
                if (args.length < 3) {
                    sender.sendMessage("§cUso: /marrycore forceengage <jugador1> <jugador2>");
                    return true;
                }
                forceEngagement(sender, args[1], args[2]);
                break;

            case "forcemarry":
                if (args.length < 3) {
                    sender.sendMessage("§cUso: /marrycore forcemarry <jugador1> <jugador2>");
                    return true;
                }
                forceMarriage(sender, args[1], args[2]);
                break;

            case "forcedivorce":
                if (args.length < 2) {
                    sender.sendMessage("§cUso: /marrycore forcedivorce <jugador>");
                    return true;
                }
                forceDivorce(sender, args[1]);
                break;

            case "givering":
                if (args.length < 3) {
                    sender.sendMessage("§cUso: /marrycore givering <jugador> <tipo>");
                    sender.sendMessage("§cTipos: proposal, engagement, wedding");
                    return true;
                }
                giveRing(sender, args[1], args[2]);
                break;

            case "reset":
                if (args.length < 2) {
                    sender.sendMessage("§cUso: /marrycore reset <jugador>");
                    return true;
                }
                resetPlayer(sender, args[1]);
                break;

            case "stats":
                showStats(sender);
                break;

            case "repair":
                repairDatabase(sender);
                break;

            case "info":
                if (args.length < 2) {
                    sender.sendMessage("§cUso: /marrycore info <jugador>");
                    return true;
                }
                showPlayerInfo(sender, args[1]);
                break;

            case "help":
                showHelp(sender);
                break;

            default:
                sender.sendMessage("§cComando desconocido. Usa /marrycore help para ver la ayuda.");
                break;
        }

        return true;
    }

    private void reloadPlugin(CommandSender sender) {
        try {
            plugin.reloadConfigs();
            itemManager.reloadItemsConfig();
            messageUtils.sendMessage(sender, "general.reload-success");
        } catch (Exception e) {
            plugin.getLogger().severe("Error al recargar configuración: " + e.getMessage());
            sender.sendMessage("§cError al recargar la configuración.");
        }
    }

    private void forceEngagement(CommandSender sender, String player1Name, String player2Name) {
        Player player1 = Bukkit.getPlayer(player1Name);
        Player player2 = Bukkit.getPlayer(player2Name);

        if (player1 == null || player2 == null) {
            sender.sendMessage("§cUno o ambos jugadores no están conectados.");
            return;
        }

        if (player1.equals(player2)) {
            sender.sendMessage("§cNo puedes comprometer a un jugador consigo mismo.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getDatabaseManager().createEngagement(player1.getUniqueId(), player2.getUniqueId());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Dar anillos nupciales
                    itemManager.giveEngagementRing(player1, player2.getName());
                    itemManager.giveEngagementRing(player2, player1.getName());

                    sender.sendMessage("§a¡" + player1.getName() + " y " + player2.getName() + " han sido comprometidos por la fuerza!");

                    messageUtils.sendMessage(player1, "admin.force-engagement.notification",
                            "{player}", player2.getName());
                    messageUtils.sendMessage(player2, "admin.force-engagement.notification",
                            "{player}", player1.getName());
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al forzar compromiso: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§cError al forzar el compromiso.");
                });
            }
        });
    }

    private void forceMarriage(CommandSender sender, String player1Name, String player2Name) {
        Player player1 = Bukkit.getPlayer(player1Name);
        Player player2 = Bukkit.getPlayer(player2Name);

        if (player1 == null || player2 == null) {
            sender.sendMessage("§cUno o ambos jugadores no están conectados.");
            return;
        }

        if (player1.equals(player2)) {
            sender.sendMessage("§cNo puedes casar a un jugador consigo mismo.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Primero comprometer si no lo están
                plugin.getDatabaseManager().createEngagement(player1.getUniqueId(), player2.getUniqueId());
                // Luego casar
                plugin.getDatabaseManager().createMarriage(player1.getUniqueId(), player2.getUniqueId());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Dar anillos de boda
                    String currentDate = java.time.LocalDate.now().toString();
                    itemManager.giveWeddingRing(player1, player2.getName(), currentDate);
                    itemManager.giveWeddingRing(player2, player1.getName(), currentDate);

                    messageUtils.sendMessage(sender, "admin.force-marriage",
                            "{player1}", player1.getName(),
                            "{player2}", player2.getName());

                    messageUtils.sendMessage(player1, "admin.force-marriage.notification",
                            "{player}", player2.getName());
                    messageUtils.sendMessage(player2, "admin.force-marriage.notification",
                            "{player}", player1.getName());

                    // Efectos especiales
                    itemManager.playEngagementEffects(player1, player2);
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al forzar matrimonio: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§cError al forzar el matrimonio.");
                });
            }
        });
    }

    private void forceDivorce(CommandSender sender, String playerName) {
        Player player = Bukkit.getPlayer(playerName);

        if (player == null) {
            sender.sendMessage("§cEl jugador no está conectado.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                var playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

                if (!playerData.hasPartner()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage("§cEl jugador no está casado o comprometido.");
                    });
                    return;
                }

                var partnerData = plugin.getDatabaseManager().getPlayerData(playerData.getPartnerUuid());
                String partnerName = partnerData.getUsername();

                plugin.getDatabaseManager().createDivorce(player.getUniqueId(), playerData.getPartnerUuid());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(sender, "admin.force-divorce",
                            "{player1}", player.getName(),
                            "{player2}", partnerName);

                    messageUtils.sendMessage(player, "admin.force-divorce.notification");

                    Player partner = Bukkit.getPlayer(playerData.getPartnerUuid());
                    if (partner != null) {
                        messageUtils.sendMessage(partner, "admin.force-divorce.notification");
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al forzar divorcio: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§cError al forzar el divorcio.");
                });
            }
        });
    }

    private void giveRing(CommandSender sender, String playerName, String ringType) {
        Player player = Bukkit.getPlayer(playerName);

        if (player == null) {
            sender.sendMessage("§cEl jugador no está conectado.");
            return;
        }

        switch (ringType.toLowerCase()) {
            case "proposal":
                itemManager.giveProposalRing(player);
                messageUtils.sendMessage(sender, "admin.give-ring",
                        "{ring_type}", "Anillo de Propuesta",
                        "{player}", player.getName());
                break;

            case "engagement":
                itemManager.giveEngagementRing(player, "Pareja");
                messageUtils.sendMessage(sender, "admin.give-ring",
                        "{ring_type}", "Anillo Nupcial",
                        "{player}", player.getName());
                break;

            case "wedding":
                String currentDate = java.time.LocalDate.now().toString();
                itemManager.giveWeddingRing(player, "Pareja", currentDate);
                messageUtils.sendMessage(sender, "admin.give-ring",
                        "{ring_type}", "Anillo de Boda",
                        "{player}", player.getName());
                break;

            default:
                sender.sendMessage("§cTipo de anillo inválido. Usa: proposal, engagement, wedding");
                return;
        }

        player.sendMessage("§aHas recibido un anillo del administrador.");
    }

    private void resetPlayer(CommandSender sender, String playerName) {
        Player player = Bukkit.getPlayer(playerName);

        if (player == null) {
            sender.sendMessage("§cEl jugador no está conectado.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getDatabaseManager().deletePlayerData(player.getUniqueId());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(sender, "admin.reset-player",
                            "{player}", player.getName());

                    messageUtils.sendMessage(player, "admin.reset-player.notification");
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al resetear jugador: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§cError al resetear los datos del jugador.");
                });
            }
        });
    }

    private void showStats(CommandSender sender) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int[] stats = plugin.getDatabaseManager().getSystemStats();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§a§l====== ESTADÍSTICAS DE MARRYCORE ======");
                    sender.sendMessage("§eTotal de jugadores: §f" + stats[0]);
                    sender.sendMessage("§eSolteros: §f" + stats[1]);
                    sender.sendMessage("§eComprometidos: §f" + stats[2]);
                    sender.sendMessage("§eCasados: §f" + stats[3]);
                    sender.sendMessage("§a§l======================================");
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al obtener estadísticas: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§cError al obtener las estadísticas.");
                });
            }
        });
    }

    private void repairDatabase(CommandSender sender) {
        sender.sendMessage("§eReparando base de datos...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int repairedCount = plugin.getDatabaseManager().repairDatabase();

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§a¡Base de datos reparada! Registros corregidos: " + repairedCount);
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al reparar base de datos: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§cError al reparar la base de datos.");
                });
            }
        });
    }

    private void showPlayerInfo(CommandSender sender, String playerName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                var playerData = plugin.getDatabaseManager().getPlayerDataByUsername(playerName);

                if (playerData == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage("§cJugador no encontrado en la base de datos.");
                    });
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§a§l====== INFORMACIÓN DE " + playerData.getUsername().toUpperCase() + " ======");
                    sender.sendMessage("§eUUID: §f" + playerData.getUuid());
                    sender.sendMessage("§eEstado: §f" + playerData.getStatus().getDisplayName());

                    if (playerData.hasPartner()) {
                        try {
                            var partnerData = plugin.getDatabaseManager().getPlayerData(playerData.getPartnerUuid());
                            sender.sendMessage("§ePareja: §f" + partnerData.getUsername());
                        } catch (Exception e) {
                            sender.sendMessage("§ePareja: §cError al cargar datos");
                        }
                    } else {
                        sender.sendMessage("§ePareja: §fNinguna");
                    }

                    sender.sendMessage("§eCreado: §f" + playerData.getCreatedAt());
                    sender.sendMessage("§eÚltima actualización: §f" + playerData.getUpdatedAt());
                    sender.sendMessage("§a§l==========================================");
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al obtener información del jugador: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§cError al obtener la información del jugador.");
                });
            }
        });
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("§a§l====== COMANDOS ADMINISTRATIVOS MARRYCORE ======");
        sender.sendMessage("§e/marrycore reload §7- Recargar configuración");
        sender.sendMessage("§e/marrycore forceengage <p1> <p2> §7- Forzar compromiso");
        sender.sendMessage("§e/marrycore forcemarry <p1> <p2> §7- Forzar matrimonio");
        sender.sendMessage("§e/marrycore forcedivorce <jugador> §7- Forzar divorcio");
        sender.sendMessage("§e/marrycore givering <jugador> <tipo> §7- Dar anillo");
        sender.sendMessage("§e/marrycore reset <jugador> §7- Resetear datos de jugador");
        sender.sendMessage("§e/marrycore stats §7- Ver estadísticas del sistema");
        sender.sendMessage("§e/marrycore repair §7- Reparar base de datos");
        sender.sendMessage("§e/marrycore info <jugador> §7- Ver información de jugador");
        sender.sendMessage("§a§l===============================================");
    }
}