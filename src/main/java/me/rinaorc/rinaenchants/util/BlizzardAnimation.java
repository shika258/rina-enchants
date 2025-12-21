package me.rinaorc.rinaenchants.util;

import me.rinaorc.rinaenchants.RinaEnchantsPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.net.URL;
import java.util.*;
import java.util.function.Consumer;

/**
 * Animation du Blizzard Éternel
 *
 * Fonctionnalités:
 * - Boules de neige tombant du ciel de façon chaotique
 * - Micro-chance d'explosion de récolte (3x3)
 * - Système de cadeaux avec tête custom
 *
 * OPTIMISATION: Toutes les snowballs sont trackées dans une seule task
 * au lieu de créer une task par snowball (réduction massive des tasks)
 */
public class BlizzardAnimation {

    private final RinaEnchantsPlugin plugin;
    private final Player owner;
    private final Location centerLocation;
    private final int durationTicks;
    private final int snowballsPerSecond;
    private final int blizzardRadius;
    private final int spawnHeight;
    private final double explosionChance;
    private final int explosionRadius;
    private final boolean giftEnabled;
    private final double giftChance;
    private final String giftTexture;
    private final List<String> giftCommands;
    private final String giftPickupMessage;
    private final boolean showParticles;
    private final boolean clientSideOnly;
    private final Set<Material> CROPS;
    private final Set<Material> NO_AGE_CROPS;

    private final Random random = new Random();
    private int totalCropsHarvested = 0;
    private int ticksElapsed = 0;
    private boolean giftSpawned = false;
    private Item activeGiftItem = null;
    private int giftTicksElapsed = 0;
    private boolean giftRewardGiven = false;

    // OPTIMISATION: Liste des snowballs actives (évite de créer une task par snowball)
    private final List<SnowballTracker> activeSnowballs = new ArrayList<>();

    private Consumer<Integer> onFinish;
    private Consumer<Location> onCropHit;

    /**
     * Classe interne pour tracker les snowballs sans créer de task séparée
     */
    private static class SnowballTracker {
        final Snowball snowball;
        int ticksAlive = 0;
        boolean processed = false;

        SnowballTracker(Snowball snowball) {
            this.snowball = snowball;
        }
    }

    public BlizzardAnimation(RinaEnchantsPlugin plugin, Player owner, Location centerLocation,
                              int durationTicks, int snowballsPerSecond, int blizzardRadius,
                              int spawnHeight, double explosionChance, int explosionRadius,
                              boolean giftEnabled, double giftChance, String giftTexture,
                              List<String> giftCommands, String giftPickupMessage,
                              boolean showParticles, boolean clientSideOnly,
                              Set<Material> crops, Set<Material> noAgeCrops) {
        this.plugin = plugin;
        this.owner = owner;
        this.centerLocation = centerLocation;
        this.durationTicks = durationTicks;
        this.snowballsPerSecond = snowballsPerSecond;
        this.blizzardRadius = blizzardRadius;
        this.spawnHeight = spawnHeight;
        this.explosionChance = explosionChance;
        this.explosionRadius = explosionRadius;
        this.giftEnabled = giftEnabled;
        this.giftChance = giftChance;
        this.giftTexture = giftTexture;
        this.giftCommands = giftCommands;
        this.giftPickupMessage = giftPickupMessage;
        this.showParticles = showParticles;
        this.clientSideOnly = clientSideOnly;
        this.CROPS = crops;
        this.NO_AGE_CROPS = noAgeCrops;
    }

    public void setOnFinish(Consumer<Integer> onFinish) {
        this.onFinish = onFinish;
    }

    public void setOnCropHit(Consumer<Location> onCropHit) {
        this.onCropHit = onCropHit;
    }

    public void start() {
        World world = centerLocation.getWorld();
        if (world == null) return;

        // Particules de spawn initiales
        if (showParticles) {
            owner.spawnParticle(Particle.SNOWFLAKE, centerLocation.clone().add(0, 2, 0), 50, 3, 2, 3, 0.1);
            owner.spawnParticle(Particle.END_ROD, centerLocation.clone().add(0, 3, 0), 15, 2, 1, 2, 0.05);
        }

        // Son de début
        owner.playSound(centerLocation, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.5f);
        owner.playSound(centerLocation, Sound.BLOCK_POWDER_SNOW_STEP, 1.0f, 0.8f);

        // Calculer le nombre de snowballs par tick (20 ticks = 1 seconde)
        double snowballsPerTick = snowballsPerSecond / 20.0;

        new BukkitRunnable() {
            private double snowballAccumulator = 0;

            @Override
            public void run() {
                if (!owner.isOnline() || ticksElapsed >= durationTicks) {
                    cleanup();
                    cancel();
                    return;
                }

                ticksElapsed++;

                // Position actuelle du joueur comme centre du blizzard
                Location currentCenter = owner.getLocation();

                // ═══════════════════════════════════════════════════════════
                // SPAWN DES BOULES DE NEIGE
                // ═══════════════════════════════════════════════════════════
                snowballAccumulator += snowballsPerTick;
                while (snowballAccumulator >= 1.0) {
                    snowballAccumulator -= 1.0;
                    spawnSnowball(currentCenter);
                }

                // ═══════════════════════════════════════════════════════════
                // MISE À JOUR DE TOUTES LES SNOWBALLS (OPTIMISATION)
                // Au lieu d'avoir une task par snowball, on les update toutes ici
                // ═══════════════════════════════════════════════════════════
                updateAllSnowballs();

                // ═══════════════════════════════════════════════════════════
                // PARTICULES AMBIANTES
                // ═══════════════════════════════════════════════════════════
                if (showParticles && ticksElapsed % 8 == 0) {
                    for (int i = 0; i < 5; i++) {
                        double offsetX = (random.nextDouble() - 0.5) * blizzardRadius * 2;
                        double offsetY = random.nextDouble() * spawnHeight;
                        double offsetZ = (random.nextDouble() - 0.5) * blizzardRadius * 2;
                        Location particleLoc = currentCenter.clone().add(offsetX, offsetY, offsetZ);
                        owner.spawnParticle(Particle.SNOWFLAKE, particleLoc, 1, 0, 0, 0, 0);
                    }
                }

                // ═══════════════════════════════════════════════════════════
                // CHANCE DE CADEAU
                // ═══════════════════════════════════════════════════════════
                if (giftEnabled && !giftSpawned && random.nextDouble() * 100 < giftChance) {
                    spawnGift(currentCenter);
                    giftSpawned = true;
                }

                // ═══════════════════════════════════════════════════════════
                // MISE À JOUR DU CADEAU (OPTIMISATION - plus de task séparée)
                // ═══════════════════════════════════════════════════════════
                if (activeGiftItem != null && !giftRewardGiven) {
                    updateGift();
                }

                // Sons ambiants
                if (ticksElapsed % 20 == 0) {
                    owner.playSound(currentCenter, Sound.BLOCK_POWDER_SNOW_STEP, 0.5f, 1.0f);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * OPTIMISATION: Met à jour toutes les snowballs dans une seule boucle
     * au lieu de créer une task par snowball
     */
    private void updateAllSnowballs() {
        Iterator<SnowballTracker> iterator = activeSnowballs.iterator();

        while (iterator.hasNext()) {
            SnowballTracker tracker = iterator.next();

            // Skip si déjà traité
            if (tracker.processed) {
                iterator.remove();
                continue;
            }

            tracker.ticksAlive++;

            Snowball snowball = tracker.snowball;

            // Vérifier si la snowball est morte ou a expiré
            if (snowball.isDead() || !snowball.isValid() || tracker.ticksAlive > 100) {
                // La boule a touché le sol ou expiré
                processSnowballImpact(snowball);
                tracker.processed = true;

                if (!snowball.isDead()) {
                    snowball.remove();
                }
                iterator.remove();
                continue;
            }

            // Particules de traînée
            if (showParticles && tracker.ticksAlive % 2 == 0) {
                owner.spawnParticle(Particle.SNOWFLAKE, snowball.getLocation(), 1, 0, 0, 0, 0);
            }
        }
    }

    /**
     * Traite l'impact d'une snowball (récolte + explosion potentielle)
     */
    private void processSnowballImpact(Snowball snowball) {
        Location impactLoc = snowball.getLocation();

        // ═══════════════════════════════════════════════════════════
        // RÉCOLTE DE LA CULTURE TOUCHÉE PAR LA BOULE DE NEIGE
        // ═══════════════════════════════════════════════════════════
        Block hitBlock = impactLoc.getBlock();
        // Vérifier aussi les blocs adjacents (la boule peut atterrir à côté)
        Block[] blocksToCheck = {
            hitBlock,
            hitBlock.getRelative(0, -1, 0),
            hitBlock.getRelative(0, 1, 0),
            hitBlock.getRelative(1, 0, 0),
            hitBlock.getRelative(-1, 0, 0),
            hitBlock.getRelative(0, 0, 1),
            hitBlock.getRelative(0, 0, -1)
        };

        for (Block block : blocksToCheck) {
            if (isMatureCrop(block)) {
                Location cropLoc = block.getLocation();
                plugin.markEntityBreakingLocation(cropLoc);

                if (onCropHit != null) {
                    onCropHit.accept(cropLoc);
                }
                totalCropsHarvested++;

                // Particules de récolte
                if (showParticles) {
                    owner.spawnParticle(Particle.SNOWFLAKE, cropLoc.clone().add(0.5, 0.5, 0.5), 5, 0.2, 0.2, 0.2, 0.02);
                }
                break; // Une seule culture par boule de neige
            }
        }

        // ═══════════════════════════════════════════════════════════
        // CHANCE D'EXPLOSION (récolte en zone 3x3)
        // ═══════════════════════════════════════════════════════════
        if (random.nextDouble() * 100 < explosionChance) {
            triggerExplosion(impactLoc);
        }
    }

    private void spawnSnowball(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        // Position aléatoire au-dessus du joueur
        double offsetX = (random.nextDouble() - 0.5) * blizzardRadius * 2;
        double offsetZ = (random.nextDouble() - 0.5) * blizzardRadius * 2;
        Location spawnLoc = center.clone().add(offsetX, spawnHeight, offsetZ);

        // Créer la boule de neige
        Snowball snowball = world.spawn(spawnLoc, Snowball.class);
        snowball.setShooter(owner);

        // Vélocité vers le bas avec un peu de chaos
        double velX = (random.nextDouble() - 0.5) * 0.3;
        double velY = -0.5 - random.nextDouble() * 0.3;
        double velZ = (random.nextDouble() - 0.5) * 0.3;
        snowball.setVelocity(new Vector(velX, velY, velZ));

        // Marquer comme entité du plugin
        plugin.markAsEnchantEntity(snowball);

        // Client-side
        if (clientSideOnly) {
            plugin.makeEntityClientSide(snowball, owner);
        }

        // OPTIMISATION: Ajouter à la liste au lieu de créer une task
        activeSnowballs.add(new SnowballTracker(snowball));
    }

    private void triggerExplosion(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        // Effets visuels
        if (showParticles) {
            owner.spawnParticle(Particle.EXPLOSION, center, 1, 0, 0, 0, 0);
            owner.spawnParticle(Particle.SNOWFLAKE, center, 25, 2, 2, 2, 0.1);
            owner.spawnParticle(Particle.END_ROD, center, 10, 1, 1, 1, 0.05);
        }

        // Son d'explosion
        owner.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
        owner.playSound(center, Sound.BLOCK_GLASS_BREAK, 0.3f, 1.2f);

        // Récolter les cultures dans le rayon
        int centerX = center.getBlockX();
        int centerY = center.getBlockY();
        int centerZ = center.getBlockZ();

        for (int x = -explosionRadius; x <= explosionRadius; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -explosionRadius; z <= explosionRadius; z++) {
                    Block block = world.getBlockAt(centerX + x, centerY + y, centerZ + z);
                    if (isMatureCrop(block)) {
                        Location cropLoc = block.getLocation();
                        plugin.markEntityBreakingLocation(cropLoc);

                        if (onCropHit != null) {
                            onCropHit.accept(cropLoc);
                        }
                        totalCropsHarvested++;

                        // Particules de récolte
                        if (showParticles) {
                            owner.spawnParticle(Particle.HAPPY_VILLAGER, cropLoc.clone().add(0.5, 0.5, 0.5), 2, 0.3, 0.3, 0.3, 0);
                        }
                    }
                }
            }
        }
    }

    private void spawnGift(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        // Position aléatoire au-dessus du joueur
        double offsetX = (random.nextDouble() - 0.5) * blizzardRadius;
        double offsetZ = (random.nextDouble() - 0.5) * blizzardRadius;
        Location spawnLoc = center.clone().add(offsetX, spawnHeight + 5, offsetZ);

        // Créer l'item cadeau avec une tête custom
        ItemStack giftItem = createGiftHead();
        Item droppedGift = world.dropItem(spawnLoc, giftItem);

        // Configuration du cadeau - empêcher le ramassage automatique
        // Le tracker gère la détection de proximité et les récompenses
        droppedGift.setPickupDelay(Integer.MAX_VALUE);
        droppedGift.setGlowing(true);
        droppedGift.setCustomName(ChatColor.translateAlternateColorCodes('&', "&6&l🎁 Cadeau de Noël"));
        droppedGift.setCustomNameVisible(true);

        // Vélocité lente vers le bas
        droppedGift.setVelocity(new Vector(0, -0.1, 0));

        // Marquer comme entité du plugin
        plugin.markAsEnchantEntity(droppedGift);

        activeGiftItem = droppedGift;
        giftTicksElapsed = 0;

        // Effets visuels
        if (showParticles) {
            owner.spawnParticle(Particle.TOTEM_OF_UNDYING, spawnLoc, 15, 1, 1, 1, 0.1);
        }

        // Son de spawn
        owner.playSound(spawnLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        owner.playSound(spawnLoc, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);

        // OPTIMISATION: Plus de task séparée - géré dans updateGift()
    }

    /**
     * OPTIMISATION: Met à jour le cadeau dans la task principale
     * au lieu d'avoir une task séparée
     */
    private void updateGift() {
        if (activeGiftItem == null) return;

        giftTicksElapsed++;

        // Timeout après 30 secondes - pas de récompense
        if (giftTicksElapsed > 600) {
            if (!activeGiftItem.isDead()) {
                activeGiftItem.remove();
            }
            activeGiftItem = null;
            return;
        }

        // Vérifier si le joueur est proche (ramassage) - SEUL moyen d'obtenir la récompense
        if (!activeGiftItem.isDead() && activeGiftItem.isValid() && owner.isOnline()) {
            try {
                if (owner.getLocation().distance(activeGiftItem.getLocation()) < 1.5) {
                    activeGiftItem.remove();
                    if (!giftRewardGiven) {
                        giftRewardGiven = true;
                        executeGiftReward();
                    }
                    activeGiftItem = null;
                    return;
                }
            } catch (IllegalArgumentException ignored) {
                // Mondes différents - ignorer
            }
        }

        // Si le cadeau a disparu sans être ramassé par le joueur (hopper, despawn, etc.) - pas de récompense
        if (activeGiftItem.isDead() || !activeGiftItem.isValid()) {
            activeGiftItem = null;
            return;
        }

        // Particules autour du cadeau
        if (showParticles && giftTicksElapsed % 8 == 0) {
            Location giftLoc = activeGiftItem.getLocation();
            owner.spawnParticle(Particle.END_ROD, giftLoc, 2, 0.3, 0.3, 0.3, 0.02);
            owner.spawnParticle(Particle.SNOWFLAKE, giftLoc.clone().add(0, 0.5, 0), 1, 0.2, 0.2, 0.2, 0);
        }
    }

    private ItemStack createGiftHead() {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        if (meta != null) {
            try {
                // Créer un profil avec la texture
                PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
                PlayerTextures textures = profile.getTextures();

                // Décoder l'URL de la texture
                String decodedTexture = new String(Base64.getDecoder().decode(giftTexture));
                // Extraire l'URL de la texture
                String urlString = decodedTexture.split("\"url\":\"")[1].split("\"")[0];
                URL url = new URL(urlString);
                textures.setSkin(url);
                profile.setTextures(textures);

                meta.setOwnerProfile(profile);
                meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Cadeau de Noël");
                meta.setLore(Arrays.asList(
                    ChatColor.GRAY + "Un cadeau magique du Blizzard!",
                    ChatColor.AQUA + "Ramassez-le pour une récompense!"
                ));
            } catch (Exception e) {
                plugin.getLogger().warning("§e[Blizzard] Erreur lors de la création de la tête: " + e.getMessage());
            }

            skull.setItemMeta(meta);
        }

        return skull;
    }

    private void executeGiftReward() {
        // Message de ramassage
        if (giftPickupMessage != null && !giftPickupMessage.isEmpty()) {
            owner.sendMessage(ChatColor.translateAlternateColorCodes('&', giftPickupMessage));
        }

        // Effets
        owner.playSound(owner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        owner.playSound(owner.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);

        if (showParticles) {
            owner.spawnParticle(Particle.TOTEM_OF_UNDYING, owner.getLocation().add(0, 1, 0), 25, 0.5, 1, 0.5, 0.2);
        }

        // Exécuter les commandes
        for (String command : giftCommands) {
            String processedCommand = command.replace("%player%", owner.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand);
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

    private void cleanup() {
        // Nettoyer toutes les snowballs actives
        for (SnowballTracker tracker : activeSnowballs) {
            if (tracker.snowball != null && !tracker.snowball.isDead()) {
                tracker.snowball.remove();
            }
        }
        activeSnowballs.clear();

        // Nettoyer le cadeau actif s'il existe encore
        if (activeGiftItem != null && !activeGiftItem.isDead()) {
            activeGiftItem.remove();
        }

        // Effets de fin
        if (showParticles && owner.isOnline()) {
            owner.spawnParticle(Particle.SNOWFLAKE, owner.getLocation().add(0, 2, 0), 25, 3, 2, 3, 0.05);
        }

        if (owner.isOnline()) {
            owner.playSound(owner.getLocation(), Sound.BLOCK_POWDER_SNOW_BREAK, 1.0f, 0.8f);
        }

        if (onFinish != null) {
            onFinish.accept(totalCropsHarvested);
        }
    }
}
