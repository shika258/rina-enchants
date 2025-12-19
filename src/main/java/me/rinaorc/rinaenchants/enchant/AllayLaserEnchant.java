package me.rinaorc.rinaenchants.enchant;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import me.rinaorc.rinaenchants.util.AllayAnimation;
import me.rivaldev.harvesterhoes.api.events.HoeEnchant;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.*;

/**
 * Enchantement Allay Laser
 * 
 * Un Allay apparaît, suit le joueur et tire des lasers de particules
 * vers les cultures pour les récolter automatiquement.
 * 
 * 1000 NIVEAUX MAXIMUM:
 * - Plus le niveau est élevé, plus l'Allay tire rapidement
 * - Plus le niveau est élevé, plus le rayon d'absorption est grand
 * - Plus le niveau est élevé, plus l'Allay reste longtemps
 * 
 * OPTIMISATIONS:
 * - Allay visible uniquement par le joueur (client-side)
 * - Lasers en particules (pas d'entités)
 */
public class AllayLaserEnchant implements HoeEnchant, Listener {

    private final RinaEnchantsPlugin plugin;
    private final Set<Material> CROPS = new HashSet<>();
    private final Set<Material> NO_AGE_CROPS = new HashSet<>();

    public AllayLaserEnchant(RinaEnchantsPlugin plugin) {
        this.plugin = plugin;
        initCrops();
        plugin.getLogger().info("§a✓ AllayLaser: " + CROPS.size() + " types de cultures chargés!");
    }
    
    private void initCrops() {
        CROPS.addAll(Arrays.asList(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.COCOA, Material.SWEET_BERRY_BUSH,
            Material.MELON, Material.PUMPKIN, Material.SUGAR_CANE, Material.CACTUS,
            Material.BAMBOO, Material.KELP, Material.KELP_PLANT,
            Material.TUBE_CORAL, Material.BUBBLE_CORAL, Material.BRAIN_CORAL,
            Material.FIRE_CORAL, Material.HORN_CORAL,
            Material.TUBE_CORAL_BLOCK, Material.BUBBLE_CORAL_BLOCK, Material.BRAIN_CORAL_BLOCK,
            Material.FIRE_CORAL_BLOCK, Material.HORN_CORAL_BLOCK,
            Material.WARPED_ROOTS, Material.CRIMSON_ROOTS, Material.NETHER_SPROUTS,
            Material.TWISTING_VINES, Material.WEEPING_VINES,
            Material.LILAC, Material.ROSE_BUSH, Material.PEONY, Material.SUNFLOWER,
            Material.TALL_GRASS, Material.LARGE_FERN,
            Material.OAK_SAPLING, Material.BIRCH_SAPLING, Material.JUNGLE_SAPLING,
            Material.SPRUCE_SAPLING, Material.CHERRY_SAPLING, Material.ACACIA_SAPLING,
            Material.DARK_OAK_SAPLING, Material.MANGROVE_PROPAGULE,
            Material.SEA_PICKLE, Material.CHORUS_FLOWER, Material.CHORUS_PLANT
        ));
        
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
    
    /**
     * Plus besoin de listener onBlockBreak - la vérification se fait directement
     * dans onEnchantProc via isEntityBreakingLocation()
     */

    @Override
    public String getEnchantId() {
        return plugin.getConfig().getString("allay-laser.enchant-id", "allay_laser");
    }

    @Override
    public void onEnchantProc(Player player, long hoeLevel, long hoePrestige, long enchantLevel, 
                              String enchantId, Location cropLocation, boolean isMultiHarvest) {
        
        boolean debug = plugin.getConfig().getBoolean("debug", false);

        if (debug) {
            plugin.getLogger().info("§b[AllayLaser] onEnchantProc appelé! Joueur: " + player.getName() + ", Niveau: " + enchantLevel);
        }

        // ═══════════════════════════════════════════════════════════
        // VÉRIFICATION ANTI-CASCADE: Empêche les proc récursifs
        // ═══════════════════════════════════════════════════════════
        if (plugin.isEntityBreakingLocation(cropLocation)) {
            if (debug) {
                plugin.getLogger().info("§c[AllayLaser] Bloqué - isEntityBreakingLocation=true");
            }
            return;
        }

        int maxLevel = plugin.getConfig().getInt("allay-laser.max-level", 1000);
        int baseRadius = plugin.getConfig().getInt("allay-laser.base-radius", 3);
        int baseDuration = plugin.getConfig().getInt("allay-laser.base-duration", 100);
        int maxDuration = plugin.getConfig().getInt("allay-laser.max-duration", 600);
        int baseFireRate = plugin.getConfig().getInt("allay-laser.base-fire-rate", 20);
        int minFireRate = plugin.getConfig().getInt("allay-laser.min-fire-rate", 2);
        boolean showParticles = plugin.getConfig().getBoolean("allay-laser.particles", true);
        boolean playSound = plugin.getConfig().getBoolean("allay-laser.sound", true);
        boolean clientSideOnly = plugin.getConfig().getBoolean("allay-laser.client-side-only", true);
        
        double levelRatio = Math.min(1.0, (double) enchantLevel / maxLevel);
        
        int radius = baseRadius + (int)(enchantLevel / 50);
        radius = Math.min(radius, baseRadius + 20);
        
        int fireRate = (int)(baseFireRate - (levelRatio * (baseFireRate - minFireRate)));
        fireRate = Math.max(minFireRate, fireRate);
        
        int duration = (int)(baseDuration + (levelRatio * (maxDuration - baseDuration)));

        if (debug) {
            plugin.getLogger().info("§b[AllayLaser] Paramètres: Rayon=" + radius + ", FireRate=" + fireRate + "t, Durée=" + duration + "t");
        }

        World world = cropLocation.getWorld();
        if (world == null) {
            return;
        }

        Location allaySpawn = player.getLocation().clone().add(
            -player.getLocation().getDirection().getX() * 1.5,
            2.0,
            -player.getLocation().getDirection().getZ() * 1.5
        );

        if (debug) {
            plugin.getLogger().info("§b[AllayLaser] Spawn Allay à " + allaySpawn.getBlockX() + "," + allaySpawn.getBlockY() + "," + allaySpawn.getBlockZ());
        }
        
        if (playSound) {
            player.playSound(allaySpawn, Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 1.0f, 1.2f);
        }
        
        // Message de début au joueur
        if (plugin.getConfig().getBoolean("allay-laser.start-message", true)) {
            String startMsg = plugin.getConfig().getString("allay-laser.start-message-text", 
                "&b&l✦ &3Un Allay magique vous assiste!");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', startMsg));
        }

        AllayAnimation animation = new AllayAnimation(plugin, allaySpawn, player, radius, fireRate, 
                                                       duration, showParticles, clientSideOnly, CROPS, NO_AGE_CROPS);
        
        animation.setOnCropHit((loc) -> {
            Block block = loc.getBlock();
            if (isMatureCrop(block)) {
                plugin.safeBreakCrop(player, loc);
            }
        });
        
        animation.setOnFinish((totalCrops) -> {
            if (plugin.getConfig().getBoolean("allay-laser.message", true) && totalCrops > 0) {
                String message = plugin.getConfig().getString("allay-laser.message-text", 
                    "&b&l✦ &3L'Allay a récolté &b{count} &3cultures avec ses lasers!");
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
