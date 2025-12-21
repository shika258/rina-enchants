package me.rinaorc.rinaenchants.util;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import org.bukkit.*;
import org.bukkit.entity.Bee;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Animation d'abeille optimisée pour serveurs 500+ joueurs
 * 
 * Caractéristiques:
 * - Client-side: Visible uniquement par le joueur qui proc
 * - Mouvements vraiment aléatoires et uniques par abeille
 * - Comportement naturel avec variations de vitesse
 */
public class BeeAnimation {

    private final RinaEnchantsPlugin plugin;
    private final Location startLocation;
    private final List<Location> targetCrops;
    private final boolean showParticles;
    private final int beeId;
    private final Player owner; // Le joueur qui voit l'abeille
    private final boolean clientSideOnly;
    private final Random random;
    
    private Consumer<Location> onCropReached;
    private Bee beeEntity;
    private int currentTargetIndex = 0;
    private boolean useParticleMode = false;
    private Location currentParticleLocation;

    // Paramètres uniques pour cette abeille
    private final double baseSpeed;
    private final double waveAmplitudeX;
    private final double waveAmplitudeY;
    private final double waveAmplitudeZ;
    private final double waveFrequencyX;
    private final double waveFrequencyY;
    private final double waveFrequencyZ;
    private final double phaseX;
    private final double phaseY;
    private final double phaseZ;
    
    private static final double ARRIVAL_DISTANCE = 1.0;

    public BeeAnimation(RinaEnchantsPlugin plugin, Location startLocation, List<Location> targetCrops, 
                        boolean showParticles, int beeId, Player owner, boolean clientSideOnly) {
        this.plugin = plugin;
        this.startLocation = startLocation.clone();
        this.targetCrops = targetCrops;
        this.showParticles = showParticles;
        this.beeId = beeId;
        this.owner = owner;
        this.clientSideOnly = clientSideOnly;
        
        // Seed unique pour cette abeille
        this.random = new Random(System.nanoTime() + beeId * 12345L);
        
        // ═══════════════════════════════════════════════════════════
        // PARAMÈTRES UNIQUES pour chaque abeille (vraiment aléatoires)
        // ═══════════════════════════════════════════════════════════
        this.baseSpeed = 0.2 + random.nextDouble() * 0.25; // 0.2 à 0.45
        
        // Amplitudes différentes pour chaque axe
        this.waveAmplitudeX = 0.02 + random.nextDouble() * 0.1;  // 0.02 à 0.12
        this.waveAmplitudeY = 0.03 + random.nextDouble() * 0.12; // 0.03 à 0.15
        this.waveAmplitudeZ = 0.02 + random.nextDouble() * 0.1;  // 0.02 à 0.12
        
        // Fréquences différentes pour chaque axe (crée des mouvements complexes)
        this.waveFrequencyX = 0.2 + random.nextDouble() * 0.4; // 0.2 à 0.6
        this.waveFrequencyY = 0.15 + random.nextDouble() * 0.35; // 0.15 à 0.5
        this.waveFrequencyZ = 0.25 + random.nextDouble() * 0.4; // 0.25 à 0.65
        
        // Phases de départ différentes (décale les mouvements)
        this.phaseX = random.nextDouble() * Math.PI * 2;
        this.phaseY = random.nextDouble() * Math.PI * 2;
        this.phaseZ = random.nextDouble() * Math.PI * 2;
    }

    public void setOnCropReached(Consumer<Location> callback) {
        this.onCropReached = callback;
    }

    public void start() {
        if (targetCrops.isEmpty()) return;

        World world = startLocation.getWorld();
        if (world == null) return;

        // Essayer de créer l'entité abeille
        try {
            beeEntity = (Bee) world.spawnEntity(startLocation, EntityType.BEE);
            beeEntity.setInvulnerable(true);
            beeEntity.setSilent(true);
            beeEntity.setAI(false);
            beeEntity.setGravity(false);
            beeEntity.setCollidable(false);
            beeEntity.setCanPickupItems(false);
            beeEntity.setAnger(0);
            beeEntity.setHasStung(false);
            beeEntity.setHasNectar(random.nextBoolean());
            beeEntity.setRemoveWhenFarAway(false);

            // ═══════════════════════════════════════════════════════════
            // MARQUER L'ENTITÉ pour cleanup après reboot
            // ═══════════════════════════════════════════════════════════
            plugin.markAsEnchantEntity(beeEntity);

            // ═══════════════════════════════════════════════════════════
            // CLIENT-SIDE: Cacher l'abeille à tous sauf le propriétaire
            // ═══════════════════════════════════════════════════════════
            if (clientSideOnly) {
                plugin.makeEntityClientSide(beeEntity, owner);
            }

        } catch (Exception e) {
            useParticleMode = true;
            currentParticleLocation = startLocation.clone();
        }

        // Particules de spawn (uniquement pour le propriétaire)
        if (showParticles) {
            owner.spawnParticle(Particle.END_ROD, startLocation, 5 + random.nextInt(4), 0.3, 0.3, 0.3, 0.05);
        }

        // Démarrer l'animation avec délai initial aléatoire
        new BeeMovementTask().runTaskTimer(plugin, random.nextInt(3), 1L);
    }

    private class BeeMovementTask extends BukkitRunnable {
        
        private int ticksAlive = 0;
        private static final int MAX_TICKS = 300;
        
        // Variation de vitesse dynamique
        private double speedMultiplier = 1.0;
        private int speedChangeCounter = 0;
        private int nextSpeedChange = 15 + random.nextInt(25);
        
        // Déviation temporaire aléatoire
        private double deviationX = 0;
        private double deviationZ = 0;
        private int deviationCounter = 0;

        @Override
        public void run() {
            ticksAlive++;

            if (ticksAlive > MAX_TICKS) {
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
            
            // Changement de vitesse aléatoire
            speedChangeCounter++;
            if (speedChangeCounter >= nextSpeedChange) {
                speedMultiplier = 0.6 + random.nextDouble() * 0.8; // 0.6 à 1.4
                speedChangeCounter = 0;
                nextSpeedChange = 15 + random.nextInt(25);
            }
            
            // Déviation aléatoire occasionnelle (simule l'abeille qui "explore")
            deviationCounter++;
            if (deviationCounter >= 30 + random.nextInt(40)) {
                deviationX = (random.nextDouble() - 0.5) * 0.15;
                deviationZ = (random.nextDouble() - 0.5) * 0.15;
                deviationCounter = 0;
            }

            if (useParticleMode) {
                runParticleMode();
                return;
            }

            if (beeEntity == null || beeEntity.isDead() || !beeEntity.isValid()) {
                useParticleMode = true;
                currentParticleLocation = startLocation.clone();
                return;
            }

            if (currentTargetIndex >= targetCrops.size()) {
                cleanup();
                cancel();
                return;
            }

            // Cible avec légère variation
            Location target = targetCrops.get(currentTargetIndex).clone().add(
                0.5 + (random.nextDouble() - 0.5) * 0.4,
                0.5,
                0.5 + (random.nextDouble() - 0.5) * 0.4
            );
            Location current = beeEntity.getLocation();

            Vector direction = target.toVector().subtract(current.toVector());
            double distance = direction.length();

            if (distance < ARRIVAL_DISTANCE) {
                if (onCropReached != null) {
                    onCropReached.accept(targetCrops.get(currentTargetIndex));
                }

                if (showParticles) {
                    owner.spawnParticle(Particle.WAX_ON, current, 3 + random.nextInt(3), 0.2, 0.2, 0.2, 0);
                }

                currentTargetIndex++;
                
                // Pause aléatoire à chaque récolte
                if (random.nextDouble() < 0.4) {
                    speedMultiplier = 0.3;
                }
                return;
            }

            double currentSpeed = baseSpeed * speedMultiplier;
            direction.normalize().multiply(currentSpeed);
            
            // ═══════════════════════════════════════════════════════════
            // MOUVEMENT COMPLEXE ET UNIQUE
            // Combinaison de sinusoïdes avec fréquences et phases différentes
            // ═══════════════════════════════════════════════════════════
            double t = ticksAlive;
            
            double waveX = Math.sin(t * waveFrequencyX + phaseX) * waveAmplitudeX
                         + Math.sin(t * waveFrequencyX * 1.7 + phaseX * 0.5) * waveAmplitudeX * 0.3;
            
            double waveY = Math.sin(t * waveFrequencyY + phaseY) * waveAmplitudeY
                         + Math.cos(t * waveFrequencyY * 0.8 + phaseY * 1.3) * waveAmplitudeY * 0.4;
            
            double waveZ = Math.cos(t * waveFrequencyZ + phaseZ) * waveAmplitudeZ
                         + Math.sin(t * waveFrequencyZ * 1.4 + phaseZ * 0.7) * waveAmplitudeZ * 0.35;
            
            // Ajouter la déviation temporaire
            waveX += deviationX;
            waveZ += deviationZ;
            
            // Micro-perturbations aléatoires
            if (random.nextDouble() < 0.15) {
                waveX += (random.nextDouble() - 0.5) * 0.08;
                waveY += (random.nextDouble() - 0.5) * 0.05;
                waveZ += (random.nextDouble() - 0.5) * 0.08;
            }
            
            direction.add(new Vector(waveX, waveY, waveZ));

            Location newLocation = current.add(direction);
            
            Vector lookDir = target.toVector().subtract(newLocation.toVector());
            if (lookDir.lengthSquared() > 0) {
                newLocation.setDirection(lookDir);
            }
            
            beeEntity.teleport(newLocation);

            // Particules de traînée (uniquement pour le propriétaire)
            if (showParticles && ticksAlive % (2 + random.nextInt(2)) == 0) {
                owner.spawnParticle(Particle.END_ROD, current, 1, 0, 0, 0, 0);
                if (random.nextDouble() < 0.6) {
                    owner.spawnParticle(Particle.WAX_OFF, current, 1 + random.nextInt(2), 0.1, 0.1, 0.1, 0);
                }
            }
        }

        private void runParticleMode() {
            if (currentTargetIndex >= targetCrops.size()) {
                cancel();
                return;
            }

            World world = currentParticleLocation.getWorld();
            if (world == null) {
                cancel();
                return;
            }

            Location target = targetCrops.get(currentTargetIndex).clone().add(0.5, 0.5, 0.5);
            Vector direction = target.toVector().subtract(currentParticleLocation.toVector());
            double distance = direction.length();

            if (distance < ARRIVAL_DISTANCE) {
                if (onCropReached != null) {
                    onCropReached.accept(targetCrops.get(currentTargetIndex));
                }
                currentTargetIndex++;
                return;
            }

            double currentSpeed = baseSpeed * speedMultiplier;
            direction.normalize().multiply(currentSpeed);
            
            double t = ticksAlive;
            direction.add(new Vector(
                Math.sin(t * waveFrequencyX + phaseX) * waveAmplitudeX,
                Math.sin(t * waveFrequencyY + phaseY) * waveAmplitudeY,
                Math.cos(t * waveFrequencyZ + phaseZ) * waveAmplitudeZ
            ));
            
            currentParticleLocation.add(direction);

            if (ticksAlive % 2 == 0) {
                owner.spawnParticle(Particle.WAX_ON, currentParticleLocation, 2, 0.1, 0.1, 0.1, 0);
                owner.spawnParticle(Particle.END_ROD, currentParticleLocation, 1, 0, 0, 0, 0);
            }
        }

        private void cleanup() {
            if (beeEntity != null && !beeEntity.isDead()) {
                Location loc = beeEntity.getLocation();

                if (showParticles && owner.isOnline()) {
                    owner.spawnParticle(Particle.POOF, loc, 5 + random.nextInt(4), 0.3, 0.3, 0.3, 0.05);
                    owner.spawnParticle(Particle.WAX_ON, loc, 3 + random.nextInt(3), 0.2, 0.2, 0.2, 0);
                }

                beeEntity.remove();
            }
        }
    }
}
