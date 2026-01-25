/* ******************************************************
 * Team A Secondary Bot - The Scout
 * Updates:
 * - Ensures continuous broadcasting.
 * - Random wiggle to prevent patrol syncing.
 * ******************************************************/
package algorithms;

import robotsimulator.Brain;
import characteristics.Parameters;
import characteristics.IFrontSensorResult;
import characteristics.IRadarResult;
import java.util.ArrayList;

public class TeamASecondaryBotMarssoMougamadoubougary extends Brain {

    private int fireCooldown;
    private double myX, myY;
    private boolean movingUp;
    private int stuckTimer;
    private boolean unstuckMode;
    private int unstuckTimer;
    private boolean unstuckTurnRight;
    
    private static final double MIN_SAFE_DIST = 400;
    private static final double MAX_SAFE_DIST = 600;
    private static final int FIRE_LATENCY = 20;

    public TeamASecondaryBotMarssoMougamadoubougary() {
        super();
    }

    public void activate() {
        fireCooldown = 0;
        movingUp = true;
        stuckTimer = 0;
        unstuckMode = false;
        if (Math.random() > 0.5) movingUp = false;
        myX = 500; myY = 1000; 
        sendLogMessage("Scout Activated.");
    }

    public void step() {
        if (fireCooldown > 0) fireCooldown--;

        IFrontSensorResult front = detectFront();
        if (front.getObjectType() == IFrontSensorResult.Types.TeamMainBot || 
            front.getObjectType() == IFrontSensorResult.Types.TeamSecondaryBot) {
            moveBack();
            return;
        }

        if (unstuckMode) {
            handleUnstuck();
            return;
        }

        if (isBlocked()) {
            stuckTimer++;
            if (stuckTimer > 10) {
                unstuckMode = true;
                unstuckTimer = 20;
                unstuckTurnRight = Math.random() > 0.5;
                return;
            }
        } else {
            stuckTimer = 0;
        }

        ArrayList<IRadarResult> radar = detectRadar();
        IRadarResult enemy = getClosestEnemy(radar);

        if (enemy != null) {
            handleContact(enemy);
        } else {
            handlePatrol();
        }
    }
    
    private void handleUnstuck() {
        unstuckTimer--;
        if (unstuckTimer <= 0) { unstuckMode = false; return; }
        moveBack();
        if (unstuckTurnRight) stepTurn(Parameters.Direction.RIGHT);
        else stepTurn(Parameters.Direction.LEFT);
    }
    
    private void handleContact(IRadarResult enemy) {
        double dist = enemy.getObjectDistance();
        double enemyDir = enemy.getObjectDirection();
        
        // Broadcast
        double ex = myX + dist * Math.cos(enemyDir);
        double ey = myY + dist * Math.sin(enemyDir);
        broadcast("TARGET:" + (int)ex + ":" + (int)ey);
        
        turnTo(enemyDir);
        
        if (isFacing(enemyDir) && fireCooldown <= 0) safeFire(enemyDir);
        
        if (isFacing(enemyDir)) {
            if (dist < MIN_SAFE_DIST) moveBack();
            else if (dist > MAX_SAFE_DIST) move();
        }
    }

    private void handlePatrol() {
        double targetDir = movingUp ? Parameters.NORTH : Parameters.SOUTH;
        targetDir += (Math.random() - 0.5) * 0.2; 
        if (Math.random() < 0.1) targetDir = Parameters.EAST;

        turnTo(targetDir);
        
        if (isBlocked()) {
            movingUp = !movingUp;
            stepTurn(Parameters.Direction.RIGHT);
        } else {
            move();
            myX += Parameters.teamASecondaryBotSpeed * Math.cos(getHeading());
            myY += Parameters.teamASecondaryBotSpeed * Math.sin(getHeading());
        }
    }

    private void safeFire(double dir) {
        IFrontSensorResult front = detectFront();
        if (front.getObjectType() == IFrontSensorResult.Types.TeamMainBot || 
            front.getObjectType() == IFrontSensorResult.Types.TeamSecondaryBot) return; 
        fire(dir);
        fireCooldown = FIRE_LATENCY;
    }

    private void turnTo(double targetDir) {
        double current = getHeading();
        double delta = targetDir - current;
        while (delta > Math.PI) delta -= 2 * Math.PI;
        while (delta < -Math.PI) delta += 2 * Math.PI;
        if (delta > 0) stepTurn(Parameters.Direction.RIGHT);
        else stepTurn(Parameters.Direction.LEFT);
    }
    
    private boolean isFacing(double dir) {
        double delta = Math.abs(getHeading() - dir);
        while (delta > Math.PI) delta = Math.abs(delta - 2 * Math.PI);
        return delta < 0.2;
    }
    
    private boolean isBlocked() {
        IFrontSensorResult f = detectFront();
        return f.getObjectType() == IFrontSensorResult.Types.WALL ||
               f.getObjectType() == IFrontSensorResult.Types.Wreck;
    }

    private IRadarResult getClosestEnemy(ArrayList<IRadarResult> radar) {
        IRadarResult best = null;
        double minDist = Double.MAX_VALUE;
        for (IRadarResult r : radar) {
            if (r.getObjectType() == IRadarResult.Types.OpponentMainBot || 
                r.getObjectType() == IRadarResult.Types.OpponentSecondaryBot) {
                if (r.getObjectDistance() < minDist) {
                    minDist = r.getObjectDistance();
                    best = r;
                }
            }
        }
        return best;
    }
}