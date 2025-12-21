package me.rinaorc.rinaenchants.enchant;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import me.rinaorc.rinaenchants.util.EnderDragonBreathAnimation;
import me.rivaldev.harvesterhoes.api.events.HoeEnchant;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.*;

/**
 * Enchantement Ender Dragon Breath / Souffle du Dragon
 *
 * Un mini Ender Dragon (scale 0.4) survole le champ en spirale
 * et crache des nuages de dragon breath qui récoltent les cultures.
 *
 * Mécaniques:
 * - Dragon miniature grâce à l'attribut SCALE
 * - Vol en spirale autour du point de proc
 * - Souffle périodique créant des zones de récolte
 * - Les zones persistent et continuent de récolter
 * - Souffle final massif à la fin
 *
 * Scaling (5000 niveaux):
 * - Durée du vol: augmente avec le niveau
 * - Rayon de la spirale: augmente avec le niveau
 * - Intervalle de souffle: diminue (plus fréquent)
 * - Rayon du souffle: augmente avec le niveau
 * - Durée des zones: augmente avec le niveau
 *
 * Client-side pour optimisation serveur 500+ joueurs.
 */
public class EnderDragonBreathEnchant implements HoeEnchant, Listener {

    private final RinaEnchantsPlugin plugin;
    private final Set<Material> CROPS = new HashSet<>();
    private final Set<Material> NO_AGE_CROPS = new HashSet<>();

    public EnderDragonBreathEnchant(RinaEnchantsPlugin plugin) {
        this.plugin = plugin;
        initCrops();
        plugin.getLogger().info("§a✓ EnderDragonBreath: " + CROPS.size() + " types de cultures chargés!");
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
        return plugin.getConfig().getString("ender-dragon-breath.enchant-id", "ender_dragon_breath");
    }

    @Override
    public void onEnchantProc(Player player, long hoeLevel, long hoePrestige, long enchantLevel,
                              String enchantId, Location cropLocation, boolean isMultiHarvest) {

        boolean debug = plugin.getConfig().getBoolean("debug", false);

        // ═══════════════════════════════════════════════════════════
        // VÉRIFICATION ANTI-CASCADE: Empêche les proc récursifs
        // ═══════════════════════════════════════════════════════════
        if (plugin.isEntityBreakingLocation(cropLocation)) {
            if (debug) {
                plugin.getLogger().info("§e[DEBUG] EnderDragonBreath: Ignoré - cassé par une entité");
            }
            return;
        }

        if (debug) {
            plugin.getLogger().info("§e[DEBUG] EnderDragonBreath proc! Niveau: " + enchantLevel);
        }

        // ═══════════════════════════════════════════════════════════
        // RÉCUPÉRER LES PARAMÈTRES DE CONFIG
        // ═══════════════════════════════════════════════════════════

        int maxLevel = plugin.getConfig().getInt("ender-dragon-breath.max-level", 5000);
        double levelRatio = Math.min(1.0, (double) enchantLevel / maxLevel);

        // Scale du dragon (fixe à 0.4 par défaut)
        double dragonScale = plugin.getConfig().getDouble("ender-dragon-breath.dragon-scale", 0.4);

        // Durée du vol (en ticks)
        int baseFlightDuration = plugin.getConfig().getInt("ender-dragon-breath.base-flight-duration", 100);
        int maxFlightDuration = plugin.getConfig().getInt("ender-dragon-breath.max-flight-duration", 400);
        int flightDuration = (int) (baseFlightDuration + levelRatio * (maxFlightDuration - baseFlightDuration));

        // Rayon de la spirale
        double baseSpiralRadius = plugin.getConfig().getDouble("ender-dragon-breath.base-spiral-radius", 5.0);
        double maxSpiralRadius = plugin.getConfig().getDouble("ender-dragon-breath.max-spiral-radius", 15.0);
        double spiralRadius = baseSpiralRadius + levelRatio * (maxSpiralRadius - baseSpiralRadius);

        // Vitesse de rotation (radians par tick)
        double spiralSpeed = plugin.getConfig().getDouble("ender-dragon-breath.spiral-speed", 0.1);

        // Intervalle de souffle (en ticks) - diminue avec le niveau
        int baseBreathInterval = plugin.getConfig().getInt("ender-dragon-breath.base-breath-interval", 40);
        int minBreathInterval = plugin.getConfig().getInt("ender-dragon-breath.min-breath-interval", 15);
        int breathInterval = (int) (baseBreathInterval - levelRatio * (baseBreathInterval - minBreathInterval));

        // Rayon du souffle
        double baseBreathRadius = plugin.getConfig().getDouble("ender-dragon-breath.base-breath-radius", 3.0);
        double maxBreathRadius = plugin.getConfig().getDouble("ender-dragon-breath.max-breath-radius", 8.0);
        double breathRadius = baseBreathRadius + levelRatio * (maxBreathRadius - baseBreathRadius);

        // Durée des zones de souffle (en ticks)
        int baseBreathDuration = plugin.getConfig().getInt("ender-dragon-breath.base-breath-zone-duration", 40);
        int maxBreathDuration = plugin.getConfig().getInt("ender-dragon-breath.max-breath-zone-duration", 100);
        int breathDuration = (int) (baseBreathDuration + levelRatio * (maxBreathDuration - baseBreathDuration));

        boolean showParticles = plugin.getConfig().getBoolean("ender-dragon-breath.particles", true);
        boolean playSound = plugin.getConfig().getBoolean("ender-dragon-breath.sound", true);
        boolean clientSideOnly = plugin.getConfig().getBoolean("ender-dragon-breath.client-side-only", true);

        if (debug) {
            plugin.getLogger().info("§e[DEBUG] EnderDragonBreath - Scale: " + dragonScale +
                ", Durée: " + flightDuration + "t, Spirale: " + String.format("%.1f", spiralRadius) +
                ", Souffle: " + String.format("%.1f", breathRadius) + " tous les " + breathInterval + "t");
        }

        World world = cropLocation.getWorld();
        if (world == null) return;

        // ═══════════════════════════════════════════════════════════
        // MESSAGE DE DÉBUT
        // ═══════════════════════════════════════════════════════════

        if (plugin.getConfig().getBoolean("ender-dragon-breath.start-message", true)) {
            String message = plugin.getConfig().getString("ender-dragon-breath.start-message-text",
                "&5&l🐉 &dUn Dragon de l'End apparaît!");
            message = ChatColor.translateAlternateColorCodes('&', message);
            player.sendMessage(message);
        }

        // ═══════════════════════════════════════════════════════════
        // CRÉER L'ANIMATION DU DRAGON
        // ═══════════════════════════════════════════════════════════

        EnderDragonBreathAnimation animation = new EnderDragonBreathAnimation(
            plugin, cropLocation, player,
            dragonScale, flightDuration, spiralRadius, spiralSpeed,
            breathInterval, breathRadius, breathDuration,
            showParticles, clientSideOnly
        );

        // Callback quand une culture est récoltée
        animation.setOnCropHit((cropLoc) -> {
            Block block = cropLoc.getBlock();
            if (isMatureCrop(block)) {
                plugin.safeBreakCrop(player, cropLoc, "ender-dragon-breath");
            }
        });

        // Callback nuage de souffle
        animation.setOnBreathCloud((cloudCount) -> {
            if (debug) {
                plugin.getLogger().info("§e[DEBUG] EnderDragonBreath: Souffle #" + cloudCount);
            }
        });

        // Callback fin
        animation.setOnFinish((totalCrops, totalClouds) -> {
            if (plugin.getConfig().getBoolean("ender-dragon-breath.message", true) && totalCrops > 0) {
                String message = plugin.getConfig().getString("ender-dragon-breath.message-text",
                    "&5&l🐉 &dLe Dragon a incinéré &5{count} &dcultures!");
                message = message.replace("{count}", String.valueOf(totalCrops));

                if (totalClouds > 0) {
                    String cloudSuffix = plugin.getConfig().getString("ender-dragon-breath.cloud-suffix",
                        " &7(&5{clouds} souffles&7)");
                    cloudSuffix = cloudSuffix.replace("{clouds}", String.valueOf(totalClouds));
                    message += cloudSuffix;
                }

                message = ChatColor.translateAlternateColorCodes('&', message);
                player.sendMessage(message);
            }

            if (playSound) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f, 1.0f);
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
