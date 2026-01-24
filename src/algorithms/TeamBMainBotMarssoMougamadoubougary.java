/* ******************************************************
 * Team B Main Bot - Stratégie Groupée Synchronisée (SOLIDE)
 *
 * Comportement final:
 *  - MOVING: déplacement aléatoire N/S/E/W synchro (DIR broadcast)
 *  - KAMIKAZE_HINT: si kamikaze détecte -> envoie direction+distance
 *  - HUNTING: les mains suivent cette direction pendant un "probe" limité
 *      -> si radar local voit un ennemi => FIRING (sync + HOLD)
 *      -> sinon fin probe => retour MOVING
 *  - FIRING: HOLD collectif (freeze total), ils ne bougent plus
 *  - RETREAT/REGROUP: déblocage si stuck
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
    private static final int HUNTING = 4;

    // Directions possibles
    private static final double[] DIRECTIONS = {
        Parameters.NORTH, Parameters.SOUTH, Parameters.EAST, Parameters.WEST
    };

    // Constantes
    private static final double HEADING_TOL = 0.12;
    private static final int MOVE_SEGMENT_STEPS = 60;

    private static final int RETREAT_STEPS = 80;
    private static final int FIRE_LATENCY = 18;

    private static final int LOST_ENEMY_TIMEOUT = 120;
    private static final int ENEMY_SEEN_GRACE = 30;

    private static final int STUCK_TRIGGER = 8;
    private static final int ESCAPE_TURN_BUDGET = 20;

    // Regroup cycle
    private static final int REGROUP_UP_STEPS = 45;
    private static final int REGROUP_ADVANCE_STEPS = 60;
    private static final double REGROUP_UP_DIR = Parameters.NORTH;
    private static final double REGROUP_ADVANCE_DIR = Parameters.WEST;

    private static final int FIRING_HOLD_STEPS = 35;

    private static final int HUNTING_MIN_PROBE = 35;
    private static final int HUNTING_MAX_PROBE = 90;
    private static final int HUNTING_HINT_TIMEOUT = 200; // si pas de hint récent -> stop hunt

    // État
    private int state;

    // Temps
    private int tick;
    private int fireCooldown;

    // Déplacement
    private int segmentRemaining;
    private double currentDirection;

    private int retreatRemaining;
    private int regroupRemaining;

    // Anti-stuck
    private int stuckCounter;
    private int escapeTurnRemaining;
    private boolean escapeTurnRight;

    // FIRING
    private int localNoEnemyCounter;
    private int lastEnemySeenMsgTick;

    // HOLD FIRING
    private int firingHoldRemaining = 0;
    private double sharedEnemyDir = Double.NaN;

    // Random
    private Random random;

    // HUNTING (hint kamikaze)
    private double kamikazeHintDir = Double.NaN;
    private double kamikazeHintDist = 0;
    private int huntingProbeRemaining = 0;
    private int lastKamikazeMsgTick = -99999;

    public TeamBMainBotMarssoMougamadoubougary() { super(); }

    public void activate() {
        state = MOVING;

        tick = 0;
        fireCooldown = 0;

        currentDirection = Parameters.WEST;
        segmentRemaining = MOVE_SEGMENT_STEPS;

        retreatRemaining = 0;
        regroupRemaining = 0;

        stuckCounter = 0;
        escapeTurnRemaining = 0;
        escapeTurnRight = true;

        localNoEnemyCounter = 0;
        lastEnemySeenMsgTick = -99999;

        firingHoldRemaining = 0;
        sharedEnemyDir = Double.NaN;

        kamikazeHintDir = Double.NaN;
        kamikazeHintDist = 0;
        huntingProbeRemaining = 0;
        lastKamikazeMsgTick = -99999;

        random = new Random();

        broadcast(dirMsg(currentDirection));
        sendLogMessage("Bot B activé - MOVING sync");
    }

    public void step() {
        tick++;
        if (fireCooldown > 0) fireCooldown--;

        // 1) messages en priorité
        processMessages();

        // 2) état
        switch (state) {
            case MOVING:     doMoving();     break;
            case FIRING:     doFiring();     break;
            case RETREATING: doRetreating(); break;
            case REGROUPING: doRegrouping(); break;
            case HUNTING:    doHunting();    break;
        }
    }

    /* ===========================
       ========== MESSAGES =========
       =========================== */

    private void processMessages() {
        ArrayList<String> messages = fetchAllMessages();
        for (String msg : messages) {
            if (msg == null) continue;

            // CMD:FIRING:tick:dir
            if (msg.startsWith("CMD:FIRING:")) {
                double dir = parseFiringDir(msg);

                if (state != FIRING) {
                    state = FIRING;
                    localNoEnemyCounter = 0;
                }

                firingHoldRemaining = FIRING_HOLD_STEPS;
                if (!Double.isNaN(dir)) sharedEnemyDir = dir;

                continue;
            }

            // CMD:RETREAT
            if (msg.startsWith("CMD:RETREAT:")) {
                if (state != RETREATING) {
                    state = RETREATING;
                    retreatRemaining = RETREAT_STEPS;
                    initEscapeTurn();
                }
                continue;
            }

            // CMD:REGROUP
            if (msg.startsWith("CMD:REGROUP:")) {
                if (state != REGROUPING) {
                    state = REGROUPING;
                    regroupRemaining = REGROUP_UP_STEPS + REGROUP_ADVANCE_STEPS;
                    initEscapeTurn();
                }
                continue;
            }

            // ENEMY_SEEN
            if (msg.startsWith("ENEMY_SEEN:")) {
                int t = parseIntSafe(msg.substring("ENEMY_SEEN:".length()), -99999);
                if (t > lastEnemySeenMsgTick) lastEnemySeenMsgTick = t;
                continue;
            }

            // DIR sync
            if (msg.startsWith("DIR:")) {
                double d = parseDir(msg);
                if (!Double.isNaN(d)) {
                    currentDirection = d;
                    segmentRemaining = MOVE_SEGMENT_STEPS;
                }
                continue;
            }

            // ✅ KAMIKAZE_HINT:<id>:<dir>:<dist>
            if (msg.startsWith("KAMIKAZE_HINT:")) {
                if (state == FIRING) continue;

                try {
                    String[] parts = msg.split(":");
                    double dir = Double.parseDouble(parts[2]);
                    double dist = Double.parseDouble(parts[3]);

                    kamikazeHintDir = dir;
                    kamikazeHintDist = dist;
                    lastKamikazeMsgTick = tick;

                    int probe = (int)(dist / 8.0); // facteur ajustable
                    if (probe < HUNTING_MIN_PROBE) probe = HUNTING_MIN_PROBE;
                    if (probe > HUNTING_MAX_PROBE) probe = HUNTING_MAX_PROBE;
                    huntingProbeRemaining = probe;

                    state = HUNTING;
                    initEscapeTurn();

                    sendLogMessage("KAMIKAZE_HINT -> HUNTING probe=" + probe);
                } catch (Exception ignored) {}

                continue;
            }
        }
    }

    private void initEscapeTurn() {
        stuckCounter = 0;
        escapeTurnRemaining = ESCAPE_TURN_BUDGET;
        escapeTurnRight = true;
    }

    private double parseFiringDir(String msg) {
        try {
            String[] parts = msg.split(":");
            if (parts.length >= 4 && parts[3] != null && !parts[3].isEmpty()) {
                return Double.parseDouble(parts[3]);
            }
        } catch (Exception ignored) {}
        return Double.NaN;
    }

    private String firingMsg(double enemyDirOrNaN) {
        if (Double.isNaN(enemyDirOrNaN)) return "CMD:FIRING:" + tick + ":";
        return "CMD:FIRING:" + tick + ":" + enemyDirOrNaN;
    }
    private String retreatMsg() { return "CMD:RETREAT:" + tick; }
    private String regroupMsg() { return "CMD:REGROUP:" + tick; }
    private String enemySeenMsg(){ return "ENEMY_SEEN:" + tick; }

    private String dirMsg(double dir) { return "DIR:" + dirLetter(dir); }
    private String dirLetter(double dir) {
        if (dir == Parameters.NORTH) return "N";
        if (dir == Parameters.SOUTH) return "S";
        if (dir == Parameters.EAST)  return "E";
        if (dir == Parameters.WEST)  return "W";
        return "?";
    }
    private double parseDir(String msg) {
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
        IRadarResult e = findClosestEnemy();
        if (e != null) {
            double d = e.getObjectDirection();
            broadcast(firingMsg(d));
            state = FIRING;
            localNoEnemyCounter = 0;

            firingHoldRemaining = FIRING_HOLD_STEPS;
            sharedEnemyDir = d;
            return;
        }

        if (!tryMoveToward(currentDirection)) {
            stuckCounter++;
            if (stuckCounter >= STUCK_TRIGGER) {
                broadcast(retreatMsg());
                state = RETREATING;
                retreatRemaining = RETREAT_STEPS;
                initEscapeTurn();
                stuckCounter = 0;
                return;
            }
        } else {
            stuckCounter = 0;
        }

        segmentRemaining--;
        if (segmentRemaining <= 0) {
            currentDirection = DIRECTIONS[random.nextInt(4)];
            segmentRemaining = MOVE_SEGMENT_STEPS;
            broadcast(dirMsg(currentDirection));
        }
    }

    /* ===========================
       ========== HUNTING ==========
       =========================== */

    private void doHunting() {
        IRadarResult e = findClosestEnemy();
        if (e != null) {
            double d = e.getObjectDirection();
            broadcast(firingMsg(d));
            state = FIRING;
            localNoEnemyCounter = 0;

            firingHoldRemaining = FIRING_HOLD_STEPS;
            sharedEnemyDir = d;
            return;
        }

        if (tick - lastKamikazeMsgTick > HUNTING_HINT_TIMEOUT) {
            state = MOVING;
            return;
        }

        if (huntingProbeRemaining > 0 && !Double.isNaN(kamikazeHintDir)) {
            huntingProbeRemaining--;
            tryMoveTowardWithEscape(kamikazeHintDir);
            return;
        }

        state = MOVING;
    }

    /* ===========================
       ========== FIRING ==========
       =========================== */

    private void doFiring() {
        if (firingHoldRemaining > 0) firingHoldRemaining--;

        IRadarResult enemy = findClosestEnemy();

        if (enemy != null) {
            broadcast(enemySeenMsg());
            localNoEnemyCounter = 0;

            double enemyDir = enemy.getObjectDirection();
            sharedEnemyDir = enemyDir;
            firingHoldRemaining = FIRING_HOLD_STEPS;

            if (!isHeading(enemyDir)) {
                turnToward(enemyDir);
                return;
            }

            if (fireCooldown == 0) {
                fire(enemyDir);
                fireCooldown = FIRE_LATENCY;
            }
            return;
        }

        localNoEnemyCounter++;

        if (firingHoldRemaining > 0 && !Double.isNaN(sharedEnemyDir)) {
            if (!isHeading(sharedEnemyDir)) {
                turnToward(sharedEnemyDir);
            } else if (fireCooldown == 0) {
                fire(sharedEnemyDir);
                fireCooldown = FIRE_LATENCY;
            }
            return;
        }

        boolean mateStillSees = (tick - lastEnemySeenMsgTick) <= ENEMY_SEEN_GRACE;
        if (localNoEnemyCounter >= LOST_ENEMY_TIMEOUT && !mateStillSees) {
            state = MOVING;
            localNoEnemyCounter = 0;

            currentDirection = DIRECTIONS[random.nextInt(4)];
            segmentRemaining = MOVE_SEGMENT_STEPS;
            broadcast(dirMsg(currentDirection));
        }
    }

    /* ==============================
       ========== RETREATING ==========
       ============================== */

    private void doRetreating() {
        retreatRemaining--;

        if (isFrontBlockedByWallOrEnemy()) {
            if (escapeTurnRemaining > 0) {
                stepTurn(escapeTurnRight ? Parameters.Direction.RIGHT : Parameters.Direction.LEFT);
                escapeTurnRemaining--;
                if (escapeTurnRemaining % 5 == 0) escapeTurnRight = !escapeTurnRight;
            } else {
                broadcast(regroupMsg());
                state = REGROUPING;
                regroupRemaining = REGROUP_UP_STEPS + REGROUP_ADVANCE_STEPS;
                initEscapeTurn();
                return;
            }
        } else {
            move();
        }

        if (retreatRemaining <= 0) {
            if (isFrontBlockedByWallOrEnemy()) {
                broadcast(regroupMsg());
                state = REGROUPING;
                regroupRemaining = REGROUP_UP_STEPS + REGROUP_ADVANCE_STEPS;
                initEscapeTurn();
                return;
            }

            state = MOVING;
            currentDirection = DIRECTIONS[random.nextInt(4)];
            segmentRemaining = MOVE_SEGMENT_STEPS;
            broadcast(dirMsg(currentDirection));
        }
    }

    /* ==============================
       ========== REGROUPING =========
       ============================== */

    private void doRegrouping() {
        regroupRemaining--;

        int phase1Left = regroupRemaining - REGROUP_ADVANCE_STEPS;
        double target = (phase1Left > 0) ? REGROUP_UP_DIR : REGROUP_ADVANCE_DIR;

        tryMoveTowardWithEscape(target);

        if (regroupRemaining <= 0) {
            state = MOVING;
            currentDirection = DIRECTIONS[random.nextInt(4)];
            segmentRemaining = MOVE_SEGMENT_STEPS;
            broadcast(dirMsg(currentDirection));
        }
    }

    /* ===========================
       ========== HELPERS =========
       =========================== */

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
        return !(front.getObjectType() == IFrontSensorResult.Types.NOTHING ||
                 front.getObjectType() == IFrontSensorResult.Types.TeamMainBot ||
                 front.getObjectType() == IFrontSensorResult.Types.TeamSecondaryBot);
    }

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

    private boolean tryMoveTowardWithEscape(double direction) {
        if (!isHeading(direction)) {
            turnToward(direction);
            return false;
        }

        if (!isFrontBlockedByWallOrEnemy()) {
            move();
            return true;
        }

        if (escapeTurnRemaining <= 0) {
            escapeTurnRemaining = ESCAPE_TURN_BUDGET;
            escapeTurnRight = !escapeTurnRight;
        }

        stepTurn(escapeTurnRight ? Parameters.Direction.RIGHT : Parameters.Direction.LEFT);
        escapeTurnRemaining--;
        if (escapeTurnRemaining % 4 == 0) escapeTurnRight = !escapeTurnRight;
        return false;
    }
}
