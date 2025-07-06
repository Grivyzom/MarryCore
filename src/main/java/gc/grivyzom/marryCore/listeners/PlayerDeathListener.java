package gc.grivyzom.marryCore.listeners;

import gc.grivyzom.marryCore.MarryCore;
import gc.grivyzom.marryCore.commands.DivorceCommand;
import gc.grivyzom.marryCore.commands.MarryCommand;
import gc.grivyzom.marryCore.commands.SpouseTeleportCommand;
import gc.grivyzom.marryCore.enums.MaritalStatus;
import gc.grivyzom.marryCore.items.ItemManager;
import gc.grivyzom.marryCore.models.MarryPlayer;
import gc.grivyzom.marryCore.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.List;

/**
 * Listener para manejar eventos de muerte y desconexión.
 * Protege ítems especiales y limpia datos temporales.
 *
 * @author Brocolitx
 * @version 0.0.1
 */
public class PlayerDeathListener implements Listener {

    private final MarryCore plugin;
    private final ItemManager itemManager;
    private final MessageUtils messageUtils;

    public PlayerDeathListener(MarryCore plugin) {
        this.plugin = plugin;
        this.itemManager = new ItemManager(plugin);
        this.messageUtils = new MessageUtils(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // Proteger ítems especiales configurados para no perderse
        protectSpecialItems(event);

        // Notificar al cónyuge si está casado
        notifySpouseOfDeath(player);
    }

    private void protectSpecialItems(PlayerDeathEvent event) {
        List<String> protectedItems = plugin.getConfig().getStringList("global_settings.keep_on_death");

        if (protectedItems.isEmpty()) {
            return;
        }

        List<ItemStack> drops = event.getDrops();
        Iterator<ItemStack> iterator = drops.iterator();

        while (iterator.hasNext()) {
            ItemStack item = iterator.next();

            if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
                continue;
            }

            String displayName = item.getItemMeta().getDisplayName();

            // Verificar anillos nupciales
            if (protectedItems.contains("engagement_ring") && isEngagementRing(displayName)) {
                iterator.remove();
                // Añadir al inventario del jugador cuando respawnee
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    event.getEntity().getInventory().addItem(item);
                }, 20L);
                continue;
            }

            // Verificar anillos de boda
            if (protectedItems.contains("wedding_ring") && isWeddingRing(displayName)) {
                iterator.remove();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    event.getEntity().getInventory().addItem(item);
                }, 20L);
                continue;
            }

            // Verificar invitaciones de boda
            if (protectedItems.contains("wedding_invitation") && isWeddingInvitation(displayName)) {
                iterator.remove();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    event.getEntity().getInventory().addItem(item);
                }, 20L);
            }
        }
    }

    private void notifySpouseOfDeath(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

                if (playerData.getStatus() == MaritalStatus.CASADO && playerData.hasPartner()) {
                    Player spouse = Bukkit.getPlayer(playerData.getPartnerUuid());

                    if (spouse != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            messageUtils.sendMessage(spouse, "benefits.death.spouse-died",
                                    "{player}", player.getName(),
                                    "{location}", formatLocation(player.getLocation()));
                        });
                    }
                }

            } catch (Exception e) {
                plugin.getLogger().warning("Error al notificar muerte al cónyuge: " + e.getMessage());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Limpiar datos temporales
        MarryCommand.cleanupProposal(player.getUniqueId());
        DivorceCommand.cleanupConfirmation(player.getUniqueId());
        SpouseTeleportCommand.cleanupCooldown(player.getUniqueId());

        // Log de debug si está habilitado
        if (plugin.getConfig().getBoolean("general.debug", false)) {
            plugin.getLogger().info("Limpieza de datos temporales para " + player.getName());
        }
    }

    private boolean isEngagementRing(String displayName) {
        String expectedName = plugin.getConfig().getString("items.engagement_ring.name", "&b&l💍 Anillo Nupcial 💍")
                .replace("&", "§");
        return displayName.contains("💍 Anillo Nupcial 💍") || displayName.equals(expectedName);
    }

    private boolean isWeddingRing(String displayName) {
        String expectedName = plugin.getConfig().getString("items.wedding_ring.name", "&d&l💖 Anillo de Boda 💖")
                .replace("&", "§");
        return displayName.contains("💖 Anillo de Boda 💖") || displayName.equals(expectedName);
    }

    private boolean isWeddingInvitation(String displayName) {
        String expectedName = plugin.getConfig().getString("items.wedding_invitation.name", "&f&l📜 Invitación de Boda 📜")
                .replace("&", "§");
        return displayName.contains("📜 Invitación de Boda 📜") || displayName.equals(expectedName);
    }

    private String formatLocation(org.bukkit.Location location) {
        return String.format("%s: %d, %d, %d",
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
    }
}