package me.rinaorc.rinaenchants.enchant;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import me.rinaorc.rinaenchants.util.WardenPulseAnimation;
import me.rivaldev.harvesterhoes.api.events.HoeEnchant;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.*;

/**
 * Enchantement Warden Pulse / Onde du Warden
 *
 * Le Warden émerge du sol et émet des ondes soniques concentriques
 * qui récoltent les cultures en vagues successives.
 *
 * Mécaniques:
 * - Animation d'émergence iconique du Warden
 * - Pulses soniques qui s'étendent en cercles concentriques
 * - Système de résonance: une zone touchée 2+ fois donne un bonus
 * - Effets visuels sculk et sonic boom spectaculaires
 *
 * Scaling (2000 niveaux):
 * - Nombre de pulses: augmente avec le niveau
 * - Rayon max des pulses: augmente avec le niveau
 * - Intervalle entre pulses: diminue (plus rapide)
 * - Vitesse d'expansion: augmente avec le niveau
 *
 * Client-side pour optimisation serveur 500+ joueurs.
 */
public class WardenPulseEnchant implements HoeEnchant, Listener {

    private final RinaEnchantsPlugin plugin;
    private final Set<Material> CROPS = new HashSet<>();
    private final Set<Material> NO_AGE_CROPS = new HashSet<>();

    public WardenPulseEnchant(RinaEnchantsPlugin plugin) {
        this.plugin = plugin;
        initCrops();
        plugin.getLogger().info("§a✓ WardenPulse: " + CROPS.size() + " types de cultures chargés!");
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
        return plugin.getConfig().getString("warden-pulse.enchant-id", "warden_pulse");
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
                plugin.getLogger().info("§e[DEBUG] WardenPulse: Ignoré - cassé par une entité");
            }
            return;
        }

        if (debug) {
            plugin.getLogger().info("§e[DEBUG] WardenPulse proc! Niveau: " + enchantLevel);
        }

        // ═══════════════════════════════════════════════════════════
        // RÉCUPÉRER LES PARAMÈTRES DE CONFIG
        // ═══════════════════════════════════════════════════════════

        int maxLevel = plugin.getConfig().getInt("warden-pulse.max-level", 2000);
        double levelRatio = Math.min(1.0, (double) enchantLevel / maxLevel);

        // Rayon maximum de l'onde unique
        int baseMaxRadius = plugin.getConfig().getInt("warden-pulse.base-max-radius", 8);
        int maxMaxRadius = plugin.getConfig().getInt("warden-pulse.max-max-radius", 25);
        int maxPulseRadius = (int) (baseMaxRadius + levelRatio * (maxMaxRadius - baseMaxRadius));

        // Vitesse d'expansion de l'onde (blocs par seconde)
        int baseExpandSpeed = plugin.getConfig().getInt("warden-pulse.base-expand-speed", 8);
        int maxExpandSpeed = plugin.getConfig().getInt("warden-pulse.max-expand-speed", 20);
        int pulseExpandSpeed = (int) (baseExpandSpeed + levelRatio * (maxExpandSpeed - baseExpandSpeed));

        boolean showParticles = plugin.getConfig().getBoolean("warden-pulse.particles", true);
        boolean playSound = plugin.getConfig().getBoolean("warden-pulse.sound", true);
        boolean clientSideOnly = plugin.getConfig().getBoolean("warden-pulse.client-side-only", true);

        if (debug) {
            plugin.getLogger().info("§e[DEBUG] WardenPulse - Rayon max: " + maxPulseRadius +
                ", Vitesse: " + pulseExpandSpeed + " blocs/s");
        }

        World world = cropLocation.getWorld();
        if (world == null) return;

        // ═══════════════════════════════════════════════════════════
        // MESSAGE DE DÉBUT
        // ═══════════════════════════════════════════════════════════

        if (plugin.getConfig().getBoolean("warden-pulse.start-message", true)) {
            String message = plugin.getConfig().getString("warden-pulse.start-message-text",
                "&8&l🔊 &7Le &8Warden &7émerge des profondeurs...");
            message = ChatColor.translateAlternateColorCodes('&', message);
            player.sendMessage(message);
        }

        // ═══════════════════════════════════════════════════════════
        // CRÉER L'ANIMATION DU WARDEN (onde unique avec glowing cyan)
        // ═══════════════════════════════════════════════════════════

        WardenPulseAnimation animation = new WardenPulseAnimation(
            plugin, cropLocation, player,
            maxPulseRadius, pulseExpandSpeed,
            showParticles, clientSideOnly
        );

        // Callback quand une culture est récoltée
        animation.setOnCropHit((cropLoc) -> {
            Block block = cropLoc.getBlock();
            if (isMatureCrop(block)) {
                plugin.safeBreakCrop(player, cropLoc, "warden-pulse");
            }
        });

        // Callback résonance
        animation.setOnResonance((resonanceCount) -> {
            if (plugin.getConfig().getBoolean("warden-pulse.resonance-message", true)) {
                // Message uniquement toutes les 5 résonances pour ne pas spam
                if (resonanceCount % 5 == 0) {
                    String message = plugin.getConfig().getString("warden-pulse.resonance-message-text",
                        "&8&l🔊 &5RÉSONANCE! &7x{count}");
                    message = message.replace("{count}", String.valueOf(resonanceCount));
                    message = ChatColor.translateAlternateColorCodes('&', message);
                    player.sendMessage(message);
                }
            }
        });

        // Callback fin
        animation.setOnFinish((totalCrops, totalResonances) -> {
            if (plugin.getConfig().getBoolean("warden-pulse.message", true) && totalCrops > 0) {
                String message = plugin.getConfig().getString("warden-pulse.message-text",
                    "&8&l🔊 &7Les ondes du Warden ont récolté &8{count} &7cultures!");
                message = message.replace("{count}", String.valueOf(totalCrops));

                if (totalResonances > 0) {
                    String resonanceSuffix = plugin.getConfig().getString("warden-pulse.resonance-suffix",
                        " &7(&5{resonances} résonances&7)");
                    resonanceSuffix = resonanceSuffix.replace("{resonances}", String.valueOf(totalResonances));
                    message += resonanceSuffix;
                }

                message = ChatColor.translateAlternateColorCodes('&', message);
                player.sendMessage(message);
            }

            if (playSound) {
                player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.4f, 1.5f);
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
