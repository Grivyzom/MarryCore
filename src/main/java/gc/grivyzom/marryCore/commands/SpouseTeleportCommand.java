package gc.grivyzom.marryCore.commands;

import gc.grivyzom.marryCore.MarryCore;
import gc.grivyzom.marryCore.enums.MaritalStatus;
import gc.grivyzom.marryCore.models.MarryPlayer;
import gc.grivyzom.marryCore.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Comando para teletransportarse al cónyuge.
 *
 * @author Brocolitx
 * @version 0.0.1
 */
public class SpouseTeleportCommand implements CommandExecutor {

    private final MarryCore plugin;
    private final MessageUtils messageUtils;

    // Cooldowns de teletransporte por jugador
    private static final Map<UUID, Long> teleportCooldowns = new HashMap<>();
    // Jugadores en proceso de teletransporte
    private static final Map<UUID, Integer> teleportingPlayers = new HashMap<>();

    public SpouseTeleportCommand(MarryCore plugin) {
        this.plugin = plugin;
        this.messageUtils = new MessageUtils(plugin);
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
        if (!player.hasPermission("marrycore.teleport")) {
            messageUtils.sendMessage(player, "general.no-permission");
            return true;
        }

        // Verificar si el teletransporte está habilitado
        if (!plugin.getConfig().getBoolean("benefits.teleport.enabled", true)) {
            messageUtils.sendMessage(player, "benefits.teleport.disabled");
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

                // Verificar que esté casado
                if (playerData.getStatus() != MaritalStatus.CASADO) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "benefits.teleport.not-married");
                    });
                    return;
                }

                // Verificar que tenga pareja
                if (!playerData.hasPartner()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "benefits.teleport.no-partner");
                    });
                    return;
                }

                // Verificar que la pareja esté conectada
                Player partner = Bukkit.getPlayer(playerData.getPartnerUuid());
                if (partner == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "benefits.teleport.partner-offline");
                    });
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Verificar cooldown
                    if (hasCooldown(player)) {
                        long remainingTime = getRemainingCooldown(player);
                        messageUtils.sendMessage(player, "benefits.teleport.cooldown",
                                "{time}", formatTime(remainingTime));
                        return;
                    }

                    // Verificar que no esté en combate (si está habilitado)
                    if (isInCombat(player)) {
                        messageUtils.sendMessage(player, "benefits.teleport.in-combat");
                        return;
                    }

                    // Verificar costo de experiencia
                    int expCost = plugin.getConfig().getInt("benefits.teleport.experience_cost", 1);
                    if (player.getLevel() < expCost) {
                        messageUtils.sendMessage(player, "benefits.teleport.insufficient-experience",
                                "{cost}", String.valueOf(expCost));
                        return;
                    }

                    // Iniciar teletransporte
                    startTeleport(player, partner, expCost);
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al procesar teletransporte: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "general.database-error");
                });
            }
        });

        return true;
    }

    private boolean hasCooldown(Player player) {
        Long lastTeleport = teleportCooldowns.get(player.getUniqueId());
        if (lastTeleport == null) return false;

        int cooldownMinutes = plugin.getConfig().getInt("benefits.teleport.cooldown", 30);
        long cooldownMs = cooldownMinutes * 60 * 1000L;

        return (System.currentTimeMillis() - lastTeleport) < cooldownMs;
    }

    private long getRemainingCooldown(Player player) {
        Long lastTeleport = teleportCooldowns.get(player.getUniqueId());
        if (lastTeleport == null) return 0;

        int cooldownMinutes = plugin.getConfig().getInt("benefits.teleport.cooldown", 30);
        long cooldownMs = cooldownMinutes * 60 * 1000L;
        long elapsed = System.currentTimeMillis() - lastTeleport;

        return Math.max(0, cooldownMs - elapsed);
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        } else {
            return seconds + "s";
        }
    }

    private boolean isInCombat(Player player) {
        // TODO: Implementar verificación de combate
        // Esto podría integrarse con plugins de combate tag
        return false;
    }

    private void startTeleport(Player player, Player partner, int expCost) {
        Location originalLocation = player.getLocation().clone();
        int warmupTime = plugin.getConfig().getInt("benefits.teleport.warmup_time", 3);

        messageUtils.sendMessage(player, "benefits.teleport.teleporting",
                "{player}", partner.getName());

        if (warmupTime <= 0) {
            // Teletransporte instantáneo
            performTeleport(player, partner, expCost);
            return;
        }

        // Teletransporte con tiempo de carga
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            teleportingPlayers.remove(player.getUniqueId());

            // Verificar que el jugador no se haya movido (si está habilitado)
            if (plugin.getConfig().getBoolean("benefits.teleport.cancel_on_move", true)) {
                if (player.getLocation().distance(originalLocation) > 1.0) {
                    messageUtils.sendMessage(player, "benefits.teleport.cancelled-moved");
                    return;
                }
            }

            // Verificar que la pareja siga conectada
            if (!partner.isOnline()) {
                messageUtils.sendMessage(player, "benefits.teleport.partner-offline");
                return;
            }

            performTeleport(player, partner, expCost);

        }, warmupTime * 20L).getTaskId();

        teleportingPlayers.put(player.getUniqueId(), taskId);
    }

    private void performTeleport(Player player, Player partner, int expCost) {
        // Consumir experiencia
        player.setLevel(player.getLevel() - expCost);

        // Teletransportar
        Location targetLocation = partner.getLocation().clone();
        targetLocation.add(1, 0, 0); // Offset para no aparecer encima

        player.teleport(targetLocation);

        // Registrar cooldown
        teleportCooldowns.put(player.getUniqueId(), System.currentTimeMillis());

        // Enviar mensajes
        messageUtils.sendMessage(player, "benefits.teleport.teleport-success");
        messageUtils.sendMessage(partner, "benefits.teleport.spouse-teleported",
                "{player}", player.getName());

        // Efectos de partículas
        partner.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, targetLocation, 20, 0.5, 1, 0.5, 0);
    }

    // Método para cancelar teletransporte si el jugador se desconecta
    public static void cancelTeleport(UUID playerUuid) {
        Integer taskId = teleportingPlayers.remove(playerUuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    // Método para limpiar cooldowns al desconectarse
    public static void cleanupCooldown(UUID playerUuid) {
        teleportCooldowns.remove(playerUuid);
        cancelTeleport(playerUuid);
    }
}