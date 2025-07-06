package gc.grivyzom.marryCore.listeners;

import gc.grivyzom.marryCore.MarryCore;
import gc.grivyzom.marryCore.commands.MarryCommand;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.SQLException;

/**
 * Listener para manejar eventos de entrada y salida de jugadores.
 * Se encarga de actualizar información en la base de datos y limpiar datos temporales.
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

                // Log de debug si está habilitado
                if (plugin.getConfig().getBoolean("general.debug", false)) {
                    plugin.getLogger().info("Datos actualizados para " + player.getName() + " (" + player.getUniqueId() + ")");
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Error al actualizar datos del jugador " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Limpiar propuestas pendientes del jugador que se desconecta
        MarryCommand.cleanupProposal(player.getUniqueId());

        // Log de debug si está habilitado
        if (plugin.getConfig().getBoolean("general.debug", false)) {
            plugin.getLogger().info("Limpieza de datos temporales para " + player.getName());
        }
    }
}