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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Listener para manejar eventos de entrada y salida de jugadores.
 * Se encarga de actualizar información en la base de datos y limpiar datos temporales.
 * ACTUALIZADO: Incluye limpieza de cooldowns de besos y sincronización de estados.
 *
 * @author Brocolitx
 * @version 0.0.1
 */
public class PlayerJoinListener implements Listener {

    private final MarryCore plugin;

    public PlayerJoinListener(MarryCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Actualizar información del jugador en la base de datos de forma asíncrona
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Obtener o crear datos del jugador (esto actualiza el username automáticamente)
                plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

                // Actualizar nombre de usuario si ha cambiado
                plugin.getDatabaseManager().updatePlayerUsername(player.getUniqueId(), player.getName());

                // NUEVO: Sincronizar estado del jugador con la base de datos
                plugin.getDatabaseManager().synchronizePlayerStatus(player.getUniqueId());

                // FUNCIONALIDAD EXISTENTE: Notificar a la pareja que se conectó
                MarryPlayer mpJoin = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());
                if ((mpJoin.getStatus() == MaritalStatus.CASADO || mpJoin.getStatus() == MaritalStatus.COMPROMETIDO)
                        && mpJoin.getPartnerUuid() != null) {

                    Player pareja = Bukkit.getPlayer(mpJoin.getPartnerUuid());
                    if (pareja != null && pareja.isOnline()) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            pareja.sendMessage("§a♥ Tu pareja §f" + player.getName() + " §ase ha conectado.");
                        });
                    }
                }

                // Log de debug si está habilitado
                if (plugin.getConfig().getBoolean("general.debug", false)) {
                    plugin.getLogger().info("Datos actualizados y sincronizados para " + player.getName() + " (" + player.getUniqueId() + ")");
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Error al actualizar datos del jugador " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // FUNCIONALIDAD EXISTENTE: Notificar a la pareja que se desconectó
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MarryPlayer mpQuit = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());
                if ((mpQuit.getStatus() == MaritalStatus.CASADO || mpQuit.getStatus() == MaritalStatus.COMPROMETIDO)
                        && mpQuit.getPartnerUuid() != null) {

                    Player pareja = Bukkit.getPlayer(mpQuit.getPartnerUuid());
                    if (pareja != null && pareja.isOnline()) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            pareja.sendMessage("§c♥ Tu pareja §f" + player.getName() + " §cse ha desconectado.");
                        });
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Error al notificar desconexión: " + e.getMessage());
            }
        });

        // LIMPIEZA EXPANDIDA: Limpiar todos los datos temporales
        cleanupPlayerData(player);

        // Log de debug si está habilitado
        if (plugin.getConfig().getBoolean("general.debug", false)) {
            plugin.getLogger().info("Limpieza completa de datos temporales para " + player.getName());
        }
    }

    /**
     * NUEVO MÉTODO: Limpia todos los datos temporales de un jugador
     */
    private void cleanupPlayerData(Player player) {
        UUID playerUuid = player.getUniqueId();

        // Limpiar propuestas pendientes
        MarryCommand.cleanupProposal(playerUuid);

        // NUEVO: Limpiar cooldowns de besos
        KissListener.cleanupKissCooldown(playerUuid);

        // Limpiar confirmaciones de divorcio
        try {
            // Importar y usar el método de limpieza de DivorceCommand
            gc.grivyzom.marryCore.commands.DivorceCommand.cleanupConfirmation(playerUuid);
        } catch (Exception e) {
            // Si no existe el método, continuar
        }

        // Limpiar cooldowns de teletransporte
        try {
            // Importar y usar el método de limpieza de SpouseTeleportCommand
            gc.grivyzom.marryCore.commands.SpouseTeleportCommand.cleanupCooldown(playerUuid);
        } catch (Exception e) {
            // Si no existe el método, continuar
        }

        plugin.getLogger().info("Datos temporales limpiados para: " + player.getName());
    }
}