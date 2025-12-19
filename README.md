# RinaEnchants

Plugin d'enchantements personnalisés pour RivalHarvesterHoes.

## Enchantements disponibles

### 🐝 Bee Collector (Apiculteur)
Des abeilles magiques apparaissent et récoltent les cultures autour de vous.
- Plus le niveau est élevé, plus d'abeilles et un rayon plus grand
- Mouvements des abeilles aléatoires et naturels

### 🐼 Panda Roll (Roulade de Panda)  
Un panda fait une roulade en ligne droite et casse les cultures sur son passage.
- **Système de Combo**: Le panda peut enchaîner plusieurs roulades!
- Chance de combo = niveau de l'enchant (1% à niveau 1, 100% à niveau 100)
- Le panda change de direction (90°) à chaque combo

## Installation

1. Compilez le plugin avec Maven: `mvn clean package`
2. Copiez `target/RinaEnchants-1.0.0.jar` dans votre dossier `plugins/`
3. Ajoutez les enchantements dans `enchants.yml` de RivalHarvesterHoes
4. Redémarrez le serveur

## Commandes

- `/rinaenchants reload` - Recharge la configuration
- `/rinaenchants info` - Affiche les informations du plugin
- `/rinaenchants help` - Affiche l'aide

## Améliorations par rapport à BeeEnchant

- ✅ Commande `/rinaenchants reload` ajoutée
- ✅ Fix du bug après `/hoe reload` (ré-enregistrement automatique)
- ✅ L'enchant ne s'active que si le JOUEUR casse une culture (pas les abeilles/pandas)
- ✅ Mouvements des abeilles plus aléatoires et naturels
- ✅ Nouvel enchantement Panda Roll avec système de combo

## Configuration RivalHarvesterHoes

Ajoutez ceci dans `enchants.yml`:

```yaml
bee_collector:
  enabled: true
  name: "&e🐝 Apiculteur"
  description:
    - "&7Des abeilles magiques récoltent"
    - "&7les cultures autour de vous!"
  max-level: 10
  chance-per-level: 3.0
  base-chance: 5.0
  cost-per-level: 5000
  base-cost: 10000

panda_roll:
  enabled: true
  name: "&d🐼 Roulade de Panda"
  description:
    - "&7Un panda fait une roulade et"
    - "&7casse les cultures sur son passage!"
    - "&6Système de combo intégré!"
  max-level: 100
  chance-per-level: 0.5
  base-chance: 2.0
  cost-per-level: 2500
  base-cost: 5000
```

## Auteur

Rinaorc Studio
