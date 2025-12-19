package me.rinaorc.rinaenchants.util;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Allay;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.function.Consumer;

/**
 * Animation de l'Allay qui tire des lasers vers les cultures
 * 
 * Caractéristiques:
 * - Suit le joueur en flottant
 * - Tire des lasers de particules vers les cultures
 * - Client-side pour optimisation serveur 500+ joueurs
 * - Effet visuel spectaculaire avec particules
 */
public class AllayAnimation {

    private final RinaEnchantsPlugin plugin;
    private final Location startLocation;
    private final Player owner;
    private final int radius;
    private final int fireRate;
    private final int duration;
    private final boolean showParticles;
    private final boolean clientSideOnly;
    private final Set<Material> crops;
    private final Set<Material> noAgeCrops;
    private final Random random;
    
    private Consumer<Location> onCropHit;
    private Consumer<Integer> onFinish;
    
    private Allay allayEntity;
    private int totalCropsHarvested = 0;

    // Paramètres de mouvement
    private static final double FOLLOW_SPEED = 0.15;
    private static final double HOVER_HEIGHT = 2.0;
    private static final double HOVER_OFFSET = 1.5;

    public AllayAnimation(RinaEnchantsPlugin plugin, Location startLocation, Player owner,
                         int radius, int fireRate, int duration, boolean showParticles,
                         boolean clientSideOnly, Set<Material> crops, Set<Material> noAgeCrops) {
        this.plugin = plugin;
        this.startLocation = startLocation.clone();
        this.owner = owner;
        this.radius = radius;
        this.fireRate = fireRate;
        this.duration = duration;
        this.showParticles = showParticles;
        this.clientSideOnly = clientSideOnly;
        this.crops = crops;
        this.noAgeCrops = noAgeCrops;
        this.random = new Random();
    }

    public void setOnCropHit(Consumer<Location> callback) {
        this.onCropHit = callback;
    }
    
    public void setOnFinish(Consumer<Integer> callback) {
        this.onFinish = callback;
    }

    public void start() {
        World world = startLocation.getWorld();
        if (world == null) return;

        try {
            allayEntity = (Allay) world.spawnEntity(startLocation, EntityType.ALLAY);
            allayEntity.setInvulnerable(true);
            allayEntity.setSilent(false);
            allayEntity.setAI(false);
            allayEntity.setGravity(false);
            allayEntity.setCollidable(false);
            allayEntity.setCanPickupItems(false);
            allayEntity.setRemoveWhenFarAway(false);

            // ═══════════════════════════════════════════════════════════
            // MARQUER L'ENTITÉ pour cleanup après reboot
            // ═══════════════════════════════════════════════════════════
            plugin.markAsEnchantEntity(allayEntity);

            // Client-side
            if (clientSideOnly) {
                plugin.makeEntityClientSide(allayEntity, owner);
            }

        } catch (Exception e) {
            plugin.getLogger().warning("§e[RinaEnchants] Impossible de spawn l'Allay: " + e.getMessage());
            return;
        }

        // Particules de spawn
        if (showParticles) {
            owner.spawnParticle(Particle.END_ROD, startLocation, 30, 0.5, 0.5, 0.5, 0.1);
            owner.spawnParticle(Particle.ENCHANT, startLocation, 50, 0.5, 0.5, 0.5, 0.5);
        }

        // Démarrer l'animation principale
        new AllayBehaviorTask().runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * Task principale qui gère le comportement de l'Allay
     */
    private class AllayBehaviorTask extends BukkitRunnable {
        
        private int ticksAlive = 0;
        private int ticksSinceLastShot = 0;
        private final Set<String> harvestedBlocks = new HashSet<>();
        
        // Paramètres de mouvement fluide
        private double wavePhase = random.nextDouble() * Math.PI * 2;
        private double orbitAngle = random.nextDouble() * Math.PI * 2;

        @Override
        public void run() {
            ticksAlive++;
            ticksSinceLastShot++;

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

            // Vérifier si l'allay existe
            if (allayEntity == null || allayEntity.isDead() || !allayEntity.isValid()) {
                cancel();
                if (onFinish != null) {
                    onFinish.accept(totalCropsHarvested);
                }
                return;
            }

            World world = owner.getWorld();
            if (world == null) {
                cleanup();
                cancel();
                return;
            }

            // ═══════════════════════════════════════════════════════════
            // MOUVEMENT: Suivre le joueur en flottant
            // ═══════════════════════════════════════════════════════════
            
            // Position cible: légèrement au-dessus et derrière le joueur
            wavePhase += 0.1;
            orbitAngle += 0.03;
            
            Location targetPos = owner.getLocation().clone();
            targetPos.add(
                Math.sin(orbitAngle) * HOVER_OFFSET,
                HOVER_HEIGHT + Math.sin(wavePhase) * 0.3,
                Math.cos(orbitAngle) * HOVER_OFFSET
            );
            
            // Mouvement fluide vers la cible
            Location currentPos = allayEntity.getLocation();
            Vector toTarget = targetPos.toVector().subtract(currentPos.toVector());
            
            if (toTarget.length() > 0.1) {
                toTarget.normalize().multiply(FOLLOW_SPEED);
                currentPos.add(toTarget);
            }
            
            // Orienter l'allay vers le joueur
            Vector lookDir = owner.getLocation().toVector().subtract(currentPos.toVector());
            if (lookDir.lengthSquared() > 0) {
                currentPos.setDirection(lookDir);
            }
            
            allayEntity.teleport(currentPos);
            
            // ═══════════════════════════════════════════════════════════
            // TIR DE LASER vers les cultures
            // ═══════════════════════════════════════════════════════════
            
            if (ticksSinceLastShot >= fireRate) {
                // Trouver une culture à cibler
                Location target = findTargetCrop(currentPos, world, harvestedBlocks);
                
                if (target != null) {
                    ticksSinceLastShot = 0;
                    String key = target.getBlockX() + ":" + target.getBlockY() + ":" + target.getBlockZ();
                    harvestedBlocks.add(key);
                    totalCropsHarvested++;
                    
                    // Tirer le laser!
                    fireLaser(currentPos, target);
                    
                    // Callback
                    if (onCropHit != null) {
                        onCropHit.accept(target);
                    }
                }
            }
            
            // ═══════════════════════════════════════════════════════════
            // EFFETS VISUELS de l'Allay (aura magique)
            // ═══════════════════════════════════════════════════════════
            
            if (showParticles && ticksAlive % 3 == 0) {
                // Aura magique autour de l'allay
                owner.spawnParticle(Particle.ENCHANT, currentPos, 3, 0.3, 0.3, 0.3, 0.2);
                
                // Particules de notes de musique occasionnelles
                if (ticksAlive % 15 == 0) {
                    owner.spawnParticle(Particle.NOTE, currentPos.clone().add(0, 0.5, 0), 1, 0.3, 0.3, 0.3, 0);
                }
            }
            
            // Son ambient occasionnel
            if (ticksAlive % 40 == 0) {
                owner.playSound(currentPos, Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 0.3f, 1.0f + random.nextFloat() * 0.4f);
            }
        }
        
        /**
         * Trouve une culture à cibler dans le rayon
         */
        private Location findTargetCrop(Location center, World world, Set<String> alreadyHarvested) {
            int cx = center.getBlockX();
            int cy = center.getBlockY();
            int cz = center.getBlockZ();
            
            // Liste des cultures potentielles
            List<Location> potentialTargets = new ArrayList<>();
            
            for (int x = -radius; x <= radius; x++) {
                for (int y = -3; y <= 1; y++) {
                    for (int z = -radius; z <= radius; z++) {
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
         * Tire un laser de particules vers une cible
         */
        private void fireLaser(Location from, Location to) {
            if (!showParticles) {
                // Même sans particules, jouer le son
                owner.playSound(from, Sound.ENTITY_ALLAY_ITEM_THROWN, 0.5f, 1.5f);
                return;
            }
            
            Location laserStart = from.clone().add(0, -0.5, 0);
            Location laserEnd = to.clone().add(0.5, 0.5, 0.5);
            
            Vector direction = laserEnd.toVector().subtract(laserStart.toVector());
            double distance = direction.length();
            direction.normalize();
            
            // ═══════════════════════════════════════════════════════════
            // ANIMATION DU LASER
            // Particules le long de la trajectoire
            // ═══════════════════════════════════════════════════════════
            
            // Couleur du laser (cyan/turquoise comme l'allay)
            Particle.DustOptions dustOptions = new Particle.DustOptions(
                Color.fromRGB(85, 255, 255), // Cyan clair
                1.0f
            );
            
            Particle.DustOptions dustOptions2 = new Particle.DustOptions(
                Color.fromRGB(170, 255, 255), // Blanc-cyan
                0.7f
            );
            
            // Tracer le laser
            for (double d = 0; d < distance; d += 0.3) {
                Location point = laserStart.clone().add(direction.clone().multiply(d));
                
                // Particule principale (cyan)
                owner.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dustOptions);
                
                // Particule secondaire (plus claire, effet de brillance)
                if (d % 0.6 < 0.3) {
                    owner.spawnParticle(Particle.DUST, point, 1, 0.05, 0.05, 0.05, 0, dustOptions2);
                }
            }
            
            // Effet d'impact à la cible
            owner.spawnParticle(Particle.END_ROD, laserEnd, 8, 0.2, 0.2, 0.2, 0.05);
            owner.spawnParticle(Particle.HAPPY_VILLAGER, laserEnd, 5, 0.3, 0.3, 0.3, 0);
            
            // Effet de charge à l'origine
            owner.spawnParticle(Particle.ELECTRIC_SPARK, laserStart, 3, 0.1, 0.1, 0.1, 0.02);
            
            // Sons
            owner.playSound(laserStart, Sound.ENTITY_ALLAY_ITEM_THROWN, 0.4f, 1.5f);
            owner.playSound(laserEnd, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, 1.8f);
        }
        
        /**
         * Vérifie si un bloc est une culture mature
         */
        private boolean isMatureCrop(Block block) {
            Material type = block.getType();
            
            if (!crops.contains(type)) return false;
            if (noAgeCrops.contains(type)) return true;

            if (block.getBlockData() instanceof Ageable ageable) {
                return ageable.getAge() >= ageable.getMaximumAge();
            }

            return true;
        }
        
        private void cleanup() {
            if (allayEntity != null && !allayEntity.isDead()) {
                Location loc = allayEntity.getLocation();
                
                if (showParticles && owner.isOnline()) {
                    // Effet de disparition magique
                    owner.spawnParticle(Particle.END_ROD, loc, 30, 0.5, 0.5, 0.5, 0.1);
                    owner.spawnParticle(Particle.ENCHANT, loc, 50, 0.5, 0.5, 0.5, 0.3);
                    owner.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 20, 0.3, 0.3, 0.3, 0.2);
                }
                
                if (owner.isOnline()) {
                    owner.playSound(loc, Sound.ENTITY_ALLAY_DEATH, 0.5f, 1.2f);
                }
                
                allayEntity.remove();
            }
            
            if (onFinish != null) {
                onFinish.accept(totalCropsHarvested);
            }
        }
    }
}
