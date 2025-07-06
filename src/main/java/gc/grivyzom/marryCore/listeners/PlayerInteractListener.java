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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.SQLException;
import java.util.*;

/**
 * Listener mejorado para manejar interacciones de jugadores.
 * Incluye sistema de regalos de flores entre parejas casadas.
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
     * NUEVO: Handler principal para regalar flores a la pareja
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

        // Verificar asíncronamente si son pareja casada
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Obtener datos del jugador que regala
                MarryPlayer giverData = plugin.getDatabaseManager().getPlayerData(giver.getUniqueId());

                // Verificar que esté casado
                if (giverData.getStatus() != MaritalStatus.CASADO) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String message = messageUtils.getMessage("flowers.gift.not-married");
                        giver.sendMessage(messageUtils.getPrefix() + message);
                    });
                    return;
                }

                // Verificar que tenga pareja
                if (!giverData.hasPartner()) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String message = messageUtils.getMessage("flowers.gift.not-married");
                        giver.sendMessage(messageUtils.getPrefix() + message);
                    });
                    return;
                }

                // Verificar que el receptor sea su pareja
                if (!giverData.getPartnerUuid().equals(receiver.getUniqueId())) {
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
                                "{partner}", receiver.getName());
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
     * Procesa el regalo de flor entre la pareja casada
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

        // Efectos visuales - Partículas de corazones
        spawnHeartParticles(receiver);

        // Aplicar efecto de poción según el tipo de flor
        applyFlowerEffect(receiver, giftFlower.getType());

        // Sonidos románticos
        playRomanticSounds(giver, receiver);

        // Log para debug
        if (plugin.getConfig().getBoolean("general.debug", false)) {
            plugin.getLogger().info("Flor regalada: " + giver.getName() + " -> " + receiver.getName() +
                    " (" + flowerName + ")");
        }
    }

    /**
     * Aplica efectos de poción según el tipo de flor
     */
    private void applyFlowerEffect(Player receiver, Material flowerType) {
        PotionEffect effect = null;

        switch (flowerType) {
            case DANDELION:
                effect = new PotionEffect(PotionEffectType.SPEED, 10 * 20, 1); // Velocidad II por 10s
                break;
            case POPPY:
                effect = new PotionEffect(PotionEffectType.JUMP, 8 * 20, 1); // Salto II por 8s
                break;
            case BLUE_ORCHID:
                effect = new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 0); // Regeneración I por 5s
                break;
            case ALLIUM:
                effect = new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 12 * 20, 0); // Resistencia I por 12s
                break;
            case AZURE_BLUET:
                effect = new PotionEffect(PotionEffectType.NIGHT_VISION, 15 * 20, 0); // Visión nocturna por 15s
                break;
            case RED_TULIP:
                effect = new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 10 * 20, 0); // Fuerza I por 10s
                break;
            case ORANGE_TULIP:
                effect = new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 10 * 20, 0); // Resistencia al fuego por 10s
                break;
            case WHITE_TULIP:
                effect = new PotionEffect(PotionEffectType.SATURATION, 5 * 20, 0); // Saturación por 5s
                break;
            case PINK_TULIP:
                effect = new PotionEffect(PotionEffectType.INVISIBILITY, 8 * 20, 0); // Invisibilidad por 8s
                break;
            case OXEYE_DAISY:
                effect = new PotionEffect(PotionEffectType.HEALTH_BOOST, 20 * 20, 0); // Vida extra por 20s
                break;
            case CORNFLOWER:
                effect = new PotionEffect(PotionEffectType.FAST_DIGGING, 10 * 20, 0); // Prisa por 10s
                break;
            case LILY_OF_THE_VALLEY:
                // Esta flor es venenosa en la vida real, pero aquí damos suerte
                effect = new PotionEffect(PotionEffectType.LUCK, 30 * 20, 0); // Suerte por 30s
                break;
            case SUNFLOWER:
                effect = new PotionEffect(PotionEffectType.ABSORPTION, 20 * 20, 1); // Absorción II por 20s
                break;
            case LILAC:
                effect = new PotionEffect(PotionEffectType.SLOW_FALLING, 15 * 20, 0); // Caída lenta por 15s
                break;
            case ROSE_BUSH:
                effect = new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 10 * 20, 0); // Héroe por 10s
                break;
            case PEONY:
                effect = new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 12 * 20, 0); // Gracia de delfín por 12s
                break;
            case WITHER_ROSE:
                // Para parejas con sentido del humor - efecto de brillo
                effect = new PotionEffect(PotionEffectType.GLOWING, 8 * 20, 0); // Brillo por 8s
                break;
            default:
                // Flor no reconocida, dar regeneración básica
                effect = new PotionEffect(PotionEffectType.REGENERATION, 3 * 20, 0);
                break;
        }

        if (effect != null) {
            receiver.addPotionEffect(effect);
        }
    }

    /**
     * Genera partículas de corazones alrededor del receptor
     */
    private void spawnHeartParticles(Player receiver) {
        try {
            receiver.getWorld().spawnParticle(
                    Particle.HEART,
                    receiver.getLocation().add(0, 2, 0),
                    10, // cantidad
                    0.5, 0.5, 0.5, // dispersión X, Y, Z
                    0.01 // velocidad extra
            );

            // Partículas adicionales alrededor del jugador
            for (int i = 0; i < 5; i++) {
                double offsetX = (Math.random() - 0.5) * 2;
                double offsetZ = (Math.random() - 0.5) * 2;
                receiver.getWorld().spawnParticle(
                        Particle.VILLAGER_HAPPY,
                        receiver.getLocation().add(offsetX, 1, offsetZ),
                        1, 0, 0, 0, 0
                );
            }
        } catch (Exception e) {
            // Si las partículas fallan en alguna versión, continuar sin ellas
            plugin.getLogger().warning("Error al generar partículas de flores: " + e.getMessage());
        }
    }

    /**
     * Reproduce sonidos románticos
     */
    private void playRomanticSounds(Player giver, Player receiver) {
        try {
            // Sonido para el que regala
            giver.playSound(giver.getLocation(),
                    org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.5f);

            // Sonido para el que recibe (más agudo y dulce)
            receiver.playSound(receiver.getLocation(),
                    org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.8f);

            // Sonido adicional de campana ocasional
            if (new Random().nextInt(100) < 30) { // 30% de probabilidad
                receiver.playSound(receiver.getLocation(),
                        org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 2.0f);
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