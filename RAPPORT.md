# Rapport de Projet DAAR - Algorithmique d'Essaims
## Simovies Robot Simulator

**Auteurs:** Marsso, Mougamadoubougary
**Cours:** DAAR - Algorithmique d'essaims
**Date:** Janvier 2026

---

## 1. Introduction

Ce projet implémente des stratégies de combat pour robots dans le simulateur Simovies. Deux équipes s'affrontent : l'équipe A (attaquante) et l'équipe B (défensive). Chaque équipe dispose de 3 robots principaux (Main Bots) et 2 robots secondaires (Secondary Bots).

### 1.1 Architecture du Simulateur

- **Brain** : Classe de base que chaque robot doit étendre
- **Parameters** : Configuration des équipes, positions initiales, caractéristiques des robots
- **Capteurs disponibles** :
  - `detectRadar()` : Détection circulaire 360° autour du robot
  - `detectFront()` : Détection frontale d'obstacles
- **Actions disponibles** :
  - `move()` : Avancer dans la direction actuelle
  - `stepTurn(Direction)` : Tourner à gauche ou droite
  - `fire(direction)` : Tirer dans une direction
  - `broadcast(message)` / `fetchAllMessages()` : Communication inter-robots

---

## 2. Stratégies Implémentées

### 2.1 Équipe A - "KD Runners" (Stratégie Offensive)

#### TeamAMainBotMarssoMougamadoubougary - Tank Chasseur

**Rôle** : Robot principal offensif qui patrouille, détecte et engage les ennemis.

**États** :
| État | Description |
|------|-------------|
| PATROL | Patrouille vers l'EST en attendant un signal |
| RUSHING | Se dirige vers le scout après réception du signal "ENEMY_FOUND" |
| ENGAGING | Combat actif : tire et se rapproche de l'ennemi |
| DODGE_TURN1 à DODGE_MOVE3 | Esquive en U pour contourner les obstacles |

**Comportement clé** :
```
1. Patrouille vers l'EST
2. Si signal "ENEMY_FOUND" reçu → RUSHING vers le scout
3. Si ennemi détecté → ENGAGING (tir + approche)
4. Si obstacle → Esquive en U (N/S → EST → retour)
5. Tire automatiquement dès qu'un ennemi est visible (scanAndShoot)
```

**Esquive en U** :
```
Position initiale    Étape 1: Turn N/S    Étape 2: Move    Étape 3: Turn E
       X             ↑ ou ↓                    |                  →
                                               |
                                               X

Étape 4: Move E      Étape 5: Turn S/N    Étape 6: Move back
       →→→X          ↓ ou ↑                    |
                                               |
                                               X (retour ligne)
```

#### TeamASecondaryBotMarssoMougamadoubougary - Scout Éclaireur

**Rôle** : Robot rapide qui explore, localise les ennemis et signale leur position.

**États** :
| État | Description |
|------|-------------|
| ADVANCING | Avance vers l'EST à la recherche d'ennemis |
| EVASIVE_TURN | Tourne vers une direction aléatoire (N/S/E/W) |
| EVASIVE_MOVE | Se déplace dans la direction choisie |

**Comportement clé** :
```
1. Avance vers l'EST
2. Si ennemi détecté → broadcast("ENEMY_FOUND") + tire
3. Passe en mode EVASIVE : mouvement aléatoire dans 4 directions
4. Continue à signaler et tirer pendant l'évasion
```

**Caractéristiques** :
- Vitesse : 3 (vs 1 pour Main Bot)
- Santé : 100 (vs 300 pour Main Bot)
- Priorité : Survie et communication

---

### 2.2 Équipe B - "Fantom Danger" (Stratégie Défensive)

#### TeamBMainBotMarssoMougamadoubougary - Défenseur Groupé

**Rôle** : Robot défensif qui avance en groupe et se coordonne avec ses coéquipiers.

**États** :
| État | Description |
|------|-------------|
| ADVANCING | Avance vers l'OUEST en formation |
| HOLDING | Position de tir fixe quand ennemi détecté |
| RETREAT | Recul groupé quand un coéquipier est bloqué |
| SHIFT_VERTICAL | Décalage vertical coordonné pour contourner un obstacle |

**Comportement clé** :
```
1. Avance vers l'OUEST en groupe
2. Si ennemi détecté → HOLDING (tire sans bouger)
3. Si bloqué → broadcast("BLOCKED") + RETREAT
4. Si message "BLOCKED" reçu → Tous les Main B reculent ensemble
5. Après 80 steps de recul → Reprend l'avancée
6. Si atteint un bord horizontal → Fait automatiquement demi-tour
7. Si bloqué horizontalement pendant 50 steps → SHIFT_VERTICAL coordonné
```

**Gestion des bords** :
- Si le robot atteint le bord gauche (X < 100), il fait demi-tour vers l'EST
- Si le robot atteint le bord droit (X > 2900), il fait demi-tour vers l'OUEST

**Décalage vertical coordonné** :
- Si un robot ne peut ni avancer vers l'OUEST ni reculer vers l'EST pendant 50 steps
- Il envoie le signal "SHIFT" à ses coéquipiers
- Tous les Main B montent ou descendent ensemble pendant 150 steps
- Direction choisie : NORTH si Y > 1000 (milieu de l'arène), sinon SOUTH

**Coordination de groupe** :
```java
// Quand un robot est bloqué
if (isBlocked()) {
    broadcast("BLOCKED");  // Signal aux coéquipiers
    currentState = RETREAT;
}

// Tous les robots écoutent
if (msg.equals("BLOCKED")) {
    currentState = RETREAT;  // Recul synchronisé
}

// Décalage vertical coordonné
if (blockedCounter > BLOCKED_THRESHOLD) {
    broadcast("SHIFT");  // Signal de décalage vertical
    currentState = SHIFT_VERTICAL;
}

if (msg.equals("SHIFT")) {
    currentState = SHIFT_VERTICAL;  // Décalage synchronisé
}
```

#### TeamBSecondaryBotMarssoMougamadoubougary - Kamikaze

**Rôle** : Robot suicide qui fonce sur les ennemis sans jamais s'arrêter.

**États** :
| État | Description |
|------|-------------|
| SEARCHING | Cherche des ennemis en avançant vers l'OUEST |
| SUICIDE | Fonce sur l'ennemi le plus proche en tirant |

**Comportement clé** :
```
1. Avance vers l'OUEST (ignore les obstacles)
2. Si ennemi détecté → MODE SUICIDE
3. En SUICIDE : tire continuellement + fonce vers la cible
4. Ne s'arrête jamais, ne recule jamais
```

---

## 3. Paramètres de Configuration

### 3.1 Positions Initiales

**Équipe A (côté gauche - X faible)** :
| Robot | Position X | Position Y | Direction |
|-------|------------|------------|-----------|
| Main 1 | 200 | 800 | EAST |
| Main 2 | 200 | 1000 | EAST |
| Main 3 | 200 | 1200 | EAST |
| Secondary 1 | 500 | 800 | EAST |
| Secondary 2 | 500 | 1200 | EAST |

**Équipe B (côté droit, en bas - X élevé, Y élevé)** :
| Robot | Position X | Position Y | Direction |
|-------|------------|------------|-----------|
| Main 1 | 2800 | 1400 | WEST |
| Main 2 | 2800 | 1500 | WEST |
| Main 3 | 2800 | 1600 | WEST |
| Secondary 1 | 2500 | 1450 | WEST |
| Secondary 2 | 2500 | 1550 | WEST |

*Note : Dans le système de coordonnées écran, Y=0 est en haut, Y élevé est en bas.*

### 3.2 Caractéristiques des Robots

| Paramètre | Main Bot | Secondary Bot |
|-----------|----------|---------------|
| Rayon | 50 mm | 50 mm |
| Vitesse | 1 mm/step | 3 mm/step |
| Santé | 300 | 100 |
| Détection frontale | 300 mm | 500 mm |
| Angle de rotation | 0.01π rad/step | 0.01π rad/step |

### 3.3 Paramètres des Projectiles

| Paramètre | Valeur |
|-----------|--------|
| Vitesse | 10 mm/step |
| Dégâts | 10 |
| Rayon | 5 mm |
| Portée | 1500 mm |
| Latence de tir | 20 steps |

---

## 4. Détails Techniques

### 4.1 Machine à États

Chaque robot utilise une machine à états finis pour gérer son comportement :

```java
public void step() {
    // Mise à jour des compteurs
    if (fireCounter > 0) fireCounter--;

    // Actions communes (tir automatique)
    scanAndShoot();

    // Exécution de l'état courant
    switch (currentState) {
        case STATE_1: stepState1(); break;
        case STATE_2: stepState2(); break;
        // ...
    }
}
```

### 4.2 Gestion des Directions

Le simulateur utilise un système trigonométrique horaire :
- EAST = 0
- SOUTH = π/2
- WEST = π
- NORTH = -π/2

```java
private void turnTo(double targetDir) {
    double diff = targetDir - getHeading();
    // Normalisation entre -π et π
    while (diff > Math.PI) diff -= 2 * Math.PI;
    while (diff < -Math.PI) diff += 2 * Math.PI;

    if (diff > 0) stepTurn(Parameters.Direction.RIGHT);
    else stepTurn(Parameters.Direction.LEFT);
}
```

### 4.3 Détection d'Obstacles

```java
private boolean isBlocked() {
    IFrontSensorResult front = detectFront();
    return front.getObjectType() != IFrontSensorResult.Types.NOTHING &&
           front.getObjectType() != IFrontSensorResult.Types.TeamMainBot &&
           front.getObjectType() != IFrontSensorResult.Types.TeamSecondaryBot;
}
```

### 4.4 Communication Inter-Robots

```java
// Envoi de message
broadcast("ENEMY_FOUND");

// Réception de messages
ArrayList<String> messages = fetchAllMessages();
for (String msg : messages) {
    if (msg.equals("ENEMY_FOUND")) {
        // Réagir au signal
    }
}
```

---

## 5. Problèmes Rencontrés et Solutions

### 5.1 Robots Bloqués en Place

**Problème** : Les robots spammaient les changements de direction sans bouger.

**Solution** : Séparation des phases TURN et MOVE avec des compteurs de délai :
```java
private void stepEvasiveTurn() {
    turnCounter--;
    if (isHeadingTo(currentDirection) || turnCounter <= 0) {
        currentState = EVASIVE_MOVE;
        moveCounter = MOVE_STEPS;
    } else {
        turnTo(currentDirection);
    }
}
```

### 5.2 Incompatibilité Java

**Problème** : Eclipse compile avec Java 23, mais le runtime est Java 17.

**Solution** : Compilation manuelle avec Java 17 :
```bash
rm -rf beans/*/*.class
javac -cp "jars/simulator.jar" -d beans src/**/*.java
java -cp "beans/:jars/simulator.jar" supportGUI.Viewer
```

### 5.3 Radar vs Scan 360°

**Problème** : Le scan 360° (rotation complète pour scanner) était inefficace.

**Solution** : Le radar `detectRadar()` détecte déjà à 360° autour du robot. Le scan par rotation est inutile et a été supprimé.

### 5.4 Équipe B Perdait Contre Berzerk

**Problème** : La stratégie statique de l'équipe B était vulnérable aux attaques agressives.

**Solutions** :
1. Suppression de l'état SCANNING inutile
2. Tir automatique dès détection d'ennemi (`scanAndShoot()`)
3. Augmentation de la portée des projectiles (1000 → 1500 mm)
4. Recul groupé pour éviter les blocages

---

## 6. Compilation et Exécution

### 6.1 Prérequis
- Java 17 ou supérieur
- Fichier `jars/simulator.jar`

### 6.2 Compilation
```bash
cd /mnt/c/Users/Denn/eclipse-workspace/Projet_Daar
rm -rf beans/*/*.class
javac -cp "jars/simulator.jar" -d beans src/**/*.java
```

### 6.3 Exécution
```bash
java -cp "beans/:jars/simulator.jar" supportGUI.Viewer
```

---

## 7. Conclusion

Ce projet implémente deux stratégies complémentaires :

- **Équipe A** : Stratégie offensive coordonnée entre scouts rapides et tanks puissants
- **Équipe B** : Stratégie défensive groupée avec coordination de recul

Les points clés de l'implémentation :
1. Machine à états pour gérer les comportements complexes
2. Communication inter-robots pour la coordination
3. Esquive intelligente des obstacles
4. Tir automatique pour maximiser les dégâts

### 7.1 Améliorations Possibles

- Implémentation de formations plus complexes
- Prédiction de trajectoire des ennemis pour le tir
- Stratégies adaptatives basées sur l'état du combat
- Meilleure coordination spatiale entre les robots

---

## 8. Structure des Fichiers

```
Projet_Daar/
├── src/
│   ├── algorithms/
│   │   ├── TeamAMainBotMarssoMougamadoubougary.java
│   │   ├── TeamASecondaryBotMarssoMougamadoubougary.java
│   │   ├── TeamBMainBotMarssoMougamadoubougary.java
│   │   └── TeamBSecondaryBotMarssoMougamadoubougary.java
│   └── characteristics/
│       └── Parameters.java
├── beans/
│   └── algorithms/
│       └── *.class
├── jars/
│   └── simulator.jar
└── RAPPORT.md
```
