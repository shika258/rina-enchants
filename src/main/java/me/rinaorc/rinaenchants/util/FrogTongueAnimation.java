package me.rinaorc.rinaenchants.util;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Frog;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Animation de Grenouilles avec Langue Élastique
 *
 * FROG TONGUE LASH / COUP DE LANGUE
 *
 * 3 types de grenouilles avec comportements différents:
 * - 🟠 WARM (Orange) = Langue courte mais rapide
 * - ⚪ COLD (Blanc) = Langue longue mais lente
 * - 🟢 TEMPERATE (Vert) = Langue moyenne + rebond (2 cultures)
 *
 * Caractéristiques:
 * - Utilise l'animation native de la grenouille (tongue attack)
 * - Son "slurp" satisfaisant
 * - Système de combo si 3 grenouilles visent la même zone
 * - Les grenouilles restent sur place pour farmer les cultures
 * - Client-side pour optimisation serveur 500+ joueurs
 */
public class FrogTongueAnimation {

    private final RinaEnchantsPlugin plugin;
    private final Location centerLocation;
    private final Player owner;
    private final int baseTongueRange;
    private final int baseFireRate;
    private final int duration;
    private final int frogCount;
    private final boolean showParticles;
    private final boolean clientSideOnly;
    private final Random random;

    // Callbacks
    private Consumer<Location> onCropHit;
    private Consumer<Integer> onComboTongue;
    private BiConsumer<Integer, Integer> onFinish;

    // État
    private final List<FrogInstance> frogs = new ArrayList<>();
    private int totalCropsHarvested = 0;
    private int totalCombos = 0;

    // Tracking des zones ciblées pour les combos
    private final Map<String, Integer> zoneLickCount = new HashMap<>();
    private static final int COMBO_ZONE_RADIUS = 2;

    // Set des cultures
    private static final Set<Material> CROPS = new HashSet<>();
    private static final Set<Material> NO_AGE_CROPS = new HashSet<>();

    static {
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
     * Types de grenouilles avec leurs caractéristiques
     */
    public enum FrogType {
        // Orange: Langue courte mais rapide
        WARM(Frog.Variant.WARM, 0.5, 0.6, false,
             Color.fromRGB(255, 140, 0), "Orange"),

        // Blanc: Langue longue mais lente
        COLD(Frog.Variant.COLD, 1.4, 1.8, false,
             Color.fromRGB(220, 220, 255), "Blanc"),

        // Vert: Langue moyenne + rebond (2 cultures)
        TEMPERATE(Frog.Variant.TEMPERATE, 1.0, 1.2, true,
                  Color.fromRGB(80, 200, 80), "Vert");

        public final Frog.Variant variant;
        public final double rangeMultiplier;    // Multiplicateur de portée
        public final double fireRateMultiplier; // Multiplicateur de cadence (plus haut = plus lent)
        public final boolean hasBounce;         // Peut attraper 2 cultures
        public final Color tongueColor;         // Couleur de la langue
        public final String displayName;

        FrogType(Frog.Variant variant, double rangeMultiplier, double fireRateMultiplier,
                 boolean hasBounce, Color tongueColor, String displayName) {
            this.variant = variant;
            this.rangeMultiplier = rangeMultiplier;
            this.fireRateMultiplier = fireRateMultiplier;
            this.hasBounce = hasBounce;
            this.tongueColor = tongueColor;
            this.displayName = displayName;
        }
    }

    /**
     * Instance d'une grenouille individuelle
     */
    private class FrogInstance {
        Frog entity;
        FrogType type;
        int ticksSinceLastLick = 0;
        int personalFireRate;
        int personalRange;
        Location spawnLocation; // Position fixe où la grenouille reste

        FrogInstance(FrogType type, Location spawnLocation) {
            this.type = type;
            this.spawnLocation = spawnLocation.clone();
            this.personalFireRate = (int) (baseFireRate * type.fireRateMultiplier);
            this.personalRange = (int) (baseTongueRange * type.rangeMultiplier);
        }
    }

    public FrogTongueAnimation(RinaEnchantsPlugin plugin, Location centerLocation, Player owner,
                               int baseTongueRange, int baseFireRate, int duration, int frogCount,
                               boolean showParticles, boolean clientSideOnly) {
        this.plugin = plugin;
        this.centerLocation = centerLocation.clone();
        this.owner = owner;
        this.baseTongueRange = baseTongueRange;
        this.baseFireRate = baseFireRate;
        this.duration = duration;
        this.frogCount = frogCount;
        this.showParticles = showParticles;
        this.clientSideOnly = clientSideOnly;
        this.random = new Random();
    }

    public void setOnCropHit(Consumer<Location> callback) {
        this.onCropHit = callback;
    }

    public void setOnComboTongue(Consumer<Integer> callback) {
        this.onComboTongue = callback;
    }

    public void setOnFinish(BiConsumer<Integer, Integer> callback) {
        this.onFinish = callback;
    }

    public void start() {
        World world = centerLocation.getWorld();
        if (world == null) return;

        // Spawner les grenouilles avec différents types
        FrogType[] types = FrogType.values();
        double angleStep = (2 * Math.PI) / frogCount;

        for (int i = 0; i < frogCount; i++) {
            FrogType type = types[i % types.length];
            double startAngle = i * angleStep;

            try {
                // Position fixe autour de la culture initiale
                Location spawnLoc = centerLocation.clone();
                spawnLoc.add(
                    Math.cos(startAngle) * 2,
                    0,
                    Math.sin(startAngle) * 2
                );
                // S'assurer que la grenouille est sur un bloc solide
                spawnLoc = findSafeSpawnLocation(spawnLoc, world);

                Frog frog = (Frog) world.spawnEntity(spawnLoc, EntityType.FROG);
                frog.setInvulnerable(true);
                frog.setSilent(false);
                frog.setAI(true); // AI activée pour permettre l'animation de la langue
                frog.setGravity(true);
                frog.setCollidable(false);
                frog.setRemoveWhenFarAway(false);
                frog.setVariant(type.variant);

                // Marquer l'entité pour cleanup
                plugin.markAsEnchantEntity(frog);

                // Client-side
                if (clientSideOnly) {
                    plugin.makeEntityClientSide(frog, owner);
                }

                FrogInstance instance = new FrogInstance(type, spawnLoc);
                instance.entity = frog;
                frogs.add(instance);

                // Effet de spawn minimal (juste un peu de particules vertes)
                if (showParticles) {
                    owner.spawnParticle(Particle.HAPPY_VILLAGER, spawnLoc, 5, 0.3, 0.3, 0.3, 0);
                }

            } catch (Exception e) {
                plugin.getLogger().warning("§e[RinaEnchants] Impossible de spawn la grenouille: " + e.getMessage());
            }
        }

        if (frogs.isEmpty()) {
            return;
        }

        // Son de spawn global
        owner.playSound(centerLocation, Sound.ENTITY_FROG_AMBIENT, 1.0f, 1.0f);

        // Démarrer l'animation principale
        new FrogBehaviorTask().runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Trouve une position de spawn sûre (sur un bloc solide)
     */
    private Location findSafeSpawnLocation(Location loc, World world) {
        Location result = loc.clone();
        // Chercher le sol le plus proche
        for (int y = 0; y <= 3; y++) {
            Block below = world.getBlockAt(result.getBlockX(), result.getBlockY() - y - 1, result.getBlockZ());
            Block at = world.getBlockAt(result.getBlockX(), result.getBlockY() - y, result.getBlockZ());
            if (below.getType().isSolid() && !at.getType().isSolid()) {
                result.setY(result.getBlockY() - y);
                break;
            }
        }
        return result;
    }

    /**
     * Task principale qui gère le comportement des grenouilles
     */
    private class FrogBehaviorTask extends BukkitRunnable {

        private int ticksAlive = 0;
        private final Set<String> harvestedBlocks = new HashSet<>();

        @Override
        public void run() {
            ticksAlive++;

            // Fin de la durée
            if (ticksAlive > duration) {
                cleanup();
                cancel();
                return;
            }

            // Vérifier si le propriétaire est toujours en ligne
            if (!owner.isOnline()) {
                cleanup();
                cancel();
                return;
            }

            World world = centerLocation.getWorld();
            if (world == null) {
                cleanup();
                cancel();
                return;
            }

            // Nettoyer les grenouilles mortes
            frogs.removeIf(f -> f.entity == null || f.entity.isDead() || !f.entity.isValid());

            if (frogs.isEmpty()) {
                cancel();
                finishAnimation();
                return;
            }

            // Mettre à jour chaque grenouille
            for (FrogInstance frog : frogs) {
                updateFrog(frog, world);
            }

            // Son ambient occasionnel
            if (ticksAlive % 60 == 0 && !frogs.isEmpty()) {
                FrogInstance randomFrog = frogs.get(random.nextInt(frogs.size()));
                owner.playSound(randomFrog.entity.getLocation(), Sound.ENTITY_FROG_AMBIENT, 0.4f, 0.9f + random.nextFloat() * 0.2f);
            }
        }

        private void updateFrog(FrogInstance frog, World world) {
            frog.ticksSinceLastLick++;

            Location frogPos = frog.entity.getLocation();

            // ═══════════════════════════════════════════════════════════
            // TIR DE LANGUE vers les cultures (animation native)
            // ═══════════════════════════════════════════════════════════

            if (frog.ticksSinceLastLick >= frog.personalFireRate) {
                Location target = findTargetCrop(frogPos, world, frog.personalRange, harvestedBlocks);

                if (target != null) {
                    frog.ticksSinceLastLick = 0;

                    // Orienter la grenouille vers la cible
                    Vector lookDir = target.toVector().subtract(frogPos.toVector());
                    if (lookDir.lengthSquared() > 0) {
                        Location newLoc = frog.entity.getLocation();
                        newLoc.setDirection(lookDir);
                        frog.entity.teleport(newLoc);
                    }

                    // Tirer la langue avec l'animation native!
                    fireTongue(frog, frogPos, target, world);
                }
            }
        }

        /**
         * Trouve une culture à cibler dans le rayon
         */
        private Location findTargetCrop(Location center, World world, int range, Set<String> alreadyHarvested) {
            int cx = center.getBlockX();
            int cy = center.getBlockY();
            int cz = center.getBlockZ();

            List<Location> potentialTargets = new ArrayList<>();

            for (int x = -range; x <= range; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -range; z <= range; z++) {
                        // Vérifier la distance (cercle, pas carré)
                        if (x * x + z * z > range * range) continue;

                        Location blockLoc = new Location(world, cx + x, cy + y, cz + z);
                        String key = blockLoc.getBlockX() + ":" + blockLoc.getBlockY() + ":" + blockLoc.getBlockZ();

                        if (alreadyHarvested.contains(key)) continue;

                        Block block = blockLoc.getBlock();
                        if (isMatureCrop(block)) {
                            potentialTargets.add(blockLoc);
                        }
                    }
                }
            }

            if (potentialTargets.isEmpty()) return null;

            // Retourner une cible aléatoire
            return potentialTargets.get(random.nextInt(potentialTargets.size()));
        }

        /**
         * Tire une langue vers une cible
         */
        private void fireTongue(FrogInstance frog, Location from, Location to, World world) {
            String key = to.getBlockX() + ":" + to.getBlockY() + ":" + to.getBlockZ();
            harvestedBlocks.add(key);
            totalCropsHarvested++;

            // Vérifier le combo (zone)
            checkAndTriggerCombo(to);

            // Orienter la grenouille vers la cible
            Location targetLoc = to.clone().add(0.5, 0.5, 0.5);
            Vector lookDir = targetLoc.toVector().subtract(from.toVector());
            if (lookDir.lengthSquared() > 0) {
                Location newLoc = frog.entity.getLocation();
                newLoc.setDirection(lookDir);
                frog.entity.teleport(newLoc);
            }

            // Son de langue
            owner.playSound(from, Sound.ENTITY_FROG_EAT, 0.6f, 1.0f);

            // Récolter après un petit délai
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (onCropHit != null) {
                    onCropHit.accept(to);
                }
            }, 5L);

            // Si cette grenouille a le rebond, chercher une 2ème cible
            if (frog.type.hasBounce) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Location bounceTarget = findTargetCrop(to, world, 3, harvestedBlocks);
                    if (bounceTarget != null) {
                        String bounceKey = bounceTarget.getBlockX() + ":" + bounceTarget.getBlockY() + ":" + bounceTarget.getBlockZ();
                        harvestedBlocks.add(bounceKey);
                        totalCropsHarvested++;

                        // Orienter la grenouille vers la nouvelle cible
                        Location bounceTargetLoc = bounceTarget.clone().add(0.5, 0.5, 0.5);
                        Vector bounceLookDir = bounceTargetLoc.toVector().subtract(from.toVector());
                        if (bounceLookDir.lengthSquared() > 0) {
                            Location newLoc = frog.entity.getLocation();
                            newLoc.setDirection(bounceLookDir);
                            frog.entity.teleport(newLoc);
                        }

                        // Récolter le rebond
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (onCropHit != null) {
                                onCropHit.accept(bounceTarget);
                            }
                        }, 5L);

                        owner.playSound(bounceTargetLoc, Sound.ENTITY_FROG_EAT, 0.6f, 1.5f);
                    }
                }, 12L);
            }
        }

        /**
         * Vérifie si on déclenche un combo (3 grenouilles même zone)
         */
        private void checkAndTriggerCombo(Location target) {
            // Créer une clé de zone (arrondie)
            int zoneX = target.getBlockX() / COMBO_ZONE_RADIUS;
            int zoneY = target.getBlockY() / COMBO_ZONE_RADIUS;
            int zoneZ = target.getBlockZ() / COMBO_ZONE_RADIUS;
            String zoneKey = zoneX + ":" + zoneY + ":" + zoneZ;

            int count = zoneLickCount.getOrDefault(zoneKey, 0) + 1;
            zoneLickCount.put(zoneKey, count);

            // Combo déclenché à 3 licks dans la même zone!
            if (count == 3) {
                totalCombos++;

                if (onComboTongue != null) {
                    onComboTongue.accept(totalCombos);
                }

                // Effet de combo spectaculaire
                if (showParticles) {
                    Location comboLoc = target.clone().add(0.5, 0.5, 0.5);
                    owner.spawnParticle(Particle.TOTEM_OF_UNDYING, comboLoc, 15, 0.5, 0.5, 0.5, 0.3);
                }

                owner.playSound(target, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
                owner.playSound(target, Sound.ENTITY_FROG_EAT, 1.0f, 0.8f);

                // Reset la zone pour permettre un nouveau combo
                zoneLickCount.put(zoneKey, 0);
            }
        }

        private void cleanup() {
            for (FrogInstance frog : frogs) {
                if (frog.entity != null && !frog.entity.isDead()) {
                    Location loc = frog.entity.getLocation();

                    // Effet de disparition simple
                    if (showParticles && owner.isOnline()) {
                        owner.spawnParticle(Particle.POOF, loc, 5, 0.3, 0.3, 0.3, 0.05);
                    }

                    frog.entity.remove();
                }
            }
            frogs.clear();

            finishAnimation();
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

    private void finishAnimation() {
        if (owner.isOnline()) {
            owner.playSound(owner.getLocation(), Sound.ENTITY_FROG_LONG_JUMP, 0.6f, 1.2f);
        }

        if (onFinish != null) {
            onFinish.accept(totalCropsHarvested, totalCombos);
        }
    }
}
