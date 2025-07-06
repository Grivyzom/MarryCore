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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class PlayerInteractListener implements Listener {

    private final MarryCore plugin;
    private final ItemManager itemManager;
    private final MessageUtils messageUtils;

    // Cooldown por jugador (ms)
    private final Map<UUID, Long> giftCooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 60 * 1000; // 60 segundos

    // Lista de flores válidas
    private static final List<Material> FLOWERS = Arrays.asList(
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
            Material.LILY_OF_THE_VALLEY
    );

    public PlayerInteractListener(MarryCore plugin) {
        this.plugin = plugin;
        this.itemManager = new ItemManager(plugin);
        this.messageUtils = new MessageUtils(plugin);
    }

    // --- (Tu handler existente para anillos/propuestas via PlayerInteractEvent) ---
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // ... tu código actual para ring/proposal ...
    }

    // Nuevo handler para regalar flores al cónyuge
    @EventHandler
    public void onPlayerGiftFlower(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) return;

        Player giver = event.getPlayer();
        Player receiver = (Player) event.getRightClicked();

        // Consulta asincrónica para no bloquear el hilo principal
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MarryPlayer giverData = plugin.getDatabaseManager().getPlayerData(giver.getUniqueId());
                // Comprueba que estén casados y sean pareja el uno del otro
                if (giverData.getStatus() != MaritalStatus.CASADO
                        || !giverData.hasPartner()
                        || !giverData.getPartnerUuid().equals(receiver.getUniqueId())) {
                    return;
                }

                // Comprueba ítem en mano
                ItemStack hand = giver.getInventory().getItemInMainHand();
                if (hand == null || hand.getAmount() <= 0) return;

                Material mat = hand.getType();
                if (!FLOWERS.contains(mat)) return;

                // Comprueba cooldown
                long now = System.currentTimeMillis();
                Long last = giftCooldowns.get(giver.getUniqueId());
                if (last != null && now - last < COOLDOWN_MS) {
                    long secsLeft = (COOLDOWN_MS - (now - last)) / 1000;
                    Bukkit.getScheduler().runTask(plugin, () ->
                            giver.sendMessage("§cAún no puedes regalar otra flor. Espera " + secsLeft + "s.")
                    );
                    return;
                }

                // Todo OK: programar transferencia/efectos en el hilo principal
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // 1) Quitar de la mano
                    hand.setAmount(hand.getAmount() - 1);
                    // 2) Dar al receptor
                    receiver.getInventory().addItem(new ItemStack(mat, 1));
                    // 3) Partículas de corazones
                    receiver.getWorld().spawnParticle(
                            Particle.HEART,
                            receiver.getLocation().add(0, 2, 0),
                            10, 0.3, 0.5, 0.3, 0.01
                    );
                    // 4) Efecto según flor
                    applyFlowerEffect(mat, receiver);
                    // 5) Mensajes
                    String flowerName = prettify(mat);
                    giver.sendMessage("§aHas regalado una " + flowerName + " a " + receiver.getName() + " ❤️");
                    receiver.sendMessage("§dHas recibido una " + flowerName + " de " + giver.getName() + "!");
                    // 6) Resetear cooldown
                    giftCooldowns.put(giver.getUniqueId(), now);
                });

            } catch (Exception e) {
                plugin.getLogger().severe("Error al regalar flor: " + e.getMessage());
            }
        });
    }

    private void applyFlowerEffect(Material mat, Player target) {
        switch (mat) {
            case DANDELION:
                target.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 10 * 20, 1));
                break;
            case POPPY:
                target.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, 8 * 20, 1));
                break;
            case BLUE_ORCHID:
                target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 5 * 20, 0));
                break;
            case ALLIUM:
                target.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 12 * 20, 1));
                break;
            case AZURE_BLUET:
                target.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 15 * 20, 0));
                break;
            case RED_TULIP:
                target.addPotionEffect(new PotionEffect(PotionEffectType.INCREASE_DAMAGE, 10 * 20, 0));
                break;
            case ORANGE_TULIP:
                target.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 10 * 20, 0));
                break;
            case WHITE_TULIP:
                target.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 5 * 20, 0));
                break;
            case PINK_TULIP:
                target.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 8 * 20, 0));
                break;
            case OXEYE_DAISY:
                target.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, 20 * 20, 0));
                break;
            case CORNFLOWER:
                target.addPotionEffect(new PotionEffect(PotionEffectType.FAST_DIGGING, 10 * 20, 0));
                break;
            case LILY_OF_THE_VALLEY:
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 5 * 20, 0));
                break;
            default:
                // sin efecto
                break;
        }
    }

    private String prettify(Material mat) {
        String name = mat.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String part : name.split(" ")) {
            sb.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1))
                    .append(" ");
        }
        return sb.toString().trim();
    }
}
