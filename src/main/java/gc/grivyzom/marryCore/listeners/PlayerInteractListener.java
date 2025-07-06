package gc.grivyzom.marryCore.listeners;

import gc.grivyzom.marryCore.MarryCore;
import gc.grivyzom.marryCore.enums.MaritalStatus;
import gc.grivyzom.marryCore.items.ItemManager;
import gc.grivyzom.marryCore.models.MarryPlayer;
import gc.grivyzom.marryCore.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listener para manejar interacciones de jugadores con ítems especiales.
 * Maneja el uso de anillos y teletransporte.
 *
 * @author Brocolitx
 * @version 0.0.1
 */
public class PlayerInteractListener implements Listener {

    private final MarryCore plugin;
    private final ItemManager itemManager;
    private final MessageUtils messageUtils;

    public PlayerInteractListener(MarryCore plugin) {
        this.plugin = plugin;
        this.itemManager = new ItemManager(plugin);
        this.messageUtils = new MessageUtils(plugin);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        // Verificar si es click derecho
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // Verificar si es un anillo de propuesta
        if (itemManager.isProposalRing(item)) {
            event.setCancelled(true);
            handleProposalRingUse(player);
            return;
        }

        // Verificar si es un anillo de boda (para teletransporte)
        if (isWeddingRing(item)) {
            event.setCancelled(true);
            handleWeddingRingUse(player);
            return;
        }
    }

    private void handleProposalRingUse(Player player) {
        messageUtils.sendMessage(player, "marriage.proposal.ring-instruction");
    }

    private void handleWeddingRingUse(Player player) {
        // Verificar que las habilidades especiales estén habilitadas
        if (!plugin.getConfig().getBoolean("benefits.teleport.enabled", true)) {
            messageUtils.sendMessage(player, "benefits.teleport.disabled");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

                if (playerData.getStatus() != MaritalStatus.CASADO) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "benefits.teleport.not-married");
                    });
                    return;
                }

                if (!playerData.hasPartner()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "benefits.teleport.no-partner");
                    });
                    return;
                }

                Player partner = Bukkit.getPlayer(playerData.getPartnerUuid());
                if (partner == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageUtils.sendMessage(player, "benefits.teleport.partner-offline");
                    });
                    return;
                }

                // Ejecutar comando de teletransporte
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.performCommand("conyuge");
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al usar anillo de boda: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    messageUtils.sendMessage(player, "general.database-error");
                });
            }
        });
    }

    private boolean isWeddingRing(ItemStack item) {
        if (!item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return false;
        }

        String expectedName = plugin.getConfig().getString("items.wedding_ring.name", "&d&l💖 Anillo de Boda 💖")
                .replace("&", "§");

        return item.getItemMeta().getDisplayName().equals(expectedName);
    }

    /**
     * Maneja efectos especiales para parejas casadas cuando están cerca
     */
    @EventHandler
    public void onPlayerMove(org.bukkit.event.player.PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // Verificar si los bonus de experiencia están habilitados
        if (!plugin.getConfig().getBoolean("experience.married_bonus.enabled", true)) {
            return;
        }

        // Solo verificar cada cierto tiempo para optimizar rendimiento
        if (System.currentTimeMillis() % 5000 != 0) { // Cada 5 segundos aproximadamente
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

                if (playerData.getStatus() != MaritalStatus.CASADO || !playerData.hasPartner()) {
                    return;
                }

                Player partner = Bukkit.getPlayer(playerData.getPartnerUuid());
                if (partner == null) {
                    return;
                }

                // Verificar distancia
                double maxDistance = plugin.getConfig().getDouble("experience.married_bonus.max_distance", 50.0);
                Location playerLoc = player.getLocation();
                Location partnerLoc = partner.getLocation();

                if (playerLoc.getWorld() != partnerLoc.getWorld()) {
                    return;
                }

                if (playerLoc.distance(partnerLoc) <= maxDistance) {
                    // Aplicar efectos de partículas ocasionales
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (Math.random() < 0.1) { // 10% de probabilidad
                            player.getWorld().spawnParticle(
                                    org.bukkit.Particle.HEART,
                                    playerLoc.add(0, 2, 0),
                                    1, 0.2, 0.2, 0.2, 0
                            );
                        }
                    });
                }

            } catch (Exception e) {
                // Silenciar errores menores para no saturar los logs
            }
        });
    }
}