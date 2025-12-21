package me.rinaorc.rinaenchants.enchant;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import me.rinaorc.rinaenchants.util.GolemFactoryAnimation;
import me.rivaldev.harvesterhoes.api.events.HoeEnchant;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.*;

/**
 * Enchantement Golem Factory / Usine à Golems
 *
 * Spawne des Iron Golems miniatures qui patrouillent et récoltent.
 * Quand 2 golems se rencontrent, ils peuvent fusionner en un
 * golem géant qui fait un ground slam massif!
 *
 * Mécaniques:
 * - Mini-golems avec attribut SCALE réduit
 * - Patrouille aléatoire dans une zone
 * - Animation de swing arm lors de la récolte
 * - Système de fusion: 2 golems → 1 golem géant
 * - Ground slam avec shockwave circulaire
 *
 * Scaling (500 niveaux):
 * - Nombre de golems: augmente avec le niveau
 * - Chance de merge: augmente avec le niveau
 * - Rayon du slam: augmente avec le niveau
 * - Durée: augmente avec le niveau
 *
 * Client-side pour optimisation serveur 500+ joueurs.
 */
public class GolemFactoryEnchant implements HoeEnchant, Listener {

    private final RinaEnchantsPlugin plugin;
    private final Set<Material> CROPS = new HashSet<>();
    private final Set<Material> NO_AGE_CROPS = new HashSet<>();

    public GolemFactoryEnchant(RinaEnchantsPlugin plugin) {
        this.plugin = plugin;
        initCrops();
        plugin.getLogger().info("§a✓ GolemFactory: " + CROPS.size() + " types de cultures chargés!");
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
        return plugin.getConfig().getString("golem-factory.enchant-id", "golem_factory");
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
                plugin.getLogger().info("§e[DEBUG] GolemFactory: Ignoré - cassé par une entité");
            }
            return;
        }

        if (debug) {
            plugin.getLogger().info("§e[DEBUG] GolemFactory proc! Niveau: " + enchantLevel);
        }

        // ═══════════════════════════════════════════════════════════
        // RÉCUPÉRER LES PARAMÈTRES DE CONFIG
        // ═══════════════════════════════════════════════════════════

        int maxLevel = plugin.getConfig().getInt("golem-factory.max-level", 500);
        double levelRatio = Math.min(1.0, (double) enchantLevel / maxLevel);

        // Nombre de golems
        int baseGolemCount = plugin.getConfig().getInt("golem-factory.base-golem-count", 2);
        int maxGolemCount = plugin.getConfig().getInt("golem-factory.max-golem-count", 6);
        int golemCount = (int) (baseGolemCount + levelRatio * (maxGolemCount - baseGolemCount));

        // Taille des golems (scale)
        double baseScale = plugin.getConfig().getDouble("golem-factory.base-scale", 0.4);
        double maxScale = plugin.getConfig().getDouble("golem-factory.max-scale", 0.7);
        double golemScale = baseScale + levelRatio * (maxScale - baseScale);

        // Rayon de patrouille
        int basePatrolRadius = plugin.getConfig().getInt("golem-factory.base-patrol-radius", 5);
        int maxPatrolRadius = plugin.getConfig().getInt("golem-factory.max-patrol-radius", 12);
        int patrolRadius = (int) (basePatrolRadius + levelRatio * (maxPatrolRadius - basePatrolRadius));

        // Durée
        int baseDuration = plugin.getConfig().getInt("golem-factory.base-duration", 100);
        int maxDuration = plugin.getConfig().getInt("golem-factory.max-duration", 300);
        int duration = (int) (baseDuration + levelRatio * (maxDuration - baseDuration));

        // Chance de fusion (%)
        double baseMergeChance = plugin.getConfig().getDouble("golem-factory.base-merge-chance", 5.0);
        double maxMergeChance = plugin.getConfig().getDouble("golem-factory.max-merge-chance", 25.0);
        double mergeChance = baseMergeChance + levelRatio * (maxMergeChance - baseMergeChance);

        // Rayon du slam
        int baseSlamRadius = plugin.getConfig().getInt("golem-factory.base-slam-radius", 4);
        int maxSlamRadius = plugin.getConfig().getInt("golem-factory.max-slam-radius", 10);
        int slamRadius = (int) (baseSlamRadius + levelRatio * (maxSlamRadius - baseSlamRadius));

        boolean showParticles = plugin.getConfig().getBoolean("golem-factory.particles", true);
        boolean playSound = plugin.getConfig().getBoolean("golem-factory.sound", true);
        boolean clientSideOnly = plugin.getConfig().getBoolean("golem-factory.client-side-only", true);

        if (debug) {
            plugin.getLogger().info("§e[DEBUG] GolemFactory - Golems: " + golemCount +
                ", Scale: " + String.format("%.2f", golemScale) +
                ", Merge%: " + String.format("%.1f", mergeChance) +
                ", Slam: " + slamRadius);
        }

        World world = cropLocation.getWorld();
        if (world == null) return;

        // ═══════════════════════════════════════════════════════════
        // MESSAGE DE DÉBUT
        // ═══════════════════════════════════════════════════════════

        if (plugin.getConfig().getBoolean("golem-factory.start-message", true)) {
            String message = plugin.getConfig().getString("golem-factory.start-message-text",
                "&7&l🤖 &f" + golemCount + " mini-golems sortent de l'usine!");
            message = message.replace("{count}", String.valueOf(golemCount));
            message = ChatColor.translateAlternateColorCodes('&', message);
            player.sendMessage(message);
        }

        // ═══════════════════════════════════════════════════════════
        // CRÉER L'ANIMATION DES GOLEMS
        // ═══════════════════════════════════════════════════════════

        GolemFactoryAnimation animation = new GolemFactoryAnimation(
            plugin, cropLocation, player,
            golemCount, golemScale, patrolRadius, duration,
            mergeChance, slamRadius,
            showParticles, clientSideOnly
        );

        // Callback quand une culture est récoltée
        animation.setOnCropHit((cropLoc) -> {
            Block block = cropLoc.getBlock();
            if (isMatureCrop(block)) {
                plugin.safeBreakCrop(player, cropLoc, "golem-factory");
            }
        });

        // Callback fusion
        animation.setOnMerge((mergeCount) -> {
            if (plugin.getConfig().getBoolean("golem-factory.merge-message", true)) {
                String message = plugin.getConfig().getString("golem-factory.merge-message-text",
                    "&7&l🤖 &6&lFUSION! &fGolem géant créé!");
                message = ChatColor.translateAlternateColorCodes('&', message);
                player.sendMessage(message);
            }

            if (playSound) {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 0.8f);
            }
        });

        // Callback fin
        animation.setOnFinish((totalCrops, totalMerges) -> {
            if (plugin.getConfig().getBoolean("golem-factory.message", true) && totalCrops > 0) {
                String message = plugin.getConfig().getString("golem-factory.message-text",
                    "&7&l🤖 &fLes golems ont récolté &7{count} &fcultures!");
                message = message.replace("{count}", String.valueOf(totalCrops));

                if (totalMerges > 0) {
                    String mergeSuffix = plugin.getConfig().getString("golem-factory.merge-suffix",
                        " &7(&6{merges} fusion(s)&7)");
                    mergeSuffix = mergeSuffix.replace("{merges}", String.valueOf(totalMerges));
                    message += mergeSuffix;
                }

                message = ChatColor.translateAlternateColorCodes('&', message);
                player.sendMessage(message);
            }

            if (playSound) {
                player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_REPAIR, 0.6f, 1.3f);
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
