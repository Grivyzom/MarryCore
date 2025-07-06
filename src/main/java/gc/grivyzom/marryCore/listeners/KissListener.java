package gc.grivyzom.marryCore.listeners;

import gc.grivyzom.marryCore.MarryCore;
import gc.grivyzom.marryCore.enums.MaritalStatus;
import gc.grivyzom.marryCore.models.MarryPlayer;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Listener para manejar el sistema de besos entre parejas.
 * Permite a las parejas besarse usando agacharse + click izquierdo.
 *
 * @author Brocolitx
 * @version 0.0.1
 */
public class KissListener implements Listener {

    private final MarryCore plugin;

    public KissListener(MarryCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerKiss(PlayerInteractEntityEvent event) {
        // Verificar que sea un jugador quien hace click en otro jugador
        if (!(event.getRightClicked() instanceof Player)) return;

        Player beso = event.getPlayer();
        Player objetivo = (Player) event.getRightClicked();

        // Verificar que esté agachado y usando la mano principal
        if (!beso.isSneaking() || event.getHand() != EquipmentSlot.HAND) return;

        // Verificar de forma asíncrona si son pareja
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MarryPlayer mp = plugin.getDatabaseManager().getPlayerData(beso.getUniqueId());

                // Sólo si están casados o comprometidos entre ellos
                if ((mp.getStatus() == MaritalStatus.CASADO || mp.getStatus() == MaritalStatus.COMPROMETIDO)
                        && mp.getPartnerUuid() != null
                        && mp.getPartnerUuid().equals(objetivo.getUniqueId())) {

                    // Volver al hilo principal para efectos visuales
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        // Partículas de corazones en ambos
                        Location locBeso = beso.getLocation().add(0, 2, 0);
                        Location locObj = objetivo.getLocation().add(0, 2, 0);

                        beso.getWorld().spawnParticle(Particle.HEART, locBeso, 5, 0.5, 0.5, 0.5, 0.05);
                        beso.getWorld().spawnParticle(Particle.HEART, locObj, 5, 0.5, 0.5, 0.5, 0.05);

                        // Mensajes románticos
                        beso.sendMessage("§d♥ Le has dado un beso a tu pareja §f" + objetivo.getName() + "§d! ♥");
                        objetivo.sendMessage("§d♥ Tu pareja §f" + beso.getName() + " §dte ha besado! ♥");

                        // Sonido opcional
                        try {
                            beso.playSound(beso.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
                            objetivo.playSound(objetivo.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.5f);
                        } catch (Exception e) {
                            // Si el sonido no existe en la versión, continuar sin él
                        }
                    });
                }

            } catch (Exception e) {
                plugin.getLogger().warning("Error en sistema de besos: " + e.getMessage());
            }
        });
    }
}