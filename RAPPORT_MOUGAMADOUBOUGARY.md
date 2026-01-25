# Rapport Technique : Systemes Multi-Agents et Modele d'Acteur
## Application au Combat Robotique dans Simovies

**Auteur:** Mohamed MOUGAMADOUBOUGARY
**Cours:** DAAR - Algorithmique d'Essaims
**Date:** Janvier 2026

---

## 1. Introduction

### 1.1 Contexte et motivation

Le projet Simovies nous plonge dans l'univers des Systemes Multi-Agents (SMA), ou des robots autonomes doivent cooperer et s'affronter dans une arene. Ce qui m'a particulierement interesse dans ce projet, c'est la dualite entre **autonomie individuelle** et **coordination collective** : comment faire en sorte que des agents independants, sans chef centralise, puissent ensemble accomplir un objectif commun ?

Le **modele d'acteur**, formalise par Carl Hewitt en 1973, offre un cadre elegant pour cette problematique. Chaque robot est un acteur qui :
- Possede un etat prive (position, orientation, sante)
- Reagit aux messages de ses coequipiers (broadcast asynchrone)
- Percoit partiellement son environnement (radar 360°, capteur frontal)
- Prend des decisions locales via une machine a etats finis (FSM)

### 1.2 Definition formelle du probleme

Soit deux equipes E_A et E_B de 5 robots chacune (3 Main Bots + 2 Secondary Bots) evoluant dans une arene de 3000x2000mm.

Chaque robot r_i est modelise par le tuple :

```
r_i = (S_i, P_i, A_i, delta_i, M_i)
```

Ou :
- **S_i** : ensemble fini d'etats (ex: {ADVANCING, HOLDING, DODGING})
- **P_i** : fonction de perception (radar + capteur frontal)
- **A_i** : actions possibles {move, turn, fire, broadcast}
- **delta_i** : fonction de transition S_i x Perception x Messages -> S_i x Action
- **M_i** : file de messages recus des coequipiers

**Objectif** : maximiser le score (robots adverses elimines) tout en minimisant les pertes.

### 1.3 Structures de donnees utilisees

Pour implementer nos strategies, j'ai utilise les structures suivantes :

| Structure | Usage | Justification |
|-----------|-------|---------------|
| `ArrayList<IRadarResult>` | Stockage des detections radar | Acces sequentiel, taille variable |
| `ArrayList<String>` | File de messages broadcast | FIFO naturel pour messages |
| Variables primitives (int, double) | Etats, compteurs, directions | Efficacite memoire, pas besoin de complexite |
| Constantes statiques | Parametres FSM (durees, seuils) | Lisibilite et maintenance |

J'ai volontairement evite les structures complexes (arbres, graphes) car le temps reel impose des decisions en O(1) ou O(n) avec n petit.

---

## 2. Analyse Theorique

### 2.1 Le modele d'acteur dans Simovies

Le simulateur Simovies implemente naturellement le modele d'acteur :

```
┌─────────────────────────────────────────────────────┐
│                    ACTEUR (Robot)                    │
├─────────────────────────────────────────────────────┤
│  Etat prive:                                        │
│    - currentState (ADVANCING, HOLDING, ...)         │
│    - position estimee (myX, myY)                    │
│    - compteurs internes                             │
├─────────────────────────────────────────────────────┤
│  Boite aux lettres:                                 │
│    - fetchAllMessages() -> ArrayList<String>        │
│    - Messages asynchrones, non-bloquants            │
├─────────────────────────────────────────────────────┤
│  Comportement:                                      │
│    - step() execute a chaque tick                   │
│    - Decisions locales basees sur perception + msgs │
└─────────────────────────────────────────────────────┘
```

Les proprietes du modele d'acteur sont respectees :
1. **Encapsulation** : l'etat de chaque robot est inaccessible aux autres
2. **Communication asynchrone** : broadcast() est non-bloquant
3. **Pas de memoire partagee** : chaque robot a ses propres variables

### 2.2 Machines a etats finis (FSM)

La FSM est le coeur decisionnel de chaque robot. Apres plusieurs iterations, nous avons converge vers des FSM **minimalistes** :

**Main Bot B - Version finale (3 etats) :**
```
         ┌──────────────────────────────────────┐
         │                                      │
         v                                      │
    ┌─────────┐  ennemi detecte   ┌─────────┐  │
    │ADVANCING│ ───────────────> │ HOLDING │  │
    └─────────┘                   └─────────┘  │
         │                             │       │
         │ obstacle                    │ plus d'ennemi
         v                             │       │
    ┌─────────┐                        │       │
    │ DODGING │ <──────────────────────┘       │
    └─────────┘                                │
         │                                     │
         └─────────────────────────────────────┘
              fin esquive
```

**Secondary Bot B - Kamikaze (2 etats) :**
```
    ┌───────────┐  ennemi visible   ┌───────────┐
    │ SEARCHING │ <───────────────> │ ATTACKING │
    └───────────┘  ennemi perdu     └───────────┘
```

### 2.3 Complexite et garanties

| Aspect | Complexite | Justification |
|--------|------------|---------------|
| Scan radar | O(n) | n = nombre d'objets detectes (max ~10) |
| Decision FSM | O(1) | Switch sur etat courant |
| Broadcast | O(1) | Envoi simple |
| Traitement messages | O(m) | m = messages recus (~5 max) |

**Garantie de terminaison** : chaque appel a `step()` termine en temps borne car :
- Pas de boucle infinie (tous les while sont bornes)
- Pas de recursion
- Toutes les transitions FSM sont deterministes

---

## 3. Algorithmes Implementes

### 3.1 Strategie B : "Fantom Danger"

C'est notre strategie principale, celle qui a donne les meilleurs resultats.

#### 3.1.1 Main Bot B - Defenseur Vertical

**Philosophie** : Avancer methodiquement vers l'Ouest, engager tout ennemi visible, esquiver les obstacles sans perdre la direction generale.

**Implementation cle** :

```java
public void step() {
    // Toujours scanner et tirer si possible
    scanAndShoot();

    // Machine a etats
    switch (currentState) {
        case ADVANCING: stepAdvancing(); break;
        case HOLDING:   stepHolding();   break;
        case DODGING:   stepDodging();   break;
    }
}

private void scanAndShoot() {
    ArrayList<IRadarResult> radarResults = detectRadar();
    for (IRadarResult r : radarResults) {
        if (isEnemy(r.getObjectType())) {
            if (fireCounter == 0) {
                fire(r.getObjectDirection());
                fireCounter = FIRE_LATENCY;
            }
            if (currentState == ADVANCING) {
                currentState = HOLDING;
            }
            return;
        }
    }
}
```

**Points techniques** :
- `fireCounter` evite le spam de tirs (latence de 1 tick)
- `dodgeAttempts` compte les esquives consecutives : apres 2 echecs, demi-tour
- L'esquive est en deux phases : deplacement lateral puis retour a la direction principale

#### 3.1.2 Secondary Bot B - Kamikaze

**Philosophie** : Foncer vers l'Ouest, detecter les ennemis, broadcaster sa position pour guider les Main Bots.

```java
public void step() {
    IRadarResult enemy = findClosestEnemy();

    if (enemy != null) {
        state = ATTACKING;
        // Signaler aux Main Bots
        broadcast("KAMIKAZE:" + (int)myX + ":" + (int)myY);
        // Foncer vers l'ennemi
        moveToward(enemy.getObjectDirection());
    } else {
        state = SEARCHING;
        moveToward(Parameters.WEST);
    }
}
```

**Role strategique** : Le kamikaze sert d'eclaireur et de leurre. Il attire l'attention ennemie pendant que les Main Bots arrivent.

### 3.2 Strategie A : "KD Runners"

Notre strategie alternative, moins performante mais interessante conceptuellement.

#### 3.2.1 Main Bot A - Tank Chasseur

**Philosophie** : Patrouiller vers l'Est, reagir aux signaux des scouts, esquive en U sophistiquee.

La FSM est plus complexe (9 etats) avec une esquive en trois temps :
1. Tourner perpendiculairement
2. Avancer lateralement
3. Reprendre la direction initiale

```
PATROL -> RUSHING -> ENGAGING
   |         |          |
   +-----> DODGE_TURN1 -> DODGE_MOVE1 -> DODGE_TURN2 -> ...
```

#### 3.2.2 Secondary Bot A - Scout Eclaireur

**Philosophie** : Avancer, detecter, signaler "ENEMY_FOUND", puis passer en mode evasif aleatoire.

Le scout utilise des mouvements aleatoires pour :
- Esquiver les tirs ennemis
- Rester en vie le plus longtemps possible
- Garder l'attention de l'adversaire

---

## 4. Methodologie de Test

### 4.1 Protocole experimental

**Configuration de l'arene** :
- Dimensions : 3000 x 2000 mm
- Team A : spawn X ~ 200-500 (cote Ouest)
- Team B : spawn X ~ 2500-2800 (cote Est)

**Adversaires testes** :
- `BootingBerzerk` : agressif, mouvements rapides
- `CampFire` : defensif, peu mobile
- `RandomFire` : imprevisible, tirs aleatoires

**Metriques** :
1. Taux de victoire (sur 10 parties)
2. Robots survivants en fin de partie
3. Comportements anormaux (blocages, oscillations)

### 4.2 Resultats experimentaux

```
Taux de victoire de la Strategie B (sur 10 parties)

100% |                      ████████████
 90% | ████████████         ████████████
 80% | ████████████         ████████████
 70% | ████████████         ████████████
 60% | ████████████         ████████████
 50% | ████████████         ████████████
 40% | ████████████         ████████████
 30% | ████████████         ████████████
 20% | ████████████         ████████████
 10% | ████████████         ████████████
     +------------+---------+------------+
       Booting      Camp       Random
       Berzerk      Fire       Fire

  Victoires:  9/10       10/10       10/10
```

**Observations detaillees** :

| Adversaire | V | D | Taux | Commentaire |
|------------|---|---|------|-------------|
| BootingBerzerk | 9 | 1 | 90% | Defaite due a un enchainement defavorable d'angles |
| CampFire | 10 | 0 | 100% | Victoires lentes mais systematiques |
| RandomFire | 10 | 0 | 100% | Domination par la regularite |

### 4.3 Comparaison Strategie A vs B

La strategie A (KD Runners) a systematiquement perdu contre tous les adversaires :

```
Comparaison interne A vs B (10 parties)

Strategie A : 0 victoires
Strategie B : 10 victoires
```

**Causes identifiees** :
- Trop d'etats = trop de transitions = plus de bugs
- Communication "ENEMY_FOUND" trop vague (pas de position)
- Esquive en U trop longue, perte de temps

---

## 5. Discussion et Analyse Critique

### 5.1 Ce qui a fonctionne

**La simplicite paye** : Notre meilleure strategie est aussi la plus simple. Avec seulement 3 etats pour le Main Bot, nous avons :
- Moins de bugs
- Comportement plus previsible
- Meilleure reactivite

**La verticalite est efficace** : Avancer en ligne droite vers l'Ouest garantit :
- Des angles de tir propres
- Moins de rotations inutiles
- Une pression constante sur l'adversaire

**Le kamikaze comme eclaireur** : Meme si le Secondary Bot meurt souvent, il remplit son role :
- Il detecte l'ennemi en premier
- Il attire les tirs
- Il guide implicitement les Main Bots

### 5.2 Ce qui n'a pas fonctionne

**La coordination explicite** : Nous avons teste une version avec :
- Broadcast de positions precises
- Synchronisation des mouvements ("freeze" pendant les tirs allies)
- Chasse groupee vers les kamikazes

Resultat : **performances degradees**. Pourquoi ?

1. **Blocages en cascade** : quand un robot se bloque, les autres qui le suivent se bloquent aussi
2. **Angles de tir compromis** : en suivant le kamikaze, les Main Bots se retrouvent mal positionnes
3. **Latence des messages** : le temps que le message arrive, la situation a change

**Mon analyse personnelle** : La coordination explicite fonctionne bien en theorie, mais dans un environnement temps reel avec perception partielle, elle introduit plus de problemes qu'elle n'en resout. C'est contre-intuitif mais reel.

### 5.3 Limites de notre approche

1. **Pas d'anticipation** : nous tirons sur la position actuelle de l'ennemi, pas sur sa position future
2. **Pas de formation** : la cohesion du groupe est emergente, pas controlee
3. **Vulnerabilite aux strategies passives** : si l'ennemi ne bouge pas et qu'on ne le croise pas, on perd aux points
4. **Estimation de position approximative** : le tracking (myX, myY) derive avec le temps

### 5.4 Reflexion sur le modele d'acteur

Ce projet m'a fait realiser que le modele d'acteur a des **forces** et des **faiblesses** dans le contexte des SMA :

**Forces** :
- Scalabilite naturelle (chaque robot est independant)
- Tolerance aux pannes (si un robot meurt, les autres continuent)
- Pas de goulot d'etranglement central

**Faiblesses** :
- Difficulte a coordonner precisement (pas d'etat global)
- Latence de communication
- Risque de comportements emergents non desires

### 5.5 Ameliorations envisageables

1. **Tir predictif** : anticiper la trajectoire ennemie avec une estimation lineaire
2. **Evitement d'allies ameliore** : garder une distance minimale sans coordination explicite
3. **Exploration adaptative** : si pas d'ennemi detecte depuis longtemps, elargir la zone de recherche
4. **Cadence de tir variable** : tirer plus vite quand l'ennemi est proche

---

## 6. Conclusion

### 6.1 Bilan du projet

Ce projet illustre parfaitement les enjeux des systemes multi-agents :
- **Autonomie vs coordination** : trop de coordination peut etre contre-productif
- **Simplicite vs sophistication** : les FSM minimales sont souvent plus robustes
- **Theorie vs pratique** : les algorithmes elegants sur papier peuvent echouer en temps reel

Notre strategie finale "Fantom Danger" avec ses 3 etats simples obtient d'excellents resultats (90-100% de victoires) precisement parce qu'elle est **simple, verticale et reactive**.

### 6.2 Perspectives

Pour aller plus loin, je pense qu'il faudrait explorer :
- **L'apprentissage par renforcement** : laisser les robots apprendre leurs propres strategies
- **La communication semantique** : des messages plus riches que de simples positions
- **Les comportements emergents controles** : definir des regles locales qui produisent des comportements globaux souhaites (comme les boids de Reynolds)

### 6.3 Apport personnel

Ce projet m'a appris que dans les systemes distribues, **moins peut etre plus**. La tentation est grande d'ajouter de la coordination, de la communication, des etats supplementaires. Mais chaque ajout est aussi une source potentielle de bugs et de comportements inattendus.

La vraie difficulte n'est pas d'implementer des algorithmes complexes, mais de trouver le **bon niveau d'abstraction** : assez simple pour etre robuste, assez sophistique pour etre efficace.

---

## References

[1] C. Hewitt, P. Bishop, R. Steiger. "A Universal Modular ACTOR Formalism for Artificial Intelligence", IJCAI 1973.

[2] A. Chakraborty, A.K. Kar. "Swarm Intelligence: A Review of Algorithms", Nature-Inspired Computing and Optimization, 2017.

[3] M. Starzec, G. Starzec, A. Byrski, W. Turek. "Distributed ant colony optimization based on actor model", Parallel Computing, 2019.

[4] W. van der Hoek, M. Wooldridge. "Multi-Agent Systems", Handbook of Knowledge Representation, 2007.

[5] R.C. Cardoso, A. Ferrando. "A Review of Agent-Based Programming for Multi-Agent Systems", Computers, 2021.

[6] C.W. Reynolds. "Flocks, Herds, and Schools: A Distributed Behavioral Model", SIGGRAPH 1987.
