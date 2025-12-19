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

    private Consumer<Integer> onFinish;
    private Consumer<Location> onCropHit;

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
            owner.spawnParticle(Particle.SNOWFLAKE, centerLocation.clone().add(0, 2, 0), 100, 3, 2, 3, 0.1);
            owner.spawnParticle(Particle.END_ROD, centerLocation.clone().add(0, 3, 0), 30, 2, 1, 2, 0.05);
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
                // PARTICULES AMBIANTES
                // ═══════════════════════════════════════════════════════════
                if (showParticles && ticksElapsed % 5 == 0) {
                    for (int i = 0; i < 10; i++) {
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

                // Sons ambiants
                if (ticksElapsed % 20 == 0) {
                    owner.playSound(currentCenter, Sound.BLOCK_POWDER_SNOW_STEP, 0.5f, 1.0f);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
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

        // Tracker pour la récolte et l'explosion potentielle
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (snowball.isDead() || !snowball.isValid() || ticks > 100) {
                    // La boule a touché le sol ou expiré
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

                    if (!snowball.isDead()) {
                        snowball.remove();
                    }
                    cancel();
                    return;
                }

                ticks++;

                // Particules de traînée
                if (showParticles && ticks % 2 == 0) {
                    owner.spawnParticle(Particle.SNOWFLAKE, snowball.getLocation(), 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void triggerExplosion(Location center) {
        World world = center.getWorld();
        if (world == null) return;

        // Effets visuels
        if (showParticles) {
            owner.spawnParticle(Particle.EXPLOSION, center, 1, 0, 0, 0, 0);
            owner.spawnParticle(Particle.SNOWFLAKE, center, 50, 2, 2, 2, 0.1);
            owner.spawnParticle(Particle.END_ROD, center, 20, 1, 1, 1, 0.05);
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
                            owner.spawnParticle(Particle.HAPPY_VILLAGER, cropLoc.clone().add(0.5, 0.5, 0.5), 5, 0.3, 0.3, 0.3, 0);
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

        // Effets visuels
        if (showParticles) {
            owner.spawnParticle(Particle.TOTEM_OF_UNDYING, spawnLoc, 30, 1, 1, 1, 0.1);
        }

        // Son de spawn
        owner.playSound(spawnLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        owner.playSound(spawnLoc, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);

        // Tracker pour le ramassage
        new BukkitRunnable() {
            int ticks = 0;
            boolean rewardGiven = false;

            @Override
            public void run() {
                // Timeout après 30 secondes - pas de récompense
                if (ticks > 600) {
                    if (!droppedGift.isDead()) {
                        droppedGift.remove();
                    }
                    cancel();
                    return;
                }

                // Vérifier si le joueur est proche (ramassage) - SEUL moyen d'obtenir la récompense
                if (!droppedGift.isDead() && droppedGift.isValid() && owner.isOnline()) {
                    try {
                        if (owner.getLocation().distance(droppedGift.getLocation()) < 1.5) {
                            droppedGift.remove();
                            if (!rewardGiven) {
                                rewardGiven = true;
                                executeGiftReward();
                            }
                            cancel();
                            return;
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Mondes différents - ignorer
                    }
                }

                // Si le cadeau a disparu sans être ramassé par le joueur (hopper, despawn, etc.) - pas de récompense
                if (droppedGift.isDead() || !droppedGift.isValid()) {
                    cancel();
                    return;
                }

                ticks++;

                // Particules autour du cadeau
                if (showParticles && ticks % 5 == 0) {
                    Location giftLoc = droppedGift.getLocation();
                    owner.spawnParticle(Particle.END_ROD, giftLoc, 3, 0.3, 0.3, 0.3, 0.02);
                    owner.spawnParticle(Particle.SNOWFLAKE, giftLoc.clone().add(0, 0.5, 0), 2, 0.2, 0.2, 0.2, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
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
            owner.spawnParticle(Particle.TOTEM_OF_UNDYING, owner.getLocation().add(0, 1, 0), 50, 0.5, 1, 0.5, 0.2);
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
        // Nettoyer le cadeau actif s'il existe encore
        if (activeGiftItem != null && !activeGiftItem.isDead()) {
            activeGiftItem.remove();
        }

        // Effets de fin
        if (showParticles) {
            owner.spawnParticle(Particle.SNOWFLAKE, owner.getLocation().add(0, 2, 0), 50, 3, 2, 3, 0.05);
        }

        owner.playSound(owner.getLocation(), Sound.BLOCK_POWDER_SNOW_BREAK, 1.0f, 0.8f);

        if (onFinish != null) {
            onFinish.accept(totalCropsHarvested);
        }
    }
}
