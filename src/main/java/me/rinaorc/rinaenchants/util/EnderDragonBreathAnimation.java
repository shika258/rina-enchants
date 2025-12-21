package me.rinaorc.rinaenchants.util;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Animation du Souffle du Dragon Ender
 *
 * Un mini Ender Dragon (scale 0.4) apparaît et survole le champ
 * en soufflant des nuages de dragon breath qui récoltent les cultures.
 *
 * Mécaniques:
 * - Vol en spirale autour du point de départ
 * - Souffle périodique créant des zones de récolte
 * - Les zones persistent et continuent de récolter
 * - Effet visuel spectaculaire avec particules violettes
 */
public class EnderDragonBreathAnimation {

    private final RinaEnchantsPlugin plugin;
    private final Location startLocation;
    private final Player player;
    private final World world;

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

    // Paramètres de l'animation
    private final double dragonScale;
    private final int flightDuration;
    private final double spiralRadius;
    private final double spiralSpeed;
    private final int breathInterval;
    private final double breathRadius;
    private final int breathDuration;
    private final boolean showParticles;
    private final boolean clientSideOnly;

    // État de l'animation
    private EnderDragon dragon;
    private double spiralAngle = 0;
    private double currentHeight;
    private int ticksElapsed = 0;
    private int totalCropsHarvested = 0;
    private int totalBreathClouds = 0;

    // Zones de souffle actives (location -> ticks restants)
    private final Map<Location, Integer> activeBreathZones = new HashMap<>();

    // Tracking des blocs déjà récoltés
    private final Set<String> harvestedBlocks = new HashSet<>();

    // Callbacks
    private Consumer<Location> onCropHit;
    private Consumer<Integer> onBreathCloud;
    private BiConsumer<Integer, Integer> onFinish;

    private final Random random = new Random();

    public EnderDragonBreathAnimation(RinaEnchantsPlugin plugin, Location startLocation, Player player,
                                       double dragonScale, int flightDuration, double spiralRadius,
                                       double spiralSpeed, int breathInterval, double breathRadius,
                                       int breathDuration, boolean showParticles, boolean clientSideOnly) {
        this.plugin = plugin;
        this.startLocation = startLocation.clone();
        this.player = player;
        this.world = startLocation.getWorld();

        this.dragonScale = dragonScale;
        this.flightDuration = flightDuration;
        this.spiralRadius = spiralRadius;
        this.spiralSpeed = spiralSpeed;
        this.breathInterval = breathInterval;
        this.breathRadius = breathRadius;
        this.breathDuration = breathDuration;
        this.showParticles = showParticles;
        this.clientSideOnly = clientSideOnly;

        this.currentHeight = 5.0; // Hauteur initiale
    }

    public void setOnCropHit(Consumer<Location> callback) {
        this.onCropHit = callback;
    }

    public void setOnBreathCloud(Consumer<Integer> callback) {
        this.onBreathCloud = callback;
    }

    public void setOnFinish(BiConsumer<Integer, Integer> callback) {
        this.onFinish = callback;
    }

    public void start() {
        // Spawn le dragon au-dessus du point de départ
        Location spawnLoc = startLocation.clone().add(0, currentHeight, 0);

        dragon = world.spawn(spawnLoc, EnderDragon.class, d -> {
            d.setAI(false);
            d.setSilent(true);
            d.setInvulnerable(true);
            d.setGravity(false);
            d.setPersistent(false);

            // Appliquer le scale réduit (0.4 par défaut)
            if (d.getAttribute(Attribute.SCALE) != null) {
                d.getAttribute(Attribute.SCALE).setBaseValue(dragonScale);
            }

            // Marquer l'entité pour cleanup
            plugin.markAsEnchantEntity(d);
        });

        // Rendre client-side si configuré
        if (clientSideOnly && dragon != null) {
            plugin.makeEntityClientSide(dragon, player);
        }

        // Jouer le son d'apparition
        player.playSound(startLocation, Sound.ENTITY_ENDER_DRAGON_AMBIENT, 0.6f, 1.2f);

        // Démarrer l'animation
        new BukkitRunnable() {
            @Override
            public void run() {
                if (dragon == null || dragon.isDead() || !player.isOnline()) {
                    cleanup();
                    cancel();
                    return;
                }

                ticksElapsed++;

                // Mettre à jour la position du dragon (vol en spirale)
                updateDragonPosition();

                // Vérifier si c'est le moment de souffler
                if (ticksElapsed % breathInterval == 0) {
                    createBreathCloud();
                }

                // Mettre à jour les zones de souffle actives
                updateBreathZones();

                // Particules de traînée du dragon
                if (showParticles && ticksElapsed % 2 == 0) {
                    spawnDragonTrailParticles();
                }

                // Vérifier la fin de l'animation
                if (ticksElapsed >= flightDuration) {
                    // Animation finale: le dragon s'envole
                    performFinalDive();
                    cleanup();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void updateDragonPosition() {
        // Mouvement en spirale
        spiralAngle += spiralSpeed;

        // Variation de hauteur sinusoïdale
        double heightVariation = Math.sin(ticksElapsed * 0.05) * 2.0;
        currentHeight = 5.0 + heightVariation;

        // Position sur la spirale
        double x = startLocation.getX() + Math.cos(spiralAngle) * spiralRadius;
        double z = startLocation.getZ() + Math.sin(spiralAngle) * spiralRadius;
        double y = startLocation.getY() + currentHeight;

        Location newLoc = new Location(world, x, y, z);

        // Orienter le dragon vers sa direction de vol
        Vector direction = newLoc.toVector().subtract(dragon.getLocation().toVector());
        if (direction.lengthSquared() > 0.01) {
            newLoc.setDirection(direction);
        }

        dragon.teleport(newLoc);
    }

    private void createBreathCloud() {
        // Position du souffle (sous le dragon)
        Location breathLoc = dragon.getLocation().clone().subtract(0, 3, 0);

        // S'assurer que c'est au niveau du sol
        breathLoc.setY(startLocation.getY());

        // Ajouter la zone de souffle active
        activeBreathZones.put(breathLoc.clone(), breathDuration);
        totalBreathClouds++;

        // Callback
        if (onBreathCloud != null) {
            onBreathCloud.accept(totalBreathClouds);
        }

        // Effets visuels du souffle
        if (showParticles) {
            // Particules de dragon breath
            for (int i = 0; i < 15; i++) {
                double offsetX = (random.nextDouble() - 0.5) * breathRadius * 2;
                double offsetZ = (random.nextDouble() - 0.5) * breathRadius * 2;
                Location particleLoc = breathLoc.clone().add(offsetX, 0.5, offsetZ);

                player.spawnParticle(Particle.DRAGON_BREATH, particleLoc, 2, 0.2, 0.2, 0.2, 0.01);
            }

            // Nuage violet au centre
            player.spawnParticle(Particle.ENTITY_EFFECT, breathLoc.clone().add(0, 0.5, 0),
                10, breathRadius * 0.5, 0.3, breathRadius * 0.5, 0);
        }

        // Son du souffle
        player.playSound(breathLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.5f);

        // Récolter immédiatement les cultures dans la zone
        harvestInRadius(breathLoc, breathRadius);
    }

    private void updateBreathZones() {
        Iterator<Map.Entry<Location, Integer>> iterator = activeBreathZones.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Location, Integer> entry = iterator.next();
            Location zoneLoc = entry.getKey();
            int ticksRemaining = entry.getValue() - 1;

            if (ticksRemaining <= 0) {
                iterator.remove();
                continue;
            }

            entry.setValue(ticksRemaining);

            // Récolter périodiquement dans les zones actives
            if (ticksRemaining % 10 == 0) {
                harvestInRadius(zoneLoc, breathRadius * 0.7);

                // Particules persistantes
                if (showParticles) {
                    for (int i = 0; i < 3; i++) {
                        double offsetX = (random.nextDouble() - 0.5) * breathRadius;
                        double offsetZ = (random.nextDouble() - 0.5) * breathRadius;
                        Location particleLoc = zoneLoc.clone().add(offsetX, 0.3, offsetZ);

                        player.spawnParticle(Particle.DRAGON_BREATH, particleLoc, 1, 0.1, 0.1, 0.1, 0);
                    }
                }
            }
        }
    }

    private void harvestInRadius(Location center, double radius) {
        int radiusInt = (int) Math.ceil(radius);

        for (int dx = -radiusInt; dx <= radiusInt; dx++) {
            for (int dz = -radiusInt; dz <= radiusInt; dz++) {
                // Vérifier si dans le rayon circulaire
                if (dx * dx + dz * dz > radius * radius) continue;

                // Vérifier plusieurs niveaux de hauteur
                for (int dy = -1; dy <= 2; dy++) {
                    Location cropLoc = center.clone().add(dx, dy, dz);
                    String key = cropLoc.getBlockX() + ":" + cropLoc.getBlockY() + ":" + cropLoc.getBlockZ();

                    // Éviter de récolter deux fois le même bloc
                    if (harvestedBlocks.contains(key)) continue;

                    Block block = cropLoc.getBlock();
                    if (isMatureCrop(block)) {
                        harvestedBlocks.add(key);
                        totalCropsHarvested++;

                        if (onCropHit != null) {
                            onCropHit.accept(cropLoc);
                        }

                        // Effet visuel de récolte
                        if (showParticles) {
                            player.spawnParticle(Particle.DRAGON_BREATH,
                                cropLoc.clone().add(0.5, 0.5, 0.5), 2, 0.1, 0.1, 0.1, 0.02);
                        }
                    }
                }
            }
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

    private void spawnDragonTrailParticles() {
        Location dragonLoc = dragon.getLocation();

        // Particules violettes derrière le dragon
        for (int i = 0; i < 3; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 2;
            double offsetY = (random.nextDouble() - 0.5) * 1;
            double offsetZ = (random.nextDouble() - 0.5) * 2;

            Location particleLoc = dragonLoc.clone().add(offsetX, offsetY, offsetZ);
            player.spawnParticle(Particle.DRAGON_BREATH, particleLoc, 1, 0, 0, 0, 0);
        }

        // Particules d'End autour du dragon
        player.spawnParticle(Particle.PORTAL, dragonLoc, 2, 1, 0.5, 1, 0);
    }

    private void performFinalDive() {
        if (showParticles) {
            // Explosion de particules finale
            Location dragonLoc = dragon.getLocation();

            for (int i = 0; i < 25; i++) {
                double offsetX = (random.nextDouble() - 0.5) * 4;
                double offsetY = (random.nextDouble() - 0.5) * 2;
                double offsetZ = (random.nextDouble() - 0.5) * 4;

                player.spawnParticle(Particle.DRAGON_BREATH, dragonLoc.clone().add(offsetX, offsetY, offsetZ),
                    1, 0.1, 0.1, 0.1, 0.05);
                player.spawnParticle(Particle.PORTAL, dragonLoc.clone().add(offsetX, offsetY, offsetZ),
                    1, 0, 0, 0, 0.5);
            }

            // Dernier souffle massif
            Location finalBreathLoc = startLocation.clone();
            double finalRadius = breathRadius * 1.5;

            for (int i = 0; i < 30; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double dist = random.nextDouble() * finalRadius;
                double x = Math.cos(angle) * dist;
                double z = Math.sin(angle) * dist;

                player.spawnParticle(Particle.DRAGON_BREATH,
                    finalBreathLoc.clone().add(x, 0.5, z), 1, 0.1, 0.2, 0.1, 0.02);
            }

            // Récolte finale massive
            harvestInRadius(finalBreathLoc, finalRadius);
        }

        // Son de départ
        player.playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 1.0f);
    }

    private void cleanup() {
        // Supprimer le dragon
        if (dragon != null && !dragon.isDead()) {
            dragon.remove();
        }

        // Nettoyer les zones de souffle
        activeBreathZones.clear();

        // Callback de fin
        if (onFinish != null) {
            onFinish.accept(totalCropsHarvested, totalBreathClouds);
        }
    }
}
