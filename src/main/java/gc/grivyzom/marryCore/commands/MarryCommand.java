package gc.grivyzom.marryCore.commands;

import gc.grivyzom.marryCore.MarryCore;
import gc.grivyzom.marryCore.enums.MaritalStatus;
import gc.grivyzom.marryCore.models.MarryPlayer;
import gc.grivyzom.marryCore.utils.MessageUtils;
import gc.grivyzom.marryCore.utils.ValidationUtils;
import gc.grivyzom.marryCore.items.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

        // Verificar argumentos
        if (args.length != 1) {
            messageUtils.sendMessage(player, "general.invalid-command",
                    "{usage}", "/marry <jugador>");
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

                // Verificar estado del que propone
                if (playerData.getStatus() != MaritalStatus.SOLTERO) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (playerData.getStatus() == MaritalStatus.CASADO) {
                            messageUtils.sendMessage(player, "marriage.proposal.already-married");
                        } else {
                            messageUtils.sendMessage(player, "marriage.proposal.already-engaged");
                        }
                    });
                    return;
                }

                // Verificar estado del objetivo
                if (targetData.getStatus() != MaritalStatus.SOLTERO) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (targetData.getStatus() == MaritalStatus.CASADO) {
                            messageUtils.sendMessage(player, "marriage.proposal.target-married",
                                    "{player}", target.getName());
                        } else {
                            messageUtils.sendMessage(player, "marriage.proposal.target-engaged",
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

                    // Crear la propuesta
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

        // Enviar mensajes
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
        }, timeoutMinutes * 60 * 20L); // Convertir minutos a ticks
    }

    private void expireProposal(UUID targetUUID) {
        UUID proposerUUID = pendingProposals.remove(targetUUID);
        if (proposerUUID != null) {
            Player proposer = Bukkit.getPlayer(proposerUUID);
            Player target = Bukkit.getPlayer(targetUUID);

            if (proposer != null) {
                messageUtils.sendMessage(proposer, "marriage.proposal.proposal-timeout");
                // Devolver anillo de propuesta
                itemManager.giveProposalRing(proposer);
            }

            if (target != null) {
                messageUtils.sendMessage(target, "marriage.proposal.proposal-timeout");
            }
        }
    }

    // Método para manejar cuando un jugador acepta una propuesta
    public static boolean acceptProposal(Player target) {
        UUID proposerUUID = pendingProposals.remove(target.getUniqueId());

        if (proposerUUID == null) {
            return false; // No hay propuesta pendiente
        }

        Player proposer = Bukkit.getPlayer(proposerUUID);
        if (proposer == null) {
            return false; // El que propuso no está conectado
        }

        MarryCore plugin = MarryCore.getPlugin(MarryCore.class);

        // Procesar compromiso de forma asíncrona
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Cambiar estados en la base de datos
                plugin.getDatabaseManager().createEngagement(proposer.getUniqueId(), target.getUniqueId());

                // Volver al hilo principal para operaciones del servidor
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Dar anillos nupciales
                    ItemManager itemManager = new ItemManager(plugin);
                    itemManager.giveEngagementRing(proposer, target.getName());
                    itemManager.giveEngagementRing(target, proposer.getName());

                    // Enviar mensajes
                    MessageUtils messageUtils = new MessageUtils(plugin);
                    messageUtils.sendMessage(proposer, "marriage.accept.engagement-success",
                            "{player}", target.getName());
                    messageUtils.sendMessage(target, "marriage.accept.engagement-success",
                            "{player}", proposer.getName());

                    messageUtils.sendMessage(proposer, "marriage.accept.rings-received");
                    messageUtils.sendMessage(target, "marriage.accept.rings-received");

                    // Anuncio global si está habilitado
                    if (plugin.getConfig().getBoolean("marriage.proposal.announce_engagements", true)) {
                        messageUtils.broadcastMessage("marriage.accept.engagement-announcement",
                                "{player1}", proposer.getName(),
                                "{player2}", target.getName());
                    }

                    // Efectos especiales
                    itemManager.playEngagementEffects(proposer, target);
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

    // Método para rechazar una propuesta
    public static boolean rejectProposal(Player target) {
        UUID proposerUUID = pendingProposals.remove(target.getUniqueId());

        if (proposerUUID == null) {
            return false; // No hay propuesta pendiente
        }

        Player proposer = Bukkit.getPlayer(proposerUUID);
        MarryCore plugin = MarryCore.getPlugin(MarryCore.class);
        MessageUtils messageUtils = new MessageUtils(plugin);
        ItemManager itemManager = new ItemManager(plugin);

        // Enviar mensajes
        messageUtils.sendMessage(target, "marriage.reject.proposal-rejected",
                "{player}", proposer != null ? proposer.getName() : "Jugador desconectado");

        if (proposer != null) {
            messageUtils.sendMessage(proposer, "marriage.reject.rejection-notification",
                    "{player}", target.getName());
            // Devolver anillo de propuesta
            itemManager.giveProposalRing(proposer);
        }

        return true;
    }

    // Método para verificar si un jugador tiene propuesta pendiente
    public static boolean hasPendingProposal(UUID playerUUID) {
        return pendingProposals.containsKey(playerUUID);
    }

    // Método para limpiar propuestas de jugadores desconectados
    public static void cleanupProposal(UUID playerUUID) {
        pendingProposals.remove(playerUUID);
        // También remover si es el que propuso
        pendingProposals.entrySet().removeIf(entry -> entry.getValue().equals(playerUUID));
    }
}