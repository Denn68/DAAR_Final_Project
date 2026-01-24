/* ******************************************************
 * Team B Secondary Bot - Kamikaze Agressif
 * Fonce vers l'ennemi, tire, et guide les Main Bots
 * ******************************************************/
package algorithms;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IRadarResult;
import java.util.ArrayList;

public class TeamBSecondaryBotMarssoMougamadoubougary extends Brain {

    private static final int SEARCHING = 0;
    private static final int ATTACKING = 1;

    private static final double HEADING_PRECISION = 0.05;
    private static final int FIRE_LATENCY = 15;
    private static final int BROADCAST_INTERVAL = 3;  // Broadcast tres frequent

    private int state;
    private int fireCounter;
    private int broadcastCooldown;
    private double targetDir;

    // Position tracking
    private double myX, myY;

    public TeamBSecondaryBotMarssoMougamadoubougary() { super(); }

    public void activate() {
        state = SEARCHING;
        fireCounter = 0;
        broadcastCooldown = 0;
        targetDir = Parameters.WEST;

        // Position initiale basee sur le heading initial
        // Les secondary B commencent a X~2500
        myX = 2500;
        myY = 1500;

        sendLogMessage("Kamikaze B pret - WEST");
    }

    public void step() {
        if (fireCounter > 0) fireCounter--;
        if (broadcastCooldown > 0) broadcastCooldown--;

        // Toujours scanner pour les ennemis
        IRadarResult enemy = findClosestEnemy();

        if (enemy != null) {
            // Ennemi trouve!
            state = ATTACKING;
            targetDir = enemy.getObjectDirection();

            // Broadcast position aux Main Bots
            if (broadcastCooldown <= 0) {
                broadcast("KAMIKAZE:" + (int)myX + ":" + (int)myY);
                broadcastCooldown = BROADCAST_INTERVAL;
            }

            // Tirer
            if (fireCounter == 0) {
                fire(targetDir);
                fireCounter = FIRE_LATENCY;
            }

            // Foncer vers l'ennemi
            if (!isHeading(targetDir)) {
                turnToward(targetDir);
            } else {
                myMove();
            }
        } else {
            // Pas d'ennemi visible - avancer vers WEST
            state = SEARCHING;
            if (!isHeading(Parameters.WEST)) {
                turnToward(Parameters.WEST);
            } else {
                myMove();
            }
        }
    }

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

    private void myMove() {
        myX += Parameters.teamBSecondaryBotSpeed * Math.cos(getHeading());
        myY += Parameters.teamBSecondaryBotSpeed * Math.sin(getHeading());
        move();
    }

    private boolean isHeading(double dir) {
        double diff = Math.abs(normalizeAngle(getHeading() - dir));
        return diff < HEADING_PRECISION;
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
}
