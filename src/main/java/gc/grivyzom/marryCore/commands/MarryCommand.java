package gc.grivyzom.marryCore.commands;

import gc.grivyzom.marryCore.MarryCore;
import gc.grivyzom.marryCore.enums.MaritalStatus;
import gc.grivyzom.marryCore.models.MarryPlayer;
import gc.grivyzom.marryCore.utils.MessageUtils;
import gc.grivyzom.marryCore.utils.ValidationUtils;
import gc.grivyzom.marryCore.items.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Comando principal de matrimonio actualizado para incluir el sistema de noviazgo.
 * ACTUALIZADO: Ahora maneja la progresión: Noviazgo -> Compromiso -> Matrimonio
 *
 * @author Brocolitx
 * @version 0.1.0
 */
public class MarryCommand implements CommandExecutor {

    private final MarryCore plugin;
    private final MessageUtils messageUtils;
    private final ValidationUtils validationUtils;
    private final ItemManager itemManager;

    // Map para almacenar propuestas pendientes: UUID del propuesto -> UUID del que propone
    private static final Map<UUID, UUID> pendingProposals = new HashMap<>();

    public MarryCommand(MarryCore plugin) {
        this.plugin = plugin;
        this.messageUtils = new MessageUtils(plugin);
        this.validationUtils = new ValidationUtils(plugin);
        this.itemManager = new ItemManager(plugin);
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
        if (!player.hasPermission("marrycore.marry")) {
            messageUtils.sendMessage(player, "general.no-permission");
            return true;
        }

        // FUNCIONALIDAD EXISTENTE: Comando para hacer regalos
        if (args.length > 0 && args[0].equalsIgnoreCase("gift")) {
            return handleGiftCommand(player);
        }

        // FUNCIONALIDAD EXISTENTE: Comando para teletransporte
        if (args.length > 0 && args[0].equalsIgnoreCase("tp")) {
            return handleTeleportCommand(player);
        }

        // FUNCIONALIDAD EXISTENTE: Guía de flores
        if (args.length > 0 && args[0].equalsIgnoreCase("flores")) {
            showFlowerGuide(player);
            return true;
        }

        // NUEVA FUNCIONALIDAD: Mostrar información de relación
        if (args.length > 0 && args[0].equalsIgnoreCase("info")) {
            showRelationshipInfo(player);
            return true;
        }

        // NUEVA FUNCIONALIDAD: Avanzar en la relación
        if (args.length > 0 && args[0].equalsIgnoreCase("avanzar")) {
            handleAdvanceRelationship(player);
            return true;
        }

        // Verificar argumentos para propuesta
        if (args.length != 1) {
            messageUtils.sendMessage(player, "general.invalid-command",
                    "{usage}", "/marry <jugador> | /marry <info|avanzar|gift|tp|flores>");
            return true;
        }

        String targetName = args[0];

        // Buscar jugador objetivo
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            messageUtils.sendMessage(player, "general.player-not-found",
                    "{player}", targetName);
            return true;
        }

        // No puede proponerse a sí mismo
        if (player.equals(target)) {
            messageUtils.sendMessage(player, "marriage.proposal.self-proposal");
            return true;
        }

        // Verificar que el jugador tenga anillo de propuesta
        if (!itemManager.hasProposalRing(player)) {
            messageUtils.sendMessage(player, "marriage.proposal.no-ring");
            return true;
        }

        // Ejecutar validaciones de forma asíncrona
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Validar estados de los jugadores
                MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());
                MarryPlayer targetData = plugin.getDatabaseManager().getPlayerData(target.getUniqueId());

                // ACTUALIZADO: Verificar estado del que propone (solo solteros pueden proponer compromiso)
                if (playerData.getStatus() != MaritalStatus.SOLTERO) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (playerData.getStatus() == MaritalStatus.NOVIO) {
                            messageUtils.sendMessage(player, "marriage.proposal.already-dating");
                        } else if (playerData.getStatus() == MaritalStatus.COMPROMETIDO) {
                            messageUtils.sendMessage(player, "marriage.proposal.already-engaged");
                        } else {
                            messageUtils.sendMessage(player, "marriage.proposal.already-married");
                        }
                    });
                    return;
                }

                // ACTUALIZADO: Verificar estado del objetivo
                if (targetData.getStatus() != MaritalStatus.SOLTERO) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (targetData.getStatus() == MaritalStatus.NOVIO) {
                            messageUtils.sendMessage(player, "marriage.proposal.target-dating",
                                    "{player}", target.getName());
                        } else if (targetData.getStatus() == MaritalStatus.COMPROMETIDO) {
                            messageUtils.sendMessage(player, "marriage.proposal.target-engaged",
                                    "{player}", target.getName());
                        } else {
                            messageUtils.sendMessage(player, "marriage.proposal.target-married",
                                    "{player}", target.getName());
                        }
                    });
                    return;
                }

                // Verificar distancia
                Bukkit.getScheduler().runTask(plugin, () -> {
                    double maxDistance = plugin.getConfig().getDouble("marriage.proposal.max_distance", 10.0);
                    if (player.getLocation().distance(target.getLocation()) > maxDistance) {
                        messageUtils.sendMessage(player, "marriage.proposal.too-far",
                                "{distance}", String.valueOf((int) maxDistance));
                        return;
                    }

                    // ACTUALIZADO: Ahora las propuestas de matrimonio son para compromiso directo
                    createProposal(player, target);
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al procesar propuesta de matrimonio: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "general.database-error");
                });
            }
        });

        return true;
    }

    /**
     * NUEVA FUNCIONALIDAD: Muestra información de la relación actual
     */
    private void showRelationshipInfo(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getDatabaseManager().synchronizePlayerStatus(player.getUniqueId());
                MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());
                MaritalStatus actualStatus = plugin.getDatabaseManager().getActualMaritalStatus(player.getUniqueId());

                if (actualStatus == MaritalStatus.SOLTERO) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "relationship.info.single");
                    });
                    return;
                }

                // Obtener información de la relación
                Map<String, Object> relationshipInfo = plugin.getDatabaseManager().getActiveRelationshipInfo(player.getUniqueId());

                if (relationshipInfo == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "relationship.info.no-data");
                    });
                    return;
                }

                // Determinar nombre de la pareja
                String player1Uuid = (String) relationshipInfo.get("player1_uuid");
                String partnerName;

                if (player.getUniqueId().toString().equals(player1Uuid)) {
                    partnerName = (String) relationshipInfo.get("player2_name");
                } else {
                    partnerName = (String) relationshipInfo.get("player1_name");
                }

                // Calcular tiempo de relación
                java.sql.Timestamp startDate = (java.sql.Timestamp) relationshipInfo.get("engagement_date");
                long daysTogether = java.time.temporal.ChronoUnit.DAYS.between(
                        startDate.toLocalDateTime().toLocalDate(),
                        java.time.LocalDate.now()
                );

                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "relationship.info.header");
                    messageUtils.sendMessage(player, "relationship.info.status",
                            "{status}", actualStatus.getDisplayName());
                    messageUtils.sendMessage(player, "relationship.info.partner",
                            "{partner}", partnerName);
                    messageUtils.sendMessage(player, "relationship.info.time",
                            "{days}", String.valueOf(daysTogether));

                    // Mostrar opciones de avance si es posible
                    if (actualStatus.canAdvance()) {
                        MaritalStatus nextStatus = actualStatus.getNextStatus();
                        messageUtils.sendMessage(player, "relationship.info.can-advance",
                                "{next_status}", nextStatus.getDisplayName());
                        messageUtils.sendMessage(player, "relationship.info.advance-command");
                    }

                    messageUtils.sendMessage(player, "relationship.info.footer");
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al obtener información de relación: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "general.database-error");
                });
            }
        });
    }

    /**
     * NUEVA FUNCIONALIDAD: Avanzar en la relación (de novios a comprometidos)
     */
    private void handleAdvanceRelationship(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

                // Solo los novios pueden avanzar a compromiso
                if (playerData.getStatus() != MaritalStatus.NOVIO) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (playerData.getStatus() == MaritalStatus.SOLTERO) {
                            messageUtils.sendMessage(player, "relationship.advance.not-dating");
                        } else if (playerData.getStatus() == MaritalStatus.COMPROMETIDO) {
                            messageUtils.sendMessage(player, "relationship.advance.already-engaged");
                        } else {
                            messageUtils.sendMessage(player, "relationship.advance.already-married");
                        }
                    });
                    return;
                }

                if (!playerData.hasPartner()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "relationship.advance.no-partner");
                    });
                    return;
                }

                // Verificar que la pareja esté online
                Player partner = Bukkit.getPlayer(playerData.getPartnerUuid());
                if (partner == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "relationship.advance.partner-offline");
                    });
                    return;
                }

                // Verificar que ambos puedan avanzar
                if (!plugin.getDatabaseManager().canAdvanceRelationship(player.getUniqueId(), playerData.getPartnerUuid())) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "relationship.advance.cannot-advance");
                    });
                    return;
                }

                // Avanzar a compromiso
                plugin.getDatabaseManager().createEngagement(player.getUniqueId(), playerData.getPartnerUuid());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Dar anillos nupciales
                    itemManager.giveEngagementRing(player, partner.getName());
                    itemManager.giveEngagementRing(partner, player.getName());

                    // Enviar mensajes
                    messageUtils.sendMessage(player, "relationship.advance.engagement-success",
                            "{player}", partner.getName());
                    messageUtils.sendMessage(partner, "relationship.advance.engagement-success",
                            "{player}", player.getName());

                    messageUtils.sendMessage(player, "marriage.accept.rings-received");
                    messageUtils.sendMessage(partner, "marriage.accept.rings-received");

                    // Anuncio global si está habilitado
                    if (plugin.getConfig().getBoolean("marriage.proposal.announce_engagements", true)) {
                        messageUtils.broadcastMessage("marriage.accept.engagement-announcement",
                                "{player1}", player.getName(),
                                "{player2}", partner.getName());
                    }

                    // Efectos especiales
                    itemManager.playEngagementEffects(player, partner);

                    plugin.getLogger().info("Relación avanzada a compromiso: " + player.getName() + " <-> " + partner.getName());
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al avanzar relación: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "general.database-error");
                });
            }
        });
    }

    /**
     * MÉTODO ACTUALIZADO: Crear propuesta (ahora para compromiso directo)
     */
    private void createProposal(Player proposer, Player target) {
        // Verificar si ya hay una propuesta pendiente
        if (pendingProposals.containsKey(target.getUniqueId())) {
            messageUtils.sendMessage(proposer, "marriage.proposal.already-pending");
            return;
        }

        // Consumir anillo de propuesta
        if (!itemManager.consumeProposalRing(proposer)) {
            messageUtils.sendMessage(proposer, "marriage.proposal.no-ring");
            return;
        }

        // Registrar propuesta pendiente
        pendingProposals.put(target.getUniqueId(), proposer.getUniqueId());

        // Enviar mensajes (ahora se refiere a compromiso)
        messageUtils.sendMessage(proposer, "marriage.proposal.proposal-sent",
                "{player}", target.getName());

        messageUtils.sendMessage(target, "marriage.proposal.proposal-received",
                "{player}", proposer.getName());
        messageUtils.sendMessage(target, "marriage.proposal.proposal-instruction");

        // Reproducir efectos
        itemManager.playProposalEffects(proposer, target);

        // Programar expiración de la propuesta
        int timeoutMinutes = plugin.getConfig().getInt("marriage.proposal.timeout", 5);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            expireProposal(target.getUniqueId());
        }, timeoutMinutes * 60 * 20L);
    }

    /**
     * MÉTODO EXISTENTE: Manejar regalos (sin cambios)
     */
    private boolean handleGiftCommand(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getDatabaseManager().synchronizePlayerStatus(player.getUniqueId());
                MarryPlayer mp = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());
                MaritalStatus actualStatus = plugin.getDatabaseManager().getActualMaritalStatus(player.getUniqueId());

                // ACTUALIZADO: Ahora incluye novios
                if (!actualStatus.hasRelationshipBenefits() || mp.getPartnerUuid() == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c♥ Debes estar en una relación para compartir regalos.");
                    });
                    return;
                }

                Map<String, Object> relationshipInfo = plugin.getDatabaseManager().getActiveRelationshipInfo(player.getUniqueId());

                if (relationshipInfo == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c♥ No se encontró información de tu relación activa.");
                    });
                    return;
                }

                String player1Uuid = (String) relationshipInfo.get("player1_uuid");
                String player2Uuid = (String) relationshipInfo.get("player2_uuid");

                UUID partnerUuid;
                String partnerName;

                if (player.getUniqueId().toString().equals(player1Uuid)) {
                    partnerUuid = UUID.fromString(player2Uuid);
                    partnerName = (String) relationshipInfo.get("player2_name");
                } else {
                    partnerUuid = UUID.fromString(player1Uuid);
                    partnerName = (String) relationshipInfo.get("player1_name");
                }

                Player pareja = Bukkit.getPlayer(partnerUuid);
                if (pareja == null || !pareja.isOnline()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c♥ Tu pareja " + partnerName + " no está en línea.");
                    });
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    ItemStack item = player.getInventory().getItemInMainHand();
                    if (item == null || item.getType() == Material.AIR) {
                        player.sendMessage("§c♥ Debes sostener el item que quieras regalar en tu mano.");
                        return;
                    }

                    if (isProtectedItem(item)) {
                        player.sendMessage("§c♥ No puedes regalar este ítem especial.");
                        return;
                    }

                    ItemStack regalo = item.clone();
                    regalo.setAmount(1);

                    if (pareja.getInventory().firstEmpty() == -1) {
                        player.sendMessage("§c♥ El inventario de tu pareja está lleno.");
                        return;
                    }

                    pareja.getInventory().addItem(regalo);

                    if (item.getAmount() > 1) {
                        item.setAmount(item.getAmount() - 1);
                    } else {
                        player.getInventory().setItemInMainHand(null);
                    }

                    String itemName = regalo.getType().name().toLowerCase().replace("_", " ");
                    player.sendMessage("§a♥ Has regalado §f" + itemName + " §aa tu pareja §e" + pareja.getName() + "§a! ♥");
                    pareja.sendMessage("§a♥ Tu pareja §e" + player.getName() + " §ate ha regalado §f" + itemName + "§a! ♥");

                    try {
                        Location locPlayer = player.getLocation().add(0, 2, 0);
                        Location locPareja = pareja.getLocation().add(0, 2, 0);

                        player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, locPlayer, 10, 0.5, 0.5, 0.5, 0.1);
                        pareja.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, locPareja, 10, 0.5, 0.5, 0.5, 0.1);
                    } catch (Exception e) {
                        // Si las partículas fallan, continuar
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().warning("Error en sistema de regalos: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§c♥ Error al procesar el regalo.");
                });
            }
        });

        return true;
    }

    /**
     * MÉTODO EXISTENTE: Manejar teletransporte (ahora incluye novios)
     */
    private boolean handleTeleportCommand(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());
                MaritalStatus actualStatus = plugin.getDatabaseManager().getActualMaritalStatus(player.getUniqueId());

                // ACTUALIZADO: Verificar que tenga beneficios de relación
                if (!actualStatus.hasRelationshipBenefits()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c♥ Debes estar en una relación para teletransportarte a tu pareja.");
                    });
                    return;
                }

                if (!playerData.hasPartner()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c♥ No tienes pareja a quien teletransportarte.");
                    });
                    return;
                }

                Player partner = Bukkit.getPlayer(playerData.getPartnerUuid());
                if (partner == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c♥ Tu pareja no está en línea.");
                    });
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.getWorld().equals(partner.getWorld())) {
                        player.sendMessage("§c♥ Tu pareja está en otro mundo.");
                        return;
                    }

                    Location targetLocation = partner.getLocation().clone();
                    targetLocation.add(1, 0, 0);

                    player.teleport(targetLocation);
                    player.sendMessage("§a♥ Te has teletransportado a tu pareja §e" + partner.getName() + "§a! ♥");
                    partner.sendMessage("§a♥ Tu pareja §e" + player.getName() + " §ase ha teletransportado a ti! ♥");

                    try {
                        partner.getWorld().spawnParticle(Particle.PORTAL, targetLocation, 20, 0.5, 1, 0.5, 0);
                    } catch (Exception e) {
                        // Si las partículas fallan, continuar
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al procesar teletransporte: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§c♥ Error al teletransportarte.");
                });
            }
        });

        return true;
    }

    /**
     * MÉTODO EXISTENTE: Guía de flores (sin cambios)
     */
    private void showFlowerGuide(Player player) {
        player.sendMessage("§d§l======= GUÍA DE FLORES ROMÁNTICAS =======");
        player.sendMessage("§e🌸 Para regalar: §aSostén una flor y haz click derecho sobre tu pareja");
        player.sendMessage("§e⏱️ Cooldown: §c60 segundos entre regalos");
        player.sendMessage("");
        player.sendMessage("§a§lEfectos de las flores:");
        player.sendMessage("§e🌻 Diente de León: §fVelocidad II (10s)");
        player.sendMessage("§c🌺 Amapola: §fSalto II (8s)");
        player.sendMessage("§9🌸 Orquídea Azul: §fRegeneración I (5s)");
        // ... resto de la guía
        player.sendMessage("§d§l=======================================");
    }

    /**
     * MÉTODO EXISTENTE: Verificar ítems protegidos (sin cambios)
     */
    private boolean isProtectedItem(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }

        String displayName = item.getItemMeta().getDisplayName();

        if (itemManager.isProposalRing(item)) {
            return true;
        }

        if (displayName.contains("💍 Anillo Nupcial 💍")) {
            return true;
        }

        if (displayName.contains("💖 Anillo de Boda 💖")) {
            return true;
        }

        if (displayName.contains("📜 Invitación de Boda 📜")) {
            return true;
        }

        return false;
    }

    /**
     * MÉTODO EXISTENTE: Expirar propuesta (sin cambios)
     */
    private void expireProposal(UUID targetUUID) {
        UUID proposerUUID = pendingProposals.remove(targetUUID);
        if (proposerUUID != null) {
            Player proposer = Bukkit.getPlayer(proposerUUID);
            Player target = Bukkit.getPlayer(targetUUID);

            if (proposer != null) {
                messageUtils.sendMessage(proposer, "marriage.proposal.proposal-timeout");
                itemManager.giveProposalRing(proposer);
            }

            if (target != null) {
                messageUtils.sendMessage(target, "marriage.proposal.proposal-timeout");
            }
        }
    }

    // MÉTODOS ESTÁTICOS ACTUALIZADOS

    /**
     * MÉTODO ACTUALIZADO: Aceptar propuesta (ahora crea compromiso directo)
     */
    public static boolean acceptProposal(Player target) {
        UUID proposerUUID = pendingProposals.remove(target.getUniqueId());

        if (proposerUUID == null) {
            return false;
        }

        Player proposer = Bukkit.getPlayer(proposerUUID);
        if (proposer == null) {
            return false;
        }

        MarryCore plugin = MarryCore.getPlugin(MarryCore.class);

        // ACTUALIZADO: Ahora crea compromiso directo (no noviazgo)
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getDatabaseManager().createEngagement(proposer.getUniqueId(), target.getUniqueId());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    ItemManager itemManager = new ItemManager(plugin);
                    itemManager.giveEngagementRing(proposer, target.getName());
                    itemManager.giveEngagementRing(target, proposer.getName());

                    MessageUtils messageUtils = new MessageUtils(plugin);
                    messageUtils.sendMessage(proposer, "marriage.accept.engagement-success",
                            "{player}", target.getName());
                    messageUtils.sendMessage(target, "marriage.accept.engagement-success",
                            "{player}", proposer.getName());

                    messageUtils.sendMessage(proposer, "marriage.accept.rings-received");
                    messageUtils.sendMessage(target, "marriage.accept.rings-received");

                    if (plugin.getConfig().getBoolean("marriage.proposal.announce_engagements", true)) {
                        messageUtils.broadcastMessage("marriage.accept.engagement-announcement",
                                "{player1}", proposer.getName(),
                                "{player2}", target.getName());
                    }

                    itemManager.playEngagementEffects(proposer, target);

                    plugin.getLogger().info("Compromiso registrado: " + proposer.getName() + " <-> " + target.getName());
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al procesar compromiso: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    MessageUtils messageUtils = new MessageUtils(plugin);
                    messageUtils.sendMessage(proposer, "general.database-error");
                    messageUtils.sendMessage(target, "general.database-error");
                });
            }
        });

        return true;
    }

    /**
     * MÉTODO EXISTENTE: Rechazar propuesta (sin cambios)
     */
    public static boolean rejectProposal(Player target) {
        UUID proposerUUID = pendingProposals.remove(target.getUniqueId());

        if (proposerUUID == null) {
            return false;
        }

        Player proposer = Bukkit.getPlayer(proposerUUID);
        MarryCore plugin = MarryCore.getPlugin(MarryCore.class);
        MessageUtils messageUtils = new MessageUtils(plugin);
        ItemManager itemManager = new ItemManager(plugin);

        messageUtils.sendMessage(target, "marriage.reject.proposal-rejected",
                "{player}", proposer != null ? proposer.getName() : "Jugador desconectado");

        if (proposer != null) {
            messageUtils.sendMessage(proposer, "marriage.reject.rejection-notification",
                    "{player}", target.getName());
            itemManager.giveProposalRing(proposer);
        }

        return true;
    }

    /**
     * MÉTODOS ESTÁTICOS EXISTENTES (sin cambios)
     */
    public static boolean hasPendingProposal(UUID playerUUID) {
        return pendingProposals.containsKey(playerUUID);
    }

    public static void cleanupProposal(UUID playerUUID) {
        pendingProposals.remove(playerUUID);
        pendingProposals.entrySet().removeIf(entry -> entry.getValue().equals(playerUUID));
    }
}