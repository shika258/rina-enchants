package me.rinaorc.rinaenchants.enchant;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import me.rinaorc.rinaenchants.util.PandaAnimation;
import me.rivaldev.harvesterhoes.api.events.HoeEnchant;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Enchantement Panda Roll / Roulade de Panda
 * 
 * Le panda utilise setRolling(true) pour une vraie animation de roulade!
 * Système de combo avec chance configurable par niveau.
 * Client-side pour optimisation serveur 500+ joueurs.
 */
public class PandaRollEnchant implements HoeEnchant, Listener {

    private final RinaEnchantsPlugin plugin;
    private final Set<Material> CROPS = new HashSet<>();
    private final Set<Material> NO_AGE_CROPS = new HashSet<>();

    public PandaRollEnchant(RinaEnchantsPlugin plugin) {
        this.plugin = plugin;
        initCrops();
        plugin.getLogger().info("§a✓ PandaRoll: " + CROPS.size() + " types de cultures chargés!");
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
    
    /**
     * Plus besoin de listener onBlockBreak - la vérification se fait directement
     * dans onEnchantProc via isEntityBreakingLocation()
     */

    @Override
    public String getEnchantId() {
        return plugin.getConfig().getString("panda-roll.enchant-id", "panda_roll");
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
                plugin.getLogger().info("§e[DEBUG] PandaRoll: Ignoré - cassé par une entité");
            }
            return;
        }

        // ═══════════════════════════════════════════════════════════
        // ENREGISTREMENT DU MULTIPLICATEUR CYBERLEVEL
        // ═══════════════════════════════════════════════════════════
        double cyberLevelMulti = plugin.getConfig().getDouble("panda-roll.cyber-level-multi", 1.0);
        if (cyberLevelMulti > 1.0 && plugin.getCyberLevelListener() != null) {
            plugin.getCyberLevelListener().registerMultiplier(
                player.getUniqueId(), getEnchantId(), cyberLevelMulti, cropLocation);
        }

        if (debug) {
            plugin.getLogger().info("§e[DEBUG] PandaRoll proc! Niveau: " + enchantLevel);
        }

        // Récupérer les paramètres de config
        int rollDistance = plugin.getConfig().getInt("panda-roll.roll-distance", 5);
        double comboChancePerLevel = plugin.getConfig().getDouble("panda-roll.combo-chance-per-level", 0.25);
        boolean showParticles = plugin.getConfig().getBoolean("panda-roll.particles", true);
        boolean playSound = plugin.getConfig().getBoolean("panda-roll.sound", true);
        boolean clientSideOnly = plugin.getConfig().getBoolean("panda-roll.client-side-only", true);
        
        // ═══════════════════════════════════════════════════════════
        // CALCUL DE LA CHANCE DE COMBO CONFIGURABLE
        // combo-chance-per-level: 0.25 signifie niveau 100 = 25% de combo
        // ═══════════════════════════════════════════════════════════
        double comboChance = enchantLevel * comboChancePerLevel;
        comboChance = Math.min(100.0, comboChance); // Cap à 100%
        
        if (debug) {
            plugin.getLogger().info("§e[DEBUG] Chance de combo: " + comboChance + "%");
        }

        World world = cropLocation.getWorld();
        if (world == null) return;
        
        // Direction de la roulade (direction du regard du joueur)
        Vector playerDirection = player.getLocation().getDirection();
        playerDirection.setY(0);
        playerDirection.normalize();
        Vector rollDirection = getCardinalDirection(playerDirection);
        
        // Position de spawn
        Location pandaSpawn = cropLocation.clone().add(0, 0.5, 0);
        
        if (playSound) {
            player.playSound(pandaSpawn, Sound.ENTITY_PANDA_AGGRESSIVE_AMBIENT, 1.0f, 1.0f);
        }

        // Créer l'animation du panda
        PandaAnimation animation = new PandaAnimation(plugin, pandaSpawn, rollDirection, rollDistance, 
                                                       comboChance, showParticles, player, clientSideOnly);
        
        // Callback quand le panda passe sur une culture
        animation.setOnCropHit((cropLoc) -> {
            Block block = cropLoc.getBlock();
            if (isMatureCrop(block)) {
                // Utiliser la méthode sécurisée pour casser
                plugin.safeBreakCrop(player, cropLoc);
            }
        });
        
        // Callback combo
        animation.setOnCombo((comboCount) -> {
            if (plugin.getConfig().getBoolean("panda-roll.combo-message", true)) {
                String message = plugin.getConfig().getString("panda-roll.combo-message-text", 
                    "&d&l🐼 &5COMBO x{combo}!");
                message = message.replace("{combo}", String.valueOf(comboCount));
                message = ChatColor.translateAlternateColorCodes('&', message);
                player.sendMessage(message);
            }
            
            if (playSound) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f + (comboCount * 0.1f));
            }
        });
        
        // Callback fin
        animation.setOnFinish((totalCrops, totalCombos) -> {
            if (plugin.getConfig().getBoolean("panda-roll.message", true) && totalCrops > 0) {
                String message = plugin.getConfig().getString("panda-roll.message-text", 
                    "&d&l🐼 &5Le panda a récolté &d{count} &5cultures!");
                message = message.replace("{count}", String.valueOf(totalCrops));
                if (totalCombos > 0) {
                    message += ChatColor.translateAlternateColorCodes('&', " &7(x" + (totalCombos + 1) + " combos)");
                }
                message = ChatColor.translateAlternateColorCodes('&', message);
                player.sendMessage(message);
            }
        });
        
        animation.start();
    }
    
    private Vector getCardinalDirection(Vector direction) {
        double absX = Math.abs(direction.getX());
        double absZ = Math.abs(direction.getZ());
        
        if (absX > absZ) {
            return new Vector(direction.getX() > 0 ? 1 : -1, 0, 0);
        } else {
            return new Vector(0, 0, direction.getZ() > 0 ? 1 : -1);
        }
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
