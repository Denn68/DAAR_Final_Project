/* ******************************************************
 * Team B Main Bot - Stratégie Groupée Synchronisée (SOLIDE)
 *
 * Règles conservées:
 *  1. Groupement obligatoire - les 3 bots suivent les mêmes ordres (DIR sync)
 *  2. Signaux prioritaires - messages traités avant toute action
 *  3. Tir synchronisé - quand 1 détecte → les 3 tirent
 *  4. Recul synchronisé - quand 1 est bloqué → les 3 reculent
 *  5. Mouvement aléatoire - N/S/E/W, mais choisi + broadcast pour synchro
 *  6. Timeout ennemi - sortie FIRING si plus d'ennemi pendant un certain temps
 *  7. Étape post-retreat (REGROUP) - si toujours bloqués → monter puis ravancer (cycle)
 * ******************************************************/
package algorithms;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;

import java.util.ArrayList;
import java.util.Random;

public class TeamBMainBotMarssoMougamadoubougary extends Brain {

    // États
    private static final int MOVING = 0;
    private static final int FIRING = 1;
    private static final int RETREATING = 2;
    private static final int REGROUPING = 3;

    // Directions possibles (N, S, E, W)
    private static final double[] DIRECTIONS = {
        Parameters.NORTH,
        Parameters.SOUTH,
        Parameters.EAST,
        Parameters.WEST
    };

    // Constantes "timing" (évite glitch / oscillations)
    private static final double HEADING_TOL = 0.12;         // tolérance rad (plus stable que 0.01)
    private static final int MOVE_SEGMENT_STEPS = 60;       // durée d'une direction avant changement
    private static final int RETREAT_STEPS = 80;            // durée du retreat (mais avec vrai déblocage)
    private static final int FIRE_LATENCY = 18;             // délai entre tirs
    private static final int LOST_ENEMY_TIMEOUT = 120;      // steps sans voir l'ennemi (local)
    private static final int ENEMY_SEEN_GRACE = 30;         // si un mate voit encore, on reste FIRING un peu
    private static final int STUCK_TRIGGER = 8;             // nb steps consécutifs sans pouvoir avancer => bloqué
    private static final int ESCAPE_TURN_BUDGET = 20;        // max steps de turn pour chercher un couloir

    // Regroup phase (cycle demandé)
    private static final int REGROUP_UP_STEPS = 45;         // "monter"
    private static final int REGROUP_ADVANCE_STEPS = 60;    // "ravancer"
    private static final double REGROUP_UP_DIR = Parameters.NORTH;
    private static final double REGROUP_ADVANCE_DIR = Parameters.WEST;

    // État courant
    private int state;

    // Ticks / timers
    private int tick;
    private int fireCooldown;

    private int segmentRemaining;        // durée restante dans la direction courante
    private double currentDirection;

    private int retreatRemaining;
    private int regroupRemaining;

    // Anti-stuck
    private int stuckCounter;
    private int escapeTurnRemaining;
    private boolean escapeTurnRight;     // alterne L/R pour éviter de tourner toujours pareil

    // FIRING sortie propre
    private int localNoEnemyCounter;
    private int lastEnemySeenTick;       // dernier tick où (moi) j'ai vu l'ennemi
    private int lastEnemySeenMsgTick;    // dernier tick où j'ai reçu ENEMY_SEEN d'un mate

    // Random
    private Random random;

    public TeamBMainBotMarssoMougamadoubougary() {
        super();
    }

    public void activate() {
        state = MOVING;

        tick = 0;
        fireCooldown = 0;

        // start direction = WEST (comme ton code), mais on sync tout de suite
        currentDirection = Parameters.WEST;
        segmentRemaining = MOVE_SEGMENT_STEPS;

        retreatRemaining = 0;
        regroupRemaining = 0;

        stuckCounter = 0;
        escapeTurnRemaining = 0;
        escapeTurnRight = true;

        localNoEnemyCounter = 0;
        lastEnemySeenTick = -99999;
        lastEnemySeenMsgTick = -99999;

        random = new Random();

        broadcast(dirMsg(currentDirection));
        sendLogMessage("Bot B activé - MOVING (sync DIR)");
    }

    public void step() {
        tick++;
        if (fireCooldown > 0) fireCooldown--;

        // 1) PRIORITÉ ABSOLUE: traiter messages
        processMessages();

        // 2) Exécuter état
        switch (state) {
            case MOVING:
                doMoving();
                break;
            case FIRING:
                doFiring();
                break;
            case RETREATING:
                doRetreating();
                break;
            case REGROUPING:
                doRegrouping();
                break;
        }
    }

    /* ===========================
       ========== MESSAGES =========
       =========================== */

    private void processMessages() {
        ArrayList<String> messages = fetchAllMessages();
        for (String msg : messages) {
            if (msg == null) continue;

            // FIRING / RETREAT / REGROUP
            if (msg.startsWith("CMD:FIRING:")) {
                // entrée FIRING immédiate
                if (state != FIRING) {
                    state = FIRING;
                    localNoEnemyCounter = 0;
                    sendLogMessage("Signal FIRING reçu - Tir synchronisé");
                }
                continue;
            }

            if (msg.startsWith("CMD:RETREAT:")) {
                if (state != RETREATING) {
                    state = RETREATING;
                    retreatRemaining = RETREAT_STEPS;

                    // reset anti-stuck
                    stuckCounter = 0;
                    escapeTurnRemaining = ESCAPE_TURN_BUDGET;
                    escapeTurnRight = true;

                    sendLogMessage("Signal RETREAT reçu - Recul synchronisé");
                }
                continue;
            }

            if (msg.startsWith("CMD:REGROUP:")) {
                if (state != REGROUPING) {
                    state = REGROUPING;
                    regroupRemaining = REGROUP_UP_STEPS + REGROUP_ADVANCE_STEPS;

                    // reset anti-stuck
                    stuckCounter = 0;
                    escapeTurnRemaining = ESCAPE_TURN_BUDGET;
                    escapeTurnRight = true;

                    sendLogMessage("Signal REGROUP reçu - Monter puis ravancer");
                }
                continue;
            }

            // ENEMY_SEEN (sert à ne pas sortir trop vite de FIRING)
            if (msg.startsWith("ENEMY_SEEN:")) {
                int t = parseIntSafe(msg.substring("ENEMY_SEEN:".length()), -99999);
                if (t > lastEnemySeenMsgTick) lastEnemySeenMsgTick = t;
                continue;
            }

            // DIR sync
            if (msg.startsWith("DIR:")) {
                // format: DIR:<N|S|E|W>
                double d = parseDir(msg);
                if (!Double.isNaN(d)) {
                    currentDirection = d;
                    // on repart sur un segment complet (tout le monde identique)
                    segmentRemaining = MOVE_SEGMENT_STEPS;
                }
            }
        }
    }

    private String firingMsg()  { return "CMD:FIRING:" + tick; }
    private String retreatMsg() { return "CMD:RETREAT:" + tick; }
    private String regroupMsg() { return "CMD:REGROUP:" + tick; }
    private String enemySeenMsg(){ return "ENEMY_SEEN:" + tick; }

    private String dirMsg(double dir) {
        return "DIR:" + dirLetter(dir);
    }

    private String dirLetter(double dir) {
        if (dir == Parameters.NORTH) return "N";
        if (dir == Parameters.SOUTH) return "S";
        if (dir == Parameters.EAST)  return "E";
        if (dir == Parameters.WEST)  return "W";
        return "?";
    }

    private double parseDir(String msg) {
        // DIR:N / DIR:S / DIR:E / DIR:W
        if (msg.length() < 5) return Double.NaN;
        char c = msg.charAt(4);
        switch (c) {
            case 'N': return Parameters.NORTH;
            case 'S': return Parameters.SOUTH;
            case 'E': return Parameters.EAST;
            case 'W': return Parameters.WEST;
            default:  return Double.NaN;
        }
    }

    private int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    /* ===========================
       ========== MOVING ==========
       =========================== */

    private void doMoving() {
        // 1) Détection ennemi → FIRING sync
        if (detectEnemy()) {
            broadcast(firingMsg());
            state = FIRING;
            localNoEnemyCounter = 0;
            sendLogMessage("Ennemi détecté - FIRING broadcast");
            return;
        }

        // 2) Si on ne peut pas avancer plusieurs steps → RETREAT sync
        if (!tryMoveToward(currentDirection)) {
            stuckCounter++;
            if (stuckCounter >= STUCK_TRIGGER) {
                broadcast(retreatMsg());
                state = RETREATING;
                retreatRemaining = RETREAT_STEPS;

                // init escape pour dégager VRAIMENT
                escapeTurnRemaining = ESCAPE_TURN_BUDGET;
                escapeTurnRight = true;

                sendLogMessage("Bloqué (stuck) - RETREAT broadcast");
                stuckCounter = 0;
                return;
            }
        } else {
            stuckCounter = 0;
        }

        // 3) Direction segmentée (synchro)
        segmentRemaining--;
        if (segmentRemaining <= 0) {
            double newDir = DIRECTIONS[random.nextInt(4)];
            currentDirection = newDir;
            segmentRemaining = MOVE_SEGMENT_STEPS;

            // Sync: tout le monde prend la même direction
            broadcast(dirMsg(currentDirection));
            sendLogMessage("Nouvelle direction sync: " + directionName(currentDirection));
        }
    }

    /* ===========================
       ========== FIRING ==========
       =========================== */

    private void doFiring() {
        IRadarResult enemy = findClosestEnemy();

        if (enemy != null) {
            lastEnemySeenTick = tick;
            broadcast(enemySeenMsg());

            double enemyDir = enemy.getObjectDirection();

            // se tourner vers la cible
            if (!isHeading(enemyDir)) {
                turnToward(enemyDir);
            } else {
                // tirer avec cooldown
                if (fireCooldown == 0) {
                    fire(enemyDir);
                    fireCooldown = FIRE_LATENCY;
                }
            }

            localNoEnemyCounter = 0;
            return;
        }

        // Pas d'ennemi visible localement
        localNoEnemyCounter++;

        // Sortie FIRING solide:
        // - si moi je ne vois rien depuis longtemps
        // - ET personne n'a broadcast ENEMY_SEEN récemment
        boolean mateStillSees = (tick - lastEnemySeenMsgTick) <= ENEMY_SEEN_GRACE;

        if (localNoEnemyCounter >= LOST_ENEMY_TIMEOUT && !mateStillSees) {
            state = MOVING;
            localNoEnemyCounter = 0;

            // force changement direction sync (évite rester planté)
            currentDirection = DIRECTIONS[random.nextInt(4)];
            segmentRemaining = MOVE_SEGMENT_STEPS;
            broadcast(dirMsg(currentDirection));

            sendLogMessage("Fin FIRING (timeout) - retour MOVING + DIR sync");
            return;
        }

        // Option: en FIRING sans cible, on évite d’être statique
        // petite micro-mobilité (douce) pour casser les coins
        tryMoveToward(currentDirection);
    }

    /* ==============================
       ========== RETREATING ==========
       ============================== */

    private void doRetreating() {
        retreatRemaining--;

        // Vrai déblocage:
        // si devant est bloqué, on tourne (budget) pour chercher un couloir
        if (isFrontBlockedByWallOrEnemy()) {
            if (escapeTurnRemaining > 0) {
                stepTurn(escapeTurnRight ? Parameters.Direction.RIGHT : Parameters.Direction.LEFT);
                escapeTurnRemaining--;
                // alterne parfois pour ne pas faire un cercle infini
                if (escapeTurnRemaining % 5 == 0) escapeTurnRight = !escapeTurnRight;
            } else {
                // budget fini → on passe en REGROUP (cycle)
                broadcast(regroupMsg());
                state = REGROUPING;
                regroupRemaining = REGROUP_UP_STEPS + REGROUP_ADVANCE_STEPS;
                sendLogMessage("RETREAT bloqué (budget turn fini) -> REGROUP");
                return;
            }
        } else {
            // si libre devant, on bouge
            move();
        }

        if (retreatRemaining <= 0) {
            // Si après retreat on est encore bloqué -> REGROUP
            if (isFrontBlockedByWallOrEnemy()) {
                broadcast(regroupMsg());
                state = REGROUPING;
                regroupRemaining = REGROUP_UP_STEPS + REGROUP_ADVANCE_STEPS;
                sendLogMessage("Fin RETREAT mais encore bloqué -> REGROUP");
                return;
            }

            // Sinon retour MOVING + changement direction sync
            state = MOVING;
            currentDirection = DIRECTIONS[random.nextInt(4)];
            segmentRemaining = MOVE_SEGMENT_STEPS;
            broadcast(dirMsg(currentDirection));
            sendLogMessage("Fin RETREAT -> MOVING + DIR sync");
        }
    }

    /* ==============================
       ========== REGROUPING =========
       ============================== */

    private void doRegrouping() {
        regroupRemaining--;

        int phase1Left = regroupRemaining - REGROUP_ADVANCE_STEPS; // >0 => on est encore dans UP
        double target = (phase1Left > 0) ? REGROUP_UP_DIR : REGROUP_ADVANCE_DIR;

        // On applique la même routine anti-stuck qu'en retreat (sinon REGROUP sert à rien)
        if (!tryMoveTowardWithEscape(target)) {
            // si vraiment impossible -> on continue à tourner un peu
            // (tryMoveTowardWithEscape gère déjà du turn)
        }

        if (regroupRemaining <= 0) {
            // Retour MOVING et on repart sur direction sync (random)
            state = MOVING;
            currentDirection = DIRECTIONS[random.nextInt(4)];
            segmentRemaining = MOVE_SEGMENT_STEPS;
            broadcast(dirMsg(currentDirection));
            sendLogMessage("Fin REGROUP -> MOVING + DIR sync");
        }
    }

    /* ===========================
       ========== HELPERS =========
       =========================== */

    private boolean detectEnemy() {
        ArrayList<IRadarResult> radarResults = detectRadar();
        for (IRadarResult r : radarResults) {
            if (isEnemy(r.getObjectType())) return true;
        }
        return false;
    }

    private IRadarResult findClosestEnemy() {
        ArrayList<IRadarResult> radarResults = detectRadar();
        IRadarResult closest = null;
        double minDist = Double.MAX_VALUE;

        for (IRadarResult r : radarResults) {
            if (isEnemy(r.getObjectType())) {
                if (r.getObjectDistance() < minDist) {
                    closest = r;
                    minDist = r.getObjectDistance();
                }
            }
        }
        return closest;
    }

    private boolean isEnemy(IRadarResult.Types type) {
        return type == IRadarResult.Types.OpponentMainBot ||
               type == IRadarResult.Types.OpponentSecondaryBot;
    }

    private boolean isHeading(double targetDir) {
        double diff = normalizeAngle(targetDir - getHeading());
        return Math.abs(diff) <= HEADING_TOL;
    }

    private void turnToward(double targetDir) {
        double diff = normalizeAngle(targetDir - getHeading());
        if (diff > 0) stepTurn(Parameters.Direction.RIGHT);
        else stepTurn(Parameters.Direction.LEFT);
    }

    private double normalizeAngle(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    private boolean isFrontBlockedByWallOrEnemy() {
        IFrontSensorResult front = detectFront();
        // On considère "libre" si NOTHING ou allié (comme ton code)
        return !(front.getObjectType() == IFrontSensorResult.Types.NOTHING ||
                 front.getObjectType() == IFrontSensorResult.Types.TeamMainBot ||
                 front.getObjectType() == IFrontSensorResult.Types.TeamSecondaryBot);
    }

    /**
     * Tente de se déplacer vers une direction.
     * Retourne true si on a pu faire un move() (donc mouvement réel),
     * false si on a juste tourné ou si c'était bloqué.
     */
    private boolean tryMoveToward(double direction) {
        if (!isHeading(direction)) {
            turnToward(direction);
            return false;
        }

        if (!isFrontBlockedByWallOrEnemy()) {
            move();
            return true;
        }

        return false;
    }

    /**
     * Version "solide" : si bloqué, on tourne un peu pour trouver libre, sinon move.
     */
    private boolean tryMoveTowardWithEscape(double direction) {
        if (!isHeading(direction)) {
            turnToward(direction);
            return false;
        }

        if (!isFrontBlockedByWallOrEnemy()) {
            move();
            return true;
        }

        // devant bloqué → escape turn (alterné) pour trouver un couloir
        if (escapeTurnRemaining <= 0) {
            escapeTurnRemaining = ESCAPE_TURN_BUDGET;
            escapeTurnRight = !escapeTurnRight;
        }

        stepTurn(escapeTurnRight ? Parameters.Direction.RIGHT : Parameters.Direction.LEFT);
        escapeTurnRemaining--;
        if (escapeTurnRemaining % 4 == 0) escapeTurnRight = !escapeTurnRight;
        return false;
    }

    private String directionName(double dir) {
        if (dir == Parameters.NORTH) return "NORTH";
        if (dir == Parameters.SOUTH) return "SOUTH";
        if (dir == Parameters.EAST)  return "EAST";
        if (dir == Parameters.WEST)  return "WEST";
        return "?";
    }
}
