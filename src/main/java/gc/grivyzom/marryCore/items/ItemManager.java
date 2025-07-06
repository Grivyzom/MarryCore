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
        Material material = getMaterialSafely(itemsConfig.getString("proposal_ring.material", "GOLD_INGOT"));
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

            // Custom Model Data (solo en versiones que lo soporten)
            if (itemsConfig.contains("proposal_ring.custom_model_data")) {
                try {
                    meta.setCustomModelData(itemsConfig.getInt("proposal_ring.custom_model_data"));
                } catch (NoSuchMethodError e) {
                    // Versión no soporta Custom Model Data
                }
            }

            // Glow effect con encantamiento compatible
            if (itemsConfig.getBoolean("proposal_ring.glow", true)) {
                addGlowEffect(meta);
            }

            // Unbreakable (con manejo de compatibilidad)
            setUnbreakableSafely(meta, true);
            hideItemFlags(meta);

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
        Material material = getMaterialSafely(itemsConfig.getString("engagement_ring.material", "DIAMOND"));
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
                try {
                    meta.setCustomModelData(itemsConfig.getInt("engagement_ring.custom_model_data"));
                } catch (NoSuchMethodError e) {
                    // Versión no soporta Custom Model Data
                }
            }

            // Glow effect
            if (itemsConfig.getBoolean("engagement_ring.glow", true)) {
                addGlowEffect(meta);
            }

            // Unbreakable y no transferible
            setUnbreakableSafely(meta, true);
            hideItemFlags(meta);

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
        Material material = getMaterialSafely(itemsConfig.getString("wedding_ring.material", "NETHERITE_INGOT"));
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
                try {
                    meta.setCustomModelData(itemsConfig.getInt("wedding_ring.custom_model_data"));
                } catch (NoSuchMethodError e) {
                    // Versión no soporta Custom Model Data
                }
            }

            // Enchanted effect
            if (itemsConfig.getBoolean("wedding_ring.enchanted", true)) {
                addEnchantedEffect(meta);
                if (!itemsConfig.getBoolean("wedding_ring.show_enchants", false)) {
                    hideEnchantments(meta);
                }
            }

            // Unbreakable
            if (itemsConfig.getBoolean("wedding_ring.unbreakable", true)) {
                setUnbreakableSafely(meta, true);
                hideItemFlags(meta);
            }

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Obtiene un Material de forma segura, con fallback
     */
    private Material getMaterialSafely(String materialName) {
        try {
            return Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Material inválido: " + materialName + ", usando GOLD_INGOT por defecto");
            return Material.GOLD_INGOT;
        }
    }

    /**
     * Añade efecto de brillo de forma compatible
     */
    private void addGlowEffect(ItemMeta meta) {
        try {
            // Intentar usar LUCK_OF_THE_SEA primero
            meta.addEnchant(Enchantment.getByName("LUCK"), 1, true);
        } catch (Exception e) {
            try {
                // Fallback a DURABILITY
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
            } catch (Exception ex) {
                // Último fallback a PROTECTION
                meta.addEnchant(Enchantment.PROTECTION_ENVIRONMENTAL, 1, true);
            }
        }
    }

    /**
     * Añade efecto encantado para anillos de boda
     */
    private void addEnchantedEffect(ItemMeta meta) {
        try {
            // Intentar usar INFINITY primero
            meta.addEnchant(Enchantment.getByName("ARROW_INFINITE"), 1, true);
        } catch (Exception e) {
            try {
                // Fallback a MENDING
                meta.addEnchant(Enchantment.getByName("MENDING"), 1, true);
            } catch (Exception ex) {
                // Último fallback a DURABILITY
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
            }
        }
    }

    /**
     * Establece unbreakable de forma segura
     */
    private void setUnbreakableSafely(ItemMeta meta, boolean unbreakable) {
        try {
            meta.setUnbreakable(unbreakable);
        } catch (NoSuchMethodError e) {
            // Versión no soporta setUnbreakable, usar encantamiento de durabilidad
            if (unbreakable) {
                meta.addEnchant(Enchantment.DURABILITY, 10, true);
            }
        }
    }

    /**
     * Oculta flags de ítems de forma segura
     */
    private void hideItemFlags(ItemMeta meta) {
        try {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        } catch (Exception e) {
            // Versión no soporta ItemFlags
        }
    }

    /**
     * Oculta encantamientos específicamente
     */
    private void hideEnchantments(ItemMeta meta) {
        try {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } catch (Exception e) {
            // Versión no soporta ItemFlags
        }
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
            if (item != null && isProposalRing(item)) {
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
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && isProposalRing(item)) {
                if (item.getAmount() > 1) {
                    item.setAmount(item.getAmount() - 1);
                } else {
                    player.getInventory().setItem(i, null);
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
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(ring);
        } else {
            // Inventario lleno, dropear el ítem
            player.getWorld().dropItem(player.getLocation(), ring);
            player.sendMessage(ChatColor.YELLOW + "Tu inventario está lleno. El anillo ha sido dropeado.");
        }
    }

    /**
     * Da un anillo nupcial a un jugador
     * @param player Jugador que recibirá el anillo
     * @param partnerName Nombre de la pareja
     */
    public void giveEngagementRing(Player player, String partnerName) {
        ItemStack ring = createEngagementRing(partnerName);
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(ring);
        } else {
            player.getWorld().dropItem(player.getLocation(), ring);
            player.sendMessage(ChatColor.YELLOW + "Tu inventario está lleno. El anillo ha sido dropeado.");
        }
    }

    /**
     * Da un anillo de boda a un jugador
     * @param player Jugador que recibirá el anillo
     * @param partnerName Nombre de la pareja
     * @param weddingDate Fecha de la boda
     */
    public void giveWeddingRing(Player player, String partnerName, String weddingDate) {
        ItemStack ring = createWeddingRing(partnerName, weddingDate);
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(ring);
        } else {
            player.getWorld().dropItem(player.getLocation(), ring);
            player.sendMessage(ChatColor.YELLOW + "Tu inventario está lleno. El anillo ha sido dropeado.");
        }
    }

    /**
     * Reproduce efectos de propuesta
     * @param proposer Jugador que propone
     * @param target Jugador objetivo
     */
    public void playProposalEffects(Player proposer, Player target) {
        // Sonido con manejo de compatibilidad
        String soundName = itemsConfig.getString("special_effects.proposal_use.sound", "ENTITY_EXPERIENCE_ORB_PICKUP");
        playCompatibleSound(proposer, soundName, 1.0f, 1.2f);
        playCompatibleSound(target, soundName, 1.0f, 1.2f);

        // Partículas con manejo de compatibilidad
        String particleName = itemsConfig.getString("special_effects.proposal_use.particles", "HEART");
        spawnCompatibleParticles(proposer, particleName, 10);
        spawnCompatibleParticles(target, particleName, 10);
    }

    /**
     * Reproduce efectos de compromiso exitoso
     * @param player1 Primer jugador
     * @param player2 Segundo jugador
     */
    public void playEngagementEffects(Player player1, Player player2) {
        // Sonido
        String soundName = itemsConfig.getString("special_effects.engagement_success.sound", "ENTITY_PLAYER_LEVELUP");
        playCompatibleSound(player1, soundName, 1.0f, 1.0f);
        playCompatibleSound(player2, soundName, 1.0f, 1.0f);

        // Fuegos artificiales si están habilitados
        if (itemsConfig.getBoolean("special_effects.engagement_success.fireworks", true)) {
            spawnFirework(player1.getLocation());
            spawnFirework(player2.getLocation());
        }
    }

    /**
     * Reproduce sonido de forma compatible
     */
    private void playCompatibleSound(Player player, String soundName, float volume, float pitch) {
        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException e) {
            try {
                // Fallback a sonidos comunes
                if (soundName.contains("EXPERIENCE")) {
                    player.playSound(player.getLocation(), Sound.valueOf("ENTITY_EXPERIENCE_ORB_PICKUP"), volume, pitch);
                } else if (soundName.contains("LEVELUP")) {
                    player.playSound(player.getLocation(), Sound.valueOf("ENTITY_PLAYER_LEVELUP"), volume, pitch);
                } else {
                    // Último fallback
                    player.playSound(player.getLocation(), Sound.valueOf("ENTITY_ITEM_PICKUP"), volume, pitch);
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("No se pudo reproducir sonido: " + soundName);
            }
        }
    }

    /**
     * Genera partículas de forma compatible
     */
    private void spawnCompatibleParticles(Player player, String particleName, int count) {
        try {
            Particle particle = Particle.valueOf(particleName);
            Location loc = player.getLocation().add(0, 2, 0);
            player.getWorld().spawnParticle(particle, loc, count, 0.5, 0.5, 0.5, 0);
        } catch (IllegalArgumentException e) {
            try {
                // Fallback a partículas comunes
                Particle fallbackParticle;
                if (particleName.equals("HEART")) {
                    fallbackParticle = Particle.valueOf("VILLAGER_HAPPY");
                } else {
                    fallbackParticle = Particle.valueOf("SPELL_WITCH");
                }
                Location loc = player.getLocation().add(0, 2, 0);
                player.getWorld().spawnParticle(fallbackParticle, loc, count, 0.5, 0.5, 0.5, 0);
            } catch (Exception ex) {
                plugin.getLogger().warning("No se pudo generar partículas: " + particleName);
            }
        }
    }

    /**
     * Crea un fuego artificial en una ubicación
     * @param location Ubicación donde crear el fuego artificial
     */
    private void spawnFirework(Location location) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                org.bukkit.entity.Firework firework = location.getWorld().spawn(location, org.bukkit.entity.Firework.class);
                org.bukkit.inventory.meta.FireworkMeta meta = firework.getFireworkMeta();

                // Crear efecto con colores compatibles
                org.bukkit.FireworkEffect.Builder effectBuilder = org.bukkit.FireworkEffect.builder();

                // Tipo de efecto con fallback
                try {
                    effectBuilder.with(org.bukkit.FireworkEffect.Type.valueOf("HEART"));
                } catch (Exception e) {
                    effectBuilder.with(org.bukkit.FireworkEffect.Type.BALL_LARGE);
                }

                // Colores con fallback
                try {
                    effectBuilder.withColor(Color.RED, Color.fromRGB(255, 192, 203)); // PINK fallback
                } catch (Exception e) {
                    effectBuilder.withColor(Color.RED, Color.WHITE);
                }

                effectBuilder.withFade(Color.WHITE);
                effectBuilder.flicker(true);
                effectBuilder.trail(true);

                org.bukkit.FireworkEffect effect = effectBuilder.build();
                meta.addEffect(effect);
                meta.setPower(1);
                firework.setFireworkMeta(meta);
            } catch (Exception e) {
                plugin.getLogger().warning("Error al crear fuego artificial: " + e.getMessage());
            }
        });
    }
}