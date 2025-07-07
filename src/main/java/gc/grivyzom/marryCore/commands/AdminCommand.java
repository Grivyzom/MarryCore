package gc.grivyzom.marryCore.commands;

import gc.grivyzom.marryCore.MarryCore;
import gc.grivyzom.marryCore.items.ItemManager;
import gc.grivyzom.marryCore.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Comandos administrativos para MarryCore.
 * Actualizado con funciones de placeholders.
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

            case "forcedating":
                if (args.length < 3) {
                    sender.sendMessage("§cUso: /marrycore forcedating <jugador1> <jugador2>");
                    return true;
                }
                forceDating(sender, args[1], args[2]);
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

            case "placeholders":
                handlePlaceholderCommands(sender, args);
                break;

            case "test":
                if (args.length < 2) {
                    sender.sendMessage("§cUso: /marrycore test <placeholder> [jugador]");
                    return true;
                }
                testPlaceholder(sender, args);
                break;


            case "debug":
                showDebugInfo(sender);
                break;

            case "check":
                checkPlayerStatus(sender, args.length > 1 ? args[1] : null);
                break;

            case "sync":
                if (args.length < 2) {
                    sender.sendMessage("§cUso: /marrycore sync <jugador>");
                    return true;
                }
                syncPlayerStatus(sender, args[1]);
                break;


            case "help":
                showHelp(sender);
                break;
        }

        return true;
    }

    private void forceDating(CommandSender sender, String player1Name, String player2Name) {
        Player player1 = Bukkit.getPlayer(player1Name);
        Player player2 = Bukkit.getPlayer(player2Name);

        if (player1 == null || player2 == null) {
            sender.sendMessage("§cUno o ambos jugadores no están conectados.");
            return;
        }

        if (player1.equals(player2)) {
            sender.sendMessage("§cNo puedes poner a un jugador en relación consigo mismo.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Crear relación de noviazgo
                plugin.getDatabaseManager().createRelationship(player1.getUniqueId(), player2.getUniqueId());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§a¡Has forzado el noviazgo entre " + player1.getName() + " y " + player2.getName() + "!");

                    messageUtils.sendMessage(player1, "admin.force-dating.notification",
                            "{player}", player2.getName());
                    messageUtils.sendMessage(player2, "admin.force-dating.notification",
                            "{player}", player1.getName());

                    plugin.getLogger().info("Noviazgo forzado por " + sender.getName() + ": " + player1.getName() + " <-> " + player2.getName());
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al forzar noviazgo: " + e.getMessage());
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§cError al forzar el noviazgo: " + e.getMessage());
                });
            }
        });
    }

    private void reloadPlugin(CommandSender sender) {
        try {
            plugin.reloadConfigs();
            itemManager.reloadItemsConfig();
            messageUtils.sendMessage(sender, "general.reload-success");

            // Mostrar estado de placeholders después de recargar
            if (plugin.getPlaceholderManager() != null) {
                boolean enabled = plugin.getPlaceholderManager().isPlaceholderAPIEnabled();
                sender.sendMessage("§aPlaceholders: " + (enabled ? "§2✓ Habilitados" : "§c✗ Deshabilitados"));
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error al recargar configuración: " + e.getMessage());
            sender.sendMessage("§cError al recargar la configuración.");
        }
    }

    private void handlePlaceholderCommands(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUso: /marrycore placeholders <info|list|test>");
            return;
        }

        String placeholderSubCommand = args[1].toLowerCase();

        switch (placeholderSubCommand) {
            case "info":
                if (plugin.getPlaceholderManager() != null) {
                    plugin.getPlaceholderManager().showPlaceholderInfo(sender);
                } else {
                    sender.sendMessage("§cSistema de placeholders no inicializado.");
                }
                break;

            case "list":
                int page = 1;
                if (args.length > 2) {
                    try {
                        page = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§cNúmero de página inválido.");
                        return;
                    }
                }

                if (plugin.getPlaceholderManager() != null) {
                    plugin.getPlaceholderManager().listAllPlaceholders(sender, page);
                } else {
                    sender.sendMessage("§cSistema de placeholders no inicializado.");
                }
                break;

            case "test":
                if (args.length < 3) {
                    sender.sendMessage("§cUso: /marrycore placeholders test <placeholder> [jugador]");
                    return;
                }
                testPlaceholder(sender, args);
                break;

            case "debug":
                showPlaceholderDebug(sender);
                break;

            default:
                sender.sendMessage("§cSubcomando inválido. Usa: info, list, test, debug");
                break;
        }
    }

    private void testPlaceholder(CommandSender sender, String[] args) {
        if (plugin.getPlaceholderManager() == null || !plugin.getPlaceholderManager().isPlaceholderAPIEnabled()) {
            sender.sendMessage("§cPlaceholderAPI no está habilitado.");
            return;
        }

        String placeholder = args.length > 1 ? args[1] : args[1];
        Player targetPlayer = null;

        // Determinar jugador objetivo
        if (args.length > 2) {
            targetPlayer = Bukkit.getPlayer(args[2]);
            if (targetPlayer == null) {
                sender.sendMessage("§cJugador no encontrado: " + args[2]);
                return;
            }
        } else if (sender instanceof Player) {
            targetPlayer = (Player) sender;
        } else {
            sender.sendMessage("§cDebes especificar un jugador al ejecutar desde consola.");
            return;
        }

        // Asegurar formato correcto del placeholder
        if (!placeholder.startsWith("%")) {
            placeholder = "%" + placeholder;
        }
        if (!placeholder.endsWith("%")) {
            placeholder = placeholder + "%";
        }

        // Verificar si es un placeholder de MarryCore
        if (!placeholder.startsWith("%marry_")) {
            sender.sendMessage("§cEste comando solo prueba placeholders de MarryCore (%marry_*)");
            return;
        }

        try {
            // Probar el placeholder
            String result = plugin.getPlaceholderManager().replacePlaceholders(targetPlayer, placeholder);

            sender.sendMessage("§a§l===== PRUEBA DE PLACEHOLDER =====");
            sender.sendMessage("§eJugador: §f" + targetPlayer.getName());
            sender.sendMessage("§ePlaceholder: §f" + placeholder);
            sender.sendMessage("§eResultado: §f" + (result != null ? result : "§cnull"));

            // Mostrar ayuda del placeholder si está disponible
            String help = plugin.getPlaceholderManager().getPlaceholderHelp(placeholder);
            if (!help.equals("Placeholder no reconocido o sin ayuda disponible")) {
                sender.sendMessage("§eDescripción: §7" + help);
            }

            sender.sendMessage("§a§l===============================");

        } catch (Exception e) {
            sender.sendMessage("§cError al probar placeholder: " + e.getMessage());
            plugin.getLogger().warning("Error en prueba de placeholder: " + e.getMessage());
        }
    }

    private void showPlaceholderDebug(CommandSender sender) {
        if (plugin.getPlaceholderManager() == null) {
            sender.sendMessage("§cSistema de placeholders no inicializado.");
            return;
        }

        sender.sendMessage("§a§l===== DEBUG DE PLACEHOLDERS =====");

        String debugInfo = plugin.getPlaceholderManager().getDebugInfo();
        String[] lines = debugInfo.split("\n");

        for (String line : lines) {
            sender.sendMessage("§e" + line);
        }

        // Información adicional del plugin
        sender.sendMessage("§ePlugin Version: §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§eSpigot Version: §f" + Bukkit.getVersion());

        // Verificar si PlaceholderAPI está presente
        boolean papiPresent = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        sender.sendMessage("§ePlaceholderAPI Present: §f" + (papiPresent ? "§aYes" : "§cNo"));

        if (papiPresent) {
            try {
                String papiVersion = Bukkit.getPluginManager().getPlugin("PlaceholderAPI").getDescription().getVersion();
                sender.sendMessage("§ePlaceholderAPI Version: §f" + papiVersion);
            } catch (Exception e) {
                sender.sendMessage("§ePlaceholderAPI Version: §cError retrieving");
            }
        }

        sender.sendMessage("§a§l===============================");
    }

    private void showDebugInfo(CommandSender sender) {
        sender.sendMessage("§a§l===== DEBUG DE MARRYCORE =====");
        sender.sendMessage("§eVersión del Plugin: §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§eVersión de Minecraft: §f" + Bukkit.getVersion());
        sender.sendMessage("§eJugadores Online: §f" + Bukkit.getOnlinePlayers().size());

        // Estado de la base de datos
        boolean dbConnected = plugin.isDatabaseConnected();
        sender.sendMessage("§eBase de Datos: " + (dbConnected ? "§aConectada" : "§cDesconectada"));

        // Estado de placeholders
        if (plugin.getPlaceholderManager() != null) {
            boolean placeholdersEnabled = plugin.getPlaceholderManager().isPlaceholderAPIEnabled();
            sender.sendMessage("§ePlaceholders: " + (placeholdersEnabled ? "§aHabilitados" : "§cDeshabilitados"));
        } else {
            sender.sendMessage("§ePlaceholders: §cNo Inicializados");
        }

        // Información de memoria
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;

        sender.sendMessage("§eMemoria Usada: §f" + usedMemory + "MB / " + maxMemory + "MB");

        // Estadísticas del sistema de matrimonio
        try {
            int[] stats = plugin.getDatabaseManager().getSystemStats();
            sender.sendMessage("§eTotal Jugadores: §f" + stats[0]);
            sender.sendMessage("§eSolteros: §f" + stats[1]);
            sender.sendMessage("§eComprometidos: §f" + stats[2]);
            sender.sendMessage("§eCasados: §f" + stats[3]);
        } catch (Exception e) {
            sender.sendMessage("§eEstadísticas: §cError al obtener");
        }

        sender.sendMessage("§a§l=============================");
    }

    // Métodos existentes del AdminCommand...
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
                // CORRECCIÓN: Usar el método mejorado que registra correctamente
                // Primero comprometer si no lo están
                plugin.getDatabaseManager().createEngagement(player1.getUniqueId(), player2.getUniqueId());

                // Luego casar CORRECTAMENTE
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

                    // NUEVO: Log para verificación
                    plugin.getLogger().info("Matrimonio forzado completado por " + sender.getName() + ": "
                            + player1.getName() + " <-> " + player2.getName());

                    // NUEVO: Verificar estado después del matrimonio
                    Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                        try {
                            var p1Data = plugin.getDatabaseManager().getPlayerData(player1.getUniqueId());
                            var p2Data = plugin.getDatabaseManager().getPlayerData(player2.getUniqueId());

                            plugin.getLogger().info("Estado verificado - " + player1.getName() + ": " + p1Data.getStatus());
                            plugin.getLogger().info("Estado verificado - " + player2.getName() + ": " + p2Data.getStatus());

                        } catch (Exception e) {
                            plugin.getLogger().warning("Error al verificar estados: " + e.getMessage());
                        }
                    }, 20L); // 1 segundo después
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al forzar matrimonio: " + e.getMessage());
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§cError al forzar el matrimonio: " + e.getMessage());
                });
            }
        });
    }

    /**
     * NUEVO MÉTODO: Verificar estado de un jugador
     */
    private void checkPlayerStatus(CommandSender sender, String playerName) {
        Player player = Bukkit.getPlayer(playerName);

        if (player == null) {
            sender.sendMessage("§cEl jugador no está conectado.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Obtener datos actuales
                var playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

                // Obtener estado real de la base de datos
                var actualStatus = plugin.getDatabaseManager().getActualMaritalStatus(player.getUniqueId());

                // Obtener información de matrimonio activo
                var marriageInfo = plugin.getDatabaseManager().getActiveMarriageInfo(player.getUniqueId());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§a§l====== VERIFICACIÓN DE ESTADO ======");
                    sender.sendMessage("§eJugador: §f" + player.getName());
                    sender.sendMessage("§eEstado en tabla jugadores: §f" + playerData.getStatus().getDisplayName());
                    sender.sendMessage("§eEstado real calculado: §f" + actualStatus.getDisplayName());

                    if (marriageInfo != null) {
                        sender.sendMessage("§eMatrimonio activo: §aYes");
                        sender.sendMessage("§eEstado matrimonio: §f" + marriageInfo.get("status"));
                        sender.sendMessage("§eFecha compromiso: §f" + marriageInfo.get("engagement_date"));
                        sender.sendMessage("§eFecha boda: §f" + marriageInfo.get("wedding_date"));
                    } else {
                        sender.sendMessage("§eMatrimonio activo: §cNo");
                    }

                    sender.sendMessage("§a§l=====================================");
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al verificar estado: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§cError al verificar el estado.");
                });
            }
        });
    }

    /**
     * NUEVO MÉTODO: Sincronizar estado de un jugador
     */
    private void syncPlayerStatus(CommandSender sender, String playerName) {
        Player player = Bukkit.getPlayer(playerName);

        if (player == null) {
            sender.sendMessage("§cEl jugador no está conectado.");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Sincronizar estado
                plugin.getDatabaseManager().synchronizePlayerStatus(player.getUniqueId());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§aEstado sincronizado para " + player.getName());
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al sincronizar estado: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    sender.sendMessage("§cError al sincronizar el estado.");
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

                    // Calcular porcentajes
                    if (stats[0] > 0) {
                        double marriedPercentage = (stats[3] * 100.0) / stats[0];
                        double engagedPercentage = (stats[2] * 100.0) / stats[0];
                        sender.sendMessage("§ePorcentaje casados: §f" + String.format("%.1f%%", marriedPercentage));
                        sender.sendMessage("§ePorcentaje comprometidos: §f" + String.format("%.1f%%", engagedPercentage));
                    }

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

                    // Mostrar placeholders de prueba si están habilitados
                    if (plugin.getPlaceholderManager() != null && plugin.getPlaceholderManager().isPlaceholderAPIEnabled()) {
                        Player onlinePlayer = Bukkit.getPlayer(playerData.getUuid());
                        if (onlinePlayer != null) {
                            sender.sendMessage("§a--- Placeholders de Prueba ---");
                            String status = plugin.getPlaceholderManager().replacePlaceholders(onlinePlayer, "%marry_status%");
                            String name = plugin.getPlaceholderManager().replacePlaceholders(onlinePlayer, "%marry_name%");
                            sender.sendMessage("§eEstado (placeholder): §f" + status);
                            sender.sendMessage("§ePareja (placeholder): §f" + name);
                        }
                    }

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
        sender.sendMessage("§e/marrycore forcedating <p1> <p2> §7- Forzar noviazgo");  // NUEVO
        sender.sendMessage("§e/marrycore forceengage <p1> <p2> §7- Forzar compromiso");
        sender.sendMessage("§e/marrycore forcemarry <p1> <p2> §7- Forzar matrimonio");
        sender.sendMessage("§e/marrycore forcedivorce <jugador> §7- Forzar divorcio");
        sender.sendMessage("§e/marrycore givering <jugador> <tipo> §7- Dar anillo");
        sender.sendMessage("§e/marrycore reset <jugador> §7- Resetear datos de jugador");
        sender.sendMessage("§e/marrycore stats §7- Ver estadísticas del sistema");
        sender.sendMessage("§e/marrycore repair §7- Reparar base de datos");
        sender.sendMessage("§e/marrycore info <jugador> §7- Ver información de jugador");
        sender.sendMessage("§6--- COMANDOS DE PLACEHOLDERS ---");
        sender.sendMessage("§e/marrycore placeholders info §7- Información de placeholders");
        sender.sendMessage("§e/marrycore placeholders list [página] §7- Listar placeholders");
        sender.sendMessage("§e/marrycore placeholders test <placeholder> [jugador] §7- Probar placeholder");
        sender.sendMessage("§e/marrycore placeholders debug §7- Debug de placeholders");
        sender.sendMessage("§6--- OTROS COMANDOS ---");
        sender.sendMessage("§e/marrycore test <placeholder> [jugador] §7- Probar placeholder específico");
        sender.sendMessage("§e/marrycore debug §7- Información de debug general");
        sender.sendMessage("§a§l===============================================");
    }
}