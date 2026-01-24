/* ******************************************************
 * Team B Main Bot - Défenseur
 * Stratégie: Avance, détecte, tient et tire
 * ******************************************************/
package algorithms;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;
import java.util.ArrayList;
import java.util.Random;

public class TeamBMainBotMarssoMougamadoubougary extends Brain {

    // Etats
    private static final int MOVING = 0;
    private static final int FIRING = 1;
    private static final int HUNTING = 2;

    // Constantes
    private static final double HEADINGPRECISION = 0.01;
    private static final int ADVANCING_STEPS = 100;
    private static final int DODGE_STEPS = 50;
    private static final int FIRE_LATENCY = 5;

    private int fireCounter;

    public TeamBMainBotMarssoMougamadoubougary() {
        super();
    }

    public void activate() {
        state = MOVING;
        tick = 0;
        fireCooldown = 0;
        freezeUntil = 0;
        stuckCounter = 0;
        noEnemyCounter = 0;
        huntTimer = 0;

        currentDir = Parameters.WEST;
        segmentRemaining = SEGMENT_STEPS;
        turnRight = true;

        // Position initiale (approximative)
        myX = 2800;
        myY = 1500;

        targetX = 0;
        targetY = 0;
        lastKamikazeMsg = -9999;

        random = new Random();

        broadcast("DIR:W");
        sendLogMessage("Main B ready");
    }

    public void step() {
        tick++;
        if (fireCooldown > 0) fireCooldown--;

        // Traiter messages
        processMessages();

        // Scanner ennemi - TOUJOURS tirer si visible
        IRadarResult enemy = findClosestEnemy();
        if (enemy != null && fireCooldown == 0) {
            fire(enemy.getObjectDirection());
            fireCooldown = FIRE_LATENCY;
            broadcast("SHOOTING");  // Signaler aux autres de freeze
            freezeUntil = tick + FREEZE_DURATION;  // Freeze moi aussi
        }

        // Machine a etats
        switch (state) {
            case MOVING:
                doMoving(enemy);
                break;
            case FIRING:
                doFiring(enemy);
                break;
            case HUNTING:
                doHunting(enemy);
                break;
        }
    }

    private void processMessages() {
        ArrayList<String> messages = fetchAllMessages();
        for (String msg : messages) {
            if (msg == null) continue;

            // Signal de tir - freeze pour éviter friendly fire
            if (msg.equals("SHOOTING")) {
                freezeUntil = tick + FREEZE_DURATION;
                continue;
            }

            // Signal de tir
            if (msg.startsWith("FIRE")) {
                if (state != FIRING) {
                    state = FIRING;
                    noEnemyCounter = 0;
                }
                continue;
            }

            // Synchronisation direction
            if (msg.startsWith("DIR:")) {
                if (state == MOVING) {
                    char c = msg.charAt(4);
                    if (c == 'N') currentDir = Parameters.NORTH;
                    else if (c == 'S') currentDir = Parameters.SOUTH;
                    else if (c == 'E') currentDir = Parameters.EAST;
                    else if (c == 'W') currentDir = Parameters.WEST;
                    segmentRemaining = SEGMENT_STEPS;
                }
                continue;
            }

            // Position kamikaze - KAMIKAZE:x:y
            if (msg.startsWith("KAMIKAZE:")) {
                if (state == FIRING) continue;  // Ne pas quitter le combat

                try {
                    String[] parts = msg.split(":");
                    targetX = Double.parseDouble(parts[1]);
                    targetY = Double.parseDouble(parts[2]);

                    // Valider position
                    if (targetX >= 0 && targetX <= 3000 && targetY >= 0 && targetY <= 2000) {
                        lastKamikazeMsg = tick;
                        huntTimer = 0;
                        state = HUNTING;
                    }
                } catch (Exception e) {}
                continue;
            }
        }
    }

    private void doMoving(IRadarResult enemy) {
        // Ennemi detecte -> FIRING
        if (enemy != null) {
            broadcast("FIRE");
            state = FIRING;
            noEnemyCounter = 0;
            return;
        }

        // Freeze: pas de mouvement pendant qu'un allié tire
        if (tick < freezeUntil) {
            return;
        }

        // Avancer
        if (!tryMove(currentDir)) {
            stuckCounter++;
            if (stuckCounter > STUCK_LIMIT) {
                // Bloque -> changer direction
                turnRight = !turnRight;
                currentDir = getPerpendicularDir(currentDir);
                broadcast("DIR:" + dirChar(currentDir));
                segmentRemaining = SEGMENT_STEPS;
                stuckCounter = 0;
            }
        } else {
            stuckCounter = 0;
        }

        // Changer direction periodiquement
        segmentRemaining--;
        if (segmentRemaining <= 0) {
            // Privilegier WEST pour avancer vers l'ennemi
            if (random.nextDouble() < 0.6) {
                currentDir = Parameters.WEST;
            } else {
                double[] dirs = {Parameters.NORTH, Parameters.SOUTH, Parameters.WEST};
                currentDir = dirs[random.nextInt(3)];
            }
            broadcast("DIR:" + dirChar(currentDir));
            segmentRemaining = SEGMENT_STEPS;
        }
    }

    private void doFiring(IRadarResult enemy) {
        if (enemy != null) {
            noEnemyCounter = 0;

            // Se tourner vers l'ennemi
            double dir = enemy.getObjectDirection();
            if (!isHeading(dir)) {
                turnToward(dir);
            }
            // Tir gere dans step()
        } else {
            noEnemyCounter++;

            // Timeout - retour MOVING
            if (noEnemyCounter > FIRE_TIMEOUT) {
                state = MOVING;
                noEnemyCounter = 0;
                currentDir = Parameters.WEST;
                segmentRemaining = SEGMENT_STEPS;
                broadcast("DIR:W");
            }
        }
    }

    private void doHunting(IRadarResult enemy) {
        // Ennemi detecte -> FIRING
        if (enemy != null) {
            broadcast("FIRE");
            state = FIRING;
            noEnemyCounter = 0;
            return;
        }

        // Freeze: pas de mouvement pendant qu'un allié tire
        if (tick < freezeUntil) {
            return;
        }

        huntTimer++;

        // Timeout kamikaze
        if (tick - lastKamikazeMsg > HUNT_TIMEOUT || huntTimer > HUNT_TIMEOUT * 2) {
            state = MOVING;
            currentDir = Parameters.WEST;
            segmentRemaining = SEGMENT_STEPS;
            return;
        }

        // Distance au kamikaze
        double dist = Math.sqrt(Math.pow(targetX - myX, 2) + Math.pow(targetY - myY, 2));

        // Proche du kamikaze mais pas d'ennemi -> retour MOVING
        if (dist < 150) {
            state = MOVING;
            currentDir = Parameters.WEST;
            segmentRemaining = SEGMENT_STEPS;
            return;
        }

        // Se diriger vers le kamikaze
        double dirToTarget = Math.atan2(targetY - myY, targetX - myX);
        if (!isHeading(dirToTarget)) {
            turnToward(dirToTarget);
        } else {
            if (!isFrontBlocked()) {
                myMove();
            } else {
                // Contourner
                stepTurn(turnRight ? Parameters.Direction.RIGHT : Parameters.Direction.LEFT);
            }
        }
    }

    // ========== HELPERS ==========

    private IRadarResult findClosestEnemy() {
        ArrayList<IRadarResult> results = detectRadar();
        IRadarResult closest = null;
        double minDist = Double.MAX_VALUE;

        for (IRadarResult r : results) {
            if (r.getObjectType() == IRadarResult.Types.OpponentMainBot ||
                r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot) {
                if (r.getObjectDistance() < minDist) {
                    closest = r;
                    minDist = r.getObjectDistance();
                }
            }
        }
        return closest;
    }

    private boolean tryMove(double dir) {
        if (!isHeading(dir)) {
            turnToward(dir);
            return false;
        }
        if (!isFrontBlocked()) {
            myMove();
            return true;
        }
        return false;
    }

    private void myMove() {
        myX += Parameters.teamBMainBotSpeed * Math.cos(getHeading());
        myY += Parameters.teamBMainBotSpeed * Math.sin(getHeading());
        move();
    }

    private boolean isHeading(double dir) {
        double diff = Math.abs(normalizeAngle(getHeading() - dir));
        return diff < HEADING_TOL;
    }

    private void turnToward(double dir) {
        double diff = normalizeAngle(dir - getHeading());
        stepTurn(diff > 0 ? Parameters.Direction.RIGHT : Parameters.Direction.LEFT);
    }

    private double normalizeAngle(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a < -Math.PI) a += 2 * Math.PI;
        return a;
    }

    private boolean isFrontBlocked() {
        IFrontSensorResult f = detectFront();
        return f.getObjectType() != IFrontSensorResult.Types.NOTHING &&
               f.getObjectType() != IFrontSensorResult.Types.TeamMainBot &&
               f.getObjectType() != IFrontSensorResult.Types.TeamSecondaryBot;
    }

    private double getPerpendicularDir(double dir) {
        if (dir == Parameters.WEST || dir == Parameters.EAST) {
            return turnRight ? Parameters.SOUTH : Parameters.NORTH;
        }
        return Parameters.WEST;
    }

    private char dirChar(double dir) {
        if (dir == Parameters.NORTH) return 'N';
        if (dir == Parameters.SOUTH) return 'S';
        if (dir == Parameters.EAST) return 'E';
        return 'W';
    }
}
