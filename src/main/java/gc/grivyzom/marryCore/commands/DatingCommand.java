package gc.grivyzom.marryCore.commands;

import gc.grivyzom.marryCore.MarryCore;
import gc.grivyzom.marryCore.enums.MaritalStatus;
import gc.grivyzom.marryCore.models.MarryPlayer;
import gc.grivyzom.marryCore.utils.MessageUtils;
import gc.grivyzom.marryCore.utils.ValidationUtils;
import gc.grivyzom.marryCore.items.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Comando para manejar el sistema de noviazgo.
 * Permite a los jugadores iniciar relaciones casuales antes del compromiso.
 *
 * @author Brocolitx
 * @version 0.1.0
 */
public class DatingCommand implements CommandExecutor {

    private final MarryCore plugin;
    private final MessageUtils messageUtils;
    private final ValidationUtils validationUtils;
    private final ItemManager itemManager;

    // Map para almacenar propuestas de noviazgo pendientes: UUID del propuesto -> UUID del que propone
    private static final Map<UUID, UUID> pendingDatingProposals = new HashMap<>();

    public DatingCommand(MarryCore plugin) {
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
        if (!player.hasPermission("marrycore.dating")) {
            messageUtils.sendMessage(player, "general.no-permission");
            return true;
        }

        // Manejar subcomandos
        if (args.length > 0) {
            String subCommand = args[0].toLowerCase();

            switch (subCommand) {
                case "terminar":
                case "cortar":
                case "break":
                    handleBreakup(player);
                    return true;

                case "info":
                case "estado":
                    showRelationshipInfo(player);
                    return true;

                case "ayuda":
                case "help":
                    showHelp(player);
                    return true;
            }
        }

        // Si no es un subcomando, verificar si es una propuesta de noviazgo
        if (args.length != 1) {
            messageUtils.sendMessage(player, "general.invalid-command",
                    "{usage}", "/novio <jugador> | /novio <terminar|info|ayuda>");
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
            messageUtils.sendMessage(player, "dating.proposal.self-proposal");
            return true;
        }

        // Ejecutar validaciones de forma asíncrona
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Validar estados de los jugadores
                MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());
                MarryPlayer targetData = plugin.getDatabaseManager().getPlayerData(target.getUniqueId());

                // Verificar estado del que propone
                if (!playerData.getStatus().canProposeRelationship()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String messageKey = getStatusMessage(playerData.getStatus(), "proposal", "already");
                        messageUtils.sendMessage(player, messageKey);
                    });
                    return;
                }

                // Verificar estado del objetivo
                if (!targetData.getStatus().canReceiveRelationshipProposal()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String messageKey = getStatusMessage(targetData.getStatus(), "target", "unavailable");
                        messageUtils.sendMessage(player, messageKey,
                                "{player}", target.getName());
                    });
                    return;
                }

                // Verificar distancia
                Bukkit.getScheduler().runTask(plugin, () -> {
                    double maxDistance = plugin.getConfig().getDouble("dating.proposal.max_distance", 10.0);
                    if (player.getLocation().distance(target.getLocation()) > maxDistance) {
                        messageUtils.sendMessage(player, "dating.proposal.too-far",
                                "{distance}", String.valueOf((int) maxDistance));
                        return;
                    }

                    // Crear la propuesta de noviazgo
                    createDatingProposal(player, target);
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al procesar propuesta de noviazgo: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "general.database-error");
                });
            }
        });

        return true;
    }

    /**
     * Obtiene el mensaje apropiado según el estado civil
     */
    private String getStatusMessage(MaritalStatus status, String type, String action) {
        String base = "dating." + type + "." + action + ".";

        switch (status) {
            case NOVIO:
                return base + "dating";
            case COMPROMETIDO:
                return base + "engaged";
            case CASADO:
                return base + "married";
            default:
                return base + "unavailable";
        }
    }

    /**
     * Crea una propuesta de noviazgo
     */
    private void createDatingProposal(Player proposer, Player target) {
        // Verificar si ya hay una propuesta pendiente
        if (pendingDatingProposals.containsKey(target.getUniqueId())) {
            messageUtils.sendMessage(proposer, "dating.proposal.already-pending");
            return;
        }

        // Registrar propuesta pendiente
        pendingDatingProposals.put(target.getUniqueId(), proposer.getUniqueId());

        // Enviar mensajes
        messageUtils.sendMessage(proposer, "dating.proposal.proposal-sent",
                "{player}", target.getName());

        messageUtils.sendMessage(target, "dating.proposal.proposal-received",
                "{player}", proposer.getName());
        messageUtils.sendMessage(target, "dating.proposal.proposal-instruction");

        // Reproducir efectos suaves
        playDatingProposalEffects(proposer, target);

        // Programar expiración de la propuesta
        int timeoutMinutes = plugin.getConfig().getInt("dating.proposal.timeout", 3);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            expireDatingProposal(target.getUniqueId());
        }, timeoutMinutes * 60 * 20L);
    }

    /**
     * Maneja la ruptura de una relación
     */
    private void handleBreakup(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

                if (playerData.getStatus() != MaritalStatus.NOVIO) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "dating.breakup.not-dating");
                    });
                    return;
                }

                if (!playerData.hasPartner()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "dating.breakup.no-partner");
                    });
                    return;
                }

                UUID partnerUuid = playerData.getPartnerUuid();
                Player partner = Bukkit.getPlayer(partnerUuid);

                // Obtener nombre de la pareja
                MarryPlayer partnerData = plugin.getDatabaseManager().getPlayerData(partnerUuid);
                String partnerName = partnerData.getUsername();

                // Terminar la relación en la base de datos
                plugin.getDatabaseManager().endRelationship(player.getUniqueId(), partnerUuid);

                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Enviar mensajes
                    messageUtils.sendMessage(player, "dating.breakup.success",
                            "{player}", partnerName);

                    if (partner != null) {
                        messageUtils.sendMessage(partner, "dating.breakup.notification",
                                "{player}", player.getName());
                    }

                    // Anuncio global si está habilitado
                    if (plugin.getConfig().getBoolean("dating.announcements.breakups", false)) {
                        messageUtils.broadcastMessage("dating.breakup.announcement",
                                "{player1}", player.getName(),
                                "{player2}", partnerName);
                    }
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al procesar ruptura: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "general.database-error");
                });
            }
        });
    }

    /**
     * Muestra información de la relación actual
     */
    private void showRelationshipInfo(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

                if (playerData.getStatus() == MaritalStatus.SOLTERO) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "dating.info.single");
                    });
                    return;
                }

                String partnerName = "Desconocido";
                if (playerData.hasPartner()) {
                    try {
                        MarryPlayer partnerData = plugin.getDatabaseManager().getPlayerData(playerData.getPartnerUuid());
                        partnerName = partnerData.getUsername();
                    } catch (Exception e) {
                        // Usar valor por defecto
                    }
                }

                final String finalPartnerName = partnerName;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "dating.info.relationship",
                            "{status}", playerData.getStatus().getDisplayName(),
                            "{partner}", finalPartnerName);
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
     * Muestra ayuda del comando
     */
    private void showHelp(Player player) {
        messageUtils.sendMultilineMessage(player, "dating.help");
    }

    /**
     * Reproduce efectos para propuesta de noviazgo
     */
    private void playDatingProposalEffects(Player proposer, Player target) {
        try {
            // Sonidos suaves
            proposer.playSound(proposer.getLocation(),
                    org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
            target.playSound(target.getLocation(),
                    org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);

            // Partículas suaves (menos intensas que matrimonio)
            Location proposerLoc = proposer.getLocation().add(0, 1.5, 0);
            Location targetLoc = target.getLocation().add(0, 1.5, 0);

            proposer.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, proposerLoc, 5, 0.3, 0.3, 0.3, 0.1);
            target.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, targetLoc, 5, 0.3, 0.3, 0.3, 0.1);

        } catch (Exception e) {
            // Si fallan los efectos, continuar sin ellos
        }
    }

    /**
     * Expira una propuesta de noviazgo
     */
    private void expireDatingProposal(UUID targetUUID) {
        UUID proposerUUID = pendingDatingProposals.remove(targetUUID);
        if (proposerUUID != null) {
            Player proposer = Bukkit.getPlayer(proposerUUID);
            Player target = Bukkit.getPlayer(targetUUID);

            if (proposer != null) {
                messageUtils.sendMessage(proposer, "dating.proposal.proposal-timeout");
            }

            if (target != null) {
                messageUtils.sendMessage(target, "dating.proposal.proposal-timeout");
            }
        }
    }

    // Métodos estáticos para manejar aceptación/rechazo desde otros comandos

    /**
     * Acepta una propuesta de noviazgo
     */
    public static boolean acceptDatingProposal(Player target, MarryCore plugin) {
        UUID proposerUUID = pendingDatingProposals.remove(target.getUniqueId());

        if (proposerUUID == null) {
            return false; // No hay propuesta pendiente
        }

        Player proposer = Bukkit.getPlayer(proposerUUID);
        if (proposer == null) {
            return false; // El que propuso no está conectado
        }

        // Procesar inicio de relación de forma asíncrona
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Crear relación de noviazgo
                plugin.getDatabaseManager().createRelationship(proposer.getUniqueId(), target.getUniqueId());

                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Enviar mensajes
                    MessageUtils messageUtils = new MessageUtils(plugin);
                    messageUtils.sendMessage(proposer, "dating.accept.relationship-success",
                            "{player}", target.getName());
                    messageUtils.sendMessage(target, "dating.accept.relationship-success",
                            "{player}", proposer.getName());

                    // Anuncio global si está habilitado
                    if (plugin.getConfig().getBoolean("dating.announcements.relationships", true)) {
                        messageUtils.broadcastMessage("dating.accept.relationship-announcement",
                                "{player1}", proposer.getName(),
                                "{player2}", target.getName());
                    }

                    // Efectos especiales suaves
                    try {
                        Location loc1 = proposer.getLocation().add(0, 2, 0);
                        Location loc2 = target.getLocation().add(0, 2, 0);

                        proposer.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, loc1, 10, 0.5, 0.5, 0.5, 0.1);
                        target.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, loc2, 10, 0.5, 0.5, 0.5, 0.1);
                    } catch (Exception e) {
                        // Si fallan los efectos, continuar
                    }

                    plugin.getLogger().info("Relación iniciada: " + proposer.getName() + " <-> " + target.getName());
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al procesar inicio de relación: " + e.getMessage());
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
     * Rechaza una propuesta de noviazgo
     */
    public static boolean rejectDatingProposal(Player target, MarryCore plugin) {
        UUID proposerUUID = pendingDatingProposals.remove(target.getUniqueId());

        if (proposerUUID == null) {
            return false; // No hay propuesta pendiente
        }

        Player proposer = Bukkit.getPlayer(proposerUUID);
        MessageUtils messageUtils = new MessageUtils(plugin);

        // Enviar mensajes
        messageUtils.sendMessage(target, "dating.reject.proposal-rejected",
                "{player}", proposer != null ? proposer.getName() : "Jugador desconectado");

        if (proposer != null) {
            messageUtils.sendMessage(proposer, "dating.reject.rejection-notification",
                    "{player}", target.getName());
        }

        return true;
    }

    /**
     * Verifica si un jugador tiene propuesta de noviazgo pendiente
     */
    public static boolean hasPendingDatingProposal(UUID playerUUID) {
        return pendingDatingProposals.containsKey(playerUUID);
    }

    /**
     * Limpia propuestas de jugadores desconectados
     */
    public static void cleanupDatingProposal(UUID playerUUID) {
        pendingDatingProposals.remove(playerUUID);
        // También remover si es el que propuso
        pendingDatingProposals.entrySet().removeIf(entry -> entry.getValue().equals(playerUUID));
    }
}