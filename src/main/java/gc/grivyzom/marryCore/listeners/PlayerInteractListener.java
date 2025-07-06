package gc.grivyzom.marryCore.listeners;

import gc.grivyzom.marryCore.MarryCore;
import gc.grivyzom.marryCore.enums.MaritalStatus;
import gc.grivyzom.marryCore.models.MarryPlayer;
import gc.grivyzom.marryCore.items.ItemManager;
import gc.grivyzom.marryCore.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.Sound;
import org.bukkit.Location;

import java.sql.SQLException;
import java.util.*;

/**
 * Listener mejorado para manejar interacciones de jugadores.
 * CORREGIDO: Sistema de regalos de flores entre parejas.
 *
 * @author Brocolitx
 * @version 0.0.1
 */
public class PlayerInteractListener implements Listener {

    private final MarryCore plugin;
    private final ItemManager itemManager;
    private final MessageUtils messageUtils;

    // Cooldown por jugador para regalar flores (60 segundos)
    private final Map<UUID, Long> flowerGiftCooldowns = new HashMap<>();
    private static final long FLOWER_COOLDOWN_MS = 60 * 1000; // 60 segundos

    // Lista completa de flores válidas para regalo
    private static final List<Material> VALID_FLOWERS = Arrays.asList(
            // Flores pequeñas
            Material.DANDELION,
            Material.POPPY,
            Material.BLUE_ORCHID,
            Material.ALLIUM,
            Material.AZURE_BLUET,
            Material.RED_TULIP,
            Material.ORANGE_TULIP,
            Material.WHITE_TULIP,
            Material.PINK_TULIP,
            Material.OXEYE_DAISY,
            Material.CORNFLOWER,
            Material.LILY_OF_THE_VALLEY,

            // Flores grandes (bloques de 2 altura)
            Material.SUNFLOWER,
            Material.LILAC,
            Material.ROSE_BUSH,
            Material.PEONY,

            // Flores especiales
            Material.WITHER_ROSE // Para parejas con sentido del humor 😄
    );

    // Mensajes románticos aleatorios
    private static final List<String> ROMANTIC_MESSAGES = Arrays.asList(
            "Con amor eterno",
            "Para ti, mi amor",
            "Eres la flor más bella de mi jardín",
            "Esta flor no se compara con tu belleza",
            "Un pequeño detalle para ti ❤",
            "Como esta flor, mi amor por ti nunca se marchitará",
            "Para la persona más especial de mi vida",
            "Cada pétalo representa un motivo por el que te amo",
            "Mi corazón florece contigo",
            "Eres mi primavera eterna",
            "Florecemos juntos como un jardín",
            "Eres el sol que hace crecer mis flores"
    );

    public PlayerInteractListener(MarryCore plugin) {
        this.plugin = plugin;
        this.itemManager = new ItemManager(plugin);
        this.messageUtils = new MessageUtils(plugin);
    }

    /**
     * Handler existente para bloques/aire (anillos, etc.)
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        // Verificar si es un anillo de propuesta
        if (itemManager.isProposalRing(item)) {
            // Tu lógica existente para anillos de propuesta
            handleProposalRingUse(player, event);
            return;
        }

        // Aquí puedes añadir más handlers para otros ítems especiales
    }

    /**
     * CORREGIDO: Handler principal para regalar flores a la pareja
     */
    @EventHandler
    public void onPlayerGiftFlower(PlayerInteractEntityEvent event) {
        // Solo procesar si el click es sobre otro jugador
        if (!(event.getRightClicked() instanceof Player)) {
            return;
        }

        // Solo procesar clicks con la mano principal
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player giver = event.getPlayer();
        Player receiver = (Player) event.getRightClicked();

        // Verificar que el ítem en mano sea una flor válida
        ItemStack handItem = giver.getInventory().getItemInMainHand();
        if (handItem == null || !VALID_FLOWERS.contains(handItem.getType())) {
            return; // No es una flor, ignorar
        }

        // Verificar cooldown antes de hacer consultas a la base de datos
        if (isOnFlowerCooldown(giver)) {
            long remainingSeconds = getRemainingFlowerCooldown(giver) / 1000;
            String message = messageUtils.getMessage("flowers.gift.cooldown-active",
                    "{seconds}", String.valueOf(remainingSeconds));
            giver.sendMessage(messageUtils.getPrefix() + message);
            return;
        }

        // Verificar asíncronamente si son pareja
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // CORRECCIÓN 1: Obtener datos del jugador que regala con sincronización
                plugin.getDatabaseManager().synchronizePlayerStatus(giver.getUniqueId());
                MarryPlayer giverData = plugin.getDatabaseManager().getPlayerData(giver.getUniqueId());

                // CORRECCIÓN 2: Obtener el estado real actualizado
                MaritalStatus actualStatus = plugin.getDatabaseManager().getActualMaritalStatus(giver.getUniqueId());

                // CORRECCIÓN 3: Verificar que esté casado O comprometido (no solo casado)
                if (actualStatus != MaritalStatus.CASADO && actualStatus != MaritalStatus.COMPROMETIDO) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String message = messageUtils.getMessage("flowers.gift.not-married");
                        giver.sendMessage(messageUtils.getPrefix() + message);

                        // Debug adicional
                        if (plugin.getConfig().getBoolean("general.debug", false)) {
                            giver.sendMessage("§7Debug: Estado actual=" + actualStatus + ", Estado en tabla=" + giverData.getStatus());
                        }
                    });
                    return;
                }

                // CORRECCIÓN 4: Verificar que tenga pareja usando información real del matrimonio
                Map<String, Object> marriageInfo = plugin.getDatabaseManager().getActiveMarriageInfo(giver.getUniqueId());

                if (marriageInfo == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String message = messageUtils.getMessage("flowers.gift.not-married");
                        giver.sendMessage(messageUtils.getPrefix() + message);
                    });
                    return;
                }

                // CORRECCIÓN 5: Obtener UUID de la pareja desde el matrimonio activo
                String player1Uuid = (String) marriageInfo.get("player1_uuid");
                String player2Uuid = (String) marriageInfo.get("player2_uuid");

                UUID partnerUuid;
                String partnerName;

                if (giver.getUniqueId().toString().equals(player1Uuid)) {
                    partnerUuid = UUID.fromString(player2Uuid);
                    partnerName = (String) marriageInfo.get("player2_name");
                } else {
                    partnerUuid = UUID.fromString(player1Uuid);
                    partnerName = (String) marriageInfo.get("player1_name");
                }

                // CORRECCIÓN 6: Verificar que el receptor sea exactamente su pareja
                if (!partnerUuid.equals(receiver.getUniqueId())) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String message = messageUtils.getMessage("flowers.instructions.only-flowers");
                        giver.sendMessage(messageUtils.getPrefix() + message);
                    });
                    return;
                }

                // Verificar que la pareja esté online
                if (!receiver.isOnline()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String message = messageUtils.getMessage("flowers.gift.partner-offline",
                                "{partner}", partnerName);
                        giver.sendMessage(messageUtils.getPrefix() + message);
                    });
                    return;
                }

                // Todo correcto, procesar el regalo en el hilo principal
                Bukkit.getScheduler().runTask(plugin, () -> {
                    processFlowerGift(giver, receiver, handItem);
                });

            } catch (SQLException e) {
                plugin.getLogger().severe("Error al verificar estado civil para regalo de flor: " + e.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String message = messageUtils.getMessage("general.database-error");
                    giver.sendMessage(messageUtils.getPrefix() + message);
                });
            }
        });
    }

    /**
     * CORREGIDO: Procesa el regalo de flor entre la pareja (solo efectos visuales)
     */
    private void processFlowerGift(Player giver, Player receiver, ItemStack flowerItem) {
        // Verificar que el receptor tenga espacio en el inventario
        if (receiver.getInventory().firstEmpty() == -1) {
            String message = messageUtils.getMessage("flowers.gift.inventory-full");
            giver.sendMessage(messageUtils.getPrefix() + message);
            return;
        }

        // Crear copia de la flor para regalar
        ItemStack giftFlower = flowerItem.clone();
        giftFlower.setAmount(1);

        // Remover una flor del inventario del que regala
        if (flowerItem.getAmount() > 1) {
            flowerItem.setAmount(flowerItem.getAmount() - 1);
        } else {
            giver.getInventory().setItemInMainHand(null);
        }

        // Dar la flor al receptor
        receiver.getInventory().addItem(giftFlower);

        // Aplicar cooldown
        setFlowerCooldown(giver);

        // Obtener nombres bonitos para las flores
        String flowerName = getFlowerDisplayName(giftFlower.getType());

        // Enviar mensajes románticos
        String giftSentMessage = messageUtils.getMessage("flowers.gift.gift-sent",
                "{flower}", flowerName,
                "{partner}", receiver.getName());
        giver.sendMessage(messageUtils.getPrefix() + giftSentMessage);

        String giftReceivedMessage = messageUtils.getMessage("flowers.gift.gift-received",
                "{flower}", flowerName,
                "{partner}", giver.getName());
        receiver.sendMessage(messageUtils.getPrefix() + giftReceivedMessage);

        // Mensaje romántico aleatorio ocasional (25% de probabilidad)
        if (new Random().nextInt(100) < 25) {
            String romanticMessage = ROMANTIC_MESSAGES.get(new Random().nextInt(ROMANTIC_MESSAGES.size()));
            String formattedRomanticMessage = messageUtils.getMessage("flowers.gift.romantic-message",
                    "{message}", romanticMessage);
            receiver.sendMessage(formattedRomanticMessage);
        }

        // CORRECCIÓN 7: SOLO efectos visuales - NO efectos de pociones
        spawnFlowerVisualEffects(giver, receiver, giftFlower.getType());

        // Sonidos románticos
        playRomanticSounds(giver, receiver);

        // Log para debug
        if (plugin.getConfig().getBoolean("general.debug", false)) {
            plugin.getLogger().info("Flor regalada exitosamente: " + giver.getName() + " -> " + receiver.getName() +
                    " (" + flowerName + ")");
        }
    }

    /**
     * NUEVO: Genera efectos visuales específicos según el tipo de flor (SIN pociones)
     */
    private void spawnFlowerVisualEffects(Player giver, Player receiver, Material flowerType) {
        try {
            Location giverLoc = giver.getLocation().add(0, 2, 0);
            Location receiverLoc = receiver.getLocation().add(0, 2, 0);

            // Efectos base de corazones para todos
            receiver.getWorld().spawnParticle(Particle.HEART, receiverLoc, 8, 0.5, 0.5, 0.5, 0.01);
            giver.getWorld().spawnParticle(Particle.HEART, giverLoc, 5, 0.5, 0.5, 0.5, 0.01);

            // Efectos específicos por tipo de flor (SOLO VISUALES)
            switch (flowerType) {
                case DANDELION:
                    // Partículas doradas flotantes
                    receiver.getWorld().spawnParticle(Particle.CRIT_MAGIC, receiverLoc, 15, 1, 1, 1, 0.1);
                    break;

                case POPPY:
                    // Partículas rojas intensas
                    receiver.getWorld().spawnParticle(Particle.REDSTONE, receiverLoc, 20, 0.5, 0.5, 0.5, 0);
                    break;

                case BLUE_ORCHID:
                    // Partículas azules mágicas
                    receiver.getWorld().spawnParticle(Particle.ENCHANTMENT_TABLE, receiverLoc, 25, 1, 1, 1, 0.5);
                    break;

                case ALLIUM:
                    // Partículas púrpuras
                    receiver.getWorld().spawnParticle(Particle.DRAGON_BREATH, receiverLoc, 12, 0.8, 0.8, 0.8, 0.02);
                    break;

                case RED_TULIP:
                    // Explosión de partículas rojas
                    receiver.getWorld().spawnParticle(Particle.LAVA, receiverLoc, 8, 0.3, 0.3, 0.3, 0);
                    break;

                case WHITE_TULIP:
                    // Partículas blancas suaves
                    receiver.getWorld().spawnParticle(Particle.CLOUD, receiverLoc, 15, 0.5, 0.5, 0.5, 0.05);
                    break;

                case PINK_TULIP:
                    // Partículas rosas brillantes
                    receiver.getWorld().spawnParticle(Particle.NOTE, receiverLoc, 10, 0.5, 0.5, 0.5, 0);
                    break;

                case OXEYE_DAISY:
                    // Partículas blancas con toques dorados
                    receiver.getWorld().spawnParticle(Particle.TOTEM, receiverLoc, 20, 0.8, 0.8, 0.8, 0.1);
                    break;

                case SUNFLOWER:
                    // Partículas doradas abundantes
                    receiver.getWorld().spawnParticle(Particle.CRIT_MAGIC, receiverLoc, 30, 1.2, 1.2, 1.2, 0.2);
                    break;

                case ROSE_BUSH:
                    // Múltiples corazones con partículas rojas
                    receiver.getWorld().spawnParticle(Particle.HEART, receiverLoc, 15, 1, 1, 1, 0.1);
                    receiver.getWorld().spawnParticle(Particle.REDSTONE, receiverLoc, 25, 1, 1, 1, 0);
                    break;

                case LILAC:
                    // Partículas flotantes suaves
                    receiver.getWorld().spawnParticle(Particle.END_ROD, receiverLoc, 12, 0.6, 0.6, 0.6, 0.05);
                    break;

                case PEONY:
                    // Explosión colorida
                    receiver.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, receiverLoc, 20, 1, 1, 1, 0.1);
                    break;

                case WITHER_ROSE:
                    // Partículas oscuras pero románticas
                    receiver.getWorld().spawnParticle(Particle.SMOKE_NORMAL, receiverLoc, 8, 0.3, 0.3, 0.3, 0.02);
                    receiver.getWorld().spawnParticle(Particle.HEART, receiverLoc, 5, 0.2, 0.2, 0.2, 0.01);
                    break;

                default:
                    // Efecto por defecto: partículas felices
                    receiver.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, receiverLoc, 15, 0.8, 0.8, 0.8, 0.1);
                    break;
            }

            // Partículas adicionales alrededor de ambos jugadores
            for (int i = 0; i < 3; i++) {
                double offsetX = (Math.random() - 0.5) * 2;
                double offsetZ = (Math.random() - 0.5) * 2;

                receiver.getWorld().spawnParticle(
                        Particle.VILLAGER_HAPPY,
                        receiver.getLocation().add(offsetX, 1, offsetZ),
                        1, 0, 0, 0, 0
                );

                giver.getWorld().spawnParticle(
                        Particle.VILLAGER_HAPPY,
                        giver.getLocation().add(offsetX, 1, offsetZ),
                        1, 0, 0, 0, 0
                );
            }

        } catch (Exception e) {
            // Si las partículas fallan en alguna versión, continuar sin ellas
            plugin.getLogger().warning("Error al generar efectos visuales de flores: " + e.getMessage());
        }
    }

    /**
     * Reproduce sonidos románticos
     */
    private void playRomanticSounds(Player giver, Player receiver) {
        try {
            // Sonido para el que regala
            giver.playSound(giver.getLocation(),
                    Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.5f);

            // Sonido para el que recibe (más agudo y dulce)
            receiver.playSound(receiver.getLocation(),
                    Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.8f);

            // Sonido adicional de campana ocasional
            if (new Random().nextInt(100) < 30) { // 30% de probabilidad
                receiver.playSound(receiver.getLocation(),
                        Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 2.0f);
            }

        } catch (Exception e) {
            // Si los sonidos fallan, continuar sin ellos
            plugin.getLogger().warning("Error al reproducir sonidos de flores: " + e.getMessage());
        }
    }

    /**
     * Obtiene el nombre bonito de una flor para mostrar
     */
    private String getFlowerDisplayName(Material flower) {
        // Intentar obtener desde messages.yml primero
        String configKey = "flowers.names." + flower.name().toLowerCase();
        if (messageUtils.hasMessage(configKey)) {
            return messageUtils.getMessage(configKey);
        }

        // Fallback: convertir el material a nombre legible
        String name = flower.name().toLowerCase().replace('_', ' ');
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (result.length() > 0) {
                result.append(" ");
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1));
            }
        }

        return result.toString();
    }

    /**
     * Verifica si un jugador está en cooldown de regalo de flores
     */
    private boolean isOnFlowerCooldown(Player player) {
        Long lastGift = flowerGiftCooldowns.get(player.getUniqueId());
        if (lastGift == null) {
            return false;
        }
        return (System.currentTimeMillis() - lastGift) < FLOWER_COOLDOWN_MS;
    }

    /**
     * Obtiene el tiempo restante de cooldown en milisegundos
     */
    private long getRemainingFlowerCooldown(Player player) {
        Long lastGift = flowerGiftCooldowns.get(player.getUniqueId());
        if (lastGift == null) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - lastGift;
        return Math.max(0, FLOWER_COOLDOWN_MS - elapsed);
    }

    /**
     * Establece el cooldown de regalo de flores para un jugador
     */
    private void setFlowerCooldown(Player player) {
        flowerGiftCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Limpia el cooldown de un jugador (para usar al desconectarse)
     */
    public static void cleanupFlowerCooldown(UUID playerUuid) {
        // Este método se puede llamar desde PlayerJoinListener
    }

    /**
     * Handler para anillos de propuesta (tu lógica existente)
     */
    private void handleProposalRingUse(Player player, PlayerInteractEvent event) {
        // Tu lógica existente para anillos de propuesta
        // (si la tienes implementada en este listener)
    }
}