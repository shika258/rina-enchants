package me.rinaorc.rinaenchants.enchant;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import me.rinaorc.rinaenchants.util.AxolotlTsunamiAnimation;
import me.rivaldev.harvesterhoes.api.events.HoeEnchant;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.*;

/**
 * Enchantement Axolotl Tsunami / Tsunami d'Axolotls
 *
 * Une vague d'axolotls colorés déferle sur le champ et récolte
 * tout sur son passage! Direction aléatoire à chaque proc.
 *
 * Mécaniques:
 * - Direction aléatoire (pas basée sur le regard)
 * - Ligne d'axolotls qui surfent sur la vague
 * - Particules d'eau, bulles et écume
 * - Axolotl bleu (rare) = récolte x2 dans sa zone
 * - Play dead = effet bonus mignon
 * - Retour de vague dans l'autre sens
 *
 * Scaling (3000 niveaux):
 * - Largeur de la vague: augmente avec le niveau
 * - Distance parcourue: augmente avec le niveau
 * - Nombre d'axolotls: augmente avec le niveau
 * - Chance d'axolotl bleu: augmente avec le niveau
 * - Retour de vague: activé à partir d'un certain niveau
 *
 * Client-side pour optimisation serveur 500+ joueurs.
 */
public class AxolotlTsunamiEnchant implements HoeEnchant, Listener {

    private final RinaEnchantsPlugin plugin;
    private final Set<Material> CROPS = new HashSet<>();
    private final Set<Material> NO_AGE_CROPS = new HashSet<>();

    public AxolotlTsunamiEnchant(RinaEnchantsPlugin plugin) {
        this.plugin = plugin;
        initCrops();
        plugin.getLogger().info("§a✓ AxolotlTsunami: " + CROPS.size() + " types de cultures chargés!");
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
        return plugin.getConfig().getString("axolotl-tsunami.enchant-id", "axolotl_tsunami");
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
                plugin.getLogger().info("§e[DEBUG] AxolotlTsunami: Ignoré - cassé par une entité");
            }
            return;
        }

        if (debug) {
            plugin.getLogger().info("§e[DEBUG] AxolotlTsunami proc! Niveau: " + enchantLevel);
        }

        // ═══════════════════════════════════════════════════════════
        // RÉCUPÉRER LES PARAMÈTRES DE CONFIG
        // ═══════════════════════════════════════════════════════════

        int maxLevel = plugin.getConfig().getInt("axolotl-tsunami.max-level", 3000);
        double levelRatio = Math.min(1.0, (double) enchantLevel / maxLevel);

        // Largeur de la vague
        int baseWaveWidth = plugin.getConfig().getInt("axolotl-tsunami.base-wave-width", 5);
        int maxWaveWidth = plugin.getConfig().getInt("axolotl-tsunami.max-wave-width", 15);
        int waveWidth = (int) (baseWaveWidth + levelRatio * (maxWaveWidth - baseWaveWidth));

        // Distance parcourue
        int baseDistance = plugin.getConfig().getInt("axolotl-tsunami.base-distance", 10);
        int maxDistance = plugin.getConfig().getInt("axolotl-tsunami.max-distance", 30);
        int waveDistance = (int) (baseDistance + levelRatio * (maxDistance - baseDistance));

        // Nombre d'axolotls
        int baseAxolotlCount = plugin.getConfig().getInt("axolotl-tsunami.base-axolotl-count", 3);
        int maxAxolotlCount = plugin.getConfig().getInt("axolotl-tsunami.max-axolotl-count", 10);
        int axolotlCount = (int) (baseAxolotlCount + levelRatio * (maxAxolotlCount - baseAxolotlCount));

        // Chance d'axolotl bleu (rare)
        double baseBlueChance = plugin.getConfig().getDouble("axolotl-tsunami.base-blue-chance", 2.0);
        double maxBlueChance = plugin.getConfig().getDouble("axolotl-tsunami.max-blue-chance", 15.0);
        double blueChance = baseBlueChance + levelRatio * (maxBlueChance - baseBlueChance);

        // Chance de "play dead"
        double playDeadChance = plugin.getConfig().getDouble("axolotl-tsunami.play-dead-chance", 5.0);

        // Retour de vague (activé après 50% du niveau max)
        double returnWaveThreshold = plugin.getConfig().getDouble("axolotl-tsunami.return-wave-threshold", 0.5);
        boolean hasReturnWave = levelRatio >= returnWaveThreshold;

        boolean showParticles = plugin.getConfig().getBoolean("axolotl-tsunami.particles", true);
        boolean playSound = plugin.getConfig().getBoolean("axolotl-tsunami.sound", true);
        boolean clientSideOnly = plugin.getConfig().getBoolean("axolotl-tsunami.client-side-only", true);

        if (debug) {
            plugin.getLogger().info("§e[DEBUG] AxolotlTsunami - Largeur: " + waveWidth +
                ", Distance: " + waveDistance + ", Axolotls: " + axolotlCount +
                ", Blue%: " + String.format("%.1f", blueChance) +
                ", Retour: " + hasReturnWave);
        }

        World world = cropLocation.getWorld();
        if (world == null) return;

        // ═══════════════════════════════════════════════════════════
        // MESSAGE DE DÉBUT
        // ═══════════════════════════════════════════════════════════

        if (plugin.getConfig().getBoolean("axolotl-tsunami.start-message", true)) {
            String message = plugin.getConfig().getString("axolotl-tsunami.start-message-text",
                "&b&l🌊 &3Une vague d'axolotls déferle!");
            message = ChatColor.translateAlternateColorCodes('&', message);
            player.sendMessage(message);
        }

        // ═══════════════════════════════════════════════════════════
        // CRÉER L'ANIMATION DU TSUNAMI
        // ═══════════════════════════════════════════════════════════

        AxolotlTsunamiAnimation animation = new AxolotlTsunamiAnimation(
            plugin, cropLocation, player,
            waveWidth, waveDistance, axolotlCount,
            blueChance, playDeadChance, hasReturnWave,
            showParticles, clientSideOnly
        );

        // Callback quand une culture est récoltée
        animation.setOnCropHit((cropLoc) -> {
            Block block = cropLoc.getBlock();
            if (isMatureCrop(block)) {
                plugin.safeBreakCrop(player, cropLoc, "axolotl-tsunami");
            }
        });

        // Callback bonus bleu
        animation.setOnBlueBonus((bonusCount) -> {
            if (plugin.getConfig().getBoolean("axolotl-tsunami.blue-bonus-message", true)) {
                String message = plugin.getConfig().getString("axolotl-tsunami.blue-bonus-message-text",
                    "&b&l🌊 &9Axolotl bleu: &b+{count} bonus!");
                message = message.replace("{count}", String.valueOf(bonusCount));
                message = ChatColor.translateAlternateColorCodes('&', message);
                player.sendMessage(message);
            }
        });

        // Callback play dead
        animation.setOnPlayDead((deadCount) -> {
            // Juste pour les effets visuels, pas de message pour ne pas spam
        });

        // Callback fin
        animation.setOnFinish((totalCrops, totalBlueBonuses) -> {
            if (plugin.getConfig().getBoolean("axolotl-tsunami.message", true) && totalCrops > 0) {
                String message = plugin.getConfig().getString("axolotl-tsunami.message-text",
                    "&b&l🌊 &3Le tsunami a emporté &b{count} &3cultures!");
                message = message.replace("{count}", String.valueOf(totalCrops));

                if (totalBlueBonuses > 0) {
                    String blueSuffix = plugin.getConfig().getString("axolotl-tsunami.blue-suffix",
                        " &7(&9+{blue} bonus bleu&7)");
                    blueSuffix = blueSuffix.replace("{blue}", String.valueOf(totalBlueBonuses));
                    message += blueSuffix;
                }

                message = ChatColor.translateAlternateColorCodes('&', message);
                player.sendMessage(message);
            }

            if (playSound) {
                player.playSound(player.getLocation(), Sound.ENTITY_AXOLOTL_SPLASH, 0.8f, 1.0f);
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
