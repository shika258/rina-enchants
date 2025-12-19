package me.rinaorc.rinaenchants.enchant;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import me.rinaorc.rinaenchants.util.RavagerAnimation;
import me.rivaldev.harvesterhoes.api.events.HoeEnchant;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Enchantement RAVAGER STAMPEDE / Charge du Ravageur
 * 
 * Un Ravageur massif apparaît et charge dans la direction du joueur!
 * 
 * MÉCANIQUE EN 3 PHASES:
 * ═══════════════════════════════════════════════════════════════
 * 
 * 1. CHARGE - Le Ravageur fonce en ligne droite
 *    - Écrase les cultures sur un chemin de 3 blocs de large
 *    - Distance augmente avec le niveau
 * 
 * 2. STOMP - Toutes les X blocs, piétinement
 *    - Onde de choc circulaire qui récolte autour
 *    - Rayon du stomp augmente avec le niveau
 *    - Fréquence augmente avec le niveau
 * 
 * 3. ROAR - Rugissement final
 *    - Explosion de récolte MASSIVE à la fin
 *    - Rayon du roar augmente avec le niveau
 *    - Effets visuels épiques!
 * 
 * SCALING (100 niveaux max):
 * - Niveau 1: Distance 10, Stomp rayon 3, Roar rayon 5
 * - Niveau 50: Distance 20, Stomp rayon 5, Roar rayon 10
 * - Niveau 100: Distance 30, Stomp rayon 8, Roar rayon 15
 */
public class RavagerStampedeEnchant implements HoeEnchant {

    private final RinaEnchantsPlugin plugin;
    private final Set<Material> CROPS = new HashSet<>();
    private final Set<Material> NO_AGE_CROPS = new HashSet<>();

    public RavagerStampedeEnchant(RinaEnchantsPlugin plugin) {
        this.plugin = plugin;
        initCrops();
        plugin.getLogger().info("§c✓ RavagerStampede: " + CROPS.size() + " types de cultures chargés!");
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
        CROPS.add(Material.TUBE_CORAL_FAN);
        CROPS.add(Material.BUBBLE_CORAL_FAN);
        CROPS.add(Material.BRAIN_CORAL_FAN);
        CROPS.add(Material.FIRE_CORAL_FAN);
        CROPS.add(Material.HORN_CORAL_FAN);
        
        // Cultures spéciales IridiumSkyblock
        try {
            CROPS.add(Material.valueOf("MELON"));
            CROPS.add(Material.valueOf("PUMPKIN"));
            CROPS.add(Material.valueOf("CACTUS"));
            CROPS.add(Material.valueOf("SUGAR_CANE"));
            CROPS.add(Material.valueOf("BAMBOO"));
            CROPS.add(Material.valueOf("KELP"));
            CROPS.add(Material.valueOf("CHORUS_FLOWER"));
            CROPS.add(Material.valueOf("SEA_PICKLE"));
        } catch (Exception ignored) {}
        
        // Cultures sans âge
        NO_AGE_CROPS.add(Material.MELON);
        NO_AGE_CROPS.add(Material.PUMPKIN);
        NO_AGE_CROPS.add(Material.CACTUS);
        NO_AGE_CROPS.add(Material.SUGAR_CANE);
        NO_AGE_CROPS.add(Material.BAMBOO);
        NO_AGE_CROPS.add(Material.CHORUS_FLOWER);
        
        // Coraux (pas d'âge)
        NO_AGE_CROPS.add(Material.TUBE_CORAL);
        NO_AGE_CROPS.add(Material.BUBBLE_CORAL);
        NO_AGE_CROPS.add(Material.BRAIN_CORAL);
        NO_AGE_CROPS.add(Material.FIRE_CORAL);
        NO_AGE_CROPS.add(Material.HORN_CORAL);
        NO_AGE_CROPS.add(Material.TUBE_CORAL_FAN);
        NO_AGE_CROPS.add(Material.BUBBLE_CORAL_FAN);
        NO_AGE_CROPS.add(Material.BRAIN_CORAL_FAN);
        NO_AGE_CROPS.add(Material.FIRE_CORAL_FAN);
        NO_AGE_CROPS.add(Material.HORN_CORAL_FAN);
    }

    @Override
    public String getEnchantId() {
        return plugin.getConfig().getString("ravager-stampede.enchant-id", "ravager_stampede");
    }

    @Override
    public void onEnchantProc(Player player, long hoeLevel, long hoePrestige, long enchantLevel, 
                              String enchantId, Location cropLocation, boolean isMultiHarvest) {
        
        boolean debug = plugin.getConfig().getBoolean("debug", false);
        
        if (debug) {
            plugin.getLogger().info("§c[RavagerStampede] onEnchantProc! Joueur: " + player.getName() + ", Niveau: " + enchantLevel);
        }
        
        // ═══════════════════════════════════════════════════════════
        // VÉRIFICATION ANTI-CASCADE
        // ═══════════════════════════════════════════════════════════
        if (plugin.isEntityBreakingLocation(cropLocation)) {
            if (debug) {
                plugin.getLogger().info("§c[RavagerStampede] Bloqué - isEntityBreakingLocation=true");
            }
            return;
        }

        // ═══════════════════════════════════════════════════════════
        // PARAMÈTRES DE CONFIG
        // ═══════════════════════════════════════════════════════════
        
        int maxLevel = plugin.getConfig().getInt("ravager-stampede.max-level", 100);
        
        // Paramètres de base
        int baseChargeDistance = plugin.getConfig().getInt("ravager-stampede.base-charge-distance", 10);
        int maxChargeDistance = plugin.getConfig().getInt("ravager-stampede.max-charge-distance", 30);
        
        int baseStompInterval = plugin.getConfig().getInt("ravager-stampede.base-stomp-interval", 15);
        int minStompInterval = plugin.getConfig().getInt("ravager-stampede.min-stomp-interval", 5);
        
        int baseStompRadius = plugin.getConfig().getInt("ravager-stampede.base-stomp-radius", 3);
        int maxStompRadius = plugin.getConfig().getInt("ravager-stampede.max-stomp-radius", 8);
        
        int baseRoarRadius = plugin.getConfig().getInt("ravager-stampede.base-roar-radius", 5);
        int maxRoarRadius = plugin.getConfig().getInt("ravager-stampede.max-roar-radius", 15);
        
        boolean showParticles = plugin.getConfig().getBoolean("ravager-stampede.particles", true);
        boolean playSound = plugin.getConfig().getBoolean("ravager-stampede.sound", true);
        boolean clientSideOnly = plugin.getConfig().getBoolean("ravager-stampede.client-side-only", true);
        
        // ═══════════════════════════════════════════════════════════
        // CALCUL DES VALEURS SELON LE NIVEAU
        // ═══════════════════════════════════════════════════════════
        
        double levelRatio = Math.min(1.0, (double) enchantLevel / maxLevel);
        
        // Distance de charge (linéaire)
        int chargeDistance = baseChargeDistance + (int)(levelRatio * (maxChargeDistance - baseChargeDistance));
        
        // Intervalle de stomp (diminue = plus fréquent)
        int stompInterval = baseStompInterval - (int)(levelRatio * (baseStompInterval - minStompInterval));
        stompInterval = Math.max(minStompInterval, stompInterval);
        
        // Rayon de stomp (augmente)
        int stompRadius = baseStompRadius + (int)(levelRatio * (maxStompRadius - baseStompRadius));
        
        // Rayon de roar (augmente)
        int roarRadius = baseRoarRadius + (int)(levelRatio * (maxRoarRadius - baseRoarRadius));
        
        if (debug) {
            plugin.getLogger().info("§c[RavagerStampede] Distance: " + chargeDistance + 
                ", StompInterval: " + stompInterval + "t, StompRadius: " + stompRadius + 
                ", RoarRadius: " + roarRadius);
        }

        World world = cropLocation.getWorld();
        if (world == null) return;

        // ═══════════════════════════════════════════════════════════
        // DIRECTION DE CHARGE (direction du regard du joueur)
        // ═══════════════════════════════════════════════════════════
        
        Vector playerDirection = player.getLocation().getDirection();
        playerDirection.setY(0); // Horizontal uniquement
        playerDirection.normalize();
        
        // Position de spawn (devant le joueur)
        Location ravagerSpawn = player.getLocation().clone().add(
            playerDirection.getX() * 2,
            0,
            playerDirection.getZ() * 2
        );
        
        // Message de début
        if (plugin.getConfig().getBoolean("ravager-stampede.start-message", true)) {
            String startMsg = plugin.getConfig().getString("ravager-stampede.start-message-text", 
                "&c&l⚔ &4Un Ravageur déchaîné charge!");
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', startMsg));
        }
        
        if (playSound) {
            player.playSound(ravagerSpawn, Sound.ENTITY_RAVAGER_CELEBRATE, 1.5f, 0.8f);
        }

        // ═══════════════════════════════════════════════════════════
        // CRÉATION ET DÉMARRAGE DE L'ANIMATION
        // ═══════════════════════════════════════════════════════════
        
        RavagerAnimation animation = new RavagerAnimation(
            plugin, ravagerSpawn, playerDirection, player,
            chargeDistance, stompInterval, stompRadius, roarRadius,
            showParticles, clientSideOnly, CROPS, NO_AGE_CROPS
        );
        
        // Callback quand une culture est touchée
        animation.setOnCropHit((loc) -> {
            Block block = loc.getBlock();
            if (isMatureCrop(block)) {
                plugin.safeBreakCrop(player, loc, "ravager-stampede");
            }
        });
        
        // Callback à chaque stomp
        animation.setOnStomp(() -> {
            if (playSound) {
                player.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ATTACK, 0.5f, 1.2f);
            }
        });
        
        // Callback de fin
        animation.setOnFinish((totalCrops, totalStomps) -> {
            if (plugin.getConfig().getBoolean("ravager-stampede.message", true) && totalCrops > 0) {
                String message = plugin.getConfig().getString("ravager-stampede.message-text", 
                    "&c&l⚔ &4Le Ravageur a dévasté &c{count} &4cultures avec &c{stomps} &4piétinements!");
                message = message.replace("{count}", String.valueOf(totalCrops));
                message = message.replace("{stomps}", String.valueOf(totalStomps));
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
