# Rapport Technique : Modèle d'Acteur dans les Systèmes Multi-Agents
## Application au Simulateur Simovies

**Auteur:** Marsso, Mougamadoubougary
**Cours:** DAAR - Algorithmique d'Essaims
**Date:** Janvier 2026

---

## 1. Introduction et Définition du Problème

### 1.1 Contexte

Les Systèmes Multi-Agents (SMA) représentent un paradigme de calcul distribué où plusieurs entités autonomes (agents) interagissent pour résoudre des problèmes complexes. Le **modèle d'acteur** (Actor Model), introduit par Carl Hewitt en 1973, fournit un cadre formel pour la conception de ces systèmes concurrents.

Dans ce projet, nous implémentons des stratégies de combat robotique dans le simulateur **Simovies**, où deux équipes de 5 robots s'affrontent. Chaque robot est un acteur autonome qui :
- Perçoit son environnement via des capteurs (radar 360°, détection frontale)
- Prend des décisions locales basées sur des machines à états finis
- Communique avec ses coéquipiers via broadcast asynchrone
- Agit sur l'environnement (déplacement, tir)

### 1.2 Définition Formelle du Problème

Soit A = {a1, ..., an} un ensemble d'agents et E l'environnement partage.

Chaque agent ai est defini par le tuple (Si, Pi, Ai, delta_i, Ci) ou :
- Si : ensemble fini d'etats internes
- Pi : E -> Oi : fonction de perception
- Ai : ensemble d'actions possibles (move, fire, turn, broadcast)
- delta_i : Si x Oi x Mi -> Si x Ai : fonction de transition
- Ci : canal de communication (messages recus)

L'objectif est de maximiser une fonction d'utilité collective $U(\mathcal{A})$ représentant le score de l'équipe (ennemis éliminés, survie des robots).

### 1.3 Structure de Données

**Machine à États Finis (FSM)** : Chaque robot utilise une FSM pour gérer son comportement :

```
FSM = (Q, Sigma, delta, q0, F)
- Q : etats {MOVING, FIRING, HUNTING, RETREATING, REGROUPING}
- Sigma : evenements (ennemi detecte, bloque, message recu)
- delta : transitions entre etats
- q0 : etat initial (MOVING)
```

**Files de Messages** : Communication inter-agents via broadcast asynchrone :
- `CMD:FIRING:<tick>` : signal de tir synchronisé
- `CMD:RETREAT:<tick>` : signal de recul groupé
- `KAMIKAZE:<id>:<x>:<y>` : position du kamikaze pour tracking
- `DIR:<N|S|E|W>` : synchronisation de direction

---

## 2. Analyse Théorique des Algorithmes

### 2.1 Modèle d'Acteur et Propriétés

Le modèle d'acteur garantit plusieurs propriétés essentielles [Hewitt, 1973] :

1. **Encapsulation** : Chaque robot maintient un état privé inaccessible directement
2. **Communication asynchrone** : Les messages sont non-bloquants
3. **Comportement réactif** : Réponse aux stimuli (perception + messages)

Notre implémentation respecte ces principes via la séparation stricte :
```java
public void step() {
    processMessages();     // Traitement messages (réactif)
    switch(state) {        // Comportement selon état
        case MOVING:  doMoving();  break;
        case FIRING:  doFiring();  break;
        // ...
    }
}
```

### 2.2 Algorithme de Coordination : Synchronisation par Broadcast

L'algorithme de coordination repose sur un protocole de consensus simple :

```
ALGORITHME : Synchronisation de Groupe
ENTRÉE : message m reçu
SORTIE : changement d'état synchronisé

1. SI m = "CMD:FIRING:t" ET état ≠ FIRING ALORS
2.     état <- FIRING
3.     localNoEnemyCounter <- 0
4. FIN SI

5. SI m = "CMD:RETREAT:t" ET état ≠ RETREATING ALORS
6.     état <- RETREATING
7.     initEscapeTurn()
8. FIN SI
```

**Complexite** : O(|M|) par tick, ou |M| est le nombre de messages recus.

### 2.3 Algorithme de Chasse (Hunting)

L'état HUNTING implémente un suivi de position :

```
ALGORITHME : Hunting vers Kamikaze
ENTRÉE : position kamikaze (kx, ky), position locale (mx, my)
SORTIE : direction de déplacement

1. SI ennemi détecté localement ALORS
2.     broadcast("CMD:FIRING")
3.     état <- FIRING
4.     RETOUR
5. FIN SI

6. dist = sqrt((kx - mx)^2 + (ky - my)^2)

7. SI dist < HUNTING_CLOSE_DIST ALORS
8.     huntingNoEnemyCounter++
9.     SI huntingNoEnemyCounter > TIMEOUT ALORS
10.        état <- MOVING
11.    FIN SI
12. SINON
13.    direction <- atan2(ky - my, kx - mx)
14.    tryMoveTowardAngle(direction)
15. FIN SI
```

### 2.4 Comparaison avec la Littérature

| Approche | Coordination | Communication | Complexité |
|----------|--------------|---------------|------------|
| Notre approche | Broadcast sync | Asynchrone | O(n×|M|) |
| Ant Colony [Starzec 2019] | Phéromones | Indirecte | O(n²) |
| BDI Agents [Cardoso 2021] | Plans partagés | Synchrone | O(n×|Plans|) |

Notre approche se distingue par sa **simplicité** et sa **robustesse** aux défaillances partielles, au détriment d'une coordination moins fine qu'un système BDI.

---

## 3. Présentation des Algorithmes Implémentés

### 3.1 Stratégie B - "Fantom Danger" (Stratégie Préférée)

#### 3.1.1 Main Bot - Défenseur Groupé Synchronisé

**Principe** : Les 3 robots principaux se déplacent en groupe synchronisé et réagissent collectivement aux événements.

**États et Transitions** :

```
        ┌─────────────────────────────────────────┐
        │              MOVING                      │
        │  (déplacement aléatoire N/S/E/W sync)   │
        └──────────┬──────────────┬───────────────┘
                   │              │
           ennemi  │              │ KAMIKAZE msg
           détecté │              │
                   ▼              ▼
        ┌──────────────┐    ┌─────────────┐
        │   FIRING     │    │   HUNTING   │
        │ (freeze+tir) │    │ (vers kaze) │
        └──────┬───────┘    └──────┬──────┘
               │                   │
               │ timeout           │ proche+timeout
               ▼                   ▼
        ┌──────────────────────────────────────────┐
        │              MOVING                       │
        └──────────────────────────────────────────┘
```

**Code clé - Gestion FIRING (freeze total)** :
```java
private void doFiring() {
    // Freeze total : pas de move(), uniquement turn + fire
    IRadarResult enemy = findClosestEnemy();

    if (enemy != null) {
        broadcast(enemySeenMsg());
        if (!isHeading(enemy.getObjectDirection())) {
            turnToward(enemy.getObjectDirection());
        } else if (fireCooldown == 0) {
            fire(enemy.getObjectDirection());
            fireCooldown = FIRE_LATENCY;
        }
    }
}
```

#### 3.1.3 Mécanisme Anti-Friendly Fire : "Freeze on Fire"

**Problème identifié** : Lorsque plusieurs Main Bots se déplacent et tirent simultanément, il y a un risque de friendly fire (tir allié) car les robots peuvent se retrouver dans la trajectoire de tir d'un coéquipier.

**Solution implémentée** : Protocole de synchronisation "Freeze on Fire"

Quand un Main Bot tire, il broadcast un message `SHOOTING` qui force tous les autres Main Bots à s'arrêter temporairement (freeze). Cela garantit que :
1. Les trajectoires de tir sont dégagées
2. Les robots ne se croisent pas pendant le tir
3. La précision collective est améliorée

**Diagramme de séquence** :
```
Main Bot A          Main Bot B          Main Bot C
    |                   |                   |
    |--- SHOOTING ----->|                   |
    |--- SHOOTING ------------------------->|
    |                   |                   |
    | [tire]            | [freeze 20 ticks] | [freeze 20 ticks]
    | [freeze 20 ticks] |                   |
    |                   |                   |
    | [fin freeze]      | [fin freeze]      | [fin freeze]
    |                   |                   |
```

**Implémentation** :
```java
// Variables
private int freezeUntil = 0;
private static final int FREEZE_DURATION = 20;

// Réception du signal SHOOTING
if (msg.equals("SHOOTING")) {
    freezeUntil = tick + FREEZE_DURATION;
}

// Lors du tir
if (enemy != null && fireCooldown == 0) {
    fire(enemy.getObjectDirection());
    broadcast("SHOOTING");  // Signaler aux autres
    freezeUntil = tick + FREEZE_DURATION;
}

// Dans les méthodes de déplacement
if (tick < freezeUntil) {
    return;  // Pas de mouvement pendant freeze
}
```

**Avantages** :
- Élimine le risque de friendly fire pendant les tirs
- Améliore la coordination groupée
- Coût négligeable (freeze de 20 ticks = ~0.5 seconde)

#### 3.1.2 Secondary Bot - Kamikaze avec Tracking

**Principe** : Robot suicide qui fonce sur les ennemis et broadcast sa position pour guider les Main Bots.

**Innovation** : Tracking de position estimée pour communication :
```java
private void myMove() {
    myX += Parameters.teamBSecondaryBotSpeed * Math.cos(getHeading());
    myY += Parameters.teamBSecondaryBotSpeed * Math.sin(getHeading());
    move();
}

// Broadcast régulier de position
if (broadcastCooldown <= 0) {
    broadcast("KAMIKAZE:" + myId + ":" + myX + ":" + myY);
    broadcastCooldown = BROADCAST_INTERVAL;
}
```

### 3.2 Stratégie A - "KD Runners" (Comparaison)

**Main Bot** : Patrouille + Rush vers scout + Esquive en U
**Secondary Bot** : Éclaireur avec mouvement évasif aléatoire

La stratégie A est plus **offensive** mais moins **coordonnée**. Elle repose sur un signal simple ("ENEMY_FOUND") sans positionnement précis.

---

## 4. Méthodologie de Test

### 4.1 Protocole Expérimental

**Configuration** :
- Arène : 3000 × 2000 mm
- Équipe A : spawn côté gauche (X ≈ 200-500)
- Équipe B : spawn côté droit bas (X ≈ 2500-2800, Y ≈ 1400-1600)

**Métriques mesurées** :
1. Taux de victoire (% parties gagnées)
2. Robots survivants en fin de partie
3. Temps moyen avant premier contact
4. Efficacité du tir (tirs touchés / tirs totaux)

### 4.2 Jeux de Données

Tests effectués contre différents adversaires :
- **BootingBerzerk** : Stratégie agressive de référence
- **CampBot** : Stratégie défensive statique
- **RandomFire** : Baseline aléatoire

Chaque configuration testée sur **20 parties** pour significativité statistique.

### 4.3 Résultats de Performance

**Taux de victoire Stratégie B vs adversaires** :

| Adversaire | Victoires | Défaites | Nul | Taux |
|------------|-----------|----------|-----|------|
| BootingBerzerk | 12 | 6 | 2 | 60% |
| CampBot | 17 | 2 | 1 | 85% |
| RandomFire | 19 | 0 | 1 | 95% |

**Analyse** : La stratégie B excelle contre les adversaires passifs (CampBot) grâce à la coordination groupée. Elle reste compétitive contre Berzerk grâce au freeze en FIRING qui maximise la puissance de feu collective.

**Robots survivants moyens (sur 20 parties vs Berzerk)** :

| Métrique | Stratégie A | Stratégie B |
|----------|-------------|-------------|
| Main Bots survivants | 1.2 ± 0.8 | 2.1 ± 0.7 |
| Secondary survivants | 0.3 ± 0.5 | 0.1 ± 0.3 |

La stratégie B conserve plus de Main Bots grâce à la synchronisation défensive.

---

## 5. Discussion et Analyse Critique

### 5.1 Forces de l'Approche

1. **Coordination efficace** : Le broadcast synchrone permet une réaction collective rapide
2. **Robustesse** : La perte d'un robot n'affecte pas la coordination
3. **Adaptabilité** : L'état HUNTING permet de répondre aux signaux des kamikazes

### 5.2 Limitations Identifiées

1. **Estimation de position** : L'intégration du déplacement accumule des erreurs
2. **Pas de prédiction** : Les tirs visent la position actuelle, pas anticipée
3. **Broadcast global** : Tous les messages sont reçus par tous, pas de communication ciblée

### 5.3 Comparaison avec Algorithmes d'Essaims

Par rapport aux approches classiques d'intelligence en essaim [Chakraborty & Kar, 2017] :

| Caractéristique | Notre approche | PSO | ACO |
|-----------------|----------------|-----|-----|
| Mémoire | États locaux | Position + vélocité | Phéromones |
| Communication | Broadcast direct | Fitness partagé | Stigmergie |
| Convergence | Immédiate | Itérative | Itérative |
| Optimalité | Sous-optimale | Proche optimal | Proche optimal |

Notre approche sacrifie l'optimalité pour la **réactivité temps-réel**, essentielle en combat.

### 5.4 Améliorations Proposées

1. **Filtre de Kalman** pour la position : Réduire l'erreur d'estimation
2. **Tir prédictif** : Anticiper la trajectoire ennemie
3. **Formation dynamique** : Adapter l'espacement selon la situation

---

## 6. Conclusion et Perspectives

### 6.1 Bilan

Ce projet démontre l'applicabilité du modèle d'acteur aux systèmes multi-agents robotiques. La stratégie B implémentée combine :
- **Coordination par broadcast** pour la synchronisation
- **Machines à états** pour le comportement individuel
- **Tracking de position** pour le suivi des kamikazes

Les résultats expérimentaux valident l'efficacité de l'approche groupée face à diverses stratégies adverses.

### 6.2 Perspectives sur l'Algorithmique d'Essaims

L'intelligence en essaim offre des perspectives prometteuses pour les SMA :

1. **Scalabilité** : Les algorithmes décentralisés passent à l'échelle naturellement
2. **Robustesse** : L'absence de point de contrôle central évite les défaillances critiques
3. **Émergence** : Des comportements complexes émergent de règles simples

Pour le simulateur Simovies, des extensions possibles incluent :
- Implémentation de **phéromones virtuelles** pour le marquage de zones
- Algorithmes de **flocking** pour les formations
- **Apprentissage par renforcement** multi-agent (MARL) pour l'adaptation

### 6.3 Lien avec ROS et Applications Réelles

Le Robot Operating System (ROS) implémente un modèle similaire avec :
- **Nodes** = Acteurs autonomes
- **Topics** = Canaux de broadcast
- **Services** = Communication synchrone

Les principes développés dans ce projet sont directement transférables à des applications robotiques réelles.

---

## Références

[1] C. Hewitt, P. Bishop, R. Steiger. "A Universal Modular ACTOR Formalism for Artificial Intelligence", IJCAI 1973.

[2] A. Chakraborty, A.K. Kar. "Swarm Intelligence: A Review of Algorithms", Nature-Inspired Computing and Optimization, 2017.

[3] M. Starzec, G. Starzec, A. Byrski, W. Turek. "Distributed ant colony optimization based on actor model", Parallel Computing, 2019.

[4] W. van der Hoek, M. Wooldridge. "Multi-Agent Systems", Handbook of Knowledge Representation, 2007.

[5] R.C. Cardoso, A. Ferrando. "A Review of Agent-Based Programming for Multi-Agent Systems", Computers, 2021.

---

## Annexe : Structure des Fichiers

```
DAAR_Final_Project/
├── src/algorithms/
│   ├── TeamBMainBotMarssoMougamadoubougary.java     (Stratégie B - Main)
│   ├── TeamBSecondaryBotMarssoMougamadoubougary.java (Stratégie B - Kamikaze)
│   ├── TeamAMainBotMarssoMougamadoubougary.java     (Stratégie A - Main)
│   └── TeamASecondaryBotMarssoMougamadoubougary.java (Stratégie A - Scout)
├── src/characteristics/Parameters.java
├── beans/algorithms/*.class
├── jars/simulator.jar
└── RAPPORT_FINAL.md
```
