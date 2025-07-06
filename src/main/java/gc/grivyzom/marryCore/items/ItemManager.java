package gc.grivyzom.marryCore.items;

import gc.grivyzom.marryCore.MarryCore;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Clase encargada de gestionar todos los ítems del sistema de matrimonio.
 * Maneja la creación, validación y efectos de anillos y otros ítems especiales.
 *
 * @author Brocolitx
 * @version 0.0.1
 */
public class ItemManager {

    private final MarryCore plugin;
    private FileConfiguration itemsConfig;

    public ItemManager(MarryCore plugin) {
        this.plugin = plugin;
        loadItemsConfig();
    }

    /**
     * Carga la configuración de ítems
     */
    private void loadItemsConfig() {
        File itemsFile = new File(plugin.getDataFolder(), "items.yml");

        if (!itemsFile.exists()) {
            plugin.saveResource("items.yml", false);
        }

        itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
    }

    /**
     * Recarga la configuración de ítems
     */
    public void reloadItemsConfig() {
        loadItemsConfig();
    }

    /**
     * Crea un anillo de propuesta
     * @return ItemStack del anillo de propuesta
     */
    public ItemStack createProposalRing() {
        Material material = Material.valueOf(itemsConfig.getString("proposal_ring.material", "GOLD_INGOT"));
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Nombre del ítem
            String name = ChatColor.translateAlternateColorCodes('&',
                    itemsConfig.getString("proposal_ring.name", "&e&l⭐ Anillo de Propuesta ⭐"));
            meta.setDisplayName(name);

            // Lore del ítem
            List<String> lore = new ArrayList<>();
            List<String> configLore = itemsConfig.getStringList("proposal_ring.lore");
            for (String line : configLore) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);

            // Custom Model Data
            if (itemsConfig.contains("proposal_ring.custom_model_data")) {
                meta.setCustomModelData(itemsConfig.getInt("proposal_ring.custom_model_data"));
            }

            // Glow effect
            if (itemsConfig.getBoolean("proposal_ring.glow", true)) {
                meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            // Unbreakable
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Crea un anillo nupcial (de compromiso)
     * @param partnerName Nombre de la pareja
     * @return ItemStack del anillo nupcial
     */
    public ItemStack createEngagementRing(String partnerName) {
        Material material = Material.valueOf(itemsConfig.getString("engagement_ring.material", "DIAMOND"));
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Nombre del ítem
            String name = ChatColor.translateAlternateColorCodes('&',
                    itemsConfig.getString("engagement_ring.name", "&b&l💍 Anillo Nupcial 💍"));
            meta.setDisplayName(name);

            // Lore del ítem con nombre de pareja
            List<String> lore = new ArrayList<>();
            List<String> configLore = itemsConfig.getStringList("engagement_ring.lore");
            for (String line : configLore) {
                line = line.replace("{partner}", partnerName);
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);

            // Custom Model Data
            if (itemsConfig.contains("engagement_ring.custom_model_data")) {
                meta.setCustomModelData(itemsConfig.getInt("engagement_ring.custom_model_data"));
            }

            // Glow effect
            if (itemsConfig.getBoolean("engagement_ring.glow", true)) {
                meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            // Unbreakable y no transferible
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Crea un anillo de boda
     * @param partnerName Nombre de la pareja
     * @param weddingDate Fecha de la boda
     * @return ItemStack del anillo de boda
     */
    public ItemStack createWeddingRing(String partnerName, String weddingDate) {
        Material material = Material.valueOf(itemsConfig.getString("wedding_ring.material", "NETHERITE_INGOT"));
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // Nombre del ítem
            String name = ChatColor.translateAlternateColorCodes('&',
                    itemsConfig.getString("wedding_ring.name", "&d&l💖 Anillo de Boda 💖"));
            meta.setDisplayName(name);

            // Lore del ítem con información de la boda
            List<String> lore = new ArrayList<>();
            List<String> configLore = itemsConfig.getStringList("wedding_ring.lore");
            for (String line : configLore) {
                line = line.replace("{partner}", partnerName);
                line = line.replace("{wedding_date}", weddingDate);
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);

            // Custom Model Data
            if (itemsConfig.contains("wedding_ring.custom_model_data")) {
                meta.setCustomModelData(itemsConfig.getInt("wedding_ring.custom_model_data"));
            }

            // Enchanted effect
            if (itemsConfig.getBoolean("wedding_ring.enchanted", true)) {
                meta.addEnchant(Enchantment.INFINITY, 1, true);
                if (!itemsConfig.getBoolean("wedding_ring.show_enchants", false)) {
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
            }

            // Unbreakable
            if (itemsConfig.getBoolean("wedding_ring.unbreakable", true)) {
                meta.setUnbreakable(true);
                meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Verifica si un ítem es un anillo de propuesta
     * @param item ItemStack a verificar
     * @return true si es un anillo de propuesta
     */
    public boolean isProposalRing(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }

        String expectedName = ChatColor.translateAlternateColorCodes('&',
                itemsConfig.getString("proposal_ring.name", "&e&l⭐ Anillo de Propuesta ⭐"));

        return meta.getDisplayName().equals(expectedName);
    }

    /**
     * Verifica si un jugador tiene un anillo de propuesta
     * @param player Jugador a verificar
     * @return true si tiene el anillo
     */
    public boolean hasProposalRing(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isProposalRing(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Consume un anillo de propuesta del inventario del jugador
     * @param player Jugador del que consumir el anillo
     * @return true si se consumió exitosamente
     */
    public boolean consumeProposalRing(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isProposalRing(item)) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().remove(item);
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Da un anillo de propuesta a un jugador
     * @param player Jugador que recibirá el anillo
     */
    public void giveProposalRing(Player player) {
        ItemStack ring = createProposalRing();
        player.getInventory().addItem(ring);
    }

    /**
     * Da un anillo nupcial a un jugador
     * @param player Jugador que recibirá el anillo
     * @param partnerName Nombre de la pareja
     */
    public void giveEngagementRing(Player player, String partnerName) {
        ItemStack ring = createEngagementRing(partnerName);
        player.getInventory().addItem(ring);
    }

    /**
     * Da un anillo de boda a un jugador
     * @param player Jugador que recibirá el anillo
     * @param partnerName Nombre de la pareja
     * @param weddingDate Fecha de la boda
     */
    public void giveWeddingRing(Player player, String partnerName, String weddingDate) {
        ItemStack ring = createWeddingRing(partnerName, weddingDate);
        player.getInventory().addItem(ring);
    }

    /**
     * Reproduce efectos de propuesta
     * @param proposer Jugador que propone
     * @param target Jugador objetivo
     */
    public void playProposalEffects(Player proposer, Player target) {
        // Sonido
        String soundName = itemsConfig.getString("special_effects.proposal_use.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        try {
            Sound sound = Sound.valueOf(soundName);
            float volume = (float) itemsConfig.getDouble("special_effects.proposal_use.volume", 1.0);
            float pitch = (float) itemsConfig.getDouble("special_effects.proposal_use.pitch", 1.2);

            proposer.playSound(proposer.getLocation(), sound, volume, pitch);
            target.playSound(target.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Sonido inválido: " + soundName);
        }

        // Partículas
        String particleName = itemsConfig.getString("special_effects.proposal_use.particles", "HEART");
        try {
            Particle particle = Particle.valueOf(particleName);
            Location proposerLoc = proposer.getLocation().add(0, 2, 0);
            Location targetLoc = target.getLocation().add(0, 2, 0);

            proposer.getWorld().spawnParticle(particle, proposerLoc, 10, 0.5, 0.5, 0.5, 0);
            target.getWorld().spawnParticle(particle, targetLoc, 10, 0.5, 0.5, 0.5, 0);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Partícula inválida: " + particleName);
        }
    }

    /**
     * Reproduce efectos de compromiso exitoso
     * @param player1 Primer jugador
     * @param player2 Segundo jugador
     */
    public void playEngagementEffects(Player player1, Player player2) {
        // Sonido
        String soundName = itemsConfig.getString("special_effects.engagement_success.sound", "ENTITY_PLAYER_LEVELUP");
        try {
            Sound sound = Sound.valueOf(soundName);
            float volume = (float) itemsConfig.getDouble("special_effects.engagement_success.volume", 1.0);
            float pitch = (float) itemsConfig.getDouble("special_effects.engagement_success.pitch", 1.0);

            player1.playSound(player1.getLocation(), sound, volume, pitch);
            player2.playSound(player2.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Sonido inválido: " + soundName);
        }

        // Fuegos artificiales si están habilitados
        if (itemsConfig.getBoolean("special_effects.engagement_success.fireworks", true)) {
            spawnFirework(player1.getLocation());
            spawnFirework(player2.getLocation());
        }
    }

    /**
     * Crea un fuego artificial en una ubicación
     * @param location Ubicación donde crear el fuego artificial
     */
    private void spawnFirework(Location location) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            org.bukkit.entity.Firework firework = location.getWorld().spawn(location, org.bukkit.entity.Firework.class);
            org.bukkit.inventory.meta.FireworkMeta meta = firework.getFireworkMeta();

            org.bukkit.FireworkEffect effect = org.bukkit.FireworkEffect.builder()
                    .with(org.bukkit.FireworkEffect.Type.HEART)
                    .withColor(Color.RED, Color.PINK)
                    .withFade(Color.WHITE)
                    .flicker(true)
                    .trail(true)
                    .build();

            meta.addEffect(effect);
            meta.setPower(1);
            firework.setFireworkMeta(meta);
        });
    }
}