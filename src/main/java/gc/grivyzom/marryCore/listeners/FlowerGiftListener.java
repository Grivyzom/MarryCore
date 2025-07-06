package gc.grivyzom.marryCore.listeners;

import gc.grivyzom.marryCore.MarryCore;
import gc.grivyzom.marryCore.enums.MaritalStatus;
import gc.grivyzom.marryCore.models.MarryPlayer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Listener para manejar el sistema de regalos de flores entre parejas casadas.
 * Permite a las parejas regalarse diferentes tipos de flores con efectos únicos.
 *
 * @author Brocolitx
 * @version 0.0.1
 */
public class FlowerGiftListener implements Listener {

    private final MarryCore plugin;

    // Map para cooldowns de regalos de flores (en milisegundos)
    private static final Map<UUID, Long> flowerGiftCooldowns = new HashMap<>();

    // Cooldown de 10 segundos entre regalos de flores
    private static final long FLOWER_GIFT_COOLDOWN_MS = 10000L;

    public FlowerGiftListener(MarryCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onFlowerGift(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // Verificar que tenga un ítem en la mano
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        // Verificar que sea una flor
        if (!isFlower(item.getType())) {
            return;
        }

        // Verificar que sea shift + click derecho
        if (!player.isSneaking() ||
                (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
                        event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        // Cancelar el evento para evitar otras acciones
        event.setCancelled(true);

        // Verificar cooldown
        if (isOnFlowerGiftCooldown(player)) {
            long remainingTime = getRemainingFlowerGiftCooldown(player);
            player.sendMessage("§c🌸 Debes esperar " + (remainingTime / 1000) + " segundos antes de regalar otra flor.");
            return;
        }

        // Verificar estado civil de forma asíncrona
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Obtener estado actualizado
                plugin.getDatabaseManager().synchronizePlayerStatus(player.getUniqueId());
                MarryPlayer playerData = plugin.getDatabaseManager().getPlayerData(player.getUniqueId());

                // Verificar que esté casado
                MaritalStatus actualStatus = plugin.getDatabaseManager().getActualMaritalStatus(player.getUniqueId());

                if (actualStatus != MaritalStatus.CASADO || !playerData.hasPartner()) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c🌸 Debes estar casado/a para regalar flores a tu pareja.");
                    });
                    return;
                }

                // Obtener información del matrimonio activo
                Map<String, Object> marriageInfo = plugin.getDatabaseManager().getActiveMarriageInfo(player.getUniqueId());

                if (marriageInfo == null) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c🌸 No se encontró información de tu matrimonio activo.");
                    });
                    return;
                }

                // Obtener UUID y nombre de la pareja
                String player1Uuid = (String) marriageInfo.get("player1_uuid");
                String player2Uuid = (String) marriageInfo.get("player2_uuid");

                UUID partnerUuid;
                String partnerName;

                if (player.getUniqueId().toString().equals(player1Uuid)) {
                    partnerUuid = UUID.fromString(player2Uuid);
                    partnerName = (String) marriageInfo.get("player2_name");
                } else {
                    partnerUuid = UUID.fromString(player1Uuid);
                    partnerName = (String) marriageInfo.get("player1_name");
                }

                Player partner = org.bukkit.Bukkit.getPlayer(partnerUuid);

                if (partner == null || !partner.isOnline()) {
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§c🌸 Tu pareja " + partnerName + " no está en línea.");
                    });
                    return;
                }

                // Volver al hilo principal para manipular inventarios
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    processFlowerGift(player, partner, item);
                });

            } catch (Exception e) {
                plugin.getLogger().warning("Error en sistema de regalos de flores: " + e.getMessage());
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§c🌸 Error al procesar el regalo de flor.");
                });
            }
        });
    }

    /**
     * Procesa el regalo de flor entre parejas
     */
    private void processFlowerGift(Player giver, Player receiver, ItemStack flowerItem) {
        Material flowerType = flowerItem.getType();
        String flowerName = getFlowerName(flowerType);

        // Verificar espacio en inventario del receptor
        if (receiver.getInventory().firstEmpty() == -1) {
            giver.sendMessage("§c🌸 El inventario de tu pareja está lleno.");
            return;
        }

        // Crear copia de la flor para regalar
        ItemStack giftFlower = flowerItem.clone();
        giftFlower.setAmount(1);

        // Añadir la flor al inventario del receptor
        receiver.getInventory().addItem(giftFlower);

        // Reducir cantidad en el inventario del que regala
        if (flowerItem.getAmount() > 1) {
            flowerItem.setAmount(flowerItem.getAmount() - 1);
        } else {
            giver.getInventory().setItemInMainHand(null);
        }

        // Establecer cooldown
        setFlowerGiftCooldown(giver);

        // Mensajes personalizados según el tipo de flor
        FlowerMessage flowerMsg = getFlowerMessage(flowerType);

        giver.sendMessage("§a🌸 Le has regalado " + flowerName + " a tu pareja §e" + receiver.getName() + "§a! " + flowerMsg.giverMessage);
        receiver.sendMessage("§a🌸 Tu pareja §e" + giver.getName() + " §ate ha regalado " + flowerName + "§a! " + flowerMsg.receiverMessage);

        // Efectos especiales según la flor
        applyFlowerEffects(giver, receiver, flowerType);

        // Log para debug
        if (plugin.getConfig().getBoolean("general.debug", false)) {
            plugin.getLogger().info("Regalo de flor: " + giver.getName() + " -> " + receiver.getName() + " (" + flowerName + ")");
        }
    }

    /**
     * Verifica si un material es una flor
     */
    private boolean isFlower(Material material) {
        switch (material) {
            case DANDELION:
            case POPPY:
            case BLUE_ORCHID:
            case ALLIUM:
            case AZURE_BLUET:
            case RED_TULIP:
            case ORANGE_TULIP:
            case WHITE_TULIP:
            case PINK_TULIP:
            case OXEYE_DAISY:
            case CORNFLOWER:
            case LILY_OF_THE_VALLEY:
            case WITHER_ROSE:
            case SUNFLOWER:
            case LILAC:
            case ROSE_BUSH:
            case PEONY:
                return true;
            default:
                return false;
        }
    }

    /**
     * Obtiene el nombre bonito de la flor
     */
    private String getFlowerName(Material flowerType) {
        switch (flowerType) {
            case DANDELION: return "§eun Diente de León";
            case POPPY: return "§cuna Amapola";
            case BLUE_ORCHID: return "§9una Orquídea Azul";
            case ALLIUM: return "§dun Allium";
            case AZURE_BLUET: return "§buna Nomeolvides";
            case RED_TULIP: return "§cun Tulipán Rojo";
            case ORANGE_TULIP: return "§6un Tulipán Naranja";
            case WHITE_TULIP: return "§fun Tulipán Blanco";
            case PINK_TULIP: return "§dun Tulipán Rosa";
            case OXEYE_DAISY: return "§funa Margarita";
            case CORNFLOWER: return "§9un Aciano";
            case LILY_OF_THE_VALLEY: return "§funa Campanilla";
            case WITHER_ROSE: return "§8una Rosa Marchita";
            case SUNFLOWER: return "§eun Girasol";
            case LILAC: return "§duna Lila";
            case ROSE_BUSH: return "§cun Rosal";
            case PEONY: return "§duna Peonía";
            default: return "§auna Flor";
        }
    }

    /**
     * Obtiene mensajes personalizados según el tipo de flor
     */
    private FlowerMessage getFlowerMessage(Material flowerType) {
        switch (flowerType) {
            case DANDELION:
                return new FlowerMessage("💛", "¡Que todos tus deseos se cumplan!");

            case POPPY:
                return new FlowerMessage("❤️", "¡Símbolo de amor eterno!");

            case BLUE_ORCHID:
                return new FlowerMessage("💙", "¡Eres única y especial!");

            case ALLIUM:
                return new FlowerMessage("💜", "¡Fuerza y unidad!");

            case AZURE_BLUET:
                return new FlowerMessage("💙", "¡Nunca te olvidaré!");

            case RED_TULIP:
                return new FlowerMessage("❤️", "¡Declaración de amor!");

            case ORANGE_TULIP:
                return new FlowerMessage("🧡", "¡Energía y entusiasmo!");

            case WHITE_TULIP:
                return new FlowerMessage("🤍", "¡Perdón y pureza!");

            case PINK_TULIP:
                return new FlowerMessage("💗", "¡Cariño y afecto!");

            case OXEYE_DAISY:
                return new FlowerMessage("🤍", "¡Inocencia y lealtad!");

            case CORNFLOWER:
                return new FlowerMessage("💙", "¡Delicadeza y elegancia!");

            case LILY_OF_THE_VALLEY:
                return new FlowerMessage("🤍", "¡Humildad y dulzura!");

            case WITHER_ROSE:
                return new FlowerMessage("🖤", "¡Amor que supera todo obstáculo!");

            case SUNFLOWER:
                return new FlowerMessage("🌻", "¡Eres mi sol!");

            case LILAC:
                return new FlowerMessage("💜", "¡Primer amor!");

            case ROSE_BUSH:
                return new FlowerMessage("🌹", "¡Pasión ardiente!");

            case PEONY:
                return new FlowerMessage("🌸", "¡Honor y riqueza!");

            default:
                return new FlowerMessage("🌺", "¡Con mucho amor!");
        }
    }

    /**
     * Aplica efectos especiales según el tipo de flor
     */
    private void applyFlowerEffects(Player giver, Player receiver, Material flowerType) {
        Location giverLoc = giver.getLocation().add(0, 2, 0);
        Location receiverLoc = receiver.getLocation().add(0, 2, 0);

        switch (flowerType) {
            case POPPY:
            case RED_TULIP:
            case ROSE_BUSH:
                // Flores rojas: Corazones y sonido romántico
                spawnParticles(giverLoc, Particle.HEART, 8);
                spawnParticles(receiverLoc, Particle.HEART, 8);
                playSound(giver, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                playSound(receiver, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                break;

            case DANDELION:
            case SUNFLOWER:
                // Flores amarillas: Partículas doradas
                spawnParticles(giverLoc, Particle.DRIPPING_HONEY, 5);
                spawnParticles(receiverLoc, Particle.DRIPPING_HONEY, 5);
                playSound(giver, Sound.ENTITY_BEE_POLLINATE, 1.0f, 1.2f);
                playSound(receiver, Sound.ENTITY_BEE_POLLINATE, 1.0f, 1.2f);
                break;

            case BLUE_ORCHID:
            case CORNFLOWER:
            case AZURE_BLUET:
                // Flores azules: Partículas de agua
                spawnParticles(giverLoc, Particle.DRIP_WATER, 6);
                spawnParticles(receiverLoc, Particle.DRIP_WATER, 6);
                playSound(giver, Sound.AMBIENT_UNDERWATER_ENTER, 0.5f, 1.8f);
                playSound(receiver, Sound.AMBIENT_UNDERWATER_ENTER, 0.5f, 1.8f);
                break;

            case PINK_TULIP:
            case PEONY:
            case LILAC:
                // Flores rosas/moradas: Partículas mágicas
                spawnParticles(giverLoc, Particle.ENCHANTMENT_TABLE, 10);
                spawnParticles(receiverLoc, Particle.ENCHANTMENT_TABLE, 10);
                playSound(giver, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.5f);
                playSound(receiver, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.5f);
                break;

            case WHITE_TULIP:
            case OXEYE_DAISY:
            case LILY_OF_THE_VALLEY:
                // Flores blancas: Partículas de nieve
                spawnParticles(giverLoc, Particle.SNOWBALL, 8);
                spawnParticles(receiverLoc, Particle.SNOWBALL, 8);
                playSound(giver, Sound.ENTITY_SNOW_GOLEM_AMBIENT, 0.8f, 1.3f);
                playSound(receiver, Sound.ENTITY_SNOW_GOLEM_AMBIENT, 0.8f, 1.3f);
                break;

            case WITHER_ROSE:
                // Rosa marchita: Efectos únicos (humo pero con amor)
                spawnParticles(giverLoc, Particle.SMOKE_NORMAL, 5);
                spawnParticles(receiverLoc, Particle.SMOKE_NORMAL, 5);
                spawnParticles(giverLoc, Particle.HEART, 3); // Corazones para contrastar
                spawnParticles(receiverLoc, Particle.HEART, 3);
                playSound(giver, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.3f, 2.0f);
                playSound(receiver, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.3f, 2.0f);
                break;

            default:
                // Flores genéricas: Efectos básicos de felicidad
                spawnParticles(giverLoc, Particle.VILLAGER_HAPPY, 6);
                spawnParticles(receiverLoc, Particle.VILLAGER_HAPPY, 6);
                playSound(giver, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.2f);
                playSound(receiver, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.2f);
                break;
        }
    }

    /**
     * Genera partículas de forma segura
     */
    private void spawnParticles(Location location, Particle particle, int count) {
        try {
            location.getWorld().spawnParticle(particle, location, count, 0.5, 0.5, 0.5, 0.1);
        } catch (Exception e) {
            // Si las partículas fallan, continuar silenciosamente
        }
    }

    /**
     * Reproduce sonido de forma segura
     */
    private void playSound(Player player, Sound sound, float volume, float pitch) {
        try {
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {
            // Si el sonido falla, continuar silenciosamente
        }
    }

    /**
     * Verifica si un jugador está en cooldown de regalo de flores
     */
    private boolean isOnFlowerGiftCooldown(Player player) {
        Long lastGift = flowerGiftCooldowns.get(player.getUniqueId());
        if (lastGift == null) {
            return false;
        }
        return (System.currentTimeMillis() - lastGift) < FLOWER_GIFT_COOLDOWN_MS;
    }

    /**
     * Obtiene el tiempo restante de cooldown en milisegundos
     */
    private long getRemainingFlowerGiftCooldown(Player player) {
        Long lastGift = flowerGiftCooldowns.get(player.getUniqueId());
        if (lastGift == null) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - lastGift;
        return Math.max(0, FLOWER_GIFT_COOLDOWN_MS - elapsed);
    }

    /**
     * Establece el cooldown de regalo de flores para un jugador
     */
    private void setFlowerGiftCooldown(Player player) {
        flowerGiftCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Método estático para limpiar cooldowns al desconectarse
     */
    public static void cleanupFlowerGiftCooldown(UUID playerUuid) {
        flowerGiftCooldowns.remove(playerUuid);
    }

    /**
     * Clase interna para mensajes de flores
     */
    private static class FlowerMessage {
        final String giverMessage;
        final String receiverMessage;

        FlowerMessage(String emoji, String meaning) {
            this.giverMessage = emoji;
            this.receiverMessage = meaning;
        }
    }
}