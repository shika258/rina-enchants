package me.rinaorc.rinaenchants.enchant;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import me.rinaorc.rinaenchants.util.BlizzardAnimation;
import me.rivaldev.harvesterhoes.api.events.HoeEnchant;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Enchantement Blizzard Éternel
 *
 * Un blizzard magique envahit la zone!
 *
 * FONCTIONNALITÉS (10 000 niveaux max):
 * ═══════════════════════════════════════════════════════════════
 *
 * 1. BLIZZARD - Des boules de neige tombent du ciel de façon chaotique
 *    - Durée: 5s + (niveau / 1000)s
 *    - Intensité: (niveau / 100) + 1 snowballs/seconde
 *
 * 2. EXPLOSION - Micro-chance (0.1%) que les boules explosent et récoltent
 *    - Rayon d'explosion: 3x3
 *
 * 3. CADEAUX - Chance rare qu'un cadeau de Noël tombe du ciel!
 *    - Tête custom avec texture configurable
 *    - Exécute des commandes au ramassage
 */
public class BlizzardEnchant implements HoeEnchant {

    private final RinaEnchantsPlugin plugin;
    private final Set<Material> CROPS = new HashSet<>();
    private final Set<Material> NO_AGE_CROPS = new HashSet<>();

    public BlizzardEnchant(RinaEnchantsPlugin plugin) {
        this.plugin = plugin;
        initCrops();
        plugin.getLogger().info("§b✓ BlizzardEternal: " + CROPS.size() + " types de cultures chargés!");
    }

    private void initCrops() {
        // Cultures classiques
        CROPS.add(Material.WHEAT);
        CROPS.add(Material.CARROTS);
        CROPS.add(Material.POTATOES);
        CROPS.add(Material.BEETROOTS);
        CROPS.add(Material.NETHER_WART);
        CROPS.add(Material.COCOA);
        CROPS.add(Material.SWEET_BERRY_BUSH);

        // Coraux
        CROPS.add(Material.TUBE_CORAL);
        CROPS.add(Material.BUBBLE_CORAL);
        CROPS.add(Material.BRAIN_CORAL);
        CROPS.add(Material.FIRE_CORAL);
        CROPS.add(Material.HORN_CORAL);
        CROPS.add(Material.TUBE_CORAL_BLOCK);
        CROPS.add(Material.BUBBLE_CORAL_BLOCK);
        CROPS.add(Material.BRAIN_CORAL_BLOCK);
        CROPS.add(Material.FIRE_CORAL_BLOCK);
        CROPS.add(Material.HORN_CORAL_BLOCK);

        // Racines nether
        CROPS.add(Material.WARPED_ROOTS);
        CROPS.add(Material.CRIMSON_ROOTS);
        CROPS.add(Material.NETHER_SPROUTS);
        CROPS.add(Material.TWISTING_VINES);
        CROPS.add(Material.WEEPING_VINES);

        // Fleurs
        CROPS.add(Material.LILAC);
        CROPS.add(Material.ROSE_BUSH);
        CROPS.add(Material.PEONY);
        CROPS.add(Material.SUNFLOWER);
        CROPS.add(Material.TALL_GRASS);
        CROPS.add(Material.LARGE_FERN);

        // Pousses
        CROPS.add(Material.OAK_SAPLING);
        CROPS.add(Material.BIRCH_SAPLING);
        CROPS.add(Material.JUNGLE_SAPLING);
        CROPS.add(Material.SPRUCE_SAPLING);
        CROPS.add(Material.CHERRY_SAPLING);
        CROPS.add(Material.ACACIA_SAPLING);
        CROPS.add(Material.DARK_OAK_SAPLING);
        CROPS.add(Material.MANGROVE_PROPAGULE);

        // Autres
        CROPS.add(Material.MELON);
        CROPS.add(Material.PUMPKIN);
        CROPS.add(Material.SUGAR_CANE);
        CROPS.add(Material.CACTUS);
        CROPS.add(Material.BAMBOO);
        CROPS.add(Material.KELP);
        CROPS.add(Material.KELP_PLANT);
        CROPS.add(Material.SEA_PICKLE);
        CROPS.add(Material.CHORUS_FLOWER);
        CROPS.add(Material.CHORUS_PLANT);

        // Cultures sans âge
        NO_AGE_CROPS.addAll(Arrays.asList(
            Material.MELON, Material.PUMPKIN, Material.SUGAR_CANE, Material.CACTUS,
            Material.BAMBOO, Material.KELP, Material.KELP_PLANT,
            Material.TUBE_CORAL, Material.BUBBLE_CORAL, Material.BRAIN_CORAL,
            Material.FIRE_CORAL, Material.HORN_CORAL,
            Material.TUBE_CORAL_BLOCK, Material.BUBBLE_CORAL_BLOCK, Material.BRAIN_CORAL_BLOCK,
            Material.FIRE_CORAL_BLOCK, Material.HORN_CORAL_BLOCK,
            Material.WARPED_ROOTS, Material.CRIMSON_ROOTS, Material.NETHER_SPROUTS,
            Material.LILAC, Material.ROSE_BUSH, Material.PEONY, Material.SUNFLOWER,
            Material.OAK_SAPLING, Material.BIRCH_SAPLING, Material.JUNGLE_SAPLING,
            Material.SPRUCE_SAPLING, Material.CHERRY_SAPLING, Material.ACACIA_SAPLING,
            Material.DARK_OAK_SAPLING, Material.MANGROVE_PROPAGULE,
            Material.CHORUS_FLOWER, Material.CHORUS_PLANT, Material.SEA_PICKLE
        ));
    }

    @Override
    public String getEnchantId() {
        return plugin.getConfig().getString("blizzard-eternal.enchant-id", "blizzard_eternal");
    }

    @Override
    public void onEnchantProc(Player player, long hoeLevel, long hoePrestige, long enchantLevel,
                              String enchantId, Location cropLocation, boolean isMultiHarvest) {

        boolean debug = plugin.getConfig().getBoolean("debug", false);

        if (debug) {
            plugin.getLogger().info("§b[BlizzardEternal] onEnchantProc! Joueur: " + player.getName() + ", Niveau: " + enchantLevel);
        }

        // ═══════════════════════════════════════════════════════════
        // VÉRIFICATION ANTI-CASCADE
        // ═══════════════════════════════════════════════════════════
        if (plugin.isEntityBreakingLocation(cropLocation)) {
            if (debug) {
                plugin.getLogger().info("§c[BlizzardEternal] Bloqué - isEntityBreakingLocation=true");
            }
            return;
        }

        // ═══════════════════════════════════════════════════════════
        // ENREGISTREMENT DU MULTIPLICATEUR CYBERLEVEL
        // ═══════════════════════════════════════════════════════════
        double cyberLevelMulti = plugin.getConfig().getDouble("blizzard-eternal.cyber-level-multi", 1.0);
        if (cyberLevelMulti > 1.0 && plugin.getCyberLevelListener() != null) {
            plugin.getCyberLevelListener().registerMultiplier(
                player.getUniqueId(), getEnchantId(), cyberLevelMulti, cropLocation);
        }

        // ═══════════════════════════════════════════════════════════
        // PARAMÈTRES DE CONFIG
        // ═══════════════════════════════════════════════════════════

        int maxLevel = plugin.getConfig().getInt("blizzard-eternal.max-level", 10000);

        // Durée du blizzard
        int baseDuration = plugin.getConfig().getInt("blizzard-eternal.base-duration", 5);
        int maxDuration = plugin.getConfig().getInt("blizzard-eternal.max-duration", 15);

        // Intensité (snowballs)
        int snowballsDivisor = plugin.getConfig().getInt("blizzard-eternal.snowballs-divisor", 100);
        int baseSnowballs = plugin.getConfig().getInt("blizzard-eternal.base-snowballs-per-second", 1);

        // Rayon et hauteur
        int blizzardRadius = plugin.getConfig().getInt("blizzard-eternal.blizzard-radius", 8);
        int spawnHeight = plugin.getConfig().getInt("blizzard-eternal.spawn-height", 10);

        // Explosions
        double explosionChance = plugin.getConfig().getDouble("blizzard-eternal.explosion-chance", 0.1);
        int explosionRadius = plugin.getConfig().getInt("blizzard-eternal.explosion-radius", 3);

        // Cadeaux
        boolean giftEnabled = plugin.getConfig().getBoolean("blizzard-eternal.gift.enabled", true);
        double giftChance = plugin.getConfig().getDouble("blizzard-eternal.gift.chance", 0.5);
        String giftTexture = plugin.getConfig().getString("blizzard-eternal.gift.texture",
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTI5MTljNjczMTdjNzY3ODQzOGZmNTIwYzk4ZGRlMGUzYjRkNjg3NjljODkzOGE1YTNkZTI5NjhlZGZjNzMxNCJ9fX0=");
        List<String> giftCommands = plugin.getConfig().getStringList("blizzard-eternal.gift.commands");
        String giftPickupMessage = plugin.getConfig().getString("blizzard-eternal.gift.pickup-message",
            "&b❄ &fVous avez trouvé un &6Cadeau de Noël&f!");

        // Effets
        boolean showParticles = plugin.getConfig().getBoolean("blizzard-eternal.particles", true);
        boolean clientSideOnly = plugin.getConfig().getBoolean("blizzard-eternal.client-side-only", true);

        // ═══════════════════════════════════════════════════════════
        // CALCUL DES VALEURS SELON LE NIVEAU
        // ═══════════════════════════════════════════════════════════

        double levelRatio = Math.min(1.0, (double) enchantLevel / maxLevel);

        // Durée: baseDuration + (niveau / 1000) secondes, cap à maxDuration
        int durationSeconds = baseDuration + (int)(enchantLevel / 1000);
        durationSeconds = Math.min(durationSeconds, maxDuration);
        int durationTicks = durationSeconds * 20;

        // Snowballs par seconde: (niveau / divisor) + base
        int snowballsPerSecond = (int)(enchantLevel / snowballsDivisor) + baseSnowballs;

        if (debug) {
            plugin.getLogger().info("§b[BlizzardEternal] Durée: " + durationSeconds + "s, Snowballs/s: " + snowballsPerSecond);
        }

        World world = cropLocation.getWorld();
        if (world == null) return;

        // Message de début
        if (plugin.getConfig().getBoolean("blizzard-eternal.start-message", true)) {
            String startMsg = plugin.getConfig().getString("blizzard-eternal.start-message-text",
                "&b❄ &fUn &bBlizzard Éternel &fenvahit la zone!");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', startMsg));
        }

        // ═══════════════════════════════════════════════════════════
        // CRÉATION ET DÉMARRAGE DE L'ANIMATION
        // ═══════════════════════════════════════════════════════════

        BlizzardAnimation animation = new BlizzardAnimation(
            plugin, player, cropLocation,
            durationTicks, snowballsPerSecond, blizzardRadius, spawnHeight,
            explosionChance, explosionRadius,
            giftEnabled, giftChance, giftTexture, giftCommands, giftPickupMessage,
            showParticles, clientSideOnly,
            CROPS, NO_AGE_CROPS
        );

        // Callback quand une culture est touchée
        animation.setOnCropHit((loc) -> {
            Block block = loc.getBlock();
            if (isMatureCrop(block)) {
                plugin.safeBreakCrop(player, loc);
            }
        });

        // Callback de fin
        animation.setOnFinish((totalCrops) -> {
            if (plugin.getConfig().getBoolean("blizzard-eternal.message", true) && totalCrops > 0) {
                String message = plugin.getConfig().getString("blizzard-eternal.message-text",
                    "&b❄ &fLe blizzard a récolté &b{count} &fcultures!");
                message = message.replace("{count}", String.valueOf(totalCrops));
                message = ChatColor.translateAlternateColorCodes('&', message);
                player.sendMessage(message);
            }
        });

        animation.start();
    }

    private boolean isMatureCrop(Block block) {
        Material type = block.getType();

        if (!CROPS.contains(type)) return false;
        if (NO_AGE_CROPS.contains(type)) return true;

        if (block.getBlockData() instanceof Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }

        return true;
    }
}
