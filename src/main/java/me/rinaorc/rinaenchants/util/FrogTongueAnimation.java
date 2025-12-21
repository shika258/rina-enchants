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
 * - Animation élastique de la langue (particules)
 * - Son "slurp" satisfaisant
 * - Système de combo si 3 grenouilles visent la même zone
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
        double orbitAngle;
        double hopPhase;

        FrogInstance(FrogType type, double startAngle) {
            this.type = type;
            this.orbitAngle = startAngle;
            this.hopPhase = random.nextDouble() * Math.PI * 2;
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
                // Position initiale autour du joueur
                Location spawnLoc = owner.getLocation().clone();
                spawnLoc.add(
                    Math.cos(startAngle) * 2,
                    0,
                    Math.sin(startAngle) * 2
                );

                Frog frog = (Frog) world.spawnEntity(spawnLoc, EntityType.FROG);
                frog.setInvulnerable(true);
                frog.setSilent(false);
                frog.setAI(false);
                frog.setGravity(false);
                frog.setCollidable(false);
                frog.setRemoveWhenFarAway(false);
                frog.setVariant(type.variant);

                // Marquer l'entité pour cleanup
                plugin.markAsEnchantEntity(frog);

                // Client-side
                if (clientSideOnly) {
                    plugin.makeEntityClientSide(frog, owner);
                }

                FrogInstance instance = new FrogInstance(type, startAngle);
                instance.entity = frog;
                frogs.add(instance);

                // Effet de spawn
                if (showParticles) {
                    owner.spawnParticle(Particle.HAPPY_VILLAGER, spawnLoc, 10, 0.3, 0.3, 0.3, 0);
                    Particle.DustOptions dust = new Particle.DustOptions(type.tongueColor, 1.5f);
                    owner.spawnParticle(Particle.DUST, spawnLoc.clone().add(0, 0.5, 0), 15, 0.3, 0.3, 0.3, 0, dust);
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
     * Task principale qui gère le comportement des grenouilles
     */
    private class FrogBehaviorTask extends BukkitRunnable {

        private int ticksAlive = 0;
        private final Set<String> harvestedBlocks = new HashSet<>();
        private static final double ORBIT_RADIUS = 2.5;
        private static final double HOP_HEIGHT = 0.3;

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

            World world = owner.getWorld();
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
            frog.orbitAngle += 0.04;
            frog.hopPhase += 0.15;

            // ═══════════════════════════════════════════════════════════
            // MOUVEMENT: Orbiter autour du joueur avec saut
            // ═══════════════════════════════════════════════════════════

            Location playerLoc = owner.getLocation();
            double hopOffset = Math.max(0, Math.sin(frog.hopPhase) * HOP_HEIGHT);

            Location targetPos = playerLoc.clone().add(
                Math.cos(frog.orbitAngle) * ORBIT_RADIUS,
                hopOffset,
                Math.sin(frog.orbitAngle) * ORBIT_RADIUS
            );

            // Orienter la grenouille vers le centre
            Vector lookDir = playerLoc.toVector().subtract(targetPos.toVector());
            if (lookDir.lengthSquared() > 0) {
                targetPos.setDirection(lookDir);
            }

            frog.entity.teleport(targetPos);

            // ═══════════════════════════════════════════════════════════
            // TIR DE LANGUE vers les cultures
            // ═══════════════════════════════════════════════════════════

            if (frog.ticksSinceLastLick >= frog.personalFireRate) {
                Location target = findTargetCrop(targetPos, world, frog.personalRange, harvestedBlocks);

                if (target != null) {
                    frog.ticksSinceLastLick = 0;

                    // Tirer la langue!
                    fireTongue(frog, targetPos, target, world);
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
         * Tire une langue élastique vers une cible
         */
        private void fireTongue(FrogInstance frog, Location from, Location to, World world) {
            String key = to.getBlockX() + ":" + to.getBlockY() + ":" + to.getBlockZ();
            harvestedBlocks.add(key);
            totalCropsHarvested++;

            // Vérifier le combo (zone)
            checkAndTriggerCombo(to);

            // Animation de la langue
            new TongueAnimationTask(frog, from.clone(), to.clone(), world).runTaskTimer(plugin, 0L, 1L);

            // Callback
            if (onCropHit != null) {
                onCropHit.accept(to);
            }

            // Si cette grenouille a le rebond, chercher une 2ème cible
            if (frog.type.hasBounce) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Location bounceTarget = findTargetCrop(to, world, 3, harvestedBlocks);
                    if (bounceTarget != null) {
                        String bounceKey = bounceTarget.getBlockX() + ":" + bounceTarget.getBlockY() + ":" + bounceTarget.getBlockZ();
                        harvestedBlocks.add(bounceKey);
                        totalCropsHarvested++;

                        // Animation de rebond
                        new TongueAnimationTask(frog, to.clone().add(0.5, 0.5, 0.5), bounceTarget.clone(), world)
                            .runTaskTimer(plugin, 0L, 1L);

                        if (onCropHit != null) {
                            onCropHit.accept(bounceTarget);
                        }

                        // Effet spécial de rebond
                        if (showParticles) {
                            owner.spawnParticle(Particle.ENCHANT, to.clone().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0.5);
                        }
                        owner.playSound(to, Sound.ENTITY_SLIME_SQUISH, 0.6f, 1.5f);
                    }
                }, 6L);
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
                    owner.spawnParticle(Particle.TOTEM_OF_UNDYING, comboLoc, 30, 0.5, 0.5, 0.5, 0.3);
                    owner.spawnParticle(Particle.FLASH, comboLoc, 1, 0, 0, 0, 0);
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

                    if (showParticles && owner.isOnline()) {
                        Particle.DustOptions dust = new Particle.DustOptions(frog.type.tongueColor, 1.5f);
                        owner.spawnParticle(Particle.DUST, loc, 20, 0.4, 0.4, 0.4, 0, dust);
                        owner.spawnParticle(Particle.POOF, loc, 15, 0.3, 0.3, 0.3, 0.1);
                    }

                    frog.entity.remove();
                }
            }
            frogs.clear();

            finishAnimation();
        }
    }

    /**
     * Animation de la langue élastique
     */
    private class TongueAnimationTask extends BukkitRunnable {

        private final FrogInstance frog;
        private final Location from;
        private final Location to;
        private final World world;
        private int tick = 0;
        private static final int EXTEND_TICKS = 4;
        private static final int RETRACT_TICKS = 3;
        private static final int TOTAL_TICKS = EXTEND_TICKS + RETRACT_TICKS;

        TongueAnimationTask(FrogInstance frog, Location from, Location to, World world) {
            this.frog = frog;
            this.from = from.clone().add(0, 0.3, 0); // Bouche de la grenouille
            this.to = to.clone().add(0.5, 0.5, 0.5); // Centre du bloc
            this.world = world;
        }

        @Override
        public void run() {
            tick++;

            if (tick > TOTAL_TICKS) {
                cancel();
                return;
            }

            if (!owner.isOnline()) {
                cancel();
                return;
            }

            // Calculer la progression de la langue (effet élastique)
            double progress;
            if (tick <= EXTEND_TICKS) {
                // Extension: accélère puis décélère (ease-out)
                double t = (double) tick / EXTEND_TICKS;
                progress = 1 - Math.pow(1 - t, 3); // Cubic ease-out
            } else {
                // Rétraction: rapide (ease-in)
                double t = (double) (tick - EXTEND_TICKS) / RETRACT_TICKS;
                progress = 1 - Math.pow(t, 2); // Quadratic ease-in (inversé)
            }

            // Position actuelle de l'extrémité de la langue
            Vector direction = to.toVector().subtract(from.toVector());
            double distance = direction.length() * progress;
            direction.normalize();

            // ═══════════════════════════════════════════════════════════
            // DESSINER LA LANGUE AVEC DES PARTICULES
            // ═══════════════════════════════════════════════════════════

            if (showParticles) {
                Particle.DustOptions tongueDust = new Particle.DustOptions(frog.type.tongueColor, 1.2f);
                Particle.DustOptions tipDust = new Particle.DustOptions(
                    Color.fromRGB(255, 100, 100), // Bout de langue rose/rouge
                    1.5f
                );

                // Tracer la langue
                for (double d = 0; d < distance; d += 0.2) {
                    Location point = from.clone().add(direction.clone().multiply(d));

                    // Légère ondulation pour effet élastique
                    double wave = Math.sin(d * 3 + tick * 0.5) * 0.05 * (1 - progress);
                    point.add(0, wave, 0);

                    owner.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, tongueDust);
                }

                // Bout de la langue (plus gros)
                Location tipLoc = from.clone().add(direction.clone().multiply(distance));
                owner.spawnParticle(Particle.DUST, tipLoc, 2, 0.05, 0.05, 0.05, 0, tipDust);

                // À l'extension maximale, effet d'impact
                if (tick == EXTEND_TICKS) {
                    owner.spawnParticle(Particle.HAPPY_VILLAGER, to, 8, 0.2, 0.2, 0.2, 0);
                    owner.spawnParticle(Particle.ITEM_SLIME, to, 5, 0.2, 0.2, 0.2, 0.1);
                }
            }

            // Son de lick à l'extension maximale
            if (tick == EXTEND_TICKS) {
                owner.playSound(to, Sound.ENTITY_FROG_EAT, 0.8f, 1.0f + random.nextFloat() * 0.3f);
            }

            // Son de rétraction (slurp)
            if (tick == EXTEND_TICKS + 1) {
                owner.playSound(from, Sound.ENTITY_SLIME_SQUISH_SMALL, 0.5f, 1.4f);
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

    private void finishAnimation() {
        if (owner.isOnline()) {
            owner.playSound(owner.getLocation(), Sound.ENTITY_FROG_LONG_JUMP, 0.6f, 1.2f);
        }

        if (onFinish != null) {
            onFinish.accept(totalCropsHarvested, totalCombos);
        }
    }
}
